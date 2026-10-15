package org.qstuff.qplayer.util

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.decimalSeparator

internal actual fun localeDecimalSeparator(): Char =
    NSLocale.currentLocale.decimalSeparator.firstOrNull() ?: '.'
