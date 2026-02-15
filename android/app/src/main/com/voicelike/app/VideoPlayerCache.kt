package com.voicelike.app

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.LinkedList

/**
 * A simple cache for ExoPlayer instances to avoid the overhead of creating new players repeatedly.
 * This is crucial for smooth swiping performance in video feeds (like TikTok).
 */
object VideoPlayerCache {
    private val pool = LinkedList<ExoPlayer>()
    private const val MAX_POOL_SIZE = 3

    /**
     * Get a player from the pool or create a new one.
     */
    fun get(context: Context): ExoPlayer {
        return synchronized(pool) {
            if (pool.isNotEmpty()) {
                pool.removeFirst().apply {
                    // Reset player state
                    playWhenReady = false
                    repeatMode = Player.REPEAT_MODE_ONE
                    seekTo(0)
                }
            } else {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                }
            }
        }
    }

    /**
     * Release a player back to the pool or destroy it if the pool is full.
     */
    fun release(player: ExoPlayer) {
        player.stop()
        player.clearMediaItems()
        
        synchronized(pool) {
            if (pool.size < MAX_POOL_SIZE) {
                pool.add(player)
            } else {
                player.release()
            }
        }
    }

    /**
     * Clear all players in the pool. Call this when the activity is destroyed.
     */
    fun clear() {
        synchronized(pool) {
            pool.forEach { it.release() }
            pool.clear()
        }
    }
}
