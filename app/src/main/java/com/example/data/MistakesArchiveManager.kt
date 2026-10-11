package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ArchivedMistake(
    val id: String = UUID.randomUUID().toString(),
    val dateKey: String,       // "yyyy-MM-dd"
    val displayDate: String,   // "Today, Oct 11", "Yesterday", or "Oct 9, 2026"
    val timestamp: Long,
    val timeFormatted: String, // "02:45 PM"
    val original: String,
    val replacement: String,
    val type: SuggestionType,   // GRAMMAR (Red) or WORD_CHOICE (Yellow)
    val reason: String
)

class MistakesArchiveManager(context: Context) {
    companion object {
        private const val TAG = "MistakesArchive"
        private const val PREFS_NAME = "mistakes_archive_prefs"
        private const val KEY_ARCHIVE_JSON = "archive_json"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _items = mutableListOf<ArchivedMistake>()

    init {
        loadArchive()
    }

    private fun loadArchive() {
        synchronized(this) {
            _items.clear()
            val raw = prefs.getString(KEY_ARCHIVE_JSON, null)
            if (!raw.isNullOrBlank()) {
                try {
                    val array = JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.optString("id", UUID.randomUUID().toString())
                        val dateKey = obj.optString("dateKey", "")
                        val displayDate = obj.optString("displayDate", "")
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        val timeFormatted = obj.optString("timeFormatted", "")
                        val original = obj.optString("original", "")
                        val replacement = obj.optString("replacement", "")
                        val typeStr = obj.optString("type", SuggestionType.GRAMMAR.name)
                        val reason = obj.optString("reason", "")

                        val type = try {
                            SuggestionType.valueOf(typeStr)
                        } catch (e: Exception) {
                            SuggestionType.GRAMMAR
                        }

                        if (original.isNotBlank() && dateKey.isNotBlank()) {
                            _items.add(
                                ArchivedMistake(
                                    id = id,
                                    dateKey = dateKey,
                                    displayDate = displayDate,
                                    timestamp = timestamp,
                                    timeFormatted = timeFormatted,
                                    original = original,
                                    replacement = replacement,
                                    type = type,
                                    reason = reason
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading archive JSON: ${e.message}")
                }
            }
        }
    }

    private fun saveArchive() {
        val array = JSONArray()
        for (item in _items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("dateKey", item.dateKey)
                put("displayDate", item.displayDate)
                put("timestamp", item.timestamp)
                put("timeFormatted", item.timeFormatted)
                put("original", item.original)
                put("replacement", item.replacement)
                put("type", item.type.name)
                put("reason", item.reason)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_ARCHIVE_JSON, array.toString()).apply()
    }

    fun recordMistake(suggestion: GrammarSuggestion) = synchronized(this) {
        val now = System.currentTimeMillis()
        val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateKey = dateKeyFormat.format(Date(now))

        // Avoid adding exact duplicate on the same day within last 5 minutes
        val isDuplicate = _items.any {
            it.dateKey == dateKey &&
            it.original.equals(suggestion.original, ignoreCase = true) &&
            it.replacement.equals(suggestion.replacement, ignoreCase = true) &&
            (now - it.timestamp) < 300_000L
        }
        if (isDuplicate) return

        val displayDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val entry = ArchivedMistake(
            id = UUID.randomUUID().toString(),
            dateKey = dateKey,
            displayDate = displayDateFormat.format(Date(now)),
            timestamp = now,
            timeFormatted = timeFormat.format(Date(now)),
            original = suggestion.original,
            replacement = suggestion.replacement,
            type = suggestion.type,
            reason = suggestion.reason
        )
        _items.add(0, entry) // Add newest first
        saveArchive()
    }

    /**
     * Returns mistakes grouped by date (newest day first).
     * Only days that have AT LEAST ONE item are returned. Days with 0 items are omitted.
     */
    fun getMistakesGroupedByDay(): Map<String, List<ArchivedMistake>> = synchronized(this) {
        // Group by displayDate while preserving date order
        _items.groupBy { it.displayDate }
            .filter { it.value.isNotEmpty() }
    }

    fun getTotalMistakesCount(): Int = synchronized(this) {
        _items.size
    }

    fun deleteDay(displayDate: String) = synchronized(this) {
        _items.removeAll { it.displayDate == displayDate }
        saveArchive()
    }

    fun clearAll() = synchronized(this) {
        _items.clear()
        saveArchive()
    }
}
