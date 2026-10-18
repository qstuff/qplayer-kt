package org.qstuff.qplayer.util

/** Facts about the app build and the device, shown in Settings — platform independent. */
interface PlatformInfo {
    /** "QdeQ 4.0.0 (532)", or "QdeQ-dev …" in debug builds. */
    val appVersion: String

    /** Manufacturer, model, CPU. */
    val deviceInfo: String
}
