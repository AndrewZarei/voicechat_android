# 🎙️ SolVoice - Voice Chat dApp on Solana

A decentralized voice communication application built on Solana blockchain using Kotlin/Android with real-time audio recording, storage, and playback functionality.

## 📱 Features

### 🔊 Voice Chat Capabilities
- **Real-time voice recording** using Android MediaRecorder API
- **Voice-optimized audio** (8kHz, 16-bit PCM - telephone quality)
- **Blockchain storage** via Solana smart contracts (~19 seconds capacity)
- **Voice message playback** from blockchain data
- **Audio level monitoring** with visual feedback
- **Quick voice messages** (5-second auto-record)

### 🏗️ Blockchain Integration
- **Solana smart contract interaction** via custom RPC client
- **10 Storage PDAs** (30KB each, 300KB total capacity)
- **Voice room management** (create, join, leave rooms)
- **Cross-program invocation** between storage and voice chat contracts
- **Real-time transaction monitoring**

### 🎨 Modern Android UI
- **Jetpack Compose** for reactive UI
- **Material 3 Design** with beautiful animations
- **Permission handling** for microphone access
- **Real-time status updates** and progress indicators
- **Storage visualization** with PDA usage monitoring

## 🏛️ Smart Contract Architecture

The app interacts with 3 Solana smart contracts:

### 1. Storage Manager (`storage_manager`)
- **Program ID**: `SU6CRGJXz5ksvXPyUuWXYfW2qmba6ZgHa3sxdr9aYMz`
- Creates and manages 10 storage PDAs (30KB each)
- Handles voice data storage and retrieval
- Provides storage optimization and cleanup

### 2. Voice Chat Manager (`voice_chat_manager`)
- **Program ID**: `GVqX9pcoxbiY7i1W3Ad6Sinw1pNpwUHq1tu4tpkH6TF8`
- Manages voice rooms and participants
- Coordinates voice data transmission
- Handles message sequencing and routing

### 3. Base Voice Chat (`voicechat`)
- **Program ID**: `HPxbCqRWpSxCEE2L6Vy1S1oMTc3D9aknrBGwZ9WTAvSK`
- Basic PDA management and data operations
- Provides foundation for voice chat functionality

## 📊 Application Architecture

```
┌─────────────────────────────────────┐
│           Android App               │
├─────────────────────────────────────┤
│  MainActivity (Jetpack Compose UI)  │
├─────────────────────────────────────┤
│       VoiceChatViewModel           │
├─────────────────────────────────────┤
│ ┌─────────────┐ ┌─────────────────┐ │
│ │VoiceRoom    │ │ StorageManager  │ │
│ │Manager      │ │                 │ │
│ └─────────────┘ └─────────────────┘ │
├─────────────────────────────────────┤
│ ┌─────────────┐ ┌─────────────────┐ │
│ │AudioManager │ │ SolanaClient    │ │
│ │             │ │                 │ │
│ └─────────────┘ └─────────────────┘ │
├─────────────────────────────────────┤
│          Solana Blockchain          │
│   ┌───────┐ ┌───────┐ ┌───────┐    │
│   │Storage│ │Voice  │ │Base   │    │
│   │Manager│ │Chat   │ │Voice  │    │
│   │       │ │Manager│ │Chat   │    │
│   └───────┘ └───────┘ └───────┘    │
└─────────────────────────────────────┘
```

## 🛠️ Project Structure

```
solvoice/
├── app/
│   ├── src/main/java/com/example/solvoice/
│   │   ├── MainActivity.kt              # Main UI with Compose
│   │   ├── models/
│   │   │   ├── SmartContractModels.kt   # Data models for contracts
│   │   │   └── SolanaRpcModels.kt       # RPC communication models
│   │   ├── services/
│   │   │   ├── SolanaClient.kt          # Blockchain interaction
│   │   │   ├── AudioManager.kt          # Voice recording/playback
│   │   │   ├── StorageManager.kt        # PDA storage management
│   │   │   └── VoiceRoomManager.kt      # Room coordination
│   │   ├── viewmodels/
│   │   │   └── VoiceChatViewModel.kt    # UI state management
│   │   └── ui/theme/                    # Material 3 theming
│   ├── build.gradle.kts                 # Dependencies & config
│   └── AndroidManifest.xml              # Permissions & features
└── README.md                            # This file
```

## 🚀 Getting Started

### Prerequisites
- **Android Studio** Hedgehog or later
- **Android SDK** API 24+ (Android 7.0)
- **Kotlin** 1.9+
- **Device with microphone** (for voice recording)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd voice-chat_smartcontract/solvoice
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the `solvoice` directory

3. **Sync dependencies**
   - Android Studio will automatically sync Gradle dependencies
   - Wait for the build to complete

4. **Run the app**
   - Connect an Android device or start an emulator
   - Click "Run" or press Shift+F10

### 📱 Usage Guide

#### 1. **Initialize the System**
- Open the app and grant microphone permission when prompted
- Tap "Initialize Voice Chat System" to create storage PDAs
- Wait for the blockchain transaction to complete

#### 2. **Create or Join a Room**
- After initialization, enter a room ID (optional) or leave blank for auto-generation
- Tap "Create Room" to start a new voice chat session
- Or tap "Join Room" with an existing room ID

#### 3. **Voice Communication**
- **Start Recording**: Tap the microphone button to begin recording
- **Stop Recording**: Tap the stop button to end recording and send to blockchain
- **Play Messages**: Tap "Play Messages" to hear voice data from the blockchain
- **Quick Message**: Tap "Quick Msg" for a 5-second auto-record-and-send

#### 4. **Monitor Storage**
- View storage PDA usage in the Storage section
- Each PDA can store up to 30KB of voice data
- Green = low usage, Yellow = medium, Red = high usage

## 🔧 Configuration

### Smart Contract Endpoints
The app is configured for Solana Devnet by default:
- **RPC URL**: `https://api.devnet.solana.com`
- **Commitment Level**: `confirmed`

### Audio Settings
- **Sample Rate**: 8kHz (voice-optimized, telephone quality)
- **Encoding**: 16-bit PCM
- **Channels**: Mono
- **Data Rate**: 16KB/second (16,000 bytes/sec)
- **Buffer Size**: Optimized for low latency
- **Storage Per PDA**: ~1.9 seconds of audio (30KB)
- **Total Capacity**: ~19 seconds across all 10 PDAs

### Storage Configuration
- **Total PDAs**: 10
- **PDA Size**: 30KB each
- **Total Capacity**: 300KB
- **Compression**: 2:1 ratio (simplified)

## 📱 Screenshots & Demo Flow

### 1. Initial State
- System status showing "Not initialized"
- Initialize button prominent and ready

### 2. After Initialization
- Storage PDAs created and visible
- Room management controls available
- Storage visualization showing 10 PDAs

### 3. In Voice Room
- Voice controls enabled
- Real-time recording status
- Audio level indicators during recording

### 4. Voice Message Flow
- Record → Visual feedback → Stop → Send to blockchain → Confirmation
- Play received messages from blockchain storage

## 🛡️ Permissions Required

### Android Permissions
- `RECORD_AUDIO` - For voice recording
- `INTERNET` - For blockchain communication
- `ACCESS_NETWORK_STATE` - Network connectivity checks
- `WAKE_LOCK` - Keep device active during voice chat
- `MODIFY_AUDIO_SETTINGS` - Audio configuration

### Hardware Features
- `android.hardware.microphone` - Required
- `android.hardware.audio.output` - Required

## 🔐 Security Considerations

### Current Implementation
- **Simplified key generation** for demo purposes
- **Local keypair storage** (not production-ready)
- **Basic RPC communication** without advanced security

### Production Recommendations
- Integrate with **Phantom/Solflare wallet**
- Use **proper Ed25519 key generation**
- Implement **secure key storage** (Android Keystore)
- Add **transaction verification**
- Use **encrypted communication**

## 🐛 Known Issues & Limitations

### Current Limitations
1. **Simplified crypto operations** - Not production-grade key management
2. **Mock transaction signing** - Needs real wallet integration
3. **Basic audio compression** - Should use proper codecs (Opus)
4. **Limited error handling** - Needs more robust error recovery
5. **No real-time communication** - Messages require manual refresh

### Future Enhancements
- Real-time voice streaming
- End-to-end encryption
- Multi-participant rooms
- Voice chat history
- Push notifications
- Wallet integration

## 🔨 Development

### Building
```bash
cd solvoice
./gradlew assembleDebug
```

### Testing
```bash
./gradlew test
./gradlew connectedAndroidTest  # Requires connected device
```

### Code Style
- **Kotlin** with official style guide
- **Jetpack Compose** for UI
- **MVVM architecture** pattern
- **Coroutines** for async operations

## 📚 Dependencies

### Core Android
- **Jetpack Compose** - Modern UI toolkit
- **ViewModel & LiveData** - MVVM architecture
- **Coroutines** - Async programming
- **Navigation** - Screen navigation

### Blockchain & Crypto
- **OkHttp & Retrofit** - HTTP client for RPC calls
- **Gson** - JSON parsing
- **BitcoinJ** - Cryptographic operations
- **Commons Codec** - Base58 encoding

### Audio & Permissions
- **AndroidX Media** - Audio recording/playback
- **Accompanist Permissions** - Permission handling

## 📄 License

This project is created for educational and development purposes. Please ensure proper security measures and wallet integration before any production use.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📞 Support

For issues, questions, or contributions, please refer to the smart contract documentation in the main repository and the Android development guidelines.

---

**🎙️ SolVoice** - Bringing voice communication to the decentralized world! 

*Built with ❤️ using Kotlin, Jetpack Compose, and Solana*
