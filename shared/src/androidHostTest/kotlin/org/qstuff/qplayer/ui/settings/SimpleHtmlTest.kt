package org.qstuff.qplayer.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class SimpleHtmlTest {

    @Test
    fun `blocks, line breaks, whitespace and entities`() {
        val text = simpleHtmlToAnnotatedString(
            """
            <!-- editor note -->
            <h2>Title</h2>
            <p>
                QStuff is:<br>
                Berlin
            </p>
            <p>Coding &amp; UX, &quot;AS IS&quot;</p>
            """
        )

        assertEquals("Title\n\nQStuff is:\nBerlin\n\nCoding & UX, \"AS IS\"", text.text)
    }

    @Test
    fun `styles and links cover exactly their text`() {
        val text = simpleHtmlToAnnotatedString(
            """<h2><font color="#FC7614">Imprint</font></h2><p><b>Consent:</b> see <a href="mailto:a@b.org">contact</a>.</p>"""
        )

        assertEquals("Imprint\n\nConsent: see contact.", text.text)
        val styles = text.spanStyles.map { text.text.substring(it.start, it.end) to it.item }
        assertEquals(FontWeight.Bold, styles.single { it.first == "Imprint" && it.second.fontWeight != null }.second.fontWeight)
        assertEquals(Color(0xFFFC7614), styles.single { it.second.color != Color.Unspecified }.second.color)
        assertEquals("Consent:", styles.single { it.first == "Consent:" }.first)
        val link = text.getLinkAnnotations(0, text.length).single()
        assertEquals("contact", text.text.substring(link.start, link.end))
        assertEquals("mailto:a@b.org", (link.item as LinkAnnotation.Url).url)
    }
}
