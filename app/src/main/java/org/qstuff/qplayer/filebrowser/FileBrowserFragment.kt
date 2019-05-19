package org.qstuff.qplayer.filebrowser

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.dialog_m3u_show_tracks.view.*
import kotlinx.android.synthetic.main.fragment_filebrowser.*
import kotlinx.android.synthetic.main.queue_dialog_save_tracks_as_playlist.view.*
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.player.PlayerViewModel
import org.qstuff.qplayer.playlists.M3uUtils
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.util.*
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class FileBrowserFragment:
        Fragment(),
        FileBrowserAdapter.FileBrowserItemInteractionListener {

    companion object {

        const val MY_PERMISSIONS_REQUEST_READ_STORAGE = 222

        fun newInstance(): FileBrowserFragment {
            val contentListFragment = FileBrowserFragment()
            return contentListFragment
        }
    }

    private lateinit var queueViewModel: QueueViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel
    private lateinit var playerViewModel: PlayerViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        queueViewModel = ViewModelProviders.of(activity!!).get(QueueViewModel::class.java)
        fileBrowserViewModel = ViewModelProviders.of(activity!!).get(FileBrowserViewModel::class.java)
        playerViewModel = ViewModelProviders.of(activity!!).get(PlayerViewModel::class.java)

        return inflater.inflate(R.layout.fragment_filebrowser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkForStoragePermission()
    }

    override fun onStop() {
        super.onStop()
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
        Timber.d("onFileItemClicked()")

        if (file.isM3UList()) {
            showOpenM3uListDialog(file)
            return
        }
        if (file.isFile) {
            queueViewModel.addFile(file)
        }
        if (file.isDirectory) {
            fileBrowserViewModel.onFileItemClicked(file)
        }
    }

    override fun onFileItemLongClicked(file: File) {

        if (file.isDirectory && file.directoryContainsSupportedFiles()) {
            showAddTracksToQueueDialog(file)
        } else if (file.isDirectory && !file.directoryContainsSupportedFiles()) {
            context?.shortToast(getString(R.string.filebrowser_toast_empty_directory))
        }
    }

    override fun onFilePrelistenClicked(file: File) {
        playerViewModel.loadTrack(Track(file, true))
    }

    //
    // Dialogs
    //

    private fun showAddTracksToQueueDialog(file: File) {

        val files = file.listTracksForAddDialog()
        val dialogView = layoutInflater.inflate(R.layout.dialog_show_tracks, null)

        dialogView.listview?.apply {
            adapter = DialogFileListAdapter(context, files)
        }

        AlertDialog.Builder(activity)
                .apply {
                    setCancelable(false)
                    setView(dialogView)
                    setTitle(getString(R.string.filebrowser_dialog_add_tracks_to_queue_title))
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, which ->
                        queueViewModel.addFileList(files)
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.filebrowser_dialog_queue_overwrite)) { dialog, which ->
                        queueViewModel.clearTrackList()
                        queueViewModel.addFileList(files)
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.dialog_cancel)) { dialog, which ->
                        dialog.dismiss()
                    }
                }
                .show()
    }

    private fun showOpenM3uListDialog(file: File) {
        Timber.d("showOpenM3uListDialog(): ${file.name}")

        val tracks = M3uUtils.m3UParserGetTracks(FileInputStream(file), file.parent)

        val tracksFound = tracks.first
        val tracksNotFound = tracks.second
        val dialogView = layoutInflater.inflate(R.layout.dialog_m3u_show_tracks, null)

        if (tracksFound.isNotEmpty()) {
            Timber.d("showOpenM3uListDialog(): found ${tracksFound.size} tracks")
            dialogView.titleItemsFound.visibility = View.VISIBLE
            dialogView.listviewItemsFound?.apply {
                adapter = DialogTrackListAdapter(context, tracksFound)
            }
        } else {
            dialogView.titleItemsFound.visibility = View.GONE
        }

        if (tracksNotFound.isNotEmpty()) {
            Timber.d("showOpenM3uListDialog(): not found ${tracksFound.size} tracks")
            dialogView.titleItemsNotFound.visibility = View.VISIBLE
            dialogView.listviewItemsNotFound?.apply {
                adapter = DialogTrackListAdapter(context, tracksNotFound)
            }
        } else {
            dialogView.titleItemsNotFound.visibility = View.GONE
        }

        AlertDialog.Builder(activity)
                .apply {
                    if(tracksFound.isEmpty()) {
                        setCancelable(false)
                        setView(dialogView)
                        setTitle(getString(R.string.add_m3ulist_to_queue_dialog_no_tracks_found_title))
                        setPositiveButton(getString(R.string.dialog_ok)) { dialog, which ->
                            dialog.dismiss()
                        }
                    } else {
                        setCancelable(false)
                        setView(dialogView)
                        setTitle(getString(R.string.add_m3ulist_to_queue_dialog_tracks_found_title))
                        setPositiveButton(getString(R.string.dialog_ok)) { dialog, which ->
                            queueViewModel.addTrackList(tracksFound)
                            dialog.dismiss()
                        }
                        setNegativeButton(getString(R.string.filebrowser_dialog_queue_overwrite)) { dialog, which ->
                            queueViewModel.clearTrackList()
                            queueViewModel.addTrackList(tracksFound)
                            dialog.dismiss()
                        }
                        setNeutralButton(getString(R.string.dialog_cancel)) { dialog, which ->

                            dialog.dismiss()
                        }
                    }
                }
                .show()
    }

    private class DialogFileListAdapter(context: Context, val items: List<File>):
            ArrayAdapter<File>(context, 0, items) {

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