package com.example.solvoice.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.solvoice.models.*
import com.example.solvoice.services.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the voice chat application
 */
class VoiceChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "VoiceChatViewModel"
    
    // Services
    private val solanaClient = SolanaClient()
    private val audioManager = VoiceChatAudioManager(application)
    private val storageManager = StorageManager(solanaClient)
    private val voiceRoomManager = VoiceRoomManager(solanaClient, storageManager, audioManager)
    
    // UI State
    private val _uiState = MutableStateFlow(VoiceChatUiState())
    val uiState: StateFlow<VoiceChatUiState> = _uiState.asStateFlow()
    
    // Error handling
    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()
    
    // Success messages
    private val _successMessages = MutableSharedFlow<String>()
    val successMessages: SharedFlow<String> = _successMessages.asSharedFlow()
    
    init {
        initializeServices()
        observeSystemState()
    }
    
    /**
     * Initialize all services
     */
    private fun initializeServices() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, status = "Initializing services...") }
                
                // Initialize Solana client
                solanaClient.initialize()
                
                // Initialize audio manager
                audioManager.initialize()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        status = "Services initialized. Ready to start.",
                        isSystemInitialized = true,
                        userWalletAddress = solanaClient.getUserPublicKey()
                    )
                }
                
                Log.d(TAG, "Services initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing services", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = "Failed to initialize: ${e.message}",
                        status = "Initialization failed"
                    ) 
                }
                _errorMessages.emit("Failed to initialize services: ${e.message}")
            }
        }
    }
    
    /**
     * Observe system state changes
     */
    private fun observeSystemState() {
        viewModelScope.launch {
            // Observe storage PDAs
            storageManager.storagePDAs.collect { pdas ->
                _uiState.update { 
                    it.copy(
                        storagePDAs = pdas,
                        totalStorage = "${pdas.size * 30}KB"
                    ) 
                }
            }
        }
        
        viewModelScope.launch {
            // Observe current room
            voiceRoomManager.currentRoom.collect { room ->
                _uiState.update { 
                    it.copy(
                        currentRoom = room,
                        isInRoom = room != null,
                        roomId = room?.roomId ?: "Not in room"
                    ) 
                }
            }
        }
        
        viewModelScope.launch {
            // Observe recording state
            audioManager.isRecording.collect { isRecording ->
                _uiState.update { 
                    it.copy(
                        isRecording = isRecording,
                        recordingStatus = if (isRecording) "Recording..." else "Not recording"
                    ) 
                }
            }
        }
        
        viewModelScope.launch {
            // Observe playback state
            audioManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }
        
        viewModelScope.launch {
            // Observe recording duration
            audioManager.recordingDuration.collect { duration ->
                _uiState.update { 
                    it.copy(recordingDuration = formatDuration(duration))
                }
            }
        }
        
        viewModelScope.launch {
            // Observe audio level
            audioManager.audioLevel.collect { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }
    }
    
    /**
     * Initialize the voice chat system (create storage PDAs)
     */
    fun initializeVoiceChatSystem() {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isLoading = true, 
                        status = "Creating storage PDAs..."
                    ) 
                }
                
                val result = storageManager.initializeStorage()
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                isStorageInitialized = true,
                                status = "Storage initialized! ${result.data.size} PDAs created."
                            ) 
                        }
                        _successMessages.emit("Voice chat system initialized successfully!")
                    }
                    is Result.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = result.exception.message,
                                status = "Failed to initialize storage"
                            ) 
                        }
                        _errorMessages.emit("Failed to initialize system: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message,
                        status = "Initialization error"
                    ) 
                }
                _errorMessages.emit("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Create a voice room
     */
    fun createVoiceRoom(roomId: String = "") {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isLoading = true,
                        status = "Creating voice room..."
                    ) 
                }
                
                val result = voiceRoomManager.createVoiceRoom(roomId.ifEmpty { null })
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                status = "Voice room created: ${result.data.roomId}"
                            ) 
                        }
                        _successMessages.emit("Voice room '${result.data.roomId}' created successfully!")
                    }
                    is Result.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = result.exception.message,
                                status = "Failed to create room"
                            ) 
                        }
                        _errorMessages.emit("Failed to create room: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message,
                        status = "Room creation error"
                    ) 
                }
                _errorMessages.emit("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Join a voice room
     */
    fun joinVoiceRoom(roomId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isLoading = true,
                        status = "Joining voice room..."
                    ) 
                }
                
                val result = voiceRoomManager.joinVoiceRoom(roomId)
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                status = "Joined room: ${result.data.roomId}"
                            ) 
                        }
                        _successMessages.emit("Joined room '${result.data.roomId}' successfully!")
                    }
                    is Result.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = result.exception.message,
                                status = "Failed to join room"
                            ) 
                        }
                        _errorMessages.emit("Failed to join room: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message,
                        status = "Join room error"
                    ) 
                }
                _errorMessages.emit("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Leave current voice room
     */
    fun leaveVoiceRoom() {
        viewModelScope.launch {
            try {
                val result = voiceRoomManager.leaveVoiceRoom()
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(status = "Left voice room")
                        }
                        _successMessages.emit("Left voice room successfully")
                    }
                    is Result.Error -> {
                        _errorMessages.emit("Failed to leave room: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessages.emit("Error leaving room: ${e.message}")
            }
        }
    }
    
    /**
     * Start voice recording
     */
    fun startRecording() {
        if (!voiceRoomManager.hasMicrophonePermission()) {
            viewModelScope.launch {
                _errorMessages.emit("Microphone permission required")
            }
            return
        }
        
        viewModelScope.launch {
            try {
                val result = voiceRoomManager.startVoiceTransmission()
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(status = "Recording started...")
                        }
                    }
                    is Result.Error -> {
                        _errorMessages.emit("Failed to start recording: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessages.emit("Recording error: ${e.message}")
            }
        }
    }
    
    /**
     * Stop voice recording and send
     */
    fun stopRecording() {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(status = "Sending voice data...")
                }
                
                val result = voiceRoomManager.stopVoiceTransmission()
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                status = "Voice message sent!",
                                lastSentTime = getCurrentTime()
                            )
                        }
                        _successMessages.emit("Voice message sent successfully!")
                    }
                    is Result.Error -> {
                        _uiState.update { 
                            it.copy(status = "Failed to send voice data")
                        }
                        _errorMessages.emit("Failed to send voice: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessages.emit("Error sending voice: ${e.message}")
            }
        }
    }
    
    /**
     * Play voice messages from the room
     */
    fun playVoiceMessages() {
        viewModelScope.launch {
            try {
                val messages = voiceRoomManager.getVoiceMessages()
                
                if (messages.isEmpty()) {
                    _errorMessages.emit("No voice messages to play")
                    return@launch
                }
                
                _uiState.update { 
                    it.copy(status = "Playing voice messages...")
                }
                
                // Play the most recent message
                val latestMessage = messages.last()
                val result = voiceRoomManager.playVoiceMessage(latestMessage)
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(status = "Playing voice message...")
                        }
                        _successMessages.emit("Playing voice message")
                    }
                    is Result.Error -> {
                        _errorMessages.emit("Failed to play voice: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessages.emit("Playback error: ${e.message}")
            }
        }
    }
    
    /**
     * Send a quick voice message (5 seconds)
     */
    fun sendQuickVoiceMessage() {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(status = "Recording quick message...")
                }
                
                val result = voiceRoomManager.sendQuickVoiceMessage(5000)
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                status = "Quick message sent!",
                                lastSentTime = getCurrentTime()
                            )
                        }
                        _successMessages.emit("Quick voice message sent!")
                    }
                    is Result.Error -> {
                        _errorMessages.emit("Failed to send quick message: ${result.exception.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessages.emit("Quick message error: ${e.message}")
            }
        }
    }
    
    /**
     * Get storage statistics
     */
    fun getStorageStats(): StorageStats? {
        return try {
            storageManager.getStorageStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage stats", e)
            null
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Emergency stop all operations
     */
    fun emergencyStop() {
        voiceRoomManager.emergencyStop()
        _uiState.update { 
            it.copy(
                isRecording = false,
                isPlaying = false,
                status = "All operations stopped"
            )
        }
    }
    
    /**
     * Format duration in milliseconds to MM:SS
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 60000)
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * Get current time formatted
     */
    private fun getCurrentTime(): String {
        val now = System.currentTimeMillis()
        val date = java.util.Date(now)
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(date)
    }
    
    override fun onCleared() {
        super.onCleared()
        voiceRoomManager.cleanup()
        audioManager.release()
        Log.d(TAG, "ViewModel cleared")
    }
}

/**
 * UI state for the voice chat application
 */
data class VoiceChatUiState(
    val isLoading: Boolean = false,
    val isSystemInitialized: Boolean = false,
    val isStorageInitialized: Boolean = false,
    val isInRoom: Boolean = false,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val status: String = "Welcome to Voice Chat dApp",
    val error: String? = null,
    val userWalletAddress: String? = null,
    val roomId: String = "Not in room",
    val participantCount: Int = 0,
    val recordingStatus: String = "Ready",
    val recordingDuration: String = "00:00",
    val audioLevel: Float = 0f,
    val lastSentTime: String = "Never",
    val totalStorage: String = "0KB",
    val storagePDAs: List<StoragePDA> = emptyList(),
    val currentRoom: VoiceRoom? = null
)
