package dev.airscroll.core.settings


/**
 * Il profilo, scritto in un file che si puo' leggere.
 *
 * Serve a due cose concrete. La prima: cambiare telefono senza rifare tutto da
 * capo. La seconda, meno ovvia e per ora piu' frequente, e' che finche' le
 * versioni non sono firmate con una chiave stabile ogni aggiornamento obbliga a
 * disinstallare - e disinstallare porta via la calibrazione. Poter salvare il
 * profilo prima toglie il danno.
 *
 * Il formato e' testo semplice, `chiave = valore`, per la stessa ragione per
 * cui le registrazioni del laboratorio sono in CSV: chi lo esporta deve poterlo
 * **aprire e guardare**, e vedere che dentro non c'e' niente che non si aspetta.
 * Un file binario chiederebbe di fidarsi.
 *
 * Le regole di lettura sono due, e nascono dallo stesso principio - un profilo
 * vecchio o rovinato non deve poter peggiorare le cose:
 *
 *  - una chiave sconosciuta viene **ignorata**, non fa fallire l'import: cosi'
 *    un file scritto da una versione piu' recente resta utilizzabile;
 *  - una chiave assente lascia il valore che c'e' gia', invece di azzerarlo.
 */
object ProfileTransfer {

    /** Cambia solo se il formato diventa incompatibile, non a ogni campo nuovo. */
    const val FORMAT_VERSION = 1

    private const val HEADER = "# AirScroll - profilo"

    fun encode(settings: AirScrollSettings): String {
        val calibration = settings.calibration
        val righe = buildList {
            add(HEADER)
            add("# Puoi aprirlo e leggerlo: sono solo numeri e scelte tue.")
            add("formato = $FORMAT_VERSION")
            add("")
            add("# --- calibrazione: misurata sulla tua mano ---")
            add("cal_completata = ${calibration.completed}")
            add("cal_mano = ${calibration.referenceHandSpan}")
            add("cal_tremore = ${calibration.tremor}")
            add("cal_portata_su = ${calibration.reachUp}")
            add("cal_portata_giu = ${calibration.reachDown}")
            add("cal_portata_sinistra = ${calibration.reachLeft}")
            add("cal_portata_destra = ${calibration.reachRight}")
            add("")
            add("# --- come la mano comanda ---")
            add("modo = ${settings.scrollMode.name}")
            add("sensibilita = ${settings.sensitivity}")
            add("zona_neutra = ${settings.neutralZoneScale}")
            add("velocita_massima = ${settings.maxScrollSpeedPxPerSec}")
            add("inverti = ${settings.invertScroll}")
            add("azione_orizzontale = ${settings.horizontalAction.name}")
            add("gradini_volume = ${settings.maxVolumeStepsPerSec}")
            add("")
            add("# --- tempi ---")
            add("attesa_ms = ${settings.waitingWindowMs}")
            add("pugno_ms = ${settings.stopHoldMs}")
            add("pollice_ms = ${settings.activationHoldMs}")
            add("")
            add("# --- il resto ---")
            add("prestazioni = ${settings.performanceMode.name}")
            add("situazione = ${settings.situationMode.name}")
            add("risparmio = ${settings.powerSaving}")
            add("indicatore = ${settings.indicatorEnabled}")
            add("angolo = ${settings.indicatorCorner.name}")
            add("vibrazione = ${settings.hapticsEnabled}")
            add("voce = ${settings.voiceEnabled}")
        }
        return righe.joinToString("\n") + "\n"
    }

    /**
     * Legge un profilo e lo applica sopra quello dato.
     *
     * Restituisce null solo se il file non e' un profilo AirScroll: meglio non
     * fare niente che applicare mezzo file preso da chissa' dove.
     */
    fun decode(text: String, current: AirScrollSettings): AirScrollSettings? {
        if (!text.contains(HEADER)) return null

        val valori = HashMap<String, String>()
        text.lineSequence().forEach { riga ->
            val pulita = riga.substringBefore('#').trim()
            if (pulita.isEmpty()) return@forEach
            val separatore = pulita.indexOf('=')
            if (separatore <= 0) return@forEach
            valori[pulita.take(separatore).trim()] = pulita.drop(separatore + 1).trim()
        }
        if (valori["formato"].isNullOrBlank()) return null

        val calibration = current.calibration.copy(
            completed = valori.bool("cal_completata", current.calibration.completed),
            referenceHandSpan = valori.float("cal_mano", current.calibration.referenceHandSpan),
            tremor = valori.float("cal_tremore", current.calibration.tremor),
            reachUp = valori.float("cal_portata_su", current.calibration.reachUp),
            reachDown = valori.float("cal_portata_giu", current.calibration.reachDown),
            reachLeft = valori.float("cal_portata_sinistra", current.calibration.reachLeft),
            reachRight = valori.float("cal_portata_destra", current.calibration.reachRight),
            // La data e' di questo import, non del file: dice quando questo
            // telefono ha ricevuto il profilo, che e' l'unica cosa vera.
            calibratedAtMillis = current.calibration.calibratedAtMillis,
        ).withDerivedRanges()

        return current.copy(
            calibration = calibration,
            scrollMode = valori.enum("modo", current.scrollMode),
            sensitivity = valori.float("sensibilita", current.sensitivity),
            neutralZoneScale = valori.float("zona_neutra", current.neutralZoneScale),
            maxScrollSpeedPxPerSec = valori.float("velocita_massima", current.maxScrollSpeedPxPerSec),
            invertScroll = valori.bool("inverti", current.invertScroll),
            horizontalAction = valori.enum("azione_orizzontale", current.horizontalAction),
            maxVolumeStepsPerSec = valori.float("gradini_volume", current.maxVolumeStepsPerSec),
            waitingWindowMs = valori.long("attesa_ms", current.waitingWindowMs),
            stopHoldMs = valori.long("pugno_ms", current.stopHoldMs),
            activationHoldMs = valori.long("pollice_ms", current.activationHoldMs),
            performanceMode = valori.enum("prestazioni", current.performanceMode),
            situationMode = valori.enum("situazione", current.situationMode),
            powerSaving = valori.bool("risparmio", current.powerSaving),
            indicatorEnabled = valori.bool("indicatore", current.indicatorEnabled),
            indicatorCorner = valori.enum("angolo", current.indicatorCorner),
            hapticsEnabled = valori.bool("vibrazione", current.hapticsEnabled),
            voiceEnabled = valori.bool("voce", current.voiceEnabled),
        )
    }

    // Un valore illeggibile vale come assente: si tiene quello che c'e' gia'.
    // Un profilo rovinato non deve poter peggiorare la situazione.
    private fun Map<String, String>.float(key: String, fallback: Float) =
        this[key]?.toFloatOrNull() ?: fallback

    private fun Map<String, String>.long(key: String, fallback: Long) =
        this[key]?.toLongOrNull() ?: fallback

    private fun Map<String, String>.bool(key: String, fallback: Boolean) =
        when (this[key]?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> fallback
        }

    private inline fun <reified T : Enum<T>> Map<String, String>.enum(key: String, fallback: T): T =
        this[key]?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
