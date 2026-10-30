package com.aditya.music.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aditya.music.data.FavoritesStore
import com.aditya.music.data.HistoryStore
import com.aditya.music.data.MusicPlaylist
import com.aditya.music.data.MusicRepository
import com.aditya.music.data.PlaylistStore
import com.aditya.music.data.Song
import com.aditya.music.data.formatDuration
import com.aditya.music.playback.PlaybackService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {
    private lateinit var searchField: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var list: RecyclerView
    private lateinit var summary: TextView
    private lateinit var contextTitle: TextView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: ImageButton
    private lateinit var adapter: LibraryAdapter

    private val allSongs = mutableListOf<Song>()
    private var visibleSongs: List<Song> = emptyList()
    private var selectedTab = TAB_SONGS
    private var pickerMode = false
    private var collectionIds: Set<Long>? = null
    private var collectionTitle: String? = null
    private var openedPlaylistName: String? = null
    private val chips = LinkedHashMap<String, Chip>()
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestNotificationPermission()
            loadLibrary()
        } else {
            showPermissionMessage()
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickerMode = intent.action == Intent.ACTION_GET_CONTENT || intent.action == Intent.ACTION_PICK
        buildScreen()
        connectPlaybackController()
        ensureAudioPermission()
    }

    override fun onResume() {
        super.onResume()
        if (hasAudioPermission() && allSongs.isNotEmpty()) renderLibrary()
        updateMiniPlayer()
    }

    private fun buildScreen() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 17, 22))
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "ADITYA MUSIC"
            textSize = 20f
            letterSpacing = 0.07f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val refresh = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_popup_sync)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Refresh music library"
            setOnClickListener { loadLibrary() }
        }
        top.addView(brand)
        top.addView(refresh)
        root.addView(top)

        searchField = EditText(this).apply {
            hint = "Search songs, albums, artists"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(145, 149, 163))
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
            compoundDrawablePadding = dp(10)
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.rgb(30, 32, 40))
        }
        root.addView(searchField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(8)
            bottomMargin = dp(10)
        })

        val horizontal = ScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = dp(6)
        }
        listOf(TAB_SONGS, TAB_ALBUMS, TAB_ARTISTS, TAB_FOLDERS, TAB_PLAYLISTS, TAB_FAVORITES, TAB_RECENT).forEach { tab ->
            val chip = Chip(this).apply {
                text = tab
                isCheckable = true
                isChecked = tab == selectedTab
                setTextColor(Color.WHITE)
                chipBackgroundColor = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.rgb(73, 82, 116), Color.rgb(43, 46, 57))
                )
                checkedIcon = null
                setOnClickListener {
                    selectedTab = tab
                    collectionIds = null
                    collectionTitle = null
                    openedPlaylistName = null
                    renderLibrary()
                }
            }
            chips[tab] = chip
            chipGroup.addView(chip)
        }
        horizontal.addView(chipGroup)
        root.addView(horizontal)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        val headerColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        contextTitle = TextView(this).apply {
            text = "Your library"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        summary = TextView(this).apply {
            text = "Loading music…"
            textSize = 12f
            setTextColor(Color.rgb(164, 168, 181))
        }
        headerColumn.addView(contextTitle)
        headerColumn.addView(summary)
        header.addView(headerColumn)
        val playAll = MaterialButton(this).apply {
            text = "Play all"
            isAllCaps = false
            setOnClickListener { startPlayback(visibleSongs, 0, false) }
        }
        val shuffle = MaterialButton(this).apply {
            text = "Shuffle"
            isAllCaps = false
            icon = getDrawable(android.R.drawable.ic_menu_rotate)
            setOnClickListener { startPlayback(visibleSongs, 0, true) }
        }
        header.addView(playAll)
        header.addView(shuffle)
        if (pickerMode) {
            playAll.visibility = View.GONE
            shuffle.visibility = View.GONE
        }
        root.addView(header)

        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(8))
        }
        adapter = LibraryAdapter(
            onTrackClick = { song ->
                if (pickerMode) returnSelectedSong(song)
                else startPlayback(visibleSongs, visibleSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0), false)
            },
            onCollectionClick = ::openCollection,
            onMoreClick = ::showTrackMenu,
            onCollectionMoreClick = ::showCollectionMenu
        )
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val mini = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.rgb(40, 43, 54))
            setOnClickListener { openPlayer() }
        }
        val miniRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(8), dp(7))
        }
        val miniIcon = TextView(this).apply {
            text = "♫"
            textSize = 27f
            setTextColor(Color.rgb(190, 203, 255))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        val miniTextColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
        }
        miniTitle = TextView(this).apply {
            text = "Nothing playing"
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
        }
        miniArtist = TextView(this).apply {
            text = "Choose a song to start"
            setTextColor(Color.rgb(174, 178, 191))
            textSize = 12f
            maxLines = 1
        }
        miniTextColumn.addView(miniTitle)
        miniTextColumn.addView(miniArtist)
        miniPlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Play or pause"
            setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        }
        miniRow.addView(miniIcon)
        miniRow.addView(miniTextColumn)
        miniRow.addView(miniPlay)
        mini.addView(miniRow)
        mini.visibility = if (pickerMode) View.GONE else View.VISIBLE
        root.addView(mini, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
            bottomMargin = dp(2)
        })

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(16), bars.top + dp(8), dp(16), bars.bottom + dp(8))
            insets
        }
        setContentView(root)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderLibrary()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun connectPlaybackController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updateMiniPlayer()
                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = updateMiniPlayer()
                    override fun onPlaybackStateChanged(playbackState: Int) = updateMiniPlayer()
                })
                updateMiniPlayer()
            }.onFailure { Toast.makeText(this, "Playback service could not start", Toast.LENGTH_LONG).show() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun ensureAudioPermission() {
        if (hasAudioPermission()) {
            requestNotificationPermission()
            loadLibrary()
        } else {
            audioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showPermissionMessage() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow music access")
            .setMessage("Aditya Music needs audio access to find and play music stored on this device. You can grant it in App settings.")
            .setPositiveButton("Try again") { _, _ -> audioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun loadLibrary() {
        if (!hasAudioPermission()) return
        runCatching { MusicRepository(this).loadSongs() }
            .onSuccess { songs ->
                allSongs.clear()
                allSongs.addAll(songs)
                collectionIds = null
                collectionTitle = null
                openedPlaylistName = null
                renderLibrary()
            }
            .onFailure { error ->
                Toast.makeText(this, "Could not read music: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
    }

    private fun renderLibrary() {
        val query = searchField.text?.toString()?.trim().orEmpty()
        val searchMatches: (Song) -> Boolean = { song ->
            query.isBlank() || listOf(song.title, song.artist, song.album, song.relativePath)
                .any { it.contains(query, ignoreCase = true) }
        }
        val filteredBase = allSongs.filter(searchMatches)

        val entries: List<LibraryEntry> = when (selectedTab) {
            TAB_ALBUMS -> filteredBase.groupBy { "${it.album.lowercase()}|${it.artist.lowercase()}" }
                .values.map { group ->
                    val first = group.first()
                    LibraryEntry.Collection(
                        first.album.ifBlank { "Unknown album" },
                        "${first.artist.ifBlank { "Unknown artist" }}  •  ${group.size} tracks",
                        group.map { it.id }.toSet(), first.artworkUri
                    )
                }.sortedBy { it.title.lowercase() }
            TAB_ARTISTS -> filteredBase.groupBy { it.artist.ifBlank { "Unknown artist" } }
                .map { (artist, group) ->
                    LibraryEntry.Collection(artist, "${group.size} tracks", group.map { it.id }.toSet(), group.firstOrNull()?.artworkUri)
                }.sortedBy { it.title.lowercase() }
            TAB_FOLDERS -> filteredBase.groupBy { it.relativePath.ifBlank { "Unknown folder" } }
                .map { (path, group) ->
                    val display = path.trimEnd('/').substringAfterLast('/').ifBlank { "Music" }
                    LibraryEntry.Collection(display, "$path  •  ${group.size} tracks", group.map { it.id }.toSet(), group.firstOrNull()?.artworkUri)
                }.sortedBy { it.title.lowercase() }
            TAB_PLAYLISTS -> PlaylistStore.all(this).map { playlist ->
                val available = playlist.songIds.intersect(allSongs.map { it.id }.toSet())
                LibraryEntry.Collection(playlist.name, "${available.size} tracks  •  Playlist", available, canDelete = true)
            }.filter { it.title.contains(query, ignoreCase = true) }
            TAB_FAVORITES -> {
                val ids = FavoritesStore.ids(this)
                filteredBase.filter { it.id in ids }.map(LibraryEntry::Track)
            }
            TAB_RECENT -> {
                val byId = filteredBase.associateBy { it.id }
                HistoryStore.recentIds(this).mapNotNull(byId::get).map(LibraryEntry::Track)
            }
            else -> {
                val base = openedPlaylistName?.let { name ->
                    val byId = filteredBase.associateBy { it.id }
                    PlaylistStore.all(this).firstOrNull { it.name == name }
                        ?.songIds?.mapNotNull(byId::get).orEmpty()
                } ?: (collectionIds?.let { ids -> filteredBase.filter { it.id in ids } } ?: filteredBase)
                base.map(LibraryEntry::Track)
            }
        }

        visibleSongs = when (selectedTab) {
            TAB_PLAYLISTS -> {
                val allIds = PlaylistStore.all(this).flatMap { it.songIds }.toSet()
                filteredBase.filter { it.id in allIds }
            }
            TAB_FAVORITES -> filteredBase.filter { it.id in FavoritesStore.ids(this) }
            TAB_RECENT -> {
                val byId = filteredBase.associateBy { it.id }
                HistoryStore.recentIds(this).mapNotNull(byId::get)
            }
            else -> openedPlaylistName?.let { name ->
                val byId = filteredBase.associateBy { it.id }
                PlaylistStore.all(this).firstOrNull { it.name == name }
                    ?.songIds?.mapNotNull(byId::get).orEmpty()
            } ?: (collectionIds?.let { ids -> filteredBase.filter { it.id in ids } } ?: filteredBase)
        }
        adapter.submit(entries)
        val count = when (selectedTab) {
            TAB_ALBUMS, TAB_ARTISTS, TAB_FOLDERS, TAB_PLAYLISTS -> entries.size
            else -> visibleSongs.size
        }
        summary.text = when (selectedTab) {
            TAB_PLAYLISTS -> "$count playlists"
            TAB_ALBUMS -> "$count albums"
            TAB_ARTISTS -> "$count artists"
            TAB_FOLDERS -> "$count folders"
            else -> "$count tracks"
        }
        contextTitle.text = collectionTitle ?: when (selectedTab) {
            TAB_SONGS -> "Your songs"
            TAB_ALBUMS -> "Albums"
            TAB_ARTISTS -> "Artists"
            TAB_FOLDERS -> "Folders"
            TAB_PLAYLISTS -> "Playlists"
            TAB_FAVORITES -> "Favorites"
            else -> "Recently played"
        }
        if (selectedTab == TAB_PLAYLISTS) showCreatePlaylistButtonInHeader() else removeCreatePlaylistButton()
        if (entries.isEmpty()) {
            contextTitle.text = if (selectedTab == TAB_SONGS && allSongs.isEmpty()) "No music found" else contextTitle.text
            summary.text = if (allSongs.isEmpty()) "Add audio files to your device, then refresh" else "Nothing to show here"
        }
    }

    private var createPlaylistButton: MaterialButton? = null

    private fun showCreatePlaylistButtonInHeader() {
        if (createPlaylistButton != null) return
        val button = MaterialButton(this).apply {
            text = "+ New"
            isAllCaps = false
            setOnClickListener { createPlaylistDialog() }
        }
        val parent = summary.parent as? LinearLayout ?: return
        val header = parent.parent as? LinearLayout ?: return
        header.addView(button)
        createPlaylistButton = button
    }

    private fun removeCreatePlaylistButton() {
        createPlaylistButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        createPlaylistButton = null
    }

    private fun openCollection(collection: LibraryEntry.Collection) {
        openedPlaylistName = if (selectedTab == TAB_PLAYLISTS) collection.title else null
        collectionIds = collection.songIds
        collectionTitle = collection.title
        selectedTab = TAB_SONGS
        chips[TAB_SONGS]?.isChecked = true
        renderLibrary()
    }

    private fun createPlaylistDialog() {
        val input = TextInputEditText(this).apply {
            hint = "Playlist name"
            setSingleLine(true)
        }
        val container = LinearLayout(this).apply {
            setPadding(24, 8, 24, 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Create playlist")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString().orEmpty()
                if (PlaylistStore.create(this, name)) renderLibrary()
                else Toast.makeText(this, "Enter a unique playlist name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCollectionMenu(collection: LibraryEntry.Collection, anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add("Delete playlist")
        popup.setOnMenuItemClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete ${collection.title}?")
                .setMessage("The playlist will be removed. Your audio files will not be deleted.")
                .setPositiveButton("Delete") { _, _ ->
                    PlaylistStore.delete(this, collection.title)
                    renderLibrary()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
        popup.show()
    }

    private fun showTrackMenu(song: Song, anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        val favorite = FavoritesStore.contains(this, song.id)
        popup.menu.add(if (favorite) "Remove from favorites" else "Add to favorites")
        popup.menu.add("Add to playlist")
        popup.menu.add("Add next in queue")
        popup.menu.add("Share audio")
        popup.menu.add("Track information")
        openedPlaylistName?.let { popup.menu.add("Remove from this playlist") }
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Add to favorites", "Remove from favorites" -> {
                    FavoritesStore.toggle(this, song.id)
                    renderLibrary()
                }
                "Add to playlist" -> choosePlaylist(song)
                "Add next in queue" -> addNext(song)
                "Share audio" -> shareSong(song)
                "Track information" -> showSongInfo(song)
                "Remove from this playlist" -> {
                    openedPlaylistName?.let { PlaylistStore.removeSong(this, it, song.id) }
                    renderLibrary()
                }
            }
            true
        }
        popup.show()
    }

    private fun choosePlaylist(song: Song) {
        val playlists = PlaylistStore.all(this)
        if (playlists.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage("You have no playlists yet. Create one now?")
                .setPositiveButton("Create") { _, _ -> createPlaylistDialog() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Add to playlist")
            .setItems(playlists.map { it.name }.toTypedArray()) { _, which ->
                PlaylistStore.addSong(this, playlists[which].name, song.id)
                Toast.makeText(this, "Added to ${playlists[which].name}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun addNext(song: Song) {
        val player = controller
        if (player == null) {
            startPlayback(listOf(song), 0, false)
            return
        }
        val index = player.currentMediaItemIndex
        if (player.mediaItemCount == 0 || index < 0) {
            player.setMediaItem(song.toMediaItem())
            player.prepare()
            player.play()
        } else {
            player.addMediaItem(index + 1, song.toMediaItem())
        }
        Toast.makeText(this, "Added next in queue", Toast.LENGTH_SHORT).show()
    }

    private fun shareSong(song: Song) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, song.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share audio"))
    }

    private fun showSongInfo(song: Song) {
        val sizeMb = song.sizeBytes / (1024.0 * 1024.0)
        MaterialAlertDialogBuilder(this)
            .setTitle(song.title)
            .setMessage("Artist: ${song.artist}\nAlbum: ${song.album}\nDuration: ${formatDuration(song.durationMs)}\nFile size: %.2f MB\nFolder: %s".format(sizeMb, song.relativePath))
            .setPositiveButton("Close", null)
            .show()
    }

    private fun startPlayback(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        if (songs.isEmpty()) {
            Toast.makeText(this, "No songs to play", Toast.LENGTH_SHORT).show()
            return
        }
        val player = controller
        if (player == null) {
            Toast.makeText(this, "Player is connecting. Try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        val mediaItems = songs.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
        songs.getOrNull(startIndex)?.let { HistoryStore.record(this, it.id) }
        openPlayer()
    }

    private fun returnSelectedSong(song: Song) {
        val result = Intent().apply {
            setDataAndType(song.uri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun openPlayer() {
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun updateMiniPlayer() {
        val current = controller?.currentMediaItem
        val metadata = current?.mediaMetadata
        miniTitle.text = metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Nothing playing"
        miniArtist.text = metadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Choose a song to start"
        miniPlay.setImageResource(if (controller?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }

    companion object {
        const val TAB_SONGS = "Songs"
        const val TAB_ALBUMS = "Albums"
        const val TAB_ARTISTS = "Artists"
        const val TAB_FOLDERS = "Folders"
        const val TAB_PLAYLISTS = "Playlists"
        const val TAB_FAVORITES = "Favorites"
        const val TAB_RECENT = "Recent"
    }
}
