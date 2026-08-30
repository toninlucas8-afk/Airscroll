package dev.airscroll.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.IndicatorCorner

/**
 * Aggiunge e rimuove l'indicatore dalla finestra di sistema.
 *
 * La finestra e' `NOT_TOUCHABLE` e `NOT_FOCUSABLE`: non intercetta nulla, non
 * ruba il fuoco, non compare negli screenshot delle app. E' solo un pixel di
 * informazione.
 */
class StatusOverlayController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(WindowManager::class.java)

    private var view: StatusIndicatorView? = null

    val canDraw: Boolean
        get() = Settings.canDrawOverlays(appContext)

    fun show(corner: IndicatorCorner) {
        if (!canDraw) return
        val manager = windowManager ?: return
        if (view != null) {
            update(corner)
            return
        }
        val indicator = StatusIndicatorView(appContext)
        val size = (appContext.resources.displayMetrics.density * SIZE_DP).toInt()
        val margin = (appContext.resources.displayMetrics.density * MARGIN_DP).toInt()

        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = corner.toGravity()
            // Al centro l'offset orizzontale va azzerato, altrimenti il
            // margine lo sposta di lato e non e' piu' centrato.
            x = if (corner == IndicatorCorner.TOP_CENTER) 0 else margin
            y = if (corner == IndicatorCorner.TOP_CENTER) statusBarInset() else margin
        }

        runCatching { manager.addView(indicator, params) }
            .onSuccess { view = indicator }
            .onFailure { Log.w(TAG, "Overlay non aggiunto", it) }
    }

    fun update(corner: IndicatorCorner) {
        val indicator = view ?: return
        val manager = windowManager ?: return
        val params = indicator.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.gravity == corner.toGravity()) return
        params.gravity = corner.toGravity()
        val margin = (appContext.resources.displayMetrics.density * MARGIN_DP).toInt()
        params.x = if (corner == IndicatorCorner.TOP_CENTER) 0 else margin
        params.y = if (corner == IndicatorCorner.TOP_CENTER) statusBarInset() else margin
        runCatching { manager.updateViewLayout(indicator, params) }
    }

    fun render(state: EngineState, handPresent: Boolean) {
        view?.let {
            it.state = state
            it.handPresent = handPresent
        }
    }

    fun hide() {
        val indicator = view ?: return
        view = null
        runCatching { windowManager?.removeView(indicator) }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /**
     * Altezza della status bar, per posare il puntino dentro quella fascia
     * invece che sopra il contenuto dell'app.
     *
     * Se il sistema non la dichiara si ripiega su un valore ragionevole: e'
     * un'inezia di posizione, non vale un ramo di codice fragile.
     */
    private fun statusBarInset(): Int {
        val resources = appContext.resources
        val density = resources.displayMetrics.density
        val fallback = (density * MARGIN_DP).toInt()

        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (id > 0) resources.getDimensionPixelSize(id) else 0
        if (statusBarHeight <= 0) return fallback

        val centered = (statusBarHeight - density * SIZE_DP) / 2f
        return centered.toInt().coerceAtLeast(0)
    }

    private fun IndicatorCorner.toGravity(): Int = when (this) {
        IndicatorCorner.TOP_CENTER -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        IndicatorCorner.TOP_START -> Gravity.TOP or Gravity.START
        IndicatorCorner.TOP_END -> Gravity.TOP or Gravity.END
        IndicatorCorner.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
        IndicatorCorner.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
    }

    private companion object {
        const val TAG = "AirScroll/Overlay"
        const val SIZE_DP = 18f
        const val MARGIN_DP = 10f
    }
}
