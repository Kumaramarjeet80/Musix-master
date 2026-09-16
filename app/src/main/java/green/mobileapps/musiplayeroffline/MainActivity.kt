package green.mobileapps.musiplayeroffline

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

data class AudioTrackItem(
    val id: Long,
    val title: String,
    val artist: String,
    val dataPath: String,
    val durationMs: Long
)

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 200
    private var musicService: MusicService? = null
    private var isBound = false

    private lateinit var recyclerSongs: RecyclerView
    private val trackList = ArrayList<AudioTrackItem>()
    private lateinit var trackAdapter: TrackAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        recyclerSongs = findViewById(R.id.recycler_view_songs)
        recyclerSongs.layoutManager = LinearLayoutManager(this)

        trackAdapter = TrackAdapter(trackList) { track ->
            onTrackClicked(track)
        }
        recyclerSongs.adapter = trackAdapter

        bindMusicService()
        checkAndRequestPermissions()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadDeviceAudioFiles()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadDeviceAudioFiles()
        } else {
            Toast.makeText(this, "Permission required to access audio files", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDeviceAudioFiles() {
        trackList.clear()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val path = it.getString(dataCol)
                if (path != null && File(path).exists()) {
                    val track = AudioTrackItem(
                        id = it.getLong(idCol),
                        title = it.getString(titleCol) ?: File(path).name,
                        artist = it.getString(artistCol) ?: "Unknown Artist",
                        dataPath = path,
                        durationMs = it.getLong(durCol)
                    )
                    trackList.add(track)
                }
            }
        }

        trackAdapter.notifyDataSetChanged()
    }

    private fun onTrackClicked(track: AudioTrackItem) {
        musicService?.playTrack(track.dataPath, track.title, track.artist)

        val intent = Intent(this, MusicActivity::class.java)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    // Inner RecyclerView Adapter
    inner class TrackAdapter(
        private val items: List<AudioTrackItem>,
        private val onClick: (AudioTrackItem) -> Unit
    ) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

        inner class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageTrackArt: ImageView = view.findViewById(R.id.image_track_art)
            val textTrackTitle: TextView = view.findViewById(R.id.text_track_title)
            val textTrackSubtitle: TextView = view.findViewById(R.id.text_track_subtitle)
            val btnTrackMenu: ImageButton = view.findViewById(R.id.btn_track_menu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_music_file, parent, false)
            return TrackViewHolder(view)
        }

        override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
            val item = items[position]
            holder.textTrackTitle.text = item.title

            val totalSeconds = item.durationMs / 1000
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            val durationText = String.format("%d:%02d", m, s)
            holder.textTrackSubtitle.text = "${item.artist} • $durationText"

            holder.itemView.setOnClickListener {
                onClick(item)
            }

            holder.btnTrackMenu.setOnClickListener {
                Toast.makeText(this@MainActivity, "Options: ${item.title}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}