package org.qstuff.qplayer.util

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

import timber.log.Timber

class TimberCrashReportingTree(private val mTag: String) : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        var message = message

        val prefix = createPrefix()
        message = String.format(Locale.ENGLISH, "%s   %s", prefix, message)

        super.log(priority, mTag, message, t)
    }

    companion object {

        private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")

        private fun createPrefix(): String {

            val stackTrace = Throwable().stackTrace
            if (stackTrace.size < 7) {
                throw IllegalStateException(
                        "Synthetic stacktrace didn't have enough elements: are you using proguard?")
            }

            var className = stackTrace[6].className
            val m = ANONYMOUS_CLASS.matcher(className)
            if (m.find()) {
                className = m.replaceAll("")
            }
            className = className.substring(className.lastIndexOf('.') + 1)

            val fileName = stackTrace[6].fileName
            val methodName = stackTrace[6].methodName
            val lineNumber = stackTrace[6].lineNumber

            return String.format(Locale.ENGLISH, "%s.%s(%s:%,d)",
                    className, methodName, fileName, lineNumber)
        }
    }
}
