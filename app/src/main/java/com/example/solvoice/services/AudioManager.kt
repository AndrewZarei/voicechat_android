package com.example.solvoice.services

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.example.solvoice.models.VoiceData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Audio management service for voice recording and playback in voice chat
 */
class VoiceChatAudioManager(private val context: Context) {
    
    private val TAG = "VoiceChatAudioManager"
    
    // Audio recording configuration
    // Using 8kHz for voice chat - optimized for blockchain storage constraints
    // At 8kHz 16-bit mono: 16KB/sec → 30KB PDA = ~1.9 seconds per PDA
    // Total capacity: 10 PDAs × 1.9s = ~19 seconds of voice chat
    private val sampleRate = 8000  // Voice-optimized sample rate (telephone quality)
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    // Audio playback configuration
    private val playbackChannelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val playbackBufferSize = AudioTrack.getMinBufferSize(
        sampleRate, playbackChannelConfig, audioFormat
    )
    
    // Recording state
    private var audioRecord: AudioRecord? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    
    // State flows
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel
    
    // Recorded audio data
    private var currentRecordedData: ByteArray? = null
    private var recordingStartTime: Long = 0
    
    /**
     * Initialize audio manager
     */
    fun initialize() {
        Log.d(TAG, "Initializing audio manager")
        setupAudioSession()
    }
    
    /**
     * Setup audio session for voice chat
     */
    private fun setupAudioSession() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Set audio mode for voice communication
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
        audioManager.isBluetoothScoOn = true
        
        Log.d(TAG, "Audio session configured for voice communication")
    }
    
    /**
     * Start recording audio from microphone
     */
    suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (_isRecording.value) {
                Log.w(TAG, "Already recording")
                return@withContext false
            }
            
            // Initialize audio recorder
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 4 // Use larger buffer for better quality
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return@withContext false
            }
            
            // Start recording
            audioRecord?.startRecording()
            _isRecording.value = true
            recordingStartTime = System.currentTimeMillis()
            
            // Start recording coroutine
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordAudio()
            }
            
            Log.d(TAG, "Recording started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            false
        }
    }
    
    /**
     * Stop recording and return voice data
     */
    suspend fun stopRecording(): VoiceData? = withContext(Dispatchers.IO) {
        try {
            if (!_isRecording.value) {
                Log.w(TAG, "Not currently recording")
                return@withContext null
            }
            
            // Stop recording
            recordingJob?.cancel()
            audioRecord?.stop()
            _isRecording.value = false
            
            val duration = System.currentTimeMillis() - recordingStartTime
            
            // Create VoiceData object
            val voiceData = currentRecordedData?.let { data ->
                VoiceData(
                    audioData = data,
                    timestamp = recordingStartTime,
                    duration = duration,
                    sampleRate = sampleRate,
                    encoding = "pcm_16bit"
                )
            }
            
            cleanup()
            
            Log.d(TAG, "Recording stopped. Duration: ${duration}ms, Data size: ${currentRecordedData?.size ?: 0} bytes")
            voiceData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            cleanup()
            null
        }
    }
    
    /**
     * Record audio data in a coroutine
     */
    private suspend fun recordAudio() {
        val buffer = ByteArray(bufferSize)
        val recordedData = mutableListOf<ByteArray>()
        
        while (_isRecording.value && !Thread.currentThread().isInterrupted) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Copy buffer data
                    val audioChunk = buffer.copyOf(bytesRead)
                    recordedData.add(audioChunk)
                    
                    // Calculate audio level for UI feedback
                    val level = calculateAudioLevel(audioChunk)
                    _audioLevel.value = level
                    
                    // Update recording duration
                    val duration = System.currentTimeMillis() - recordingStartTime
                    _recordingDuration.value = duration
                }
                
                delay(10) // Small delay to prevent excessive CPU usage
            } catch (e: Exception) {
                Log.e(TAG, "Error reading audio data", e)
                break
            }
        }
        
        // Combine all recorded data
        val totalSize = recordedData.sumOf { it.size }
        currentRecordedData = ByteArray(totalSize)
        
        var offset = 0
        recordedData.forEach { chunk ->
            System.arraycopy(chunk, 0, currentRecordedData!!, offset, chunk.size)
            offset += chunk.size
        }
        
        Log.d(TAG, "Recording completed. Total size: $totalSize bytes")
    }
    
    /**
     * Calculate audio level for visual feedback
     */
    private fun calculateAudioLevel(buffer: ByteArray): Float {
        var sum = 0.0
        for (i in buffer.indices step 2) {
            if (i + 1 < buffer.size) {
                val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
                sum += sample * sample
            }
        }
        
        val rms = kotlin.math.sqrt(sum / (buffer.size / 2))
        return (rms / 32768.0).toFloat() // Normalize to 0-1 range
    }
    
    /**
     * Play voice data through speaker/earpiece
     */
    suspend fun playVoiceData(voiceData: VoiceData): Boolean = withContext(Dispatchers.IO) {
        try {
            if (_isPlaying.value) {
                Log.w(TAG, "Already playing audio")
                return@withContext false
            }
            
            _isPlaying.value = true
            
            // Initialize AudioTrack for playback
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(voiceData.sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(playbackBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
            
            // Play audio data in chunks
            val chunkSize = 1024
            var offset = 0
            
            while (offset < voiceData.audioData.size && _isPlaying.value) {
                val remainingBytes = voiceData.audioData.size - offset
                val bytesToWrite = minOf(chunkSize, remainingBytes)
                
                val bytesWritten = audioTrack?.write(
                    voiceData.audioData, offset, bytesToWrite
                ) ?: 0
                
                if (bytesWritten > 0) {
                    offset += bytesWritten
                } else {
                    break
                }
                
                delay(10) // Small delay for smooth playback
            }
            
            // Wait for playback to finish
            audioTrack?.stop()
            _isPlaying.value = false
            
            Log.d(TAG, "Playback completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play voice data", e)
            _isPlaying.value = false
            stopPlayback()
            false
        }
    }
    
    /**
     * Play voice data from byte array (retrieved from blockchain)
     */
    suspend fun playVoiceDataFromBytes(audioData: ByteArray): Boolean {
        val voiceData = VoiceData(
            audioData = audioData,
            timestamp = System.currentTimeMillis(),
            duration = audioData.size.toLong() / (sampleRate * 2) * 1000, // Estimate duration
            sampleRate = sampleRate
        )
        return playVoiceData(voiceData)
    }
    
    /**
     * Stop current playback
     */
    fun stopPlayback() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            _isPlaying.value = false
            
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }
    
    /**
     * Save voice data to file (for debugging or caching)
     */
    suspend fun saveVoiceDataToFile(voiceData: VoiceData, filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "$filename.pcm")
            FileOutputStream(file).use { fos ->
                fos.write(voiceData.audioData)
            }
            
            Log.d(TAG, "Voice data saved to: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save voice data", e)
            false
        }
    }
    
    /**
     * Load voice data from file
     */
    suspend fun loadVoiceDataFromFile(filename: String): VoiceData? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "$filename.pcm")
            if (!file.exists()) {
                Log.w(TAG, "File not found: ${file.absolutePath}")
                return@withContext null
            }
            
            val audioData = FileInputStream(file).use { fis ->
                fis.readBytes()
            }
            
            VoiceData(
                audioData = audioData,
                timestamp = file.lastModified(),
                duration = audioData.size.toLong() / (sampleRate * 2) * 1000,
                sampleRate = sampleRate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load voice data from file", e)
            null
        }
    }
    
    /**
     * Convert audio data to compressed format (simplified)
     * In production, implement proper audio compression
     */
    fun compressAudioData(voiceData: VoiceData): ByteArray {
        // Simplified compression - just downsample
        // In production, use proper audio codec like Opus
        val compressionRatio = 4
        val compressed = ByteArray(voiceData.audioData.size / compressionRatio)
        
        for (i in compressed.indices) {
            compressed[i] = voiceData.audioData[i * compressionRatio]
        }
        
        return compressed
    }
    
    /**
     * Decompress audio data (simplified)
     */
    fun decompressAudioData(compressedData: ByteArray): ByteArray {
        // Simplified decompression - just upsample with interpolation
        val compressionRatio = 4
        val decompressed = ByteArray(compressedData.size * compressionRatio)
        
        for (i in compressedData.indices) {
            val baseIndex = i * compressionRatio
            val sample = compressedData[i]
            
            // Simple interpolation
            for (j in 0 until compressionRatio) {
                if (baseIndex + j < decompressed.size) {
                    decompressed[baseIndex + j] = sample
                }
            }
        }
        
        return decompressed
    }
    
    /**
     * Check if microphone permission is granted
     */
    fun hasMicrophonePermission(): Boolean {
        return try {
            val testRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            val hasPermission = testRecord.state == AudioRecord.STATE_INITIALIZED
            testRecord.release()
            hasPermission
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            recordingJob?.cancel()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            
            _recordingDuration.value = 0L
            _audioLevel.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        cleanup()
        mediaPlayer?.release()
        mediaPlayer = null
        
        Log.d(TAG, "Audio manager released")
    }
    
    /**
     * Get current recording duration
     */
    fun getCurrentRecordingDuration(): Long {
        return if (_isRecording.value) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0L
        }
    }
}
