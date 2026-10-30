package com.aditya.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS = "aditya_music_library"

data class MusicPlaylist(val name: String, val songIds: List<Long>)

object FavoritesStore {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ids(context: Context): Set<Long> = prefs(context).getStringSet("favorites", emptySet())
        .orEmpty().mapNotNull(String::toLongOrNull).toSet()

    fun contains(context: Context, songId: Long): Boolean = songId in ids(context)

    fun toggle(context: Context, songId: Long): Boolean {
        val current = ids(context).toMutableSet()
        val added = if (current.add(songId)) true else {
            current.remove(songId)
            false
        }
        prefs(context).edit().putStringSet("favorites", current.map(Long::toString).toSet()).apply()
        return added
    }
}

object PlaylistStore {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<MusicPlaylist> {
        val raw = prefs(context).getString("playlists", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val ids = item.optJSONArray("ids") ?: JSONArray()
                    val songIds = buildList {
                        for (j in 0 until ids.length()) {
                            ids.optLong(j).takeIf { it > 0L }?.let { if (it !in this) add(it) }
                        }
                    }
                    val name = item.optString("name").trim()
                    if (name.isNotEmpty()) add(MusicPlaylist(name, songIds))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun create(context: Context, name: String): Boolean {
        val cleanName = name.trim().take(60)
        if (cleanName.isEmpty() || all(context).any { it.name.equals(cleanName, ignoreCase = true) }) return false
        save(context, all(context) + MusicPlaylist(cleanName, emptyList()))
        return true
    }

    fun addSong(context: Context, playlistName: String, songId: Long) {
        val playlists = all(context).map { playlist ->
            if (playlist.name == playlistName) playlist.copy(songIds = (playlist.songIds + songId).distinct()) else playlist
        }
        save(context, playlists)
    }

    fun removeSong(context: Context, playlistName: String, songId: Long) {
        val playlists = all(context).map { playlist ->
            if (playlist.name == playlistName) playlist.copy(songIds = playlist.songIds.filterNot { it == songId }) else playlist
        }
        save(context, playlists)
    }

    fun delete(context: Context, playlistName: String) {
        save(context, all(context).filterNot { it.name == playlistName })
    }

    private fun save(context: Context, playlists: List<MusicPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val ids = JSONArray()
            playlist.songIds.forEach { songId -> ids.put(songId) }
            array.put(JSONObject().put("name", playlist.name).put("ids", ids))
        }
        prefs(context).edit().putString("playlists", array.toString()).apply()
    }
}

object HistoryStore {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(context: Context, songId: Long) {
        val raw = prefs(context).getString("history", "[]") ?: "[]"
        val updated = runCatching {
            val old = JSONArray(raw)
            val result = JSONArray().put(songId)
            for (i in 0 until old.length()) {
                val id = old.optLong(i)
                if (id > 0L && id != songId && result.length() < 100) result.put(id)
            }
            result
        }.getOrElse { JSONArray().put(songId) }
        prefs(context).edit().putString("history", updated.toString()).apply()
    }

    fun recentIds(context: Context): List<Long> {
        val raw = prefs(context).getString("history", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optLong(it) }.filter { it > 0L }
        }.getOrDefault(emptyList())
    }
}
