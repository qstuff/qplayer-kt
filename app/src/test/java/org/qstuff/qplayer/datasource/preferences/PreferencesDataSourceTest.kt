package org.qstuff.qplayer.datasource.preferences

import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.qstuff.qplayer.datasource.model.Track

class PreferencesDataSourceTest {

    // Written by the former Gson code (copied from a device): alphabetical keys, incl. the
    // runtime-only trackStatus that kotlinx.serialization no longer stores.
    private val gsonTrack = """{"cuePosition":0,"duration":593841,"isAutoplay":false,""" +
        """"name":"Sweet N Candy - Movement (Trouble Edit).mp3","playPosition":0,"playlistName":"",""" +
        """"trackStatus":"PREPARED","track_id":0,""" +
        """"uri":"/storage/6262-3034/tipsy_2019/proggressive/Sweet N Candy - Movement (Trouble Edit).mp3"}"""

    @Test
    fun `reads the selected track stored by Gson`() {
        val settings = MapSettings(PreferencesDataSource.PREF_SELECTED_TRACK to gsonTrack)

        val track = PreferencesDataSource(settings).readSelectedTrack()!!

        assertEquals("Sweet N Candy - Movement (Trouble Edit).mp3", track.name)
        assertEquals(593841L, track.duration)
        assertEquals("/storage/6262-3034/tipsy_2019/proggressive/Sweet N Candy - Movement (Trouble Edit).mp3", track.uri)
        assertEquals(Track.TrackStatus.UNDEFINED, track.trackStatus)
    }

    @Test
    fun `reads the queue stored by Gson`() {
        val settings = MapSettings(PreferencesDataSource.PREF_QUEUE_LIST to "[$gsonTrack,$gsonTrack]")

        val tracks = PreferencesDataSource(settings).readTrackList(PreferencesDataSource.PREF_QUEUE_LIST)!!

        assertEquals(2, tracks.size)
        assertEquals(593841L, tracks[1].duration)
    }

    @Test
    fun `queue round trip`() {
        val prefs = PreferencesDataSource(MapSettings())
        val track = Track(name = "a.mp3", uri = "/music/a.mp3", duration = 1000, cuePosition = 250)

        prefs.saveTrackList(PreferencesDataSource.PREF_QUEUE_LIST, arrayListOf(track))

        assertEquals(listOf(track), prefs.readTrackList(PreferencesDataSource.PREF_QUEUE_LIST))
    }

    @Test
    fun `unreadable stored queue gives null instead of crashing`() {
        val settings = MapSettings(PreferencesDataSource.PREF_QUEUE_LIST to "not json")

        assertNull(PreferencesDataSource(settings).readTrackList(PreferencesDataSource.PREF_QUEUE_LIST))
    }

    @Test
    fun `jog wheel values keep their string format`() {
        val settings = MapSettings()
        val prefs = PreferencesDataSource(settings)

        prefs.setJogWheelSensitivity(20)

        assertEquals("20", settings.getStringOrNull(PreferencesDataSource.PREFS_JOG_WHEEL_SENSITIVITY))
        assertEquals(20, prefs.getJogWheelSensitivity())
    }
}
