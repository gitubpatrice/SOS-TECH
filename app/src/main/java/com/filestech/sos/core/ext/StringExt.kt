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
