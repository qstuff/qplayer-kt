package org.qstuff.qplayer.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_queue.*
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.filebrowser.FileBrowserViewModel
import org.qstuff.qplayer.player.PlayerViewModel
import timber.log.Timber

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueFragment: Fragment(),
        KoinComponent,
        QueueAdapter.QueueItemInteractionListener {

    companion object {

        fun newInstance(): QueueFragment {
            val contentListFragment = QueueFragment()
            return contentListFragment
        }
    }

    private lateinit var queueViewModel: QueueViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var queueAdapter: QueueAdapter


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

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
            // TODO: Dialog, then
            // queueViewModel.clearTrackList()
        }

        queueSaveAsPlaylistButton.setOnClickListener {
            // TODO: Dialog, then
            // PlayListViewModel
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
        //playerViewModel.loadTrack(track)
        queueViewModel.onTrackSelected(track)
    }

    override fun onQueueItemDismsissed(track: Track) {
        queueViewModel.removeTrack(track)
        // TODO: UNDO snackbar ?
    }

    override fun onQueueItemMoved(tracks: MutableList<Track>) {
        queueViewModel.trackListReordered(tracks)
    }
}