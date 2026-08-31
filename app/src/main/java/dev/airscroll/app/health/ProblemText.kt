package dev.airscroll.app.health

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import dev.airscroll.app.MainActivity
import dev.airscroll.app.R
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.core.health.Problem

/**
 * Come si racconta un guasto, e dove si va a sistemarlo.
 *
 * Ogni problema ha tre pezzi e nessuno e' facoltativo: **cosa** e' successo,
 * **perche'** (che quasi sempre non e' colpa di chi legge), e **il pulsante che
 * porta esattamente dove si risolve**. Un avviso che dice solo "AirScroll non
 * funziona" e' peggio del silenzio: aggiunge preoccupazione senza aggiungere
 * potere di fare qualcosa.
 */

@StringRes
fun problemTitle(problem: Problem): Int = when (problem) {
    Problem.AFTER_REBOOT -> R.string.problem_reboot_title
    Problem.SERVICE_KILLED -> R.string.problem_service_killed_title
    Problem.ACCESSIBILITY_OFF -> R.string.problem_accessibility_title
    Problem.CAMERA_PERMISSION -> R.string.problem_camera_permission_title
    Problem.VISION_BROKEN -> R.string.problem_vision_title
    Problem.CAMERA_BUSY -> R.string.problem_camera_busy_title
    Problem.OVERLAY_MISSING -> R.string.problem_overlay_title
    Problem.BATTERY_RESTRICTED -> R.string.problem_battery_title
}

@StringRes
fun problemBody(problem: Problem): Int = when (problem) {
    Problem.AFTER_REBOOT -> R.string.problem_reboot_body
    Problem.SERVICE_KILLED -> R.string.problem_service_killed_body
    Problem.ACCESSIBILITY_OFF -> R.string.problem_accessibility_body
    Problem.CAMERA_PERMISSION -> R.string.problem_camera_permission_body
    Problem.VISION_BROKEN -> R.string.problem_vision_body
    Problem.CAMERA_BUSY -> R.string.problem_camera_busy_body
    Problem.OVERLAY_MISSING -> R.string.problem_overlay_body
    Problem.BATTERY_RESTRICTED -> R.string.problem_battery_body
}

/**
 * Il pulsante che risolve: un'etichetta e la schermata dove si va.
 *
 * I due pezzi stanno insieme perche' non hanno senso separati - un'etichetta
 * senza destinazione e' un pulsante finto - e perche' cosi' il caso "non c'e'
 * niente da premere" e' un `null` solo, invece di due che vanno tenuti
 * d'accordo a mano.
 */
data class Remedy(
    @StringRes val label: Int,
    val intent: Intent,
)

/**
 * Dove si risolve davvero il problema, o `null` se non si risolve li'.
 *
 * `null` non e' una dimenticanza: la fotocamera occupata da un'altra app non si
 * sistema in nessuna impostazione, si sistema chiudendo quell'app. Mettere un
 * pulsante che non risolve niente sarebbe peggio che non metterlo.
 */
fun remedy(context: Context, problem: Problem): Remedy? = when (problem) {
    Problem.SERVICE_KILLED,
    Problem.BATTERY_RESTRICTED,
    -> Remedy(
        R.string.problem_action_battery,
        AirScrollPermissions.batteryOptimizationIntent(context),
    )

    Problem.ACCESSIBILITY_OFF -> Remedy(
        R.string.problem_action_accessibility,
        AirScrollPermissions.accessibilitySettingsIntent(),
    )

    Problem.CAMERA_PERMISSION -> Remedy(
        R.string.problem_action_app_settings,
        AirScrollPermissions.appSettingsIntent(context),
    )

    Problem.OVERLAY_MISSING -> Remedy(
        R.string.problem_action_overlay,
        AirScrollPermissions.overlaySettingsIntent(context),
    )

    // Riaccendere si puo' solo dall'app: Android concede la fotocamera in
    // background solo a un servizio avviato mentre l'app e' in primo piano.
    Problem.AFTER_REBOOT -> Remedy(
        R.string.problem_action_open_app,
        Intent(context, MainActivity::class.java),
    )

    Problem.VISION_BROKEN -> Remedy(
        R.string.problem_action_open_app,
        Intent(context, MainActivity::class.java),
    )

    Problem.CAMERA_BUSY -> null
}
