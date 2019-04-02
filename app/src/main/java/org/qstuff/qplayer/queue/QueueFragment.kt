package org.qstuff.qplayer.queue

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_queue.*
import kotlinx.android.synthetic.main.queue_dialog_save_tracks_as_playlist.view.*
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.R
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
            val contentListFragment = QueueFragment()
            return contentListFragment
        }
    }

    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var queueViewModel: QueueViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var queueAdapter: QueueAdapter


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        playlistViewModel = ViewModelProviders.of(activity!!).get(PlaylistViewModel::class.java)
        queueViewModel = ViewModelProviders.of(activity!!).get(QueueViewModel::class.java)
        fileBrowserViewModel = ViewModelProviders.of(activity!!).get(FileBrowserViewModel::class.java)
        playerViewModel = ViewModelProviders.of(activity!!).get(PlayerViewModel::class.java)

        return inflater.inflate(R.layout.fragment_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        queueAdapter = QueueAdapter(this@QueueFragment)
        val callback = ItemTouchHelperCallback(queueAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(queueRecycler)

        queueRecycler.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(context)
        }

        queueViewModel.trackList.observe(this, Observer { tracks ->
            Timber.d("trackList: ${tracks.size}")
            tracks?.also {
                queueAdapter.setTrackList(tracks.toMutableList())
            }
        })

        queueViewModel.onTrackSelectedIndex.observe(this, Observer { index ->
            Timber.d("onTrackSelectedIndex(): $index")

            queueAdapter.onItemSelectedIndex(index)
            queueRecycler.scrollToPosition(index)
        })

        queueViewModel.onTrackSelected.observe(this, Observer { track ->
            Timber.d("onTrackSelected(): $track")

            playerViewModel.loadTrack(track)
        })

        queueClearButton.setOnClickListener {
            showClearQueueDialog()
        }

        queueSaveAsPlaylistButton.setOnClickListener {
            showSaveAsPlaylistDialog()
        }
    }

    override fun onResume() {
        super.onResume()

        queueViewModel.loadTrackList()
        queueViewModel.loadSelectedTrack()

    }

    override fun onDestroyView() {
        super.onDestroyView()

        queueViewModel.saveTrackList()
        queueViewModel.saveSelectedTrack()
    }

    //
    // QueueAdapter.QueueItemInteractionListener
    //

    override fun onQueueItemClicked(track: Track) {
        queueViewModel.onTrackSelected(track)
    }

    override fun onQueueItemDismsissed(track: Track) {
        queueViewModel.removeTrack(track)
        // TODO: UNDO snackbar ?
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
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, which ->
                        queueViewModel.clearTrackList()
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.dialog_cancel)) { dialog, which ->
                        dialog.dismiss()
                    }
                }.show()
    }

    private fun showSaveAsPlaylistDialog() {

        val tracks = queueViewModel.trackList.value
        val playlists = playlistViewModel.playlistList.value

        val dialogView = layoutInflater.inflate(R.layout.queue_dialog_save_tracks_as_playlist, null)
        dialogView.listview.apply {
            adapter = DialogListAdapter(context, playlists ?: listOf())
            setOnItemClickListener { parent, view, position, id ->
                showAddToExistingPlaylistDialog(position)
            }
        }

        AlertDialog.Builder(activity)
                .apply {
                    setView(dialogView)
                    setCancelable(false)
                    setTitle(getString(R.string.queue_dialog_save_tracks_as_playlist_title))
                    setMessage(getString(R.string.queue_dialog_save_tracks_as_playlist_message))
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, which ->

                        if (dialogView.textInput.text.isBlank()) {
                            context.shortToast(getString(R.string.queue_toast_save_tracks_as_queue_need_name))
                        } else {
                            playlistViewModel.saveTracksAsNewPlaylist(tracks ?: listOf(),
                                    dialogView.textInput.text.toString())
                        }
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.dialog_cancel)) { dialog, which ->
                        dialog.dismiss()
                    }
                }.show()
    }

    private fun showAddToExistingPlaylistDialog(position: Int) {

    }

    private class DialogListAdapter(context: Context, val items: List<Playlist>):
            ArrayAdapter<Playlist>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_list_item, null)
            }
            val text = view!!.findViewById<TextView>(R.id.itemText)
            text.text = items.get(position).name
            return view
        }
    }
}