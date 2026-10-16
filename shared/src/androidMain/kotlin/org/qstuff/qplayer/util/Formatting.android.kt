package org.qstuff.qplayer.util

import java.text.DecimalFormatSymbols

internal actual fun localeDecimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator
