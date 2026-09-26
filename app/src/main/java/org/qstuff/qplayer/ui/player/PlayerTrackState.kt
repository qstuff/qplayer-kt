package org.qstuff.qplayer.ui.player

import org.qstuff.qplayer.datasource.model.Track

/**
 * Immutable snapshot of the current track and its status, emitted by [PlayerViewModel].
 *
 * [revision] increments on every emission so each snapshot is distinct. `Track.trackStatus` is
 * `@Ignore` and the media layer mutates the *same* `Track` instance in place, so a plain
 * `StateFlow<Track>` (or Compose `State<Track>`) would dedup by equality and drop transitions —
 * including re-selecting the already-loaded track, which must re-trigger playback. The revision
 * guarantees every transition propagates, which is what PlayerScreen's old version-counter
 * observer used to do by hand.
 */
data class PlayerTrackState(
    val track: Track,
    val status: Track.TrackStatus,
    val revision: Int
)
