# HaioBypass API Reference

Base: `http://localhost:8000` / `https://api.haio.ir`
Auth: JWT Bearer token (except where noted)

---

## Plans

### `GET /api/v1/plans/` — List active plans

Public. No auth.

**Response 200:**
```json
{
  "data": [
    {
      "id": 1, "slug": "free", "name": "Free 50MB",
      "traffic_bytes": 52428800, "traffic_gb": 0.05, "traffic_mb": 50.0,
      "price_toman": 0, "duration_days": 7,
      "features": {"servers": "Netherlands", "support": "None"}
    }
  ],
  "total_items": 1, "current_page": 1, "last_page": 1
}
```

### `GET /api/v1/plans/{slug}/` — Plan detail

Public. No auth.

Fields: `id, name, slug, description, traffic_bytes, traffic_gb, traffic_mb, price_toman, duration_days, features`

---

## Auth

### `POST /api/v1/auth/register/` — Register

Public. No auth.

**Request:**
```json
{ "username": "string", "email": "string?", "password": "string (min 6)", "mobile": "string?" }
```

**Response 201:**
```json
{
  "user": { "id": 1, "username": "user", "email": "", "profile": { "balance": 0, "mobile": null } },
  "tokens": { "access": "eyJ...", "refresh": "eyJ..." }
}
```

### `POST /api/v1/auth/login/` — Login

Public. No auth.

**Request:**
```json
{ "username": "string", "password": "string" }
```

**Response 200:**
```json
{
  "access": "eyJ...",
  "refresh": "eyJ...",
  "user": { "id": 1, "username": "admin", "balance": 0 }
}
```

### `POST /api/v1/auth/token/refresh/` — Refresh JWT

**Request:**
```json
{ "refresh": "eyJ..." }
```

### `GET /api/v1/user/me/` — Current user

Requires auth.

### `GET /api/v1/user/balance/` — Balance + mobile

Requires auth.

```json
{ "balance": 50000, "mobile": "09123456789" }
```

---

## Subscriptions

### `POST /api/v1/subscriptions/auto-activate/` — First launch free activation

**Public. No auth required.**

Called once per device lifetime.

**Request:**
```json
{ "device_id": "550e8400-e29b-41d4-a716-446655440000" }
```

**Response 201:**
```json
{
  "subscription": {
    "id": 1,
    "uuid": "c9d347f7-ef59-464b-91da-ab89a8c5fbe1",
    "plan_detail": { "id": 1, "name": "Free 50MB", "slug": "free", "traffic_bytes": 52428800, "traffic_mb": 50.0, "price_toman": 0, "duration_days": 7 },
    "status": "active",
    "title": "Free 50MB",
    "username": "rw8yi3l0v5",
    "password": "0l7f2q9sq5ng",
    "device_id": "550e8400-...",
    "traffic_usage_percent": 0.0,
    "traffic_total_mb": 50.0,
    "traffic_usage_mb": 0.0,
    "total_traffic_byte": 52428800,
    "total_traffic_usage_byte": 0,
    "traffic_is_over": false,
    "expired_at": "2026-07-17T19:02:20+03:30",
    "activated_at": "2026-07-10T19:02:20+03:30",
    "created_at": "2026-07-10T19:02:19+03:30",
    "trojan_config_url": "trojan://0l7f2q9sq5ng@tahrim.haiocloud.com:443?allowInsecureCertificate=1&allowInsecure=1&sni=tahrim.haiocloud.com#Free 50MB"
  },
  "trojan_config_url": "trojan://...",
  "user_id": 2
}
```

**Response 400 (already used):**
```json
{ "detail": "This device has already activated the free plan." }
```

---

### `GET /api/v1/subscriptions/` — List user's subscriptions

Requires auth.

**Response 200:**
```json
{
  "data": [ { "id": 1, "uuid": "...", "status": "active", ...Subscription fields... } ],
  "total_items": 1
}
```

### `GET /api/v1/subscriptions/{uuid}/` — Subscription detail

Requires auth. UUID in path.

Returns full Subscription object including `trojan_config_url`, traffic usage, plan detail.

### `POST /api/v1/subscriptions/create/` — Buy plan (balance deduction)

Requires auth.

**Request:**
```json
{ "plan": "basic-5gb", "title": "My Config", "device_id": "optional-device-id" }
```

- `plan` — slug of an active plan
- `title` — user label, 2-30 chars
- `device_id` — optional, for free plan per-device tracking

Plan `price_toman` deducted from `user.profile.balance`.

**Response 201:** Full Subscription object.

**Error 400:**
```json
{ "detail": "Insufficient balance. Need 15000 Toman, you have 0 Toman." }
```
```json
{ "detail": "This device has already used the free plan." }
```

### `POST /api/v1/subscriptions/{uuid}/renew/` — Renew subscription

Requires auth. UUID in path. Deducts balance.

---

## Payments (Cafe Bazar IAB)

### `POST /api/v1/payments/` — Create pending payment

Requires auth.

**Request:**
```json
{ "plan": "basic-5gb" }
```

**Response 201:**
```json
{
  "id": 5,
  "plan_detail": { "name": "Basic 5GB", ... },
  "amount_toman": 15000,
  "gateway": "cafebazar",
  "status": "pending",
  "purchase_token": "",
  "created_at": "2026-07-10T..."
}
```

### `POST /api/v1/payments/verify/` — Verify Cafe Bazar purchase

Requires auth.

**Request:**
```json
{
  "purchase_token": "token-from-cafebazaar-sdk",
  "product_id": "basic-5gb",
  "payment_id": 5
}
```

Backend calls Cafe Bazar REST API to validate the token, then:
- Marks payment as `verified`
- Creates a new `Subscription`
- Calls tahrim API to activate anti-sanction
- Returns both payment and subscription

**Response 200:**
```json
{
  "payment": { "id": 5, "status": "verified", "plan_detail": {...} },
  "subscription": {
    "id": 2, "uuid": "...", "status": "active",
    "username": "...", "password": "...",
    "trojan_config_url": "trojan://...",
    "traffic_total_mb": 5120.0,
    "expired_at": "2026-08-09T..."
  }
}
```

**Error 400 (already consumed):**
```json
{ "detail": "This purchase has already been consumed." }
```

**Error 400 (invalid token):**
```json
{ "detail": "Purchase verification failed. Invalid or unverified purchase token." }
```

### `GET /api/v1/payments/history/` — Payment history

Requires auth. Returns paginated Payment records.

---

## Common Error Responses

| Status | Format |
|--------|--------|
| 400 Bad Request | `{"detail": "..."}` or `{"plan":["This field is required."]}` |
| 401 Unauthorized | `{"detail": "Authentication credentials were not provided."}` |
| 422 Validation Error | `{"field": ["error message"]}` |
| 404 Not Found | `{"detail": "Not found."}` |
| 500 Server Error | HTML debug page (production: JSON error) |

---

## Data Types

### Subscription object
```json
{
  "id": 1,
  "uuid": "c9d347f7-ef59-464b-91da-ab89a8c5fbe1",
  "plan_detail": { "id": 1, "name": "string", "slug": "string", "traffic_bytes": 0, "traffic_mb": 0.0, "price_toman": 0, "duration_days": 7 },
  "plan": "free",              // write-only: plan slug on create
  "status": "active",          // active | expired | traffic_exhausted | cancelled | pending
  "title": "string",
  "username": "string",
  "password": "string",
  "device_id": "string",
  "traffic_usage_percent": 0.0,
  "traffic_total_mb": 0.0,
  "traffic_usage_mb": 0.0,
  "total_traffic_byte": 0,
  "total_traffic_usage_byte": 0,
  "traffic_is_over": false,
  "expired_at": "datetime",
  "activated_at": "datetime|null",
  "created_at": "datetime",
  "trojan_config_url": "string"
}
```

### Plan object
```json
{
  "id": 1,
  "slug": "basic-5gb",
  "name": "Basic 5GB",
  "description": "string",
  "traffic_bytes": 5368709120,
  "traffic_gb": 5.0,
  "traffic_mb": 5120.0,
  "price_toman": 15000,
  "duration_days": 30,
  "features": {"servers": "Netherlands"}
}
```

### Payment object
```json
{
  "id": 1,
  "plan_detail": {...},
  "amount_toman": 15000,
  "gateway": "cafebazar",
  "status": "pending",
  "purchase_token": "",
  "product_id": "",
  "transaction_ref": "",
  "verified_at": null,
  "created_at": "datetime"
}
```

### Paginated list wrapper
```json
{
  "data": [ ...items... ],
  "total_items": 10,
  "current_items": 10,
  "per_page": 20,
  "current_page": 1,
  "last_page": 1
}
```

---

## JWT Token Lifetime

- Access token: 120 minutes (configurable via `JWT_ACCESS_TOKEN_LIFETIME_MINUTES`)
- Refresh token: 7 days (configurable via `JWT_REFRESH_TOKEN_LIFETIME_DAYS`)
- Token type: `Bearer`