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
            x = margin
            y = margin
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

    private fun IndicatorCorner.toGravity(): Int = when (this) {
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
