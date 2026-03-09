package com.zorindisplays.mystery.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.zorindisplays.mystery.R

class SoundManager(private val context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val rainSoundId = soundPool.load(context, R.raw.rain, 1)
    private val coinsSoundId = soundPool.load(context, R.raw.coins, 1)
    private val counterSoundId = soundPool.load(context, R.raw.counter, 1)

    private var loopPlayer: MediaPlayer? = null

    fun playRain() {
        soundPool.play(rainSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playCoins() {
        soundPool.play(coinsSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playCounter() {
        soundPool.play(counterSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun startLoop() {
        if (loopPlayer?.isPlaying == true) return

        loopPlayer?.release()
        loopPlayer = MediaPlayer.create(context, R.raw.loop)?.apply {
            isLooping = true
            start()
        }
    }

    fun stopLoop() {
        loopPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            player.release()
        }
        loopPlayer = null
    }

    fun release() {
        stopLoop()
        soundPool.release()
    }
}