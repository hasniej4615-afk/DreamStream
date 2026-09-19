package com.duta.movie.data.local

import androidx.room.TypeConverter
import com.duta.movie.model.Episode
import com.duta.movie.model.VideoServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toList(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromMap(value: Map<String, String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toMap(value: String): Map<String, String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromEpisodeList(value: List<Episode>): String = Json.encodeToString(value)

    @TypeConverter
    fun toEpisodeList(value: String): List<Episode> = Json.decodeFromString(value)

    @TypeConverter
    fun fromServerList(value: List<VideoServer>): String = Json.encodeToString(value)

    @TypeConverter
    fun toServerList(value: String): List<VideoServer> = Json.decodeFromString(value)
}
