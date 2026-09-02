package com.aod.musicshow

import android.graphics.Bitmap

object MusicRepository {

    data class NowPlaying(
        val title: String = "",
        val artist: String = "",
        val albumArt: Bitmap? = null,
        val isPlaying: Boolean = false
    )

    @Volatile
    var current: NowPlaying = NowPlaying()
        private set

    private val listeners = mutableListOf<(NowPlaying) -> Unit>()

    fun update(newState: NowPlaying) {
        current = newState
        listeners.forEach { it(newState) }
    }

    fun clear() {
        update(NowPlaying())
    }

    fun addListener(listener: (NowPlaying) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (NowPlaying) -> Unit) {
        listeners.remove(listener)
    }
}
