package com.example.solvoice.models

import kotlinx.datetime.Instant

/**
 * Data models representing the smart contract structures
 * These match the Rust structs from the smart contracts
 */

/**
 * Represents a PDA Account from the voicechat contract
 * Rust struct: PDAAccount
 */
data class PDAAccount(
    val index: Int,
    val authority: String,
    val createdAt: Long,
    val dataLength: Long,
    val address: String? = null
)

/**
 * Represents a Storage PDA from the storage_manager contract
 * Rust struct: StoragePDA
 */
data class StoragePDA(
    val index: Int,
    val authority: String,
    val createdAt: Long,
    val dataLength: Long,
    val isActive: Boolean,
    val address: String? = null
)

/**
 * Represents a Voice Room from the voice_chat_manager contract
 * Rust struct: VoiceRoom
 */
data class VoiceRoom(
    val roomId: String,
    val host: String,
    val participantCount: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val lastActivity: Long,
    val address: String? = null
)

/**
 * Represents a Voice Message from the voice_chat_manager contract
 * Rust struct: VoiceMessage
 */
data class VoiceMessage(
    val sender: String,
    val roomId: String,
    val storagePdaIndex: Int,
    val sequenceNumber: Long,
    val dataLength: Long,
    val timestamp: Long,
    val address: String? = null
)

/**
 * Represents a Broadcast Message from the voice_chat_manager contract
 * Rust struct: BroadcastMessage
 */
data class BroadcastMessage(
    val sender: String,
    val roomId: String,
    val targetPdas: List<Int>,
    val sequenceNumber: Long,
    val dataLength: Long,
    val timestamp: Long,
    val address: String? = null
)

/**
 * Configuration for the storage system
 * Rust struct: StorageConfig
 */
data class StorageConfig(
    val authority: String,
    val totalPdas: Int,
    val createdAt: Long,
    val address: String? = null
)

/**
 * Represents voice data ready for transmission
 */
data class VoiceData(
    val audioData: ByteArray,
    val timestamp: Long,
    val duration: Long,
    val sampleRate: Int = 8000,  // 8kHz optimized for voice chat storage
    val encoding: String = "pcm_16bit"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoiceData

        if (!audioData.contentEquals(other.audioData)) return false
        if (timestamp != other.timestamp) return false
        if (duration != other.duration) return false
        if (sampleRate != other.sampleRate) return false
        if (encoding != other.encoding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + encoding.hashCode()
        return result
    }
}

/**
 * UI state for the application
 */
data class VoiceChatState(
    val isSystemInitialized: Boolean = false,
    val storagePDAs: List<StoragePDA> = emptyList(),
    val currentRoom: VoiceRoom? = null,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val recordedVoiceData: VoiceData? = null,
    val isConnectedToSolana: Boolean = false,
    val userWalletAddress: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    val lastOperation: String? = null
)

/**
 * Smart contract program IDs (matching Anchor.toml)
 */
object ProgramIds {
    const val VOICECHAT = "HPxbCqRWpSxCEE2L6Vy1S1oMTc3D9aknrBGwZ9WTAvSK"
    const val STORAGE_MANAGER = "SU6CRGJXz5ksvXPyUuWXYfW2qmba6ZgHa3sxdr9aYMz"
    const val VOICE_CHAT_MANAGER = "GVqX9pcoxbiY7i1W3Ad6Sinw1pNpwUHq1tu4tpkH6TF8"
}

/**
 * Constants matching the smart contract constants
 */
object Constants {
    const val DATA_SIZE = 30 * 1024 // 30KB per PDA
    const val MAX_PDAS = 10 // Maximum number of PDAs
    const val MAX_VOICE_DATA_SIZE = 29 * 1024 // Leave 1KB for metadata
    const val MAX_PARTICIPANTS = 10
    const val MAX_ROOM_ID_LENGTH = 32
    const val CHUNK_SIZE = 30 * 1024 // 30KB per storage PDA
    const val MAX_STORAGE_PDAS = 10
}
