package dev.airscroll.core.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewConfiguration
import android.view.WindowManager
import dev.airscroll.core.common.model.ScrollCommand
import kotlin.math.abs
import kotlin.math.sign

/**
 * Trasforma una velocita' in uno scorrimento continuo, come un dito appoggiato
 * allo schermo che si muove senza mai staccarsi.
 *
 * Il trucco e' `StrokeDescription.continueStroke`: si dispatcha un segmento
 * breve alla volta (~60 ms) e, quando il sistema conferma di averlo eseguito, si
 * aggancia il successivo leggendo la velocita' aggiornata. Il risultato non ha
 * gli scatti dei comandi `ACTION_SCROLL_FORWARD` e funziona in qualunque app
 * che risponda al tocco, anche dentro WebView e liste custom.
 *
 * Quando il dito arriva a fine corsa lo si stacca e lo si riappoggia al centro
 * ("re-grip"), esattamente come farebbe una persona.
 */
class ScrollDispatcher(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private var lastStroke: GestureDescription.StrokeDescription? = null
    private var running = false
    private var dispatchInFlight = false

    private var pointerX = 0f
    private var pointerY = 0f
    private var velocity = 0f
    private var gripFractionX = 0.5f
    private var gripFractionY = 0.55f
    private var strokeStartedAt = 0L
    private var idleSinceMs = NOT_IDLE
    private var jitterSign = 1f

    /** Ultimo comando ricevuto. Chiamabile ad alta frequenza: e' quasi gratis. */
    fun update(command: ScrollCommand) {
        velocity = command.velocityPxPerSec
        gripFractionX = command.gripFractionX
        gripFractionY = command.gripFractionY
        if (velocity != 0f && !running) {
            press()
        }
    }

    /** Stacca il dito e azzera tutto. */
    fun stop() {
        velocity = 0f
        if (running && !dispatchInFlight) {
            lift()
        } else {
            running = false
        }
    }

    /** Da chiamare quando il servizio viene distrutto. */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        running = false
        dispatchInFlight = false
        lastStroke = null
    }

    private fun press() {
        val size = screenSize()
        if (size.x <= 0 || size.y <= 0) return

        pointerX = (size.x * gripFractionX).coerceIn(EDGE_MARGIN_PX, size.x - EDGE_MARGIN_PX)
        pointerY = startY(size)

        // Primo scatto abbastanza ampio da superare il "touch slop": senza questo
        // il sistema potrebbe interpretare l'appoggio come un tocco e aprire un link.
        val kick = (touchSlop * 1.6f + 8f) * sign(velocity).let { if (it == 0f) 1f else it }
        val targetY = (pointerY + kick).coerceIn(verticalMargin(size), size.y - verticalMargin(size))

        val path = Path().apply {
            moveTo(pointerX, pointerY)
            lineTo(pointerX, targetY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SEGMENT_MS, true)
        pointerY = targetY
        strokeStartedAt = SystemClock.uptimeMillis()
        idleSinceMs = NOT_IDLE
        running = true
        dispatch(stroke)
    }

    private fun continueSegment() {
        if (!running) {
            lift()
            return
        }

        val size = screenSize()
        val topLimit = verticalMargin(size)
        val bottomLimit = size.y - verticalMargin(size)

        val now = SystemClock.uptimeMillis()
        var delta = velocity * SEGMENT_MS / 1000f

        if (abs(delta) < 1f) {
            // Fermi ma ancora "appoggiati": micro-oscillazione a somma nulla per
            // tenere vivo il gesto senza muovere davvero la pagina.
            if (idleSinceMs == NOT_IDLE) idleSinceMs = now
            if (now - idleSinceMs > IDLE_LIFT_MS) {
                lift()
                return
            }
            delta = jitterSign
            jitterSign = -jitterSign
        } else {
            idleSinceMs = NOT_IDLE
        }

        var targetY = pointerY + delta
        var mustRegrip = false
        if (targetY <= topLimit || targetY >= bottomLimit) {
            targetY = targetY.coerceIn(topLimit, bottomLimit)
            mustRegrip = true
        }
        if (now - strokeStartedAt > MAX_STROKE_MS) mustRegrip = true

        if (abs(targetY - pointerY) < 0.5f) {
            // Path di lunghezza nulla: il sistema lo rifiuta. Meglio riappoggiare.
            regrip()
            return
        }

        val path = Path().apply {
            moveTo(pointerX, pointerY)
            lineTo(pointerX, targetY)
        }
        val previous = lastStroke
        if (previous == null) {
            press()
            return
        }

        val stroke = runCatching {
            previous.continueStroke(path, 0L, SEGMENT_MS, !mustRegrip)
        }.getOrNull()

        if (stroke == null) {
            regrip()
            return
        }

        pointerY = targetY
        if (mustRegrip) {
            dispatch(stroke, thenRegrip = true)
        } else {
            dispatch(stroke)
        }
    }

    private fun regrip() {
        lastStroke = null
        running = false
        if (velocity != 0f) {
            handler.postDelayed({ if (velocity != 0f) press() }, REGRIP_PAUSE_MS)
        }
    }

    private fun lift() {
        val previous = lastStroke
        running = false
        lastStroke = null
        if (previous == null) return
        val path = Path().apply {
            moveTo(pointerX, pointerY)
            lineTo(pointerX, pointerY + 1f)
        }
        val closing = runCatching { previous.continueStroke(path, 0L, LIFT_MS, false) }.getOrNull()
            ?: return
        dispatchRaw(closing, null)
    }

    private fun dispatch(
        stroke: GestureDescription.StrokeDescription,
        thenRegrip: Boolean = false,
    ) {
        lastStroke = stroke
        dispatchRaw(stroke) {
            if (thenRegrip) regrip() else continueSegment()
        }
    }

    private fun dispatchRaw(
        stroke: GestureDescription.StrokeDescription,
        onDone: (() -> Unit)?,
    ) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                dispatchInFlight = false
                onDone?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                dispatchInFlight = false
                // Succede quando l'utente tocca davvero lo schermo, o su alcune
                // schermate di sistema: si riparte da capo appena possibile.
                regrip()
            }
        }

        dispatchInFlight = true
        val accepted = runCatching { service.dispatchGesture(gesture, callback, handler) }
            .getOrElse { error ->
                Log.w(TAG, "dispatchGesture non riuscita", error)
                false
            }
        if (!accepted) {
            dispatchInFlight = false
            running = false
            lastStroke = null
            if (velocity != 0f) handler.postDelayed({ if (velocity != 0f) press() }, RETRY_MS)
        }
    }

    /**
     * Dove appoggiare il dito, in funzione del verso in cui deve andare.
     *
     * Prima si appoggiava sempre a `gripFractionY` - fra il 50% e il 65%
     * dell'altezza a seconda dell'app - e da li' partiva in entrambi i versi.
     * Il risultato era che le due direzioni **non avevano la stessa corsa**.
     * Su uno schermo da 2400 px con margini al 18%, in Instagram
     * (`gripFractionY = 0.65`): 1128 px verso l'alto e appena 408 px verso il
     * basso, cioe' 2,8 volte meno. A 900 px/s sono 1,25 s di trascinamento
     * fluido da una parte e 0,45 s dall'altra, dopo i quali il dito arriva a
     * fondo corsa, si stacca e si riappoggia.
     *
     * In una lista che risponde all'inerzia, un trascinamento interrotto ogni
     * mezzo secondo non scorre quasi niente. Da qui il difetto segnalato alla
     * prova: **giu' funziona, su no**.
     *
     * Adesso il dito parte dall'estremita' opposta al verso di marcia, e ha
     * tutta la banda utile davanti a se': la stessa corsa in entrambi i versi,
     * qualunque sia l'app.
     */
    private fun startY(size: Point): Float {
        val top = verticalMargin(size)
        val bottom = size.y - verticalMargin(size)
        if (bottom <= top) return (top + bottom) / 2f
        // Un dito di margine oltre l'estremita', per lasciare spazio allo
        // scatto iniziale che deve superare il touch slop.
        val room = (touchSlop * 2f + 16f).coerceAtMost((bottom - top) / 4f)
        return when {
            velocity > 0f -> top + room      // il dito va verso il basso: parte in alto
            velocity < 0f -> bottom - room   // il dito va verso l'alto: parte in basso
            // Verso ignoto: si torna al punto preferito dal profilo dell'app.
            else -> (size.y * gripFractionY).coerceIn(top, bottom)
        }
    }

    private fun verticalMargin(size: Point): Float =
        (size.y * VERTICAL_MARGIN_FRACTION).coerceAtLeast(EDGE_MARGIN_PX)

    private fun screenSize(): Point {
        val windowManager = service.getSystemService(WindowManager::class.java) ?: return Point()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Point(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private companion object {
        const val TAG = "AirScroll/Scroll"
        const val NOT_IDLE = -1L

        /** Durata di ogni segmento: piu' corto = piu' reattivo ma piu' dispatch. */
        const val SEGMENT_MS = 60L
        const val LIFT_MS = 24L
        const val RETRY_MS = 120L
        const val REGRIP_PAUSE_MS = 40L

        /** Un gesto continuo non puo' durare all'infinito: si riappoggia il dito. */
        const val MAX_STROKE_MS = 45_000L

        /** Dopo questo tempo fermi si stacca il dito, per non bloccare i tocchi reali. */
        const val IDLE_LIFT_MS = 1_200L

        const val EDGE_MARGIN_PX = 24f
        const val VERTICAL_MARGIN_FRACTION = 0.18f
    }
}
