package dev.airscroll.core.camera

import kotlin.math.abs

/**
 * Decide se vale la pena analizzare un fotogramma o se e' identico al
 * precedente.
 *
 * Nello stato di attesa - telefono sul ripiano, nessuno davanti - la
 * stragrande maggioranza dei fotogrammi e' uguale a quello prima, e finora li
 * mandavo tutti al modello. L'inferenza e' la seconda voce di consumo dopo la
 * fotocamera stessa, e per la maggior parte del tempo la stavo spendendo per
 * riconfermare che non succede niente.
 *
 * Il confronto e' volutamente grezzo: qualche centinaio di campioni di
 * luminosita' presi a griglia. Costa qualche microsecondo contro i millisecondi
 * di un'inferenza, e per rispondere a "si e' mosso qualcosa?" e' abbastanza.
 *
 * **Non decide da solo quando essere usato.** Un pollice in su tenuto fermo e'
 * a tutti gli effetti una scena ferma: saltarlo significherebbe non riconoscere
 * proprio il gesto che si sta aspettando. Chi lo usa deve accenderlo solo
 * quando davvero non c'e' niente in corso.
 */
class StillnessDetector(
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val maxSkipStreak: Int = DEFAULT_MAX_SKIP_STREAK,
) {

    private var previous: IntArray? = null
    private var skipped = 0

    /** Quanti fotogrammi sono stati saltati da quando e' partito. */
    var totalSkipped: Long = 0L
        private set

    fun reset() {
        previous = null
        skipped = 0
    }

    /**
     * @param samples campioni di luminosita' del fotogramma, 0..255.
     * @return true se il fotogramma va analizzato.
     */
    fun shouldAnalyse(samples: IntArray): Boolean {
        val before = previous
        previous = samples

        if (before == null || before.size != samples.size || samples.isEmpty()) {
            skipped = 0
            return true
        }

        var total = 0L
        for (index in samples.indices) {
            total += abs(samples[index] - before[index])
        }
        val averageChange = total / samples.size

        if (averageChange >= threshold) {
            skipped = 0
            return true
        }

        // Anche con la scena immobile si analizza ogni tanto: una rete di
        // sicurezza contro il caso in cui il confronto sbagli e la fotocamera
        // resti accesa a non guardare mai niente.
        if (skipped >= maxSkipStreak) {
            skipped = 0
            return true
        }

        skipped++
        totalSkipped++
        return false
    }

    companion object {
        /**
         * Variazione media di luminosita' sotto la quale la scena e' ferma.
         *
         * Bassa di proposito: il rumore del sensore in penombra vale gia' due o
         * tre livelli, e sbagliare per eccesso di prudenza costa un'inferenza,
         * mentre sbagliare per difetto costa un gesto perso.
         */
        const val DEFAULT_THRESHOLD = 4

        /** Dopo questi salti si analizza comunque. */
        const val DEFAULT_MAX_SKIP_STREAK = 6
    }
}
