package org.qstuff.qplayer.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.ui.theme.QDeqTheme

class PlaylistFragment : Fragment() {

    companion object {
        fun newInstance() = PlaylistFragment()
    }

    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var queueViewModel: QueueViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        playlistViewModel = ViewModelProvider(requireActivity()).get(PlaylistViewModel::class.java)
        queueViewModel = ViewModelProvider(requireActivity()).get(QueueViewModel::class.java)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QDeqTheme {
                    PlaylistScreen(
                        playlistViewModel = playlistViewModel,
                        queueViewModel = queueViewModel
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlistViewModel.loadPlaylists()
    }
}
