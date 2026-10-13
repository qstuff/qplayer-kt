package org.qstuff.qplayer.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.ui.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.ui.player.mediaservice.TransportCommand
import org.qstuff.qplayer.ui.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.queue.QueueViewModel

/**
 * Wires the player and the queue together — everything that isn't a single ViewModel's business:
 * - a selected queue track is loaded into the player,
 * - a finished track lets the queue decide what follows (repeat / shuffle), or stops,
 * - Next / Prev from system media controls behave like the on-screen buttons,
 * - changed settings are applied right away, and state is restored on start / saved on demand.
 *
 * Platform independent: the platform host (the activity on Android) [start]s it in its own scope
 * and calls [saveState] when the app goes to the background or closes. Playback is meant to end
 * with that scope (the host stops the media service), so it is not an app-wide singleton.
 */
class PlaybackCoordinator(
    private val player: PlayerViewModel,
    private val queue: QueueViewModel,
    private val playlists: PlaylistViewModel,
    private val fileBrowser: FileBrowserViewModel,
    private val preferences: PreferencesDataSource
) {

    /**
     * Start the wiring in [scope] and restore the queue. The collectors are deliberately not gated
     * on a "visible" state: auto-advance must load and start the next track in the background too.
     *
     * [scope] must dispatch immediately on the calling (main) thread (like Android's
     * lifecycleScope), so the selection collector is subscribed before the restored track is
     * emitted below.
     */
    fun start(scope: CoroutineScope) {
        // queue track selection → player load
        scope.launch {
            queue.trackSelected.collect { track -> player.loadTrack(track) }
        }
        // track finished → the queue decides what follows; if nothing does, rewind so Play
        // starts the finished track again
        scope.launch {
            player.trackCompleted.collect { track ->
                if (!queue.onTrackCompleted(track)) player.stopAfterCompletion()
            }
        }
        // Next / Prev from system media controls → same as the on-screen buttons
        // (Prev is position-aware)
        scope.launch {
            player.transportCommands.collect { command ->
                val current = player.playerTrackState.value?.track
                when (command) {
                    TransportCommand.NEXT -> queue.nextTrack(current)
                    TransportCommand.PREVIOUS ->
                        if (!player.restartCurrentTrackIfPlayed()) queue.previousTrack(current)
                }
            }
        }
        // Settings changed (Settings screen) → apply right away
        scope.launch {
            preferences.settingsChanged.collect { applySettings() }
        }

        applySettings()
        queue.readTrackList()
        queue.readSelectedTrack()
        queue.loadStates()
        playlists.loadPlaylists()
    }

    /** Persist player, queue and file browser state (app goes to the background / closes). */
    fun saveState() {
        player.saveState()
        queue.saveStates()
        fileBrowser.saveLastBrowsedDir()
    }

    private fun applySettings() {
        player.loadSettings()
        queue.loadSettings()
    }
}
