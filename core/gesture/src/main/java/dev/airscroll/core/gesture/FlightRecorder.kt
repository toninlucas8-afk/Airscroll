package dev.airscroll.core.gesture

import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal

/**
 * Gli ultimi due minuti, sempre pronti da mandare.
 *
 * Nasce da un fallimento pratico: le soglie di riconoscimento di AirScroll sono
 * scelte ragionate e non misurate, e per misurarle serve una registrazione di
 * movimenti veri. La registrazione si poteva gia' fare - cinque prese guidate
 * in laboratorio - ma nessuno la fa, perche' e' un compito da svolgere *prima*,
 * quando tutto funziona, per un problema che si presenta *dopo*.
 *
 * Qui si ribalta il costo. AirScroll tiene in memoria gli ultimi due minuti
 * mentre lo si usa normalmente; quando qualcosa va storto si preme un pulsante
 * e si manda **quel momento li'**, invece di provare a riprodurlo a comando.
 *
 * Tre cose che questa scatola nera non fa, e che non deve fare:
 *
 *  - non tocca il disco finche' non si preme il pulsante: e' un anello in
 *    memoria che si sovrascrive da solo;
 *  - non contiene **nessuna immagine**, mai: solo la posizione del palmo, la
 *    sua dimensione apparente, il gesto riconosciuto e cosa ha deciso il
 *    motore;
 *  - non parte da sola e non manda niente da sola. Non potrebbe nemmeno
 *    volendo - l'app non ha il permesso di rete - ma la decisione resta di chi
 *    preme.
 */
class FlightRecorder(private val capacity: Int = DEFAULT_CAPACITY) {

    /**
     * Un istante: cosa ha visto la fotocamera e cosa ne ha fatto il motore.
     *
     * Le due cose insieme, non solo la prima: sapere che la mano era a 0.3 non
     * dice niente se non si sa che il motore in quel momento non stava
     * scorrendo. E' la differenza fra una registrazione e una diagnosi.
     */
    data class Sample(
        val timestampMs: Long,
        val present: Boolean,
        val signal: HandSignal,
        val confidence: Float,
        val palmX: Float,
        val palmY: Float,
        val handSpan: Float,
        val state: EngineState,
        val scrollVelocity: Float,
    )

    private val samples = ArrayDeque<Sample>(capacity)

    val size: Int get() = samples.size

    fun record(frame: HandFrame, state: EngineState, scrollVelocity: Float) {
        record(
            Sample(
                timestampMs = frame.timestampMs,
                present = frame.present,
                signal = frame.signal,
                confidence = frame.signalConfidence,
                palmX = frame.palmX,
                palmY = frame.palmY,
                handSpan = frame.handSpan,
                state = state,
                scrollVelocity = scrollVelocity,
            )
        )
    }

    fun record(sample: Sample) {
        if (samples.size >= capacity) samples.removeFirst()
        samples.addLast(sample)
    }

    /** Una copia di quello che c'e' adesso. */
    fun snapshot(): List<Sample> = samples.toList()

    fun clear() = samples.clear()

    /** Quanti secondi copre la registrazione in memoria. */
    fun spanSeconds(): Float {
        if (samples.size < 2) return 0f
        return (samples.last().timestampMs - samples.first().timestampMs) / 1000f
    }

    companion object {
        /**
         * Due minuti a venti fotogrammi al secondo.
         *
         * Due minuti perche' e' il tempo entro cui uno si accorge che qualcosa
         * non va e prende in mano il telefono. Piu' lungo non aiuterebbe -
         * nessuno cerca l'episodio dentro dieci minuti di registrazione - e
         * costerebbe memoria per niente.
         */
        const val DEFAULT_CAPACITY = 2_400
    }
}
