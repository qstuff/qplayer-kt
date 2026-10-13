package org.qstuff.qplayer.ui.playlists

import org.qstuff.qplayer.datasource.model.Track
import co.touchlab.kermit.Logger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private val log = Logger.withTag("M3uUtils")

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

    private const val EXTENDED_INFO_TAG = "#EXTM3U"
    private const val RECORD_TAG = "^[#][E|e][X|x][T|t][I|i][N|n][F|f].*"

    /**
     * Parses the M3U playlist [content] (entries relative to [directoryPath] or absolute) into the
     * tracks that exist and the ones that don't.
     */
    fun m3UParserGetTracks(
        content: String,
        directoryPath: String,
        isExistingFile: (path: String) -> Boolean = ::isExistingFile
    ): Pair<ArrayList<Track>, ArrayList<Track>> {
        val tracksFound = ArrayList<Track>()
        val notFound = ArrayList<Track>()

        content.lineSequence().forEach { line ->
            log.v { "m3UParserGetTracks(): line: $line" }

            if (!(line.equals(EXTENDED_INFO_TAG, ignoreCase = true) || line.trim { it <= ' ' } == "")) {

                if (line.matches(RECORD_TAG.toRegex())) {
                    log.v { "m3UParserGetTracks(): RECORD TAG ${line.replace("^(.*?),".toRegex(), "")}" }
                } else {
                    log.v { "m3UParserGetTracks(): ELSE ${line.replace("^(.*?),".toRegex(), "")}" }

                    val trackPath = if (line.contains("/")) line else "$directoryPath/$line"

                    if (isExistingFile(trackPath)) {
                        tracksFound.add(Track.fromPath(trackPath))
                    } else {
                        notFound.add(Track.fromPath(trackPath))
                    }
                }
            }
        }

        return Pair(tracksFound, notFound)
    }

    private fun isExistingFile(path: String) =
        SystemFileSystem.metadataOrNull(Path(path))?.isRegularFile == true
}