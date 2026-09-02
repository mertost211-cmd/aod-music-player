package com.aod.musicshow

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class AodOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private val musicListener: (MusicRepository.NowPlaying) -> Unit = { state ->
        handler.post { updateOverlayContent(state) }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (MusicRepository.current.isPlaying) showOverlay()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    removeOverlay()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildForegroundNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        MusicRepository.addListener(musicListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        MusicRepository.removeListener(musicListener)
        removeOverlay()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "AodMusic:DimWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_aod, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        view.setOnClickListener { removeOverlay() }

        windowManager.addView(view, params)
        overlayView = view

        updateOverlayContent(MusicRepository.current)
        startClockTicker()
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        stopClockTicker()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun updateOverlayContent(state: MusicRepository.NowPlaying) {
        val view = overlayView ?: return
        view.findViewById<TextView>(R.id.aodTrack).text =
            state.title.ifBlank { getString(R.string.no_song_playing) }
        view.findViewById<TextView>(R.id.aodArtist).text = state.artist
        val art = view.findViewById<ImageView>(R.id.aodAlbumArt)
        if (state.albumArt != null) {
            art.setImageBitmap(state.albumArt)
            art.visibility = View.VISIBLE
        } else {
            art.visibility = View.GONE
        }

        if (!state.isPlaying) {
            handler.postDelayed({ if (!MusicRepository.current.isPlaying) removeOverlay() }, 1500)
        }
    }

    private fun startClockTicker() {
        val sdfTime = SimpleDateFormat("HH:mm", Locale("tr"))
        val sdfDate = SimpleDateFormat("d MMMM EEEE", Locale("tr"))
        clockRunnable = object : Runnable {
            override fun run() {
                val view = overlayView
                if (view != null) {
                    val now = Date()
                    view.findViewById<TextView>(R.id.aodClock).text = sdfTime.format(now)
                    view.findViewById<TextView>(R.id.aodDate).text = sdfDate.format(now)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(clockRunnable!!)
    }

    private fun stopClockTicker() {
        clockRunnable?.let { handler.removeCallbacks(it) }
        clockRunnable = null
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = "aod_music_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, getString(R.string.app_name),
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42
    }
}
