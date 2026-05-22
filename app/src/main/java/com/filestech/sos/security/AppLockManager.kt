package com.filestech.sos.security

import com.filestech.sos.core.crypto.PasswordKdf
import com.filestech.sos.core.crypto.wipe
import com.filestech.sos.data.local.datastore.LockMode
import com.filestech.sos.data.local.datastore.SecurityStore
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lock/unlock state of the app. Stores only salted PBKDF2-SHA512 hashes; never the PIN.
 *
 * Port from SMS Tech `AppLockManager` with package rename only. No logic change.
 *
 * Lockout policy: monotonic exponential backoff after a streak of failures.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val securityStore: SecurityStore,
    private val settings: SettingsRepository,
    private val kdf: PasswordKdf,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * Initial state is [LockState.Locked] (fail-closed). Any subsequent observer must wait for
     * [resolveInitialState] to flip to [LockState.Disabled] if the user has disabled the lock.
     * This closes the cold-start window: NavHost cannot show app screens before settings are
     * loaded from DataStore.
     */
    private val _state = MutableStateFlow<LockState>(LockState.Locked)
    val state: StateFlow<LockState> = _state.asStateFlow()

    sealed interface LockState {
        /** Lock is configured OFF in settings — UI is always visible. */
        data object Disabled : LockState
        /** Lock is configured ON and user has not unlocked yet. UI must be hidden. */
        data object Locked : LockState
        /** Too many failed attempts — UI shows the countdown, no PIN accepted yet. */
        data class LockedOut(val until: Long) : LockState
        /** User has unlocked successfully. */
        data object Unlocked : LockState
        /** Panic-code unlock — UI is visible but all sensitive features must remain hidden. */
        data object PanicDecoy : LockState
    }

    /** True iff the UI should be reachable (real unlock OR decoy OR lock disabled). */
    fun isOpenForUi(state: LockState): Boolean = when (state) {
        LockState.Unlocked, LockState.PanicDecoy, LockState.Disabled -> true
        LockState.Locked -> false
        is LockState.LockedOut -> false
    }

    /**
     * Latched once [resolveInitialState] has flipped `_state` away from the fail-closed default.
     * Idempotent across cold-start contention: Application.onCreate may kick it off asynchronously
     * while a broadcast receiver fired in the same process may need to wait for the result.
     */
    private val resolvedLatch = AtomicBoolean(false)
    private val resolveMutex = Mutex()

    /**
     * SEC-6: Serialises [attemptUnlock] calls. Without this, two concurrent coroutines
     * (BiometricPrompt success callback + PIN double-tap) could race on `failCount` /
     * `lockoutUntil` reads and writes, potentially under-counting failures.
     */
    private val unlockMutex = Mutex()

    suspend fun resolveInitialState(): LockState = withContext(io) {
        val s = settings.flow.first()
        val resolved = if (s.security.lockMode == LockMode.OFF) LockState.Disabled else LockState.Locked
        _state.value = resolved
        resolvedLatch.set(true)
        resolved
    }

    /**
     * Idempotent variant of [resolveInitialState]. Safe to call concurrently from multiple
     * coroutines — only the first one actually queries DataStore; subsequent callers return
     * immediately once the latch is set.
     */
    suspend fun ensureResolved() {
        if (resolvedLatch.get()) return
        resolveMutex.withLock {
            if (resolvedLatch.get()) return@withLock
            resolveInitialState()
        }
    }

    /**
     * Sets the user's PIN. The [newPin] CharArray is wiped on exit. NEVER round-trips through
     * `toByteArray(UTF-8).toCharArray()` — that would corrupt entropy for non-ASCII passwords.
     * PBKDF2-HMAC-SHA512 handles the UTF-8 encoding of CharArray internally.
     */
    suspend fun setPin(newPin: CharArray): Unit = withContext(io) {
        val salt = kdf.newSalt()
        val iters = kdf.calibrate()
        try {
            val hash = kdf.derive(newPin, salt, iters)
            securityStore.setPinHash(salt, hash, iters)
        } finally {
            newPin.wipe()
        }
        settings.update { it.copy(security = it.security.copy(lockMode = LockMode.PIN)) }
        _state.value = LockState.Locked
    }

    suspend fun clearPin() = withContext(io) {
        securityStore.clearPin()
        settings.update { it.copy(security = it.security.copy(lockMode = LockMode.OFF)) }
        _state.value = LockState.Disabled
    }

    /**
     * Promotes the lock mode to [LockMode.BIOMETRIC] on top of an existing PIN. The PIN is
     * kept as the fallback: if biometric becomes unavailable the user can still unlock with PIN.
     * Refuses when no PIN is set to avoid locking the user out on fingerprint re-enrollment.
     */
    suspend fun enableBiometric(): Boolean = withContext(io) {
        if (securityStore.pinSnapshot() == null) return@withContext false
        settings.update { it.copy(security = it.security.copy(lockMode = LockMode.BIOMETRIC)) }
        true
    }

    /**
     * Drops back from BIOMETRIC to PIN (keeps the PIN). Atomic read-modify-write via
     * settings.update — no read-then-update race window.
     */
    suspend fun disableBiometric() = withContext(io) {
        settings.update { current ->
            if (current.security.lockMode == LockMode.BIOMETRIC) {
                current.copy(security = current.security.copy(lockMode = LockMode.PIN))
            } else {
                current
            }
        }
    }

    /**
     * Sets a panic code (decoy). When this code is entered at lock screen, the app enters
     * [LockState.PanicDecoy] — UI is visible but PanicGuard blocks all side-effects.
     *
     * SEC-2: Rejects the candidate if it hashes to the same value as the configured primary PIN.
     * Without this guard, panic == PIN creates a permanent self-lockout: the panic branch in
     * [attemptUnlock] precedes the PIN branch, so entering the real PIN would silently open
     * PanicDecoy and the user could never reach [LockState.Unlocked] again.
     *
     * @throws IllegalArgumentException with message "panic_same_as_pin" if candidate matches PIN.
     */
    suspend fun setPanicCode(panicPin: CharArray): Unit = withContext(io) {
        // SEC-2: compare against existing PIN hash (constant-time) before storing.
        val existing = securityStore.pinSnapshot()
        if (existing != null) {
            val testHash = kdf.derive(panicPin, existing.salt, existing.iterations)
            val sameAsPin = try {
                constantTimeEquals(testHash, existing.hash)
            } finally {
                testHash.wipe()
            }
            if (sameAsPin) {
                panicPin.wipe()
                throw IllegalArgumentException("panic_same_as_pin")
            }
        }
        val salt = kdf.newSalt()
        val iters = kdf.calibrate()
        try {
            val hash = kdf.derive(panicPin, salt, iters)
            securityStore.setPanicCode(salt, hash, iters)
        } finally {
            panicPin.wipe()
        }
    }

    suspend fun clearPanicCode() = withContext(io) {
        securityStore.clearPanic()
    }

    suspend fun attemptUnlock(candidate: CharArray): LockState = withContext(io) {
        // SEC-6: serialise concurrent unlock attempts (BiometricPrompt + PIN double-tap race).
        unlockMutex.withLock {
            val now = System.currentTimeMillis()
            val lockoutUntil = securityStore.lockoutUntil.first()
            if (lockoutUntil > now) {
                return@withLock LockState.LockedOut(lockoutUntil).also { _state.value = it }
            }

            // SEC-4 (v0.3.2): increment failCount BEFORE evaluating PIN or panic. This closes a
            // brute-force path where an attacker could probe the panic code indefinitely — the panic
            // branch previously ran before the counter increment, allowing ~LOCKOUT_THRESHOLD
            // consecutive panic guesses with no lockout penalty.
            // Counter is reset to 0 on any successful authentication (PIN or panic).
            val currentFail = securityStore.failCount.first()
            val newFail = (currentFail + 1).coerceAtMost(MAX_FAIL_TRACKED)
            securityStore.setFailCount(newFail)

            // Evaluate panic first (panic takes precedence over PIN per design).
            // Audit P1-3: both snapshots evaluated; failure counter already incremented above.
            val panicSnap = securityStore.panicSnapshot()
            val panicMatches = panicSnap != null &&
                matches(candidate, panicSnap.salt, panicSnap.hash, panicSnap.iterations)
            if (panicMatches) {
                securityStore.setFailCount(0)
                securityStore.setLastUnlock(now)
                _state.value = LockState.PanicDecoy
                return@withLock LockState.PanicDecoy
            }

            val snap = securityStore.pinSnapshot()
                ?: run {
                    // BR-1: pinSnapshot null but lockMode != OFF means an inconsistent state
                    // (mid-wipe crash, tainted DataStore restore). Fail-closed to Locked rather
                    // than silently unlocking the app by falling through to Disabled.
                    // Counter remains at newFail (already persisted above).
                    if (newFail >= LOCKOUT_THRESHOLD) {
                        val delayMs = backoffMillis(newFail - LOCKOUT_THRESHOLD)
                        val until = now + delayMs
                        securityStore.setLockoutUntil(until)
                        _state.value = LockState.LockedOut(until)
                        return@withLock LockState.LockedOut(until)
                    }
                    _state.value = LockState.Locked
                    return@withLock LockState.Locked
                }

            if (matches(candidate, snap.salt, snap.hash, snap.iterations)) {
                securityStore.setFailCount(0)
                securityStore.setLockoutUntil(0L)
                securityStore.setLastUnlock(now)
                _state.value = LockState.Unlocked
                LockState.Unlocked
            } else {
                // Counter already set to newFail above. Evaluate lockout.
                if (newFail >= LOCKOUT_THRESHOLD) {
                    val delayMs = backoffMillis(newFail - LOCKOUT_THRESHOLD)
                    val until = now + delayMs
                    securityStore.setLockoutUntil(until)
                    _state.value = LockState.LockedOut(until)
                    LockState.LockedOut(until)
                } else {
                    _state.value = LockState.Locked
                    LockState.Locked
                }
            }
        }
    }

    /**
     * Forces the app back to its locked state. Idempotent on [LockState.Disabled].
     * PanicDecoy is also re-locked.
     */
    fun forceLock() {
        val current = _state.value
        if (current != LockState.Disabled) _state.value = LockState.Locked
    }

    // -------- Biometric handshake -----------------------------------------------------------------
    // One-shot, single-use challenge: beginBiometricChallenge issues the token, passed to
    // BiometricPrompt's success callback, verified by markBiometricUnlocked. Without a valid live
    // challenge, markBiometricUnlocked is a no-op — safe against regression / hostile call paths.
    //
    // AtomicReference ensures the swap-and-return pair is atomic: two concurrent begin/mark calls
    // cannot race (getAndSet(null) makes the consume both atomic and single-use).
    private val biometricChallenge = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
    private val biometricRng = java.security.SecureRandom()

    fun beginBiometricChallenge(): ByteArray {
        val token = ByteArray(BIO_CHALLENGE_BYTES).also(biometricRng::nextBytes)
        biometricChallenge.set(token)
        return token.copyOf()
    }

    /**
     * Promotes the session to [LockState.Unlocked] ONLY when current state is [LockState.Locked].
     * Refuses in [LockState.LockedOut] (biometric must not bypass exponential cool-down),
     * [LockState.PanicDecoy] (biometric from a panic session must never unseal it),
     * [LockState.Unlocked] and [LockState.Disabled] (no-op).
     */
    fun markBiometricUnlocked(challenge: ByteArray) {
        val expected = biometricChallenge.getAndSet(null) ?: return
        if (!java.security.MessageDigest.isEqual(expected, challenge)) return
        if (_state.value !is LockState.Locked) return
        _state.value = LockState.Unlocked
    }

    private fun matches(candidate: CharArray, salt: ByteArray, expected: ByteArray, iters: Int): Boolean {
        val derived = kdf.derive(candidate, salt, iters)
        return try {
            constantTimeEquals(derived, expected)
        } finally {
            derived.wipe()
        }
    }

    companion object {
        const val LOCKOUT_THRESHOLD = 5
        const val MAX_FAIL_TRACKED = 100
        const val BIO_CHALLENGE_BYTES = 32

        /**
         * Backoff after [LOCKOUT_THRESHOLD] failures. Starts at 5 s — long enough to make 4-digit
         * PIN brute-force costly without being punitive on a typo. Caps at 5 minutes.
         */
        private val BACKOFF_STEPS_MS = longArrayOf(
            5_000, 10_000, 30_000, 60_000, 120_000, 300_000,
        )

        fun backoffMillis(stepIndex: Int): Long =
            BACKOFF_STEPS_MS[stepIndex.coerceIn(0, BACKOFF_STEPS_MS.size - 1)]

        fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var r = 0
            for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
            return r == 0
        }
    }
}
