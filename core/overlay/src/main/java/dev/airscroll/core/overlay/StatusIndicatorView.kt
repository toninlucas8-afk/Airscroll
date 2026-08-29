package dev.airscroll.core.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import dev.airscroll.core.common.model.EngineState
import kotlin.math.min
import kotlin.math.sin

/**
 * Il pallino di stato: rosso spento, giallo in attesa, verde attivo.
 *
 * Disegnato a mano invece che con Compose perche' deve stare dentro una finestra
 * di overlay leggerissima, senza portarsi dietro un albero di composizione.
 */
class StatusIndicatorView(context: Context) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var startedAt = System.currentTimeMillis()

    var state: EngineState = EngineState.IDLE
        set(value) {
            if (field == value) return
            field = value
            startedAt = System.currentTimeMillis()
            invalidate()
        }

    /** Mostra che la mano e' vista, senza cambiare colore di stato. */
    var handPresent: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f

        val color = when (state) {
            EngineState.DISABLED, EngineState.IDLE -> COLOR_IDLE
            EngineState.WAITING -> COLOR_WAITING
            EngineState.ACTIVE -> COLOR_ACTIVE
        }

        // In attesa il pallino respira: comunica "ti sto cercando" senza rumore.
        val breathing = if (state == EngineState.WAITING) {
            val phase = ((System.currentTimeMillis() - startedAt) % PULSE_PERIOD_MS) /
                PULSE_PERIOD_MS.toFloat()
            0.75f + 0.25f * sin(phase * 2f * Math.PI).toFloat()
        } else {
            1f
        }

        val core = radius * CORE_FRACTION * breathing

        haloPaint.color = color
        haloPaint.alpha = if (handPresent) 130 else 70
        haloPaint.strokeWidth = radius * 0.16f
        canvas.drawCircle(cx, cy, radius * 0.82f, haloPaint)

        fillPaint.color = color
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, core, fillPaint)

        if (state == EngineState.WAITING) postInvalidateOnAnimation()
    }

    private companion object {
        const val CORE_FRACTION = 0.52f
        const val PULSE_PERIOD_MS = 1400L
        val COLOR_IDLE = Color.parseColor("#E0483B")
        val COLOR_WAITING = Color.parseColor("#F2B33D")
        val COLOR_ACTIVE = Color.parseColor("#2DE39A")
    }
}
