package com.filestech.sos.core.ext

private val DANGEROUS_BIDI_CHARS = setOf(
    '‪', // LEFT-TO-RIGHT EMBEDDING
    '‫', // RIGHT-TO-LEFT EMBEDDING
    '‬', // POP DIRECTIONAL FORMATTING
    '‭', // LEFT-TO-RIGHT OVERRIDE
    '‮', // RIGHT-TO-LEFT OVERRIDE
    '⁦', // LEFT-TO-RIGHT ISOLATE
    '⁧', // RIGHT-TO-LEFT ISOLATE
    '⁨', // FIRST STRONG ISOLATE
    '⁩', // POP DIRECTIONAL ISOLATE
)

/** Returns true if the string contains no Unicode bidirectional control characters (anti-spoofing). */
fun String.isSafeBidiText(): Boolean = none { it in DANGEROUS_BIDI_CHARS }

/** Strip invisible bidi chars and trim. */
fun String.stripUnsafe(): String = filter { it !in DANGEROUS_BIDI_CHARS }.trim()

/**
 * Telephony-friendly normalization: keeps a leading '+', digits, and '*#'. Strips spaces,
 * dashes, parentheses, and unicode bidi. Does NOT enforce country format (intentional — SOS Tech
 * does not bundle libphonenumber to stay FLOSS-lean for F-Droid).
 */
fun String.normalizePhone(): String {
    val sb = StringBuilder(length)
    for ((i, c) in this.withIndex()) {
        when {
            c.isDigit() -> sb.append(c)
            c == '+' && i == 0 -> sb.append(c)
            c == '*' || c == '#' -> sb.append(c)
            else -> Unit
        }
    }
    return sb.toString()
}

/**
 * Redact a phone number for log lines: keeps country prefix (if any) and the last 2 digits.
 * `+33612345678` → `+33******78`, `0612345678` → `08******78`. Best-effort, never leaks the
 * full subscriber number to Timber. Use ONLY for logs; user-facing UI shows the raw form.
 */
fun String.redactPhone(): String {
    val n = this.normalizePhone()
    if (n.length < 4) return "***"
    val head = if (n.startsWith('+')) n.take(3) else n.take(2)
    val tail = n.takeLast(2)
    val starsCount = (n.length - head.length - tail.length).coerceAtLeast(2)
    return head + "*".repeat(starsCount) + tail
}
