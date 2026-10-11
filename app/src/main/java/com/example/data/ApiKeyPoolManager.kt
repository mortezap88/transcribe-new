package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

enum class ApiKeyStatus {
    ACTIVE,
    HEALTHY,
    RATE_LIMITED,
    FAILED
}

data class ApiKeyEntry(
    val id: String,
    val key: String,
    val status: ApiKeyStatus = ApiKeyStatus.HEALTHY,
    val failureCount: Int = 0,
    val lastError: String? = null
) {
    val maskedKey: String
        get() = if (key.length <= 8) {
            "••••${key.takeLast(3)}"
        } else {
            "${key.take(6)}••••${key.takeLast(4)}"
        }
}

class ApiKeyPoolManager(context: Context) {
    companion object {
        private const val TAG = "ApiKeyPool"
        private const val PREFS_NAME = "api_pool_prefs"
        private const val KEY_POOL_JSON = "pool_json"
        private const val KEY_ACTIVE_INDEX = "active_index"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _pool = mutableListOf<ApiKeyEntry>()
    val pool: List<ApiKeyEntry>
        get() = synchronized(this) { _pool.toList() }

    var activeIndex: Int = 0
        private set

    init {
        loadPool()
    }

    private fun loadPool() {
        synchronized(this) {
            _pool.clear()
            val rawJson = prefs.getString(KEY_POOL_JSON, null)
            if (!rawJson.isNullOrBlank()) {
                try {
                    val array = JSONArray(rawJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.optString("id", System.currentTimeMillis().toString() + "_$i")
                        val key = obj.optString("key", "").trim()
                        val statusStr = obj.optString("status", ApiKeyStatus.HEALTHY.name)
                        val failureCount = obj.optInt("failureCount", 0)
                        val lastError = if (obj.has("lastError") && !obj.isNull("lastError")) {
                            obj.optString("lastError").ifBlank { null }
                        } else null

                        if (key.isNotBlank()) {
                            val status = try {
                                ApiKeyStatus.valueOf(statusStr)
                            } catch (e: Exception) {
                                ApiKeyStatus.HEALTHY
                            }
                            _pool.add(
                                ApiKeyEntry(
                                    id = id,
                                    key = key,
                                    status = status,
                                    failureCount = failureCount,
                                    lastError = if (lastError.isNullOrBlank()) null else lastError
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing saved API pool JSON: ${e.message}")
                }
            }

            // Seed with BuildConfig or default if pool is empty
            if (_pool.isEmpty()) {
                val seedKey = try {
                    BuildConfig.GEMINI_API_KEY
                } catch (e: Throwable) {
                    ""
                }
                if (seedKey.isNotBlank() && seedKey != "MY_GEMINI_API_KEY") {
                    _pool.add(
                        ApiKeyEntry(
                            id = "seed_1",
                            key = seedKey,
                            status = ApiKeyStatus.ACTIVE
                        )
                    )
                }
            }

            activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0).coerceIn(0, maxOf(0, _pool.size - 1))
            updateActiveStatus()
            savePool()
        }
    }

    private fun savePool() {
        val array = JSONArray()
        for (item in _pool) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("key", item.key)
                put("status", item.status.name)
                put("failureCount", item.failureCount)
                put("lastError", item.lastError ?: "")
            }
            array.put(obj)
        }
        prefs.edit()
            .putString(KEY_POOL_JSON, array.toString())
            .putInt(KEY_ACTIVE_INDEX, activeIndex)
            .apply()
    }

    private fun updateActiveStatus() {
        for (i in _pool.indices) {
            val current = _pool[i]
            if (i == activeIndex && current.status != ApiKeyStatus.FAILED) {
                _pool[i] = current.copy(status = ApiKeyStatus.ACTIVE)
            } else if (current.status == ApiKeyStatus.ACTIVE) {
                _pool[i] = current.copy(status = ApiKeyStatus.HEALTHY)
            }
        }
    }

    fun addKey(key: String): Boolean = synchronized(this) {
        val cleanKey = key.trim()
        if (cleanKey.isBlank()) return false
        if (_pool.any { it.key == cleanKey }) return false // Avoid duplicates

        val newEntry = ApiKeyEntry(
            id = "key_${System.currentTimeMillis()}_${_pool.size}",
            key = cleanKey,
            status = if (_pool.isEmpty()) ApiKeyStatus.ACTIVE else ApiKeyStatus.HEALTHY
        )
        _pool.add(newEntry)
        savePool()
        return true
    }

    fun addMultipleKeys(keysText: String): Int = synchronized(this) {
        val candidates = keysText.split("\n", ",", ";", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        var addedCount = 0
        for (candidate in candidates) {
            if (_pool.none { it.key == candidate }) {
                _pool.add(
                    ApiKeyEntry(
                        id = "key_${System.currentTimeMillis()}_${_pool.size}",
                        key = candidate,
                        status = if (_pool.isEmpty()) ApiKeyStatus.ACTIVE else ApiKeyStatus.HEALTHY
                    )
                )
                addedCount++
            }
        }
        if (addedCount > 0) {
            savePool()
        }
        return addedCount
    }

    fun removeKey(id: String) = synchronized(this) {
        val index = _pool.indexOfFirst { it.id == id }
        if (index != -1) {
            _pool.removeAt(index)
            if (activeIndex >= _pool.size) {
                activeIndex = maxOf(0, _pool.size - 1)
            }
            updateActiveStatus()
            savePool()
        }
    }

    fun setActiveIndex(index: Int) = synchronized(this) {
        if (index in _pool.indices) {
            activeIndex = index
            updateActiveStatus()
            savePool()
        }
    }

    /**
     * Retrieves the current candidate keys ordered starting from activeIndex
     * and wrapping around, so fallback tries every available key in sequence.
     */
    fun getCandidateKeysInOrder(): List<Pair<Int, ApiKeyEntry>> = synchronized(this) {
        if (_pool.isEmpty()) return emptyList()
        val list = mutableListOf<Pair<Int, ApiKeyEntry>>()
        val count = _pool.size
        for (offset in 0 until count) {
            val idx = (activeIndex + offset) % count
            list.add(Pair(idx, _pool[idx]))
        }
        list
    }

    fun recordKeySuccess(index: Int) = synchronized(this) {
        if (index in _pool.indices) {
            val current = _pool[index]
            _pool[index] = current.copy(
                status = ApiKeyStatus.ACTIVE,
                failureCount = 0,
                lastError = null
            )
            activeIndex = index
            updateActiveStatus()
            savePool()
        }
    }

    fun recordKeyFailure(index: Int, isRateLimit: Boolean, errorMessage: String) = synchronized(this) {
        if (index in _pool.indices) {
            val current = _pool[index]
            val newStatus = if (isRateLimit) ApiKeyStatus.RATE_LIMITED else ApiKeyStatus.FAILED
            _pool[index] = current.copy(
                status = newStatus,
                failureCount = current.failureCount + 1,
                lastError = errorMessage
            )
            // Advance active index to next candidate
            if (_pool.size > 1) {
                activeIndex = (index + 1) % _pool.size
                updateActiveStatus()
            }
            savePool()
        }
    }

    fun resetPoolStatuses() = synchronized(this) {
        for (i in _pool.indices) {
            _pool[i] = _pool[i].copy(
                status = if (i == activeIndex) ApiKeyStatus.ACTIVE else ApiKeyStatus.HEALTHY,
                failureCount = 0,
                lastError = null
            )
        }
        savePool()
    }
}
