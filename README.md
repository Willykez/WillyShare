<div align="center">

```
██╗    ██╗██╗██╗     ██╗  ██╗   ██╗███████╗██╗  ██╗ █████╗ ██████╗ ███████╗
██║    ██║██║██║     ██║  ╚██╗ ██╔╝██╔════╝██║  ██║██╔══██╗██╔══██╗██╔════╝
██║ █╗ ██║██║██║     ██║   ╚████╔╝ ███████╗███████║███████║██████╔╝█████╗  
██║███╗██║██║██║     ██║    ╚██╔╝  ╚════██║██╔══██║██╔══██║██╔══██╗██╔══╝  
╚███╔███╔╝██║███████╗███████╗██║   ███████║██║  ██║██║  ██║██║  ██║███████╗
 ╚══╝╚══╝ ╚═╝╚══════╝╚══════╝╚═╝   ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝
```

**Blazing-fast peer-to-peer file sharing for Android**  
*WiFi Direct · 8-Thread Transfer Engine · QuickShare-style Discovery*

[![Release](https://img.shields.io/github/v/release/Willykez/WillyShare?color=1A6FEE&label=Latest%20Release&style=for-the-badge)](https://github.com/Willykez/WillyShare/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%208%2B-3DDC84?style=for-the-badge&logo=android)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-0D1B2A?style=for-the-badge)](LICENSE)
[![Language](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk)](https://openjdk.org)

</div>

---

## ⚡ What is WillyShare?

WillyShare is a **no-internet, no-cloud, zero-compromise** file sharing app built for speed. Drop a file on your phone, tap the device you want to send to, and watch it fly — no accounts, no ads, no throttling.

Under the hood it runs an **8-thread parallel transfer engine** over a **WiFi Direct 5 GHz link** — the same technology that powers Xender's 5G Speed mode and Google's QuickShare — giving you real-world speeds that can hit **80–200 MB/s** on modern hardware.

---

## 🎬 Flow at a Glance

```
┌─────────────────────────────────────────────────────────────┐
│  SENDER                              RECEIVER               │
│                                                             │
│  [Send]  ──────────────────────────► [Receive]             │
│     │                                    │                  │
│  WiFi Direct                         WiFi Direct           │
│  peer scan (~2s)                     "You're Visible"      │
│     │                                    │                  │
│  Devices appear                      Waiting card          │
│  in live list   ◄── P2P handshake ──► auto-accepted        │
│     │                                    │                  │
│  Tap device                          TransferActivity       │
│     │                                    │                  │
│  Pick files ──── 8 TCP streams ────► Real-time progress    │
│                   (5 GHz link)           │                  │
│                                      Downloads/WillyShare/  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Features

### Transfer Engine
| Feature | Detail |
|---|---|
| **8 Parallel TCP Streams** | Each file split across 8 threads — saturates the WiFi NIC |
| **4 MB Read Buffers** | Dramatically fewer syscalls per MB vs the typical 64–512 KB default |
| **TCP Socket Tuning** | `TCP_NODELAY` + 4 MB `SO_SNDBUF`/`SO_RCVBUF` — eliminates kernel buffer bottleneck |
| **ACK Handshake** | Receiver pre-allocates file on disk before sender streams — no write races |
| **File-List Announcement** | Sender announces all files + total bytes upfront so receiver shows progress immediately |
| **Multi-file Queue** | Send entire folders in one session; each file transfers sequentially with an overall "File N of M" counter |
| **Pause / Resume** | Freeze any transfer mid-flight and resume exactly where it left off |
| **Cancel & Cleanup** | Graceful cancel with history entry marked accordingly |

### Device Discovery (QuickShare-style)
| Feature | Detail |
|---|---|
| **WiFi Direct P2P** | No hotspot, no SSID/password — devices find each other automatically |
| **2–5 Second Discovery** | P2P peer scan vs 12–15 seconds for Bluetooth |
| **5 GHz Direct Link** | GO intent set to prefer 5 GHz band after P2P group formation |
| **Sender scans, Receiver waits** | Sender sees a live device list; receiver sees a "You're Visible" waiting card — exactly like QuickShare / Xender 5G |
| **Status Badges** | Each discovered device shows Available / Connected badge in real time |
| **Auto-navigate on Connect** | Both sides jump straight to TransferActivity the moment P2P handshake completes |

### Speed Display
| Feature | Detail |
|---|---|
| **EMA Speed Smoothing** | Exponential Moving Average (α=0.25) — no jarring speed jumps |
| **200ms Update Interval** | Near real-time speed readout (was 500 ms) |
| **ETA Counter** | "~3s remaining" calculated live from current EMA speed |
| **Peak Speed** | Shown after transfer completes |
| **Receiver-side Progress** | Live progress bar + bytes received on receiver (was completely missing before) |

### UI & UX
| Feature | Detail |
|---|---|
| **Animated Radar** | Pulsing concentric rings during scan |
| **Staggered Device Entrance** | Each device card slides in with a 60 ms offset |
| **Smooth Progress Bar** | `countUpProgress` animator — no jump cuts |
| **Dark / Light Mode** | Full `DayNight` theme with `values-night/` color set |
| **Slide Transitions** | Every screen transition has matching enter/exit animations |
| **Transfer History** | SQLite log of every send/receive with file name, size, status, timestamp |

### Architecture
| Layer | Detail |
|---|---|
| **TransferEngine** | Pure Java, zero Android-framework dependencies — unit-testable |
| **DeviceDiscoveryManager** | Encapsulates all `WifiP2pManager` lifecycle — register/unregister per Activity |
| **TransferService** | Foreground service keeps transfers alive when app is backgrounded |
| **DatabaseHelper** | SQLite via `SQLiteOpenHelper` — no Room, no ORM overhead |
| **SpeedCalculator** | Thread-safe EMA meter, resets cleanly per file |
| **AnimUtils** | Central animation helpers — pulse, slideUp, fadeIn, buttonPress, countUpProgress |

---

## 📱 Screenshots

> _Coming soon — UI screenshots and screen-recording GIF_

---

## 🛠️ Build

```bash
# Clone
git clone https://github.com/Willykez/WillyShare.git
cd WillyShare

# Open in Android Studio (Giraffe or later recommended)
# Or build from CLI:
./gradlew assembleDebug
```

**Min SDK:** API 26 (Android 8.0)  
**Target SDK:** API 34 (Android 14)  
**Language:** Java  
**Architecture:** Single-activity per screen, no Jetpack Compose

---

## 📋 Permissions

| Permission | Why |
|---|---|
| `NEARBY_WIFI_DEVICES` (API 33+) | WiFi Direct peer scan |
| `ACCESS_FINE_LOCATION` | Required by WiFi Direct on API ≤ 32 |
| `CHANGE_WIFI_STATE` / `ACCESS_WIFI_STATE` | P2P group management |
| `CHANGE_NETWORK_STATE` | WiFi Direct group teardown |
| `READ_MEDIA_*` / `MANAGE_EXTERNAL_STORAGE` | File picker & saving received files |
| `FOREGROUND_SERVICE` | Keeps transfers running in background |
| `POST_NOTIFICATIONS` | Transfer progress notification |
| `BLUETOOTH_*` | Legacy Bluetooth stack (kept for compatibility) |

---

## 🗂️ Project Structure

```
WillyShare/
└── app/src/main/
    ├── java/com/willykez/willyshare/
    │   ├── TransferEngine.java          ← 8-thread transfer core
    │   ├── DeviceDiscoveryManager.java  ← WiFi Direct P2P discovery
    │   ├── SpeedCalculator.java         ← EMA speed meter
    │   ├── TransferService.java         ← Foreground service
    │   ├── ScanActivity.java            ← Send scan / Receive wait screen
    │   ├── TransferActivity.java        ← Live transfer progress
    │   ├── FilePickerActivity.java      ← Multi-file selector
    │   ├── HistoryActivity.java         ← Transfer log
    │   ├── MainActivity.java            ← Home screen
    │   ├── PermissionActivity.java      ← Onboarding permissions
    │   ├── HotspotManager.java          ← WiFi Direct constants
    │   ├── DatabaseHelper.java          ← SQLite history
    │   ├── AnimUtils.java               ← Animation helpers
    │   └── FileUtils.java               ← File size / MIME / paths
    └── res/
        ├── layout/                      ← XML layouts
        ├── drawable/                    ← Custom backgrounds & progress
        ├── anim/                        ← Transition & motion XML
        └── values[-night]/              ← Color tokens (light + dark)
```

---

## 📦 Releases

| Version | Highlights |
|---|---|
| **v2.0.0** | WiFi Direct discovery, 8-thread engine, 4 MB buffers, EMA speed, receiver progress, ETA, QuickShare UX |
| v1.0.0 | Initial release — Bluetooth discovery, 4-thread engine |

---

## 👨‍💻 Author

**Willykez** — Android & Web Developer, Tanzania  
GitHub: [@Willykez](https://github.com/Willykez)

---

<div align="center">

_Built with ☕ Java and a strong opinion about transfer speeds._

</div>
