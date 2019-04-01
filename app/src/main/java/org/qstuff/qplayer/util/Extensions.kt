package org.qstuff.qplayer.util

import android.content.Context
import android.widget.Toast

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */

fun Context.shortToast(message: String, length: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(this, message, length).show()

fun Context.longToast(message: String, length: Int = Toast.LENGTH_LONG) =
        Toast.makeText(this, message, length).show()
