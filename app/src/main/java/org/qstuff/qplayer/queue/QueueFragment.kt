package org.qstuff.qplayer.queue

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.R
import org.qstuff.qplayer.databinding.DialogM3uShowTracksBinding
import org.qstuff.qplayer.databinding.FragmentQueueBinding
import org.qstuff.qplayer.databinding.QueueDialogSaveTracksAsPlaylistBinding
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.player.PlayerViewModel
import org.qstuff.qplayer.playlists.PlaylistViewModel
import org.qstuff.qplayer.util.shortToast
import timber.log.Timber

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueFragment:
        Fragment(),
        KoinComponent,
        QueueAdapter.QueueItemInteractionListener {

    companion object {

        fun newInstance(): QueueFragment {
            return QueueFragment()
        }
    }

    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var queueViewModel: QueueViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var queueAdapter: QueueAdapter
    private var currentTrack: Track? = null

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        playlistViewModel = ViewModelProvider(requireActivity()).get(PlaylistViewModel::class.java)
        queueViewModel = ViewModelProvider(requireActivity()).get(QueueViewModel::class.java)
        fileBrowserViewModel = ViewModelProvider(requireActivity()).get(FileBrowserViewModel::class.java)
        playerViewModel = ViewModelProvider(requireActivity()).get(PlayerViewModel::class.java)

        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        queueAdapter = QueueAdapter(this@QueueFragment)
        val callback = ItemTouchHelperCallback(queueAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.queueRecycler)

        binding.queueRecycler.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(context)
        }

        setupObservers()

        setupInteractionListeners()

        queueViewModel.readTrackList()
        queueViewModel.readSelectedTrack()
        queueViewModel.loadStates()
        queueViewModel.loadSettings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentTrack?.playPosition = playerViewModel.getTrackPosition()
        queueViewModel.saveStates()
    }

    //
    // ViewModel Observers
    //

    private fun setupObservers() {

        queueViewModel.trackList.observe(viewLifecycleOwner) { tracks ->
            Timber.d("trackList: ${tracks.size}")
            tracks?.also {
                queueAdapter.setTrackList(tracks.toMutableList())
            }
        }

        queueViewModel.onTrackSelectedIndex.observe(viewLifecycleOwner) { index ->
            Timber.d("onTrackSelectedIndex(): $index")

            index?.let {
                queueAdapter.onItemSelectedIndex(it)
                binding.queueRecycler.scrollToPosition(it)
            }
        }

        queueViewModel.onTrackSelected.observe(viewLifecycleOwner) { track ->
            Timber.d("onTrackSelected(): $track")
            currentTrack = track
            playerViewModel.loadTrack(track)
        }
    }

    //
    // Interaction Listeners
    //

    private fun setupInteractionListeners() {

        binding.queueClearButton.setOnClickListener {
            if (queueViewModel.isShowClearQueueWarningEnabled) {
                showClearQueueDialog()
            } else {
                queueViewModel.clearTrackList()
            }
        }

        binding.queueSaveAsPlaylistButton.setOnClickListener {
            showSaveAsPlaylistDialog()
        }
    }

    //
    // QueueAdapter.QueueItemInteractionListener
    //

    override fun onQueueItemClicked(track: Track) {
        queueViewModel.onTrackSelected(track)
    }

    override fun onQueueItemDismissed(track: Track, position: Int) {
        queueViewModel.removeTrack(track)

        Snackbar.make(requireView(), getString(R.string.snackbar_title_removed, track.name), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.snackbar_undo)) {
                    queueViewModel.restoreTrackAt(track, position)
                }
                .show()
    }

    override fun onQueueItemMoved(tracks: MutableList<Track>) {
        queueViewModel.trackListReordered(tracks)
    }

    //
    // Dialogs
    //

    private fun showClearQueueDialog() {

        AlertDialog.Builder(activity)
                .apply {
                    setCancelable(false)
                    setTitle(getString(R.string.queue_dialog_confirm_clear_title))
                    setMessage(getString(R.string.queue_dialog_confirm_clear_message))
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->
                        queueViewModel.clearTrackList()
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                }.show()
    }

    private fun showSaveAsPlaylistDialog() {

        val tracks = queueViewModel.trackList.value
        val playlists = playlistViewModel.playlistList.value

        val dialogBinding = QueueDialogSaveTracksAsPlaylistBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(activity)
                .apply {
                    setView(dialogBinding.root)
                    setCancelable(false)
                    setTitle(getString(R.string.queue_dialog_save_tracks_as_playlist_title))
                    setMessage(getString(R.string.queue_dialog_save_tracks_as_playlist_message))
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->

                        if (dialogBinding.textInput.text.isBlank()) {
                            context.shortToast(getString(R.string.queue_toast_save_tracks_as_queue_need_name))
                        } else {
                            playlistViewModel.saveTracksAsNewPlaylist(tracks ?: listOf(),
                                dialogBinding.textInput.text.toString())
                        }
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                }.show()

        dialogBinding.listview.apply {
            adapter = DialogListAdapter(context, playlists ?: listOf())
            setOnItemClickListener { _, _, position, _ ->
                showAddToExistingPlaylistDialog(position)
                dialog.dismiss()
            }
        }
    }

    private fun showAddToExistingPlaylistDialog(position: Int) {
        val tracks = queueViewModel.trackList.value
        val playlist = playlistViewModel.playlistList.value?.get(position)

        if (tracks.isNullOrEmpty() || playlist?.name.isNullOrBlank()) {
            context?.shortToast(getString(R.string.queue_toast_add_to_existing_playlist_problem))
            return
        }

        AlertDialog.Builder(activity)
                .apply {
                    setCancelable(false)
                    setTitle(getString(R.string.queue_dialog_add_tracks_to_existing_playlist_title))
                    setMessage(getString(R.string.queue_dialog_add_tracks_to_existing_playlist_message))
                    setPositiveButton(getString(R.string.queue_dialog_add_tracks_to_existing_playlist_overwrite)) { dialog, _ ->
                        playlistViewModel.saveTracksToExistingPlaylist(tracks, playlist?.name, true)
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.queue_dialog_add_tracks_to_existing_playlist_append)) { dialog, _ ->
                        playlistViewModel.saveTracksToExistingPlaylist(tracks, playlist?.name, false)
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                }.show()
    }

    private class DialogListAdapter(context: Context, val items: List<Playlist>):
            ArrayAdapter<Playlist>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_list_item, null)
            }
            val text = view!!.findViewById<TextView>(R.id.itemText)
            text.text = items[position].name
            return view
        }
    }
}