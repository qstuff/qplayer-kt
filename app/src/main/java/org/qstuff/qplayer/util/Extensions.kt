package org.qstuff.qplayer.util

import android.content.Context
import android.text.TextUtils.replace
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener.add
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 4/1/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */

fun Context.shortToast(message: String, length: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(this, message, length).show()

fun Context.longToast(message: String, length: Int = Toast.LENGTH_LONG) =
        Toast.makeText(this, message, length).show()

/**
 * Returns the next enum value as declared in the class. If this is the last enum declared,
   this will wrap around to return the first declared enum.
 *
 * @param values an optional array of enum values to be used; this can be used in order to
 * cache access to the values() array of the enum type and reduce allocations if this is
 * called frequently.
 */
inline fun <reified T : Enum<T>> Enum<T>.next(values: Array<T> = enumValues()) =
    values[(ordinal + 1) % values.size]

//
// Simplify Fragment transactions
//
fun AppCompatActivity.addFragment(fragment: Fragment, frameId: Int){
   supportFragmentManager.inTransaction { add(frameId, fragment) }
}

fun AppCompatActivity.replaceFragment(fragment: Fragment, frameId: Int) {
   supportFragmentManager.inTransaction{ replace(frameId, fragment) }
}

inline fun FragmentManager.inTransaction(func: FragmentTransaction.() -> FragmentTransaction) {
   beginTransaction().func().commit()
}