package org.qstuff.qplayer.filebrowser

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.qstuff.qplayer.R
import org.qstuff.qplayer.databinding.*
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.player.PlayerViewModel
import org.qstuff.qplayer.playlists.M3uUtils
import org.qstuff.qplayer.queue.QueueViewModel
import org.qstuff.qplayer.util.directoryContainsSupportedFiles
import org.qstuff.qplayer.util.isM3UList
import org.qstuff.qplayer.util.listTracksForAddDialog
import org.qstuff.qplayer.util.shortToast
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
@RequiresApi(Build.VERSION_CODES.R)
class FileBrowserFragment:
        Fragment(),
        FileBrowserAdapter.FileBrowserItemInteractionListener {

    companion object {

        fun newInstance(): FileBrowserFragment {
            return FileBrowserFragment()
        }
    }

    private lateinit var queueViewModel: QueueViewModel
    private lateinit var fileBrowserViewModel: FileBrowserViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private var _binding: FragmentFilebrowserBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                setupObservers()
            } else {
                binding.fileBrowserHeader.text = "Please go to app settings and grant storage or audio permission"
                Timber.d("xxx Permission Not Granted:  ")
            }
        }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        queueViewModel = ViewModelProvider(requireActivity()).get(QueueViewModel::class.java)
        fileBrowserViewModel = ViewModelProvider(requireActivity()).get(FileBrowserViewModel::class.java)
        playerViewModel = ViewModelProvider(requireActivity()).get(PlayerViewModel::class.java)

        _binding = FragmentFilebrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        checkForStoragePermission()
    }

    override fun onStop() {
        super.onStop()
        fileBrowserViewModel.saveLastBrowsedDir()
    }

    private fun setupObservers() {
        Timber.d("xxx setupObservers(): ")
        fileBrowserViewModel.fileList.observe(viewLifecycleOwner) { files ->

            Timber.d("xxx fileList: $files")
            files?.also {
                binding.fileBrowserRecycler.apply {
                    adapter = FileBrowserAdapter(it, this@FileBrowserFragment)
                    layoutManager = LinearLayoutManager(context)

                }
            }
        }

        fileBrowserViewModel.directoryName.observe(viewLifecycleOwner, Observer { name ->
            binding.fileBrowserHeader.text = name
        })

        binding.fileBrowserGoToParentDir.setOnClickListener {
            fileBrowserViewModel.navigateUp()
        }
    }

    //
    // Permission for storage
    //

    private fun checkForStoragePermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    setupObservers()
                }

                ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(), Manifest.permission.READ_MEDIA_AUDIO
                ) -> {
                    // In an educational UI, explain to the user why your app requires this
                    // permission for a specific feature to behave as expected, and what
                    // features are disabled if it's declined. In this UI, include a
                    // "cancel" or "no thanks" button that lets the user continue
                    // using your app without granting the permission.
                    binding.fileBrowserHeader.text = "Please go to app settings and grant audio permission"
                    Timber.d("xxx Permission Not Granted:  ")
                }

                else -> {
                    // You can directly ask for the permission.
                    // The registered ActivityResultCallback gets the result of this request.
                    requestPermissionLauncher.launch(
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                }
            }
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    setupObservers()
                }

                ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE
                ) -> {
                    // In an educational UI, explain to the user why your app requires this
                    // permission for a specific feature to behave as expected, and what
                    // features are disabled if it's declined. In this UI, include a
                    // "cancel" or "no thanks" button that lets the user continue
                    // using your app without granting the permission.
                    binding.fileBrowserHeader.text = "Please go to app settings and grant storage permission"
                    Timber.d("xxx Permission Not Granted:  ")
                }

                else -> {
                    // You can directly ask for the permission.
                    // The registered ActivityResultCallback gets the result of this request.
                    requestPermissionLauncher.launch(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
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
        val dialogBinding = DialogShowTracksBinding.inflate(LayoutInflater.from(context))

        dialogBinding.listview.apply {
            adapter = DialogFileListAdapter(context, files)
        }

        AlertDialog.Builder(activity)
                .apply {
                    setCancelable(false)
                    setView(dialogBinding.root)
                    setTitle(getString(R.string.filebrowser_dialog_add_tracks_to_queue_title))
                    setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->
                        queueViewModel.addFileList(files)
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.filebrowser_dialog_queue_overwrite)) { dialog, _ ->
                        queueViewModel.clearTrackList()
                        queueViewModel.addFileList(files)
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                }
                .show()
    }

    private fun showOpenM3uListDialog(file: File) {
        Timber.d("showOpenM3uListDialog(): ${file.name}")

        val tracks = M3uUtils.m3UParserGetTracks(FileInputStream(file), file.parent ?: "")

        val tracksFound = tracks.first
        val tracksNotFound = tracks.second

        val dialogBinding = DialogM3uShowTracksBinding.inflate(LayoutInflater.from(context))

        if (tracksFound.isNotEmpty()) {
            Timber.d("showOpenM3uListDialog(): found ${tracksFound.size} tracks")
            dialogBinding.titleItemsFound.visibility = View.VISIBLE
            dialogBinding.listviewItemsFound?.apply {
                adapter = DialogTrackListAdapter(context, tracksFound)
            }
        } else {
            dialogBinding.titleItemsFound.visibility = View.GONE
        }

        if (tracksNotFound.isNotEmpty()) {
            Timber.d("showOpenM3uListDialog(): not found ${tracksFound.size} tracks")
            dialogBinding.titleItemsNotFound.visibility = View.VISIBLE
            dialogBinding.listviewItemsNotFound.apply {
                adapter = DialogTrackListAdapter(context, tracksNotFound)
            }
        } else {
            dialogBinding.titleItemsNotFound.visibility = View.GONE
        }

        AlertDialog.Builder(activity)
                .apply {
                    if(tracksFound.isEmpty()) {
                        setCancelable(false)
                        setView(dialogBinding.root)
                        setTitle(getString(R.string.add_m3ulist_to_queue_dialog_no_tracks_found_title, file.name))
                        setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->
                            dialog.dismiss()
                        }
                    } else {
                        setCancelable(false)
                        setView(dialogBinding.root)
                        setTitle(getString(R.string.add_m3ulist_to_queue_dialog_tracks_found_title, file.name))
                        setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->
                            queueViewModel.addTrackList(tracksFound)
                            dialog.dismiss()
                        }
                        setNegativeButton(getString(R.string.filebrowser_dialog_queue_overwrite)) { dialog, _ ->
                            queueViewModel.clearTrackList()
                            queueViewModel.addTrackList(tracksFound)
                            dialog.dismiss()
                        }
                        setNeutralButton(getString(R.string.dialog_cancel)) { dialog, _ ->

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
            text.text = items[position].name
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
            text.text = items[position].name
            return view
        }
    }
}