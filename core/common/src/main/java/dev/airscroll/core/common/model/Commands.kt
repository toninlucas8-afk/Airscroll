package dev.airscroll.core.common.model

/**
 * Comando di scorrimento continuo.
 *
 * @param velocityPxPerSec velocita' del "dito invisibile" in pixel al secondo.
 *   Positiva = il dito scende (la pagina torna verso l'alto). 0 = fermo.
 * @param gripFractionX posizione orizzontale del dito, come frazione della
 *   larghezza schermo. Alcune app gradiscono un punto di appoggio diverso.
 */
data class ScrollCommand(
    val velocityPxPerSec: Float = 0f,
    val gripFractionX: Float = 0.5f,
    /** Posizione verticale iniziale del dito, come frazione dell'altezza schermo. */
    val gripFractionY: Float = 0.55f,
) {
    val isMoving: Boolean get() = velocityPxPerSec != 0f

    companion object {
        val Stopped = ScrollCommand()
    }
}

/** Un "gradino" di volume da applicare. */
data class VolumeCommand(
    val steps: Int,
    val showUi: Boolean = true,
)

/** Motivo per cui il motore ha cambiato stato: serve solo per feedback e log. */
enum class StateChangeReason {
    SERVICE_TOGGLED,
    APP_ENTERED,
    APP_LEFT,
    ACTIVATION_GESTURE,
    STOP_GESTURE,
    WAITING_TIMEOUT,
    HAND_LOST,
    ERROR,
}
