package com.aditya.music.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aditya.music.data.FavoritesStore
import com.aditya.music.data.HistoryStore
import com.aditya.music.data.formatDuration
import com.aditya.music.playback.PlaybackService
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : AppCompatActivity() {
    private lateinit var artwork: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var elapsed: TextView
    private lateinit var total: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playPause: ImageButton
    private lateinit var shuffleButton: MaterialButton
    private lateinit var repeatButton: MaterialButton
    private lateinit var favoriteButton: MaterialButton
    private lateinit var speedButton: MaterialButton

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isSeeking = false
    private val equalizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val handler = Handler(Looper.getMainLooper())
    private val progressTask = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        connectController()
    }

    override fun onStart() {
        super.onStart()
        handler.post(progressTask)
    }

    override fun onStop() {
        handler.removeCallbacks(progressTask)
        super.onStop()
    }

    private fun buildScreen() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(16, 17, 22))
            setPadding(dp(22), dp(8), dp(22), dp(22))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Close player"
            setOnClickListener { finish() }
        }
        val nowPlaying = TextView(this).apply {
            text = "NOW PLAYING"
            textSize = 13f
            letterSpacing = 0.13f
            setTextColor(Color.rgb(187, 198, 239))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        val options = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_more)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Player options"
            setOnClickListener { showPlayerOptions() }
        }
        top.addView(back)
        top.addView(nowPlaying)
        top.addView(options)
        root.addView(top)

        val artSize = (resources.displayMetrics.widthPixels - dp(44)).coerceAtMost(dp(300))
        val artCard = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(Color.rgb(38, 41, 53))
            layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply {
                topMargin = dp(20)
                bottomMargin = dp(22)
            }
        }
        artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_media_play)
            setPadding(dp(54), dp(54), dp(54), dp(54))
        }
        artCard.addView(artwork, android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(artCard)

        title = TextView(this).apply {
            text = "Nothing playing"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
        }
        artist = TextView(this).apply {
            text = "Choose a song from your library"
            textSize = 14f
            setTextColor(Color.rgb(177, 181, 194))
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(artist, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })

        seekBar = SeekBar(this).apply {
            setMax(1_000)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) elapsed.text = formatDuration((controller?.duration?.takeIf { it > 0L } ?: 0L) * progress / 1_000L)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { isSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val player = controller ?: return
                    val duration = player.duration
                    if (duration > 0L) player.seekTo(duration * (seekBar?.progress ?: 0) / 1_000L)
                    isSeeking = false
                }
            })
        }
        root.addView(seekBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
            topMargin = dp(18)
        })
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        elapsed = TextView(this).apply { text = "0:00"; textSize = 12f; setTextColor(Color.LTGRAY) }
        total = TextView(this).apply { text = "0:00"; textSize = 12f; setTextColor(Color.LTGRAY); gravity = Gravity.END }
        times.addView(elapsed, LinearLayout.LayoutParams(0, dp(22), 1f))
        times.addView(total, LinearLayout.LayoutParams(0, dp(22), 1f))
        root.addView(times)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        shuffleButton = controlButton("Shuffle") { toggleShuffle() }
        val previous = iconButton(android.R.drawable.ic_media_previous, "Previous track") { controller?.seekToPreviousMediaItem() }
        playPause = iconButton(android.R.drawable.ic_media_play, "Play or pause") {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        val next = iconButton(android.R.drawable.ic_media_next, "Next track") { controller?.seekToNextMediaItem() }
        repeatButton = controlButton("Repeat off") { cycleRepeat() }
        controls.addView(shuffleButton)
        controls.addView(previous)
        controls.addView(playPause, LinearLayout.LayoutParams(dp(66), dp(66)))
        controls.addView(next)
        controls.addView(repeatButton)
        root.addView(controls)

        val extras = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        speedButton = controlButton("1.0×") { cycleSpeed() }
        favoriteButton = controlButton("♡") { toggleFavorite() }
        val equalizer = controlButton("Sound") { openEqualizer() }
        val sleep = controlButton("Timer") { showSleepTimer() }
        val queue = controlButton("Queue") { showQueue() }
        extras.addView(speedButton)
        extras.addView(favoriteButton)
        extras.addView(equalizer)
        extras.addView(sleep)
        extras.addView(queue)
        root.addView(extras)

        val page = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(16, 17, 22))
            addView(root, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(page)
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayerUi()
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.mediaId?.toLongOrNull()?.let { HistoryStore.record(this@PlayerActivity, it) }
                        updatePlayerUi()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) = updatePlayerUi()
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Toast.makeText(this@PlayerActivity, "Cannot play this track: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                    }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updatePlayerUi()
                    override fun onRepeatModeChanged(repeatMode: Int) = updatePlayerUi()
                    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = updatePlayerUi()
                })
                updatePlayerUi()
            }.onFailure {
                Toast.makeText(this, "Playback service is unavailable", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updatePlayerUi() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        title.text = metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Nothing playing"
        artist.text = metadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Choose a song from your library"
        Glide.with(this)
            .load(metadata?.artworkUri)
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)
            .centerCrop()
            .into(artwork)
        playPause.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        shuffleButton.text = if (player.shuffleModeEnabled) "Shuffle on" else "Shuffle"
        repeatButton.text = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat 1"
            Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        }
        speedButton.text = "%.2f×".format(player.playbackParameters.speed).trimEnd('0').trimEnd('.')
        val id = item?.mediaId?.toLongOrNull()
        val isFavorite = id != null && FavoritesStore.contains(this, id)
        favoriteButton.text = if (isFavorite) "♥" else "♡"
        updateProgress()
    }

    private fun updateProgress() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        if (!isSeeking) seekBar.progress = if (duration > 0L) (position * 1_000L / duration).toInt().coerceIn(0, 1_000) else 0
        elapsed.text = formatDuration(position)
        total.text = formatDuration(duration)
    }

    private fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; updatePlayerUi() }
    }

    private fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        updatePlayerUi()
    }

    private fun cycleSpeed() {
        val player = controller ?: return
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = player.playbackParameters.speed
        val next = speeds.firstOrNull { it > current + 0.01f } ?: speeds.first()
        player.setPlaybackSpeed(next)
        updatePlayerUi()
    }

    private fun toggleFavorite() {
        val id = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        FavoritesStore.toggle(this, id)
        updatePlayerUi()
    }

    private fun openEqualizer() {
        val sessionId = getSharedPreferences("aditya_music_audio", MODE_PRIVATE)
            .getInt("audio_session_id", 0)
        if (sessionId <= 0) {
            Toast.makeText(this, "Start playback before opening sound effects", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        runCatching { equalizerLauncher.launch(intent) }.onFailure {
            Toast.makeText(this, "No system equalizer is installed on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSleepTimer() {
        val options = arrayOf("Off / Cancel", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes", "120 minutes")
        val minutes = intArrayOf(0, 15, 30, 45, 60, 90, 120)
        MaterialAlertDialogBuilder(this)
            .setTitle("Sleep timer")
            .setItems(options) { _, which ->
                PlaybackService.setSleepTimer(this, minutes[which])
                val message = if (minutes[which] == 0) "Sleep timer cancelled" else "Playback will pause in ${minutes[which]} minutes"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showQueue() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 8, 18, 8)
        }
        scroll.addView(content)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Play queue")
            .setView(scroll)
            .setNeutralButton("Clear queue", null)
            .setNegativeButton("Close", null)
            .create()

        lateinit var refreshQueue: () -> Unit
        refreshQueue = {
            content.removeAllViews()
            val count = player.mediaItemCount
            if (count == 0) {
                dialog.dismiss()
            } else {
                for (index in 0 until count) {
                    val item = player.getMediaItemAt(index)
                    val metadata = item.mediaMetadata
                    val name = metadata.title?.toString().orEmpty().ifBlank { "Unknown title" }
                    val artistName = metadata.artist?.toString().orEmpty()
                    val current = index == player.currentMediaItemIndex
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 6, 0, 6)
                    }
                    val label = TextView(this).apply {
                        text = (if (current) "▶  " else "") + name + if (artistName.isBlank()) "" else "\n$artistName"
                        textSize = 13f
                        setTextColor(if (current) Color.rgb(190, 203, 255) else Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            player.seekToDefaultPosition(index)
                            player.play()
                            dialog.dismiss()
                        }
                    }
                    val up = MaterialButton(this).apply {
                        text = "↑"
                        isAllCaps = false
                        minWidth = 0
                        setPadding(4, 0, 4, 0)
                        isEnabled = index > 0
                        setOnClickListener {
                            player.moveMediaItem(index, index - 1)
                            refreshQueue()
                        }
                    }
                    val down = MaterialButton(this).apply {
                        text = "↓"
                        isAllCaps = false
                        minWidth = 0
                        setPadding(4, 0, 4, 0)
                        isEnabled = index < count - 1
                        setOnClickListener {
                            player.moveMediaItem(index, index + 1)
                            refreshQueue()
                        }
                    }
                    val remove = MaterialButton(this).apply {
                        text = "×"
                        isAllCaps = false
                        minWidth = 0
                        setPadding(4, 0, 4, 0)
                        setOnClickListener {
                            player.removeMediaItem(index)
                            refreshQueue()
                        }
                    }
                    row.addView(label)
                    row.addView(up)
                    row.addView(down)
                    row.addView(remove)
                    content.addView(row)
                    val divider = View(this).apply {
                        setBackgroundColor(Color.rgb(54, 57, 68))
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    }
                    content.addView(divider)
                }
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                player.clearMediaItems()
                dialog.dismiss()
            }
            refreshQueue()
        }
        dialog.show()
    }

    private fun showPlayerOptions() {
        val options = arrayOf("Sleep timer", "Queue", "Sound effects")
        MaterialAlertDialogBuilder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSleepTimer()
                    1 -> showQueue()
                    2 -> openEqualizer()
                }
            }
            .show()
    }

    private fun controlButton(label: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            minWidth = 0
            setPadding(10, 0, 10, 0)
            setOnClickListener { action() }
        }

    private fun iconButton(icon: Int, description: String, action: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = description
            setOnClickListener { action() }
        }

    override fun onDestroy() {
        handler.removeCallbacks(progressTask)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }
}
