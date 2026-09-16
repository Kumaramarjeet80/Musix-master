package green.mobileapps.musiplayeroffline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import java.io.File

class MusicService : Service() {

    private val binder = MusicBinder()
    var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    // Native DSP Engines
    var equalizer: Equalizer? = null
        private set
    var bassBoost: BassBoost? = null
        private set

    // A-B Looper Variables
    var loopPointA: Long? = null
        private set
    var loopPointB: Long? = null
        private set

    private val loopHandler = Handler(Looper.getMainLooper())
    private val loopRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val current = player.currentPosition.toLong()
                    val a = loopPointA
                    val b = loopPointB

                    if (a != null && b != null && b > a) {
                        if (current >= b) {
                            player.seekTo(a.toInt())
                        }
                    }
                }
            }
            loopHandler.postDelayed(this, 100)
        }
    }

    var currentTrackTitle: String = "No track playing"
    var currentTrackArtist: String = "Offline Audio"
    var currentTrackPath: String = ""

    companion object {
        const val CHANNEL_ID = "musix_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MusixMediaSession")
        loopHandler.post(loopRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
        }
        return START_STICKY
    }

    fun playTrack(filePath: String, title: String, artist: String) {
        val file = File(filePath)
        if (!file.exists()) return

        currentTrackPath = filePath
        currentTrackTitle = title
        currentTrackArtist = artist

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
        }

        mediaPlayer?.let { player ->
            initAudioEffects(player.audioSessionId)
            player.setOnCompletionListener {
                playNext()
            }
        }

        updateNotification(true)
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                updateNotification(false)
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                updateNotification(true)
            }
        }
    }

    fun playNext() {
        // Implement playlist traversal logic here or callback to your UI controller
    }

    fun playPrevious() {
        // Implement playlist traversal logic here or callback to your UI controller
    }

    private fun initAudioEffects(audioSessionId: Int) {
        try {
            equalizer?.release()
            bassBoost?.release()

            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBandGain(band: Short, gainMilliBels: Short) {
        equalizer?.setBandLevel(band, gainMilliBels)
    }

    fun setBassBoost(strength: Short) {
        bassBoost?.setStrength(strength)
    }

    fun setPointA(posMs: Long) {
        loopPointA = posMs
    }

    fun setPointB(posMs: Long) {
        loopPointB = posMs
    }

    fun clearLoop() {
        loopPointA = null
        loopPointB = null
    }

    private fun updateNotification(isPlaying: Boolean) {
        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE }
            val pendingPause = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pendingPause)
        } else {
            val playIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY }
            val pendingPlay = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", pendingPlay)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentTitle(currentTrackTitle)
            .setContentText(currentTrackArtist)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", null)
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "Next", null)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls background music playback and notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loopHandler.removeCallbacks(loopRunnable)
        mediaPlayer?.release()
        equalizer?.release()
        bassBoost?.release()
        mediaSession?.release()
    }
}