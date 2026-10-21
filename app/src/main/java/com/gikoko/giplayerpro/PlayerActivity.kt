package com.gikoko.giplayerpro

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_INDEX = "index"
        const val EXTRA_IS_STREAM = "stream"
        var activePlayer: ExoPlayer? = null
    }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var gestureArea: View
    private lateinit var nightOverlay: View
    private lateinit var chipsScroll: View
    private lateinit var gestureIndicator: View
    private lateinit var gestureIcon: TextView
    private lateinit var gestureText: TextView
    private lateinit var chipSpeed: TextView
    private lateinit var chipBoost: TextView
    private lateinit var chipNight: TextView
    private lateinit var chipSleep: TextView

    private val prefs by lazy { getSharedPreferences("gi_player", MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val handler = Handler(Looper.getMainLooper())

    private var uris = arrayListOf<String>()
    private var titles = arrayListOf<String>()
    private var startIndex = 0
    private var isStream = false

    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var effectsReady = false
    private var boostOn = false
    private var nightOn = false
    private var sleepAt = 0L

    private var gestureMode = 0
    private var downX = 0f
    private var downY = 0f
    private var startBrightness = 0.5f
    private var startVolume = 0.5f
    private var seekDelta = 0L
    private lateinit var gestureDetector: GestureDetector

    private val hideGestureRunnable = Runnable { gestureIndicator.visibility = View.GONE }

    private val sleepChecker = object : Runnable {
        override fun run() {
            if (sleepAt > 0L) {
                if (System.currentTimeMillis() >= sleepAt) {
                    sleepAt = 0L
                    player.pause()
                    toast("Sleep Timer ⏱ ပြီးဆုံးပါပြီ")
                } else handler.postDelayed(this, 1000)
            }
        }
    }

    private val srtPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) applyExternalSubtitle(uri)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { c ->
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        uris = intent.getStringArrayListExtra(EXTRA_URIS) ?: arrayListOf()
        titles = intent.getStringArrayListExtra(EXTRA_TITLES) ?: arrayListOf()
        startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        isStream = intent.getBooleanExtra(EXTRA_IS_STREAM, false)
        if (uris.isEmpty()) {
            finish()
            return
        }

        bindViews()
        setupPlayer()
        setupGestures()
        setupChips()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        gestureArea = findViewById(R.id.gestureArea)
        nightOverlay = findViewById(R.id.nightOverlay)
        chipsScroll = findViewById(R.id.chipsScroll)
        gestureIndicator = findViewById(R.id.gestureIndicator)
        gestureIcon = findViewById(R.id.gestureIcon)
        gestureText = findViewById(R.id.gestureText)
        chipSpeed = findViewById(R.id.chipSpeed)
        chipBoost = findViewById(R.id.chipBoost)
        chipNight = findViewById(R.id.chipNight)
        chipSleep = findViewById(R.id.chipSleep)
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        activePlayer = player
        playerView.player = player
        playerView.setControllerVisibilityListener { visibility ->
            gestureArea.visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) attachEffects()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateKeepAlive(isPlaying)
                playerView.keepScreenOn = isPlaying
            }

            override fun onPlaybackParametersChanged(params: PlaybackParameters) {
                chipSpeed.text = if (params.speed == 1.0f) "1x"
                else String.format(Locale.US, "%.2fx", params.speed)
            }
        })
        uris.forEach { player.addMediaItem(MediaItem.fromUri(Uri.parse(it))) }

        var startMs = 0L
        if (!isStream && prefs.getBoolean("auto_resume", true)) {
            val key = uris[startIndex.coerceIn(0, uris.size - 1)]
            val saved = prefs.getLong("pos:$key", 0L)
            val dur = prefs.getLong("dur:$key", 0L)
            if (saved > 10_000 && (dur <= 0L || saved < dur - 10_000)) {
                startMs = saved
                toast("▶ " + formatMs(saved) + " မှ ဆက်လက်ဖွင့်ပါသည်")
            }
        }
        player.seekTo(startIndex.coerceIn(0, player.mediaItemCount - 1), startMs)
        player.prepare()
        player.play()
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (playerView.isControllerShown) playerView.hideController()
                    else playerView.showController()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val w = gestureArea.width.toFloat()
                    when {
                        e.x < w / 3f -> player.seekBack()
                        e.x > w * 2f / 3f -> player.seekForward()
                        else -> if (player.isPlaying) player.pause() else player.play()
                    }
                    return true
                }
            }
        )
        gestureArea.setOnTouchListener { _, event -> handleGesture(event); true }
    }

    private fun handleGesture(event: MotionEvent) {
        gestureDetector.onTouchEvent(event)
        val w = gestureArea.width.toFloat()
        val h = gestureArea.height.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                gestureMode = 0
                seekDelta = 0
                val sb = window.attributes.screenBrightness
                startBrightness = if (sb < 0f) 0.5f else sb
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (gestureMode == 0) {
                    if (abs(dy) > 24f && abs(dy) > abs(dx)) {
                        gestureMode = if (downX < w / 2f) 1 else 2
                    } else if (abs(dx) > 24f) {
                        gestureMode = 3
                    }
                }
                when (gestureMode) {
                    1 -> {
                        val b = (startBrightness - dy / h * 1.5f).coerceIn(0.02f, 1f)
                        window.attributes = window.attributes.also { it.screenBrightness = b }
                        showGesture("☀", "အလင်း ${(b * 100).toInt()}%")
                    }

                    2 -> {
                        val v = (startVolume - dy / h * 1.5f).coerceIn(0f, 1f)
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt(), 0)
                        showGesture("🔊", "အသံ ${(v * 100).toInt()}%")
                    }

                    3 -> {
                        seekDelta = (dx / w * 120_000f).toLong()
                        val target = (player.currentPosition + seekDelta).coerceAtLeast(0)
                        val sign = if (seekDelta >= 0) "+" else "-"
                        showGesture("↔", sign + formatMs(seekDelta) + "\n→ " + formatMs(target))
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureMode == 3 && seekDelta != 0L) {
                    player.seekTo((player.currentPosition + seekDelta).coerceAtLeast(0))
                }
                hideGesture()
                gestureMode = 0
            }
        }
    }

    private fun showGesture(icon: String, text: String) {
        gestureIcon.text = icon
        gestureText.text = text
        gestureIndicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideGestureRunnable)
        handler.postDelayed(hideGestureRunnable, 800)
    }

    private fun hideGesture() {
        handler.removeCallbacks(hideGestureRunnable)
        gestureIndicator.visibility = View.GONE
    }

    private fun setupChips() {
        chipSpeed.setOnClickListener { showSpeedDialog() }
        findViewById<TextView>(R.id.chipEq).setOnClickListener { showEqDialog() }
        findViewById<TextView>(R.id.chipTrack).setOnClickListener { showTrackDialog() }
        findViewById<TextView>(R.id.chipSrt).setOnClickListener { srtPicker.launch(arrayOf("*/*")) }
        findViewById<TextView>(R.id.chipLoop).setOnClickListener { cycleLoop() }
        chipBoost.setOnClickListener { toggleBoost() }
        chipNight.setOnClickListener { toggleNight() }
        findViewById<TextView>(R.id.chipZoom).setOnClickListener { toggleZoom() }
        findViewById<TextView>(R.id.chipPip).setOnClickListener { enterPip() }
        chipSleep.setOnClickListener { showSleepDialog() }
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
        val names = speeds.map { if (it == 1.0f) "1x (ပုံမှန်)" else "${it}x" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("အရှိန် Speed ⏩")
            .setItems(names) { _, which -> player.setPlaybackSpeed(speeds[which]) }
            .show()
    }

    private fun cycleLoop() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF ->
                Player.REPEAT_MODE_ONE.also { toast("🔁 ဖိုင်တစ်ခုတည်း ပြန်ကစားမည်") }
            Player.REPEAT_MODE_ONE ->
                Player.REPEAT_MODE_ALL.also { toast("🔁 အားလုံး ပြန်ကစားမည်") }
            else ->
                Player.REPEAT_MODE_OFF.also { toast("🔁 Loop ပိတ်ပါပြီ") }
        }
    }

    private fun toggleBoost() {
        attachEffects()
        val l = loudness
        if (l == null) {
            toast("BOOST အသုံးပြုရန် ဗီဒီယိုကို ခဏ စတင်ပေးပါ")
            return
        }
        boostOn = !boostOn
        l.setEnabled(boostOn)
        if (boostOn) l.setTargetGain(500)
        chipBoost.setTextColor(
            ContextCompat.getColor(this, if (boostOn) R.color.gold else R.color.cyber_cyan)
        )
        toast(if (boostOn) "Volume Boost 🔊 +50%" else "Volume Boost ပိတ်ပါပြီ")
    }

    private fun toggleNight() {
        nightOn = !nightOn
        nightOverlay.visibility = if (nightOn) View.VISIBLE else View.GONE
        chipNight.setTextColor(
            ContextCompat.getColor(this, if (nightOn) R.color.gold else R.color.cyber_cyan)
        )
        toast(if (nightOn) "Night Mode 🌙 ဖွင့်ထားပါသည်" else "Night Mode ပိတ်ပါပြီ")
    }

    private fun toggleZoom() {
        val newMode =
            if (playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT)
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else AspectRatioFrameLayout.RESIZE_MODE_FIT
        playerView.resizeMode = newMode
        toast(
            if (newMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "Zoom: CROP 🖼"
            else "Zoom: FIT 🖼"
        )
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            val w = playerView.width.coerceAtLeast(1)
            val h = playerView.height.coerceAtLeast(1)
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(w, h))
                    .build()
            )
        } else {
            toast("ဤ Android version တွင် မထောက်ပံ့ပါ")
        }
    }

    private fun showSleepDialog() {
        val mins = intArrayOf(0, 5, 10, 15, 30, 45, 60)
        val labels = arrayOf(
            "ပိတ်", "5 မိနစ်", "10 မိနစ်", "15 မိနစ်", "30 မိနစ်", "45 မိနစ်", "60 မိနစ်"
        )
        AlertDialog.Builder(this)
            .setTitle("Sleep Timer ⏱")
            .setItems(labels) { _, which ->
                handler.removeCallbacks(sleepChecker)
                if (mins[which] == 0) {
                    sleepAt = 0L
                    chipSleep.setTextColor(ContextCompat.getColor(this, R.color.cyber_cyan))
                    toast("Sleep Timer ပိတ်ပါပြီ")
                } else {
                    sleepAt = System.currentTimeMillis() + mins[which] * 60_000L
                    handler.postDelayed(sleepChecker, 1000)
                    chipSleep.setTextColor(ContextCompat.getColor(this, R.color.gold))
                    toast("Sleep Timer ⏱ ${mins[which]} မိနစ်")
                }
            }
            .show()
    }

    private fun showTrackDialog() {
        val disabled =
            player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val options = mutableListOf(
            if (disabled) "💬 စာတန်းထိုး ဖွင့်မည်" else "💬 စာတန်းထိုး ပိတ်မည်",
            "📄 SRT ဖိုင်မှ တင်မည်"
        )
        val sels = mutableListOf<Pair<Tracks.Group, Int>>()
        for (g in player.currentTracks.groups) {
            if (g.type == C.TRACK_TYPE_AUDIO || g.type == C.TRACK_TYPE_TEXT) {
                for (t in 0 until g.length) {
                    if (g.isTrackSupported(t)) {
                        val f = g.getTrackFormat(t)
                        val name = f.label ?: f.language ?: ("Track " + (t + 1))
                        options.add(
                            (if (g.type == C.TRACK_TYPE_AUDIO) "🔊 " else "💬 ") + name +
                                if (g.isTrackSelected(t)) "  ✓" else ""
                        )
                        sels.add(g to t)
                    }
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("ထရက်များ (Audio / Subtitle)")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> player.trackSelectionParameters =
                        player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !disabled)
                            .build()

                    1 -> srtPicker.launch(arrayOf("*/*"))

                    else -> {
                        val (g, t) = sels[which - 2]
                        player.trackSelectionParameters =
                            player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(g.mediaTrackGroup, listOf(t))
                                )
                                .build()
                    }
                }
            }
            .show()
    }

    private fun applyExternalSubtitle(uri: Uri) {
        val idx = player.currentMediaItemIndex
        val currentUri = player.currentMediaItem?.localConfiguration?.uri ?: return
        val pos = player.currentPosition
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("ext")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val newItem = MediaItem.Builder()
            .setUri(currentUri)
            .setSubtitleConfigurations(listOf(sub))
            .build()
        player.removeMediaItem(idx)
        player.addMediaItem(idx, newItem)
        player.seekTo(idx, pos)
        player.prepare()
        toast("SRT ဖိုင် တင်ပြီးပါပြီ 💬")
    }

    private fun showEqDialog() {
        val e = eq ?: run {
            toast("EQ ဖွင့်ရန် ဗီဒီယိုကို ခဏ စတင်ပေးပါ")
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_eq, null)
        val swEq = view.findViewById<Switch>(R.id.swEq)
        val spPreset = view.findViewById<Spinner>(R.id.spPreset)
        val sbBass = view.findViewById<SeekBar>(R.id.sbBass)
        val sbBoost = view.findViewById<SeekBar>(R.id.sbBoost)

        swEq.isChecked = e.enabled
        val presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it) }
        spPreset.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presets)
        val cur = e.preset.toInt()
        if (cur in presets.indices) spPreset.setSelection(cur)

        var spinnerArmed = false
        spPreset.post { spinnerArmed = true }
        spPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (spinnerArmed) e.usePreset(pos.toShort())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        sbBass.max = 1000
        sbBass.progress = bassBoost?.strength?.toInt() ?: 0
        sbBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                bassBoost?.let {
                    it.setEnabled(progress > 0)
                    it.setStrength(progress.toShort())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbBoost.max = 20
        sbBoost.progress = ((loudness?.targetGain ?: 0) / 100).coerceIn(0, 20)
        sbBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                loudness?.let {
                    it.setEnabled(progress > 0)
                    it.setTargetGain(progress * 100)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        swEq.setOnCheckedChangeListener { _, checked -> e.setEnabled(checked) }

        AlertDialog.Builder(this)
            .setTitle("Equalizer 🎚")
            .setView(view)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun attachEffects() {
        if (effectsReady) return
        val sid = player.audioSessionId
        if (sid == 0) return
        try {
            eq = Equalizer(0, sid).also { it.setEnabled(true) }
            bassBoost = BassBoost(0, sid)
            loudness = LoudnessEnhancer(sid)
            effectsReady = true
        } catch (_: Exception) {
        }
    }

    private fun updateKeepAlive(playing: Boolean) {
        if (playing) {
            val intent = Intent(this, PlaybackKeepAliveService::class.java)
            intent.putExtra(
                "title",
                titles.getOrNull(player.currentMediaItemIndex) ?: getString(R.string.app_name)
            )
            ContextCompat.startForegroundService(this, intent)
        } else if (!isChangingConfigurations) {
            stopService(Intent(this, PlaybackKeepAliveService::class.java))
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::player.isInitialized && player.isPlaying &&
            Build.VERSION.SDK_INT >= 26 && prefs.getBoolean("auto_pip", true)
        ) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(newConfig: Configuration) {
        super.onPictureInPictureModeChanged(newConfig)
        if (isInPictureInPictureMode) {
            chipsScroll.visibility = View.GONE
            playerView.hideController()
        } else {
            chipsScroll.visibility = View.VISIBLE
            if (!player.isPlaying) finish()
        }
    }

    private fun savePosition() {
        val idx = player.currentMediaItemIndex
        val uri = uris.getOrNull(idx) ?: return
        if (!isStream) {
            prefs.edit()
                .putLong("pos:$uri", player.currentPosition)
                .putLong("dur:$uri", player.duration)
                .apply()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            savePosition()
            if (!player.isPlaying && !isInPictureInPictureMode) finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            activePlayer = null
            handler.removeCallbacksAndMessages(null)
            stopService(Intent(this, PlaybackKeepAliveService::class.java))
            player.release()
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun formatMs(ms: Long): String {
        val total = abs(ms) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }
}
