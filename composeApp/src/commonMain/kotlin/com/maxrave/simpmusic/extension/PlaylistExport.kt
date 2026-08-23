package com.maxrave.simpmusic.extension

import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data classes for playlist JSON export.
 */
@Serializable
data class PlaylistExportData(
    val name: String,
    val songs: List<SongExportEntry>,
)

@Serializable
data class SongExportEntry(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long = 0,
    val videoId: String,
)

/**
 * Converts a local playlist and its songs into a serializable export format.
 */
fun LocalPlaylistEntity.toExportData(songs: List<SongEntity>): PlaylistExportData {
    return PlaylistExportData(
        name = title,
        songs = songs.map { song ->
            SongExportEntry(
                title = song.title,
                artist = song.artistName ?: "",
                album = song.albumName,
                duration = song.duration,
                videoId = song.videoId,
            )
        },
    )
}

/** Pretty-printed JSON for a single playlist. */
private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

/**
 * Serializes a playlist export data object to pretty-printed JSON.
 */
fun PlaylistExportData.toJsonString(): String = exportJson.encodeToString(this)

/**
 * Serializes a list of playlist export data objects to pretty-printed JSON array.
 */
fun List<PlaylistExportData>.toJsonString(): String = exportJson.encodeToString(this)