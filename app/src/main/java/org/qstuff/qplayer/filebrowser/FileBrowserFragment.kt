package org.qstuff.qplayer.filebrowser

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.qstuff.qplayer.player.PlayerViewModel
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.ui.theme.QDeqTheme

@RequiresApi(Build.VERSION_CODES.R)
class FileBrowserFragment : Fragment() {

    companion object {
        fun newInstance() = FileBrowserFragment()
    }

    private lateinit var fileBrowserViewModel: FileBrowserViewModel
    private lateinit var queueViewModel: QueueViewModel
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fileBrowserViewModel = ViewModelProvider(requireActivity()).get(FileBrowserViewModel::class.java)
        queueViewModel = ViewModelProvider(requireActivity()).get(QueueViewModel::class.java)
        playerViewModel = ViewModelProvider(requireActivity()).get(PlayerViewModel::class.java)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QDeqTheme {
                    FileBrowserScreen(
                        fileBrowserViewModel = fileBrowserViewModel,
                        queueViewModel = queueViewModel,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        fileBrowserViewModel.saveLastBrowsedDir()
    }
}
