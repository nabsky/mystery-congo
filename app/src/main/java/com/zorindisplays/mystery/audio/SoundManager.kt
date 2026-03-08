package com.zorindisplays.mystery.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.zorindisplays.mystery.R

class SoundManager(context: Context) {

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
    private val coinsEndSoundId = soundPool.load(context, R.raw.coins, 1)
    private val counterSoundId = soundPool.load(context, R.raw.counter, 1)

    fun playRain() {
        soundPool.play(rainSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playCoins() {
        soundPool.play(coinsEndSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playCounter() {
        soundPool.play(counterSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}