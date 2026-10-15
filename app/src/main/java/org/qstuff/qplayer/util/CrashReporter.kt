package org.qstuff.qplayer.util

/**
 * Crash reporting — platform independent; opt-in (asked once, changeable in Settings). Android:
 * FirebaseCrashReporter (Crashlytics).
 */
interface CrashReporter {
    /** Apply the user's choice: send crash reports or not (unsent ones are dropped when off). */
    fun setEnabled(enabled: Boolean)
}
