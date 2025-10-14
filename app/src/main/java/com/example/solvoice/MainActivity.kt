package com.example.solvoice

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.solvoice.models.StoragePDA
import com.example.solvoice.services.StorageStats
import com.example.solvoice.ui.theme.SolvoiceTheme
import com.example.solvoice.viewmodels.VoiceChatViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    
    private val viewModel: VoiceChatViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            SolvoiceTheme {
                VoiceChatApp(viewModel = viewModel)
            }
        }
        
        // Observe messages
        lifecycleScope.launchWhenStarted {
            viewModel.errorMessages.collectLatest { message ->
                Toast.makeText(this@MainActivity, "Error: $message", Toast.LENGTH_LONG).show()
            }
        }
        
        lifecycleScope.launchWhenStarted {
            viewModel.successMessages.collectLatest { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatApp(viewModel: VoiceChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Permission handling
    val microphonePermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    var roomIdInput by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        if (!microphonePermission.status.isGranted) {
            microphonePermission.launchPermissionRequest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "🎙️ Voice Chat dApp",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Section
            item {
                StatusCard(uiState = uiState)
            }
            
            // System Initialization Section
            if (!uiState.isStorageInitialized) {
                item {
                    SystemInitCard(
                        onInitialize = { viewModel.initializeVoiceChatSystem() },
                        isLoading = uiState.isLoading
                    )
                }
            }
            
            // Room Management Section
            if (uiState.isStorageInitialized) {
                item {
                    RoomManagementCard(
                        uiState = uiState,
                        roomIdInput = roomIdInput,
                        onRoomIdChange = { roomIdInput = it },
                        onCreateRoom = { viewModel.createVoiceRoom(roomIdInput) },
                        onJoinRoom = { viewModel.joinVoiceRoom(roomIdInput) },
                        onLeaveRoom = { viewModel.leaveVoiceRoom() },
                        isLoading = uiState.isLoading
                    )
                }
            }
            
            // Voice Controls Section
            if (uiState.isInRoom) {
                item {
                    VoiceControlsCard(
                        uiState = uiState,
                        onStartRecording = { 
                            if (microphonePermission.status.isGranted) {
                                viewModel.startRecording()
                            } else {
                                microphonePermission.launchPermissionRequest()
                            }
                        },
                        onStopRecording = { viewModel.stopRecording() },
                        onPlayMessages = { viewModel.playVoiceMessages() },
                        onQuickMessage = { viewModel.sendQuickVoiceMessage() },
                        onEmergencyStop = { viewModel.emergencyStop() }
                    )
                }
            }
            
            // Storage PDAs Section
            if (uiState.storagePDAs.isNotEmpty()) {
                item {
                    StoragePDAsCard(
                        storagePDAs = uiState.storagePDAs,
                        storageStats = viewModel.getStorageStats()
                    )
                }
            }
            
            // Microphone Permission Section
            if (!microphonePermission.status.isGranted) {
                item {
                    PermissionCard(
                        onRequestPermission = { microphonePermission.launchPermissionRequest() },
                        shouldShowRationale = microphonePermission.status.shouldShowRationale
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(uiState: VoiceChatUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 System Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            StatusRow("Status", uiState.status)
            StatusRow("Wallet", uiState.userWalletAddress?.take(8)?.plus("...") ?: "Not connected")
            StatusRow("Room", uiState.roomId)
            StatusRow("Storage", uiState.totalStorage)
            StatusRow("Recording", uiState.recordingStatus)
            if (uiState.isRecording) {
                StatusRow("Duration", uiState.recordingDuration)
                
                // Audio level indicator
                LinearProgressIndicator(
                    progress = { uiState.audioLevel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = if (uiState.audioLevel > 0.5f) MaterialTheme.colorScheme.error 
                           else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Audio Level: ${(uiState.audioLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            StatusRow("Last Sent", uiState.lastSentTime)
            
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "❌ ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun SystemInitCard(
    onInitialize: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🚀 System Initialization",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Initialize the voice chat system by creating 10 storage PDAs (300KB total)",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onInitialize,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Initialize Voice Chat System")
            }
        }
    }
}

@Composable
fun RoomManagementCard(
    uiState: VoiceChatUiState,
    roomIdInput: String,
    onRoomIdChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🏠 Voice Room Management",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!uiState.isInRoom) {
                OutlinedTextField(
                    value = roomIdInput,
                    onValueChange = onRoomIdChange,
                    label = { Text("Room ID (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateRoom,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Room")
                    }
                    
                    Button(
                        onClick = onJoinRoom,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && roomIdInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Join Room")
                    }
                }
            } else {
                Text(
                    text = "Currently in room: ${uiState.roomId}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onLeaveRoom,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Leave Room")
                }
            }
        }
    }
}

@Composable
fun VoiceControlsCard(
    uiState: VoiceChatUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayMessages: () -> Unit,
    onQuickMessage: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🎤 Voice Communication",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recording Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = if (uiState.isRecording) onStopRecording else onStartRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isRecording) 
                            MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (uiState.isRecording) "Stop Recording" else "Start Recording")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Playback and Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayMessages,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isPlaying
                ) {
                    Icon(
                        if (uiState.isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (uiState.isPlaying) "Playing..." else "Play Messages")
                }
                
                Button(
                    onClick = onQuickMessage,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRecording && !uiState.isPlaying
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quick Msg")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Emergency Stop
            Button(
                onClick = onEmergencyStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Emergency Stop All")
            }
        }
    }
}

@Composable
fun StoragePDAsCard(
    storagePDAs: List<StoragePDA>,
    storageStats: StorageStats?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📦 Storage PDAs (${storagePDAs.size}/10)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (storageStats != null) {
                Spacer(modifier = Modifier.height(8.dp))
    Text(
                    text = "Usage: ${storageStats.utilizationPercentage}% (${storageStats.usedSpace / 1024}KB / ${storageStats.totalCapacity / 1024}KB)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(storagePDAs.take(10)) { pda ->
                    PDAItem(pda = pda)
                }
            }
        }
    }
}

@Composable
fun PDAItem(pda: StoragePDA) {
    val utilizationColor = when {
        pda.dataLength == 0L -> MaterialTheme.colorScheme.surface
        pda.dataLength < 10240 -> Color.Green.copy(alpha = 0.3f)
        pda.dataLength < 20480 -> Color.Yellow.copy(alpha = 0.3f)
        else -> Color.Red.copy(alpha = 0.3f)
    }
    
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(utilizationColor)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PDA ${pda.index}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "30KB",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = if (pda.isActive) "✅" else "❌",
            fontSize = 16.sp
        )
        Text(
            text = "${pda.dataLength / 1024}KB",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun PermissionCard(
    onRequestPermission: () -> Unit,
    shouldShowRationale: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "🎤 Microphone Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (shouldShowRationale) {
                    "Voice chat requires microphone access to record and send voice messages. Please grant the permission to continue."
                } else {
                    "Please allow microphone access to use voice chat features."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Microphone Permission")
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun VoiceChatAppPreview() {
    SolvoiceTheme {
        // Preview with mock data would go here
    }
}