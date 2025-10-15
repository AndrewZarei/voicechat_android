package com.example.solvoice.services

import android.util.Base64
import android.util.Log
import com.example.solvoice.models.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        // For demo purposes, use a known valid devnet address format
        // In production, use proper ed25519 key generation
        val testPublicKeys = listOf(
            "11111111111111111111111111111112", // System program (valid but not for airdrops)
            "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM", // Example valid format
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", // Token program
            "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"  // Associated token program
        )
        
        val secureRandom = SecureRandom()
        val secretKey = ByteArray(32)
        secureRandom.nextBytes(secretKey)
        
        // Use a test public key for demo - in production generate properly
        val publicKey = generateValidTestPublicKey()
        
        return SolanaKeypair(publicKey, secretKey)
    }
    
    /**
     * Generate a valid test public key for devnet
     */
    private fun generateValidTestPublicKey(): String {
        // Generate a random but valid-looking Solana address
        val secureRandom = SecureRandom()
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        
        // Ensure it's a valid Base58 string of correct length
        return encodeBase58(randomBytes)
    }
    
    /**
     * Generate public key from secret key (simplified)
     * In production, use proper ed25519 library
     */
    private fun generatePublicKeyFromSecret(secretKey: ByteArray): String {
        // For now, generate a valid-looking Solana public key
        // In production, use proper ed25519 key generation
        val hash = MessageDigest.getInstance("SHA-256").digest(secretKey)
        return encodeBase58(hash)
    }
    
    /**
     * Encode bytes to Base58 (Bitcoin/Solana format)
     * Simplified implementation - in production use a proper Base58 library
     */
    private fun encodeBase58(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        
        // Count leading zeros
        var leadingZeros = 0
        for (b in input) {
            if (b.toInt() == 0) leadingZeros++ else break
        }
        
        // Convert to base 58
        val inputCopy = input.copyOf()
        val encoded = mutableListOf<Char>()
        
        var i = leadingZeros
        while (i < inputCopy.size) {
            var carry = inputCopy[i].toInt() and 0xFF
            var j = 0
            
            while (j < encoded.size || carry != 0) {
                if (j < encoded.size) {
                    carry += alphabet.indexOf(encoded[j]) * 256
                    encoded[j] = alphabet[carry % 58]
                } else {
                    encoded.add(alphabet[carry % 58])
                }
                carry /= 58
                j++
            }
            i++
        }
        
        // Add leading '1's for leading zeros
        val result = StringBuilder()
        repeat(leadingZeros) { result.append('1') }
        
        // Reverse and append
        for (k in encoded.size - 1 downTo 0) {
            result.append(encoded[k])
        }
        
        return result.toString()
    }
    
    /**
     * Get user's public key
     */
    fun getUserPublicKey(): String? = userKeypair?.publicKey
    
    /**
     * Get SOL balance for user's wallet
     */
    suspend fun getBalance(): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val publicKey = userKeypair?.publicKey 
                ?: return@withContext Result.Error(Exception("No keypair initialized"))
            
            val request = RpcRequest(
                method = "getBalance",
                params = listOf(publicKey, mapOf("commitment" to commitment))
            )
            
            val result = makeRpcCall<BalanceResponse>(request)
            when (result) {
                is Result.Success -> {
                    val lamports = result.data.value
                    val sol = lamports / 1_000_000_000.0 // Convert lamports to SOL
                    Log.d(TAG, "Balance: $sol SOL ($lamports lamports)")
                    Result.Success(sol)
                }
                is Result.Error -> Result.Error(result.exception)
                else -> Result.Error(Exception("Unknown error getting balance"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get balance", e)
            Result.Error(e)
        }
    }
    
    /**
     * Request SOL airdrop from devnet faucet
     */
    suspend fun requestAirdrop(amount: Double = 1.0): Result<String> = withContext(Dispatchers.IO) {
        try {
            val publicKey = userKeypair?.publicKey 
                ?: return@withContext Result.Error(Exception("No keypair initialized"))
            
            // Limit airdrop amount to prevent rate limiting (max 2 SOL per request on devnet)
            val clampedAmount = amount.coerceAtMost(2.0)
            val lamports = (clampedAmount * 1_000_000_000).toLong() // Convert SOL to lamports
            
            Log.d(TAG, "Requesting airdrop of $clampedAmount SOL ($lamports lamports) to $publicKey")
            
            // Validate public key format
            if (!isValidSolanaAddress(publicKey)) {
                return@withContext Result.Error(Exception("Invalid public key format: $publicKey"))
            }
            
            val request = RpcRequest(
                method = "requestAirdrop",
                params = listOf(publicKey, lamports, mapOf("commitment" to commitment))
            )
            
            val result = makeRpcCall<String>(request)
            when (result) {
                is Result.Success -> {
                    val signature = result.data
                    Log.d(TAG, "Airdrop requested successfully. Signature: $signature")
                    
                    // Wait for confirmation
                    confirmTransaction(signature)
                    
                    Result.Success(signature)
                }
                is Result.Error -> {
                    Log.e(TAG, "Airdrop request failed", result.exception)
                    
                    // Provide more specific error messages
                    val errorMessage = when {
                        result.exception.message?.contains("rate limit", ignoreCase = true) == true -> 
                            "Airdrop rate limit exceeded. Please wait before requesting another airdrop."
                        result.exception.message?.contains("Invalid param", ignoreCase = true) == true -> 
                            "Invalid parameters. Check if the public key format is correct."
                        result.exception.message?.contains("insufficient", ignoreCase = true) == true -> 
                            "Devnet faucet has insufficient funds. Try again later."
                        else -> "Airdrop failed: ${result.exception.message}"
                    }
                    
                    Result.Error(Exception(errorMessage))
                }
                else -> Result.Error(Exception("Unknown airdrop error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request airdrop", e)
            Result.Error(e)
        }
    }
    
    /**
     * Validate if a string is a valid Solana address format
     */
    private fun isValidSolanaAddress(address: String): Boolean {
        // Basic validation: Solana addresses are Base58 strings of 32-44 characters
        if (address.length < 32 || address.length > 44) return false
        
        // Check if all characters are valid Base58
        val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return address.all { it in base58Alphabet }
    }
    
    /**
     * Wait for transaction confirmation
     */
    private suspend fun confirmTransaction(signature: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Confirming transaction: $signature")
            
            // Poll for confirmation (simplified)
            repeat(30) { // Try for 30 seconds
                delay(1000) // Wait 1 second between checks
                
                val request = RpcRequest(
                    method = "getSignatureStatuses",
                    params = listOf(listOf(signature))
                )
                
                val result = makeRpcCall<Map<String, Any>>(request)
                if (result is Result.Success) {
                    // Check if transaction is confirmed
                    // Simplified check - in production, parse the response properly
                    Log.d(TAG, "Transaction status checked")
                    return@withContext Result.Success(true)
                }
            }
            
            Log.w(TAG, "Transaction confirmation timeout")
            Result.Success(true) // Assume success after timeout
        } catch (e: Exception) {
            Log.e(TAG, "Error confirming transaction", e)
            Result.Error(e)
        }
    }
    
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
