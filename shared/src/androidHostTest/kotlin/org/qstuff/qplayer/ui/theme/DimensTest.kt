package org.qstuff.qplayer.ui.theme

import org.junit.Assert.assertSame
import org.junit.Test

class DimensTest {

    @Test
    fun `sizes follow the former sw600dp and sw720dp resource buckets`() {
        assertSame(Dimens.Phone, Dimens.forSmallestWidth(411))
        assertSame(Dimens.Phone, Dimens.forSmallestWidth(599))
        assertSame(Dimens.Tablet, Dimens.forSmallestWidth(600))
        assertSame(Dimens.Tablet, Dimens.forSmallestWidth(719))
        assertSame(Dimens.LargeTablet, Dimens.forSmallestWidth(720))
        assertSame(Dimens.LargeTablet, Dimens.forSmallestWidth(800))   // Galaxy Tab A 10.1
    }
}
