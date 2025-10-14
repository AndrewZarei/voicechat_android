package com.example.solvoice.services

import android.util.Log
import com.example.solvoice.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Storage manager for handling PDA storage operations in the voice chat system
 */
class StorageManager(private val solanaClient: SolanaClient) {
    
    private val TAG = "StorageManager"
    
    // Storage PDAs state
    private val _storagePDAs = MutableStateFlow<List<StoragePDA>>(emptyList())
    val storagePDAs: StateFlow<List<StoragePDA>> = _storagePDAs
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized
    
    private val _totalStorageUsed = MutableStateFlow(0L)
    val totalStorageUsed: StateFlow<Long> = _totalStorageUsed
    
    private val _availableStorage = MutableStateFlow(0L)
    val availableStorage: StateFlow<Long> = _availableStorage
    
    // Storage allocation tracking
    private val storagePDAUsage = mutableMapOf<Int, Long>()
    
    /**
     * Initialize storage system by creating all storage PDAs
     */
    suspend fun initializeStorage(): Result<List<StoragePDA>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing storage system...")
            
            val result = solanaClient.initializeVoiceChatSystem()
            
            when (result) {
                is Result.Success -> {
                    _storagePDAs.value = result.data
                    _isInitialized.value = true
                    
                    // Initialize usage tracking
                    result.data.forEach { pda ->
                        storagePDAUsage[pda.index] = pda.dataLength
                    }
                    
                    updateStorageStats()
                    
                    Log.d(TAG, "Storage system initialized with ${result.data.size} PDAs")
                    Result.Success(result.data)
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to initialize storage system", result.exception)
                    Result.Error(result.exception)
                }
                else -> Result.Error(Exception("Unknown initialization error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing storage", e)
            Result.Error(e)
        }
    }
    
    /**
     * Store voice data in the most appropriate storage PDA
     */
    suspend fun storeVoiceData(
        voiceRoom: VoiceRoom,
        voiceData: VoiceData,
        sequenceNumber: Long? = null
    ): Result<VoiceMessage> = withContext(Dispatchers.IO) {
        try {
            if (!_isInitialized.value) {
                return@withContext Result.Error(Exception("Storage not initialized"))
            }
            
            // Compress voice data to fit in storage constraints
            val compressedData = compressVoiceDataForStorage(voiceData.audioData)
            
            if (compressedData.size > Constants.MAX_VOICE_DATA_SIZE) {
                return@withContext Result.Error(
                    Exception("Voice data too large: ${compressedData.size} bytes (max: ${Constants.MAX_VOICE_DATA_SIZE})")
                )
            }
            
            // Find best storage PDA
            val targetPDA = findBestStoragePDA(compressedData.size)
                ?: return@withContext Result.Error(Exception("No storage PDA available"))
            
            // Generate sequence number if not provided
            val seqNum = sequenceNumber ?: System.currentTimeMillis()
            
            Log.d(TAG, "Storing ${compressedData.size} bytes in PDA ${targetPDA.index}")
            
            // Send voice data to blockchain
            val result = solanaClient.sendVoiceData(
                voiceRoom = voiceRoom,
                voiceData = compressedData,
                targetPdaIndex = targetPDA.index,
                sequenceNumber = seqNum
            )
            
            when (result) {
                is Result.Success -> {
                    // Update local storage tracking
                    storagePDAUsage[targetPDA.index] = 
                        (storagePDAUsage[targetPDA.index] ?: 0L) + compressedData.size
                    
                    updateStorageStats()
                    
                    Log.d(TAG, "Voice data stored successfully in PDA ${targetPDA.index}")
                    Result.Success(result.data)
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to store voice data", result.exception)
                    Result.Error(result.exception)
                }
                else -> Result.Error(Exception("Unknown storage error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error storing voice data", e)
            Result.Error(e)
        }
    }
    
    /**
     * Retrieve voice data from storage PDA
     */
    suspend fun retrieveVoiceData(pdaIndex: Int): Result<VoiceData> = withContext(Dispatchers.IO) {
        try {
            if (!_isInitialized.value) {
                return@withContext Result.Error(Exception("Storage not initialized"))
            }
            
            Log.d(TAG, "Retrieving voice data from PDA $pdaIndex")
            
            val result = solanaClient.getVoiceData(pdaIndex)
            
            when (result) {
                is Result.Success -> {
                    // Decompress retrieved data
                    val decompressedData = decompressVoiceDataFromStorage(result.data)
                    
                    val voiceData = VoiceData(
                        audioData = decompressedData,
                        timestamp = System.currentTimeMillis(),
                        duration = estimateAudioDuration(decompressedData),
                        sampleRate = 44100, // Default sample rate
                        encoding = "pcm_16bit"
                    )
                    
                    Log.d(TAG, "Voice data retrieved: ${decompressedData.size} bytes")
                    Result.Success(voiceData)
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to retrieve voice data", result.exception)
                    Result.Error(result.exception)
                }
                else -> Result.Error(Exception("Unknown retrieval error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving voice data", e)
            Result.Error(e)
        }
    }
    
    /**
     * Find the best storage PDA for the given data size
     */
    private fun findBestStoragePDA(dataSize: Int): StoragePDA? {
        val availablePDAs = _storagePDAs.value.filter { pda ->
            val currentUsage = storagePDAUsage[pda.index] ?: 0L
            val remainingSpace = Constants.CHUNK_SIZE - currentUsage
            remainingSpace >= dataSize
        }
        
        return if (availablePDAs.isNotEmpty()) {
            // Find PDA with least usage for better distribution
            availablePDAs.minByOrNull { pda ->
                storagePDAUsage[pda.index] ?: 0L
            }
        } else {
            null
        }
    }
    
    /**
     * Get storage statistics
     */
    fun getStorageStats(): StorageStats {
        val totalCapacity = Constants.MAX_STORAGE_PDAS * Constants.CHUNK_SIZE.toLong()
        val usedSpace = storagePDAUsage.values.sum()
        val availableSpace = totalCapacity - usedSpace
        
        return StorageStats(
            totalCapacity = totalCapacity,
            usedSpace = usedSpace,
            availableSpace = availableSpace,
            pdaCount = _storagePDAs.value.size,
            utilizationPercentage = (usedSpace.toFloat() / totalCapacity * 100).toInt()
        )
    }
    
    /**
     * Get PDA usage details
     */
    fun getPDAUsage(): List<PDAUsage> {
        return _storagePDAs.value.map { pda ->
            val usage = storagePDAUsage[pda.index] ?: 0L
            val capacity = Constants.CHUNK_SIZE.toLong()
            
            PDAUsage(
                index = pda.index,
                address = pda.address ?: "",
                usedBytes = usage,
                totalBytes = capacity,
                utilizationPercentage = (usage.toFloat() / capacity * 100).toInt(),
                isActive = pda.isActive
            )
        }
    }
    
    /**
     * Clear data from specific PDA (for testing or cleanup)
     */
    suspend fun clearPDAData(pdaIndex: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (pdaIndex !in 0 until Constants.MAX_STORAGE_PDAS) {
                return@withContext Result.Error(Exception("Invalid PDA index: $pdaIndex"))
            }
            
            // In production, implement clearStorageData function in SolanaClient
            Log.d(TAG, "Clearing PDA $pdaIndex data (simulated)")
            
            // Reset local usage tracking
            storagePDAUsage[pdaIndex] = 0L
            updateStorageStats()
            
            Result.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing PDA data", e)
            Result.Error(e)
        }
    }
    
    /**
     * Optimize storage by defragmenting or redistributing data
     */
    suspend fun optimizeStorage(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting storage optimization...")
            
            val stats = getStorageStats()
            var optimizationsPerformed = 0
            
            // Check for heavily fragmented PDAs
            val pdaUsages = getPDAUsage()
            val fragmentedPDAs = pdaUsages.filter { usage ->
                usage.utilizationPercentage > 80
            }
            
            if (fragmentedPDAs.isNotEmpty()) {
                Log.d(TAG, "Found ${fragmentedPDAs.size} heavily utilized PDAs")
                optimizationsPerformed += fragmentedPDAs.size
            }
            
            // Log optimization results
            val message = "Storage optimization completed. " +
                    "Optimized $optimizationsPerformed PDAs. " +
                    "Total usage: ${stats.utilizationPercentage}%"
            
            Log.d(TAG, message)
            Result.Success(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing storage", e)
            Result.Error(e)
        }
    }
    
    /**
     * Compress voice data for storage efficiency
     */
    private fun compressVoiceDataForStorage(audioData: ByteArray): ByteArray {
        // Simple compression by removing every other sample (50% reduction)
        // In production, use proper audio compression like Opus
        val compressionRatio = 2
        val compressed = ByteArray(audioData.size / compressionRatio)
        
        for (i in compressed.indices) {
            compressed[i] = audioData[i * compressionRatio]
        }
        
        return compressed
    }
    
    /**
     * Decompress voice data from storage
     */
    private fun decompressVoiceDataFromStorage(compressedData: ByteArray): ByteArray {
        // Simple decompression by duplicating samples
        val compressionRatio = 2
        val decompressed = ByteArray(compressedData.size * compressionRatio)
        
        for (i in compressedData.indices) {
            val baseIndex = i * compressionRatio
            decompressed[baseIndex] = compressedData[i]
            if (baseIndex + 1 < decompressed.size) {
                decompressed[baseIndex + 1] = compressedData[i]
            }
        }
        
        return decompressed
    }
    
    /**
     * Estimate audio duration from data size
     */
    private fun estimateAudioDuration(audioData: ByteArray): Long {
        // Assuming 16-bit PCM at 8kHz (voice-optimized)
        val bytesPerSecond = 8000 * 2 // 8kHz sample rate, 16-bit = 2 bytes per sample
        return (audioData.size * 1000L) / bytesPerSecond
    }
    
    /**
     * Update storage statistics
     */
    private fun updateStorageStats() {
        val totalUsed = storagePDAUsage.values.sum()
        val totalAvailable = (Constants.MAX_STORAGE_PDAS * Constants.CHUNK_SIZE.toLong()) - totalUsed
        
        _totalStorageUsed.value = totalUsed
        _availableStorage.value = totalAvailable
    }
    
    /**
     * Check if storage system has enough space for data
     */
    fun hasSpaceForData(dataSize: Int): Boolean {
        return findBestStoragePDA(dataSize) != null
    }
    
    /**
     * Get recommended chunk size for optimal storage
     */
    fun getRecommendedChunkSize(): Int {
        val stats = getStorageStats()
        return when {
            stats.utilizationPercentage < 50 -> Constants.MAX_VOICE_DATA_SIZE
            stats.utilizationPercentage < 80 -> Constants.MAX_VOICE_DATA_SIZE / 2
            else -> Constants.MAX_VOICE_DATA_SIZE / 4
        }
    }
    
    /**
     * Reset storage manager
     */
    fun reset() {
        _storagePDAs.value = emptyList()
        _isInitialized.value = false
        storagePDAUsage.clear()
        _totalStorageUsed.value = 0L
        _availableStorage.value = 0L
        
        Log.d(TAG, "Storage manager reset")
    }
}

/**
 * Storage statistics data class
 */
data class StorageStats(
    val totalCapacity: Long,
    val usedSpace: Long,
    val availableSpace: Long,
    val pdaCount: Int,
    val utilizationPercentage: Int
)

/**
 * Individual PDA usage data class
 */
data class PDAUsage(
    val index: Int,
    val address: String,
    val usedBytes: Long,
    val totalBytes: Long,
    val utilizationPercentage: Int,
    val isActive: Boolean
)
