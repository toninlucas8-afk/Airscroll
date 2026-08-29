package dev.airscroll.core.control

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import dev.airscroll.core.common.model.VolumeCommand
import kotlin.math.abs
import kotlin.math.sign

/**
 * Applica i gradini di volume prodotti dal motore.
 *
 * Lavora sullo stream musica: e' quello che l'utente si aspetta di cambiare
 * mentre guarda un video o ascolta qualcosa. Non serve alcun permesso.
 */
class VolumeController(context: Context) {

    private val audioManager = context.applicationContext.getSystemService<AudioManager>()
    private var lastUiAt = 0L

    fun apply(command: VolumeCommand) {
        val manager = audioManager ?: return
        val steps = command.steps
        if (steps == 0) return

        val direction = if (sign(steps.toFloat()) > 0) {
            AudioManager.ADJUST_RAISE
        } else {
            AudioManager.ADJUST_LOWER
        }

        // Mostriamo la barra di sistema, ma non a ogni singolo gradino: sarebbe
        // un lampeggio continuo.
        val now = SystemClock.uptimeMillis()
        val showUi = command.showUi && now - lastUiAt > UI_THROTTLE_MS
        if (showUi) lastUiAt = now
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0

        repeat(abs(steps).coerceAtMost(MAX_STEPS_PER_CALL)) { index ->
            runCatching {
                manager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    if (index == 0) flags else 0,
                )
            }
        }
    }

    fun currentVolumeFraction(): Float {
        val manager = audioManager ?: return 0f
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private companion object {
        const val UI_THROTTLE_MS = 350L
        const val MAX_STEPS_PER_CALL = 4
    }
}
