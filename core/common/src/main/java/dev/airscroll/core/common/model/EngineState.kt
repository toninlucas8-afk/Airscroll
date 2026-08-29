package dev.airscroll.core.common.model

/**
 * Stati del motore AirScroll.
 *
 * L'indicatore a schermo mostra: rosso = [DISABLED]/[IDLE], giallo = [WAITING],
 * verde = [ACTIVE].
 */
enum class EngineState {
    /** Il servizio e' spento dall'utente. Fotocamera chiusa. */
    DISABLED,

    /** Servizio attivo ma nessuna app compatibile in primo piano. Fotocamera chiusa. */
    IDLE,

    /** Fotocamera accesa per pochi secondi: cerchiamo solo il gesto di attivazione. */
    WAITING,

    /** Gesto riconosciuto: la mano guida lo scroll. */
    ACTIVE;

    val isCameraNeeded: Boolean get() = this == WAITING || this == ACTIVE
}
