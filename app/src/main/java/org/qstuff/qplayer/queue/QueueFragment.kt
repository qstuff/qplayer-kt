package org.qstuff.qplayer.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import org.qstuff.qplayer.R
import org.qstuff.qplayer.filebrowser.FileBrowserViewModel

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueFragment: Fragment() {

    companion object {

        fun newInstance(): QueueFragment {
            val contentListFragment = QueueFragment()
            return contentListFragment
        }
    }

    private lateinit var queueViewModel: QueueViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        queueViewModel = ViewModelProviders.of(activity!!).get(QueueViewModel::class.java)
        fileBrowserViewModel = ViewModelProviders.of(activity!!).get(FileBrowserViewModel::class.java)

        return inflater.inflate(R.layout.fragment_queue, container, false)
    }
}