# Device lock, mTLS and guest enrollment (Android)

This document describes the Android half of the HaioGate device-lock
programme (PLAN-DEVICE-LOCK-MTLS-GUEST-ENROLLMENT §12). It is implementation
documentation, not a plan.

## Core selection

| Active credential | Core | Why |
|---|---|---|
| Manual `trojan://` link | Xray | Existing routing/FakeDNS/UDP behaviour is unchanged. |
| Legacy HaioGate account (password only) | Xray | The backend keeps returning a reusable password/URL. |
| Device-locked HaioGate account | **sing-box 1.14.4** | Xray cannot combine `utls.fingerprint=chrome` with a client certificate in one outbound. |

`AppConfig.core` (`xray` / `singbox`) and `AppConfig.deviceIdentityId` record the
selection. `TrojanConfig.deviceIdentityId` marks a locked credential. Both fields
have defaults, so configs written by older builds still decode and keep Xray.

`ProxyCore` is implemented by `XrayManager` and `SingBoxManager`.
`ProxyManager` picks the core, generates the matching config, and keeps the
existing TUN + tun2socks plumbing. `HaioVpnService` is unchanged.

## Key and certificate storage

- `security/DeviceCrypto` — pure-JVM crypto (EC P-256 key generation, SPKI PEM,
  AES-256-GCM, SHA-256 leaf fingerprint, X.509 parsing). Unit tested.
- `security/DeviceKeyProtector` — non-exportable AES-256-GCM key in Android
  Keystore (`setUserAuthenticationRequired(false)`,
  `setRandomizedEncryptionRequired(true)`).
- `security/DeviceIdentityStore` — EncryptedSharedPreferences holds the
  encrypted PKCS#8 blob, IV, certificate, CA chain, fingerprint and credential
  metadata. On connect it writes `filesDir/haiogate/device.key` and
  `device.crt` with mode 0600; `ProxyManager.stop()` (disconnect, logout,
  reset) deletes both. Encrypted identities survive logout so re-applying an
  account does not require re-enrollment.
- `security/DeviceKeyManager` — enrollment persistence, active-config
  switching, reset cleanup.

No plaintext private key is persisted. A rooted device can read the decrypted
files while connected; mTLS is not DRM (accepted limitation, plan §12.3).

## Enrollment API

- Public preview/enroll use `PortalClient.anonymousApi` (no bearer token, no
  authenticator, no portal session). Guests never get an account.
- `InviteLinkParser` validates `^hgi_[A-Za-z0-9_-]{43}$` locally before any
  network call, and accepts `haio://join/<token>` and
  `https://my.haio.ir/join/<token>`.
- Enrollment sends the SPKI `public_key` (no CSR) plus `device_name`,
  `platform=android`, `app_version` and a UUID `request_id`. A retry reuses the
  same key + request id so the server's 15-minute replay path can re-sign after
  a lost response.
- Enrollment rotates the credential password; the client always uses
  `credential.password` from the enroll response, never a cached value.
- Owner endpoints: primary invite/enroll/reset, member invite/reset, pending
  invite list and cancel.

## Deep links and UI

- Manifest: App Link `https://my.haio.ir/join/` with `autoVerify=true`, plus
  `haio://join/`. `MainActivity` handles `onCreate` and `onNewIntent` and
  forwards the validated token to `HaioBypassApp`.
- `JoinScreen` implements idle/preview/enrolling/success/error states with
  Persian copy and a replace-config confirmation.
- Welcome screen has a third path: «مهمان — لینک دعوت دارم».
- `MembersScreen` adds the owner flow: add member → single-use invite with
  local QR (ZXing core), pending/active device info, new invite, reset device,
  revoke member, cancel pending invite. Legacy accounts keep the reusable-link
  UI and the legacy-unlimited notice. Locked members never show a password or
  trojan URL.

## sing-box routing parity

`SingBoxConfigGenerator` mirrors the Xray rules that matter for HaioGate:

- direct-domain exceptions (highest priority);
- UDP allow entries (`host:ports`);
- QUIC (UDP/443) block;
- Fake-IP pool `198.18.0.0/15` routed to the proxy;
- bypass domain suffixes routed to the proxy;
- `final: direct`.

Known gaps (accepted for the MVP; plan §12.2 allows a subset):

1. **No FakeDNS module in sing-box.** Domain routing depends on the SOCKS
   inbound sniffing TLS/HTTP. A connection with no hostname can only match IP
   rules.
2. **No FakeDNS module.** sing-box resolves names with its default resolver;
   the config uses `hijack-dns` for captured DNS queries and routes hostnames
   by sniffed domain suffix. It does not synthesize Fake-IP addresses.
3. **CA chain not passed to sing-box.** The leaf certificate is written to
   `device.crt`; the server already has the Device CA. If a future CA sends an
   intermediate client chain, `client_certificate_path` must contain leaf +
   intermediate.
4. **Health probe skipped for locked configs.** A plain TLS probe cannot prove
   that mTLS accepts the client certificate (the server deliberately allows
   cert-less handshakes to reach camouflage). sing-box readiness plus the
   first proxied request is the signal.

## sing-box binary

- Asset: `app/src/main/assets/sing_box_arm64_v8a` (82,765,688 bytes).
- SHA-256: `2ce1c1c2a265e7be21ffa7aaf2b58619986629e9caecfc2d3f80ef4d25b7f965`.
- Source: official `sing-box v1.14.4` Android arm64 release, built with
  `CGO_ENABLED=0 GOOS=android GOARCH=arm64` and `with_utls` (see
  `PHASE0-DEVICE-LOCK-FINDINGS.md`).
- Extracted at runtime to `codeCacheDir/singbox` with mode 0700; the copy is
  re-hashed on first extraction and rejected on mismatch.
- APK size grows by roughly 25 MB compressed.
