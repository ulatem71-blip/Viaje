package com.viajerentable.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Short confirmation tone when the user records a trip as accepted. */
object TripAlertSound {
    fun play(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            if (!tone.startTone(ToneGenerator.TONE_PROP_ACK, 250)) {
                tone.release()
                return
            }
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
        } catch (_: Exception) {
            // Sound is optional; never interrupt saving a trip if audio is unavailable.
        }
    }
}
