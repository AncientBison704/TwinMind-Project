package com.example.twinmindproject.recording.core

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class FocusController(
    private val am: AudioManager,
    private val onLoss: () -> Unit,
    private val onGain: () -> Unit
) {
    private var req: AudioFocusRequest? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLoss()
            AudioManager.AUDIOFOCUS_GAIN -> onGain()
        }
    }

    fun request(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener(listener)
                .setAudioAttributes(attrs)
                .build()
            am.requestAudioFocus(req!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            req?.let { am.abandonAudioFocusRequest(it) }
            req = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(listener)
        }
    }
}
