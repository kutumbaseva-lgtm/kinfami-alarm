package com.gdelataillade.alarm.services

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.Timer
import java.util.TimerTask
import io.flutter.Log

class AudioService(private val context: Context) {
    private val mediaPlayers = ConcurrentHashMap<Int, MediaPlayer>()
    private val timers = ConcurrentHashMap<Int, Timer>()
    // KinFami fork: pending "play again" steps per alarm id (cancelled by stopAudio).
    private val repeatHandler = Handler(Looper.getMainLooper())
    private val pendingRepeats = ConcurrentHashMap<Int, Runnable>()

    private var onAudioComplete: (() -> Unit)? = null

    fun setOnAudioCompleteListener(listener: () -> Unit) {
        onAudioComplete = listener
    }

    fun isMediaPlayerEmpty(): Boolean {
        return mediaPlayers.isEmpty()
    }

    fun getPlayingMediaPlayersIds(): List<Int> {
        return mediaPlayers.filter { (_, mediaPlayer) -> mediaPlayer.isPlaying }.keys.toList()
    }

    fun playAudio(
        id: Int,
        filePath: String,
        loopAudio: Boolean,
        fadeDuration: Double?,
        repeatCount: Int = 1,
        repeatGapMs: Long = 0L
    ) {
        stopAudio(id) // Stop and release any existing MediaPlayer and Timer for this ID

        val baseAppFlutterPath = (context.filesDir.parent ?: "") + "/app_flutter/"
        val adjustedFilePath = when {
            filePath.startsWith("assets/") -> "flutter_assets/$filePath"
            !filePath.startsWith("/") -> baseAppFlutterPath + filePath
            else -> filePath
        }

        try {
            MediaPlayer().apply {
                when {
                    adjustedFilePath.startsWith("flutter_assets/") -> {
                        // It's an asset file
                        val assetManager = context.assets
                        val descriptor = assetManager.openFd(adjustedFilePath)
                        setDataSource(
                            descriptor.fileDescriptor,
                            descriptor.startOffset,
                            descriptor.length
                        )
                    }

                    else -> {
                        // Handle local files and adjusted paths
                        setDataSource(adjustedFilePath)
                    }
                }

                prepare()
                isLooping = loopAudio
                start()

                // KinFami fork: play [repeatCount] times with [repeatGapMs] in between; only
                // the last play reports completion. Stop/Done cancels the pending replay.
                var played = 1
                setOnCompletionListener { player ->
                    if (!loopAudio && played < repeatCount) {
                        played++
                        val replay = Runnable {
                            pendingRepeats.remove(id)
                            try {
                                if (mediaPlayers[id] === player) {
                                    player.seekTo(0)
                                    player.start()
                                }
                            } catch (e: Exception) {
                                Log.e("AudioService", "Error repeating audio: $e")
                            }
                        }
                        pendingRepeats[id] = replay
                        repeatHandler.postDelayed(replay, repeatGapMs)
                    } else if (!loopAudio) {
                        onAudioComplete?.invoke()
                    }
                }

                mediaPlayers[id] = this

                if (fadeDuration != null && fadeDuration > 0) {
                    val timer = Timer(true)
                    timers[id] = timer
                    startFadeIn(this, fadeDuration, timer)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AudioService", "Error playing audio: $e")
        }
    }

    fun stopAudio(id: Int) {
        pendingRepeats.remove(id)?.let { repeatHandler.removeCallbacks(it) }
        timers[id]?.cancel()
        timers.remove(id)

        mediaPlayers[id]?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
            release()
        }
        mediaPlayers.remove(id)
    }

    private fun startFadeIn(mediaPlayer: MediaPlayer, duration: Double, timer: Timer) {
        val maxVolume = 1.0f
        val fadeDuration = (duration * 1000).toLong()
        val fadeInterval = 100L
        val numberOfSteps = fadeDuration / fadeInterval
        val deltaVolume = maxVolume / numberOfSteps
        var volume = 0.0f

        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!mediaPlayer.isPlaying) {
                    cancel()
                    return
                }

                mediaPlayer.setVolume(volume, volume)
                volume += deltaVolume

                if (volume >= maxVolume) {
                    mediaPlayer.setVolume(maxVolume, maxVolume)
                    cancel()
                }
            }
        }, 0, fadeInterval)
    }

    fun cleanUp() {
        pendingRepeats.values.forEach { repeatHandler.removeCallbacks(it) }
        pendingRepeats.clear()
        timers.values.forEach(Timer::cancel)
        timers.clear()

        mediaPlayers.values.forEach { mediaPlayer ->
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()
            mediaPlayer.release()
        }
        mediaPlayers.clear()
    }
}
