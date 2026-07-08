# HaioBypass Android — Implementation Plan

## Overview

Port HaioBypass desktop (Tauri v2 / Rust) to Android using a VPN-based approach. Same behavior: route specific bypass domains through Trojan proxy, everything else goes direct.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     HaioBypass Android (Kotlin)                  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   VpnService                              │   │
│  │  - TUN interface (10.0.0.2/24, MTU 1500)                 │   │
│  │  - Routes all traffic (0.0.0.0/0) through TUN            │   │
│  │  - DNS set to 10.0.0.1 (our fake DNS)                    │   │
│  │  - Foreground notification (required Android 8+)          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           |                                      │
│  ┌────────────────────────▼─────────────────────────────────┐   │
│  │           Fake DNS Server (Kotlin, UDP :53)               │   │
│  │  - Intercepts all DNS queries from TUN                   │   │
│  │  - Bypass domains → fake IP from 198.18.0.0/15           │   │
│  │  - Maintains ConcurrentHashMap<fakeIP, hostname>          │   │
│  │  - Non-bypass domains → forward to real DNS (direct)      │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           |                                      │
│  ┌────────────────────────▼─────────────────────────────────┐   │
│  │     hev-socks5-tunnel (Native C, libhev-socks5-tunnel.so) │   │
│  │  - Reads raw IP packets from TUN fd                       │   │
│  │  - Reassembles TCP/UDP sessions                           │   │
│  │  - Forwards ALL sessions to SOCKS5 (127.0.0.1:10808)     │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           |                                      │
│  ┌────────────────────────▼─────────────────────────────────┐   │
│  │          Xray-core (Go binary, Trojan outbound)           │   │
│  │  - SOCKS5 inbound on 127.0.0.1:10808                     │   │
│  │  - Routing rules:                                         │   │
│  │      domain in bypass list → Trojan outbound              │   │
│  │      domain NOT in list   → direct outbound               │   │
│  │  - Trojan: TLS 1.3, password auth, SNI, ALPN h2+http1.1 │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           |                                      │
│                    Trojan Server                                  │
│              (resolves DNS, connects to destination)             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Flow

```
1. App queries DNS for "youtube.com"
2. Fake DNS intercepts → returns 198.18.0.42 (fake IP)
3. Stores mapping: 198.18.0.42 ↔ youtube.com
4. App connects to 198.18.0.42:443
5. TUN captures IP packet
6. hev-socks5-tunnel reassembles TCP → SOCKS5 CONNECT
7. Xray-core receives SOCKS5 request for 198.18.0.42
8. Xray routing: fakeIP → recovers domain "youtube.com"
9. "youtube.com" matches bypass list → Trojan outbound
10. Trojan server resolves youtube.com → connects to real IP
11. Encrypted tunnel established
```

---

## Project Structure

```
HaioBypass-Android/
├── app/
│   ├── src/main/
│   │   ├── java/com/haio/bypass/
│   │   │   ├── HaioApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── HaioVpnService.kt
│   │   │   │   └── VpnNotification.kt
│   │   │   │
│   │   │   ├── dns/
│   │   │   │   ├── FakeDnsServer.kt
│   │   │   │   └── FakeIpMap.kt
│   │   │   │
│   │   │   ├── proxy/
│   │   │   │   ├── XrayManager.kt
│   │   │   │   ├── XrayConfigGenerator.kt
│   │   │   │   ├── HevTunManager.kt
│   │   │   │   └── ProxyManager.kt
│   │   │   │
│   │   │   ├── domain/
│   │   │   │   ├── DomainRouter.kt
│   │   │   │   ├── DomainFetcher.kt
│   │   │   │   └── DomainStore.kt
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.kt
│   │   │   │   ├── ConfigManager.kt
│   │   │   │   └── TrojanUrlParser.kt
│   │   │   │
│   │   │   └── ui/
│   │   │       ├── theme/
│   │   │       │   ├── Theme.kt
│   │   │       │   ├── Color.kt
│   │   │       │   └── Type.kt
│   │   │       ├── screens/
│   │   │       │   ├── MainScreen.kt
│   │   │       │   ├── ConfigScreen.kt
│   │   │       │   ├── DomainsScreen.kt
│   │   │       │   └── SettingsScreen.kt
│   │   │       └── components/
│   │   │           ├── ProxyToggle.kt
│   │   │           ├── StatusCard.kt
│   │   │           └── DomainList.kt
│   │   │
│   │   ├── res/
│   │   │   ├── raw/
│   │   │   │   ├── xray-arm64-v8a
│   │   │   │   ├── xray-armeabi-v7a
│   │   │   │   ├── libhev-socks5-tunnel.so
│   │   │   │   └── geoip.dat
│   │   │   ├── xml/
│   │   │   │   └── network_security_config.xml
│   │   │   └── drawable/
│   │   │       └── ic_vpn_key.xml
│   │   │
│   │   └── AndroidManifest.xml
│   │
│   ├── build.gradle.kts
│   └── proguard-rules.pro
│
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Dependencies

```toml
[versions]
kotlin = "1.9.22"
compose-bom = "2024.02.00"
okhttp = "4.12.0"
coroutines = "1.7.3"
serialization = "1.6.2"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version = "2.7.7" }
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version = "2.7.0" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-coroutines = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
datastore = { group = "androidx.datastore", name = "datastore-preferences", version = "1.0.0" }
work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version = "2.9.0" }
```

---

## Phase 1: Project Scaffold + Native Binary Setup

### Tasks
- [ ] Create Android project (Kotlin, minSdk 26, targetSdk 34)
- [ ] Configure `build.gradle.kts` with Compose, OkHttp, Coroutines, Serialization
- [ ] Set up `libs.versions.toml` version catalog
- [ ] Configure `AndroidManifest.xml` with permissions:
  - `android.permission.INTERNET`
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
  - `android.permission.RECEIVE_BOOT_COMPLETED`
- [ ] Declare `HaioVpnService` with `android:permission="android.permission.BIND_VPN_SERVICE"`
- [ ] Add VPN intent filter: `android.net.action.VPN_SETTINGS`
- [ ] Copy pre-built Xray-core binaries to `app/src/main/res/raw/`:
  - `xray-arm64-v8a` (for arm64 devices)
  - `xray-armeabi-v7a` (for arm32 devices)
- [ ] Build or download `libhev-socks5-tunnel.so`:
  ```bash
  git clone --recursive https://github.com/heiher/hev-socks5-tunnel
  cd hev-socks5-tunnel
  ndk-build APP_ABI=arm64-v8a,armeabi-v7a
  ```
- [ ] Place built `.so` files in `app/src/main/jniLibs/arm64-v8a/` and `armeabi-v7a/`
- [ ] Create `network_security_config.xml` to allow cleartext to localhost

### Build Xray-core for Android
```bash
# ARM64
CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build \
  -trimpath -ldflags "-s -w" \
  -o xray-arm64-v8a ./main

# ARM32
CGO_ENABLED=0 GOOS=android GOARCH=arm go build \
  -trimpath -ldflags "-s -w" \
  -o xray-armeabi-v7a ./main
```

---

## Phase 2: VpnService + Fake DNS

### Tasks
- [ ] Implement `HaioVpnService.kt`:
  - Build TUN interface with `VpnService.Builder`
  - Address: `10.0.0.2/24`, DNS: `10.0.0.1`, Route: `0.0.0.0/0`, MTU: 1500
  - Start foreground notification
  - Start fake DNS, Xray-core, hev-socks5-tunnel in order
  - Handle `onRevoke()` for clean shutdown
  - Handle `onDestroy()` for cleanup
- [ ] Implement `VpnNotification.kt`:
  - Notification channel for Android 8+
  - Persistent notification showing "HaioBypass Active"
  - Tap action opens MainActivity
- [ ] Implement `FakeDnsServer.kt`:
  - UDP server on port 53 bound to `10.0.0.1`
  - Parse DNS query packets (RFC 1035)
  - Check if queried domain is in bypass list
  - Bypass domains → return fake IP from `198.18.0.0/15`
  - Non-bypass → forward to real DNS `8.8.8.8` via direct socket
  - Build proper DNS response with A record
- [ ] Implement `FakeIpMap.kt`:
  - `ConcurrentHashMap<String, InetAddress>` (hostname → fakeIP)
  - `ConcurrentHashMap<InetAddress, String>` (fakeIP → hostname)
  - Auto-incrementing counter starting at `198.18.0.1`
  - `getOrCreate(hostname): InetAddress`
  - `lookup(fakeIp): String?`
  - `remove(hostname)` for cleanup

### DNS Packet Format (Reference)
```
Query: [ID(2)][Flags(2)][QDCOUNT(2)][ANCOUNT(2)][NSCOUNT(2)][ARCOUNT(2)][QNAME...][QTYPE(2)][QCLASS(2)]
Response: [ID(2)][Flags(84...response)][QDCOUNT(2)][ANCOUNT(1)][NSCOUNT(0)][ARCOUNT(0)]
          [QNAME...][QTYPE(2)][QCLASS(2)]
          [NAME(2, pointer to QNAME)][TYPE(1, A)][CLASS(1, IN)][TTL(4)][RDLENGTH(2)][RDATA(4, IP)]
```

---

## Phase 3: Xray-core Integration

### Tasks
- [ ] Implement `XrayManager.kt`:
  - Extract Xray binary from `res/raw/` to `context.filesDir/xray` on first run
  - Set executable permission (`chmod 755`)
  - `start(configJson: String)`: write config to file, spawn process
  - `stop()`: destroy process
  - Monitor process lifecycle, auto-restart on crash
  - Redirect stdout/stderr to log file
- [ ] Implement `XrayConfigGenerator.kt`:
  - `generateConfig(socksPort, trojanConfig, bypassDomains)` → JSON string
  - SOCKS5 inbound on `127.0.0.1:10808`
  - Trojan outbound with TLS 1.3, SNI, ALPN
  - Routing rules: bypass domains → `proxy` outbound, everything else → `direct`
  - Fake-IP DNS recovery in routing strategy
- [ ] Implement `HevTunManager.kt`:
  - Load `libhev-socks5-tunnel.so` via `System.loadLibrary()`
  - JNI bridge to pass TUN fd and SOCKS5 port
  - `start(tunFd, socksPort)`: initialize hev-socks5-tunnel
  - `stop()`: cleanup
  - Native methods declared in Kotlin, implemented in C via JNI
- [ ] Implement `ProxyManager.kt`:
  - Orchestrates startup order: fakeDNS → Xray → hevTun
  - Orchestrates shutdown in reverse order
  - Exposes connection state to UI
  - Handles error recovery

### Xray Config Template
```json
{
  "log": { "loglevel": "warning" },
  "dns": {
    "servers": [
      { "address": "8.8.8.8", "domains": ["geosite:private"] },
      "localhost"
    ]
  },
  "inbounds": [
    {
      "tag": "socks-in",
      "port": 10808,
      "listen": "127.0.0.1",
      "protocol": "socks",
      "settings": { "udp": true }
    }
  ],
  "outbounds": [
    {
      "tag": "proxy",
      "protocol": "trojan",
      "settings": {
        "servers": [{
          "address": "${server}",
          "port": ${port},
          "password": "${password}"
        }]
      },
      "streamSettings": {
        "network": "tcp",
        "security": "tls",
        "tlsSettings": {
          "serverName": "${sni}",
          "alpn": ["h2", "http/1.1"],
          "fingerprint": "chrome"
        }
      }
    },
    { "tag": "direct", "protocol": "freedom" },
    { "tag": "block", "protocol": "blackhole" }
  ],
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      {
        "type": "field",
        "domain": ["domain:youtube.com", "domain:twitter.com", ...],
        "outboundTag": "proxy"
      },
      { "type": "field", "outboundTag": "direct" }
    ]
  }
}
```

---

## Phase 4: Domain List Management (Port from Rust)

### Tasks
- [ ] Port `router.rs` → `DomainRouter.kt`:
  - `ConcurrentHashMap.newKeySet<String>()` for domain set
  - `ConcurrentHashMap<String, MutableSet<String>>` for suffix index
  - `setDomains(list)`: replace atomically, rebuild suffix index
  - `shouldProxy(host)`: exact match (O(1)) → suffix index → full scan fallback
  - `extractTwoPartSuffix(domain)`: split on `.`, join last two parts
  - Thread-safe via `ConcurrentHashMap` (no explicit locks needed)
- [ ] Implement `DomainFetcher.kt`:
  - Single hardcoded URL: `https://tools.haiocloud.com/domains.txt`
  - `parseDomainText(text)`: trim, lowercase, filter comments/invalid/DNS-infra
  - OkHttp with 10s timeout, `Cache-Control: no-cache` header
  - Return `List<String>` of valid domains
  - On failure: return empty list (no fallback domains)
- [ ] Implement `DomainStore.kt`:
  - In-memory domain list + suffix index
  - Persist to SharedPreferences as JSON
  - `setDomains(list)`: update memory + disk
  - `getDomains()`: return current list
  - `getLastFetchTime()` / `setLastFetchTime()`
- [ ] Implement domain refresh via `WorkManager`:
  - `PeriodicWorkRequest` every 60 minutes
  - Fetch → parse → update store → notify VPN service
  - `ExistingPeriodicWorkPolicy.KEEP`

### Domain Matching Algorithm (from Rust)
```
shouldProxy("www.news.google.com"):
  1. Exact match: "www.news.google.com" in set? NO
  2. Suffix index: extract "google.com" → lookup → {google.com}
     Check: "www.news.google.com" == "google.com"? NO
            "www.news.google.com" ends with ".google.com"? YES → return true
  3. Fallback scan: not reached

shouldProxy("example.com"):
  1. Exact match: "example.com" in set? NO
  2. Suffix index: extract "example.com" → lookup → null
  3. Fallback: no domain ends with ".example.com" → return false
```

---

## Phase 5: Trojan URL Parser + Config Persistence

### Tasks
- [ ] Port `trojan_url.rs` → `TrojanUrlParser.kt`:
  - Regex: `(?i)^trojan://([^@]+)@([^:/?#]+):(\d+)(?:\?([^#]*))?`
  - URL-decode password
  - Extract SNI from query params, default to server hostname
  - Validate: password, server, port must be non-empty/non-zero
  - Return `TrojanConfig?` data class
- [ ] Implement `AppConfig.kt`:
  ```kotlin
  data class AppConfig(
      val enabled: Boolean = false,
      val trojanUrl: String = "",
      val trojanConfig: TrojanConfig? = null,
      val socksPort: Int = 10808,
      val cachedDomains: List<String> = emptyList(),
      val lastFetchTime: Long = 0,
      val autostart: Boolean = false
  )
  ```
- [ ] Implement `ConfigManager.kt`:
  - SharedPreferences wrapper
  - Serialize/deserialize `AppConfig` to JSON
  - Auto-save on change
  - Migration: old port numbers (10808/10809) → new defaults (11031/11032) [note: on Android, ports are different from desktop]

---

## Phase 6: UI with Jetpack Compose

### Tasks
- [ ] Set up `ui/theme/`:
  - `Theme.kt`: Material 3 dynamic color or custom theme
  - `Color.kt`: Brand colors (green for active, gray for inactive)
  - `Type.kt`: Typography scale
- [ ] Implement `MainScreen.kt`:
  - Large toggle switch (center of screen)
  - Status card: connected/disconnected, server info, domain count
  - Bottom navigation: Main, Domains, Settings
- [ ] Implement `ConfigScreen.kt`:
  - Trojan URL input field (multiline, paste support)
  - "Parse & Save" button
  - Parsed server info display (server, port, SNI)
  - Connection test button
- [ ] Implement `DomainsScreen.kt`:
  - Scrollable list of bypass domains
  - Search/filter bar
  - Manual domain add/remove
  - Refresh button (fetch from remote)
  - Last fetch timestamp display
- [ ] Implement `SettingsScreen.kt`:
  - Auto-start on boot toggle
  - Port configuration (advanced)
  - About / version info
  - Clear config button
- [ ] Implement `components/`:
  - `ProxyToggle.kt`: Animated circular toggle with ON/OFF state
  - `StatusCard.kt`: Card showing connection status, server, latency
  - `DomainList.kt`: LazyColumn with swipe-to-delete

### Screen Layout
```
┌─────────────────────────┐
│  HaioBypass          ⚙️  │  ← Top bar with settings icon
├─────────────────────────┤
│                         │
│    ┌───────────────┐    │
│    │               │    │
│    │   ◉ ON/OFF    │    │  ← Big toggle switch
│    │               │    │
│    └───────────────┘    │
│                         │
│  ┌───────────────────┐  │
│  │ 🟢 Connected      │  │  ← Status card
│  │ Server: x.x.x.x  │  │
│  │ SNI: example.com  │  │
│  │ 42 domains active │  │
│  └───────────────────┘  │
│                         │
│  [Buy Config]           │  ← Link to console.haio.ir
│                         │
├─────────────────────────┤
│  🏠    🌐    ⚙️         │  ← Bottom nav
│ Main  Domains Settings  │
└─────────────────────────┘
```

---

## Phase 7: Background Tasks + Polish

### Tasks
- [ ] Implement boot receiver:
  - `BootReceiver.kt`: listen for `BOOT_COMPLETED`
  - Auto-start VPN if `autostart` is enabled in config
  - Requires `RECEIVE_BOOT_COMPLETED` permission
- [ ] Implement crash recovery:
  - Write sentinel file when VPN is active
  - On app restart, check sentinel and offer to restore
- [ ] Add health monitoring:
  - Periodic check that Xray process is alive
  - Auto-restart if crashed
  - Notify user if restart fails
- [ ] Add per-app proxy (optional):
  - UI to select which apps bypass the VPN
  - Use `VpnService.Builder.addDisallowedApplication()`
- [ ] Add latency test:
  - TCP connect to Trojan server, measure round-trip
  - Display in status card

---

## Phase 8: Testing + APK Signing

### Tasks
- [ ] Test on physical device (Android 8+)
- [ ] Test VPN connection lifecycle (connect/disconnect/reconnect)
- [ ] Test domain routing (bypass domains vs direct)
- [ ] Test DNS resolution (fake IP + real DNS)
- [ ] Test Trojan URL parsing (various formats)
- [ ] Test domain fetch from remote URL
- [ ] Test auto-start on boot
- [ ] Test notification behavior
- [ ] Configure release signing:
  - Generate keystore or use existing
  - Configure `build.gradle.kts` signing config
  - Enable ProGuard/R8 minification
- [ ] Build release AAB/APK
- [ ] Test APK on multiple devices (arm64, arm32)

---

## Key Differences from Desktop

| Feature | Desktop (Tauri) | Android |
|---------|-----------------|---------|
| Traffic capture | OS system proxy | VpnService + TUN |
| Protocol core | trojan-go binary | Xray-core binary |
| Domain routing | Custom HTTP proxy + HashSet | Xray routing rules + fake-IP DNS |
| TUN bridge | N/A (proxy-based) | hev-socks5-tunnel |
| Config storage | `~/.haiobypass/state.json` | SharedPreferences |
| Domain refresh | Tokio background task | WorkManager periodic task |
| System tray | Tauri TrayIcon | Foreground notification |
| Auto-start | OS-specific | Boot broadcast receiver |
| Dev-tool injection | Gradle/Maven/pip/Docker/curl | N/A (not applicable on mobile) |

---

## Port Mapping

| Port | Protocol | Purpose |
|------|----------|---------|
| 53 | UDP | Fake DNS server |
| 10808 | SOCKS5 | Xray-core SOCKS5 inbound |
| 10053 | UDP | Real DNS forwarder (internal) |

---

## Native Binary Sources

| Binary | Source | License |
|--------|--------|---------|
| Xray-core | github.com/XTLS/Xray-core | MPL-2.0 |
| hev-socks5-tunnel | github.com/heiher/hev-socks5-tunnel | BSD-2-Clause |

---

## Estimated Timeline

| Phase | Description | Days |
|-------|-------------|------|
| 1 | Project scaffold + native binaries | 1 |
| 2 | VpnService + Fake DNS | 2-3 |
| 3 | Xray-core integration | 1-2 |
| 4 | Domain list management | 1 |
| 5 | Trojan URL parser + config | 0.5 |
| 6 | UI with Jetpack Compose | 2-3 |
| 7 | Background tasks + polish | 1 |
| 8 | Testing + APK signing | 1-2 |
| **Total** | | **~10-13 days** |

---

## Reference Projects

- **v2rayNG** (github.com/2dust/v2rayNG) — Xray + hev-socks5-tunnel on Android
- **NekoBox** (github.com/MatsuriDayo/NekoBoxForAndroid) — sing-box + gVisor on Android
- **Anywhere-Android** (github.com/NodePassProject/Anywhere-Android) — Pure Kotlin Trojan
- **hev-socks5-tunnel** (github.com/heiher/hev-socks5-tunnel) — TUN to SOCKS5 bridge
