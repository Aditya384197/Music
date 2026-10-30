package com.aditya.music.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

private const val ALBUM_ART_BASE = "content://media/external/audio/albumart"

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val relativePath: String,
    val dateAddedSeconds: Long,
    val uri: Uri
) {
    val artworkUri: Uri?
        get() = if (albumId > 0L) ContentUris.withAppendedId(Uri.parse(ALBUM_ART_BASE), albumId) else null

    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "Unknown title" })
            .setArtist(artist.ifBlank { "Unknown artist" })
            .setAlbumTitle(album.ifBlank { "Unknown album" })
            .apply { artworkUri?.let(::setArtworkUri) }
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
