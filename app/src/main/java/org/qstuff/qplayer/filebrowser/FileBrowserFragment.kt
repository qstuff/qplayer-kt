package org.qstuff.qplayer.filebrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_filebrowser.*
import org.qstuff.qplayer.R
import timber.log.Timber
import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class FileBrowserFragment: Fragment(), FileBrowserAdapter.FileBrowserItemInteractionListener {

    companion object {

        fun newInstance(): FileBrowserFragment {
            val contentListFragment = FileBrowserFragment()

            return contentListFragment
        }
    }

    private lateinit var fileBrowserViewModel: FileBrowserViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        fileBrowserViewModel = ViewModelProviders.of(this).get(FileBrowserViewModel::class.java)
        return inflater.inflate(R.layout.fragment_filebrowser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileBrowserViewModel.fileList.observe(this, Observer { files ->
            Timber.d("fileList: $files")
            files?.also {
                fileBrowserRecycler.apply {
                    adapter = FileBrowserAdapter(it, this@FileBrowserFragment)
                    layoutManager = LinearLayoutManager(context)
                }
                fileBrowserRecycler.adapter?.notifyDataSetChanged()
            }
        })

        fileBrowserViewModel.directoryName.observe(this, Observer { name ->
            fileBrowserHeader.text = name
        })

        fileBrowserGoToParentDir.setOnClickListener {
            fileBrowserViewModel.navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fileBrowserViewModel.saveLastBrowsedDir()
    }

    //
    // FileBrowserAdapter.FileBrowserItemInteractionListener
    //

    override fun onFileItemClicked(file: File) {
        fileBrowserViewModel.onFileItemClicked(file)
    }

    override fun onFileItemLongClicked(file: File) {

    }

    override fun onFilePrelistenClicked(file: File) {

    }
}