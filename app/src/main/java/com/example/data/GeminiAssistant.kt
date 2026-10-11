package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAssistant {
    companion object {
        private const val TAG = "GeminiAssistant"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Contextual smoothing / boundary structural refinement.
     * Takes previous context words (up to 20 words) and new snippet.
     * Fixes boundary punctuation, capitalization, and contextual mishearings
     * WITHOUT correcting intentional grammar errors (which are handled by inspector).
     */
    suspend fun smoothSnippet(
        poolManager: ApiKeyPoolManager,
        modelName: String,
        contextWords: String,
        newSnippet: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val candidates = poolManager.getCandidateKeysInOrder()
        if (candidates.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No API keys in pool"))
        }

        val prompt = buildString {
            append("You are a real-time transcript boundary and structural corrector.\n")
            if (contextWords.isNotBlank()) {
                append("PREVIOUS CONTEXT (preceding words): \"$contextWords\"\n")
            } else {
                append("PREVIOUS CONTEXT: none (beginning of transcript)\n")
            }
            append("NEW SPEECH SNIPPET: \"$newSnippet\"\n\n")
            append("TASK:\n")
            append("1. Fix boundary punctuation and capitalization so this new snippet flows smoothly from the context (e.g., lowercase if continuing an unfinished sentence, remove period before 'and'/'but').\n")
            append("2. Fix obvious speech-to-text phonetic mishearings based on context.\n")
            append("3. DO NOT correct grammar mistakes (subject-verb agreement, tense, etc.); keep the speaker's own grammar intact.\n")
            append("4. Return ONLY the refined snippet text that directly replaces the new snippet. No quotes, no explanations.")
        }

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.0)
                val thinking = JSONObject().apply { put("thinkingLevel", "low") }
                put("thinkingConfig", thinking)
            }
            put("generationConfig", generationConfig)
        }

        val effectiveModel = modelName.trim().ifBlank { "gemini-3.5-flash-lite" }

        // Iterate through pool candidates
        for (pair in candidates) {
            val (idx, entry) = pair
            val url = "$BASE_URL$effectiveModel:generateContent?key=${entry.key}"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val text = parseSingleTextResponse(body)
                        if (text.isNotBlank()) {
                            poolManager.recordKeySuccess(idx)
                            return@withContext Result.success(text)
                        }
                    } else {
                        val isRateLimit = response.code == 429
                        poolManager.recordKeyFailure(idx, isRateLimit, "HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Smoothing call failed on key ${entry.maskedKey}: ${e.message}")
            }
        }

        // Fallback to original snippet if network/AI smoothing couldn't complete
        Result.success(newSnippet)
    }

    /**
     * Inspects the recent transcript (~50 words) for concise grammar mistakes (Red)
     * and word choice / stylistic improvements (Yellow).
     */
    suspend fun inspectGrammarAndWordChoice(
        poolManager: ApiKeyPoolManager,
        modelName: String,
        recentText: String
    ): Result<List<GrammarSuggestion>> = withContext(Dispatchers.IO) {
        val candidates = poolManager.getCandidateKeysInOrder()
        if (candidates.isEmpty() || recentText.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val prompt = buildString {
            append("Analyze the following transcript excerpt (up to 50 words) for grammar mistakes and concise word choice improvements:\n")
            append("\"$recentText\"\n\n")
            append("Identify issues. Targets MUST be short and concise (1-3 words max, matching text in the excerpt).\n")
            append("Types:\n")
            append("- \"grammar\": Grammatical errors (e.g. subject-verb agreement, incorrect verb tense, missing prepositions, wrong pronouns).\n")
            append("- \"word_choice\": Phrasing, awkward vocabulary, or expressions that could be improved for natural clarity.\n\n")
            append("Return ONLY a JSON array with objects in this exact format:\n")
            append("[\n")
            append("  {\n")
            append("    \"original\": \"exact word or phrase from text\",\n")
            append("    \"replacement\": \"corrected word or phrase\",\n")
            append("    \"type\": \"grammar\" or \"word_choice\",\n")
            append("    \"reason\": \"short 1-sentence reason\"\n")
            append("  }\n")
            append("]\n")
            append("If there are no errors, return [].")
        }

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.0)
                val thinking = JSONObject().apply { put("thinkingLevel", "low") }
                put("thinkingConfig", thinking)
            }
            put("generationConfig", generationConfig)
        }

        val effectiveModel = modelName.trim().ifBlank { "gemini-3.5-flash-lite" }

        // Start from an alternate key if possible to balance load across pool
        val orderedCandidates = if (candidates.size > 1) {
            val second = candidates.drop(1) + candidates.take(1)
            second
        } else {
            candidates
        }

        for (pair in orderedCandidates) {
            val (idx, entry) = pair
            val url = "$BASE_URL$effectiveModel:generateContent?key=${entry.key}"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val suggestions = parseSuggestionsResponse(body)
                        poolManager.recordKeySuccess(idx)
                        return@withContext Result.success(suggestions)
                    } else {
                        val isRateLimit = response.code == 429
                        poolManager.recordKeyFailure(idx, isRateLimit, "HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Grammar check failed on key ${entry.maskedKey}: ${e.message}")
            }
        }

        Result.success(emptyList())
    }

    private fun parseSingleTextResponse(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""

            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""

            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (!p.optBoolean("thought", false)) {
                    val t = p.optString("text", "")
                    if (t.isNotBlank()) sb.append(t)
                }
            }
            sb.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseSuggestionsResponse(jsonString: String): List<GrammarSuggestion> {
        val results = mutableListOf<GrammarSuggestion>()
        try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            if (candidates.length() == 0) return emptyList()

            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return emptyList()
            val parts = content.optJSONArray("parts") ?: return emptyList()

            var jsonArrayStr = ""
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (!p.optBoolean("thought", false)) {
                    val t = p.optString("text", "")
                    if (t.isNotBlank()) {
                        jsonArrayStr = t
                        break
                    }
                }
            }

            if (jsonArrayStr.isBlank()) return emptyList()

            val array = JSONArray(jsonArrayStr)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val original = item.optString("original", "").trim()
                val replacement = item.optString("replacement", "").trim()
                val typeStr = item.optString("type", "grammar").lowercase()
                val reason = item.optString("reason", "").trim()

                if (original.isNotBlank() && replacement.isNotBlank()) {
                    val type = if (typeStr == "word_choice") SuggestionType.WORD_CHOICE else SuggestionType.GRAMMAR
                    results.add(
                        GrammarSuggestion(
                            original = original,
                            replacement = replacement,
                            type = type,
                            reason = reason
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing grammar suggestions JSON: ${e.message}")
        }
        return results
    }
}
