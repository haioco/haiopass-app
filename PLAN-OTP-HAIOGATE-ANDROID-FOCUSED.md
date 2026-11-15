# Plan — Android OTP Login, HaioGate Accounts, Renewal, Purchase, Shahkar, and Payment

**Status:** Proposal for approval. **No implementation has been started.**
**Date:** 2026-11-07
**Platform:** Android only (`/home/devcloud/Documents/mine/Haio-Forever/HaioBypass-android`)
**Backend:** Existing HaioCloud control plane (`portal.haio.ir`); one small backend fix is required for correct renewal of a selected account.
**Relationship to earlier plans:** This is the focused Android implementation plan. The broader
cross-platform plan `PLAN-OTP-SHAHKAR-HAIOGATE.md` remains as reference for desktop and
optional follow-ups, but this document is the scope of record for the Android release.

---

## 1. Scope

### In scope

1. **OTP login** with the HaioCloud portal: mobile number → SMS OTP → access/refresh tokens →
   persistent session → logout.
2. **List the user's HaioGate accounts**, separated into active and expired/ended accounts.
3. **Renew an expired HaioGate account**, keeping the same account and credentials, and
   optionally switching to another package.
4. **Buy a new HaioGate package** from inside the app.
5. **Shahkar gate and in-app national-code verification** before any HaioGate purchase or
   renewal.
6. **Payment through the existing Zibal gateway**, opened from the app, with recovery after
   app restart, browser back, process death, or a delayed callback.
7. **Apply the HaioGate connection config** to the existing VPN engine.

### Out of scope

- Desktop app and the cloud companion/super-app strategy.
- Migrating legacy HaioBypass accounts into portal accounts.
- Replacing the VPN engine, proxy, routing, or notification code.
- In-app wallet, coupons, credit purchase, refunds, or invoice download.
- Creating multiple HaioGate accounts on the same package.
- SMS Retriever/autofill, embedded payment WebView, or a new payment provider.
- A public package catalog before login.
- Android App Links / custom-scheme payment return (optional Phase 2).
- Any change to the legacy store's product behaviour. It remains available during the
  transition; only its “active config source” bookkeeping is touched.

### Non-negotiable product rules

- The portal (`portal.haio.ir`) is the single identity for OTP, Shahkar, HaioGate, and billing.
- Login is optional for the VPN itself; it is required only for HaioGate and account screens.
- Shahkar is enforced before **buy** and **renew**, not at login.
- The user's login mobile is locked in the Shahkar form; it must not be editable, because the
  backend writes the submitted mobile onto the user.
- The app never stores the OTP, national code, payment URL, or HaioGate password in logs or
  analytics. Tokens are stored encrypted.
- Payment is never collected in an embedded WebView. Zibal opens in Chrome Custom Tabs.

---

## 2. Executive summary

The backend already has everything needed for the MVP:

- OTP request/login/refresh and `auth/me`.
- Shahkar verification and the `IsShahkarVerified` permission.
- HaioGate packages, accounts, quota sync, purchase intent, and renewal intent.
- Billing buy, invoices, Zibal payment request/verify, and the subscription signal that
  provisions or renews the HaioGate account.

The Android app currently has **no account system**: its store talks to the legacy backend
(`haiobypass.haiocloud.com`), its JWT fields are legacy, and it has no Shahkar or HaioGate UI.
The work is therefore client-side, plus one backend correction for selecting the right
account when a user has more than one account on the same package.

**Recommended release shape**

- `فروشگاه` tab gets a two-tab header: **هایو گیت** (default) and **پلن‌های قبلی** (existing
  legacy store, unchanged).
- Logged-out users see a login card. After login, the HaioGate tab shows:
  - **فعال** — usable accounts with usage, expiry, queued renewals, and a renew action.
  - **منقضی شده** — expired or quota-exhausted accounts with a renew action.
  - **نیازمند پشتیبانی** — suspended or inconsistent accounts.
- Buy and renew both follow:
  `login → Shahkar (if needed) → purchase/renew intent → buy plan → Zibal → poll invoice →
  refresh account → apply config`.
- Android tracks `MANUAL`, `LEGACY`, and `HAIOGATE` active config sources, so a legacy
  30-second refresh cannot overwrite an applied HaioGate config.
- No backend change is needed for OTP, Shahkar, buying, or payment. One backend change is
  required for reliably renewing a **specific** expired account when the user has multiple
  accounts on the same package (see §7).

**Rough effort:** Android 22–29 engineer-days including QA, plus 1–2 backend days for the
renewal fix. Desktop is not included.

---

## 3. Current state

### 3.1 Android app

- Kotlin/Compose, package `com.haio.bypass`, version `1.2.6` (`versionCode 9`), minSdk 26,
  target/compileSdk 34, Kotlin 1.9.22, Compose BOM 2024.02.00.
- `MainActivity` has a disabled activation/purchase path and a splash screen. The app uses
  `HaioPrefs` (`SharedPreferences("haio_prefs")`) for device id, legacy JWT, subscription
  cache, theme, and legacy pending-payment fields.
- `ConfigManager` (`SharedPreferences("haio_config")`) owns the active VPN config and exposes
  a StateFlow. `TrojanUrlParser` already parses
  `trojan://<password>@<host>:<port>?sni=...`, which is exactly the HaioGate URL shape.
- `HaioBypassApp` has four bottom tabs: خانه، عبور مستقیم، فروشگاه، تنظیمات. The store uses
  the legacy `ApiClient` (`https://haiobypass.haiocloud.com/`) and legacy plans.
- The legacy `ApiClient` always logs HTTP bodies and does not persist a rotated refresh token.
  The new portal client must not reuse it.
- `buildConfig = false`; there is no browser/Custom Tabs dependency, no
  `security-crypto`, and no MockWebServer test dependency.
- No login, OTP, Shahkar, HaioGate, invoice, or portal payment code exists.

### 3.2 Backend (already implemented)

- OTP: `POST /api/v1/auth/otp/request/`, `POST /api/v1/auth/otp/login/`,
  `POST /api/v1/auth/refresh/`, `GET /api/v1/auth/me/`.
- Shahkar: `POST /api/v1/auth/shahkar/verify/`; `IsShahkarVerified` guards buy/renew.
- HaioGate: package catalog, account list/detail, quota sync, purchase intent, renewal intent.
- Billing: buy plan, invoices, invoice pay, Zibal request/verify, provisioning signal.
- Organizations: `GET /api/v1/orgs/`; OTP login creates a personal org, so most users have
  exactly one.

### 3.3 Backend behaviour that matters to the plan

- `GET /antisanction/` returns **all** of the current user's accounts, not only active ones.
  The app filters and groups them.
- Account status values: `active`, `expired`, `quota_exhausted`, `suspended`.
- `POST /antisanction/{id}/renew/` is allowed for any account owned by the user, including an
  expired one.
- If the account is still active, renewal is queued as an `AntiSanctionReserve`; the current
  expiry does **not** move immediately. `pending_reserves_count` and
  `projected_expired_at` expose the queued renewal.
- If the account is expired or quota-exhausted, payment renews it immediately: status becomes
  `active`, expiry becomes now + 30 days, usage resets to zero.
- If the user selects a different package during renewal, the backend stores
  `pending_package_id` so the post-payment signal renews the same account with the new
  package instead of creating a second account.
- `POST /billing/subscriptions/buy/` requires Shahkar and a valid mobile. It returns either
  `status: "active"` (free or fully covered) or `status: "payment_required"` with a Zibal
  `payment_url`, `payment_id`, and `invoice_id`.
- `GET /billing/payments/verify/?pid=...` is the Zibal **callback**. It must never be used for
  polling; an early call can mark a still-pending payment failed.
- The subscription `post_save` signal creates or renews the HaioGate account after payment.
  A provisioning failure is recorded in diagnostics but does not fail the subscription, so
  the app can legitimately see “paid but account not ready yet”.

---

## 4. Decisions

| # | Decision | Recommendation | Reason |
|---|---|---|---|
| D1 | Which identity system? | **Portal** (`portal.haio.ir`) | OTP, Shahkar, Zibal, invoices, and HaioGate already live there. The legacy backend has a separate identity and no HaioGate packages. |
| D2 | Is login mandatory for VPN? | **No.** Required only for HaioGate/account screens. | Existing manual/legacy users keep working; no breaking release. |
| D3 | What happens to the legacy store? | **Keep it as the second inner tab “پلن‌های قبلی”; do not change its flow.** | Avoids removing a working purchase path while HaioGate is introduced. |
| D4 | How are active and expired accounts separated? | **Client-side classification** from `status`, `expired_at`, and `traffic_is_over`. | The list endpoint returns all accounts and has no status filter. |
| D5 | When is Shahkar required? | **Before buy/renew only**, or when the API returns `shahkar_required`. | Keeps login and VPN simple; matches the backend permission model. |
| D6 | How does payment return to the app? | **MVP: Chrome Custom Tabs + persisted pending purchase + invoice polling on resume.** | No backend change required; works after process death. Deep link is Phase 2. |
| D7 | Can an active account be renewed early? | **Yes.** Show it as a queued renewal, not an immediate expiry change. | Backend already supports reserves; hiding it would confuse users who want to prepay. |
| D8 | Can a suspended account self-renew? | **No.** Show “نیازمند پشتیبانی”. | Suspension may be administrative; self-service renewal could bypass a support decision. |
| D9 | Where are tokens stored? | **EncryptedSharedPreferences**, with both access and refresh tokens persisted on rotation. | The existing plain prefs and legacy refresh bug are not acceptable for portal credentials. |
| D10 | Is desktop in this plan? | **No.** | The user asked for the Android app flow; desktop can reuse the same contract later. |
| D11 | What if the user has multiple accounts on one package? | **Required backend fix** to mark the selected account for renewal. | Without it the signal renews the newest account on that package, not necessarily the one the user chose. |

---

## 5. User flows

### 5.1 Login with OTP

1. Entry points: the HaioGate tab when logged out, the account icon in the top bar, or the
   account section in Settings.
2. User enters a mobile number. Normalize Persian/Arabic digits, strip spaces and dashes,
   validate `^09\d{9}$`.
3. `POST auth/otp/request/`. Show “کد ۶ رقمی ارسال شد” and a countdown from `retry_after` /
   `expires_in`. Disable resend until the countdown ends.
4. User enters the 6-digit OTP. Submit to `POST auth/otp/login/`.
5. On success, store `access` and `refresh`, load `user`, close the login screen, and resume
   the action that triggered login (open HaioGate, buy, or renew).
6. On invalid/expired OTP, stay on the OTP step and show the error. On 429 lockout, disable
   submit and show the 5-minute timer. On 502 SMS failure, allow retry.
7. On app restart, if tokens exist, call `GET auth/me/`; if the access token is expired,
   refresh once and retry. If refresh fails, clear the session and show login.

### 5.2 HaioGate account list

The HaioGate tab loads in this order:

1. If no session → login card.
2. `GET auth/me/` (cached; refresh on pull).
3. `GET antisanction/?page_size=100` and `GET antisanction/packages/?page_size=100` in
   parallel.
4. Group the accounts:
   - **فعال** — `status == active`, `expired_at > now`, `traffic_is_over == false`.
   - **منقضی شده** — `status in (expired, quota_exhausted)`, or `expired_at <= now`, or
     `traffic_is_over == true`.
   - **نیازمند پشتیبانی** — `status == suspended`, or an account whose status/expiry is
     inconsistent.
5. Active accounts first. Each card shows package name, usage bar, expiry (Jalali), queued
   renewal count, and a `تمدید` button. Expired cards show why they ended and a `تمدید` button.
6. Pull-to-refresh reloads accounts. A manual “به‌روزرسانی مصرف” on detail calls
   `GET antisanction/{id}/quota/` to trigger a proxy sync; do not call quota for every list
   item.

If the user has no accounts, show an empty state with a “خرید پکیج” button. If packages are
empty, show “در حال حاضر پکیج فعالی برای خرید وجود ندارد”.

### 5.3 Account detail

- Header: package name, status chip, username.
- Usage: used / total, percentage, `usage_display`.
- Dates: current expiry, projected expiry when renewals are queued.
- Renewals: list `pending_reserves` (package, traffic, created date) and
  `pending_reserves_count`.
- Credentials: password behind a reveal toggle; copy username/password. Optional
  `FLAG_SECURE` on this screen.
- Connection: “اعمال روی اپ” parses `trojan_url` with the existing `TrojanUrlParser`, writes
  it to `ConfigManager`, and sets `activeConfigSource = HAIOGATE` plus
  `haiogateAccountId`. Do not auto-connect; ask the user to press the main connect button.
- Actions: `تمدید` (same package or choose another), `بروزرسانی مصرف`.
- A legacy account (`is_legacy = true`) gets a small “قدیمی” badge. Renewal still goes through
  the new billing flow; if the proxy rejects it, show support.

### 5.4 Renew an expired account

1. User taps `تمدید` on an expired card.
2. If not logged in → login. If `shahkar_status != 1` → Shahkar screen.
3. Load active packages. Preselect the account's current package if it is still active;
   otherwise require an active package. Disable the current package if it is inactive.
4. Show a confirmation sheet: package, price, traffic, 30-day period, “پس از پرداخت، همین
   اکانت فعال می‌شود و اعتبار قبلی حفظ می‌شود”.
5. Call `POST antisanction/{id}/renew/` with `{package_id}`.
6. Call `POST billing/subscriptions/buy/` with the returned `plan_id` (and
   `organization_id` only if required).
7. If `payment_required`, persist the pending purchase and open Zibal. If `active`, refresh
   the account and show success.
8. After payment, refresh the same account until `status == active`, expiry is ~30 days
   ahead, and usage is zero. If it is still expired after provisioning, show “در حال
   فعال‌سازی” and allow manual refresh; after 15 minutes, show support.

### 5.5 Renew an active account (early renewal)

Same flow, but the confirmation explains:

> «تمدید شما رزرو می‌شود و پس از پایان دوره فعلی فعال می‌شود. تا آن زمان اکانت فعلی بدون
> تغییر باقی می‌ماند.»

After payment:

- `pending_reserves_count` increases.
- `projected_expired_at` moves forward.
- The account remains active; the UI must not claim that the expiry date changed now.

If the user chooses a different package, the reserve is pinned to that package and will switch
the account at consumption time. Show the target package on the reserve row.

### 5.6 Buy a new package

1. User taps “خرید پکیج” in the HaioGate tab.
2. If not logged in → login. If Shahkar is not verified → Shahkar.
3. Show active packages with price, traffic, and description.
4. If the user already has any account on the selected package, route to **renew** instead of
   creating a new one. The backend renews the existing account on that package; the app must
   say so before payment.
5. `POST antisanction/` with `{package_id}` → `plan_id`.
6. `POST billing/subscriptions/buy/` with `{plan_id}`.
7. If `status == "active"` (free or fully covered), refresh accounts immediately.
8. If `status == "payment_required"`, persist and open Zibal, then poll.
9. After payment, refresh `GET antisanction/`; the new account appears. Open its detail and
   offer “اعمال روی اپ”.

If the user already has a personal org, no picker is shown. If the API returns
`organization_required`, show the organizations from the response (or `GET orgs/`) and retry
with the selected `organization_id`. Remember the choice for the session; default to the
personal org next time.

### 5.7 Shahkar

- Triggered before buy/renew when `user.shahkar_status != 1`, or when the API returns 403
  `shahkar_required`.
- Screen:
  - Mobile: read-only, prefilled from `auth/me`; caption “شماره موبایل حساب شما”.
  - If `user.mobile` is missing or invalid, do not show an editable field; explain that the
    account needs a valid mobile and route to support.
  - National code: numeric, max 10 digits, normalize Persian/Arabic digits, show/hide optional,
    no clipboard suggestions.
  - Explanation: “برای خرید و تمدید هایو گیت، احراز هویت شاهکار لازم است.”
  - Privacy note: “کد ملی فقط برای استعلام شاهکار ارسال می‌شود و در برنامه ذخیره نمی‌شود.”
- Submit `POST auth/shahkar/verify/` with `{mobile, national_id}`.
- Handle:
  - `verified == true` / `shahkar_status == 1` → update local user, clear the national-code
    field, return to the pending action, retry it once.
  - `shahkar_status == 2` (mismatch) → stay on the screen, allow correction, show “اطلاعات
    هویتی با شماره موبایل مطابقت ندارد.”
  - `shahkar_status == 3` / provider error → stay, show “سرویس شاهکار در دسترس نیست”; allow
    retry.
  - 400 duplicate mobile → “این شماره موبایل به حساب دیگری متصل است. با پشتیبانی تماس
    بگیرید.”
  - 429 → countdown from `Retry-After`.
- Never persist the national code. Clear it when leaving the screen or on success.
- If the user is already verified, skip the screen entirely; show a green “تایید شده” badge on
  the Account screen.

### 5.8 Payment and return

- Open `payment_url` with `CustomTabsIntent` in the system browser. Do not use a WebView.
- Show a short note before opening: «پس از پرداخت، به برنامه بازگردید تا وضعیت خرید بررسی شود.»
- Before opening, persist `PendingPurchase` (see §10) in a non-sensitive prefs file.
- On `ON_RESUME` / app start:
  1. If no pending purchase, do nothing.
  2. If older than 24 hours, clear it and show “پرداخت منقضی شد”.
  3. `GET billing/invoices/{invoice_id}/`.
  4. `paid` → refresh accounts and account detail; mark success; clear pending.
  5. `canceled` / `refunded` → show failure; clear pending.
  6. `pending` → keep the banner, allow “بررسی مجدد” and “ادامه پرداخت”.
- Do **not** call `billing/payments/verify/`.
- If the user paid but the account is not visible yet, poll accounts every 3 seconds for up to
  60 seconds, then show “پرداخت موفق بود؛ اکانت در حال آماده‌سازی است” and provide a manual
  refresh + support after 15 minutes.
- If the app was killed, the pending purchase survives in prefs and is reconciled on the next
  launch.
- If the user returns without paying, keep the pending invoice banner. Do not create a second
  purchase; offer “ادامه پرداخت” via `POST billing/invoices/{id}/pay/`, which returns a fresh
  payment URL.

### 5.9 Apply config and logout

- Apply: parse `trojan_url`; on success update ConfigManager and set
  `activeConfigSource = HAIOGATE`, `haiogateAccountId`. On failure show “لینک اتصال این اکانت
  معتبر نیست. با پشتیبانی تماس بگیرید.”
- Logout: clear portal tokens, cached user, org selection, and pending purchase. Keep the
  applied VPN config and the active source. Show a notice that the VPN config remains until
  the user deletes it in Settings.
- If `activeConfigSource == HAIOGATE`, the legacy 30-second subscription refresh in
  `HaioBypassApp` must be skipped; otherwise it can overwrite the HaioGate state with legacy
  subscription data.

---

## 6. API contract

Base URL: `https://portal.haio.ir/api/v1/` (Retrofit base URL must end with `/`).

### 6.1 Auth

| Method | Path | Body / params | Response | Notes |
|---|---|---|---|---|
| POST | `auth/otp/request/` | `{mobile}` | `{sent, expires_in, retry_after, provider, raw}` | 429 `{sent:false, reason:"rate_limited", retry_after}`; 502 on SMS provider failure |
| POST | `auth/otp/login/` | `{mobile, otp}` (6 digits) | `{access, refresh, user}` | Auto-creates the user; 400 invalid/expired; 429 lockout with `Retry-After` |
| POST | `auth/refresh/` | `{refresh}` | `{access, refresh}` | Rotation enabled; persist both |
| GET | `auth/me/` | — | `User` | Session validation |
| POST | `auth/shahkar/verify/` | `{mobile, national_id}` | `{verified, shahkar_status, reason, api_call_status_code, identified_response_code, user}` | Status: 0 unverified, 1 verified, 2 mismatch, 3 provider error |

### 6.2 HaioGate

| Method | Path | Body / params | Response | Notes |
|---|---|---|---|---|
| GET | `antisanction/packages/?page_size=100` | — | paginated `Package` | Auth required; active packages only |
| GET | `antisanction/?page_size=100` | — | paginated `Account` | Current user; all statuses |
| GET | `antisanction/{id}/` | — | `AccountDetail` | Includes `password`, `trojan_url`, `usage_percent`, `pending_reserves`, `projected_expired_at` |
| GET | `antisanction/{id}/quota/` | — | `AccountDetail` | Triggers server-side quota sync; use on demand |
| POST | `antisanction/` | `{package_id}` | `{plan_id, package_id, is_free, detail}` | Auth + Shahkar; does not create the account |
| POST | `antisanction/{id}/renew/` | `{package_id?}` | `{plan_id, package_id, is_free, detail}` | Auth + Shahkar; marks the selected account/package for renewal |

### 6.3 Billing and organizations

| Method | Path | Body / params | Response | Notes |
|---|---|---|---|---|
| POST | `billing/subscriptions/buy/` | `{plan_id, organization_id?, coupon?, use_credit?}` | `{status, payment_url?, payment_id?, invoice_id?, ...}` | Auth + Shahkar; `status` is `active` or `payment_required` |
| GET | `billing/invoices/{invoice_id}/` | — | `Invoice` | Auth + Shahkar; poll `pending` → `paid` |
| GET | `billing/invoices/?page_size=100` | — | paginated `Invoice` | Recover from an ambiguous buy request |
| POST | `billing/invoices/{invoice_id}/pay/` | `{use_credit?}` | `{payment_url, payment_id, invoice_id}` | Resume payment for an existing pending invoice |
| GET | `orgs/?page_size=100` | — | paginated `Organization` | `id, name, slug, owner, owner_email, my_role, created_at` |
| GET | `billing/payments/verify/?pid={payment_id}` | — | redirect | **Gateway callback — do not poll** |

### 6.4 User model

`id`, `username` (nullable), `email`, `first_name`, `last_name`, `role`, `display_name`,
`is_verified`, `onboarding_completed`, `mobile`, `shahkar_status`, `national_id` (read-only),
`created_at`.

Cache only what is displayed. Do not persist `national_id`, OTP codes, payment URLs, passwords,
or trojan URLs in analytics/logs.

### 6.5 Error envelope

DRF errors can be `{detail: "..."}`, `{field: ["..."]}`, or `{detail, code}`. Important codes:

- `shahkar_required` (403) — route to Shahkar, remember the action, retry once.
- `mobile_required` (400) — no valid mobile on the account; explain and route to support.
- `organization_required` (400) — multiple orgs; response includes
  `organizations: [{id, name}]`.
- `rate_limited` (429) — show a countdown using `retry_after` / `Retry-After`.
- `payment_required` is not an error; it is a successful buy response.

Map these into a sealed `PortalError` so UI copy and retry policy are consistent.

---

## 7. Required backend fix — renew the selected account

### 7.1 Problem

The renewal endpoint records only a **package** intent, and only when the package changes.
The subscription signal then selects an account by package:

```python
existing = AntiSanctionAccount.objects.filter(
    user=user,
    package=package,
).order_by("-created_at").first()
```

Scenario:

- User has two expired 50 GB accounts, A (older) and B (newer).
- User selects A and pays for renewal of 50 GB.
- `POST /antisanction/A/renew/` does not record A, because the package did not change.
- The signal finds B (newest) and renews B, while the user expected A.

This is not acceptable for a “renew expired account” feature. It is also a support risk when
users keep multiple accounts or re-purchase the same package.

### 7.2 Fix

Add a selected-account intent to the account metadata. No migration is needed:
`meta_data_json` is already a JSON field.

In `AccountViewSet.renew`:

- Always write a structured intent, not only a package change:

```json
{
  "pending_renewal": {
    "account_id": "<uuid>",
    "package_id": "<uuid>",
    "created_at": "<iso8601>"
  }
}
```

- Keep `pending_package_id` for backward compatibility with existing rows and the current
  signal.
- If the intent is already present for the same account, update `package_id`/`created_at`
  instead of duplicating.

In `_create_or_renew_antisanction_account`:

1. Read `pending_renewal`.
2. If present and `account_id` belongs to `user`, and `package_id` matches the package backing
   the subscription, renew **that** account with that package.
3. Clear `pending_renewal` and the legacy `pending_package_id` after a successful renewal.
4. If the intent is stale, belongs to another user, or its package does not match, ignore it
   and continue with the existing fallback:
   exact-package account → pending tier switch → create new.
5. Keep the current fallback for old rows that only have `pending_package_id`.

Cleanup:

- When an invoice is canceled/refunded/expired, clear a matching `pending_renewal` intent
  (recommended; can be a follow-up if the client blocks duplicate purchases).
- When a new renewal is started, overwrite the previous intent.
- Never trust the intent without checking `user_id` and the package.

### 7.3 Tests

- Two expired accounts on the same package: renew the older one → older one becomes active,
  newer one unchanged.
- Two active accounts on the same package: selected renewal creates a reserve on the selected
  account.
- Tier switch: selected account changes package, no second account is created.
- Abandoned payment: intent exists but no subscription; a later unrelated purchase does not
  renew the wrong account.
- Malicious/foreign account id in metadata: ignored.
- Legacy rows with only `pending_package_id`: still work.

### 7.4 If the backend fix is not approved

The Android app must:

- Allow renewal only when the user has **one** account per package.
- If multiple accounts share the package, disable self-service renewal and show
  “برای تمدید این اکانت با پشتیبانی تماس بگیرید.”
- Never promise that the selected account will be renewed.

This fallback is worse for users and support; the fix is preferred.

### 7.5 Optional backend follow-ups (not MVP)

- Non-destructive `GET billing/payments/{payment_id}/status/` that verifies without marking a
  pending payment failed.
- Per-payment return URL + `haiobypass://payment` deep link.
- Buy idempotency key.
- Public package catalog before login.
- Server-side `status` filter on `GET antisanction/`.

---

## 8. Android architecture

### 8.1 New portal package

Create `app/src/main/java/com/haio/bypass/network/haiocloud/`:

| File | Responsibility |
|---|---|
| `PortalApiService.kt` | Retrofit interface for auth, HaioGate, billing, orgs |
| `PortalClient.kt` | OkHttp/Retrofit factory; auth interceptor; refresh authenticator; debug-only logging |
| `PortalModels.kt` | Gson DTOs for requests/responses |
| `PortalSessionStore.kt` | Encrypted access/refresh token storage; session snapshot |
| `PortalSession.kt` | `StateFlow<SessionState>`: `LoggedOut`, `LoggedIn(user)`, `Refreshing` |
| `AuthRepository.kt` | OTP request/login, refresh, me, Shahkar, logout |
| `HaioGateRepository.kt` | Packages, accounts, detail, quota, start buy, start renew |
| `BillingRepository.kt` | Buy plan, invoice, invoice list, invoice pay |
| `OrgRepository.kt` | Organization list and org-required handling |
| `PortalError.kt` | Sealed error types and mapping from HTTP/DRF errors |
| `PendingPurchaseStore.kt` | Non-sensitive pending purchase persistence |
| `PurchaseCoordinator.kt` | Buy/renew orchestration, pending state, invoice polling, account refresh |
| `PortalContainer.kt` | Manual dependency graph; one instance per process |

Keep the existing legacy `com.haio.bypass.network.api` package untouched. The new client is
separate: different base URL, different token store, different logging policy, and different
error model.

### 8.2 Networking and token handling

- Retrofit + Gson, base URL `BuildConfig.HAIO_PORTAL_BASE_URL`.
- OkHttp:
  - `Authorization: Bearer <access>` interceptor for authenticated calls.
  - `Authenticator` for 401: single-flight refresh using a `Mutex`; persist both rotated
    tokens atomically; retry the original request once.
  - Refresh calls use a separate OkHttp client without the authenticator to avoid recursion.
  - If refresh returns 401/400, clear the session and emit `SessionExpired`.
- Logging:
  - Debug: `HttpLoggingInterceptor.Level.BASIC` and `redactHeader("Authorization")`.
  - Release: `Level.NONE`.
  - Never log request/response bodies for auth, Shahkar, or billing.
- Timeouts: 20 seconds connect/read; no automatic retries for non-idempotent POSTs.
- `PortalError` maps:
  - 400 field errors → form errors.
  - 401 → session expired (after refresh attempt).
  - 403 `shahkar_required` → Shahkar gate.
  - 429 → rate-limit with retry seconds.
  - 5xx/IO → transient error with retry.

### 8.3 Session storage

- `EncryptedSharedPreferences` with `MasterKey` (AES256_GCM). Keys: `access`, `refresh`,
  optional `user_json` for fast startup (no national id).
- Persist tokens before emitting `LoggedIn`, so process death immediately after login still
  restores the session.
- If the keystore is invalid or decryption fails: clear the encrypted store, clear in-memory
  session, ask the user to log in again.
- On logout: clear encrypted tokens, cached user, org selection, pending purchase; keep VPN
  config.

### 8.4 State and ViewModels

Use the existing `lifecycle-viewmodel-compose` dependency. Avoid adding Hilt.

| ViewModel | State |
|---|---|
| `AuthViewModel` | mobile, OTP step, countdown, loading, error, current user |
| `HaioGateViewModel` | accounts grouped active/expired/support, packages, selected account, loading/error |
| `AccountDetailViewModel` | detail, reveal password, quota refresh, apply config |
| `ShahkarViewModel` | national code, loading, status message, verified user |
| `PurchaseViewModel` | selected package/account, pending purchase, stage, payment URL, invoice status |
| `AccountViewModel` | user profile, Shahkar badge, logout |

Shared state (session, pending purchase) lives in repositories/coordinators, not in each
ViewModel. Use `StateFlow` and `collectAsStateWithLifecycle()`.

### 8.5 Navigation

Existing `Screen` sealed class in `ui/HaioBypassApp.kt`. Add:

- `Login` (`login`)
- `Account` (`account`)
- `Shahkar` (`shahkar`)
- `HaioGate` (`haiogate`) — the default Store tab content
- `HaioGateDetail` (`haiogate/{accountId}`)
- `PackagePicker` (`haiogate/packages?accountId={id}`) or a modal bottom sheet
- `PaymentPending` — preferably a banner/dialog on HaioGate, not a separate route

Keep the four bottom tabs. Add an account icon to the top app bar (or a “حساب هایو” row in
Settings) that opens `Account`/`Login`. The Store tab contains a `TabRow` with
**هایو گیت** and **پلن‌های قبلی**; the second reuses the current `StoreScreen` renamed to
`LegacyStoreScreen`.

### 8.6 Active config source

Extend `HaioPrefs` (or a small `ActiveConfigPrefs`):

- `activeConfigSource`: `MANUAL` | `LEGACY` | `HAIOGATE`, default `MANUAL`.
- `haiogateAccountId`: string, nullable.

Rules:

- Manual config save → `MANUAL`.
- Legacy purchase applies config → `LEGACY`.
- HaioGate “اعمال روی اپ” → `HAIOGATE` + account id.
- `HaioBypassApp` legacy 30-second refresh runs only when the source is not `HAIOGATE`.
- Home shows a HaioGate summary card when the source is `HAIOGATE`; map the account to the
  existing `SubscriptionInfo` shape (title, traffic MB, expiry) so `TrafficInfoCard` can be
  reused.
- Logout does not clear the active config source or the applied config.

### 8.7 Build, manifest, and Proguard

`app/build.gradle.kts`:

- `buildFeatures.buildConfig = true`.
- `buildConfigField("String", "HAIO_PORTAL_BASE_URL", "\"https://portal.haio.ir/api/v1/\"")`.
- Add `androidx.browser:browser` (Custom Tabs).
- Add `androidx.security:security-crypto` (encrypted tokens).
- Add `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`.

`AndroidManifest.xml`:

- No new runtime permission is required for Custom Tabs.
- No deep-link intent filter in the MVP. If Phase 2 is approved, add
  `haiobypass://payment` with `launchMode="singleTop"` and validate the invoice id.

`proguard-rules.pro`:

- Keep `com.haio.bypass.network.haiocloud.**` DTOs and the Retrofit interface.
- Verify a release build with R8; keep Gson `@SerializedName` fields.

---

## 9. Data model and account classification

### 9.1 DTOs

`PortalUser`

```text
id: Long
username: String?
email: String?
first_name: String
last_name: String
role: String
display_name: String
is_verified: Boolean
onboarding_completed: Boolean
mobile: String?
shahkar_status: Int
national_id: String?   // read-only; never persisted
created_at: String
```

`HaioGatePackage`

```text
id: String
name: String
slug: String
description: String
price: Long            // Toman
traffic_bytes: Long
is_free: Boolean
status: String         // active
sort_order: Int
```

`HaioGateAccount` (list)

```text
id: String
package: String
package_name: String
username: String
total_traffic_bytes: Long
usage_bytes: Long
usage_display: String
traffic_is_over: Boolean
expired_at: String?    // ISO 8601
status: String         // active | expired | quota_exhausted | suspended
status_display: String
last_sync: String?
is_legacy: Boolean
pending_reserves_count: Int
created_at: String
updated_at: String
```

`HaioGateAccountDetail` adds:

```text
password: String
trojan_url: String
usage_percent: Double
pending_reserves: List<Reserve>
projected_expired_at: String?
meta_data_json: JsonObject?
```

`Reserve`

```text
id: String
package: String
package_name: String
traffic_bytes: Long
created_at: String
```

`Invoice`

```text
id: String
organization: Long
plan: { id, name, slug }?
subscription: String?
amount: Long
original_amount: Long
discount_amount: Long
discount_percent: Double
status: String         // pending | paid | canceled | refunded
due_date: String?
paid_at: String?
description: String
created_at: String
```

`BuyResponse`

```text
status: String         // active | payment_required
payment_url: String?
payment_id: String?
invoice_id: String?
subscription_id: String?
credit_applied: Long?
paid_by: String?
```

`Organization`

```text
id: Long
name: String
slug: String
owner: Long
owner_email: String
my_role: String
created_at: String
```

### 9.2 Classification

Parse dates with `java.time` (minSdk 26 supports it). Treat a null/invalid `expired_at` as
“unknown”, not automatically active.

```text
fun category(account, now):
  if account.status == "suspended": return SUPPORT
  if account.status == "expired" or account.status == "quota_exhausted": return EXPIRED
  if account.traffic_is_over: return EXPIRED
  if account.expired_at != null and account.expired_at <= now: return EXPIRED
  if account.status == "active" and account.expired_at > now: return ACTIVE
  return SUPPORT
```

Rules:

- `ACTIVE` — has usable traffic and a future expiry.
- `EXPIRED` — time ended, quota ended, or `traffic_is_over`; renewable.
- `SUPPORT` — suspended or inconsistent; no self-service renewal.
- Sort active by expiry ascending (soonest first); expired by expiry descending (most recently
  ended first).
- If an active account has `pending_reserves_count > 0`, show a “تمدید رزرو شده” chip and
  `projected_expired_at`.

### 9.3 Formatting

- Traffic: bytes → GB with one decimal; use `usage_display` when provided.
- Price: Toman with thousands separators and “تومان”.
- Dates: convert ISO to Jalali with the existing `ui/util/JalaliDate.kt`; show relative text
  for soon-to-expire accounts (“۳ روز مانده”).
- Usage bar: use `usage_percent`; clamp 0–100; color warning at >= 80%, error at >= 100%.

---

## 10. Purchase and payment state machine

```text
Idle
  │ user taps Buy or Renew
  ▼
EnsureLoggedIn ── no ──► Login ── success ──┐
  │ yes                                     │
  ▼                                         │
EnsureShahkar ── no ──► Shahkar ── success ─┘
  │ yes
  ▼
ResolveOrg (only if organization_required)
  │
  ▼
POST antisanction/ (new)  or  POST antisanction/{id}/renew/ (existing)
  │  → plan_id
  ▼
POST billing/subscriptions/buy/ {plan_id, organization_id?}
  │
  ├─ status == active ──► refresh accounts ──► success
  │
  └─ status == payment_required
        │
        ▼
   Persist PendingPurchase {invoice_id, payment_id, plan_id, package_id,
                            account_id?, mode, created_at, stage}
        │
        ▼
   Open payment_url in Chrome Custom Tabs
        │
        ▼
   On resume/app start: poll invoice
        │
        ├─ paid ──► refresh accounts ──► account detail ──► apply config ──► success
        ├─ canceled/refunded ──► terminal failure
        └─ pending ──► keep banner; 24 h expiry + support
```

### 10.1 PendingPurchase

Stored in `SharedPreferences("haio_portal_pending")` as JSON. Non-sensitive only:

```text
mode: BUY | RENEW
account_id: String?
package_id: String
plan_id: String
invoice_id: String
payment_id: String?
created_at: Long
stage: CREATED | BROWSER_OPENED | PAID | PROVISIONING | DONE | FAILED
```

Never store `payment_url`, tokens, mobile, national code, or passwords.

### 10.2 Rules

- Disable buy/renew while a request is in flight; prevent double taps.
- Persist pending **before** opening the browser.
- On buy timeout with unknown outcome, list invoices and find a pending invoice for the same
  plan/org created in the last ~15 minutes; resume it via `POST invoices/{id}/pay/` instead of
  creating a duplicate.
- On app start, reconcile a pending purchase younger than 24 hours.
- Never call `billing/payments/verify/`.
- If `status == active` (free or fully covered), skip the browser and refresh accounts.
- If `organization_required`, show a picker and retry with `organization_id`.
- If `shahkar_required` appears mid-flow, route to Shahkar, remember the action, and retry once.
- Do not auto-connect the VPN after applying a config.

### 10.3 Active vs expired outcome

| Account at renewal start | After payment | UI |
|---|---|---|
| Active, future expiry, not over quota | Reserve queued; expiry unchanged now | “تمدید رزرو شد”; show projected expiry |
| Expired time | Active; expiry now + 30 days; usage reset | “اکانت فعال شد” |
| Quota exhausted / traffic over | Active; expiry now + 30 days; usage reset | “اکانت فعال شد” |
| Suspended | Not offered | “نیازمند پشتیبانی” |
| New purchase | New account created | Open detail; offer apply config |

### 10.4 Payment recovery cases

- **Browser back without paying**: invoice remains pending; keep banner; offer resume.
- **App killed**: pending survives; reconcile on next launch.
- **Paid, account not yet visible**: poll accounts for up to 60 seconds; then provisioning
  message + manual refresh.
- **Paid, provisioning failed**: show support message with invoice id; do not retry payment.
- **Duplicate tap**: blocked by in-flight state; if a duplicate invoice appears, use the
  oldest pending one and ignore the other.
- **Session expires during polling**: refresh token once; if that fails, keep pending and ask
  for login, then resume reconciliation.

---

## 11. Screen-by-screen UX

### 11.1 Login / OTP

- Full-screen sheet or route; RTL, Persian.
- Mobile step:
  - Title “ورود به حساب هایو”
  - Mobile field, “دریافت کد”
  - Explanation: “با شماره موبایل خود وارد شوید. حساب هایو گیت، خرید و تمدید به همین حساب
    متصل می‌شود.”
- OTP step:
  - Six-digit field, “تایید و ورود”
  - Resend with countdown
  - “تغییر شماره” back action
- Loading states disable the primary button.
- Errors inline, not only snackbar.

### 11.2 Account

- Mobile, display name, Shahkar badge (تایید شده / تایید نشده).
- “احراز هویت شاهکار” action if not verified.
- “خروج از حساب” with confirmation.
- Note that logout does not disconnect or delete the VPN config.

### 11.3 Shahkar

- Mobile read-only.
- National code input with numeric keyboard, max 10 digits.
- Explanation and privacy note.
- Primary “تایید هویت”.
- On success, return to the pending action automatically.
- Show status-specific messages; never show raw provider codes to the user.

### 11.4 HaioGate tab

- Header: “هایو گیت” + account icon/status.
- If logged out: login card with benefits and a button.
- If logged in:
  - Active section with cards.
  - Expired section with cards.
  - Support section if needed.
  - Floating/section action “خرید پکیج جدید”.
- Pending payment banner at the top: “پرداخت در انتظار تایید” + “بررسی وضعیت”.
- Empty states for no accounts and no packages.
- Pull-to-refresh.

### 11.5 Account detail

- Package/status header.
- Usage card.
- Expiry card, including projected expiry.
- Queued renewals list.
- Credentials card with reveal/copy.
- “اعمال روی اپ” primary action.
- “تمدید” secondary action.
- “بروزرسانی مصرف”.

### 11.6 Package picker / confirmation

- List active packages with price, traffic, description.
- Mark current package.
- Disable inactive current package and explain.
- Confirmation sheet with total price and renewal outcome (immediate vs reserve).
- If the selected package already has an account, switch to renew flow and explain.

### 11.7 Payment states

| State | UI |
|---|---|
| Opening browser | “در حال انتقال به درگاه پرداخت...” |
| Pending | Banner “در انتظار تایید پرداخت”; “بررسی مجدد”; “ادامه پرداخت” |
| Paid / provisioning | “پرداخت موفق بود؛ اکانت در حال آماده‌سازی است.” |
| Success | “اکانت فعال شد” + “اعمال روی اپ” |
| Canceled/refunded | “پرداخت لغو شد” + retry |
| Expired pending (>24h) | “پرداخت منقضی شد” + start again |
| Provisioning failure | “پرداخت انجام شد اما اکانت ساخته نشد” + support |

### 11.8 Persian copy (key strings)

| Key | Persian |
|---|---|
| login_title | ورود به حساب هایو |
| mobile_label | شماره موبایل |
| otp_label | کد تایید ۶ رقمی |
| otp_sent | کد تایید ارسال شد |
| otp_resend | ارسال دوباره کد |
| otp_invalid | کد وارد شده نامعتبر یا منقضی شده است. |
| otp_lockout | تعداد تلاش‌ها بیش از حد مجاز است. لطفاً ۵ دقیقه دیگر تلاش کنید. |
| shahkar_title | احراز هویت شاهکار |
| shahkar_required | برای خرید و تمدید هایو گیت، احراز هویت لازم است. |
| shahkar_mismatch | اطلاعات هویتی با شماره موبایل مطابقت ندارد. |
| shahkar_provider_error | سرویس شاهکار در دسترس نیست. کمی بعد دوباره تلاش کنید. |
| national_code | کد ملی |
| shahkar_success | هویت شما با موفقیت تایید شد. |
| active_accounts | اکانت‌های فعال |
| expired_accounts | اکانت‌های منقضی شده |
| support_accounts | نیازمند پشتیبانی |
| renew | تمدید |
| buy_package | خرید پکیج |
| renew_reserved | تمدید شما رزرو شد و پس از پایان دوره فعلی فعال می‌شود. |
| renew_success | اکانت شما فعال شد. |
| payment_pending | در انتظار تایید پرداخت... |
| payment_success | پرداخت با موفقیت انجام شد. |
| provisioning | پرداخت موفق بود؛ اکانت در حال آماده‌سازی است. |
| apply_config | اعمال روی اپ |
| logout_keeps_config | با خروج از حساب، تنظیمات اتصال فعلی حذف نمی‌شود. |
| no_accounts | هنوز اکانت هایو گیت ندارید. |
| no_packages | در حال حاضر پکیج فعالی برای خرید وجود ندارد. |

Add these to `values/strings.xml` and `values-fa/strings.xml` and use `stringResource` in new
screens. Existing hardcoded strings can remain for legacy screens.

---

## 12. Security and privacy

- **Tokens**: encrypted prefs only; never in `state.json`, plain prefs, logs, or crash reports.
  Persist rotated refresh tokens atomically.
- **Logging**: release `Level.NONE`; debug `BASIC` with `Authorization` redacted. No OTP,
  national code, password, payment URL, or trojan URL in logs.
- **National code**: send only; clear the field on success/leave; never persist or display
  after verification.
- **OTP**: never log the response body; clear the field after login; do not include it in
  analytics or screenshots.
- **Payment URL**: open only HTTPS URLs on the configured Zibal host; do not persist it; do not
  allow arbitrary intents or `javascript:`/`data:` URLs.
- **WebView**: do not use one for payment. Custom Tabs keeps the gateway isolated from the app.
- **Proguard**: keep portal DTOs and Retrofit interface; test a minified release build.
- **Legacy client hardening (adjacent prerequisite)**: the existing `ApiClient` logs BODY in
  release and does not persist rotated refresh tokens. Fix or disable BODY logging in release
  as part of this work, even though the new flow uses a separate client.
- **Clipboard**: copy actions show a short toast; optional clipboard clear after 60 seconds if
  product requires.
- **Screenshots**: optional `FLAG_SECURE` on account detail and Shahkar; do not apply globally.
- **Certificate pinning**: not MVP; adds operational risk without a clear threat model.

---

## 13. Edge cases

| Case | Behaviour | Copy |
|---|---|---|
| OTP request 429 | Countdown, disable resend | «برای ارسال دوباره، {n} ثانیه صبر کنید.» |
| OTP SMS 502 | Allow retry | «ارسال پیامک ناموفق بود. دوباره تلاش کنید.» |
| OTP invalid/expired | Stay on OTP step | «کد وارد شده نامعتبر یا منقضی شده است.» |
| OTP lockout | Disable submit, 5-minute timer | «تعداد تلاش‌ها بیش از حد مجاز است. لطفاً ۵ دقیقه دیگر تلاش کنید.» |
| Session expired during action | Refresh once, else login and resume | «نشست شما منقضی شده است. دوباره وارد شوید.» |
| Shahkar required (403) | Open Shahkar, remember action | «برای ادامه، احراز هویت لازم است.» |
| Shahkar mismatch | Stay, allow edit | «اطلاعات هویتی با شماره موبایل مطابقت ندارد.» |
| Shahkar provider error | Retry | «سرویس شاهکار در دسترس نیست. کمی بعد دوباره تلاش کنید.» |
| Duplicate mobile | Stop, support | «این شماره موبایل به حساب دیگری متصل است. با پشتیبانی تماس بگیرید.» |
| Multiple orgs | Picker | «حساب سازمانی مورد نظر را انتخاب کنید.» |
| No mobile on account | Explain, support | «برای خرید، شماره موبایل معتبر روی حساب لازم است.» |
| No active package | Empty state | «در حال حاضر پکیج فعالی برای خرید وجود ندارد.» |
| Current package inactive | Require another package | «پکیج فعلی شما غیرفعال شده است؛ یک پکیج فعال انتخاب کنید.» |
| Active account renew | Queue reserve, no immediate expiry change | «تمدید شما رزرو شد و پس از پایان دوره فعلی فعال می‌شود.» |
| Expired account renew | Immediate reactivation after payment | «اکانت شما فعال شد.» |
| Suspended account | No self-service renew | «این اکانت نیازمند بررسی پشتیبانی است.» |
| Payment canceled | Keep invoice if pending; allow resume | «پرداخت لغو شد.» |
| Payment pending | Keep checking | «در انتظار تایید پرداخت...» |
| Paid, account pending | Refresh/support | «پرداخت موفق بود؛ اکانت در حال آماده‌سازی است.» |
| Provisioning failed | Support | «پرداخت انجام شد اما اکانت ساخته نشد. با پشتیبانی تماس بگیرید.» |
| Duplicate purchase same package | Route to renew | «شما قبلاً اکانتی با این پکیج دارید؛ خرید مجدد به معنی تمدید آن است.» |
| Multiple accounts same package | Backend marker required; otherwise support | «برای تمدید این اکانت با پشتیبانی تماس بگیرید.» |
| Invalid trojan URL | Do not apply | «لینک اتصال این اکانت معتبر نیست. با پشتیبانی تماس بگیرید.» |
| Network offline | Retry | «اتصال به سرور برقرار نشد. اینترنت خود را بررسی کنید.» |
| Server 5xx | Retry | «خطای موقت سرور. دوباره تلاش کنید.» |
| App killed during payment | Reconcile on next launch | «وضعیت پرداخت شما بررسی می‌شود...» |
| Pending older than 24h | Clear and start over | «پرداخت قبلی منقضی شده است.» |
| Logout with HaioGate config | Keep config, clear session | «تنظیمات اتصال فعلی حفظ می‌شود.» |

---

## 14. Testing plan

### 14.1 Unit tests

- Mobile normalization and validation (`09xxxxxxxxx`), Persian/Arabic digits.
- National-code validation: 10 digits, digit normalization, optional checksum.
- OTP countdown and 429 `retry_after`.
- Token authenticator with MockWebServer:
  - 401 → refresh → retry succeeds.
  - rotated refresh token is persisted.
  - concurrent 401s trigger a single refresh.
  - refresh failure clears the session.
- Error mapping: `shahkar_required`, `mobile_required`, `organization_required`, `rate_limited`.
- Account classification: active, expired by date, expired by quota, suspended, null expiry.
- Pending purchase serialization, 24h expiry, stage transitions.
- Invoice polling state machine: pending → paid, pending → canceled, paid → provisioning.
- Package selection: inactive current package, duplicate package route to renew.

### 14.2 UI tests

- Login: mobile → OTP → success; resend; invalid code; lockout.
- Shahkar: success, mismatch, provider error, duplicate mobile, already verified.
- HaioGate list: logged out, empty, active, expired, support, mixed.
- Renew active: confirmation copy, reserve result, projected expiry.
- Renew expired: confirmation, reactivation result.
- Buy new: package list, duplicate-package routing, confirmation.
- Payment: pending banner, resume, process death, success, canceled, provisioning.
- Account detail: reveal/copy, apply config, invalid trojan URL.
- RTL layout and Persian strings on all new screens.

### 14.3 Manual E2E

Use a staging/test mobile and Zibal sandbox or a low-value test merchant. Do not use production
SMS/payment in automated tests.

1. Fresh install → login with OTP → reopen app → session restored.
2. Unverified user → buy → Shahkar → success → purchase continues.
3. Buy 50 GB → Zibal → return → account appears → apply config → connect VPN.
4. Expired account → renew same package → account active, same username/password.
5. Expired account → renew with a different package → same account, new quota.
6. Active account → renew → reserve queued; projected expiry moves; current expiry unchanged.
7. Kill app during payment → reopen → pending reconciled.
8. Cancel payment → return → pending banner → resume payment.
9. Two accounts on the same package → renew the older one → correct account changes
   (requires the backend fix).
10. Multi-org user → picker → purchase completes.
11. Logout → VPN config remains; login again → accounts visible.
12. Release build: no secrets in logcat; Proguard/R8 does not break DTOs.

### 14.4 Backend tests

Only for the required renewal fix:

- Selected account marker is written on `renew`.
- Signal renews the selected account for same-package and tier-switch cases.
- Foreign/invalid marker is ignored.
- Intent is cleared after success.
- Legacy `pending_package_id` rows still work.

---

## 15. Phases and estimates

| Phase | Work | Estimate |
|---|---|---|
| 0. Portal client + OTP | Retrofit/OkHttp, encrypted token store, refresh authenticator, OTP/login/me/logout screens, error mapping | 5–7 days |
| 1. HaioGate list/detail | Packages, accounts, active/expired/support grouping, detail, quota refresh, credentials, apply config | 4–5 days |
| 2. Shahkar | Screen, validation, status handling, gate interception, user refresh | 2–3 days |
| 3. Buy/renew/payment | Purchase coordinator, renew endpoint, buy, org picker, Custom Tabs, pending purchase, invoice polling, recovery | 7–9 days |
| 4. Integration + release | Navigation, legacy tab wrapper, active config source, strings, Proguard, tests, release build | 4–5 days |
| **Android total** | | **22–29 engineer-days** |
| Backend renewal fix | Selected-account intent, signal change, tests | 1–2 days |

Assumptions:

- No new backend work beyond §7 is approved; optional payment-status/deep-link work is not in
  this estimate.
- Zibal sandbox/test merchant is available for E2E.
- One Android engineer familiar with the codebase; parallel QA support for manual E2E.
- Android release `1.3.0` (`versionCode 10`).

---

## 16. Acceptance criteria

### Login and session

- [ ] User can request an OTP, log in, and see the HaioGate tab without restarting the app.
- [ ] Session survives app restart; expired access token refreshes silently; rotated refresh
      token is persisted.
- [ ] Logout clears portal session and pending purchase but keeps the current VPN config.
- [ ] OTP resend countdown, invalid code, 429 lockout, and SMS provider failure have distinct
      Persian messages.

### HaioGate accounts

- [ ] Logged-in user sees active accounts in the active section.
- [ ] Expired/quota-exhausted accounts appear in the expired section and can be renewed.
- [ ] Suspended/inconsistent accounts appear in the support section and cannot be self-renewed.
- [ ] Account detail shows usage, expiry, queued renewals, projected expiry, username, password
      (reveal/copy), and trojan URL.
- [ ] “اعمال روی اپ” applies the HaioGate config and marks the active source `HAIOGATE`.
- [ ] Legacy 30-second refresh does not overwrite an active HaioGate config.

### Buy and renew

- [ ] Buying a new package with a test payment creates an account and shows it in the list.
- [ ] Renewing an expired account reactivates the same account with +30 days and reset usage.
- [ ] Renewing an active account queues a reserve and shows the projected expiry without
      changing the current expiry.
- [ ] Switching package during renewal keeps the same username/password.
- [ ] Buying a package the user already owns routes to renewal, not a duplicate.
- [ ] Multiple accounts on the same package renew the selected account (requires backend fix).
- [ ] Multi-org users can choose an organization and complete the purchase.

### Shahkar

- [ ] Unverified users are blocked before buy/renew and routed to Shahkar.
- [ ] The Shahkar form locks the mobile to the logged-in number.
- [ ] National code is validated as 10 digits and normalized from Persian/Arabic digits.
- [ ] Success updates `shahkar_status`, returns to the pending action, and retries it once.
- [ ] Mismatch, provider error, duplicate mobile, and 429 are handled separately.
- [ ] National code is never persisted or logged; the field is cleared after success/leave.

### Payment

- [ ] Zibal opens in Chrome Custom Tabs, not a WebView.
- [ ] Pending purchase is persisted before the browser opens.
- [ ] Returning to the app polls the invoice and shows paid/canceled/pending correctly.
- [ ] App kill during payment is recovered on next launch.
- [ ] Paid-but-provisioning shows a clear state and a manual refresh.
- [ ] `billing/payments/verify/` is never called by the app.
- [ ] A second purchase is blocked while one is pending; resume is offered.

### Security and release

- [ ] Tokens are encrypted; no tokens/OTP/national code/payment URL in logcat or analytics.
- [ ] Release build uses `HttpLoggingInterceptor.Level.NONE` for the portal client.
- [ ] R8/Proguard release build parses all DTOs correctly.
- [ ] New screens are RTL and use Persian copy consistently.
- [ ] No regression in manual config, legacy store, VPN connect, or settings.

---

## 17. Open questions

1. **Legacy store**: keep it indefinitely as a secondary tab, or retire it after the HaioGate
   flow is proven? Recommendation: keep it for this release, then decide based on usage.
2. **Early renewal**: is self-service early renewal allowed for all active accounts, or only
   when the account is near expiry? Recommendation: allow it; show a clear “رزرو” explanation.
3. **Suspended accounts**: should support be able to re-enable them, or should the app offer a
   renewal after a support confirmation? Recommendation: support-only in v1.
4. **Backend renewal fix**: approve the selected-account intent. Without it, the client must
   disable renewal for duplicate-package accounts.
5. **Shahkar at login**: management may prefer mandatory Shahkar for the whole app. This plan
   gates only HaioGate actions; mandatory login/Shahkar would be a product policy change and
   would block VPN use for unverified users.
6. **Payment deep link**: add `haiobypass://payment` in Phase 2? It requires a backend
   return-URL allowlist and manifest changes; not needed for the polling MVP.
7. **Package catalog before login**: leave auth-gated, or make it public for marketing?
   Recommendation: leave auth-gated in v1.
8. **Multiple accounts per package**: keep supporting them, or enforce one account per package?
   The renewal fix supports the current model; a uniqueness rule would be a separate product
   decision.

---

## 18. File-level change map

### Android

| File / area | Change |
|---|---|
| `app/build.gradle.kts` | Enable `buildConfig`; add portal base URL; add browser, security-crypto, MockWebServer |
| `gradle/libs.versions.toml` | Add versions for browser/security-crypto/mockwebserver |
| `app/src/main/AndroidManifest.xml` | No MVP change; optional `haiobypass://payment` filter in Phase 2 |
| `app/proguard-rules.pro` | Keep portal DTOs and Retrofit interface |
| `HaioPrefs.kt` | Add `activeConfigSource`, `haiogateAccountId`; keep legacy fields |
| `network/haiocloud/PortalApiService.kt` | New Retrofit interface |
| `network/haiocloud/PortalClient.kt` | New OkHttp/Retrofit client + refresh authenticator |
| `network/haiocloud/PortalModels.kt` | New Gson DTOs |
| `network/haiocloud/PortalSessionStore.kt` | Encrypted token store |
| `network/haiocloud/PortalSession.kt` | Session StateFlow |
| `network/haiocloud/AuthRepository.kt` | OTP, me, Shahkar, logout |
| `network/haiocloud/HaioGateRepository.kt` | Packages, accounts, detail, quota, buy/renew intent |
| `network/haiocloud/BillingRepository.kt` | Buy, invoices, pay, orgs |
| `network/haiocloud/PortalError.kt` | Error mapping |
| `network/haiocloud/PendingPurchaseStore.kt` | Pending purchase JSON |
| `network/haiocloud/PurchaseCoordinator.kt` | Buy/renew/poll/recover |
| `network/haiocloud/PortalContainer.kt` | Manual DI |
| `ui/screens/LoginScreen.kt` | New |
| `ui/screens/AccountScreen.kt` | New |
| `ui/screens/ShahkarScreen.kt` | New |
| `ui/screens/HaioGateScreen.kt` | New; active/expired/support sections |
| `ui/screens/HaioGateDetailScreen.kt` | New |
| `ui/screens/LegacyStoreScreen.kt` | Rename/wrap existing `StoreScreen`; no flow change |
| `ui/viewmodel/AuthViewModel.kt` | New |
| `ui/viewmodel/HaioGateViewModel.kt` | New |
| `ui/viewmodel/AccountDetailViewModel.kt` | New |
| `ui/viewmodel/ShahkarViewModel.kt` | New |
| `ui/viewmodel/PurchaseViewModel.kt` | New |
| `ui/HaioBypassApp.kt` | Routes, Store tabs, account icon, skip legacy refresh for `HAIOGATE` |
| `MainActivity.kt` | Lifecycle `ON_RESUME` reconciliation hook |
| `ui/screens/MainScreen.kt` | HaioGate summary when source is `HAIOGATE` |
| `ui/screens/SettingsScreen.kt` | “حساب هایو” row with login/Shahkar/logout |
| `res/values/strings.xml`, `res/values-fa/strings.xml` | New Persian/English keys |
| `app/src/test/...` | Unit tests: validators, token refresh, classification, pending, errors |
| `app/src/androidTest/...` | UI tests for login/Shahkar/list/payment |

### Backend (required fix only)

| File | Change |
|---|---|
| `control-plane/haio/antisanction/apis.py` | `AccountViewSet.renew` writes `pending_renewal` intent |
| `control-plane/haio/antisanction/signals.py` | Honor selected account intent; clear after success |
| `control-plane/haio/antisanction/tests/...` | Same-package selected renewal, tier switch, stale/foreign intent |

### Not changed

- VPN engine, proxy, routing, DNS, boot receiver, notification code.
- Legacy `ApiClient` endpoints and legacy store product flow (except release logging
  hardening and active-source bookkeeping).
- Web frontend, desktop app, backend OTP/Shahkar/Zibal providers.

---

## 19. What ships when

### MVP (this plan)

- OTP login, persistent session, logout.
- HaioGate active/expired/support lists.
- Account detail with credentials, usage, expiry, queued renewals.
- Renew expired account; renew active as reserve; optional package switch.
- Buy new package; duplicate-package routing to renewal.
- Shahkar gate + national-code verification.
- Zibal Custom Tabs + pending purchase + invoice polling + recovery.
- Apply HaioGate config; active config source protection.
- Android `1.3.0`.

### Phase 2 (only after MVP usage data)

- Per-payment return URL and `haiobypass://payment` deep link.
- Non-destructive payment-status endpoint.
- Buy idempotency key.
- Public package catalog.
- Desktop reuse of the same portal contract.
- Cloud companion/account-center features (separate plan; not part of this work).

---

## 21. Multi-user addendum (1 / 2 / 5 users)

This section extends the plan with per-config user limits. Full server/control-plane design:
`/home/devcloud/Documents/mine/Haio-Forever/haio-cloud-ai-infra/PLAN-MULTI-USER-HAIOGATE.md`.

### 21.1 Product rules

- A package includes a fixed number of user credentials: 50 GB = 1, 100 GB = 2, 200 GB = 5.
- A “user” means a named credential (username/password/trojan URL), not a concurrent TCP
  connection. A browser opens many connections; connection caps would break browsing.
- Existing accounts are **legacy unlimited** (`included_users = NULL`) and keep working
  unchanged until expiry or renewal.
- At renewal, the selected package's count applies. The primary credential is rotated, old
  shared credentials are revoked, and the owner onboards members.
- Members are owner-managed: add, revoke, rotate within available slots.

### 21.2 Android changes

- Package cards show «N کاربر».
- Renewal sheet shows the included count and, for legacy accounts, warns that the current
  shared link will change after renewal.
- Active early renewal: reserve only; member changes apply at the start of the new period.
- Expired renewal: immediate; after payment open the member-onboarding wizard.
- Add a `MembersScreen` (or a members section in `HaioGateDetailScreen`):
  - primary row + member rows, slot counter, add/revoke/rotate, copy/QR.
  - legacy account: show «نامحدود (قدیمی)» and no member actions.
  - full slots: disable add and show «ظرفیت این پلن تکمیل است».
- DTO additions: `included_users`, `is_legacy_unlimited`, `active_members_count`,
  `available_slots`, `members`.
- Repository additions: add/rotate/revoke member.
- New strings in `values/strings.xml` and `values-fa/strings.xml`.
- Existing OTP/Shahkar/payment architecture is unchanged.
- Additive estimate: **4–6 engineer-days**.

### 21.3 Backend note

The earlier §7 “selected account” fix is already covered in the current control plane by
`AntiSanctionPurchaseIntent` (NEW vs RENEW + account). Verify this on the target branch; do
not implement a second intent mechanism.

### 21.4 Acceptance additions

- [ ] A 2-user package shows 2 slots; adding a second credential works; a third is rejected.
- [ ] A 5-user package allows primary + 4 members.
- [ ] Legacy account shows unlimited and no member actions until renewal.
- [ ] Expired renewal rotates the old link and opens onboarding.
- [ ] Active renewal keeps the current period unchanged; new count applies at activation.
- [ ] Member revoke/rotate works; old credentials stop for new connections.

---

## 22. Guest mode addendum

Full plan: `PLAN-GUEST-MODE-ANDROID-DESKTOP.md`.

Guest mode is required so a user who only received a config can use the app without OTP,
Shahkar, or a portal account; it is also the member onboarding path for the 1/2/5-user
HaioGate packages.

Changes to this Android plan:

- Add `Screen.Welcome` and `WelcomeScreen.kt` with two choices:
  - «ورود به حساب هایو»;
  - «مهمان — لینک کانفیگ دارم».
- Derive guest state from “no portal session + local config”; do not create a guest token or
  account.
- Add `welcomeSeen` to `HaioPrefs`; start at Welcome only when it is false and no config
  exists.
- Add a «مهمان» badge and account/login chip in the top bar and Settings.
- Keep `ConfigScreen` as the guest entry; add a guest subtitle, a «کپی لینک اتصال» action with
  a bearer-secret warning, and password reveal.
- Guest config sets `activeConfigSource = MANUAL` and `haiogateAccountId = null`.
- Login and logout preserve the current config; only an explicit HaioGate “اعمال روی اپ”
  replaces it.
- Guest sees a login card in the HaioGate/Store tab; no package/account/payment fetch.
- No backend changes; no new dependency for the core flow.
- Estimate: +3–5 Android engineer-days (shared guest plan total 6–10 with desktop).

Acceptance additions:

- [ ] Fresh install can choose guest, paste a valid link, and connect without login.
- [ ] Guest can copy the full current config; the link is masked in the UI and never logged.
- [ ] Guest → login and login → logout preserve the config.
- [ ] Guest HaioGate tab shows login, not packages/accounts.
- [ ] App restart preserves guest mode/config without repeating the welcome screen.
- [ ] Member can onboard via a shared link in guest mode; revoke/rotation cuts it off.

---

## 23. Plan-only statement

This document is a proposal. No application code, backend code, Gradle file, manifest, or
resource has been changed. Implementation starts only after the open questions are resolved
and the multi-user/guest-mode rollout in the shared plans is approved.
