package com.filestech.sos.domain.emergency

import android.content.Context
import com.filestech.sos.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders an [EmergencyTemplate] into a localized SMS body. Centralized here (rather than
 * inline in the use case) so:
 *  - The domain layer never has to depend on `Context` directly.
 *  - Tests can swap in a stub renderer to avoid Robolectric resource overhead.
 *  - The locale strategy is single-sourced: the active app locale at trigger time decides the
 *    body language — and that aligns with what the recipient's phone displays for the rendered
 *    text (the user sees one language in the app, they expect the SMS in the same language).
 *
 * `Context.getString` reads the string from the active resource configuration, which respects
 * the user's per-app language override (Android 13 `LocaleManager`) AND the device system locale
 * as fallback.
 */
@Singleton
class EmergencyMessageRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * @param template selected template enum
     * @param locationUrl fully-formed Maps URL or null. Null collapses to the localized fallback
     *                    "(location not available)" / "(position non disponible)".
     */
    fun render(template: EmergencyTemplate, locationUrl: String?): String {
        val locOrFallback = locationUrl?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.emergency_template_location_fallback)
        return context.getString(template.bodyTemplateRes, locOrFallback)
    }
}
