# SOS Tech &nbsp; [`github.com/gitubpatrice/SOS-TECH`](https://github.com/gitubpatrice/SOS-TECH)

A dedicated personal-safety app for Android — built with Kotlin, Jetpack Compose and
Material 3. No cloud, no telemetry, no account. Apache-2.0 licensed. F-Droid friendly.

> **Status**: v0.2.0 — first usable emergency path (SMS trigger live; cascade / voice / siren / live GPS / recording / webhook are scaffolded contracts, full implementation in v0.3+).

SOS Tech is the dedicated companion to [SMS Tech](https://github.com/gitubpatrice/SMS-TECH).
SMS Tech keeps its emergency mode minimal (4 buttons + URGENCE SMS hold-3s) so 95 % of
users carry no useless weight. The extended emergency features (continuous voice keyword
detection, automatic call cascade, siren + flash strobe, live GPS sharing, ambient recording,
webhook diffusion) live here, opt-in per feature.

## ✨ What's working today (v0.2.0)

- **Emergency contacts CRUD** — stored locally in a SQLCipher-encrypted Room database
  (AES-256-GCM passphrase wrapped by AndroidKeyStore alias `sostech_db_master`).
- **Emergency SMS trigger** — dual-clock anti-spam cooldown (wall + monotonic), single-flight
  guard, per-recipient dispatch, locale-aware body (FR/EN), optional GPS location.
- **Direct emergency calls** — 112 / 15 (SAMU) / 17 (Police) / 18 (Fire) with strict whitelist,
  three behaviour modes (DIALER_ONLY / HOLD_3S_DIRECT_CALL / TAP_DIRECT_CALL).
- **Trusted-contact call** — separate code path, same cooldown, pocket-dial chain protection.
- **First-launch welcome splash** (5 s, skippable, idempotent dismiss).
- **Settings**: SMS template picker (NEED_HELP / DANGER / DISCREET), GPS-in-SMS opt-in,
  per-feature ON/OFF toggles (features themselves are stubs until v0.3+).
- **FR + EN UI** — full parity, vouvoiement on the French side.
- **34 garde-régression tests** locking down conservative defaults and crypto invariants.

## 🚧 What's scaffolded but not yet implemented (v0.3+)

| Feature | Contract posed | Real impl |
|---|---|---|
| Voice keyword spotting (Vosk, no cloud) | ✓ | v0.3 |
| Automatic call cascade | ✓ | v0.3 |
| Siren + rear-flash strobe | ✓ | v0.3 |
| Live GPS sharing | ✓ | v0.3 |
| Ambient recording (with FR legal disclaimer) | ✓ | v0.3 |
| Webhook diffusion (HTTP POST) | ✓ | v0.3 |
| App-lock PIN + biometric + panic-decoy | ✓ | v0.3 |

## 📦 Build

```bash
./gradlew assembleDebug         # → app/build/outputs/apk/debug/*.apk
./gradlew assembleRelease       # → app/build/outputs/apk/release/*-unsigned.apk
./gradlew testReleaseUnitTest   # → 34 tests, all green
./gradlew lintVitalRelease      # → clean
```

Requires JDK 17. Min SDK 26 (Android 8.0). Compile / Target SDK 35.

## 🏗️ Architecture

```
core/      Outcome<T>, AppError, crypto (AES-GCM + Keystore + wipe), Timber wrapper
data/      Room (entities, DAOs), DataStore (settings), SmsDispatcher, LocationResolver, repositories
domain/    Immutable models, repository interfaces, EmergencyTemplate, PanicGuard, UseCases
di/        Hilt modules + qualifiers (@IoDispatcher, @ApplicationScope)
security/  EmergencyCallHelper (whitelist + single-flight + dual-clock cooldown)
system/    Foreground services (Voice, Cascade), WorkManager, BootReceiver
ui/        Theme (M3, BrandBlue + BrandDanger + BrandEmergency), Navigation, Compose screens, ViewModels
```

The emergency SMS path end-to-end:
```
EmergencyScreen URGENCE button
    → EmergencyViewModel.triggerEmergencySms() — single-flight AtomicBoolean
    → TriggerEmergencyUseCase()
        → PanicGuard.isPanicActive()            // silent suppress if duress
        → settings.emergency.{lastTriggeredAt, monotonicLastTriggeredAt}  // dual-clock cooldown
        → EmergencyContactRepository.getAll()
        → LocationResolver.getCurrentLocation() // F-Droid clean, no Google Play Services
        → EmergencyMessageRenderer.render()     // locale-aware body
        → SendSmsUseCase(recipients, body)
            → SmsDispatcher.dispatch() per recipient
                → SmsManager.divideMessage + send{Multipart}TextMessage
        → settings.update { stamp cooldown }
    → Channel<EmergencyEvent> → snackbar
```

## 🔐 Privacy &amp; Security

We collect **nothing**. No analytics, no crash reporting, no remote logging.
The only network call SOS Tech can make is the webhook diffusion to **your own configured URL**
(opt-in, default off, minimal `{trigger, ts, lat?, lng?}` payload — no contact names, no SMS body).

Local data is protected by SQLCipher with a Keystore-wrapped passphrase. Phone numbers are
**always redacted in logs** (prefix + suffix only). Locations are never persisted; they are
resolved on demand and dropped after the SMS is sent.

Recording feature carries a mandatory French legal disclaimer (art. 226-15 Code pénal —
consent of all parties required) shown before activation can be persisted.

See [SECURITY.md](SECURITY.md) for the full threat model.

## 🔗 Files Tech portfolio

| App | Role | Repo |
|---|---|---|
| **SMS Tech** | Default SMS/MMS — minimal emergency | [`gitubpatrice/SMS-TECH`](https://github.com/gitubpatrice/SMS-TECH) |
| **SOS Tech** | Dedicated personal safety — extended emergency (this repo) | — |
| **Pass Tech** | Encrypted password vault | [`gitubpatrice/PASS-TECH`](https://github.com/gitubpatrice/PASS-TECH) |
| **Notes Tech** | Encrypted notes | [`gitubpatrice/NOTES-TECH`](https://github.com/gitubpatrice/NOTES-TECH) |
| **AI Tech** | Local Gemma chat | [`gitubpatrice/AI-TECH`](https://github.com/gitubpatrice/AI-TECH) |
| **PDF Tech** | 23 PDF tools | [`gitubpatrice/PDF-TECH`](https://github.com/gitubpatrice/PDF-TECH) |
| **Read Files Tech** | OCR | [`gitubpatrice/READ-FILES-TECH`](https://github.com/gitubpatrice/READ-FILES-TECH) |
| **Health Tech** | Calendar / SQLCipher | [`gitubpatrice/health_tech`](https://github.com/gitubpatrice/health_tech) |

Portfolio site: [files-tech.com](https://files-tech.com).

## 📃 License

Apache License 2.0 — see [LICENSE](LICENSE).
© 2026 Patrice Haltaya.
