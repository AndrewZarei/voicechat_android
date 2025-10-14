package com.example.solvoice.services

import android.util.Log
import com.example.solvoice.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlin.random.Random

/**
 * Voice room manager that coordinates voice chat operations
 */
class VoiceRoomManager(
    private val solanaClient: SolanaClient,
    private val storageManager: StorageManager,
    private val audioManager: VoiceChatAudioManager
) {
    
    private val TAG = "VoiceRoomManager"
    
    // Current room state
    private val _currentRoom = MutableStateFlow<VoiceRoom?>(null)
    val currentRoom: StateFlow<VoiceRoom?> = _currentRoom
    
    private val _isInRoom = MutableStateFlow(false)
    val isInRoom: StateFlow<Boolean> = _isInRoom
    
    private val _roomParticipants = MutableStateFlow<List<String>>(emptyList())
    val roomParticipants: StateFlow<List<String>> = _roomParticipants
    
    // Voice chat state
    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting
    
    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving
    
    private val _voiceMessages = MutableStateFlow<List<VoiceMessage>>(emptyList())
    val voiceMessages: StateFlow<List<VoiceMessage>> = _voiceMessages
    
    // Room management
    private val activeRooms = mutableMapOf<String, VoiceRoom>()
    private var sequenceNumber = Random.nextLong(1000000)
    
    // Auto-polling for new messages
    private var messagePollingJob: Job? = null
    
    /**
     * Create a new voice room
     */
    suspend fun createVoiceRoom(roomId: String? = null): Result<VoiceRoom> = withContext(Dispatchers.IO) {
        try {
            val finalRoomId = roomId ?: generateRoomId()
            Log.d(TAG, "Creating voice room: $finalRoomId")
            
            val result = solanaClient.createVoiceRoom(finalRoomId)
            
            when (result) {
                is Result.Success -> {
                    val room = result.data
                    _currentRoom.value = room
                    _isInRoom.value = true
                    activeRooms[room.roomId] = room
                    
                    // Add creator as first participant
                    val userPubkey = solanaClient.getUserPublicKey()
                    if (userPubkey != null) {
                        _roomParticipants.value = listOf(userPubkey)
                    }
                    
                    Log.d(TAG, "Voice room created successfully: ${room.roomId}")
                    Result.Success(room)
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to create voice room", result.exception)
                    Result.Error(result.exception)
                }
                else -> Result.Error(Exception("Unknown error creating room"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating voice room", e)
            Result.Error(e)
        }
    }
    
    /**
     * Join an existing voice room
     */
    suspend fun joinVoiceRoom(roomId: String): Result<VoiceRoom> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Joining voice room: $roomId")
            
            // Check if room exists in active rooms or fetch from blockchain
            val room = activeRooms[roomId] ?: VoiceRoom(
                roomId = roomId,
                host = "", // Will be populated from blockchain
                participantCount = 0,
                isActive = true,
                createdAt = System.currentTimeMillis() / 1000,
                lastActivity = System.currentTimeMillis() / 1000,
                address = null // Will be derived
            )
            
            _currentRoom.value = room
            _isInRoom.value = true
            activeRooms[roomId] = room
            
            // Start message polling
            startMessagePolling()
            
            Log.d(TAG, "Joined voice room: $roomId")
            Result.Success(room)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining voice room", e)
            Result.Error(e)
        }
    }
    
    /**
     * Leave current voice room
     */
    suspend fun leaveVoiceRoom(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val room = _currentRoom.value
            if (room == null) {
                return@withContext Result.Error(Exception("Not in any room"))
            }
            
            Log.d(TAG, "Leaving voice room: ${room.roomId}")
            
            // Stop any ongoing operations
            stopMessagePolling()
            
            // Clear room state
            _currentRoom.value = null
            _isInRoom.value = false
            _roomParticipants.value = emptyList()
            _voiceMessages.value = emptyList()
            
            Log.d(TAG, "Left voice room successfully")
            Result.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving voice room", e)
            Result.Error(e)
        }
    }
    
    /**
     * Start voice recording and transmission
     */
    suspend fun startVoiceTransmission(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val room = _currentRoom.value
            if (room == null) {
                return@withContext Result.Error(Exception("Not in any room"))
            }
            
            if (_isTransmitting.value) {
                return@withContext Result.Error(Exception("Already transmitting"))
            }
            
            Log.d(TAG, "Starting voice transmission")
            
            val recordingStarted = audioManager.startRecording()
            if (recordingStarted) {
                _isTransmitting.value = true
                Log.d(TAG, "Voice transmission started")
                Result.Success(true)
            } else {
                Result.Error(Exception("Failed to start audio recording"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice transmission", e)
            Result.Error(e)
        }
    }
    
    /**
     * Stop voice recording and send to blockchain
     */
    suspend fun stopVoiceTransmission(): Result<VoiceMessage?> = withContext(Dispatchers.IO) {
        try {
            val room = _currentRoom.value
            if (room == null) {
                return@withContext Result.Error(Exception("Not in any room"))
            }
            
            if (!_isTransmitting.value) {
                return@withContext Result.Error(Exception("Not currently transmitting"))
            }
            
            Log.d(TAG, "Stopping voice transmission")
            
            // Stop recording and get voice data
            val voiceData = audioManager.stopRecording()
            _isTransmitting.value = false
            
            if (voiceData == null) {
                return@withContext Result.Error(Exception("No voice data recorded"))
            }
            
            // Send voice data to storage
            val result = storageManager.storeVoiceData(
                voiceRoom = room,
                voiceData = voiceData,
                sequenceNumber = getNextSequenceNumber()
            )
            
            when (result) {
                is Result.Success -> {
                    val voiceMessage = result.data
                    
                    // Add to local messages
                    val currentMessages = _voiceMessages.value.toMutableList()
                    currentMessages.add(voiceMessage)
                    _voiceMessages.value = currentMessages
                    
                    Log.d(TAG, "Voice transmission sent successfully")
                    Result.Success(voiceMessage)
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to send voice data", result.exception)
                    Result.Error(result.exception)
                }
                else -> Result.Error(Exception("Unknown error sending voice data"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice transmission", e)
            Result.Error(e)
        }
    }
    
    /**
     * Play voice message from storage
     */
    suspend fun playVoiceMessage(voiceMessage: VoiceMessage): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Playing voice message from PDA ${voiceMessage.storagePdaIndex}")
            
            _isReceiving.value = true
            
            // Retrieve voice data from storage
            val result = storageManager.retrieveVoiceData(voiceMessage.storagePdaIndex)
            
            when (result) {
                is Result.Success -> {
                    val voiceData = result.data
                    
                    // Play the voice data
                    val playbackResult = audioManager.playVoiceData(voiceData)
                    _isReceiving.value = false
                    
                    if (playbackResult) {
                        Log.d(TAG, "Voice message played successfully")
                        Result.Success(true)
                    } else {
                        Result.Error(Exception("Failed to play voice data"))
                    }
                }
                is Result.Error -> {
                    _isReceiving.value = false
                    Log.e(TAG, "Failed to retrieve voice data", result.exception)
                    Result.Error(result.exception)
                }
                else -> {
                    _isReceiving.value = false
                    Result.Error(Exception("Unknown error retrieving voice data"))
                }
            }
        } catch (e: Exception) {
            _isReceiving.value = false
            Log.e(TAG, "Error playing voice message", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get all voice messages in current room
     */
    fun getVoiceMessages(): List<VoiceMessage> {
        return _voiceMessages.value
    }
    
    /**
     * Send a quick voice message (record, stop, send in one operation)
     */
    suspend fun sendQuickVoiceMessage(durationMs: Long = 5000): Result<VoiceMessage?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Recording quick voice message for ${durationMs}ms")
            
            // Start recording
            val startResult = startVoiceTransmission()
            if (startResult !is Result.Success) {
                return@withContext startResult as Result<VoiceMessage?>
            }
            
            // Wait for specified duration
            delay(durationMs)
            
            // Stop and send
            stopVoiceTransmission()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending quick voice message", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get room statistics
     */
    fun getRoomStatistics(): RoomStatistics? {
        val room = _currentRoom.value ?: return null
        val messages = _voiceMessages.value
        
        return RoomStatistics(
            roomId = room.roomId,
            participantCount = room.participantCount,
            messageCount = messages.size,
            totalVoiceData = messages.sumOf { it.dataLength },
            roomDuration = System.currentTimeMillis() / 1000 - room.createdAt,
            isActive = room.isActive
        )
    }
    
    /**
     * Start polling for new messages in the room
     */
    private fun startMessagePolling() {
        messagePollingJob?.cancel()
        messagePollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && _isInRoom.value) {
                try {
                    // In production, implement message polling from blockchain
                    delay(5000) // Poll every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in message polling", e)
                }
            }
        }
    }
    
    /**
     * Stop message polling
     */
    private fun stopMessagePolling() {
        messagePollingJob?.cancel()
        messagePollingJob = null
    }
    
    /**
     * Generate unique room ID
     */
    private fun generateRoomId(): String {
        return "room-${System.currentTimeMillis()}-${Random.nextInt(1000)}"
    }
    
    /**
     * Get next sequence number for messages
     */
    private fun getNextSequenceNumber(): Long {
        return ++sequenceNumber
    }
    
    /**
     * Check if user has microphone permission
     */
    fun hasMicrophonePermission(): Boolean {
        return audioManager.hasMicrophonePermission()
    }
    
    /**
     * Get current room info
     */
    fun getCurrentRoomInfo(): VoiceRoom? = _currentRoom.value
    
    /**
     * Check if system is ready for voice chat
     */
    fun isSystemReady(): Boolean {
        return storageManager.isInitialized.value && solanaClient.getUserPublicKey() != null
    }
    
    /**
     * Get voice chat state summary
     */
    fun getVoiceChatState(): VoiceChatState {
        return VoiceChatState(
            isSystemInitialized = storageManager.isInitialized.value,
            storagePDAs = storageManager.storagePDAs.value,
            currentRoom = _currentRoom.value,
            isRecording = audioManager.isRecording.value,
            isPlaying = audioManager.isPlaying.value,
            isConnectedToSolana = solanaClient.getUserPublicKey() != null,
            userWalletAddress = solanaClient.getUserPublicKey()
        )
    }
    
    /**
     * Emergency stop all operations
     */
    fun emergencyStop() {
        try {
            // Stop all audio operations
            audioManager.stopPlayback()
            
            // Stop message polling
            stopMessagePolling()
            
            // Reset transmission states
            _isTransmitting.value = false
            _isReceiving.value = false
            
            Log.d(TAG, "Emergency stop completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during emergency stop", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        emergencyStop()
        storageManager.reset()
        
        // Clear all state
        _currentRoom.value = null
        _isInRoom.value = false
        _roomParticipants.value = emptyList()
        _voiceMessages.value = emptyList()
        
        activeRooms.clear()
        
        Log.d(TAG, "Voice room manager cleaned up")
    }
}

/**
 * Room statistics data class
 */
data class RoomStatistics(
    val roomId: String,
    val participantCount: Int,
    val messageCount: Int,
    val totalVoiceData: Long,
    val roomDuration: Long, // in seconds
    val isActive: Boolean
)
