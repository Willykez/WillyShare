<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0-FF6B35?style=for-the-badge&labelColor=0D0D1A"/>
  <img src="https://img.shields.io/badge/Android-7.0%2B-4CAF50?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Transfer-Wi--Fi%20Direct-42A5F5?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Ads-None-9C27B0?style=for-the-badge"/>
</p>

<h1 align="center">⚡ Sparks</h1>
<p align="center"><strong>Blazing-fast peer-to-peer file sharing over Wi-Fi Direct</strong></p>
<p align="center">
  No internet · No cloud · No pairing codes — just scan a QR and send
</p>

---

## 📱 Overview

**Sparks** is a fully offline Android file-transfer app built on **Wi-Fi Direct** (WifiP2pManager) and **QR pairing**. Two devices connect directly — no router, no internet, no account. Files are transferred over a raw NIO socket at full Wi-Fi speeds. Every transfer is logged locally in a Room database.

Built from the ground up with **Jetpack Compose**, a custom Material 3 `SleekPalette` design system, and a clean single-`ViewModel` architecture.

---

## ✨ Features

### 🔗 Connectivity
- **Wi-Fi Direct peer discovery** — real `WifiP2pManager` broadcasts, no simulated devices
- **QR Code pairing** — receiver displays a `SPARKS|name|ip|port` QR; sender scans it with CameraX + ZXing
- **Auto IP handshake** — group-owner IP resolved from `WifiP2pInfo` after connection
- Dual connection path: Wi-Fi Direct auto-connect **or** manual QR scan

### 📤 Sending
- Pick any files from device storage — Photos, Videos, Documents, APKs, Audio, Archives
- Grouped file browser (`GroupedList`) with category tabs and multi-select
- Per-file progress rows with speed (MB/s) and live byte count
- Overall transfer progress aggregated across all files in one batch
- Parallel multi-file send via `coroutineScope { async { ... } }` per file

### 📥 Receiving
- One-tap `ReceiveScreen` — starts a background NIO `ServerSocketChannel`
- Shows sender device name, connection status, and per-file progress
- Files saved to app-specific `Downloads/Sparks/` directory
- `senderConnected` state keeps UI in sync with socket lifecycle

### 📋 History
- Room database (`PulseDatabase`) stores every completed or failed transfer
- `TransferEntity` fields: `fileName`, `category`, `sizeBytes`, `timestamp`, `deviceName`, `isSend`, `status`, `savedPath`
- Filter by Sent / Received; category icons per file type
- Real-time `StateFlow` updates via `PulseDao.getAllTransfers()`

### 🔐 QR Pairing
- `MyQrScreen` generates a ZXing QR bitmap from `QrPairing.buildPayload()`
- `ScanQrScreen` uses CameraX `ImageAnalysis` to decode live frames
- Payload format: `SPARKS|<deviceName>|<ip>|<port>` — parsed and validated before connecting

### 🎨 UI & Design
- **Jetpack Compose** throughout — zero XML activities
- **`SleekPalette`** custom M3 colour system — dark + light with `CompositionLocal` delivery
- **`SleekBottomNav`** — pill-shaped floating nav bar with shadow + border
- **`GlassCard`** — semi-transparent surface with backdrop blur feel
- **`AuroraBackground`** — animated gradient canvas backdrop on key screens
- **`RadarPulseRing`** — animated sonar-ring send/receive indicators
- **`SleekTopBar`** with back navigation and screen title
- `AnimatedContent` slide + fade transitions between screens
- Custom `PulseIcons` vector icon set

### 🗄️ Data & State
- **Room v2** database — `TransferEntity` + `FileItemEntity` tables
- `PulseViewModel` (AndroidViewModel) — single source of truth for all UI state
- `StateFlow` for peers, connection info, progress, transfers, device files
- `DeviceFiles.queryAll()` — MediaStore query for all file categories
- Coroutines + `Dispatchers.IO` for all network and disk operations

### ⚙️ Settings
- Theme toggle (Dark / Light)
- Device display name
- Download folder path
- Notification preferences
- Clear transfer history

---

## 🗂️ File Structure

```
Spark-Share/
├── app/src/main/java/com/willyshare/willykez/
│   ├── MainActivity.kt                  # Single-Activity host; back-stack nav, AnimatedContent routing
│   │
│   ├── net/
│   │   ├── WifiDirectManager.kt         # WifiP2pManager wrapper — discovery, connect, BroadcastReceiver
│   │   ├── FileTransfer.kt              # FileReceiveServer (NIO) + FileSenderClient + ProgressAggregator
│   │   ├── QrPairing.kt                 # ZXing QR encode/decode · SPARKS payload format
│   │   └── DeviceFiles.kt               # MediaStore queries for all file categories
│   │
│   ├── ui/
│   │   ├── PulseViewModel.kt            # AndroidViewModel — all app state, Wi-Fi Direct, transfers
│   │   ├── SleekComponents.kt           # Shared Composables: TopBar, BottomNav, GlassCard, AuroraBackground, RadarPulseRing, FileProgressRow
│   │   ├── GroupedList.kt               # Category-grouped file list composable
│   │   ├── PulseIcons.kt                # Custom vector icon definitions
│   │   │
│   │   ├── theme/
│   │   │   ├── Color.kt                 # SleekPalette data class + CompositionLocal + dark/light palettes
│   │   │   ├── Theme.kt                 # SparkTheme — MaterialTheme wrapper with SleekPalette provision
│   │   │   ├── Type.kt                  # Typography scale
│   │   │   └── Shapes.kt                # Shape tokens (PillShape etc.)
│   │   │
│   │   └── screens/
│   │       ├── SplashScreen.kt          # Animated logo splash → onboarding or dashboard
│   │       ├── OnboardingScreen.kt      # First-run permission walkthrough
│   │       ├── DashboardScreen.kt       # Home — send/receive cards, recent transfers
│   │       ├── SelectFilesScreen.kt     # File picker with category tabs and multi-select
│   │       ├── SendScreen.kt            # Device discovery + Wi-Fi Direct connect + send trigger
│   │       ├── TransferringScreen.kt    # Live per-file + overall progress during send
│   │       ├── ReceiveScreen.kt         # Listening state, QR display shortcut, incoming progress
│   │       ├── MyQrScreen.kt            # Full-screen QR code for this device
│   │       ├── ScanQrScreen.kt          # CameraX live QR scanner
│   │       ├── HistoryScreen.kt         # Room-backed transfer log with filter chips
│   │       └── SettingsScreen.kt        # Theme, device name, storage, notifications
│   │
│   └── data/
│       ├── PulseDatabase.kt             # Room database (v2), singleton pattern
│       ├── PulseDao.kt                  # DAO — insert, getAllTransfers, getAllFiles, clearAll
│       ├── TransferEntity.kt            # Room entity: transfer log record
│       └── FileItemEntity.kt            # Room entity: cached file browser item
│
├── app/src/main/AndroidManifest.xml     # Permissions: Wi-Fi Direct, Camera, Storage, Notifications
├── app/build.gradle.kts                 # Compose BOM, Room KSP, CameraX, ZXing, Coroutines
├── gradle/libs.versions.toml            # Version catalog
└── README.md                            # This file
```

---

## ⚙️ Setup

### Requirements
- **Android Studio Hedgehog** or newer
- **minSdkVersion 24** (Android 7.0 Nougat)
- **targetSdkVersion 36**
- Kotlin · Jetpack Compose · KSP

### Dependencies (key)

```toml
# gradle/libs.versions.toml
androidx-compose-bom = "2024.x"
androidx-room        = "2.6.x"
androidx-camera      = "1.3.x"
zxing-core           = "3.5.x"
accompanist-permissions = "0.34.x"
kotlinx-coroutines   = "1.7.x"
```

### Permissions (AndroidManifest)

```xml
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

> On **Android 13+**, `NEARBY_WIFI_DEVICES` replaces the location permission for Wi-Fi Direct peer discovery. The manifest handles both cases.

---

## 🔄 Transfer Flow

```
Receiver                          Sender
────────                          ──────
1. Opens ReceiveScreen            
2. Starts FileReceiveServer       
   (NIO ServerSocketChannel)      
3. Generates QR:                  
   SPARKS|name|ip|port            
                                  4. Scans QR (CameraX + ZXing)
                                  5. QrPairing.parsePayload()
                                  6. Sets targetIp / targetPort
                                  7. Picks files (SelectFilesScreen)
                                  8. sendFiles() → FileSenderClient
                                     coroutineScope { async per file }
                                     writes: fileName\n | fileSize | bytes
9. Accepts connection             ←────────────────────────────────
10. Reads header + bytes
11. Saves file to disk
12. Inserts TransferEntity        
    (Room)                        → Inserts TransferEntity (Room)
```

---

## 📋 Changelog

### v1.0.0 — Initial Release *(current)*
- `NEW` Wi-Fi Direct peer discovery + connect via `WifiDirectManager`
- `NEW` QR Code pairing (`QrPairing` encode/decode, ZXing + CameraX)
- `NEW` Multi-file parallel send with per-file + overall progress
- `NEW` NIO `ServerSocketChannel` receive server
- `NEW` Room v2 database — transfer history + file cache
- `NEW` Full Compose UI — 11 screens, animated transitions
- `NEW` `SleekPalette` custom M3 design system (dark + light)
- `NEW` `GlassCard`, `AuroraBackground`, `RadarPulseRing` custom components
- `NEW` `PulseViewModel` — single ViewModel for all state
- `NEW` `DeviceFiles` MediaStore query for all file categories
- `NEW` Onboarding permission flow on first launch
- `NEW` Settings: theme, device name, storage path, notifications

---

## 🔒 Privacy

- ✅ **Fully offline** — no internet connection required for transfers
- ✅ **No cloud storage** — files go directly device-to-device
- ✅ **No account or login**
- ✅ **No analytics or tracking SDKs**
- ✅ **All history stored locally** (Room / SQLite)
- ✅ **No ads · No subscriptions · Completely free**

---

## 📄 License

```
MIT License — Copyright (c) 2026 Willykez

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software to use, copy, modify, merge, publish,
distribute and/or sell copies, subject to the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```

---

## 📬 Contact

**Developer:** Willykez  
**GitHub:** [@Willykez](https://github.com/Willykez)  
**Package:** `com.willyshare.willykez`

---

<p align="center">
  Made with ⚡ in Tanzania 🇹🇿 · If this helped you, please ⭐ the repo!
</p>
