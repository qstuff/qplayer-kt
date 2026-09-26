package org.qstuff.qplayer.ui.queue

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.qstuff.qplayer.R
import org.qstuff.qplayer.databinding.QueueListItemBinding
import org.qstuff.qplayer.datasource.model.Track
import timber.log.Timber
import java.util.*

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/12/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueAdapter(private val interactionListener: QueueItemInteractionListener):
        RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        ItemTouchHelperAdapter {

    interface QueueItemInteractionListener {
        fun onQueueItemClicked(track: Track)
        fun onQueueItemDismissed(track: Track, position: Int)
        fun onQueueItemMoved(tracks: MutableList<Track>)
    }

    private var selectedIndex = -1
    private lateinit var tracks: MutableList<Track>

    @SuppressLint("NotifyDataSetChanged")
    fun onItemSelectedIndex(index: Int) {
        Timber.d("onItemSelectedIndex(): $index")

        selectedIndex = index
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            QueueItemViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.queue_list_item, parent, false)
            )

    override fun getItemCount() = tracks.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        Timber.v("onBindViewHolder(): pos: $position, tracks: $tracks")

        val track = tracks[position]
        val binding = (holder as QueueItemViewHolder).binding
            holder.itemView.apply {

                if (selectedIndex == position) {
                    binding.queueListItemIcon.setImageResource(R.drawable.icon_track_selected)
                    binding.queueListItemTitle.setTextColor(
                        ContextCompat.getColor(
                            context!!,
                            R.color.q_orange
                        )
                    )
                } else {
                    binding.queueListItemIcon.setImageResource(R.drawable.icon_track)
                    binding.queueListItemTitle.setTextColor(
                        ContextCompat.getColor(
                            context!!,
                            R.color.white
                        )
                    )
                }
                binding.queueListItemTitle.text = track.name
                binding.queueListItemTitle.setOnClickListener {
                    interactionListener.onQueueItemClicked(track)
                }
            }

    }

    @SuppressLint("NotifyDataSetChanged")
    fun setTrackList(tracks: MutableList<Track>) {
        this.tracks = tracks
        notifyDataSetChanged()
    }

    //
    // ItemTouchHelperAdapter
    //

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        Timber.d("onItemMove(): from: $fromPosition to: $toPosition")

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tracks, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tracks, i, i - 1)
            }
        }
        if (selectedIndex == fromPosition) {
            selectedIndex = toPosition
        }
        notifyItemMoved(fromPosition, toPosition)
        interactionListener.onQueueItemMoved(tracks)
    }

    override fun onItemDismiss(position: Int) {
        Timber.d("onItemDismiss(): pos: $position, tracks: $tracks")

        interactionListener.onQueueItemDismissed(tracks[position], position)
    }

    //
    // ViewHolder
    //

    class QueueItemViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val binding = QueueListItemBinding.bind(view)
    }
}