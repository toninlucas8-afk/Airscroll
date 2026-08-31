package dev.airscroll.core.health

/**
 * Perche' esiste questo file.
 *
 * Fino alla 0.5.1 AirScroll poteva smettere di funzionare senza dire niente.
 * Il telefono chiude il servizio per risparmiare batteria, un'altra app si
 * prende la fotocamera, l'accessibilita' viene disattivata in un menu di
 * sistema: da fuori sono tutti identici. Muovi la mano e non succede niente, e
 * non c'e' modo di sapere perche'.
 *
 * E' lo stesso errore delle prime versioni - un guasto vero nascosto dietro
 * un'interfaccia che sembra a posto - e si chiude allo stesso modo: **il
 * guasto va nominato**, con la causa vera e il modo per rimetterlo a posto.
 *
 * Qui dentro c'e' solo la decisione, senza Android: date le condizioni, qual
 * e' il problema di cui parlare. Cosi' si puo' provare davvero, invece di
 * sperare che i casi limite siano stati previsti.
 */

/**
 * Quanto pesa un problema.
 *
 * Serve a decidere il tono: una notifica che urla per un indicatore che non si
 * vede insegna a ignorare le notifiche, e la volta che il servizio muore
 * davvero nessuno la legge.
 */
enum class Severity {
    /** Non funziona niente. Va detto subito e ad alta voce. */
    BLOCKING,

    /** Funziona a meta'. Va detto, senza svegliare nessuno. */
    DEGRADED,

    /** Funziona, ma c'e' una condizione che prima o poi lo rompera'. */
    WARNING,
}

/**
 * I guasti che AirScroll sa riconoscere di se stesso.
 *
 * L'ordine di dichiarazione **e'** l'ordine di priorita': [diagnose] restituisce
 * il primo che trova. Un servizio morto rende irrilevante tutto il resto, e non
 * ha senso parlare della fotocamera occupata a chi ha l'accessibilita' spenta.
 */
enum class Problem(val severity: Severity) {

    /**
     * L'interruttore e' acceso ma il servizio non c'e' piu'.
     *
     * Quasi sempre e' il costruttore del telefono: Xiaomi, Huawei, Samsung e
     * altri chiudono i servizi in background per risparmiare batteria, senza
     * avvisare nessuno.
     */
    SERVICE_KILLED(Severity.BLOCKING),

    /**
     * Il servizio di accessibilita' non e' connesso.
     *
     * Senza di lui il riconoscimento continua a funzionare ma non tocca piu'
     * niente: e' il braccio che esegue i gesti. Android lo disattiva da solo
     * dopo certi aggiornamenti di sistema.
     */
    ACCESSIBILITY_OFF(Severity.BLOCKING),

    /** Il permesso della fotocamera e' stato revocato. */
    CAMERA_PERMISSION(Severity.BLOCKING),

    /** MediaPipe non si avvia: senza riconoscimento non c'e' niente da fare. */
    VISION_BROKEN(Severity.BLOCKING),

    /**
     * Un'altra app ha preso la fotocamera.
     *
     * Non e' un guasto di AirScroll ed e' quasi sempre temporaneo - una
     * videochiamata, il lettore di QR - ma dal punto di vista di chi muove la
     * mano e' indistinguibile da un'app rotta.
     */
    CAMERA_BUSY(Severity.DEGRADED),

    /** L'indicatore e' acceso ma manca il permesso di disegnare sopra le app. */
    OVERLAY_MISSING(Severity.DEGRADED),

    /**
     * Il telefono e' libero di chiudere AirScroll quando vuole.
     *
     * Da solo non e' un guasto, ed e' il motivo per cui non compare subito:
     * comparirebbe su quasi tutti i telefoni, per un problema che magari non
     * si presentera' mai. Compare **dopo** che il servizio e' stato ucciso
     * almeno una volta, quando smette di essere una teoria.
     */
    BATTERY_RESTRICTED(Severity.WARNING),
}

/**
 * Le condizioni osservate, in un istante.
 *
 * Tutti campi che qualcuno sa misurare davvero su Android: nessuno di questi e'
 * una deduzione.
 */
data class HealthSnapshot(
    /** L'utente ha acceso l'interruttore di AirScroll. */
    val serviceEnabled: Boolean = false,
    /** Il foreground service risulta vivo. */
    val serviceRunning: Boolean = false,

    /**
     * Il servizio e' stato acceso in questo istante e sta ancora partendo.
     *
     * Fra il momento in cui si preme l'interruttore e quello in cui Android
     * elenca il servizio fra quelli vivi passano dei decimi di secondo. Senza
     * questa attesa, ogni accensione mostrerebbe per un attimo "AirScroll e'
     * stato chiuso" - un guasto inventato, proprio mentre l'utente sta facendo
     * la cosa giusta.
     */
    val serviceStarting: Boolean = false,
    /** L'AccessibilityService e' connesso. */
    val accessibilityConnected: Boolean = false,
    /** Il permesso della fotocamera e' concesso. */
    val cameraPermission: Boolean = true,
    /** Il riconoscitore si e' avviato senza errori. */
    val visionReady: Boolean = true,
    /** L'utente vuole l'indicatore a schermo. */
    val indicatorEnabled: Boolean = false,
    /** Il permesso di disegnare sopra le altre app e' concesso. */
    val canDrawOverlay: Boolean = true,
    /** AirScroll e' esente dalle ottimizzazioni della batteria. */
    val batteryUnrestricted: Boolean = true,
    /** Quante volte il sistema ha gia' chiuso il servizio da solo. */
    val systemKills: Int = 0,
    /** Quando la fotocamera ha dato errore l'ultima volta, o 0. */
    val lastCameraErrorAtMs: Long = 0L,
    /** Adesso. */
    val nowMs: Long = 0L,
)

/**
 * Il problema di cui vale la pena parlare, o `null` se va tutto bene.
 *
 * Uno solo alla volta, di proposito: un elenco di quattro guasti non si legge e
 * non si risolve. Sistemato il primo, al giro dopo compare il secondo.
 */
fun diagnose(snapshot: HealthSnapshot): Problem? {
    // Interruttore spento: non c'e' niente di rotto, c'e' una scelta.
    if (!snapshot.serviceEnabled) return null

    if (!snapshot.serviceRunning && !snapshot.serviceStarting) return Problem.SERVICE_KILLED
    if (!snapshot.accessibilityConnected) return Problem.ACCESSIBILITY_OFF
    if (!snapshot.cameraPermission) return Problem.CAMERA_PERMISSION
    if (!snapshot.visionReady) return Problem.VISION_BROKEN

    if (isCameraErrorFresh(snapshot)) return Problem.CAMERA_BUSY
    if (snapshot.indicatorEnabled && !snapshot.canDrawOverlay) return Problem.OVERLAY_MISSING

    // L'avviso sulla batteria solo dopo che il danno si e' visto almeno una
    // volta: prima e' una supposizione sul comportamento del telefono, dopo e'
    // la spiegazione di una cosa che e' successa davvero.
    if (!snapshot.batteryUnrestricted && snapshot.systemKills > 0) {
        return Problem.BATTERY_RESTRICTED
    }

    return null
}

/**
 * Un errore della fotocamera conta solo se e' appena successo.
 *
 * Le app si passano la fotocamera in continuazione e quasi sempre torna libera
 * da sola: tenere il messaggio acceso per un errore di mezzo minuto fa
 * diventa rumore.
 */
private fun isCameraErrorFresh(snapshot: HealthSnapshot): Boolean {
    if (snapshot.lastCameraErrorAtMs <= 0L) return false
    val age = snapshot.nowMs - snapshot.lastCameraErrorAtMs
    return age in 0 until CAMERA_ERROR_TTL_MS
}

/** Quanto resta valido un errore della fotocamera. */
const val CAMERA_ERROR_TTL_MS = 12_000L
