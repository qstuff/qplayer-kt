package org.qstuff.qplayer.datasource.room

/*
 * Created by claus on 19.12.17.
 */

import androidx.room.TypeConverter

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.ArrayList

object StringArrayConverter {

    @TypeConverter
    fun fromString(value: String) =
        Gson().fromJson<List<String>>(value, object : TypeToken<ArrayList<String>>() {}.type)

    @TypeConverter
    fun fromList(list: List<String>) = Gson().toJson(list)
}
