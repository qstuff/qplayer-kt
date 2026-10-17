package org.qstuff.qplayer.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val TOKEN = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>|([^<]+)")
private val ATTRIBUTE = Regex("""([a-zA-Z-]+)\s*=\s*"([^"]*)"""")
private val ENTITY = Regex("&(#?[a-zA-Z0-9]+);")
private val BLOCKS = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6")
private val HEADING_SIZES = mapOf("h1" to 1.5f, "h2" to 1.4f, "h3" to 1.3f, "h4" to 1.2f, "h5" to 1.1f, "h6" to 1f)

/**
 * Converts the simple HTML of the privacy / imprint pages into an [AnnotatedString], like
 * Android's AnnotatedString.fromHtml (which isn't multiplatform): headings h1–h6 (bold, larger),
 * p / div (separated by an empty line), br, b / strong, i / em, font color="#rrggbb" and links
 * (a href, styled with [linkStyles]). Whitespace collapses as in a browser; comments are dropped,
 * unknown tags ignored (their text is kept).
 */
fun simpleHtmlToAnnotatedString(html: String, linkStyles: TextLinkStyles? = null): AnnotatedString =
    buildAnnotatedString {
        val open = ArrayDeque<Pair<String, Boolean>>()   // tag, whether it pushed a style
        var pendingBreaks = 0
        var pendingSpace = false
        var atLineStart = true

        // Breaks and spaces are only written in front of following content, so the text has no
        // leading/trailing empty lines and styles (e.g. a link) don't start with them.
        fun flushPending() {
            if (pendingBreaks > 0 && length > 0) append("\n".repeat(pendingBreaks))
            else if (pendingSpace && !atLineStart) append(' ')
            pendingBreaks = 0
            pendingSpace = false
        }

        fun lineBreaks(count: Int) {
            pendingBreaks = maxOf(pendingBreaks, count)
            pendingSpace = false
            atLineStart = true
        }

        for (token in TOKEN.findAll(COMMENT.replace(html, ""))) {
            val text = token.groups[4]?.value
            if (text != null) {
                for (c in decodeEntities(text)) {
                    if (c == ' ' || c == '\n' || c == '\t' || c == '\r') {
                        pendingSpace = true
                    } else {
                        flushPending()
                        append(c)
                        atLineStart = false
                    }
                }
                continue
            }

            val closing = token.groupValues[1] == "/"
            val tag = token.groupValues[2].lowercase()
            val attributes = ATTRIBUTE.findAll(token.groupValues[3])
                .associate { it.groupValues[1].lowercase() to it.groupValues[2] }

            when {
                tag == "br" -> {
                    if (pendingBreaks > 0) flushPending()
                    append('\n')
                    pendingSpace = false
                    atLineStart = true
                }
                closing -> {
                    if (tag in BLOCKS) lineBreaks(2)
                    // Close the tag (and any unclosed ones inside it).
                    if (open.any { it.first == tag }) {
                        while (true) {
                            val (openTag, pushed) = open.removeLast()
                            if (pushed) pop()
                            if (openTag == tag) break
                        }
                    }
                }
                else -> {
                    if (tag in BLOCKS) lineBreaks(2) else flushPending()
                    var pushed = true
                    when (tag) {
                        in HEADING_SIZES -> pushStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontSize = HEADING_SIZES.getValue(tag).em)
                        )
                        "b", "strong" -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        "i", "em" -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        "font" -> parseColor(attributes["color"])
                            ?.let { pushStyle(SpanStyle(color = it)) } ?: run { pushed = false }
                        "a" -> attributes["href"]
                            ?.let { pushLink(LinkAnnotation.Url(it, linkStyles)) } ?: run { pushed = false }
                        else -> pushed = false
                    }
                    open.addLast(tag to pushed)
                }
            }
        }
    }

private fun parseColor(value: String?): Color? {
    val hex = value?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
    val name = match.groupValues[1]
    when {
        name == "amp" -> "&"
        name == "lt" -> "<"
        name == "gt" -> ">"
        name == "quot" -> "\""
        name == "apos" -> "'"
        name == "nbsp" -> " "
        name.startsWith("#x") || name.startsWith("#X") ->
            name.drop(2).toIntOrNull(16)?.let { Char(it).toString() } ?: match.value
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let { Char(it).toString() } ?: match.value
        else -> match.value
    }
}
