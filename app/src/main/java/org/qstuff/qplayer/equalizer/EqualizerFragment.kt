package org.qstuff.qplayer.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.qstuff.qplayer.R


/**
 * Created by Claus Chierici (claus@qstuff.org)
 * on 09/13/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
class EqualizerFragment: Fragment() {

    companion object {

        fun newInstance(): EqualizerFragment {
            return EqualizerFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

}