package org.qstuff.qplayer.playlists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.playlist_list_item.view.*
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Playlist
import org.qstuff.qplayer.queue.ItemTouchHelperAdapter
import timber.log.Timber
import java.util.*

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class PlaylistAdapter(private val interactionListener: PlaylistItemInteractionListener):
        RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        ItemTouchHelperAdapter {

    interface PlaylistItemInteractionListener {
        fun onPlaylistItemClicked(playlist: Playlist)
        fun onPlaylistItemDismsissed(playlist: Playlist, position: Int)
        fun onPlaylistItemMoved(playlists: MutableList<Playlist>)
    }

    private var playlists = mutableListOf<Playlist>()


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PlaylistItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.playlist_list_item, parent, false))

    override fun getItemCount() = playlists.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        Timber.v("onBindViewHolder(): pos: $position, playlists: $playlists")

        val playlist = playlists[position]
        holder.itemView.apply {
            playlistItemText.text = playlist.name
            playlistItemText.setOnClickListener {
                interactionListener.onPlaylistItemClicked(playlist)
            }
        }
    }

    fun setPlaylistList(playlists: MutableList<Playlist>) {

        this.playlists = playlists
        notifyDataSetChanged()
    }
    //
    // ItemTouchHelperAdapter
    //

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        Timber.d("onItemMove(): from: $fromPosition to: $toPosition")


        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(playlists, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(playlists, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        interactionListener.onPlaylistItemMoved(playlists)
    }

    override fun onItemDismiss(position: Int) {
        Timber.d("onItemDismiss(): pos: $position, playlists: $playlists")

        interactionListener.onPlaylistItemDismsissed(playlists[position], position)
    }

    //
    // ViewHolder
    //

    class PlaylistItemViewHolder(view: View): RecyclerView.ViewHolder(view)
}
