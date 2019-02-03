package org.qstuff.qplayer.player.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import org.qstuff.qplayer.R
import org.qstuff.qplayer.player.PlayerActivity

import java.io.File

import timber.log.Timber

/**
 * Created by Claus Chierici ( 3/2/17 )
 * Copyright (C) 2017
 * All rights reserved.
 */
class QMediaPlayerService : Service() {

    companion object {
        const val MEDIA_SERVICE_NOTIFICATION_ID = 1
        const val MEDIA_SERVICE_NOTIFICATION_PLAY = 2
        const val MEDIA_SERVICE_NOTIFICATION_PAUSE = 3

        const val ACTION_PLAYER_TOGGLED = "ACTION_PLAYER_TOGGLED"
        const val ACTION_NOTIFICATION_DISMISSED = "ACTION_NOTIFICATION_DISMISSED"
        const val ACTION_NOTIFICATION_CLICKED = "ACTION_NOTIFICATION_CLICKED"
        const val NOT_ACTION_PLAYER_TOGGLED = "NOT_ACTION_PLAYER_TOGGLED"
        const val NOT_ACTION_NOTIFICATION_DISMISSED = "NOT_ACTION_NOTIFICATION_DISMISSED"

        const val EXTRA_NOTIFICATION_REQUESTCODE = "EXTRA_NOTIFICATION_REQUESTCODE"
    }

    private var player: QDeqPlayer? = null
    private val binder = MyBinder()
    private var notificationManager: NotificationManager? = null
    private var currentFile: File? = null

    //
    // Public Player Controls
    //

    val isPrepared: Boolean
        get() = currentFile != null

    //
    // Service Lifecycle
    //

    override fun onBind(intent: Intent): IBinder? {
        Timber.d("onBind")
        return binder
    }

    override fun onCreate() {
        Timber.d("onCreate()")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createPlayer()

    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand()")

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        Timber.d("onDestroy():")

        destroyPlayer()
    }

    //
    // common public methods
    //

//    fun setPlayerController(playerController: PlayerController) {
//        player!!.setPlayerController(playerController)
//    }

    fun playerServicePlay() {
        Timber.d("playerServicePlay():")

        player!!.play()

        if (currentFile != null) {
            notificationManager!!.cancel(MEDIA_SERVICE_NOTIFICATION_ID)
            val notification = createNotifcation(MEDIA_SERVICE_NOTIFICATION_PAUSE)
            notificationManager!!.notify(MEDIA_SERVICE_NOTIFICATION_ID, notification)
        }
    }

    fun playerServicePause() {
        Timber.d("playerServicePause():")

        player!!.pause()

        if (currentFile != null) {
            notificationManager!!.cancel(MEDIA_SERVICE_NOTIFICATION_ID)
            val notification = createNotifcation(MEDIA_SERVICE_NOTIFICATION_PLAY)
            notificationManager!!.notify(MEDIA_SERVICE_NOTIFICATION_ID, notification)
        }
    }

    fun playerServiceIsPlaying(): Boolean {
        Timber.d("playerServiceIsPlaying():")
        return player!!.isPlaying()
    }

    fun playerServiceIsPaused(): Boolean {
        Timber.d("playerServiceIsPaused():")
        return player!!.isPaused()
    }

    fun playerServiceSetTrackSpeed(speedFactor: Float, mastertempo: Boolean) {
        Timber.v("playerServiceSetTrackSpeed():")
        player!!.setSpeed(speedFactor, mastertempo)
    }

    fun playerServiceSeekTo(position: Double, andStop: Boolean) {
        player!!.seekTo(position, andStop)
    }

    fun playerServiceGetCurrentPositionMillis(): Double {
        Timber.v("playerServiceGetCurrentPositionMillis():")
        return player!!.getCurrentPositionMillis()
    }

    fun playerServiceGetDurationMillis(): Double {
        Timber.v("playerServiceGetDurationMillis():")
        return player!!.getDurationMillis()
    }

    fun playerServiceLoadTrackSync(file: File) {
        Timber.d("playerServiceLoadTrackSync():")

        currentFile = file
        player!!.loadTrackSync(file)
    }

    fun playerServiceLoadTrackASync(file: File) {
        Timber.d("playerServiceLoadTrackASync():")

        currentFile = file
        player!!.loadTrackASync(file)
    }

    fun getWaveformData(file: File) {
        Timber.d("getWaveformData():")
        player!!.getWaveformData(file)
    }

    //
    // Private
    //

    private fun createPlayer() {
        Timber.d("createPlayer():")

        player = QDeqPlayerSuperpoweredImpl()
        player!!.create(this)
    }

    @SuppressLint("SetTextI18n")
    private fun destroyPlayer() {
        Timber.d("destroyPlayer():")

        if (player != null) {
            player!!.destroy()
            player = null
        }
        notificationManager!!.cancel(MEDIA_SERVICE_NOTIFICATION_ID)
    }

    //
    // Binder class for the Service Connection
    //

    inner class MyBinder : Binder() {

        val service: QMediaPlayerService
            get() = this@QMediaPlayerService
    }

    //
    // Notification
    //


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {

        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val mChannel = NotificationChannel(
                getString(R.string.notification_channel_id), // The id of the channel.
                getString(R.string.notification_channel_name), // The user-visible name of the channel.
                NotificationManager.IMPORTANCE_LOW)

        // Configure the notification channel with
        // The user-visible description of the channel.
        mChannel.description = getString(R.string.notification_channel_description)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)

        mNotificationManager.createNotificationChannel(mChannel)
    }

    /**
     * Create a Notification and add an Action depending on type.
     *
     * @param type Action type of this Notification ("Play" or "Pause")
     * @return
     */
    private fun createNotifcation(type: Int): Notification {
        Timber.d("createNotification(): %d", type)

        val buttonIntent = Intent(this, NotificationBroadcastReceiver::class.java)
        buttonIntent.putExtra(EXTRA_NOTIFICATION_REQUESTCODE, type)
        buttonIntent.action = ACTION_PLAYER_TOGGLED

        val pendingButtonIntent = PendingIntent.getBroadcast(applicationContext,
                type, buttonIntent, 0)

        val contentIntent = Intent(this, PlayerActivity::class.java)
        contentIntent.putExtra(EXTRA_NOTIFICATION_REQUESTCODE, type)
        contentIntent.action = ACTION_NOTIFICATION_CLICKED

        val pendingContentIntent = PendingIntent.getActivity(applicationContext,
                0, contentIntent, 0)

        val dismissIntent = Intent(this, NotificationBroadcastReceiver::class.java)
        dismissIntent.putExtra(EXTRA_NOTIFICATION_REQUESTCODE, type)
        dismissIntent.action = ACTION_NOTIFICATION_DISMISSED

        val pendingDismissIntent = PendingIntent.getBroadcast(applicationContext,
                0, dismissIntent, 0)

        val remoteViews = RemoteViews(packageName, R.layout.notification_remote_view)

        remoteViews.setImageViewResource(R.id.notificationBigIcon, R.drawable.qplayer_launcher)
        remoteViews.setOnClickPendingIntent(R.id.notificationButtonPlayPause, pendingButtonIntent)

        if (type == MEDIA_SERVICE_NOTIFICATION_PLAY) {
            remoteViews.setImageViewResource(R.id.notificationButtonPlayPause, R.drawable.button_play)
        } else {
            remoteViews.setImageViewResource(R.id.notificationButtonPlayPause, R.drawable.button_pause)
        }

        remoteViews.setTextViewText(R.id.notificationTitle,
                if (currentFile != null) currentFile!!.name else "current track")

        val builder = NotificationCompat.Builder(
                this)
                .setChannelId(getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.qpl_status_bar_icon)
                .setContent(remoteViews)
                .setAutoCancel(false)
                .setDeleteIntent(pendingDismissIntent)
                .setContentIntent(pendingContentIntent)
                .setPriority(Notification.PRIORITY_MAX)

        return builder.build()
    }

    private class NotificationBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent?) {
            if (intent != null) {
                val action = intent.action

                if (action != null) {

                    if (action == ACTION_NOTIFICATION_DISMISSED) {
                        Timber.d("onReceive(): ACTION_NOTIFICATION_DISMISSED")

                        val mgr = LocalBroadcastManager.getInstance(context)
                        mgr.sendBroadcast(Intent().setAction(NOT_ACTION_NOTIFICATION_DISMISSED))
                    }

                    if (action == ACTION_PLAYER_TOGGLED) {
                        Timber.d("onReceive(): ACTION_PLAYER_TOGGLED")

                        val mgr = LocalBroadcastManager.getInstance(context!!)
                        mgr.sendBroadcast(Intent().setAction(NOT_ACTION_PLAYER_TOGGLED))
                    }

                    if (action == ACTION_NOTIFICATION_CLICKED) {
                        Timber.d("onReceive(): ACTION_NOTIFICATION_CLICKED")
                    }
                }
            }
        }

    }
}
