package org.qstuff.qplayer.filebrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.filebrowser_list_item.view.*
import org.qstuff.qplayer.R
import org.qstuff.qplayer.util.isM3UList
import timber.log.Timber
import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/10/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class FileBrowserAdapter(val files: List<File>,
                         val interactionListener: FileBrowserItemInteractionListener):
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface FileBrowserItemInteractionListener {
        fun onFileItemClicked(file: File)
        fun onFileItemLongClicked(file: File)
        fun onFilePrelistenClicked(file: File)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FileBrowserItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.filebrowser_list_item, parent, false))

    override fun getItemCount() = files.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as FileBrowserItemViewHolder).bind(files[position], interactionListener)
    }

    //
    // ViewHolder
    //

    class FileBrowserItemViewHolder(view: View): RecyclerView.ViewHolder(view) {

        fun bind(file: File, interactionListener: FileBrowserItemInteractionListener) {
            itemView.apply {
                Timber.d("bind(): ${file.name}")

                fileListItemTitle.text = file.name
                fileListItemTitle.setOnClickListener {
                    interactionListener.onFileItemClicked(file)
                }
                fileListItemTitle.setOnLongClickListener {
                    interactionListener.onFileItemLongClicked(file)
                    true
                }

                if (file.isFile) {
                    if (file.isM3UList()) {
                        fileListItemIcon.setImageResource(R.drawable.ic_m3ulist)
                        fileListItemPrelistenButton.visibility = View.GONE
                    } else {
                        fileListItemIcon.setImageResource(R.drawable.icon_track)
                        fileListItemPrelistenButton.visibility = View.VISIBLE
                        fileListItemPrelistenButton.setOnClickListener {
                            interactionListener.onFilePrelistenClicked(file)
                        }
                    }
                } else if (file.isDirectory) {
                    fileListItemIcon.setImageResource(R.drawable.icon_directory)
                    fileListItemPrelistenButton.visibility = View.GONE
                } else {
                    // SHOULD NOT HAPPEN
                }
            }
        }
    }
}