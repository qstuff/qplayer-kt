package org.qstuff.qplayer

import android.app.Application
import android.util.DisplayMetrics
import androidx.room.Room
import org.koin.android.ext.android.startKoin
import org.koin.dsl.module.module
import org.qstuff.qplayer.datasource.preferences.PreferencesDataSource
import org.qstuff.qplayer.datasource.room.QDeqDatabase
import org.qstuff.qplayer.datasource.room.RoomDataSource
import timber.log.Timber

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/3/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QDeqApplication : Application() {

    private lateinit var metrics: DisplayMetrics

    override fun onCreate() {
        super.onCreate()

        startKoin(this, listOf(
                preferencesDataSource,
                roomDatabaseModule,
                roomDataSource
        ))

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    //
    // Koin Modules
    //

    private val preferencesDataSource = module (definition = {
        single {
            PreferencesDataSource(applicationContext)
        }
    })

    private val roomDatabaseModule = module ( definition = {
        single {
            Room.databaseBuilder(applicationContext, QDeqDatabase::class.java, "qplayer")
                    .build()
        }
    })

    private val roomDataSource = module (definition = {
        single {
            RoomDataSource()
        }
    })

    //
    // Debug
    //

    fun getDPI(): String {

        val str = StringBuilder()
        metrics = resources.displayMetrics
        str.append(metrics.densityDpi)
        str.append(" dpi ")

        if (metrics.densityDpi >= DisplayMetrics.DENSITY_LOW && metrics.densityDpi < DisplayMetrics.DENSITY_MEDIUM)
            str.append("(LDPI)")
        if (metrics.densityDpi >= DisplayMetrics.DENSITY_MEDIUM && metrics.densityDpi < DisplayMetrics.DENSITY_HIGH)
            str.append("(MDPI)")
        if (metrics.densityDpi >= DisplayMetrics.DENSITY_HIGH && metrics.densityDpi < DisplayMetrics.DENSITY_XHIGH)
            str.append("(HDPI)")
        if (metrics.densityDpi >= DisplayMetrics.DENSITY_XHIGH && metrics.densityDpi < DisplayMetrics.DENSITY_XXHIGH)
            str.append("(XHDPI)")
        if (metrics.densityDpi >= DisplayMetrics.DENSITY_XXHIGH && metrics.densityDpi < DisplayMetrics.DENSITY_XXXHIGH)
            str.append("(XXHDPI)")
        if (metrics.densityDpi >= DisplayMetrics.DENSITY_XXXHIGH)
            str.append("(XXXHDPI)")

        return str.toString()
    }

    fun getDisplayDPI(): Int {

        metrics = resources.displayMetrics
        return metrics.densityDpi
    }

    fun getDisplayResolution(): String {

        val str = StringBuilder()
        metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        str.append(width)
        str.append("x")
        str.append(height)
        return str.toString()
    }
}