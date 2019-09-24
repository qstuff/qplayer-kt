package org.qstuff.qplayer.datasource.mediaservice

import android.content.*
import android.os.IBinder
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.qstuff.qplayer.QDeqApplication
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.player.PlayerViewModel
import org.qstuff.qplayer.util.PlayerStatus
import timber.log.Timber

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 11/24/19
 * Copyright (C) 2019 until now by Claus Chierici. All rights reserved.
 */

/**
 * Provides the QMediaPlayerService
 */
class MediaServiceDataSource (val context: Context) : KoinComponent {

    companion object {
        const val ACTION_SERVICE_FOREGROUND_START = "ACTION_SERVICE_FOREGROUND_START"
        const val ACTION_SERVICE_FOREGROUND_STOP = "ACTION_SERVICE_FOREGROUND_STOP"
        const val ACTION_SERVICE_BACKGROUND_START = "ACTION_SERVICE_BACKGROUND_START"
        const val ACTION_SERVICE_BACKGROUND_STOP = "ACTION_SERVICE_BACKGROUND_STOP"
    }

    private var mediaServiceStartMode: String
    private var mediaServiceStopMode: String


    // MediaService
    private lateinit var mediaService: QMediaPlayerService
    private var isMediaServiceRunning = false
    private var isMediaServiceBound = false
    var pendingTrack: Track? = null
    private lateinit var mediaServiceConnectionInfo: PlayerViewModel.MediaServiceConnectionInfo

    private val preferencesDataSource by inject<PreferencesDataSource>()


    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected(): ${name.toShortString()}")

            mediaService = (binder as QMediaPlayerService.MyBinder).service

            mediaServiceConnectionInfo.onMediaServiceConnected()

            isMediaServiceRunning = true
            isMediaServiceBound = true

            if (pendingTrack != null) {
                mediaServiceConnectionInfo.onPendingTrack(pendingTrack!!)
                pendingTrack = null
            }

        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("onServiceDisconnected(): ${name.toShortString()}")

            mediaServiceConnectionInfo.onMediaServiceDisconnected()

            mediaService.stop()
            mediaService.player.destroy()
        }
    }

    init {
        if (preferencesDataSource.isStartForegroundEnabled()) {
            mediaServiceStartMode = ACTION_SERVICE_FOREGROUND_START
            mediaServiceStopMode = ACTION_SERVICE_FOREGROUND_STOP
        } else {
            mediaServiceStartMode = ACTION_SERVICE_BACKGROUND_START
            mediaServiceStopMode = ACTION_SERVICE_BACKGROUND_STOP
        }
    }

    //
    // MediaService
    //

    fun registerMediaServiceConnectionInfo(callback: PlayerViewModel.MediaServiceConnectionInfo) {
        mediaServiceConnectionInfo = callback
    }

    fun getMediaService() = mediaService

    fun isMediaServiceRunning() = isMediaServiceRunning

    fun isMediaServiceBound() = isMediaServiceBound

    fun startMediaService(application: QDeqApplication) {

        if(!isMediaServiceRunning) {

            val intent = Intent(application, QMediaPlayerService::class.java)
            intent.action = mediaServiceStartMode
            application.startService(intent)
            application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun stopMediaService(application: QDeqApplication) {
        mediaService.stop()

        val intent = Intent(application, QMediaPlayerService::class.java)
        intent.action = mediaServiceStopMode
        application.startService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        if (isMediaServiceBound) {
            application.unbindService(serviceConnection)
            isMediaServiceBound = false
        }
        if (isMediaServiceRunning) {
            application.stopService(Intent(application, QMediaPlayerService::class.java))
            isMediaServiceRunning = false
        }
    }

}