package dev.airscroll.core.gesture

import dev.airscroll.core.settings.CalibrationProfile
import kotlin.math.max
import kotlin.math.min

/**
 * La pagella della calibrazione.
 *
 * Fino alla 0.5.2 la calibrazione finiva con un "fatto" e basta: qualunque cosa
 * fosse stata misurata - anche una mano a mezzo metro fuori fuoco, anche un
 * cerchio percorso a meta' - il risultato veniva salvato e presentato allo
 * stesso modo. Poi, all'uso, uno dei due sensi dello scorrimento non andava, e
 * non c'era modo di sapere che la colpa era di una misura debole presa
 * trenta secondi prima.
 *
 * Qui si giudica cio' che e' stato misurato, con soglie dichiarate. Non e' un
 * voto per il gusto di darlo: serve a dire **quale pezzo va rifatto**, che e'
 * l'unica cosa utile da fare con un giudizio.
 *
 * Aritmetica pura, provata dai test: le soglie sono discutibili - e andranno
 * riviste sulle registrazioni vere - ma almeno sono scritte in un posto solo.
 */

/** Quanto e' buona una misura. */
enum class Grade {
    /** Si puo' usare cosi'. */
    GOOD,

    /** Funziona, ma si sentira'. */
    FAIR,

    /** Va rifatta: tenerla farebbe piu' danno che bene. */
    POOR,
}

/** I pezzi della calibrazione che possono essere deboli, uno per uno. */
enum class Aspect {
    /** Quanto e' lontana la mano dalla fotocamera. */
    FRAMING,

    /** Quanto e' ferma la mano da ferma: da qui esce la zona neutra. */
    STILLNESS,

    /** Le quattro portate: quanto lontano arriva la mano in ogni verso. */
    REACH_UP,
    REACH_DOWN,
    REACH_LEFT,
    REACH_RIGHT,

    /** Il pollice in su e il pugno chiuso sono stati riconosciuti. */
    GESTURES,
}

data class AspectScore(
    val aspect: Aspect,
    val grade: Grade,
    /** Il valore misurato, per poterlo mostrare invece di solo giudicarlo. */
    val measured: Float,
)

data class CalibrationReport(
    val scores: List<AspectScore>,
    val overall: Grade,
    /**
     * true quando un verso arriva molto piu' lontano dell'opposto.
     *
     * Non e' un errore - i bracci sono fatti cosi' - ma e' esattamente la
     * condizione che alla prova fa dire "in giu' funziona, in su no", ed e'
     * bene saperlo prima invece che dopo.
     */
    val lopsided: Boolean,
) {
    /** Il pezzo peggiore, cioe' quello che conviene rifare. Null se va tutto bene. */
    val weakest: AspectScore?
        get() = scores.filter { it.grade != Grade.GOOD }.maxByOrNull { it.grade.ordinal }
}

/**
 * Giudica una calibrazione appena presa.
 *
 * [thumbRecognised] e [fistRecognised] arrivano dalla prova dei gesti: senza
 * quelli non si misura niente di nuovo, si verifica che i due comandi che
 * accendono e spengono tutto funzionino **su questa mano**. E' la verifica che
 * mancava del tutto: si poteva finire una calibrazione perfetta e scoprire solo
 * in uso che il proprio pollice in su non veniva mai riconosciuto.
 */
fun reportFor(
    profile: CalibrationProfile,
    thumbRecognised: Boolean,
    fistRecognised: Boolean,
): CalibrationReport {
    val scores = listOf(
        AspectScore(Aspect.FRAMING, gradeFraming(profile.referenceHandSpan), profile.referenceHandSpan),
        AspectScore(Aspect.STILLNESS, gradeTremor(profile.tremor), profile.tremor),
        AspectScore(Aspect.REACH_UP, gradeReach(profile.reachUp), profile.reachUp),
        AspectScore(Aspect.REACH_DOWN, gradeReach(profile.reachDown), profile.reachDown),
        AspectScore(Aspect.REACH_LEFT, gradeReach(profile.reachLeft), profile.reachLeft),
        AspectScore(Aspect.REACH_RIGHT, gradeReach(profile.reachRight), profile.reachRight),
        AspectScore(
            Aspect.GESTURES,
            gradeGestures(thumbRecognised, fistRecognised),
            if (thumbRecognised && fistRecognised) 1f else 0f,
        ),
    )
    return CalibrationReport(
        scores = scores,
        // Il voto complessivo e' il peggiore, non la media: una calibrazione e'
        // buona quanto il suo pezzo piu' debole, perche' e' li' che l'utente
        // sbattera'.
        overall = scores.maxByOrNull { it.grade.ordinal }?.grade ?: Grade.GOOD,
        lopsided = isLopsided(profile.reachUp, profile.reachDown) ||
            isLopsided(profile.reachLeft, profile.reachRight),
    )
}

/**
 * La mano deve stare a una distanza in cui la fotocamera la vede bene.
 *
 * Troppo lontana e i punti della mano diventano rumore; troppo vicina ed esce
 * dall'inquadratura appena si muove.
 */
private fun gradeFraming(handSpan: Float): Grade = when {
    handSpan in GOOD_SPAN_MIN..GOOD_SPAN_MAX -> Grade.GOOD
    handSpan in FAIR_SPAN_MIN..FAIR_SPAN_MAX -> Grade.FAIR
    else -> Grade.POOR
}

/** Piu' la mano trema da ferma, piu' larga deve essere la zona morta. */
private fun gradeTremor(tremor: Float): Grade = when {
    tremor <= GOOD_TREMOR -> Grade.GOOD
    tremor <= FAIR_TREMOR -> Grade.FAIR
    else -> Grade.POOR
}

/** Una portata corta rende quel verso nervoso e difficile da dosare. */
private fun gradeReach(reach: Float): Grade = when {
    reach >= GOOD_REACH -> Grade.GOOD
    reach >= FAIR_REACH -> Grade.FAIR
    else -> Grade.POOR
}

private fun gradeGestures(thumb: Boolean, fist: Boolean): Grade = when {
    thumb && fist -> Grade.GOOD
    thumb || fist -> Grade.FAIR
    else -> Grade.POOR
}

/** Due versi opposti sono sbilanciati se uno arriva molto piu' lontano. */
private fun isLopsided(a: Float, b: Float): Boolean {
    val piccolo = min(a, b)
    val grande = max(a, b)
    if (piccolo <= 0f) return true
    return grande / piccolo >= LOPSIDED_RATIO
}

/**
 * Cosa c'e' che non va **adesso**, prima ancora di misurare.
 *
 * E' il passo che mancava del tutto: la calibrazione partiva comunque, e se la
 * mano era fuori fuoco o mezza fuori inquadratura misurava lo stesso, dando un
 * profilo sbagliato con l'aria di essere a posto. Un metro storto e' peggio di
 * nessun metro.
 */
enum class FramingHint {
    /** Nessuna mano in vista. */
    NO_HAND,

    /** Troppo lontana: i punti della mano diventano rumore. */
    TOO_FAR,

    /** Troppo vicina: esce dall'inquadratura appena si muove. */
    TOO_CLOSE,

    /**
     * Dentro l'inquadratura ma sul bordo.
     *
     * Da li' il cerchio non si potrebbe chiudere: meta' delle direzioni
     * finirebbero fuori campo, e verrebbero misurate corte.
     */
    OFF_CENTRE,

    /** Si puo' cominciare. */
    OK,
}

/**
 * Giudica l'inquadratura di un singolo fotogramma.
 *
 * Un fotogramma solo non decide niente - la mano sfarfalla - ma e' il mattone
 * su cui chi chiama tiene il conto dei fotogrammi buoni di fila.
 */
fun framingHint(
    present: Boolean,
    handSpan: Float,
    palmX: Float,
    palmY: Float,
): FramingHint = when {
    !present -> FramingHint.NO_HAND
    handSpan < FAIR_SPAN_MIN -> FramingHint.TOO_FAR
    handSpan > FAIR_SPAN_MAX -> FramingHint.TOO_CLOSE
    palmX !in CENTRE_MIN..CENTRE_MAX -> FramingHint.OFF_CENTRE
    palmY !in CENTRE_MIN..CENTRE_MAX -> FramingHint.OFF_CENTRE
    else -> FramingHint.OK
}

// Soglie. Sono scelte ragionate, non ancora misurate su registrazioni vere:
// quando arriveranno, e' qui che vanno cambiate, in un posto solo.
const val GOOD_SPAN_MIN = 0.10f
const val GOOD_SPAN_MAX = 0.28f
const val FAIR_SPAN_MIN = 0.07f
const val FAIR_SPAN_MAX = 0.36f
const val GOOD_TREMOR = 0.018f
const val FAIR_TREMOR = 0.030f
const val GOOD_REACH = 0.16f
const val FAIR_REACH = 0.10f
const val LOPSIDED_RATIO = 2.0f

/** Quanto ci si puo' allontanare dal centro dell'inquadratura per partire. */
const val CENTRE_MIN = 0.18f
const val CENTRE_MAX = 0.82f
