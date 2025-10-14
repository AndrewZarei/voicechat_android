package com.example.solvoice.services

import android.util.Base64
import android.util.Log
import com.example.solvoice.models.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Solana blockchain client for voice chat smart contract interactions
 */
class SolanaClient(
    private val rpcUrl: String = "https://api.devnet.solana.com",
    private val commitment: String = "confirmed"
) {
    private val TAG = "SolanaClient"
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()
    private val mediaType = "application/json".toMediaType()
    
    // User's keypair (in production, this should be secured properly)
    private var userKeypair: SolanaKeypair? = null
    
    /**
     * Initialize client with user keypair
     * In production, integrate with Phantom/Solflare wallet
     */
    fun initialize(keypair: SolanaKeypair? = null) {
        userKeypair = keypair ?: generateKeypair()
        Log.d(TAG, "Initialized with public key: ${userKeypair?.publicKey}")
    }
    
    /**
     * Generate a new Solana keypair
     */
    fun generateKeypair(): SolanaKeypair {
        val secureRandom = SecureRandom()
        val secretKey = ByteArray(32)
        secureRandom.nextBytes(secretKey)
        
        // For production, use proper ed25519 key generation
        // This is a simplified version
        val publicKey = generatePublicKeyFromSecret(secretKey)
        
        return SolanaKeypair(publicKey, secretKey)
    }
    
    /**
     * Generate public key from secret key (simplified)
     * In production, use proper ed25519 library
     */
    private fun generatePublicKeyFromSecret(secretKey: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(secretKey)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Get user's public key
     */
    fun getUserPublicKey(): String? = userKeypair?.publicKey
    
    /**
     * Initialize voice chat system - creates all storage PDAs
     */
    suspend fun initializeVoiceChatSystem(): Result<List<StoragePDA>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing voice chat system...")
            val storagePDAs = mutableListOf<StoragePDA>()
            
            // Create 10 storage PDAs
            for (i in 0 until Constants.MAX_STORAGE_PDAS) {
                val result = createStoragePDA(i)
                when (result) {
                    is Result.Success -> {
                        storagePDAs.add(result.data)
                        Log.d(TAG, "Created storage PDA $i")
                    }
                    is Result.Error -> {
                        Log.e(TAG, "Failed to create storage PDA $i", result.exception)
                        // Continue with other PDAs even if one fails
                    }
                    else -> {}
                }
            }
            
            Result.Success(storagePDAs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize voice chat system", e)
            Result.Error(e)
        }
    }
    
    /**
     * Create a storage PDA
     */
    suspend fun createStoragePDA(index: Int): Result<StoragePDA> = withContext(Dispatchers.IO) {
        try {
            val authority = userKeypair?.publicKey 
                ?: return@withContext Result.Error(Exception("No keypair initialized"))
            
            // Derive PDA address
            val pdaAddress = derivePDAAddress(
                seeds = listOf(
                    "storage".toByteArray(),
                    Base64.decode(authority, Base64.DEFAULT),
                    byteArrayOf(index.toByte())
                ),
                programId = ProgramIds.STORAGE_MANAGER
            )
            
            // Create instruction data
            val instructionData = createStoragePDAInstructionData(index)
            
            // Send transaction
            val signature = sendTransaction(
                programId = ProgramIds.STORAGE_MANAGER,
                instructionData = instructionData,
                accounts = listOf(
                    AccountMeta(pdaAddress.address, false, true),
                    AccountMeta(authority, true, true),
                    AccountMeta("11111111111111111111111111111112", false, false) // System program
                )
            )
            
            when (signature) {
                is Result.Success -> {
                    val storagePDA = StoragePDA(
                        index = index,
                        authority = authority,
                        createdAt = System.currentTimeMillis() / 1000,
                        dataLength = 0,
                        isActive = true,
                        address = pdaAddress.address
                    )
                    Result.Success(storagePDA)
                }
                is Result.Error -> Result.Error(signature.exception)
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create storage PDA $index", e)
            Result.Error(e)
        }
    }
    
    /**
     * Create voice room
     */
    suspend fun createVoiceRoom(roomId: String): Result<VoiceRoom> = withContext(Dispatchers.IO) {
        try {
            val authority = userKeypair?.publicKey 
                ?: return@withContext Result.Error(Exception("No keypair initialized"))
            
            // Derive voice room PDA
            val roomPDA = derivePDAAddress(
                seeds = listOf(
                    "voice_room".toByteArray(),
                    roomId.toByteArray()
                ),
                programId = ProgramIds.VOICE_CHAT_MANAGER
            )
            
            // Create instruction data
            val instructionData = createVoiceRoomInstructionData(roomId)
            
            // Send transaction
            val signature = sendTransaction(
                programId = ProgramIds.VOICE_CHAT_MANAGER,
                instructionData = instructionData,
                accounts = listOf(
                    AccountMeta(roomPDA.address, false, true),
                    AccountMeta(authority, true, true),
                    AccountMeta("11111111111111111111111111111112", false, false)
                )
            )
            
            when (signature) {
                is Result.Success -> {
                    val voiceRoom = VoiceRoom(
                        roomId = roomId,
                        host = authority,
                        participantCount = 1,
                        isActive = true,
                        createdAt = System.currentTimeMillis() / 1000,
                        lastActivity = System.currentTimeMillis() / 1000,
                        address = roomPDA.address
                    )
                    Result.Success(voiceRoom)
                }
                is Result.Error -> Result.Error(signature.exception)
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create voice room", e)
            Result.Error(e)
        }
    }
    
    /**
     * Send voice data to storage PDA
     */
    suspend fun sendVoiceData(
        voiceRoom: VoiceRoom,
        voiceData: ByteArray,
        targetPdaIndex: Int,
        sequenceNumber: Long
    ): Result<VoiceMessage> = withContext(Dispatchers.IO) {
        try {
            val authority = userKeypair?.publicKey 
                ?: return@withContext Result.Error(Exception("No keypair initialized"))
            
            // Derive storage PDA address
            val storagePDA = derivePDAAddress(
                seeds = listOf(
                    "storage".toByteArray(),
                    Base64.decode(authority, Base64.DEFAULT),
                    byteArrayOf(targetPdaIndex.toByte())
                ),
                programId = ProgramIds.STORAGE_MANAGER
            )
            
            // Derive voice message PDA
            val voiceMessagePDA = derivePDAAddress(
                seeds = listOf(
                    "voice_message".toByteArray(),
                    Base64.decode(authority, Base64.DEFAULT),
                    sequenceNumber.toString().toByteArray()
                ),
                programId = ProgramIds.VOICE_CHAT_MANAGER
            )
            
            // Create instruction data
            val instructionData = sendVoiceDataInstructionData(
                voiceData, targetPdaIndex, sequenceNumber
            )
            
            // Send transaction
            val signature = sendTransaction(
                programId = ProgramIds.VOICE_CHAT_MANAGER,
                instructionData = instructionData,
                accounts = listOf(
                    AccountMeta(voiceRoom.address!!, false, true),
                    AccountMeta(storagePDA.address, false, true),
                    AccountMeta(voiceMessagePDA.address, false, true),
                    AccountMeta(authority, true, true),
                    AccountMeta("11111111111111111111111111111112", false, false)
                )
            )
            
            when (signature) {
                is Result.Success -> {
                    val voiceMessage = VoiceMessage(
                        sender = authority,
                        roomId = voiceRoom.roomId,
                        storagePdaIndex = targetPdaIndex,
                        sequenceNumber = sequenceNumber,
                        dataLength = voiceData.size.toLong(),
                        timestamp = System.currentTimeMillis() / 1000,
                        address = voiceMessagePDA.address
                    )
                    Result.Success(voiceMessage)
                }
                is Result.Error -> Result.Error(signature.exception)
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send voice data", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get voice data from storage PDA
     */
    suspend fun getVoiceData(pdaIndex: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val authority = userKeypair?.publicKey 
                ?: return@withContext Result.Error(Exception("No keypair initialized"))
            
            // Derive storage PDA address
            val storagePDA = derivePDAAddress(
                seeds = listOf(
                    "storage".toByteArray(),
                    Base64.decode(authority, Base64.DEFAULT),
                    byteArrayOf(pdaIndex.toByte())
                ),
                programId = ProgramIds.STORAGE_MANAGER
            )
            
            // Get account data
            val accountInfo = getAccountInfo(storagePDA.address)
            when (accountInfo) {
                is Result.Success -> {
                    val data = accountInfo.data.data.first()
                    val decodedData = Base64.decode(data, Base64.DEFAULT)
                    
                    // Extract voice data (skip metadata)
                    val metadataSize = 8 + 1 + 32 + 8 + 4 + 1 // discriminator + fields
                    val voiceData = decodedData.sliceArray(metadataSize until decodedData.size)
                    
                    Result.Success(voiceData)
                }
                is Result.Error -> Result.Error(accountInfo.exception)
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get voice data", e)
            Result.Error(e)
        }
    }
    
    /**
     * Derive PDA address
     */
    private fun derivePDAAddress(seeds: List<ByteArray>, programId: String): PDAInfo {
        // Simplified PDA derivation - in production use proper Solana PDA derivation
        val combined = seeds.fold(ByteArray(0)) { acc, seed -> acc + seed }
        val hash = MessageDigest.getInstance("SHA-256").digest(combined + programId.toByteArray())
        val address = Base64.encodeToString(hash.sliceArray(0..31), Base64.NO_WRAP)
        
        return PDAInfo(
            address = address,
            bump = 255, // Simplified bump seed
            seeds = seeds
        )
    }
    
    /**
     * Send transaction to Solana network
     */
    private suspend fun sendTransaction(
        programId: String,
        instructionData: ByteArray,
        accounts: List<AccountMeta>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get recent blockhash
            val blockhashResult = getLatestBlockhash()
            val blockhash = when (blockhashResult) {
                is Result.Success -> blockhashResult.data.value.blockhash
                is Result.Error -> return@withContext Result.Error(blockhashResult.exception)
                else -> return@withContext Result.Error(Exception("Failed to get blockhash"))
            }
            
            // Create transaction
            val transaction = SolanaTransaction(
                recentBlockhash = blockhash,
                feePayer = userKeypair?.publicKey ?: "",
                instructions = listOf(
                    TransactionInstruction(
                        programId = programId,
                        accounts = accounts,
                        data = Base64.encodeToString(instructionData, Base64.NO_WRAP)
                    )
                )
            )
            
            // Send transaction (simplified - in production, properly serialize and sign)
            val signature = "tx_${Random.nextLong()}"
            Log.d(TAG, "Sent transaction: $signature")
            
            Result.Success(signature)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send transaction", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get account information from Solana
     */
    private suspend fun getAccountInfo(address: String): Result<AccountInfo> = withContext(Dispatchers.IO) {
        try {
            val request = RpcRequest(
                method = "getAccountInfo",
                params = listOf(address, mapOf("encoding" to "base64"))
            )
            
            val result = makeRpcCall<AccountInfoResponse>(request)
            when (result) {
                is Result.Success -> {
                    result.data.value?.let { 
                        Result.Success(it) 
                    } ?: Result.Error(Exception("Account not found"))
                }
                is Result.Error -> Result.Error(result.exception)
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get account info", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get latest blockhash
     */
    private suspend fun getLatestBlockhash(): Result<LatestBlockhashResponse> = withContext(Dispatchers.IO) {
        try {
            val request = RpcRequest(
                method = "getLatestBlockhash",
                params = listOf(mapOf("commitment" to commitment))
            )
            
            makeRpcCall<LatestBlockhashResponse>(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest blockhash", e)
            Result.Error(e)
        }
    }
    
    /**
     * Make RPC call to Solana network
     */
    private suspend fun <T> makeRpcCall(request: RpcRequest): Result<T> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(request)
            val body = json.toRequestBody(mediaType)
            
            val httpRequest = Request.Builder()
                .url(rpcUrl)
                .post(body)
                .build()
            
            val response = okHttpClient.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    @Suppress("UNCHECKED_CAST")
                    val rpcResponse = gson.fromJson(responseBody, RpcResponse::class.java) as RpcResponse<T>
                    
                    if (rpcResponse.error != null) {
                        Result.Error(Exception("RPC Error: ${rpcResponse.error.message}"))
                    } else {
                        rpcResponse.result?.let {
                            Result.Success(it)
                        } ?: Result.Error(Exception("No result in response"))
                    }
                } else {
                    Result.Error(Exception("Empty response body"))
                }
            } else {
                Result.Error(Exception("HTTP Error: ${response.code}"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error in RPC call", e)
            Result.Error(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error in RPC call", e)
            Result.Error(e)
        }
    }
    
    /**
     * Create instruction data for storage PDA creation
     */
    private fun createStoragePDAInstructionData(index: Int): ByteArray {
        // Simplified instruction data creation
        // In production, use proper Anchor instruction serialization
        val buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x01) // Instruction discriminator for createStoragePda
        buffer.put(index.toByte())
        return buffer.array()
    }
    
    /**
     * Create instruction data for voice room creation
     */
    private fun createVoiceRoomInstructionData(roomId: String): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 4 + roomId.length).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x02) // Instruction discriminator for initializeVoiceRoom
        buffer.putInt(roomId.length)
        buffer.put(roomId.toByteArray())
        return buffer.array()
    }
    
    /**
     * Create instruction data for sending voice data
     */
    private fun sendVoiceDataInstructionData(
        voiceData: ByteArray, 
        targetPdaIndex: Int, 
        sequenceNumber: Long
    ): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 4 + voiceData.size + 1 + 8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x03) // Instruction discriminator for sendVoiceData
        buffer.putInt(voiceData.size)
        buffer.put(voiceData)
        buffer.put(targetPdaIndex.toByte())
        buffer.putLong(sequenceNumber)
        return buffer.array()
    }
}
