package com.filestech.sos.core.logging

import timber.log.Timber

/**
 * Debug tree which prefixes log lines with file:line for fast jumps in the IDE.
 */
class LineNumberDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String =
        "SosTech::${element.fileName}:${element.lineNumber}"
}

/**
 * Release tree: drops everything except WARN/ERROR.
 * The app must NEVER log PII, phone numbers, GPS coordinates, or recording paths in release.
 */
class NoOpReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= android.util.Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Intentionally no-op: avoid leaking anything in logcat in release builds.
    }
}
