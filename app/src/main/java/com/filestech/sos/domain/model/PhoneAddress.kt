package com.filestech.sos.domain.model

import com.filestech.sos.core.ext.normalizePhone

/**
 * A normalized phone address. [raw] is kept verbatim for display / SmsManager dispatch;
 * [normalized] strips spaces/dashes/parentheses for matching against contact records.
 *
 * Alphanumeric senders ("Free", "Orange", "INFO"…) collapse to empty under digit-only
 * normalization — we fall back to the trimmed raw form so contact lookups still find them.
 */
@JvmInline
value class PhoneAddress private constructor(private val pair: Pair<String, String>) {

    val raw: String get() = pair.first
    val normalized: String get() = pair.second

    /** True if this address contains at least one digit and could plausibly route an SMS. */
    val isValidDispatchTarget: Boolean
        get() = normalized.any { it.isDigit() }

    override fun toString(): String = raw

    companion object {
        fun of(raw: String): PhoneAddress {
            val trimmed = raw.trim()
            val normalized = trimmed.normalizePhone().ifEmpty { trimmed }
            return PhoneAddress(trimmed to normalized)
        }
    }
}
