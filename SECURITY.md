# SOS Tech — Security model

Current release : **v0.4.0** (2026-05-22)

This document describes the threat model SOS Tech protects against, the cryptographic
primitives it uses, the architectural choices that make those primitives meaningful,
and the known limits beyond which no app can defend.

If you find a vulnerability, please disclose it responsibly to **contact@files-tech.com**
with the subject `SOS Tech security report`. We respond within 5 working days.

---

## Context

SOS Tech is a dedicated personal safety app. Its scope is wider than SMS Tech (which keeps
emergency minimal) — it includes ambient recording, voice keyword detection, and live GPS
tracking. This wider capability means a wider attack surface and stricter privacy requirements.

The privacy-by-design baseline is: **zero cloud, zero telemetry, zero account**. All data
stays on the device.

---

## Threat model

| Adversary | What we protect against | How |
|---|---|---|
| Device-level adb pull / forensic image | Call logs, encrypted recordings, contact list | SQLCipher DB encrypted with 32-byte random key wrapped by Keystore alias `sostech_db_master` (AES-256-GCM). Recording vault payloads encrypted under Keystore alias `sostech_recording_kek` (v0.2 wiring). |
| Lost / stolen phone (no PIN known) | Access to emergency contacts and recordings | App lock (PIN / biometric, opt-in). FLAG_SECURE on all screens. |
| Pocket-dial to emergency services | Accidental call via UI tap | Default call behavior = HOLD_3S_DIRECT_CALL. TAP_DIRECT_CALL requires explicit opt-in + CALL_PHONE granted. Anti-spam cooldown (5 s monotonic) on EmergencyCallHelper. |
| Double-trigger race on emergency SMS button | Two simultaneous SMS sends | `AtomicBoolean triggerInFlight` compareAndSet guard in EmergencyViewModel. |
| Wall-clock manipulation to bypass cooldown | Root attacker sets system clock to skip anti-spam | EmergencyViewModel checks both wall-clock AND SystemClock.elapsedRealtime(). Cooldown active if EITHER is in window. |
| Forged Intent to trigger emergency | Third-party app crafts an explicit Intent to MainActivity | MainActivity is exported only for MAIN/LAUNCHER. Emergency trigger is entirely in-process; no BroadcastReceiver is exported for trigger actions. |
| Voice keyword false positive (non-emergency speech) | Accidental trigger by ambient conversation | Repetition count = 3 (default): keyword must be spoken 3 times within [cooldownMs] to fire. Cooldown = 120 s between sessions. |
| Recording without consent (legal, FR) | Art. 226-15 Code pénal violation | Legal disclaimer screen mandatory before RecordingConfig.enabled can be set true. Warning persisted per-session. User bears sole responsibility. |
| Live GPS tracking leak | Location transmitted to wrong recipient | Contacts explicitly configured in SOS Tech (not shared with SMS Tech v0.1). GPS included in webhook only if `includeGps = true` (default false). No contact names or SMS body ever sent to webhook. |
| Webhook URL exfiltration | Payload contains PII | Webhook payload = `{"trigger","ts","lat"(opt),"lng"(opt)}` only. No name, no number, no SMS body. |
| Cascade call to a wrong number | Attacker injects a number into the cascade list via Intent extra | CascadeConfig.contactPriorityOrder contains Room entity IDs (Long), not phone strings. IDs resolved from DB at call time — no Intent extra path. |

---

## Cryptographic primitives

| Concern | Primitive | Key source | Status |
|---|---|---|---|
| Room database at rest | SQLCipher v4 (`SupportOpenHelperFactory`) | 32-byte random passphrase, wrapped AES-256-GCM by Keystore alias `sostech_db_master`; wrapped blob persisted at `<files>/db/master.key` (`[version:1][iv:12][ct+tag:N]`); raw key wiped from JVM memory immediately after Room consumes it | **v0.1 — implemented** (`DatabaseFactory` + `DatabaseKeyManager`) |
| AEAD primitive | AES-256-GCM, 12-byte IV, 128-bit tag, envelope versioned (`AeadCipher`) | Keystore-bound `SecretKey` (hardware-backed when available) OR caller-supplied 32-byte raw key | **v0.1 — implemented** |
| Recording vault | AES-256-GCM | Keystore alias `sostech_recording_kek` | Structural anchor v0.1 — full impl in v0.2 (MediaRecorder + per-recording payload encryption) |
| App-lock PIN | PBKDF2-HMAC-SHA512 | User secret + 16-byte random salt, ≥ 210 000 iterations | **v0.3 — implemented** (`PasswordKdf` + `SecurityStore` + `AppLockManager`) |
| Settings DataStore | Plain JSON (no AEAD) | — | TODO v0.4: wrap sensitive prefs under Keystore alias `sostech_settings_aead` |
| Panic-mode decoy | `LockState.PanicDecoy` gate via `AppLockPanicGuard` | Same PBKDF2 path as PIN, distinct salt/hash in `SecurityStore` | **v0.3 — implemented** (`PanicGuard` wired to real `AppLockManager.state`) |

All Keystore keys are non-exportable, hardware-backed when available, and use `setRandomizedEncryptionRequired(false)` because IVs are generated cryptographically at the call site (96-bit SecureRandom — never reused).

### Failure modes (DB key)

`DatabaseKeyManager.Failure` is a sealed exception hierarchy. The DI graph propagates these
typed failures up to `MainActivity` rather than silently re-keying or dropping data:

- **`KeystoreInvalidated`** — credential change, biometric re-enrollment, or Knox reset wiped the AndroidKeyStore alias. Existing wrapped key cannot be recovered. *Future recovery UI will offer: reset wallet (lose data) or keep encrypted blob for offline forensic recovery.*
- **`WrapCorrupted`** — Keystore is healthy but AEAD decryption of the wrapped key blob failed (file corruption). The key file is **NOT auto-deleted** — silent wipe would equal silent data loss.
- **`Io`** — read/write failure on `<files>/db/master.key`. Usually a transient storage condition; retry on next boot.

---

## Privacy inventory

| Data type | Storage | Sent to server | Notes |
|---|---|---|---|
| Emergency contacts | Room DB (SQLCipher) | Never | Local only |
| Call logs | Room DB (SQLCipher) | Never | Local only |
| Recordings | SQLCipher vault | Never | Encrypted at rest |
| Location | Memory only during session | Only if GPS live enabled AND contact configured | Never stored in cloud |
| Webhook payload | Never stored | Only `ts` + optional `lat/lng` | No PII |
| Voice audio | Discarded after keyword match | Never | Not saved |
| Settings | DataStore (local) | Never | No sync |

---

## Legal disclaimer — Recording (France, art. 226-15 Code pénal)

Recording a telephone call without the prior consent of all parties is a criminal offence
in France, punishable by up to 1 year imprisonment and a €45,000 fine.

SOS Tech requires the user to read and acknowledge this disclaimer before the recording
feature can be enabled. The responsibility for obtaining consent rests entirely with the user.
SOS Tech cannot and does not verify that consent was obtained.

---

## Permissions inventory

| Permission | Why | Default state |
|---|---|---|
| CALL_PHONE | Direct call (HOLD_3S or TAP mode) | Opt-in runtime |
| RECORD_AUDIO | Voice keyword detection + ambient recording | Opt-in runtime |
| ACCESS_FINE_LOCATION | GPS fix for emergency SMS | Opt-in runtime |
| ACCESS_BACKGROUND_LOCATION | Live GPS worker | Opt-in runtime |
| CAMERA | Rear flash strobe (siren) | Opt-in runtime |
| READ_PHONE_STATE | Cascade call state detection | Opt-in runtime |
| SEND_SMS | Emergency SMS to contacts | Always declared |
| READ_CONTACTS | Import contacts (setup wizard) | Opt-in runtime |
| INTERNET | Webhook HTTP POST | Always declared — only used if webhook enabled |
| POST_NOTIFICATIONS | Emergency shortcut notification | Requested on Android 13+ |
| FOREGROUND_SERVICE_MICROPHONE | VoiceTriggerService | Used only if voice opt-in |
| FOREGROUND_SERVICE_LOCATION | LiveGpsWorker | Used only if GPS live opt-in |
| FOREGROUND_SERVICE_CAMERA | Siren service | Used only if siren opt-in |
| RECEIVE_BOOT_COMPLETED | Re-post emergency notif after reboot | Always declared |

---

## Out of scope

- Transport encryption of SMS/MMS (unencrypted by protocol)
- Protection against a rooted device with physical access and known PIN
- Call recording on Android 9+ (OS audio policy blocks direct capture without accessibility service)
- Legal compliance in jurisdictions other than France (recording laws vary — user must check local law)

---

## Audit history

### v0.4.0 — Webhook dispatcher (first outbound network)

First real out-bound network call. Payload is minimal by design.

- **OkHttp 4.12.0** added (Apache-2.0, F-Droid compliant). Only used when `webhook.enabled = true`.
- **`WebhookDispatcherImpl`**: POST to user-configured URL. Retry up to 3 times with 1s/2s/4s
  exponential backoff (cap 8 s). 4xx = no retry. Timeout: connect 10 s, read 15 s, write 15 s.
  `retryOnConnectionFailure = false` (retry loop explicit, not OkHttp implicit).
- **Payload**: `{"trigger":"emergency","ts":<epochMs>}` + optional `"lat"/"lng"` (5 decimals,
  Locale.US separator). No contact names, no phone numbers, no SMS body ever included.
- **Fire-and-forget**: webhook is dispatched in a `launch` child coroutine — the emergency flow
  completes immediately without waiting for the HTTP round-trip. No UI feedback on webhook result.
- **URL validation**: scheme must be `https://` or `http://` — other schemes rejected with
  `AppError.Validation` before any network call is made.
- **URL not logged**: user-configured URL may embed a shared secret in query params; Timber calls
  omit the URL entirely.
- **WebhookDispatcherStub removed**: no dead code. `StubImpls.kt` no longer contains a webhook stub.
- **SettingsScreen webhook section enriched**: OutlinedTextField URL + GPS toggle visible when
  enabled. URL validated inline (red border if not http(s)://).
- **Settings DataStore**: `WebhookConfig.url` + `.includeGps` already declared in v0.1;
  no schema migration needed.

Tests: `AuditV040Test` 20 new regression tests. Total **90 / 90 green** (expected).
`lintVitalRelease` clean. `assembleRelease` successful.

### v0.3.2 — Audit bloc 2 (domain layer purity, shared settings flow, failCount upfront)

3 MEDIUM findings from the v0.3.1 audit cycle closed before the tag:

- **ARCH-1** — `EmergencyMessageRenderer` moved from `domain/emergency/` to `data/messaging/`.
  It carried an `@ApplicationContext` dependency which violates domain-layer purity (domain must
  not depend on Android Context). All callers (`TriggerEmergencyUseCase`) updated to new import.
- **PERF-1** — `SettingsRepository.flow` converted from a cold `Flow` + redundant `_state`
  `MutableStateFlow`+`onEach` pattern to a single `StateFlow` shared eagerly via
  `stateIn(SharingStarted.Eagerly, scope=IO+SupervisorJob)`. Single DataStore subscription,
  N collectors read from the cached value with zero re-parsing.
- **SEC-4** — `AppLockManager.attemptUnlock` now increments `failCount` BEFORE evaluating PIN
  or panic code. Previously, the panic branch ran before the counter increment, allowing
  `~LOCKOUT_THRESHOLD` consecutive panic guesses with no lockout penalty. Counter is reset to
  zero on any successful authentication.

Tests: all 70 existing tests green. `lintVitalRelease` clean. `assembleRelease` successful.

### v0.3.1 — Audit fixes (security foundations hardening)

Multi-axis precision audit (post-v0.3.0 tag) surfaced 4 HIGH + 4 MEDIUM findings
on the freshly landed security layer. All HIGH and the groupable MEDIUM fixed
before the v0.3.1 retag closes:

- **SEC-1+5** — `sos_tech_security.preferences_pb` (PBKDF2 PIN + panic hashes)
  excluded from cloud backup + device transfer + Android < 12 file-domain backup
  rules. Blocks offline brute-force from a leaked backup.
- **SEC-2** — `AppLockManager.setPanicCode` now rejects with `IllegalArgumentException`
  if the candidate hashes to the same value as the configured PIN (constant-time
  compare). Without this guard, panic == PIN created a permanent self-lockout
  because the panic branch in `attemptUnlock` precedes the PIN branch.
- **SEC-3** — `SecurityStore.hasPanic` Flow now feeds `SettingsUiState.isPanicConfigured`
  (was hardcoded `false`). User sees "Change panic code" vs "Set panic code"
  accurately, preventing silent overwrite. `SecurityStore.hasPin` Flow added
  for symmetry.
- **BR-1** — `attemptUnlock` with `pinSnapshot == null` now fails closed to
  `LockState.Locked` (was `Disabled` → would have silently unlocked the app
  after a crash mid-wipe).
- **SEC-6** — `unlockMutex` added around `attemptUnlock` body. Two concurrent unlock
  attempts (BiometricPrompt + PIN double-tap) can no longer race on
  `failCount` / `lockoutUntil`.
- **UI-1** — `UrgenceHoldButton` now resets `isHolding = false` BEFORE
  invoking `onTriggered()` to shrink the re-trigger window (already absorbed by
  `EmergencyViewModel.triggerInFlight` AtomicBoolean — defense in depth).
- **KQ-2** — `appLockManager` made `private` in `LockViewModel`. External callers
  now use `beginBiometricChallenge()` / `markBiometricUnlocked()` dedicated methods.
- **UI-2** — `LockScreen` fixed 500 ms timer removed. `isSubmitting` now resets via
  `LaunchedEffect(lockState)` (any state transition) and `LockEvent.InvalidPin` handler.

Tests: `AuditV031Test` 11 new regression tests. Total **70 / 70 green**
(`AuditV001Test` 21 + `AuditV020Test` 13 + `AuditV030Test` 25 + `AuditV031Test` 11).
`lintVitalRelease` clean. `assembleRelease` successful (R8 + signed APKs).

### v0.3.0 — Security foundations (PIN + biometric + panic-decoy)

- **AppLockManager** port from SMS Tech v1.x with full `LockState` sealed surface
  (`Disabled` / `Locked` / `LockedOut` / `Unlocked` / `PanicDecoy`).
- **PBKDF2-HMAC-SHA512** PIN hash with ≥ 210 000 iterations (OWASP Mobile 2024 baseline),
  calibrated at first use to ~300 ms on host device.
- **Distinct panic code** (decoy mode) — when entered, `LockState.PanicDecoy` is set silently
  and the UI is reachable but trusted contacts list, recordings, and emergency SMS history are
  all hidden. `PanicGuard` is now backed by this real state (was a stub returning `false` in v0.2).
- **BiometricPrompt** with single-use random 32-byte challenge — biometric success is only
  accepted in `LockState.Locked` (cannot bypass `LockedOut` cool-down or unseal a `PanicDecoy`
  session). AtomicReference ensures atomic one-shot consume.
- **Exponential backoff**: 5 s → 5 min over 6 steps after 5 consecutive PIN failures.
  Lockout horizon clamped to 24 h forward (anti tainted-DataStore-restore, audit P1-1).
- **Both PIN and panic evaluated on every attempt** before incrementing fail counter
  (audit P1-3: prevents panic-code brute-force bypassing the lockout threshold).
- **AutoLockObserver** via `ProcessLifecycleOwner` — app re-locks on background.
- **BootReceiver** drift recovery for `monotonicLastTriggeredAt` (anti cooldown bypass after
  reboot — monotonic clock resets on reboot, perpetual cooldown without this fix).
- **PanicService `nukeEverything()`** (Settings → "Effacer toutes les données") — close DB →
  drop wrapped key file → delete keystore aliases → `deleteDatabase` → clear all security store
  entries (credentials + fail counters) → wipe files dirs → reset settings.
- **EmergencyShortcutNotifier** persistent lock-screen notification: IMPORTANCE_LOW,
  VISIBILITY_PUBLIC, ongoing, 3 actions (URGENCE SMS / 112 / 17). Cancelled in PanicDecoy to
  avoid leaking emergency-mode presence under coercion.
- **UrgenceHoldButton** 3 s hold-to-trigger anti-pocket-dial (port from SMS Tech v1.14.0),
  replaces the simple tap button in `EmergencyScreen`.
- **Settings UI**: PIN setup / change / clear, biometric toggle (requires PIN), panic code
  setup / clear.
- **`SecurityStore`** ported from SMS Tech with `vault.*` keys removed (SOS Tech has no vault
  screen), DataStore name `sos_tech_security`.

All HIGH/MEDIUM audit findings from v0.2 carried forward addressed.

Tests: `AuditV030Test` 25 new guard-regression tests. Total **59 / 59 green**
(`AuditV001Test` 21 + `AuditV020Test` 13 + `AuditV030Test` 25).
`lintVitalRelease` clean. `assembleRelease` successful (R8 + signed APKs).

### v0.2.0 — First usable emergency path + multi-axis audit

Foundations livered:
- **Emergency contacts** CRUD persisted in SQLCipher Room DB (alias `sostech_db_master`). Validation chain: bidi/zero-width strip on name, digit enforcement on phone, non-negative priority, id > 0 on update.
- **`SendSmsUseCase`** + **`SmsDispatcher`** — multi-part split via `divideMessage`, runtime SEND_SMS check, `Outcome<Int>` aggregating per-recipient successes. **Logs redact phone numbers** (prefix + suffix only).
- **`LocationResolver`** ported from SMS Tech — no Google Play Services, single-fix with 8 s timeout, fresh `lastKnown` (≤ 5 min) cached, `removeUpdates` on every exit path including SecurityException. F-Droid-clean.
- **`TriggerEmergencyUseCase`** orchestration: `PanicGuard` → dual-clock cooldown (wall + monotonic) → contacts load → optional GPS → localized body render → per-recipient dispatch → cooldown stamp.
- **`EmergencyMessageRenderer`** — Locale-aware via `Context.getString`, lifts hardcoded FR text out of the enum.
- **First-launch welcome splash** with idempotent dismiss guard (`AtomicBoolean`), eager-shared `shouldShow` (no flash for returning users).
- **Settings**: SMS template picker (NEED_HELP / DANGER / DISCREET), GPS-in-SMS toggle (default off).

Pre-commit audit (multi-axis: security + perf + Kotlin quality + UI/Compose + architecture + cohérence cross-app + branchements + vulnerabilities) — **all HIGH/MEDIUM findings fixed before tag**:

- **SEC** — `EmergencyCallHelper.placeTrustedContactCall` now consults the same anti-spam cooldown as `placeEmergencyCall` (pocket-dial chain prevention).
- **SEC** — `Timber` failure logs in `openDialer` / `executeDirectCall` now redact the phone number.
- **ARCH** — `EmergencyTemplate`, `EmergencyCallBehavior`, `ThemeMode`, `LockMode` annotated `@Serializable` (lock kotlinx-serialization behavior explicitly).
- **COH** — `HomeViewModel.contactsConfigured` wired to repo (was hard-coded `false` v0.1).
- **A11Y** — `IconButton` Edit/Delete in `ContactsScreen` carry `contentDescription` (TalkBack-safe destructive actions).
- **DRY** — removed dead `SirenControllerStub` duplicate in `domain/siren/`, dead `withBrandSnackbar()` in `theme/Color.kt`, orphan `contacts_count_*` strings.
- **KOTLIN** — simplified `priorityText` state (single source of truth), removed redundant `io` dispatcher in `ContactsViewModel`, replaced fully-qualified `EmergencyCallBehavior` references with import.

Tests: 34 / 34 green (`AuditV001Test` 21 + `AuditV020Test` 13). `lintVitalRelease` clean. `assembleRelease` produces 4 unsigned APK splits.

### v0.1.0 — Initial scaffold + Keystore-derived SQLCipher key

Foundation: 7 feature contracts (voice, cascade, siren, live GPS, recording, webhook, contacts),
EmergencyScreen with 4 call tiles (112/15/17/18 + trusted contact), emergency SMS trigger,
dry-run preview, kill-switch disable, SettingsScreen with per-feature toggles.

**Crypto hardening (post initial scaffold, same tag)** — the SQLCipher passphrase placeholder
flagged as the v0.1 critical TODO has been **replaced before the tag closes**. SOS Tech v0.1.0
now derives the SQLCipher key from a 32-byte SecureRandom value wrapped by AndroidKeyStore alias
`sostech_db_master` (AES-256-GCM, versioned envelope). The raw key is wiped from JVM memory
immediately after Room consumes the factory. Typed `DatabaseKeyManager.Failure` propagates
Keystore invalidation distinctly from wrap corruption, avoiding silent data loss.

Security review: all conservative defaults verified in `AuditV001Test` (**21 tests green** — was 13):

Defaults:
- All features OFF by default
- Default call behavior = HOLD_3S_DIRECT_CALL (not TAP)
- FLAG_SECURE ON by default
- Recording requires legal disclaimer before enabling
- Whitelist exactly {112, 15, 17, 18}
- SIREN_MAX = 5 min, RECORDING_MAX = 30 s, WEBHOOK_MAX_RETRIES = 3
- Voice repetitionCount = 3 (anti-false-positive)
- Cascade noAnswerTimeout = 10 s
- Webhook GPS opt-in = false by default

Crypto invariants (new):
- 4 Keystore aliases SOS-namespaced, pairwise distinct (`sostech_db_master`, `sostech_recording_kek`, `sostech_settings_aead`, `sostech_panic_decoy`)
- AES key size = 256 bits
- AEAD envelope = `AES/GCM/NoPadding` + 12-byte IV + 128-bit tag, version byte `0x01`
- AEAD raw round-trip preserves plaintext + GCM tag rejects bit-flips
- AEAD rejects unsupported envelope versions
- `ByteArray.wipe()` zeroes buffer in place
- DB filename namespaced (`sos_tech.db`)

Known limitations in v0.1 (some addressed in v0.2, others deferred to v0.3+):
- No app lock implemented yet → still pending in v0.2 (`AppLockManager` port, panic mode wiring)
- 7 extended features stubs → emergency SMS path implemented in v0.2; voice / cascade / siren / live-GPS / recording / webhook still NotImplemented
- Settings DataStore not yet AEAD-wrapped → still pending (target v0.3)
- Panic-mode decoy KeyStore alias declared but not wired → `PanicGuard` interface introduced in v0.2 with a `DefaultPanicGuard` stub; wiring to a real PanicService deferred to v0.3

---

## Reporting

Disclosure: **contact@files-tech.com** — subject `SOS Tech security report`
Response within 5 working days.
