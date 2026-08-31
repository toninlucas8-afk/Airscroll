package dev.airscroll.core.settings

/**
 * Ogni versione nuova riparte da una calibrazione pulita.
 *
 * Ha due ragioni, e la seconda e' quella vera.
 *
 * La prima e' pratica: finche' le versioni non sono firmate con una chiave
 * stabile, aggiornare significa disinstallare, e disinstallare porta via tutto.
 * Da questo lato l'impostazione non cambia niente - succede gia'.
 *
 * La seconda vale anche dopo: **una calibrazione misurata da un'altra versione
 * non e' detto che voglia dire ancora la stessa cosa.** Fra la 0.4.4 e la 0.6.0
 * la calibrazione e' passata da due numeri a quattro portate distinte, la
 * finestra della mano ferma da due secondi e mezzo a quattro, e sono comparsi
 * un controllo dell'inquadratura e una prova dei gesti. Tenere i numeri vecchi
 * dentro un motore nuovo e' il modo migliore per inseguire un difetto che non
 * esiste nel codice, ma nei dati.
 *
 * Percio': si azzera, e **si dice** che si e' azzerato. Un'app che cancella
 * qualcosa in silenzio e' peggio di una che chiede di rifarlo.
 *
 * Un profilo **importato** da un file non viene mai azzerato al primo avvio:
 * chi importa ha deciso adesso, e cancellargli subito quello che ha appena
 * scelto sarebbe il contrario di quello che ha chiesto. Se ne occupa
 * `SettingsRepository.applyProfile`, che marca il profilo importato con la
 * versione che sta girando.
 *
 * L'interruttore esiste perche' questo ha senso mentre le versioni escono ogni
 * giorno, e smette di averlo quando escono ogni mese: a quel punto si spegne,
 * senza dover aspettare una versione nuova apposta.
 */
object CalibrationVersionGate {

    /**
     * Va azzerata la calibrazione?
     *
     * @param calibratedVersion la versione con cui era stata misurata. Vuota
     *   vuol dire che non lo sappiamo, cioe' che l'ha misurata una versione
     *   precedente a questo controllo: e' proprio il caso da azzerare.
     * @param currentVersion la versione che sta girando adesso.
     * @param calibrationCompleted se una calibrazione esiste davvero.
     * @param enabled l'impostazione dell'utente.
     */
    fun shouldReset(
        calibratedVersion: String,
        currentVersion: String,
        calibrationCompleted: Boolean,
        enabled: Boolean,
    ): Boolean {
        if (!enabled) return false
        // Niente calibrazione, niente da azzerare. Serve soprattutto a non
        // annunciare "l'aggiornamento te l'ha azzerata" a chi ha appena
        // installato l'app e non ne ha mai fatta una.
        if (!calibrationCompleted) return false
        // Senza sapere che versione sta girando non si decide niente: meglio
        // tenere una calibrazione forse vecchia che cancellarne una buona.
        if (currentVersion.isBlank()) return false
        return calibratedVersion != currentVersion
    }
}
