package dev.airscroll.apps.api

/**
 * Categoria del profilo: serve alla UI per raggruppare e per scegliere valori
 * di default sensati.
 */
enum class AppCategory {
    BROWSER,
    SOCIAL,
    READER,
    MEDIA,
    CUSTOM,
}

/**
 * Regolazioni fini dello scorrimento per una singola app.
 *
 * @param speedMultiplier moltiplicatore sulla velocita' massima globale.
 * @param curveGamma esponente della curva progressiva: piu' alto = partenza piu' morbida.
 * @param invertScroll inverte il verso, per le app che scorrono al contrario.
 * @param gripFractionX dove appoggiare il "dito invisibile" sull'asse orizzontale.
 * @param gripFractionY punto di partenza verticale del dito.
 * @param volumeEnabled se il controllo volume ha senso in questa app.
 */
data class ScrollTuning(
    val speedMultiplier: Float = 1.0f,
    val curveGamma: Float = 1.8f,
    val invertScroll: Boolean = false,
    val gripFractionX: Float = 0.5f,
    val gripFractionY: Float = 0.55f,
    val volumeEnabled: Boolean = true,
) {
    companion object {
        val Default = ScrollTuning()
    }
}

/**
 * Descrive un'app supportata da AirScroll.
 *
 * Aggiungere il supporto a una nuova app significa creare un modulo in `apps/`
 * che espone un [AppProfileProvider] con uno o piu' [AppProfile]: nessuna
 * modifica al motore.
 */
data class AppProfile(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>,
    val category: AppCategory,
    val tuning: ScrollTuning = ScrollTuning.Default,
    /** Se false l'app e' riconosciuta ma non accende la fotocamera da sola. */
    val armOnEnter: Boolean = true,
)

/** Punto di estensione implementato da ogni modulo in `apps/`. */
interface AppProfileProvider {
    val providerId: String
    val profiles: List<AppProfile>
}
