package org.qstuff.qplayer.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.qstuff.qplayer.player.PlayerViewModel
import org.qstuff.qplayer.playlists.PlaylistViewModel
import org.qstuff.qplayer.ui.theme.QDeqTheme

class QueueFragment : Fragment() {

    companion object {
        fun newInstance() = QueueFragment()
    }

    private lateinit var queueViewModel: QueueViewModel
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var playlistViewModel: PlaylistViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        queueViewModel = ViewModelProvider(requireActivity()).get(QueueViewModel::class.java)
        playerViewModel = ViewModelProvider(requireActivity()).get(PlayerViewModel::class.java)
        playlistViewModel = ViewModelProvider(requireActivity()).get(PlaylistViewModel::class.java)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QDeqTheme {
                    QueueScreen(
                        queueViewModel = queueViewModel,
                        playlistViewModel = playlistViewModel
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cross-ViewModel wiring: track selection → player load
        queueViewModel.onTrackSelected.observe(viewLifecycleOwner) { track ->
            track?.let { playerViewModel.loadTrack(it) }
        }

        queueViewModel.readTrackList()
        queueViewModel.readSelectedTrack()
        queueViewModel.loadStates()
        queueViewModel.loadSettings()
        playlistViewModel.loadPlaylists()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        queueViewModel.saveStates()
    }
}
