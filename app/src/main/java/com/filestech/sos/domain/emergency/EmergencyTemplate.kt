package com.filestech.sos.domain.emergency

import androidx.annotation.StringRes
import com.filestech.sos.R
import kotlinx.serialization.Serializable

/**
 * Marker enum identifying which emergency SMS template the user has selected.
 *
 * Body strings are localized via Android string resources (FR, EN, ES, IT). Rendering is
 * delegated to [EmergencyMessageRenderer] which has access to `Context.getString`. This
 * keeps the domain layer free of Android `Context` dependency while still supporting per-locale
 * wording.
 *
 * Template wording is designed to stay within GSM-7 default alphabet whenever the locale allows
 * (French/Italian: à, è, é, ì, ò, ù are GSM-7 native; Spanish acute accents á í ó ú are NOT —
 * the Spanish template uses ASCII-only forms to keep the alert in a single SMS segment under
 * weak radio conditions).
 */
@Serializable
enum class EmergencyTemplate(
    @StringRes val bodyTemplateRes: Int,
    @StringRes val labelRes: Int,
) {
    NEED_HELP(
        bodyTemplateRes = R.string.emergency_template_need_help_body,
        labelRes = R.string.settings_emergency_template_need_help,
    ),
    DANGER(
        bodyTemplateRes = R.string.emergency_template_danger_body,
        labelRes = R.string.settings_emergency_template_danger,
    ),
    DISCREET(
        bodyTemplateRes = R.string.emergency_template_discreet_body,
        labelRes = R.string.settings_emergency_template_discreet,
    ),
}
