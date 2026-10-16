package org.qstuff.qplayer.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Android [CrashReporter] via Firebase Crashlytics. Automatic collection is off in the manifest
 * (`firebase_crashlytics_collection_enabled`); the user's choice is applied at every app start and
 * whenever it changes.
 */
class FirebaseCrashReporter : CrashReporter {

    override fun setEnabled(enabled: Boolean) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Crashes recorded while reporting was off are never sent.
        if (!enabled) crashlytics.deleteUnsentReports()
    }
}
