package org.qstuff.qplayer.queue

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.standalone.KoinComponent
import org.qstuff.qplayer.datasource.model.Track
import java.io.File

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/11/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class QueueViewModel: ViewModel(), KoinComponent {

    var trackList: MutableLiveData<List<Track>> = MutableLiveData()


}