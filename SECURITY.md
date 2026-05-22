# SOS Tech — Security model

Current release : **v0.1.0** (2026-05-22)

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
| App-lock PIN | PBKDF2-HMAC-SHA512 | User secret + 16-byte random salt, ≥ 210k iterations | TODO v0.2 (same pattern as SMS Tech `AppLockManager`) |
| Settings DataStore | Plain JSON (no AEAD) | — | TODO v0.2: wrap sensitive prefs under Keystore alias `sostech_settings_aead` |
| Panic-mode decoy | AES-256-GCM | Keystore alias `sostech_panic_decoy` (separate from `sostech_db_master`) | TODO v0.2 (port from SMS Tech `PanicService`) |

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

### v0.1.0 (this release) — Initial scaffold + Keystore-derived SQLCipher key

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

Known limitations in v0.1:
- No app lock implemented yet (v0.2)
- All 7 extended features are stubs (contracts + UI screens ready, no implementation)
- Settings DataStore not yet AEAD-wrapped (v0.2)
- Panic-mode decoy KeyStore alias declared but not wired (v0.2)

---

## Reporting

Disclosure: **contact@files-tech.com** — subject `SOS Tech security report`
Response within 5 working days.
