package com.example.solvoice.models

import com.google.gson.annotations.SerializedName

/**
 * Solana RPC API models for blockchain communication
 */

/**
 * Generic RPC request structure
 */
data class RpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: Int = 1,
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: List<Any>
)

/**
 * Generic RPC response structure
 */
data class RpcResponse<T>(
    @SerializedName("jsonrpc") val jsonrpc: String,
    @SerializedName("id") val id: Int,
    @SerializedName("result") val result: T?,
    @SerializedName("error") val error: RpcError?
)

/**
 * RPC error structure
 */
data class RpcError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: Any?
)

/**
 * Account info response from Solana RPC
 */
data class AccountInfo(
    @SerializedName("data") val data: List<String>, // [data, encoding]
    @SerializedName("executable") val executable: Boolean,
    @SerializedName("lamports") val lamports: Long,
    @SerializedName("owner") val owner: String,
    @SerializedName("rentEpoch") val rentEpoch: Long
)

/**
 * Account info response wrapper
 */
data class AccountInfoResponse(
    @SerializedName("context") val context: Context,
    @SerializedName("value") val value: AccountInfo?
)

/**
 * Context information from RPC responses
 */
data class Context(
    @SerializedName("slot") val slot: Long
)

/**
 * Transaction signature response
 */
data class TransactionSignature(
    @SerializedName("signature") val signature: String
)

/**
 * Latest blockhash response
 */
data class LatestBlockhash(
    @SerializedName("blockhash") val blockhash: String,
    @SerializedName("lastValidBlockHeight") val lastValidBlockHeight: Long
)

/**
 * Latest blockhash response wrapper
 */
data class LatestBlockhashResponse(
    @SerializedName("context") val context: Context,
    @SerializedName("value") val value: LatestBlockhash
)

/**
 * Transaction instruction for Solana transactions
 */
data class TransactionInstruction(
    @SerializedName("programId") val programId: String,
    @SerializedName("accounts") val accounts: List<AccountMeta>,
    @SerializedName("data") val data: String // Base64 encoded instruction data
)

/**
 * Account metadata for transaction instructions
 */
data class AccountMeta(
    @SerializedName("pubkey") val pubkey: String,
    @SerializedName("isSigner") val isSigner: Boolean,
    @SerializedName("isWritable") val isWritable: Boolean
)

/**
 * Solana transaction structure
 */
data class SolanaTransaction(
    @SerializedName("recentBlockhash") val recentBlockhash: String,
    @SerializedName("feePayer") val feePayer: String,
    @SerializedName("instructions") val instructions: List<TransactionInstruction>
)

/**
 * Simulation result for testing transactions
 */
data class SimulationResult(
    @SerializedName("err") val error: Any?,
    @SerializedName("logs") val logs: List<String>?,
    @SerializedName("accounts") val accounts: List<AccountInfo>?
)

/**
 * Simulation response wrapper
 */
data class SimulationResponse(
    @SerializedName("context") val context: Context,
    @SerializedName("value") val value: SimulationResult
)

/**
 * Balance response
 */
data class BalanceResponse(
    @SerializedName("context") val context: Context,
    @SerializedName("value") val value: Long // Balance in lamports
)

/**
 * Anchor program account data structure
 */
data class AnchorAccountData(
    val discriminator: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnchorAccountData

        if (!discriminator.contentEquals(other.discriminator)) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = discriminator.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Keypair for Solana operations
 */
data class SolanaKeypair(
    val publicKey: String,
    val secretKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SolanaKeypair

        if (publicKey != other.publicKey) return false
        if (!secretKey.contentEquals(other.secretKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + secretKey.contentHashCode()
        return result
    }
}

/**
 * PDA (Program Derived Address) information
 */
data class PDAInfo(
    val address: String,
    val bump: Int,
    val seeds: List<ByteArray>
)

/**
 * Instruction data for smart contract calls
 */
sealed class InstructionData {
    object Initialize : InstructionData()
    data class CreateAllPDAs(val pdaIndex: Int) : InstructionData()
    data class CreatePDAAccount(val pdaIndex: Int, val data: ByteArray) : InstructionData()
    data class UpdatePDAData(val newData: ByteArray) : InstructionData()
    data class InitializeVoiceRoom(val roomId: String) : InstructionData()
    object JoinVoiceRoom : InstructionData()
    data class SendVoiceData(
        val voiceData: ByteArray, 
        val targetPdaIndex: Int, 
        val sequenceNumber: Long
    ) : InstructionData()
    data class GetVoiceData(val pdaIndex: Int) : InstructionData()
    object LeaveVoiceRoom : InstructionData()
    object GetRoomInfo : InstructionData()
    data class CreateStoragePDA(val pdaIndex: Int) : InstructionData()
    data class UpdateStorageData(val newData: ByteArray, val offset: Long) : InstructionData()
    object InitializeStorage : InstructionData()
    object ClearStorageData : InstructionData()
}

/**
 * Result wrapper for async operations
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
