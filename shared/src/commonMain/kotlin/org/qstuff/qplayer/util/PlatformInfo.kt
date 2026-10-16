package org.qstuff.qplayer.util

/** Facts about the app build and the device, shown in Settings — platform independent. */
interface PlatformInfo {
    /** "qdeq 3.0.0 (532)", or "qdeq-dev …" in debug builds. */
    val appVersion: String

    /** Manufacturer, model, CPU. */
    val deviceInfo: String
}
