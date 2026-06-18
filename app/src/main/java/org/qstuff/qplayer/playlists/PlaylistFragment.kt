package org.qstuff.qplayer.playlists

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import org.koin.core.component.KoinComponent
import org.qstuff.qplayer.R
import org.qstuff.qplayer.databinding.DialogShowTracksBinding
import org.qstuff.qplayer.databinding.FragmentPlaylistBinding
import org.qstuff.qplayer.databinding.QueueDialogSaveTracksAsPlaylistBinding
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
            return PlaylistFragment()
        }
    }

    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var queueViewModel: QueueViewModel
    private lateinit var playlistAdapter: PlaylistAdapter

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        playlistViewModel = ViewModelProvider(requireActivity()).get(PlaylistViewModel::class.java)
        queueViewModel = ViewModelProvider(requireActivity()).get(QueueViewModel::class.java)

        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistAdapter = PlaylistAdapter(this@PlaylistFragment)
        val callback = ItemTouchHelperCallback(playlistAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.playlistRecycler)

        binding.playlistRecycler.apply {
            adapter = playlistAdapter
            layoutManager = LinearLayoutManager(context)
        }

        playlistViewModel.playlistList.observe(viewLifecycleOwner, Observer { playlistList ->
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

    override fun onPlaylistItemDismsissed(playlist: Playlist, position: Int) {
        playlistViewModel.removePlaylist(playlist)

        Snackbar.make(requireView(), getString(R.string.snackbar_title_removed, playlist.name), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.snackbar_undo)) {
                    playlistViewModel.restorePlaylistAt(playlist, position)
                }
                .show()

    }

    override fun onPlaylistItemMoved(playlists: MutableList<Playlist>) {
        playlistViewModel.playlistListReordered(playlists)
    }

    //
    // Dialogs
    //

    private fun showOpenPlaylistDialog(playlist: Playlist) {

        val tracks = playlistViewModel.getTracksForPlaylist(playlist)
        val dialogBinding = DialogShowTracksBinding.inflate(LayoutInflater.from(context))

        dialogBinding.listview.apply {
            adapter = DialogTrackListAdapter(context, tracks)
        }

        AlertDialog.Builder(activity)
                .apply {
                    setView(dialogBinding.root)
                    setCancelable(false)
                    setTitle(getString(R.string.filebrowser_dialog_add_tracks_to_queue_title))
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->
                        queueViewModel.addTrackList(tracks)
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.filebrowser_dialog_queue_overwrite)) { dialog, _ ->
                        queueViewModel.replaceTrackList(tracks)
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.dialog_cancel)) { dialog, _ ->
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
            text.text = items[position].name
            return view
        }
    }
}