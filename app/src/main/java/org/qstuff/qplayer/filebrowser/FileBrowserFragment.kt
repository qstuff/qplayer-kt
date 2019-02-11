package org.qstuff.qplayer.filebrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

        const val MY_PERMISSIONS_REQUEST_READ_STORAGE = 222

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

        checkForStoragePermission()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fileBrowserViewModel.saveLastBrowsedDir()
    }

    private fun setupObservers() {

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

    //
    // Permission for storage
    //

    private fun checkForStoragePermission() {
        if (ContextCompat.checkSelfPermission(context!!, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), MY_PERMISSIONS_REQUEST_READ_STORAGE)
        } else {
            setupObservers()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        when (requestCode) {

            MY_PERMISSIONS_REQUEST_READ_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    setupObservers()
                } else {
                    fileBrowserHeader.text = "Please go to app settings and grant storage permission"
                }
            }
        }
    }
    //
    // FileBrowserAdapter.FileBrowserItemInteractionListener
    //

    override fun onFileItemClicked(file: File) {
        fileBrowserViewModel.onFileItemClicked(file)
        // TODO: pass file to queueViewModel
    }

    override fun onFileItemLongClicked(file: File) {
        // TODO: if dir: open dialog, then pass list of files to queueViewModel
    }

    override fun onFilePrelistenClicked(file: File) {
        // TODO: to playerViewModel
    }
}