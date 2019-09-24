package org.qstuff.qplayer.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.fragment_equalizer.*
import org.qstuff.qplayer.R
import org.qstuff.qplayer.player.PlayerViewModel
import timber.log.Timber


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

    private lateinit var equalizerViewModel: EqualizerViewModel
    private lateinit var playerViewModel: PlayerViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        equalizerViewModel = ViewModelProviders.of(activity!!).get(EqualizerViewModel::class.java)
        playerViewModel = ViewModelProviders.of(activity!!).get(PlayerViewModel::class.java)

        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupInteractionListeners()
    }

    //
    // ViewModel Observers
    //

    private fun setupObservers() {

        equalizerViewModel.bandOneSliderValue.observe(this, Observer { bandOneValue ->
            equalizerBandOne.setNewProgress(bandOneValue, false)
        })

        equalizerViewModel.bandOneSliderValue.observe(this, Observer { bandOneValue ->
            equalizerBandOne.setNewProgress(bandOneValue, false)
        })

        equalizerViewModel.bandOneSliderValue.observe(this, Observer { bandOneValue ->
            equalizerBandOne.setNewProgress(bandOneValue, false)
        })

    }

    //
    // Interaction Listeners
    //

    private fun setupInteractionListeners() {

        equalizerBandOne.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Timber.d("equalizerBandOne: onProgressChanged(): $progress")
                equalizerViewModel.onBandOneChanged(progress)

            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        equalizerBandTwo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Timber.d("equalizerBandTwo: onProgressChanged(): $progress")
                equalizerViewModel.onBandTwoChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        equalizerBandThree.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Timber.d("equalizerBandThree: onProgressChanged(): $progress")
                equalizerViewModel.onBandThreeChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}