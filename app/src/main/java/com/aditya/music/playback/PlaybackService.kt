package com.aditya.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aditya.music.data.HistoryStore
import com.aditya.music.ui.PlayerActivity

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioSessionId = audioManager.generateAudioSessionId()
        if (audioSessionId > 0) {
            player.setAudioSessionId(audioSessionId)
            getSharedPreferences("aditya_music_audio", Context.MODE_PRIVATE)
                .edit().putInt("audio_session_id", audioSessionId).apply()
            sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            })
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                HistoryStore.record(this@PlaybackService, id)
            }
        })
        val playerScreen = PendingIntent.getActivity(
            this,
            1001,
            Intent(this, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(playerScreen)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_SLEEP_TIMER) {
            val minutes = intent.getIntExtra(EXTRA_MINUTES, 0).coerceIn(0, 24 * 60)
            sleepRunnable?.let { handler.removeCallbacks(it) }
            sleepRunnable = null
            if (minutes > 0) {
                val task = Runnable {
                    if (::player.isInitialized) player.pause()
                    sleepRunnable = null
                }
                sleepRunnable = task
                handler.postDelayed(task, minutes * 60_000L)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        mediaSession?.release()
        if (::player.isInitialized) {
            val sessionId = getSharedPreferences("aditya_music_audio", Context.MODE_PRIVATE)
                .getInt("audio_session_id", 0)
            if (sessionId > 0) {
                sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                })
            }
            player.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_SET_SLEEP_TIMER = "com.aditya.music.action.SET_SLEEP_TIMER"
        private const val EXTRA_MINUTES = "minutes"

        fun setSleepTimer(context: Context, minutes: Int) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_SET_SLEEP_TIMER)
                .putExtra(EXTRA_MINUTES, minutes)
            context.startService(intent)
        }
    }
}
