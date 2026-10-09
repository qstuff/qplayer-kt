package org.qstuff.qplayer.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash reporting is opt-in: automatic collection is off in the manifest
 * (`firebase_crashlytics_collection_enabled`), and the user's choice (opt-in dialog / Settings)
 * is applied here at every app start and whenever it changes.
 */
object CrashReporting {

    fun apply(enabled: Boolean) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Crashes recorded while reporting was off are never sent.
        if (!enabled) crashlytics.deleteUnsentReports()
    }
}
