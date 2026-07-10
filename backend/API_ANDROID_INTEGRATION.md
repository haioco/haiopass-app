# HaioBypass Android — Backend Integration Plan

## Overview

The Android app communicates with the Django backend to:
1. Auto-activate a free 50MB anti-sanction config on first launch (per device)
2. Purchase paid plans via Cafe Bazar in-app billing
3. Manage subscriptions (list, renew)

Base URL: `https://api.haio.ir` (production) / `http://10.130.146.80:8000` (dev via LXC)

---

## Step 1: On First App Launch → Auto-Activate

When the app opens for the first time on a device:

### Generate Device ID

```kotlin
// Store persistently in SharedPreferences
val prefs = context.getSharedPreferences("haio_prefs", Context.MODE_PRIVATE)

fun getOrCreateDeviceId(): String {
    val existing = prefs.getString("device_id", null)
    if (existing != null) return existing
    val newId = UUID.randomUUID().toString()
    prefs.edit().putString("device_id", newId).apply()
    return newId
}
```

### Call Auto-Activate API

```
POST /api/v1/subscriptions/auto-activate/
Content-Type: application/json

{ "device_id": "<device-uuid>" }
```

### Response (201 Created)

```json
{
  "subscription": {
    "id": 1,
    "uuid": "c9d347f7-ef59-464b-91da-ab89a8c5fbe1",
    "plan_detail": {
      "name": "Free 50MB",
      "traffic_bytes": 52428800,
      "traffic_mb": 50.0,
      "duration_days": 7
    },
    "status": "active",
    "title": "Free 50MB",
    "username": "rw8yi3l0v5",
    "password": "0l7f2q9sq5ng",
    "traffic_total_mb": 50.0,
    "traffic_usage_mb": 0.0,
    "expired_at": "2026-07-17T19:02:20+03:30",
    "trojan_config_url": "trojan://0l7f2q9sq5ng@tahrim.haiocloud.com:443?allowInsecureCertificate=1&allowInsecure=1&sni=tahrim.haiocloud.com#Free 50MB",
    "created_at": "2026-07-10T19:02:19+03:30"
  },
  "trojan_config_url": "trojan://0l7f2q9sq5ng@tahrim.haiocloud.com:443?...",
  "user_id": 2
}
```

### Error: Already Activated (400)

```json
{ "detail": "This device has already activated the free plan." }
```

### On Success
- Save `trojan_config_url`, parse it → set `TrojanConfig` (extract password, server, port, SNI from URL)
- Save `user_id` in SharedPreferences for later purchase flows
- The VPN now works with anti-sanction via tahrim

### If Already Activated (already stored locally)
- Skip auto-activate, use previously saved config

---

## Step 2: Browse & Purchase Paid Plans

### Fetch Available Plans

```
GET /api/v1/plans/
```

```json
{
  "data": [
    {
      "id": 2, "slug": "basic-5gb", "name": "Basic 5GB",
      "traffic_bytes": 5368709120, "traffic_gb": 5.0, "traffic_mb": 5120.0,
      "price_toman": 15000, "duration_days": 30,
      "features": { "servers": "Netherlands", "support": "Basic" }
    },
    {
      "id": 3, "slug": "standard-25gb", "name": "Standard 25GB",
      "traffic_bytes": 26843545600, "traffic_gb": 25.0, "traffic_mb": 25600.0,
      "price_toman": 50000, "duration_days": 30
    }
  ]
}
```

### Purchase Flow via Cafe Bazar IAB

#### 1. Register the user if not already done

```
POST /api/v1/auth/register/
Content-Type: application/json

{ "username": "<user_id from Step 1>", "email": "", "password": "<random>" }
```

Or store JWT from auto-activate (auto-activate creates user; add JWT to that response for reuse).

#### 2. Create pending payment record

```
POST /api/v1/payments/
Authorization: Bearer <jwt_token>
Content-Type: application/json

{ "plan": "basic-5gb" }
```

Response:
```json
{ "id": 5, "amount_toman": 15000, "gateway": "cafebazar", "status": "pending" }
```

#### 3. Launch Cafe Bazar purchase flow

Product SKU = plan slug (e.g. `basic-5gb`)

Standard Cafe Bazar IAB SDK flow:
```kotlin
val helper = CafeBazaarIabHelper(context, publicKeyBase64)
helper.startSetup { // onIabSetupFinished
    helper.launchPurchaseFlow(
        context,
        productId = "basic-5gb",     // same as plan slug
        requestCode = 1001,
        listener = { purchase, info -> /* see step 4 */ }
    )
}
```

#### 4. On successful purchase → verify with backend

```
POST /api/v1/payments/verify/
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "purchase_token": "<token from CafeBazaarIabHelper>",
  "product_id": "basic-5gb",
  "payment_id": 5
}
```

Response (200):
```json
{
  "payment": { "id": 5, "status": "verified", "plan_detail": { ... } },
  "subscription": {
    "id": 2,
    "uuid": "...",
    "plan_detail": { "name": "Basic 5GB", "traffic_mb": 5120.0 },
    "status": "active",
    "username": "abc123...",
    "password": "...",
    "trojan_config_url": "trojan://...@tahrim.haiocloud.com:443?...",
    "expired_at": "2026-08-09T...",
    "traffic_total_mb": 5120.0,
    "traffic_usage_mb": 0.0,
    "activated_at": "2026-07-10T..."
  }
}
```

#### 5. On success
- Parse `trojan_config_url` → update `TrojanConfig` in the app
- If user was on free plan → now upgraded to paid plan
- Restart VPN connection with new config

---

## Step 3: Manage Existing Subscriptions

### List all subscriptions

```
GET /api/v1/subscriptions/
Authorization: Bearer <jwt_token>
```

### Get subscription detail

```
GET /api/v1/subscriptions/<uuid>/
Authorization: Bearer <jwt_token>
```

Response includes current traffic usage, expiry, trojan config URL.

### Renew expired subscription

```
POST /api/v1/subscriptions/<uuid>/renew/
Authorization: Bearer <jwt_token>
```

Deducts balance if plan has a price. Free plan renewal blocked at backend level (per-device check).

---

## Step 4: Alternative — Direct API Purchase (no Cafe Bazar)

If user has wallet balance (e.g. topped up via admin or other means):

```
POST /api/v1/subscriptions/create/
Authorization: Bearer <jwt_token>
Content-Type: application/json

{ "plan": "basic-5gb", "title": "My 5GB Config", "device_id": "<device-id>" }
```

Deducts `price_toman` from user balance. Returns subscription + trojan config directly.

---

## Summary: Per-Device State

| SharedPreferences Key | Purpose |
|---|---|
| `device_id` | UUID, auto-activate on first launch only |
| `user_id` | Backend user ID from auto-activate response |
| `trojan_config_url` | The full trojan:// URL from latest active subscription |
| `jwt_access_token` | Auth token for purchase endpoints (renew via refresh) |
| `jwt_refresh_token` | Longer-lived token for refreshing access |