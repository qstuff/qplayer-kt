package org.qstuff.qplayer.player


import com.WarwickWestonWright.HGDialV2.HGDialInfo
import com.WarwickWestonWright.HGDialV2.HGDialV2
import com.WarwickWestonWright.HGDialV2.HGViewContainer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import kotlinx.android.synthetic.main.activity_player.*
import org.qstuff.qplayer.R
import org.qstuff.qplayer.contentbrowser.ContentListFragment
import java.util.*

/**
 *
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var jogWheelContainer: HGViewContainer
    private lateinit var jogWheelDial: HGDialV2
    private lateinit var jogWheelInterface: HGDialV2.IHGDial


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        setupJogWheel()
        setupContentSection()

    }

    override fun onStart() {
        super.onStart()

        jogWheelDial.registerCallback(jogWheelInterface)
    }

    override fun onStop() {
        super.onStop()

        jogWheelDial.unRegisterCallback()
    }

    //
    // private
    //

    private fun setupJogWheel() {
        jogWheelContainer = com.WarwickWestonWright.HGDialV2.HGViewContainer(R.drawable.qpl_btn_wheel_ohne_rand01, jogWheel)
        jogWheelDial = jogWheelContainer.hgDialV2Active
        jogWheelInterface = (object: HGDialV2.IHGDial {
            override fun onDown(p0: HGDialInfo?) {
            }

            override fun onPointerDown(p0: HGDialInfo?) {
            }

            override fun onUp(p0: HGDialInfo?) {
                jogWheelDial.doManualTextureDial(0.0)

                // FIXME: onJogWheelMoved.onNext(0f)
            }

            override fun onMove(hgDialInfo: HGDialInfo?) {
                val angle = (hgDialInfo.getTextureAngle() * 100).toFloat()

                // FIXME: onJogWheelMoved.onNext(angle)
            }

            override fun onPointerUp(p0: HGDialInfo?) {
            }
        })
    }

    private fun setupContentSection() {

        contentPager.apply {
            adapter = ContentPagerAdapter(supportFragmentManager)
            offscreenPageLimit = 2
        }
        contentTabbar.apply {
            setViewPager(contentPager)
        }
    }

    /**
     *
     */
    private class ContentPagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

        override fun getItem(position: Int): Fragment {
            val fragment: Fragment
            when(position) {
                0 -> {
                    fragment = ContentListFragment.newInstance()
                }
                1 -> {
                    fragment = ContentListFragment.newInstance()
                }
                2 -> {
                    fragment = ContentListFragment.newInstance()
                }
                else -> {
                    fragment = null!!
                }
            }
            return fragment
        }

        override fun getCount() = 3

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> {
                    return "queue"
                }
                1 -> {
                    return "filebrowser"
                }
                2 -> {
                    return "playlists"
                }
            }
            return "OBJECT " + (position + 1)
        }

        override fun getItemPosition(`object`: Any) = -2
    }
}
