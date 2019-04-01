package org.qstuff.qplayer.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_playlist.*
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.player.PlayerViewModel
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
    private lateinit var fileBrowserViewModel: FileBrowserViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var playlistAdapter: PlaylistAdapter


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        playlistViewModel = ViewModelProviders.of(activity!!).get(PlaylistViewModel::class.java)
        queueViewModel = ViewModelProviders.of(activity!!).get(QueueViewModel::class.java)
        fileBrowserViewModel = ViewModelProviders.of(activity!!).get(FileBrowserViewModel::class.java)
        playerViewModel = ViewModelProviders.of(activity!!).get(PlayerViewModel::class.java)

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
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPlaylistItemDismsissed(playlist: Playlist) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPlaylistItemMoved(playlists: MutableList<Playlist>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}