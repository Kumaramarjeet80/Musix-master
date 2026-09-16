package green.mobileapps.musiplayeroffline

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File

class MusicActivity : AppCompatActivity() {

    private var musicService: MusicService? = null
    private var isBound = false

    // Player UI Views
    private lateinit var textSongTitle: TextView
    private lateinit var textSongArtist: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnOpenEqualizer: ImageButton
    private lateinit var seekBarProgress: SeekBar

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updatePlayerState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.music_activity)

        initViews()
        bindMusicService()
    }

    private fun initViews() {
        textSongTitle = findViewById(R.id.text_song_title)
        textSongArtist = findViewById(R.id.text_song_artist)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnPrevious = findViewById(R.id.btn_previous)
        btnOpenEqualizer = findViewById(R.id.btn_open_equalizer)
        seekBarProgress = findViewById(R.id.seek_bar_progress)

        btnPlayPause.setOnClickListener {
            musicService?.let { service ->
                val player = service.mediaPlayer
                if (player != null && player.isPlaying) {
                    service.pausePlayback()
                    btnPlayPause.setImageResource(R.drawable.play_arrow_24px)
                } else {
                    service.resumePlayback()
                    btnPlayPause.setImageResource(R.drawable.pause_24px)
                }
            }
        }

        btnNext.setOnClickListener {
            musicService?.playNext()
            updatePlayerState()
        }

        btnPrevious.setOnClickListener {
            musicService?.playPrevious()
            updatePlayerState()
        }

        btnOpenEqualizer.setOnClickListener {
            showEqualizerDialog()
        }
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updatePlayerState() {
        musicService?.let { service ->
            textSongTitle.text = service.currentTrackTitle
            textSongArtist.text = service.currentTrackArtist

            val isPlaying = service.mediaPlayer?.isPlaying ?: false
            btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
            )
        }
    }

    private fun showEqualizerDialog() {
        val service = musicService ?: return
        val eq = service.equalizer
        val bb = service.bassBoost

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_equalizer, null)
        val switchEq = dialogView.findViewById<SwitchCompat>(R.id.switch_eq_enable)
        val spinnerPresets = dialogView.findViewById<Spinner>(R.id.spinner_eq_presets)
        val seekBass = dialogView.findViewById<SeekBar>(R.id.seek_bass_boost)
        val textBassStrength = dialogView.findViewById<TextView>(R.id.text_bass_strength)
        val containerSliders = dialogView.findViewById<LinearLayout>(R.id.container_eq_sliders)

        val btnSetA = dialogView.findViewById<Button>(R.id.btn_set_point_a)
        val btnSetB = dialogView.findViewById<Button>(R.id.btn_set_point_b)
        val btnClearLoop = dialogView.findViewById<Button>(R.id.btn_clear_ab_loop)
        val textLoopStatus = dialogView.findViewById<TextView>(R.id.text_loop_status)
        val btnOpenTrimmer = dialogView.findViewById<Button>(R.id.btn_open_trimmer)

        // Master Switch
        switchEq.isChecked = eq?.enabled ?: false
        switchEq.setOnCheckedChangeListener { _, isChecked ->
            eq?.enabled = isChecked
            bb?.enabled = isChecked
        }

        // Bass Boost
        seekBass.max = 1000
        seekBass.progress = bb?.roundedStrength?.toInt() ?: 0
        textBassStrength.text = "${(seekBass.progress / 10)}%"
        seekBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    service.setBassBoost(progress.toShort())
                    textBassStrength.text = "${(progress / 10)}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Equalizer Hardware Presets
        if (eq != null) {
            val numPresets = eq.numberOfPresets.toInt()
            val presetNames = ArrayList<String>()
            for (i in 0 until numPresets) {
                presetNames.add(eq.getPresetName(i.toShort()))
            }
            presetNames.add("Custom")

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presetNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPresets.adapter = adapter

            val currentPreset = eq.currentPreset.toInt()
            if (currentPreset in 0 until numPresets) {
                spinnerPresets.setSelection(currentPreset)
            } else {
                spinnerPresets.setSelection(numPresets)
            }

            // Frequency Sliders Generation
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()

            containerSliders.removeAllViews()

            for (i in 0 until numBands) {
                val band = i.toShort()
                val centerFreqHz = eq.getCenterFreq(band) / 1000

                val bandRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 4, 0, 8)
                }

                val label = TextView(this).apply {
                    text = if (centerFreqHz >= 1000) "${centerFreqHz / 1000} kHz" else "$centerFreqHz Hz"
                    setTextColor(Color.parseColor("#8b949e"))
                    textSize = 12f
                }

                val bandSeekBar = SeekBar(this).apply {
                    max = maxLevel - minLevel
                    progress = (eq.getBandLevel(band) - minLevel)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, prog: Int, fromUser: Boolean) {
                            if (fromUser) {
                                val newLevel = (prog + minLevel).toShort()
                                service.setBandGain(band, newLevel)
                                spinnerPresets.setSelection(numPresets) // Custom
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }

                bandRow.addView(label)
                bandRow.addView(bandSeekBar)
                containerSliders.addView(bandRow)
            }

            spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    if (pos < numPresets) {
                        eq.usePreset(pos.toShort())
                        for (i in 0 until numBands) {
                            val band = i.toShort()
                            val row = containerSliders.getChildAt(i) as? LinearLayout
                            val sb = row?.getChildAt(1) as? SeekBar
                            sb?.progress = eq.getBandLevel(band) - minLevel
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // A-B Looper Controls
        fun refreshLoopStatus() {
            val a = service.loopPointA
            val b = service.loopPointB
            textLoopStatus.text = when {
                a != null && b != null -> "Looping: ${formatTime(a)} - ${formatTime(b)}"
                a != null -> "Point A: ${formatTime(a)} | Set Point B"
                else -> "Loop: Disabled"
            }
        }
        refreshLoopStatus()

        btnSetA.setOnClickListener {
            val pos = service.mediaPlayer?.currentPosition?.toLong() ?: 0L
            service.setPointA(pos)
            refreshLoopStatus()
        }

        btnSetB.setOnClickListener {
            val pos = service.mediaPlayer?.currentPosition?.toLong() ?: 0L
            service.setPointB(pos)
            refreshLoopStatus()
        }

        btnClearLoop.setOnClickListener {
            service.clearLoop()
            refreshLoopStatus()
        }

        // Trimmer Execution Trigger
        btnOpenTrimmer.setOnClickListener {
            val path = service.currentTrackPath
            if (path.isEmpty() || !File(path).exists()) {
                Toast.makeText(this, "No active audio file available to trim", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val curPos = service.mediaPlayer?.currentPosition?.toLong() ?: 0L
            val startUs = curPos * 1000
            val endUs = (curPos + 30000) * 1000 // 30-second default snippet

            val outDir = getExternalFilesDir(null) ?: filesDir
            val outFile = File(outDir, "Trimmed_${System.currentTimeMillis()}.m4a")

            Toast.makeText(this, "Trimming audio track...", Toast.LENGTH_SHORT).show()

            Thread {
                val success = AudioTrimmer.trimAudio(path, outFile.absolutePath, startUs, endUs)
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Clip saved: ${outFile.name}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to trim audio.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format("%d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}