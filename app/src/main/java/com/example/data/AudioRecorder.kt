package com.example.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class RecordedAudioChunk(
    val id: Long,
    val wavBytes: ByteArray,
    val durationMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RecordedAudioChunk
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

class AudioRecorder(
    private val onChunkReady: (RecordedAudioChunk) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SHORTS = 1024 // ~64ms per buffer read at 16kHz
        private const val PRE_SPEECH_BUFFER_COUNT = 6 // ~384ms pre-buffer
        private const val MIN_SPEECH_DURATION_MS = 300L // Filter out accidental clicks/pops
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var isRecording = false

    @Volatile
    var silenceDurationMs: Long = 1000L

    @Volatile
    var sensitivityThreshold: Int = 450

    private var chunkSequenceId = 0L

    fun updateSilenceDuration(ms: Long) {
        silenceDurationMs = ms.coerceIn(400L, 4000L)
        Log.d(TAG, "Updated silenceDurationMs to $silenceDurationMs ms")
    }

    fun updateSensitivity(threshold: Int) {
        sensitivityThreshold = threshold.coerceIn(200, 2500)
        Log.d(TAG, "Updated sensitivityThreshold to $sensitivityThreshold")
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, CHUNK_SHORTS * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                onStatusChanged("Audio hardware initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            onStatusChanged("Listening...")

            recordingJob = scope.launch {
                runRecordingLoop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            onStatusChanged("Could not access microphone: ${e.message}")
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord: ${e.message}")
        } finally {
            audioRecord = null
            onAmplitudeChanged(0f)
            onStatusChanged("Mic Paused")
        }
    }

    private fun runRecordingLoop() {
        val shortBuffer = ShortArray(CHUNK_SHORTS)
        val preSpeechRingBuffer = ArrayDeque<ByteArray>(PRE_SPEECH_BUFFER_COUNT)
        val speechOutputStream = ByteArrayOutputStream()

        var isSpeaking = false
        var speechStartTime = 0L
        var silenceStartTime = 0L
        var consecutiveNoiseSpikes = 0

        while (isRecording && scope.isActive) {
            val record = audioRecord ?: break
            val readCount = record.read(shortBuffer, 0, shortBuffer.size)

            if (readCount <= 0) {
                continue
            }

            // Calculate RMS
            var sumOfSquares = 0.0
            for (i in 0 until readCount) {
                val sample = shortBuffer[i].toDouble()
                sumOfSquares += sample * sample
            }
            val rms = sqrt(sumOfSquares / readCount).toFloat()

            // Normalize amplitude for visualizer (0.0 to 1.0)
            val normalizedAmp = (rms / 3000f).coerceIn(0f, 1f)
            onAmplitudeChanged(normalizedAmp)

            // Convert shorts to little-endian bytes
            val byteBuffer = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until readCount) {
                byteBuffer.putShort(shortBuffer[i])
            }
            val chunkBytes = byteBuffer.array()

            val now = System.currentTimeMillis()
            val isAboveThreshold = rms >= sensitivityThreshold

            if (isAboveThreshold) {
                if (!isSpeaking) {
                    // Beginning of detected speech!
                    isSpeaking = true
                    speechStartTime = now
                    speechOutputStream.reset()

                    // Prepend pre-speech buffer so early phonemes aren't clipped
                    for (preChunk in preSpeechRingBuffer) {
                        speechOutputStream.write(preChunk)
                    }
                    onStatusChanged("Hearing speech...")
                    silenceStartTime = 0L
                    consecutiveNoiseSpikes = 0
                } else {
                    // Already speaking: if we were previously in silence timing,
                    // require sustained speech (>= 2 chunks) before canceling the silence timer
                    // to prevent a brief click/breath from restarting a 2-second silence timer.
                    if (silenceStartTime > 0L) {
                        consecutiveNoiseSpikes++
                        if (consecutiveNoiseSpikes >= 2) {
                            silenceStartTime = 0L
                            consecutiveNoiseSpikes = 0
                            onStatusChanged("Hearing speech...")
                        }
                    }
                }

                // Append current audio
                speechOutputStream.write(chunkBytes)
            } else {
                // Below threshold
                consecutiveNoiseSpikes = 0
                if (isSpeaking) {
                    speechOutputStream.write(chunkBytes)

                    if (silenceStartTime == 0L) {
                        silenceStartTime = now
                    }

                    val elapsedSilence = now - silenceStartTime
                    val currentTargetMs = silenceDurationMs

                    // Provide live progress feedback so user sees the timer counting up!
                    val elapsedSec = String.format("%.1f", elapsedSilence / 1000f)
                    val targetSec = String.format("%.1f", currentTargetMs / 1000f)
                    onStatusChanged("Pausing (${elapsedSec}s / ${targetSec}s)...")

                    if (elapsedSilence >= currentTargetMs) {
                        // User paused for currentTargetMs!
                        val totalDuration = now - speechStartTime

                        if (totalDuration >= MIN_SPEECH_DURATION_MS) {
                            val rawPcm = speechOutputStream.toByteArray()
                            val wavBytes = createWav(rawPcm, SAMPLE_RATE, 1, 16)
                            val chunkId = ++chunkSequenceId

                            Log.d(TAG, "Silence pause completed ($elapsedSilence ms >= $currentTargetMs ms). Emitting chunk #$chunkId ($totalDuration ms)")

                            onChunkReady(
                                RecordedAudioChunk(
                                    id = chunkId,
                                    wavBytes = wavBytes,
                                    durationMs = totalDuration
                                )
                            )
                        }

                        // Seamlessly reset speech buffer; recording continues without a gap!
                        isSpeaking = false
                        speechOutputStream.reset()
                        silenceStartTime = 0L
                        consecutiveNoiseSpikes = 0
                        onStatusChanged("Listening...")
                    }
                } else {
                    // Not speaking, maintain circular pre-speech buffer
                    if (preSpeechRingBuffer.size >= PRE_SPEECH_BUFFER_COUNT) {
                        preSpeechRingBuffer.removeFirst()
                    }
                    preSpeechRingBuffer.addLast(chunkBytes)
                }
            }
        }
    }

    private fun createWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = pcmData.size
        val totalAudioLen = totalDataLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalAudioLen)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt subchunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data subchunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(totalDataLen)

        val out = ByteArrayOutputStream(header.size + pcmData.size)
        out.write(header)
        out.write(pcmData)
        return out.toByteArray()
    }
}
