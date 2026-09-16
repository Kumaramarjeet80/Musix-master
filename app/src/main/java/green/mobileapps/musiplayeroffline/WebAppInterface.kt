package green.mobileapps.musiplayeroffline

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun onSongPlay(title: String, artist: String) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = "ACTION_PLAY_STATE_UPDATE"
            putExtra("TRACK_TITLE", title)
            putExtra("TRACK_ARTIST", artist)
            putExtra("IS_PLAYING", true)
        }
        context.startService(intent)
    }

    @JavascriptInterface
    fun onSongPause() {
        val intent = Intent(context, MusicService::class.java).apply {
            action = "ACTION_PLAY_STATE_UPDATE"
            putExtra("IS_PLAYING", false)
        }
        context.startService(intent)
    }

    @JavascriptInterface
    fun dismissNotification() {
        val intent = Intent(context, MusicService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        context.startService(intent)
    }
}
