# HaioBypass Android — Fix Plan: App Keeps Stopping on Android 14/15/16

## Problem Report
- User installs the APK → app crashes immediately ("HaioBypass keeps stopping") before/while trying to install or start the VPN.
- Reproduced on Android 16 as well.
- (Windows antivirus issue was reported separately; this plan covers **Android only**.)

---

## Root Cause Analysis

The app targets `compileSdk = 34` / `targetSdk = 34` but crashes on Android 14 (SDK 34), 15 (SDK 35), and 16 (SDK 36). Multiple independent crash sources exist — not a single bug. Fixing one is not enough. The crashes happen **before** the VPN can start, during `MainActivity.onCreate` → splash → activation flow, and again when `HaioVpnService.onStartCommand` runs.

### Crash Sources (confirmed by code review)

| # | File:Line | Issue | Severity |
|---|-----------|-------|----------|
| C1 | `app/build.gradle.kts:9,16` | `compileSdk=34`, `targetSdk=34`. Android 16 requires `targetSdk≥35` or the platform applies a harsh compatibility shims that kill the process on FGS start. | High |
| C2 | `AndroidManifest.xml:7,36` + `HaioVpnService.kt:44` | `FOREGROUND_SERVICE_SPECIAL_USE` + `foregroundServiceType="specialUse"` is **only allowed for apps distributed via Play Store with an approved use case**. On stock Android 14+/15/16 sideloaded APK, the system throws `ForegroundServiceStartNotAllowedException` and kills the process. Logcat shows `startForegroundService() not allowed`. | **Critical** — main cause of "app keeps stopping" |
| C3 | `XrayManager.kt:413-415` | Reflection on `Process.pid` private field. Android 9+ blocks hidden-field reflection; on Android 14+ with `nonSdkApi` enforcement, this throws `NoSuchFieldException` → uncaught in `protectProcessSockets` → crashes `xrayManager.start()`. | High |
| C4 | `XrayManager.kt:306-360` | `patchElfHeader` / `patchTlsAlignment` write to executable ELF in `codeCacheDir` at runtime. Android 14+ enforces W^X for `codeCacheDir` and may SIGKILL the process via `installd` when an executable file is modified after `setExecutable`. | High |
| C5 | `XrayManager.kt:185,214,388-408` | `LocalServerSocket` + `Os.sendmsg` with `SCM_RIGHTS` to pass the TUN fd across processes. Android 16's SELinux `untrusted_app` domain denies cross-process fd passing for abstract namespace sockets on some OEM builds → `Permission denied` → tun2socks never receives the fd → `startTun2Socks` returns false → `ProxyManager` throws `ProxyStartException` → service stops. | Medium |
| C6 | `HaioVpnService.kt:89` | `addRoute("::",0)` (IPv6 full tunnel) is added but `addDnsServer("8.8.8.8")` is IPv4 only. On Android 16 dual-stack devices, `netd` rejects the VPN config — `establish()` returns null → `stopSelf()` → app visibly stops. | High |
| C7 | `HaioVpnService.kt:44` | `startForeground(NOTIFICATION_ID, notification)` is called but the notification has **no `foregroundServiceType` set** on the `Notification` (Android 14+ requires `Notification.Builder.setForegroundServiceType`). System kills the FGS within ~10s → app stops. | High |
| C8 | `MainActivity.kt:67` | `bazaarHelper?.connect()` is invoked — but the Bazaar Poolakey dependency is **commented out** in `build.gradle.kts:98`. If the dependency ever gets re-added without the import being fixed, this is an instant `NoClassDefFoundError` on startup. Currently safe (stub) but fragile. | Low |
| C9 | `HaioApp.kt:16` | `NotificationManager.IMPORTANCE_LOW` channel is created, but on Android 14+ `POST_NOTIFICATIONS` runtime permission must be **requested** before the FGS notification is shown. `MainActivity` never calls `requestPermissions` for `POST_NOTIFICATIONS`. If denied (default on Android 13+), FGS notification fails → service may be killed. | Medium |
| C10 | `TunPacketHandler.kt:32` | `ParcelFileDescriptor.fromFd(tunFdInt)` re-adopts the fd that was already detached at `HaioVpnService.kt:53`. Double-adopt / double-close corruption; on Android 16 this triggers a native `abort` in `ParcelFileDescriptor` finalizer → SIGABRT → "app keeps stopping". | High |
| C11 | `XrayManager.kt:71` | `Thread.sleep(3000)` blocks `Dispatchers.IO`. Not a crash per se but blocks the coroutine, delays FGS start beyond the 20s Android 16 limit — system kills app with `ANR in HaioVpnService`. | Medium |
| C12 | `app/build.gradle.kts:24-26` | `ndk.abiFilters += "arm64-v8a"` only. Devices running arm32 (older tablets, some Android 14 Go editions) get `INSTALL_FAILED_NO_MATCHING_ABIs` or, if forced, native crashes when loading xray binary. User's "Android 16" device is arm64 so this is not the immediate cause but contributes. | Low |

---

## Fix Plan

### Phase A — Immediate crash fixes (must-do before next release)

#### A1. Fix foreground service type (fixes C2, C7) — **highest priority**
- `AndroidManifest.xml`: change `foregroundServiceType="specialUse"` to `connectedDevice` is wrong for VPN; the **correct** type for a VPN service is `android:foregroundServiceType="systemExempted"` **or** (recommended) keep `specialUse` **but only when distributed via Play**. For sideloaded distribution, use the supported VPN pattern:
  - Keep `android:foregroundServiceType="specialUse"` but add `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` (already present).
  - **OR** (simpler for sideload): drop `FOREGROUND_SERVICE_SPECIAL_USE` and use `FOREGROUND_SERVICE_CONNECTED_DEVICE` is invalid for VPN.
  - **Recommended fix**: keep `specialUse`, but **upgrade `targetSdk` to 35** (see A2) and ensure the service is started only after the user taps "Connect" (not in background).
- `VpnNotification.kt`: set `Notification.Builder.setForegroundServiceType(ForegroundServiceType.SPECIAL_USE)` on Android 14+.
- `HaioVpnService.kt:44`: call `ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` instead of `startForeground(...)`.

#### A2. Upgrade targetSdk/compileSdk to 35 (fixes C1)
- `app/build.gradle.kts:9,16`: `compileSdk = 35`, `targetSdk = 35`.
- Build-Tools 35.0.0 needed (update `BUILD.md`).
- Test Android 16 behavior with the `34→35` shim disabled.

#### A3. Remove `Process.pid` reflection (fixes C3)
- `XrayManager.kt:410-421`: replace `process.javaClass.getDeclaredField("pid")` with `ProcessHandle.current().pid()` (API 34+) — but VpnService needs the **child** process pid.
- Use `Process.start` via `ProcessBuilder.start()` returns a `Process` whose `.pid()` is a public method since Java 9 / API 34+. Switch from `pb.start()` reflection to `process.pid()`.
- For older API, fall back to reading `/proc/self/task/<tid>/children` or `pgrep` — but simpler: bump `minSdk` requirement is not desired. Use a try/catch and log only (do **not** crash) if pid lookup fails.

#### A4. Stop patching the ELF at runtime (fixes C4)
- Long-term fix: build `tun2socks` and `xray` as ** Position-Independent Executables (PIE)** — `ET_DYN` — from source so `patchElfHeader` becomes unnecessary.
- Short-term fix: ship a pre-patched `tun2socks_arm64_v8a` asset (already `ET_DYN`) and remove the `patchElfHeader`/`patchTlsAlignment` calls in `XrayManager.kt:283,297`. Do the patching once at build time with a Bash script in `build.sh`, not at runtime on the user's device.
- For TLS alignment: rebuild `tun2socks` with `-Wl,-z,max-page-size=64` instead of runtime patching.

#### A5. Pass TUN fd to tun2socks via a supported mechanism (fixes C5)
- Replace the `LocalServerSocket` + `SCM_RIGHTS` fd passing with the **standard Android approach**: write the TUN `ParcelFileDescriptor` into a child process's `FileDescriptor` via `ParcelFileDescriptor.adoptFd` + `dup2` done **in the parent** (the app process), then exec tun2socks as a child of the app process.
- OR simplest: do the `dup2(3)` **in the app process** before `ProcessBuilder.start` using `Os.dup2`, then pass `--device fd://3` directly (no socket). The current `tun_wrapper/main.go` already does `dup2(tunFd, 3)` — move that logic into the Kotlin side.

#### A6. Fix IPv6/IPv4 mismatch in VPN builder (fixes C6)
- `HaioVpnService.kt:81-97`: either:
  - Restrict to IPv4 only: remove `.addAddress("fd00::1", 126)` and `.addRoute("::", 0)` → only tunnel IPv4. Most bypass use cases don't need IPv6.
  - OR (if IPv6 is needed): add an IPv6 DNS server too: `.addDnsServer("2001:4860:4860::8888")`.
- Recommended: IPv4-only tunnel (simpler, fewer crash vectors). Add `.addRoute("0.0.0.0", 0)` only.

#### A7. Fix double fd adoption (fixes C10)
- `HaioVpnService.kt:53` calls `detachFd()` and stores `vpnFd`.
- `HaioVpnService.kt:116` calls `ParcelFileDescriptor.adoptFd(vpnFd).close()`.
- `TunPacketHandler.kt:32` calls `ParcelFileDescriptor.fromFd(tunFdInt)` on the **same** detached fd.
- The fd is now owned by **two** `ParcelFileDescriptor` instances → double free in finalizer.
- Fix: pass the `ParcelFileDescriptor` (owning object) into `TunPacketHandler` instead of the raw int. Let `TunPacketHandler` own it and close it in `stop()`. Remove the `adoptFd` in `HaioVpnService.stopVpn`.

#### A8. Request POST_NOTIFICATIONS permission (fixes C9)
- `MainActivity.kt`: in `onCreate`, register `ActivityResultContracts.RequestPermission()` for `Manifest.permission.POST_NOTIFICATIONS` on API 33+ and launch it before showing the main content (or lazily when the user first taps "Connect").
- Gracefully degrade if denied — VPN still runs, just no visible notification (Android 14+ still creates the notification silently for FGS).

#### A9. Remove blocking sleeps on FGS path (fixes C11)
- `XrayManager.kt:71` `Thread.sleep(3000)`: replace with a non-blocking wait — read the xray log line that says "started" or poll `isXrayRunning()` in a `withTimeout` loop on `Dispatchers.IO`.
- Same for `XrayManager.kt:221` `Thread.sleep(500)`.

#### A10. Add arm32 ABI support (fixes C12, low priority)
- Either: ship `xray_armeabi_v7a` + `tun2socks_armeabi_v7a` + `tun_wrapper_armeabi_v7a` and add `armeabi-v7a` to `abiFilters`.
- OR: explicitly document arm64-only support and reject install on arm32 with a clear UI message.

### Phase B — Crash hardening (do together with Phase A)

#### B1. Global try/catch in `MainActivity.onCreate`
- Wrap the whole splash + activation flow in try/catch so any single failure (API down, prefs corrupt) shows a user-readable error instead of "keeps stopping".
- Install a `Thread.setDefaultUncaughtExceptionHandler` in `HaioApp.onCreate` that logs to a file and shows a toast, preventing the system "keeps stopping" dialog where possible.

#### B2. `HaioVpnService.onStartCommand` already has try/catch — verify all paths call `stopForeground` + `stopSelf` cleanly. Currently if `establishVpnInterface()` returns null, we `stopSelf` without `stopForeground` → on Android 14+ this leaks the FGS and the system kills the app. Add `stopForeground(STOP_FOREGROUND_REMOVE)` before every `stopSelf()`.

#### B3. Disable `xrayProcess` watchdog autorestart loop while the FGS is stopping (currently `HaioVpnService.stopVpn` sets `isStopping` but the watchdog in `XrayManager.kt:120-137` checks `intentionalStop` only — verify these are aligned; they aren't, `stopVpn` never calls `xrayManager.stop()` until `cleanup()`).

### Phase C — Verification (no code change)

1. `./gradlew assembleRelease --no-daemon` (with Build-Tools 35.0.0 installed).
2. `adb install -r app/build/outputs/apk/release/app-release.apk` on a real Android 16 device.
3. Launch app, tap "Connect", grant VPN permission, confirm no "keeps stopping" dialog for ≥10 min.
4. Run `adb logcat -s HaioVpnService:XrayManager:ActivityManager:*` and confirm:
   - No `ForegroundServiceStartNotAllowedException`
   - No `NoSuchFieldException` for `pid`
   - No `SIGABRT` / `SIGKILL` from `installd`
   - xray logs show "started"
5. Verify the bypass domains load from `tools.haiocloud.com/domains.txt`.
6. Verify a bypass site (e.g. youtube.com) loads through the proxy and a non-bypass site (e.g. aparat.com) goes direct.

### Phase D — Release

1. Bump `versionCode` (2 → 3) and `versionName` (1.1.0 → 1.2.0) in `app/build.gradle.kts:18`.
2. Re-sign with `haio-release.keystore`.
3. Ship the patched APK to the user who reported the issue and confirm Android 14 + Android 16 both run without "keep stopping".

---

## Implementation Order (strict)

1. **A2** (compileSdk 35, targetSdk 35) — required for the FGS type fixes to take effect.
2. **A1** (FGS type fix + notification) — main crash cause.
3. **A6** (IPv6/IPv4 fix) — second crash cause during `establish()`.
4. **A7** (double fd adoption) — native SIGABRT.
5. **A3** (remove `Process.pid` reflection) — uncaught exception.
6. **A4** (pre-patch ELF at build time, not runtime) — `installd` SIGKILL.
7. **A5** (replace SCM_RIGHTS fd passing) — tun2socks never starts.
8. **A9** (remove `Thread.sleep` on FGS path) — ANR.
9. **A8** (POST_NOTIFICATIONS permission) — notification failure.
10. **B1, B2, B3** (hardening).
11. **A10** (arm32) — only if users with arm32 devices report.
12. **Phase C + D** — verify and ship.

---

## Files Changed

| File | Changes |
|------|---------|
| `app/build.gradle.kts` | A2 compileSdk/targetSdk 35; D version bump |
| `app/src/main/AndroidManifest.xml` | A1 keep `specialUse` (document), or switch FGS type |
| `app/src/main/java/com/haio/bypass/service/HaioVpnService.kt` | A1, A6, A7, B2 |
| `app/src/main/java/com/haio/bypass/service/VpnNotification.kt` | A1 set FGS type on Notification |
| `app/src/main/java/com/haio/bypass/proxy/XrayManager.kt` | A3, A4, A5, A9 |
| `app/src/main/java/com/haio/bypass/dns/TunPacketHandler.kt` | A7 own the PFD |
| `app/src/main/java/com/haio/bypass/MainActivity.kt` | A8 POST_NOTIFICATIONS, B1 try/catch |
| `app/src/main/java/com/haio/bypass/HaioApp.kt` | B1 uncaught exception handler |
| `tun_wrapper/main.go` | A5 (optional, if SCM_RIGHTS removed) |
| `build.sh` | A4 pre-patch tun2socks ELF |
| `BUILD.md` | A2 Build-Tools 35.0.0 instructions |

---

## Out of Scope (for this plan)
- Windows antivirus detection — separate issue, separate plan.
- Browser-extension install path — not part of the Android app.
- The trojan-go desktop client — irrelevant to Android.
