package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApiKeyEntry
import com.example.data.ApiKeyPoolManager
import com.example.data.ArchivedMistake
import com.example.data.AudioRecorder
import com.example.data.GeminiAssistant
import com.example.data.GeminiTranscriber
import com.example.data.GrammarSuggestion
import com.example.data.MistakesArchiveManager
import com.example.data.PreferencesManager
import com.example.data.RecordedAudioChunk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val apiKey: String = "",
    val modelName: String = PreferencesManager.DEFAULT_MODEL,
    val correctionModelName: String = PreferencesManager.DEFAULT_CORRECTION_MODEL,
    val fontSizeSp: Float = PreferencesManager.DEFAULT_FONT_SIZE,
    val silenceDurationMs: Long = PreferencesManager.DEFAULT_SILENCE_DURATION_MS,
    val sensitivityThreshold: Int = PreferencesManager.DEFAULT_SENSITIVITY,
    val keepScreenOn: Boolean = true,
    val enableSmoothing: Boolean = true,
    val enableGrammarCheck: Boolean = true
)

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val transcriber = GeminiTranscriber()
    private val assistant = GeminiAssistant()
    val poolManager = ApiKeyPoolManager(application)
    val archiveManager = MistakesArchiveManager(application)

    private val _settings = MutableStateFlow(
        SettingsState(
            apiKey = poolManager.pool.getOrNull(poolManager.activeIndex)?.key ?: prefs.apiKey,
            modelName = prefs.modelName,
            correctionModelName = prefs.correctionModelName,
            fontSizeSp = prefs.fontSizeSp,
            silenceDurationMs = prefs.silenceDurationMs,
            sensitivityThreshold = prefs.sensitivityThreshold,
            keepScreenOn = prefs.keepScreenOn,
            enableSmoothing = prefs.enableSmoothing,
            enableGrammarCheck = prefs.enableGrammarCheck
        )
    )
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    // API Pool Observables
    private val _apiPool = MutableStateFlow(poolManager.pool)
    val apiPool: StateFlow<List<ApiKeyEntry>> = _apiPool.asStateFlow()

    private val _activeKeyIndex = MutableStateFlow(poolManager.activeIndex)
    val activeKeyIndex: StateFlow<Int> = _activeKeyIndex.asStateFlow()

    // Persisted transcript across app closes
    private val _transcript = MutableStateFlow(prefs.savedTranscript)
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    // Interactive Grammar and Word Choice Suggestions
    private val _grammarSuggestions = MutableStateFlow<List<GrammarSuggestion>>(emptyList())
    val grammarSuggestions: StateFlow<List<GrammarSuggestion>> = _grammarSuggestions.asStateFlow()

    // Mistakes Archive grouped by day
    private val _groupedMistakes = MutableStateFlow(archiveManager.getMistakesGroupedByDay())
    val groupedMistakes: StateFlow<Map<String, List<ArchivedMistake>>> = _groupedMistakes.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _statusMessage = MutableStateFlow("Waiting to hear...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _inFlightCount = MutableStateFlow(0)
    val inFlightCount: StateFlow<Int> = _inFlightCount.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Sequential audio chunk queue
    private val chunkChannel = Channel<RecordedAudioChunk>(Channel.UNLIMITED)

    private val audioRecorder = AudioRecorder(
        onChunkReady = { chunk ->
            _inFlightCount.value += 1
            viewModelScope.launch {
                chunkChannel.send(chunk)
            }
        },
        onAmplitudeChanged = { amp ->
            _audioLevel.value = amp
        },
        onStatusChanged = { status ->
            _statusMessage.value = status
        }
    )

    init {
        audioRecorder.silenceDurationMs = prefs.silenceDurationMs
        audioRecorder.sensitivityThreshold = prefs.sensitivityThreshold

        viewModelScope.launch {
            for (chunk in chunkChannel) {
                processAudioChunk(chunk)
            }
        }
    }

    private suspend fun processAudioChunk(chunk: RecordedAudioChunk) {
        val currentSettings = _settings.value

        // Step 1: Transcribe audio chunk with pool failover (quiet failover, no clutter banner!)
        val result = transcriber.transcribeWithPool(
            poolManager = poolManager,
            modelName = currentSettings.modelName,
            wavBytes = chunk.wavBytes,
            onPoolFailover = { _, _, _ ->
                refreshPoolState()
            }
        )

        refreshPoolState()
        _inFlightCount.value = maxOf(0, _inFlightCount.value - 1)

        result.onSuccess { rawSnippet ->
            if (rawSnippet.isNotBlank()) {
                handleNewTranscriptionSnippet(rawSnippet.trim(), currentSettings)
            }
        }.onFailure { error ->
            val message = error.message ?: "Transcription failed"
            _errorMessage.value = message
        }
    }

    private fun handleNewTranscriptionSnippet(snippet: String, currentSettings: SettingsState) {
        val previousFullText = _transcript.value
        val contextWords = getLastWords(previousFullText, 20)

        // Show raw snippet immediately on screen and persist
        val appendedText = if (previousFullText.isBlank()) {
            snippet
        } else {
            if (previousFullText.endsWith("\n") || previousFullText.endsWith(" ")) {
                previousFullText + snippet
            } else {
                "$previousFullText $snippet"
            }
        }
        updateTranscriptAndPersist(appendedText)

        // Step 2: Contextual smoothing (structural boundary correction using gemini-3.5-flash-lite)
        if (currentSettings.enableSmoothing && previousFullText.isNotBlank()) {
            viewModelScope.launch {
                val smoothResult = assistant.smoothSnippet(
                    poolManager = poolManager,
                    modelName = currentSettings.correctionModelName,
                    contextWords = contextWords,
                    newSnippet = snippet
                )
                refreshPoolState()
                smoothResult.onSuccess { smoothed ->
                    val cleanSmoothed = smoothed.trim()
                    if (cleanSmoothed.isNotBlank() && cleanSmoothed != snippet) {
                        val currentText = _transcript.value
                        if (currentText.endsWith(snippet)) {
                            val prefix = currentText.removeSuffix(snippet).trimEnd()
                            val updated = if (prefix.isBlank()) cleanSmoothed else "$prefix $cleanSmoothed"
                            updateTranscriptAndPersist(updated)
                        }
                    }
                }
            }
        }

        // Step 3: Interactive Grammar & Word Choice Inspection (last ~50 words)
        // Automatically archives every mistake day-by-day for study!
        if (currentSettings.enableGrammarCheck) {
            viewModelScope.launch {
                val recent50 = getLastWords(_transcript.value, 50)
                val grammarResult = assistant.inspectGrammarAndWordChoice(
                    poolManager = poolManager,
                    modelName = currentSettings.correctionModelName,
                    recentText = recent50
                )
                refreshPoolState()
                grammarResult.onSuccess { newSuggestions ->
                    if (newSuggestions.isNotEmpty()) {
                        val currentList = _grammarSuggestions.value.toMutableList()
                        for (sugg in newSuggestions) {
                            if (_transcript.value.contains(sugg.original, ignoreCase = false)) {
                                if (currentList.none { it.original == sugg.original }) {
                                    currentList.add(sugg)
                                }
                                // Automate archiving to the daily mistakes study notebook!
                                archiveManager.recordMistake(sugg)
                            }
                        }
                        _grammarSuggestions.value = currentList
                        _groupedMistakes.value = archiveManager.getMistakesGroupedByDay()
                    }
                }
            }
        }
    }

    private fun updateTranscriptAndPersist(newText: String) {
        _transcript.value = newText
        prefs.savedTranscript = newText
    }

    private fun getLastWords(text: String, count: Int): String {
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        return words.takeLast(count).joinToString(" ")
    }

    fun applySuggestion(suggestion: GrammarSuggestion) {
        val currentText = _transcript.value
        if (currentText.contains(suggestion.original)) {
            updateTranscriptAndPersist(currentText.replaceFirst(suggestion.original, suggestion.replacement))
        }
        _grammarSuggestions.value = _grammarSuggestions.value.filter { it.id != suggestion.id }
    }

    fun dismissSuggestion(suggestion: GrammarSuggestion) {
        _grammarSuggestions.value = _grammarSuggestions.value.filter { it.id != suggestion.id }
    }

    fun deleteArchiveDay(displayDate: String) {
        archiveManager.deleteDay(displayDate)
        _groupedMistakes.value = archiveManager.getMistakesGroupedByDay()
    }

    fun clearArchive() {
        archiveManager.clearAll()
        _groupedMistakes.value = archiveManager.getMistakesGroupedByDay()
    }

    private fun refreshPoolState() {
        _apiPool.value = poolManager.pool
        _activeKeyIndex.value = poolManager.activeIndex
    }

    fun startListening() {
        if (_isListening.value) return
        audioRecorder.start()
        _isListening.value = true
    }

    fun stopListening() {
        if (!_isListening.value) return
        audioRecorder.stop()
        _isListening.value = false
        _statusMessage.value = "Mic Paused"
        _audioLevel.value = 0f
    }

    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun clearTranscript() {
        updateTranscriptAndPersist("")
        _grammarSuggestions.value = emptyList()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun setLiveSilenceDuration(durationMs: Long) {
        val clamped = durationMs.coerceIn(400L, 4000L)
        prefs.silenceDurationMs = clamped
        audioRecorder.updateSilenceDuration(clamped)
        _settings.value = _settings.value.copy(silenceDurationMs = clamped)
    }

    fun setLiveSensitivity(threshold: Int) {
        val clamped = threshold.coerceIn(200, 2500)
        prefs.sensitivityThreshold = clamped
        audioRecorder.updateSensitivity(clamped)
        _settings.value = _settings.value.copy(sensitivityThreshold = clamped)
    }

    // Pool Management
    fun addKeyToPool(key: String): Boolean {
        val added = poolManager.addKey(key)
        if (added) {
            refreshPoolState()
            if (_errorMessage.value?.contains("API key", ignoreCase = true) == true) {
                _errorMessage.value = null
            }
        }
        return added
    }

    fun addMultipleKeysToPool(keysText: String): Int {
        val count = poolManager.addMultipleKeys(keysText)
        if (count > 0) {
            refreshPoolState()
            if (_errorMessage.value?.contains("API key", ignoreCase = true) == true) {
                _errorMessage.value = null
            }
        }
        return count
    }

    fun removeKeyFromPool(id: String) {
        poolManager.removeKey(id)
        refreshPoolState()
    }

    fun selectActiveKey(index: Int) {
        poolManager.setActiveIndex(index)
        refreshPoolState()
    }

    fun resetPoolStatuses() {
        poolManager.resetPoolStatuses()
        refreshPoolState()
    }

    fun updateSettings(
        apiKey: String,
        modelName: String,
        correctionModelName: String,
        fontSizeSp: Float,
        silenceDurationMs: Long,
        sensitivityThreshold: Int,
        keepScreenOn: Boolean,
        enableSmoothing: Boolean,
        enableGrammarCheck: Boolean
    ) {
        if (apiKey.isNotBlank() && poolManager.pool.none { it.key == apiKey }) {
            poolManager.addKey(apiKey)
        }

        prefs.apiKey = apiKey
        prefs.modelName = modelName
        prefs.correctionModelName = correctionModelName
        prefs.fontSizeSp = fontSizeSp
        prefs.silenceDurationMs = silenceDurationMs
        prefs.sensitivityThreshold = sensitivityThreshold
        prefs.keepScreenOn = keepScreenOn
        prefs.enableSmoothing = enableSmoothing
        prefs.enableGrammarCheck = enableGrammarCheck

        audioRecorder.updateSilenceDuration(silenceDurationMs)
        audioRecorder.updateSensitivity(sensitivityThreshold)

        _settings.value = SettingsState(
            apiKey = apiKey,
            modelName = modelName,
            correctionModelName = correctionModelName,
            fontSizeSp = fontSizeSp,
            silenceDurationMs = silenceDurationMs,
            sensitivityThreshold = sensitivityThreshold,
            keepScreenOn = keepScreenOn,
            enableSmoothing = enableSmoothing,
            enableGrammarCheck = enableGrammarCheck
        )

        refreshPoolState()

        if (_errorMessage.value?.contains("API key", ignoreCase = true) == true && poolManager.pool.isNotEmpty()) {
            _errorMessage.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        chunkChannel.close()
    }
}
