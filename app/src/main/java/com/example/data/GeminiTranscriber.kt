package com.example.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiTranscriber {
    companion object {
        private const val TAG = "GeminiTranscriber"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeWithPool(
        poolManager: ApiKeyPoolManager,
        modelName: String,
        wavBytes: ByteArray,
        onPoolFailover: (fromKey: String, toKey: String, reason: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val candidates = poolManager.getCandidateKeysInOrder()
        if (candidates.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No API keys in pool. Please tap the gear icon to add Gemini API keys.")
            )
        }

        val errors = mutableListOf<String>()

        for (i in candidates.indices) {
            val (keyIndex, entry) = candidates[i]
            val key = entry.key

            val callResult = executeApiCall(key, modelName, wavBytes)
            if (callResult.isSuccess) {
                poolManager.recordKeySuccess(keyIndex)
                return@withContext callResult
            } else {
                val error = callResult.exceptionOrNull()
                val isRateLimit = error?.message?.contains("429") == true ||
                        error?.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true
                val errorDesc = error?.message ?: "Unknown error"
                errors.add("Key #${keyIndex + 1} (${entry.maskedKey}): $errorDesc")

                poolManager.recordKeyFailure(keyIndex, isRateLimit, errorDesc)

                // If there is another candidate in the pool, notify failover
                if (i + 1 < candidates.size) {
                    val nextEntry = candidates[i + 1].second
                    onPoolFailover(
                        entry.maskedKey,
                        nextEntry.maskedKey,
                        if (isRateLimit) "Rate limit (429)" else "API Error"
                    )
                }
            }
        }

        // All keys in the pool failed
        Result.failure(
            IOException("All ${candidates.size} API keys in the pool failed:\n" + errors.joinToString("\n"))
        )
    }

    suspend fun transcribeAudio(
        apiKey: String,
        modelName: String,
        wavBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please tap the gear icon to enter your API key.")
            )
        }
        executeApiCall(apiKey, modelName, wavBytes)
    }

    private fun executeApiCall(
        apiKey: String,
        modelName: String,
        wavBytes: ByteArray
    ): Result<String> {
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Part 1: Audio data
            val audioPart = JSONObject().apply {
                val inlineData = JSONObject().apply {
                    put("mimeType", "audio/wav")
                    put("data", base64Audio)
                }
                put("inlineData", inlineData)
            }
            partsArray.put(audioPart)

            // Part 2: Transcription prompt instruction
            val textPart = JSONObject().apply {
                put(
                    "text",
                    "Transcribe the spoken audio verbatim and accurately. " +
                            "Output ONLY the transcribed speech in the exact spoken language. " +
                            "Do not include quotes, timestamps, annotations, explanations, or introductory text."
                )
            }
            partsArray.put(textPart)

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.0)
            }
            put("generationConfig", generationConfig)
        }

        val effectiveModel = modelName.trim().ifBlank { "gemini-3.5-transcribe-live" }
        val url = "$BASE_URL$effectiveModel:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    // If live model rejects HTTP generateContent because it requires WebSocket, fallback to gemini-3.5-transcribe
                    if (effectiveModel.contains("live", ignoreCase = true) && response.code == 400) {
                        Log.i(TAG, "gemini-3.5-transcribe-live requires WebSocket, falling back to gemini-3.5-transcribe")
                        val fallbackUrl = "${BASE_URL}gemini-3.5-transcribe:generateContent?key=$apiKey"
                        val fallbackReq = Request.Builder()
                            .url(fallbackUrl)
                            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .build()
                        client.newCall(fallbackReq).execute().use { fbResponse ->
                            val fbBody = fbResponse.body?.string() ?: ""
                            if (fbResponse.isSuccessful) {
                                val text = parseTranscriptionResponse(fbBody)
                                return Result.success(text)
                            }
                        }
                    }

                    val errorMessage = parseErrorMessage(response.code, responseBody)
                    Log.e(TAG, "API call failed [${response.code}]: $errorMessage")
                    return Result.failure(IOException(errorMessage))
                }

                val transcribedText = parseTranscriptionResponse(responseBody)
                Log.d(TAG, "Extracted transcription ($effectiveModel): '$transcribedText'")
                Result.success(transcribedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network or parsing exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseTranscriptionResponse(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""

            val stringBuilder = java.lang.StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                // Skip internal thoughts if present
                if (part.optBoolean("thought", false)) {
                    continue
                }

                // Format 1: gemini-3.5-transcribe dedicated schema
                val audioTranscription = part.optJSONObject("audioTranscription")
                if (audioTranscription != null) {
                    val textFromAudio = audioTranscription.optString("text", "")
                        .ifBlank { audioTranscription.optString("transcript", "") }
                    if (textFromAudio.isNotBlank()) {
                        if (stringBuilder.isNotEmpty()) stringBuilder.append(" ")
                        stringBuilder.append(textFromAudio.trim())
                        continue
                    }
                }

                // Format 2: standard multimodal generateContent response
                val directText = part.optString("text", "")
                if (directText.isNotBlank()) {
                    val cleaned = directText.trim()
                    if (stringBuilder.isNotEmpty()) stringBuilder.append(" ")
                    stringBuilder.append(cleaned)
                }
            }
            stringBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response: ${e.message}")
            ""
        }
    }

    private fun parseErrorMessage(statusCode: Int, responseBody: String): String {
        return try {
            val root = JSONObject(responseBody)
            val error = root.optJSONObject("error")
            val message = error?.optString("message")
            if (!message.isNullOrBlank()) {
                "Gemini API error ($statusCode): $message"
            } else {
                "HTTP $statusCode: $responseBody"
            }
        } catch (e: Exception) {
            "HTTP $statusCode error occurred while transcribing"
        }
    }
}
