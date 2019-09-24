package org.qstuff.qplayer.datasource.mediaservice

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.Track
import org.qstuff.qplayer.datasource.model.TrackData
import org.qstuff.qplayer.player.PlayerActivity
import timber.log.Timber

/**
 * Created by Claus Chierici ( 3/2/17 )
 * Copyright (C) 2017
 * All rights reserved.
 */
class QMediaPlayerService : LifecycleService() {

    companion object {
        const val MEDIA_SERVICE_NOTIFICATION_ID = 1
        const val MEDIA_SERVICE_NOTIFICATION_PLAY = 2
        const val MEDIA_SERVICE_NOTIFICATION_PAUSE = 3

        const val ACTION_SERVICE_FOREGROUND_START = "ACTION_SERVICE_FOREGROUND_START"
        const val ACTION_SERVICE_FOREGROUND_STOP = "ACTION_SERVICE_FOREGROUND_STOP"

        const val ACTION_PLAYER_TOGGLED = "ACTION_PLAYER_TOGGLED"
        const val ACTION_NOTIFICATION_DISMISSED = "ACTION_NOTIFICATION_DISMISSED"
        const val ACTION_NOTIFICATION_CLICKED = "ACTION_NOTIFICATION_CLICKED"

        const val NOT_ACTION_PLAYER_TOGGLED = "NOT_ACTION_PLAYER_TOGGLED"
        const val NOT_ACTION_NOTIFICATION_DISMISSED = "NOT_ACTION_NOTIFICATION_DISMISSED"

        const val EXTRA_NOTIFICATION_REQUESTCODE = "EXTRA_NOTIFICATION_REQUESTCODE"
    }

    lateinit var player: QDeqPlayer
    private val binder = MyBinder()
    private var notificationManager: NotificationManager? = null

    private var currentTrack: Track? = null

    val isPrepared: Boolean
        get() = currentTrack?.trackStatus == Track.TrackStatus.PREPARED

    //
    // Service Lifecycle
    //

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        Timber.d("onBind")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        player = QDeqPlayerSuperpowered()
        player.create(this)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand(): intent: $intent, startId: $startId")

        if (intent.action == ACTION_SERVICE_FOREGROUND_START) {
            startForeground(1, createNotifcation(MEDIA_SERVICE_NOTIFICATION_PLAY))
        }
        if (intent.action == ACTION_SERVICE_FOREGROUND_STOP) {
            stopForeground(true)
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy():")

        destroyPlayer()
    }

    //
    // common public methods
    //

    fun play() {
        Timber.d("play():")
        if (currentTrack == null) return

        player.play()

        notificationManager!!.cancel(MEDIA_SERVICE_NOTIFICATION_ID)
        val notification = createNotifcation(MEDIA_SERVICE_NOTIFICATION_PAUSE)
        notificationManager!!.notify(MEDIA_SERVICE_NOTIFICATION_ID, notification)
    }

    fun pause() {
        Timber.d("pause():")
        if (currentTrack == null) return

        player.pause()

        notificationManager!!.cancel(MEDIA_SERVICE_NOTIFICATION_ID)
        val notification = createNotifcation(MEDIA_SERVICE_NOTIFICATION_PLAY)
        notificationManager!!.notify(MEDIA_SERVICE_NOTIFICATION_ID, notification)
    }

    fun stop() {
        player.stop()
    }

    fun isPlaying(): Boolean {
        Timber.d("isPlaying():")
        return player.isPlaying()
    }

    fun playerServiceIsPaused(): Boolean {
        Timber.d("playerServiceIsPaused():")
        return player.isPaused()
    }

    fun setTrackSpeed(speedFactor: Float, mastertempo: Boolean) {
        Timber.v("setTrackSpeed():")
        player.setSpeed(speedFactor, mastertempo)
    }

    fun seekTo(position: Double, andStop: Boolean) {
        player.seekTo(position, andStop)
    }

    fun getCurrentPositionMillis(): Long {
        Timber.v("getCurrentPositionMillis():")
        return player.getCurrentPositionMillis()
    }

    fun playerServiceGetDurationMillis(): Long {
        Timber.v("playerServiceGetDurationMillis():")
        return player.getDurationMillis()
    }

    fun playerServiceLoadTrackSync(track: Track) {
        Timber.d("playerServiceLoadTrackSync():")

        currentTrack = track
        player.loadTrackSync(track)
    }

    fun loadTrackASync(track: Track) {
        Timber.d("loadTrackASync():")

        currentTrack = track
        player.loadTrackASync(track)
    }

    fun getStatusObserver(): MutableLiveData<Track> = player.getStatusObserver()
    fun getWaveFormDataObserver(): MutableLiveData<TrackData> = player.getWaveFormDataObserver()

    //
    // Private
    //

    @SuppressLint("SetTextI18n")
    private fun destroyPlayer() {
        Timber.d("destroyPlayer():")

        player.destroy()
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

        remoteViews.setTextViewText(R.id.notificationTitle, currentTrack?.name)

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

    class NotificationBroadcastReceiver : BroadcastReceiver() {

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

                        val mgr = LocalBroadcastManager.getInstance(context)
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
