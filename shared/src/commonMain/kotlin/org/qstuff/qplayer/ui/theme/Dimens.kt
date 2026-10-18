package org.qstuff.qplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizes of the player screen. Larger tablets get larger values, chosen by the screen's smallest
 * width like Android's former values-sw600dp / values-sw720dp resources (Compose Multiplatform
 * resources have no screen-width qualifiers). Read them via [LocalDimens].
 */
@Immutable
data class Dimens(
    val pitchbarWidth: Dp = 62.dp,
    val seekbarHeight: Dp = 62.dp,
    val textviewHeight: Dp = 32.dp,
    val titleTextviewHeight: Dp = 32.dp,
    val textviewPaddingStart: Dp = 12.dp,
    val textviewPaddingEnd: Dp = 12.dp,
    val roundedShapeRadius: Dp = 7.dp,
    val kebabbuttonSize: Dp = 25.dp,
    // Waveform / cue overlay
    val waveformTopMargin: Dp = 2.dp,
    val cueMarkerWidth: Dp = 30.dp,
    val cueMarkerTopMargin: Dp = 15.dp,
) {
    companion object {
        val Phone = Dimens()

        /** Smallest width ≥ 600 dp (7" tablets). */
        val Tablet = Dimens(
            textviewHeight = 40.dp,
            pitchbarWidth = 55.dp,
            seekbarHeight = 55.dp,
            cueMarkerWidth = 20.dp,
            cueMarkerTopMargin = 5.dp,
        )

        /** Smallest width ≥ 720 dp (10" tablets, e.g. the Galaxy Tab A 10.1). */
        val LargeTablet = Dimens(
            textviewPaddingStart = 15.dp,
            textviewPaddingEnd = 15.dp,
            textviewHeight = 55.dp,
            titleTextviewHeight = 44.dp,
            kebabbuttonSize = 30.dp,
            pitchbarWidth = 96.dp,
            seekbarHeight = 96.dp,
            roundedShapeRadius = 12.dp,
            cueMarkerWidth = 30.dp,
            cueMarkerTopMargin = 5.dp,
        )

        fun forSmallestWidth(smallestWidthDp: Int): Dimens = when {
            smallestWidthDp >= 720 -> LargeTablet
            smallestWidthDp >= 600 -> Tablet
            else -> Phone
        }
    }
}

val LocalDimens = staticCompositionLocalOf { Dimens.Phone }

/** The smaller side of the screen in dp, independent of the orientation. */
@Composable
expect fun smallestScreenWidthDp(): Int

/** Provides the [Dimens] for the current screen to [content]. */
@Composable
fun ProvideDimens(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDimens provides Dimens.forSmallestWidth(smallestScreenWidthDp()),
        content = content
    )
}
