package org.qstuff.qplayer.playlists

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
import kotlinx.android.synthetic.main.fragment_playlist.*
import kotlinx.android.synthetic.main.queue_dialog_save_tracks_as_playlist.view.*
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.queue.ItemTouchHelperCallback
import org.qstuff.qplayer.queue.QueueViewModel
import timber.log.Timber

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class PlaylistFragment:
        Fragment(),
        KoinComponent,
        PlaylistAdapter.PlaylistItemInteractionListener {

    companion object {

        fun newInstance(): PlaylistFragment {
            val contentListFragment = PlaylistFragment()
            return contentListFragment
        }
    }

    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var queueViewModel: QueueViewModel

    private lateinit var playlistAdapter: PlaylistAdapter


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        playlistViewModel = ViewModelProviders.of(activity!!).get(PlaylistViewModel::class.java)
        queueViewModel = ViewModelProviders.of(activity!!).get(QueueViewModel::class.java)

        return inflater.inflate(R.layout.fragment_playlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistAdapter = PlaylistAdapter(this@PlaylistFragment)
        val callback = ItemTouchHelperCallback(playlistAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(playlistRecycler)

        playlistRecycler.apply {
            adapter = playlistAdapter
            layoutManager = LinearLayoutManager(context)
        }

        playlistViewModel.playlistList.observe(this, Observer { playlistList ->
            Timber.d("playlistList(): num: ${playlistList.size}")

            playlistList?.also {
                playlistAdapter.setPlaylistList(playlistList.toMutableList())
            }
        })
        playlistViewModel.loadPlaylists()
    }

    //
    // PlaylistAdapter.PlaylistItemInteractionListener
    //

    override fun onPlaylistItemClicked(playlist: Playlist) {
        showOpenPlaylistDialog(playlist)
    }

    override fun onPlaylistItemDismsissed(playlist: Playlist) {
        playlistViewModel.removePlaylist(playlist)
    }

    override fun onPlaylistItemMoved(playlists: MutableList<Playlist>) {
        playlistViewModel.playlistListReordered(playlists)
    }

    //
    // Dialogs
    //

    private fun showOpenPlaylistDialog(playlist: Playlist) {

        val tracks = playlistViewModel.getTracksForPlaylist(playlist)
        val dialogView = layoutInflater.inflate(R.layout.dialog_show_tracks, null)
        dialogView.listview.apply {
            adapter = DialogTrackListAdapter(context, tracks ?: listOf())
        }

        AlertDialog.Builder(activity)
                .apply {
                    setView(dialogView)
                    setCancelable(false)
                    setTitle(getString(R.string.filebrowser_dialog_add_tracks_to_queue_title))
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, which ->
                        queueViewModel.addTrackList(tracks)
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.filebrowser_dialog_queue_overwrite)) { dialog, which ->
                        queueViewModel.replaceTrackList(tracks)
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.dialog_cancel)) { dialog, which ->
                        dialog.dismiss()
                    }
                }.show()
    }

    private class DialogTrackListAdapter(context: Context, val items: List<Track>):
            ArrayAdapter<Track>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.dialog_track_list_item, null)
            }
            val text = view!!.findViewById<TextView>(R.id.itemText)
            text.text = items.get(position).name
            return view
        }
    }
}