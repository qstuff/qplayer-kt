package org.qstuff.qplayer.playlists

import android.content.Context
import org.qstuff.qplayer.datasource.model.Track

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

import timber.log.Timber

/*
 * For parts of the parser code:
 *
 * Copyright 2014 William Seemann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by Claus Chierici (claus@qstuff.org)
 * on 4/22/18
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */
object M3uUtils {

    private val EXTENDED_INFO_TAG = "#EXTM3U"
    private val RECORD_TAG = "^[#][E|e][X|x][T|t][I|i][N|n][F|f].*"

    @Throws(IOException::class)
    fun m3UParserGetTracks(fis: FileInputStream, directoryPath: String):
            Pair<ArrayList<Track>, ArrayList<Track>> {
        val tracksFound = ArrayList<Track>()
        val notFound = ArrayList<Track>()

        val reader = BufferedReader(InputStreamReader(fis))
        reader.useLines {
            it.toList().forEach { line ->

                Timber.v("m3UParserGetTracks(): line: %s", line)

                if (!(line.equals(EXTENDED_INFO_TAG, ignoreCase = true) || line.trim { it <= ' ' } == "")) {

                    if (line.matches(RECORD_TAG.toRegex())) {
                        Timber.v("m3UParserGetTracks(): RECORD TAG %s", line.replace("^(.*?),".toRegex(), ""))
                    } else {
                        Timber.v("m3UParserGetTracks(): ELSE %s", line.replace("^(.*?),".toRegex(), ""))

                        val trackFile: File

                        if (line.contains("/")) {
                            trackFile = File(line)
                        } else {
                            trackFile = File("$directoryPath/$line")
                        }

                        if (trackFile.exists() && trackFile.isFile) {
                            tracksFound.add(Track(trackFile))
                        } else {
                            notFound.add(Track(trackFile))
                        }
                    }
                }
            }
        }

        val tracksNotFound = StringBuilder()
        for (track in notFound) {
            tracksNotFound.append("\n")
            tracksNotFound.append(track.name)
        }
        return Pair(tracksFound, notFound)
    }
}