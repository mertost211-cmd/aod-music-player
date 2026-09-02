package com.aod.musicshow

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushState(metadata, activeController?.playbackState)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pushState(activeController?.metadata, state)
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachToFirstController(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        mediaSessionManager =
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, NotificationListener::class.java)

        try {
            val sessions = mediaSessionManager.getActiveSessions(componentName)
            attachToFirstController(sessions)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener, componentName
            )
        } catch (e: SecurityException) {
            Log.e("NotificationListener", "Bildirim erişimi izni verilmemiş", e)
        }
    }

    private fun attachToFirstController(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)

        val playingController = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()

        activeController = playingController
        playingController?.registerCallback(controllerCallback)
        pushState(playingController?.metadata, playingController?.playbackState)
    }

    private fun pushState(metadata: MediaMetadata?, state: PlaybackState?) {
        if (metadata == null) {
            MusicRepository.clear()
            return
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art: Bitmap? = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        MusicRepository.update(
            MusicRepository.NowPlaying(
                title = title,
                artist = artist,
                albumArt = art,
                isPlaying = isPlaying
            )
        )

        if (isPlaying) {
            startService(Intent(this, AodOverlayService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
