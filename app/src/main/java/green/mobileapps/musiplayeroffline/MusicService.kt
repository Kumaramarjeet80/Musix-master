package green.mobileapps.musiplayeroffline

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    private val timerHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY_STATE_UPDATE" -> {
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)
                if (isPlaying) {
                    resetSleepTimer(300) // Reset 300-minute timer whenever music plays
                }
            }
            "ACTION_STOP_SERVICE" -> {
                timerHandler.removeCallbacks(stopRunnable)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun resetSleepTimer(minutes: Int) {
        timerHandler.removeCallbacks(stopRunnable)
        timerHandler.postDelayed(stopRunnable, minutes * 60 * 1000L)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keeps notification active even if the app is removed from recent apps
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(stopRunnable)
        super.onDestroy()
    }
}
