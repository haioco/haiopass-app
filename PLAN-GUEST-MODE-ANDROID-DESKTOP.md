# Plan — Guest Mode for Android and Desktop

**Status:** Proposal for approval. **No implementation has been started.**
**Date:** 2026-11-14
**Platforms:** Android (`/home/devcloud/Documents/mine/Haio-Forever/HaioBypass-android`) and
Tauri desktop (`/home/devcloud/Documents/mine/Haio-Forever/HaioBypass-app`).
**Backend:** None. Guest mode is entirely client-side.
**Related plans:**
- `PLAN-OTP-HAIOGATE-ANDROID-FOCUSED.md`
- `PLAN-OTP-HAIOGATE-DESKTOP-FOCUSED.md`
- `PLAN-MULTI-USER-HAIOGATE.md` (in the control-plane repo)

---

## 1. Scope

### In scope

1. A clear **first-launch choice** between:
   - **ورود / خرید** — the portal OTP and HaioGate flow.
   - **مهمان — لینک کانفیگ دارم** — paste a config and connect without an account.
2. Guest use of the existing VPN engine:
   - paste a `trojan://` or `haio://` config from the clipboard;
   - validate and save it;
   - connect, disconnect, and use domains/bypass/settings;
   - view the current config and **copy the full connection link**.
3. Guest state across Android and desktop:
   - no portal session, no OTP, no Shahkar;
   - no HaioGate account list, purchase, or renewal;
   - no portal API calls.
4. Transitions:
   - guest → login without losing the current config;
   - logout → guest with the config preserved;
   - guest config → explicit replacement by a portal or legacy config.
5. Multi-user member onboarding:
   - the account owner shares a member `trojan://` link;
   - the member installs the app, chooses guest, pastes the link, and connects;
   - no account, OTP, or Shahkar is required for the member.
6. Guest-aware UI in the planned HaioGate/Store tabs and account chip.
7. Security, testing, acceptance criteria, and estimates.

### Out of scope

- Any backend, control-plane, or trojan server change.
- Creating a guest account, guest token, or separate identity system.
- Account linking, migration, or claiming a guest config into a portal account.
- QR-code import/export (Phase 2; clipboard is the MVP).
- Changes to the legacy store's product flow.
- Member management, member limits, or shared quota (covered by the multi-user plan).
- Cloud companion/super-app features.
- Payment, invoices, wallet, or Shahkar in guest mode.

### Non-negotiable rules

- Guest mode is **the absence of a portal session**, not a new authentication system.
- VPN connection must never require login.
- A guest config is treated as `MANUAL`; it is not a HaioGate account.
- Login must not silently overwrite the current config.
- Logout must not delete the current config.
- The config is a bearer secret; it must not be logged, analyzed, or exposed in crash reports.
- Guest mode must not call portal endpoints.

### Trojan server impact

**Guest mode alone requires no trojan code change.** The server already authenticates any
valid `users.password` row; a guest is simply a client using a config that already exists. No
new endpoint, schema column, auth path, sidecar change, or redeploy is needed for guest mode.

The server-side work belongs to the separate **multi-user** plan
(`PLAN-MULTI-USER-HAIOGATE.md`), not to guest mode:

- enforcing 1/2/5 **named users** per package;
- adding `users.group_id` and a shared `quota_groups` pool;
- joining group quota in `Authenticator::fetch_user`;
- recording usage against the group;
- adding sidecar group endpoints.

Guest members only consume the extra credentials created by that plan. If multi-user ships
without the trojan changes, the split-quota fallback also needs no trojan code, but it does
not provide a true shared pool. If only guest mode ships, skip all server work.

---

## 2. Executive summary

Both apps can already accept a pasted config, but the flow is not presented as a product choice:

- **Android** has a capable `ConfigScreen` (paste, validate, save, show current config) but no
  welcome screen, no guest badge, and no copy action. A new user meets a VPN app whose store
  assumes an account and whose config screen is buried in Settings/empty states.
- **Desktop** is already guest-first: the first screen asks for an access key and the dashboard
  appears after saving. But it does not name this mode, offer a login alternative, or let the
  user copy the current config.

Guest mode formalizes the existing manual-config path:

- First launch: two clear paths — login/buy, or guest with a config.
- Guest can paste, validate, connect, copy, and manage local settings without any account.
- Guest sees a “مهمان” badge and a login action; HaioGate/Store shows a login card instead of
  account data.
- Login keeps the current config; logout returns to guest and keeps the config.
- The config owner can share a member link; the member uses guest mode. This is the natural
  onboarding path for the 1/2/5-user HaioGate packages.
- No backend work, no new dependencies required for the core flow. Desktop may need a small
  clipboard fallback command/plugin for reliable copying inside the WebView.

**Effort:** Android 3–5 days, desktop 2–3 days, shared QA/docs 1–2 days. Total **6–10
engineer-days**. No server days.

---

## 3. Current state

### 3.1 Android

- `HaioPrefs` stores device id, legacy JWT, subscription cache, and theme in
  `SharedPreferences("haio_prefs")`.
- `ConfigManager` stores `trojanUrl` and parsed `trojanConfig` in
  `SharedPreferences("haio_config")`; `ConfigManager.config` is a StateFlow.
- `TrojanUrlParser` accepts `trojan://` and `haio://` and extracts password/server/port/SNI.
- `ConfigScreen`:
  - title «اتصال به روتر ابری»;
  - multiline «لینک اتصال» field;
  - «جای‌گذاری» from clipboard;
  - validation and inline errors;
  - save via `ConfigManager`;
  - «کانفیگ فعلی» card with server, port, SNI, masked password.
- There is no welcome route, guest state, copy action, QR, or account badge.
- `HaioBypassApp` has routes `main`, `domains`, `store`, `settings`, `config`. Connecting
  without a config shows a snackbar and navigates to Config.
- The planned portal work adds login, HaioGate accounts, Shahkar, and payment. Guest mode must
  remain fully usable outside that flow.
- `AndroidManifest` has `allowBackup="false"`.

### 3.2 Desktop

- Tauri v2, Rust backend, plain HTML/CSS/JS frontend.
- `index.html` `#configOnlyCard` is the onboarding screen:
  - “Paste your access key to get started”;
  - `#configInput`, `#saveConfigBtn`;
  - a link to `console.haio.ir`.
- `main.js`:
  - `parseAccessKey` validates `trojan://`/`haio://`;
  - `save_config` Rust command stores `trojan_url` + `trojan_config` in
    `~/.haiobypass/state.json`;
  - `showOnboarding()` shows the config card;
  - `showDashboard()` reveals the three-tab dashboard;
  - `delete_config` clears the config;
  - `VALID_TABS = ['home','bypass','settings']`.
- There is no explicit guest label, login alternative, account chip, or copy action.
- The planned portal work adds a fourth `هایو گیت` tab and a header account chip; guest mode
  must be the explicit no-session state.

### 3.3 Shared

- Both apps already parse the same `trojan://` shape used by HaioGate:
  `trojan://<password>@<host>:<port>?sni=<domain>#HaioGate`.
- Both apps already have a local config store and can connect without a portal account.
- Neither app currently has a first-class “guest” concept.

---

## 4. Decisions

| # | Decision | Recommendation | Reason |
|---|---|---|---|
| D1 | Is guest a new account type? | **No.** Guest = no portal session. | Avoid a second identity, token store, and support surface. |
| D2 | When is guest the default? | **When a local config exists and no portal session is present.** | Returning config users should land on the dashboard, not a login wall. |
| D3 | What is the config source? | **`MANUAL`**, with `haiogateAccountId = null`. | Reuses the existing source model; guest is a UI state, not a source. |
| D4 | Does login overwrite the guest config? | **No.** Only an explicit “اعمال روی اپ” action replaces it. | Prevents losing a working config during login. |
| D5 | Does logout delete the config? | **No.** Logout returns to guest and keeps the config. | The VPN should keep working; only the portal session ends. |
| D6 | Is copying a config first-class? | **Yes.** A visible “کپی لینک اتصال” action with a warning. | The user explicitly wants config copying; members need to share links. |
| D7 | Can guests buy/renew? | **No.** Show a login card. | Purchase requires portal identity, Shahkar, and billing. |
| D8 | Does guest mode change the legacy store? | **No.** Keep existing behavior. | Avoid breaking a working purchase path. |
| D9 | QR import/export in v1? | **No.** Clipboard only; QR in Phase 2. | Lower scope and no camera dependency. |
| D10 | How do multi-user members onboard? | **Owner shares a member link; member uses guest mode.** | No account/Shahkar for members; revocation is server-side. |
| D11 | Is guest mode persisted? | **Derived from config + no session**, plus a “welcome seen” flag. | Avoid a stale guest flag after login. |
| D12 | Should guest config be encrypted at rest? | **Follow-up.** Keep current storage for MVP; add encrypted storage later. | A broader storage migration; current app sandbox + no backup is acceptable for v1. |

---

## 5. Shared state model

### 5.1 Modes

```text
SessionMode = RESTORING | GUEST | LOGGED_IN
```

- **RESTORING** — app is checking the portal keyring/session at startup. Show a skeleton; do
  not flash the welcome screen.
- **GUEST** — no portal session. A local config may or may not exist.
- **LOGGED_IN** — a valid portal session exists.

Guest is not a third auth system. It is the state when `SessionMode != LOGGED_IN`.

### 5.2 Derived state

| Portal session | Local config | Start screen | Badge |
|---|---|---|---|
| Restoring | any | skeleton | — |
| None | none | welcome/onboarding | — |
| None | exists | dashboard | «مهمان» |
| Logged in | any | dashboard | account chip |

`hasConfig` is derived from `ConfigManager` / `state.json`, not from a separate guest flag.
Do not persist `guestMode`; derive it. Persist only `welcomeSeen` (Android) so a guest who
skips onboarding is not shown the welcome screen on every launch.

### 5.3 Transitions

| From | Action | To | Config |
|---|---|---|---|
| Welcome | «مهمان — لینک کانفیگ دارم» | Config | unchanged |
| Welcome | «ورود به حساب» | Login | unchanged |
| Guest | save valid config | Guest dashboard | written, source `MANUAL` |
| Guest | login success | Logged in dashboard | preserved |
| Logged in | logout | Guest dashboard | preserved |
| Guest | apply HaioGate config | Guest or logged-in dashboard | replaced, source `HAIOGATE` |
| Any | delete config | Welcome/empty home | cleared |

Rules:

- Do not auto-apply a portal/HaioGate config on login. The user must tap “اعمال روی اپ”.
- If login succeeds while a guest config exists, keep the guest config and show a non-blocking
  note: «کانفیگ فعلی شما حفظ شد. برای استفاده از کانفیگ هایو گیت، آن را اعمال کنید.»
- If logout happens while a HaioGate config is active, keep the config and the
  `HAIOGATE` source; the VPN continues to work. The account chip becomes «مهمان» and account
  screens require login.
- If the config is deleted, the source resets and the app returns to the empty/welcome state.

### 5.4 Storage

Android:

- `welcomeSeen: Boolean` in `HaioPrefs` (preference key `welcome_seen`).
- No `guestMode` key; derive from `PortalSessionStore` having no tokens and `ConfigManager`
  having a config.
- `activeConfigSource` remains `MANUAL` for guest.

Desktop:

- No new persisted guest flag. Derive from `portal_session.loggedIn == false` and
  `state.trojanUrl`.
- Optional `localStorage["haiobypass:welcomeSeen"]` only to avoid repeating the welcome
  overlay; not required.
- `state.json` remains the config source. Portal tokens remain in the OS keyring.

---

## 6. Shared user flows

### 6.1 First launch

Android:

1. Show `WelcomeScreen` if `welcomeSeen == false` and no config.
2. Primary action: «ورود به حساب هایو» → Login.
3. Secondary action: «مهمان — لینک کانفیگ دارم» → Config.
4. Tertiary text link: «بعداً» → Main in guest state, empty home.
5. Mark `welcomeSeen = true` after any choice.

Desktop:

1. Show `#configOnlyCard` with a clear title:
   «برای شروع، لینک اتصال خود را وارد کنید یا وارد حساب هایو شوید.»
2. Primary: paste + «ذخیره و اتصال» (existing `saveConfigBtn`).
3. Secondary: «ورود به حساب هایو» → login panel.
4. A small «مهمان» label explains that no account is required for a pasted config.

### 6.2 Guest paste and connect

1. User pastes a `trojan://` or `haio://` link.
2. Normalize whitespace; reject empty/invalid links with inline Persian errors.
3. Parse and validate host/port/SNI/password.
4. Save to the local config store and set source `MANUAL`.
5. Show the dashboard/home and the «مهمان» badge.
6. User presses connect; the existing VPN/proxy engine handles the rest.
7. If the config is revoked/expired, the connection fails normally; show a guest-specific
   message (see §9).

### 6.3 Copy the current config

- Entry points:
  - Android: «کانفیگ فعلی» card in `ConfigScreen`; optional home quick action.
  - Desktop: home/settings «کپی لینک اتصال»; optional onboarding card.
- Disabled when no config exists.
- Copy the **full** `trojan://...` string, not the masked display value.
- Show a confirmation toast/snackbar: «لینک اتصال کپی شد.»
- Show a warning before or with the copy:
  «هر کسی این لینک را داشته باشد می‌تواند تا زمان لغو، به این کانفیگ وصل شود.»
- Do not auto-share to other apps; copying is an explicit user action.
- Do not log the link or place it in analytics/crash reports.

### 6.4 Guest → login

- Available from the welcome screen, the header account chip, the HaioGate login card, and the
  Settings account row.
- Login does not clear the config.
- After login:
  - if the user has HaioGate accounts, show them in the HaioGate tab;
  - the current guest config remains active until the user applies a HaioGate config;
  - show a small note that the current config was preserved.
- If the user logs in only to buy a package, they can still return to guest behavior by
  logging out; the config stays.

### 6.5 Logout

- Clear portal tokens, user cache, org selection, and pending purchase display.
- Keep the local config and its source.
- Set mode to guest.
- Show «از حساب خارج شدید. کانفیگ فعلی شما حفظ شد.»
- HaioGate account screens become login cards; Home continues to work.

### 6.6 Member onboarding (multi-user)

1. The account owner opens the member list and chooses «کپی لینک اتصال» for a member.
2. The owner sends the link through Bale, email, or any messenger.
3. The member installs Android/desktop, chooses «مهمان — لینک کانفیگ دارم», pastes, and
   connects.
4. No OTP, Shahkar, or portal account is required for the member.
5. If the owner revokes the member or rotates the password, the old link stops working at the
   server; the member must request a new link.

### 6.7 Guest with no config

- Home shows an empty state:
  - «هنوز کانفیگی روی این دستگاه ندارید.»
  - «افزودن کانفیگ» → Config.
  - «ورود و خرید هایو گیت» → Login.
- HaioGate tab shows a login card; do not show package prices or account data.
- Connect is disabled until a config exists.

---

## 7. Android design

### 7.1 Routes and start destination

Add `Screen.Welcome` (`"welcome"`) and keep `Screen.Config`.

`HaioBypassApp`/`MainActivity` chooses the start destination:

```text
if !prefs.welcomeSeen && !config.hasConfig:
    Welcome
else:
    Main
```

If the portal session is still restoring, show a splash/skeleton first; do not choose Welcome
until restore finishes.

### 7.2 WelcomeScreen

New `ui/screens/WelcomeScreen.kt`:

- RTL, Vazirmatn-style typography consistent with the app.
- App icon/logo and title «به هایو بایپس خوش آمدید».
- Short copy: «برای استفاده از سرویس، یکی از دو روش را انتخاب کنید.»
- Primary card/button: «ورود به حساب هایو» with subtitle «خرید، تمدید و مدیریت هایو گیت».
- Secondary card/button: «مهمان — لینک کانفیگ دارم» with subtitle «اگر لینک اتصال دارید،
  بدون حساب وارد شوید».
- Text link: «بعداً» → Main.
- Set `prefs.welcomeSeen = true` when any option is chosen.

Do not ask for OTP on the welcome screen. Do not require Shahkar.

### 7.3 Guest badge and account chip

- Top app bar: when mode is guest, show a small `AssistChip`/badge «مهمان».
- Account icon:
  - guest → person outline; tap opens Login;
  - logged in → person filled/avatar; tap opens Account.
- Settings gets an «حساب هایو» row:
  - guest: «ورود به حساب» + «مهمان هستید»;
  - logged in: mobile/display name, Shahkar badge, «خروج از حساب».
- Do not show a guest badge when a portal session is active.

### 7.4 ConfigScreen changes

Keep the existing screen and `ConfigManager` path. Add:

1. A guest-aware subtitle under the title:
   «اگر لینک اتصال دارید، همین‌جا وارد کنید. نیازی به حساب نیست.»
2. A «کپی لینک اتصال» button in the «کانفیگ فعلی» card:
   - uses `LocalClipboardManager.setText(AnnotatedString(config.trojanUrl))`;
   - enabled only when `config.trojanUrl` is non-blank;
   - shows a snackbar «لینک اتصال کپی شد»;
   - shows the bearer-secret warning in a helper text or confirmation dialog.
3. Reveal/hide for the password in the current-config card (currently masked).
4. Optional «اشتراک‌گذاری» via `Intent.ACTION_SEND` with the full link; not required for MVP.
5. Keep «جای‌گذاری» and validation.
6. On save, set `activeConfigSource = MANUAL` and clear `haiogateAccountId`.
7. Do not log the link; no `Log.d` of `trojanUrl`.

### 7.5 Main/Home changes

- If no config and guest:
  - show a prominent «افزودن کانفیگ» card;
  - show a secondary «ورود و خرید هایو گیت» card;
  - connect disabled.
- If config exists:
  - normal home;
  - guest badge;
  - optional quick action «کپی کانفیگ» next to the config summary.
- Do not call legacy subscription detail or portal endpoints in guest mode.
- If the legacy subscription cache exists, the existing 30-second refresh may still run; it is
  independent of guest mode. Do not overwrite a `MANUAL` guest config with legacy data.
- `activeConfigSource == MANUAL` must prevent legacy auto-refresh from replacing the config.

### 7.6 Store / HaioGate tab

- Guest sees a login card at the top of the HaioGate inner tab:
  - «برای خرید و مدیریت هایو گیت وارد شوید.»
  - «ورود به حساب» → Login.
  - «مهمان هستم؛ لینک کانفیگ دارم» → Config.
- Do not show packages, accounts, renewal, or payment to guests.
- The legacy «پلن‌های قبلی» inner tab keeps its current behavior. If it requires an account,
  show its existing prompts; do not add new guest logic there.
- After login, the HaioGate tab loads normally.

### 7.7 Persistence and state

- `HaioPrefs`:
  - add `welcomeSeen` (`Boolean`, key `welcome_seen`);
  - keep `activeConfigSource` / `haiogateAccountId` from the portal plan;
  - no `guestMode` key.
- `ConfigManager` remains the source of `trojanUrl`/`trojanConfig`.
- Guest state is derived:
  ```kotlin
  val isGuest = sessionState is LoggedOut && config.trojanUrl != null
  ```
- If the portal session is restoring, don't show guest UI until it resolves.

### 7.8 Android file map

| File | Change |
|---|---|
| `HaioPrefs.kt` | Add `welcomeSeen` |
| `ui/screens/WelcomeScreen.kt` | New welcome/choice screen |
| `ui/HaioBypassApp.kt` | Start destination, guest badge/account chip, gate Store, pass guest state |
| `MainActivity.kt` | Decide start route from `welcomeSeen` + config; no portal calls for guest |
| `ui/screens/ConfigScreen.kt` | Guest subtitle, copy link, reveal password, bearer warning |
| `ui/screens/MainScreen.kt` | Empty guest state, add-config CTA, optional copy action |
| `ui/screens/SettingsScreen.kt` | Account row for guest/login/logout |
| `ui/screens/StoreScreen.kt` / future `HaioGateScreen.kt` | Guest login card; no package/account fetch |
| `res/values/strings.xml`, `res/values-fa/strings.xml` | Guest strings (see §9) |
| `app/src/test/...` | Guest state derivation, config copy formatting, source preservation |
| `app/src/androidTest/...` | Welcome → guest → paste → connect; guest → login; logout keeps config |

No Gradle dependency is required for the core flow. Clipboard uses Compose's existing
`LocalClipboardManager`.

---

## 8. Desktop design

### 8.1 Onboarding card

Keep `#configOnlyCard` as the guest path, but make the choice explicit:

- Title: «به هایو بایپس خوش آمدید».
- Body: «لینک اتصال خود را وارد کنید یا وارد حساب هایو شوید.»
- Config input + «ذخیره و اتصال» (existing).
- Secondary button: «ورود به حساب هایو» → portal login view.
- Small label «مهمان» next to the paste path.
- Link «راهنمای دریافت کانفیگ» → `https://console.haio.ir` (existing external link).
- Do not remove the existing paste-first behavior; it is the guest path and must remain fast.

### 8.2 Header and account chip

- Header shows:
  - guest: `مهمان` chip + «ورود» button;
  - logged in: display name/mobile + account menu;
  - restoring: skeleton/disabled chip.
- Clicking guest chip opens login or account info? Recommendation: guest chip opens a small
  popover explaining guest mode and offering «ورود به حساب».
- Do not show portal account data to guests.

### 8.3 Tab bar

Current tabs: خانه، عبور مستقیم، تنظیمات. The portal plan adds `هایو گیت`. Guest behavior:

- `هایو گیت` tab remains visible but shows a login card:
  - «برای خرید و مدیریت هایو گیت وارد شوید.»
  - «ورود به حساب»;
  - «مهمان هستم؛ لینک کانفیگ دارم» → onboarding card.
- No package/account fetch when guest.
- Home, bypass, settings remain fully usable.
- `VALID_TABS` becomes `['home','haiogate','bypass','settings']`; guest does not change the
  tab list, only the content.

### 8.4 Copy current config

- Add «کپی لینک اتصال» to:
  - Home quick actions;
  - Settings connection card;
  - optional onboarding current-config card.
- Read `get_state().trojanUrl`; do not reconstruct from parts.
- Prefer `navigator.clipboard.writeText`; if unavailable/blocked, use a Tauri clipboard
  command or a hidden textarea fallback.
- If a new dependency is needed, add `tauri-plugin-clipboard-manager` (plan only; do not add
  unless the fallback is insufficient).
- Show a toast «لینک اتصال کپی شد» and the bearer-secret warning.
- Disable when no config.

### 8.5 State and visibility

- `portal_session.loggedIn == false` and `state.trojanUrl` exists → guest dashboard.
- `portal_session.loggedIn == false` and no config → onboarding.
- `portal_session.restoring == true` → skeleton; do not show onboarding until restore
  completes.
- Do not persist a guest flag. Derive from session + config.
- `save_config` remains the guest entry point; it must set source `manual` and clear
  `haiogate_account_id`.
- `delete_config` returns to onboarding/empty home.
- Login does not modify `trojan_url` or `trojan_config`.
- Logout keeps the config and hides account-only data.

### 8.6 Portal module behavior

- Guest mode must not initialize network calls, token refresh, or pending purchase polling.
- `portal_session` can still be queried locally; if no tokens, return `{loggedIn:false}`.
- The keyring is only touched when the user logs in or a session exists.
- If the keyring is unavailable, guest mode is unaffected.

### 8.7 Desktop file map

| File | Change |
|---|---|
| `src/index.html` | Welcome copy, guest label, login button, copy button(s), HaioGate guest card |
| `src/scripts/main.js` | Guest derivation, account chip, copy flow, gate HaioGate view, onboarding copy |
| `src/scripts/i18n.js` | FA/EN guest strings |
| `src/styles/style.css` | Guest badge, welcome card actions, copy row, login card |
| `src-tauri/src/app/commands.rs` | No change for core guest; optional clipboard command if fallback needed |
| `src-tauri/src/config/mod.rs` | `save_config` source manual/clear account (already in portal plan) |
| `src-tauri/capabilities/default.json` | Only if a clipboard plugin is added |
| `src-tauri/Cargo.toml` / `package.json` | Only if a clipboard plugin is added |

No backend changes.

---

## 9. UX copy

### 9.1 Welcome / onboarding

| Key | Persian |
|---|---|
| welcome_title | به هایو بایپس خوش آمدید |
| welcome_subtitle | برای استفاده از سرویس، یکی از دو روش را انتخاب کنید. |
| welcome_login_title | ورود به حساب هایو |
| welcome_login_subtitle | خرید، تمدید و مدیریت هایو گیت |
| welcome_guest_title | مهمان — لینک کانفیگ دارم |
| welcome_guest_subtitle | بدون حساب وارد شوید و لینک اتصال خود را وارد کنید. |
| welcome_later | بعداً |
| onboarding_desktop_title | لینک اتصال خود را وارد کنید یا وارد حساب هایو شوید. |

### 9.2 Guest badge / account

| Key | Persian |
|---|---|
| guest_badge | مهمان |
| guest_account_hint | شما در حالت مهمان هستید. برای خرید و مدیریت هایو گیت وارد شوید. |
| login_action | ورود به حساب |
| logout_action | خروج از حساب |
| logout_keeps_config | کانفیگ فعلی شما حفظ می‌شود. |
| guest_config_preserved | کانفیگ فعلی شما حفظ شد. برای استفاده از هایو گیت، آن را اعمال کنید. |

### 9.3 Config screen

| Key | Persian |
|---|---|
| config_title | اتصال به روتر ابری |
| config_guest_subtitle | اگر لینک اتصال دارید، همین‌جا وارد کنید. نیازی به حساب نیست. |
| config_link_label | لینک اتصال |
| config_paste | جای‌گذاری |
| config_save | ذخیره و اتصال |
| config_current | کانفیگ فعلی |
| config_empty | هنوز کانفیگی روی این دستگاه ندارید. |
| config_add | افزودن کانفیگ |
| config_copy | کپی لینک اتصال |
| config_copied | لینک اتصال کپی شد. |
| config_copy_warning | هر کسی این لینک را داشته باشد می‌تواند تا زمان لغو، به این کانفیگ وصل شود. |
| config_invalid | لینک اتصال معتبر نیست. |
| config_required | ابتدا یک کانفیگ اضافه کنید. |

### 9.4 HaioGate guest card

| Key | Persian |
|---|---|
| guest_haiogate_title | برای خرید و مدیریت هایو گیت وارد شوید. |
| guest_haiogate_body | با شماره موبایل وارد شوید یا اگر لینک کانفیگ دارید، از حالت مهمان استفاده کنید. |
| guest_haiogate_login | ورود به حساب |
| guest_haiogate_config | مهمان هستم؛ لینک کانفیگ دارم |

### 9.5 Copy / member sharing

| Key | Persian |
|---|---|
| member_copy_config | کپی لینک اتصال کاربر |
| member_share_hint | این لینک را برای کاربر بفرستید. با لغو کاربر، لینک از کار می‌افتد. |
| member_revoked | این لینک دیگر فعال نیست. از مدیر حساب لینک جدید بگیرید. |
| guest_expired_hint | اگر اتصال برقرار نشد، ممکن است کانفیگ منقضی شده یا توسط مدیر حساب لغو شده باشد. |

---

## 10. Multi-user member onboarding

Guest mode is the member-side onboarding path for the 1/2/5-user HaioGate packages.

### 10.1 Owner side

- In the account detail / members screen, each member row has:
  - display name;
  - username;
  - status (active/pending/revoked);
  - «کپی لینک اتصال»;
  - «چرخش رمز» / «لغو».
- Copy uses the full member `trojan://` link.
- Warning: anyone with the link can connect until revoked.
- Do not show the owner's primary config as a member config; each member has a distinct link.

### 10.2 Member side

1. Receives the link via Bale/email/messenger.
2. Installs Android or desktop.
3. Chooses «مهمان — لینک کانفیگ دارم».
4. Pastes the link and connects.
5. No OTP, Shahkar, or portal account.
6. If the link is revoked/rotated, the connection fails; the app shows the guest expiry hint.

### 10.3 Edge behavior

- A member can also log in later with their own mobile and buy their own package; the current
  guest config is preserved until replaced.
- A member cannot see the owner's usage, expiry, invoice, or member list. Guest has no account
  context.
- The owner is responsible for distributing links; the app does not send invitations in MVP.
- The guest member link is a bearer credential; forwarding it is equivalent to sharing access.

---

## 11. Security and privacy

- **Bearer secret**: a `trojan://` link grants access until the server removes/rotates it.
  Treat it like a password.
- **Masking**: show the password masked by default; reveal only on explicit tap. The copy
  button copies the full link, not the masked value.
- **Logging**: never log the full config, password, or link. Do not include it in analytics,
  crash reports, or support bundles.
- **Clipboard**: after copy, show a warning. Optional: clear the clipboard after 60 seconds if
  the platform supports it; do not silently clear the user's clipboard without a setting.
- **Screenshots**: optional `FLAG_SECURE` on the config/current-config card; do not apply
  globally. Desktop: no screenshot protection.
- **Backups**: Android already has `allowBackup="false"`. Desktop `state.json` should be
  `0600` on Unix (already in the desktop plan).
- **Encryption at rest**: Android currently stores the config in plain `SharedPreferences`.
  App sandbox + no backup is acceptable for MVP; moving `ConfigManager` to encrypted storage is
  a follow-up. Desktop stores the config in plain `state.json`; keep `0600`.
- **No portal tokens in guest mode**: guest must not create keyring entries or call portal
  endpoints.
- **Clipboard permission**: desktop WebView clipboard may be restricted; use a user gesture
  and a fallback command/plugin. Never request broad clipboard-read permission just for guest
  mode.

---

## 12. Edge cases

| Case | Behaviour | Copy |
|---|---|---|
| No config, guest | Empty home + add CTA | «هنوز کانفیگی روی این دستگاه ندارید.» |
| Invalid link | Inline error, do not save | «لینک اتصال معتبر نیست.» |
| Empty clipboard | No-op, keep field | «چیزی برای جای‌گذاری پیدا نشد.» |
| Link has whitespace | Trim and parse | — |
| Link uses `haio://` | Accept | — |
| Link is a HaioGate member link | Treat as manual | — |
| Config expired/revoked | Connection fails; explain | «ممکن است کانفیگ منقضی یا لغو شده باشد.» |
| Guest taps HaioGate | Login card | «برای خرید و مدیریت هایو گیت وارد شوید.» |
| Guest logs in | Config preserved | «کانفیگ فعلی شما حفظ شد.» |
| Logged-in user logs out | Guest, config preserved | «کانفیگ فعلی شما حفظ می‌شود.» |
| Login with HaioGate account | Do not auto-apply | User taps «اعمال روی اپ» |
| Apply HaioGate over guest config | Confirm replacement | «کانفیگ فعلی جایگزین می‌شود. ادامه می‌دهید؟» |
| Guest deletes config | Return to empty/welcome | «کانفیگ حذف شد.» |
| Clipboard blocked (desktop) | Fallback or manual select | «کپی خودکار ممکن نشد؛ لینک را دستی انتخاب کنید.» |
| Copy without config | Disabled | — |
| Member revoked | Old link fails | «از مدیر حساب لینک جدید بگیرید.» |
| Owner rotates member password | Old link fails | Same |
| Guest has no network | Connection error; config remains | «اتصال به سرور برقرار نشد.» |
| App killed after paste | Config persists | — |
| Portal session restore fails | Guest mode, config preserved | Do not show login wall |

---

## 13. Testing

### 13.1 Android unit tests

- Guest state derivation:
  - no session + no config → onboarding;
  - no session + config → guest dashboard;
  - session + config → logged in.
- `saveConfig` sets source `MANUAL` and clears `haiogateAccountId`.
- Copy uses the full `trojanUrl`, not the masked value.
- Logout keeps the config.
- Login keeps the config.
- Deleting config clears source/account id.
- No portal call is made in guest mode (fake repository throws if invoked).

### 13.2 Android UI/instrumentation

- Fresh install → Welcome → guest → paste valid link → save → dashboard with «مهمان».
- Welcome → login → OTP success → dashboard; config preserved if it existed.
- Guest → HaioGate tab → login card; no package fetch.
- Guest → Settings → account row → login.
- Copy current config → clipboard contains the full link; snackbar shown.
- Invalid link → inline error; no save.
- Logout → guest badge; config still connects.
- App restart → guest state restored; no welcome flash.
- Delete config → empty home; add CTA.

### 13.3 Desktop tests

- No config → onboarding card with guest copy and login button.
- Save valid config → dashboard; guest chip visible.
- Copy current config → clipboard contains full link; toast.
- Copy with no config → disabled.
- Guest → HaioGate tab → login card; no network calls.
- Guest → login → session; config preserved.
- Logout → guest chip; config preserved.
- Restart → config restored; guest chip derived.
- Delete config → onboarding.
- Clipboard blocked → fallback/manual message.

### 13.4 Manual E2E

1. Android fresh install, guest path, paste a working member link, connect, browse.
2. Desktop fresh install, guest path, paste the same link, connect.
3. Owner copies a member link from the HaioGate members screen; member uses it in guest mode.
4. Owner revokes/rotates the member; old link fails on both apps.
5. Guest logs in, sees HaioGate accounts, keeps old config, explicitly applies a new config.
6. Logout; old config still connects.
7. No login/Shahkar/payment appears anywhere in the guest path.

### 13.5 Regression checks

- Manual config, legacy store, VPN connect, domains, bypass, settings, theme/language.
- Android `allowBackup=false` unchanged.
- Desktop `state.json` permissions unchanged/0600.
- No secrets in logcat, browser console, or crash reports.

---

## 14. Phases and estimates

| Phase | Work | Estimate |
|---|---|---|
| 1. Shared state + Android welcome | `WelcomeScreen`, `welcomeSeen`, start destination, guest badge, account chip | 2–3 days |
| 2. Android config/copy/gating | Guest subtitle, copy action, reveal, Store/HaioGate login card, empty states | 1–2 days |
| 3. Desktop onboarding/copy/gating | Guest label, login button, account chip, copy action, HaioGate login card, i18n/CSS | 2–3 days |
| 4. QA + docs | Unit/UI/manual E2E, strings, screenshots, release notes | 1–2 days |
| **Total** | | **6–10 engineer-days** |

Assumptions:

- The portal/HaioGate plans are implemented or in progress; guest mode integrates with their
  session state.
- Android uses Compose's existing clipboard; desktop uses the WebView clipboard with a small
  fallback if needed.
- No backend change.
- Guest mode can ship with Android `1.3.0` and desktop `2.1.0`, or earlier as a standalone
  improvement.
- Multi-user member onboarding depends on the multi-user plan; guest mode itself does not.

---

## 15. Acceptance criteria

### Guest entry

- [ ] Fresh Android install shows a welcome screen with login and guest choices.
- [ ] Fresh desktop install shows an onboarding card with paste and login choices.
- [ ] Guest can paste a valid `trojan://`/`haio://` link and connect without OTP, Shahkar, or
      a portal account.
- [ ] Guest mode makes no portal API calls and creates no keyring/token entries.

### Config copy

- [ ] Android can copy the current full config from the config screen.
- [ ] Desktop can copy the current full config from home/settings.
- [ ] Copy is disabled with no config.
- [ ] Copy shows a bearer-secret warning.
- [ ] The config is masked in the UI and never logged.

### State transitions

- [ ] Guest → login preserves the current config.
- [ ] Logout → guest preserves the current config.
- [ ] Login does not auto-apply a HaioGate config.
- [ ] Applying a HaioGate config asks before replacing the current config.
- [ ] Deleting the config returns to the empty/welcome state.
- [ ] App restart preserves the guest config and does not show the welcome screen repeatedly.

### Gating

- [ ] Guest sees a login card in HaioGate/Store; no packages/accounts/payment.
- [ ] Guest can use Home, Config, Domains, Bypass, Settings.
- [ ] Legacy store behavior is unchanged.
- [ ] Account chip shows «مهمان» with a login action.

### Multi-user onboarding

- [ ] Owner can copy a member link.
- [ ] Member can paste it in guest mode and connect.
- [ ] Revoked/rotated links stop working.
- [ ] Member does not need Shahkar or a portal account.

### Security

- [ ] No config link/password in logs, analytics, or crash reports.
- [ ] Android `allowBackup=false`; desktop config file has restrictive permissions.
- [ ] Clipboard fallback does not expose the link to other apps without user action.

---

## 16. Open questions

1. **Guest and legacy store**: should the legacy «پلن‌های قبلی» tab remain fully visible to
   guests, or should it show a login prompt? Recommendation: keep it unchanged.
2. **Config encryption at rest**: move Android `ConfigManager` to encrypted storage now, or
   follow up? Recommendation: follow up; current sandbox/no-backup is acceptable for MVP.
3. **QR codes**: add QR import/export in v1? Recommendation: no; clipboard is enough, QR is
   Phase 2.
4. **Clipboard clearing**: auto-clear after 60 seconds, or only warn? Recommendation: warn;
   optional setting later.
5. **Desktop clipboard**: use `navigator.clipboard` with fallback, or add
   `tauri-plugin-clipboard-manager`? Recommendation: try native first; add plugin only if
   WebView blocks it.
6. **Guest login CTA placement**: always visible in the header, or only in HaioGate/Settings?
   Recommendation: header chip + HaioGate card + Settings row.
7. **Member invitations**: should the app send the link via Bale/email, or only copy? Recommend:
   copy only in MVP; sharing is external.
8. **Guest expiry messaging**: should the app show “expired” if the server rejects, or only a
   generic connection error? Recommendation: generic with a hint to contact the owner; the
   client cannot know the server reason.

---

## 17. File-level change map

### Android

| File | Change |
|---|---|
| `app/src/main/java/com/haio/bypass/HaioPrefs.kt` | `welcomeSeen` key |
| `app/src/main/java/com/haio/bypass/ui/screens/WelcomeScreen.kt` | New |
| `app/src/main/java/com/haio/bypass/ui/HaioBypassApp.kt` | Start destination, guest badge, account chip, gate Store, pass guest state |
| `app/src/main/java/com/haio/bypass/MainActivity.kt` | Choose start route; no portal init for guest |
| `app/src/main/java/com/haio/bypass/ui/screens/ConfigScreen.kt` | Guest subtitle, copy, reveal, warning |
| `app/src/main/java/com/haio/bypass/ui/screens/MainScreen.kt` | Empty guest state, add-config CTA, optional copy |
| `app/src/main/java/com/haio/bypass/ui/screens/SettingsScreen.kt` | Account row for guest/login/logout |
| `app/src/main/java/com/haio/bypass/ui/screens/StoreScreen.kt` / future `HaioGateScreen.kt` | Guest login card; no fetch |
| `app/src/main/res/values/strings.xml`, `values-fa/strings.xml` | Guest strings |
| `app/src/test/...` | Guest state/source/copy tests |
| `app/src/androidTest/...` | Welcome/paste/connect/login/logout tests |

### Desktop

| File | Change |
|---|---|
| `src/index.html` | Welcome copy, guest label, login button, copy buttons, HaioGate guest card |
| `src/scripts/main.js` | Guest derivation, account chip, copy, gate HaioGate, onboarding copy |
| `src/scripts/i18n.js` | FA/EN guest strings |
| `src/styles/style.css` | Guest badge, welcome actions, copy row, login card |
| `src-tauri/src/app/commands.rs` | Optional clipboard command only |
| `src-tauri/capabilities/default.json` | Only if clipboard plugin added |
| `src-tauri/Cargo.toml` / `package.json` | Only if clipboard plugin added |

### Not changed

- Backend/control plane/trojan server.
- Payment/Shahkar/OTP flows.
- VPN engine, proxy, routing, domains.
- Legacy store product flow.
- Multi-user group/member backend.

---

## 18. What ships when

### MVP (this plan)

- Android welcome screen with login vs guest.
- Desktop explicit guest onboarding.
- Guest paste/validate/save/connect.
- Guest badge/account chip.
- Copy current config on both apps with warning.
- Guest-aware HaioGate/Store login cards.
- Login/logout config preservation.
- Member link onboarding via guest mode.
- Tests, strings, security checks.

### Phase 2 (optional)

- QR import/export.
- Encrypted config storage on Android.
- Clipboard auto-clear setting.
- Member invitation sending (Bale/email) from the app.
- "Claim this config" account linking (only if product needs it).
- Guest usage/expiry lookup (requires a server endpoint and a non-account token; not planned).

---

## 19. Plan-only statement

This document is a proposal. No Android, desktop, backend, server, Gradle, manifest, HTML, CSS,
JavaScript, or Rust code has been changed. Implementation starts only after the decisions in
§16 are resolved and the related portal/multi-user plans are approved.
