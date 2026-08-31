package dev.airscroll.app

import android.app.Application
import dev.airscroll.app.bootstrap.AppProfileBootstrap
import dev.airscroll.app.bootstrap.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AirScrollApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppProfileBootstrap.install()

        // Una versione nuova riparte da una calibrazione pulita.
        //
        // Gira prima di tutto il resto e una volta sola per avvio del processo.
        // Non e' un caso che stia qui e non dentro una schermata: se scattasse
        // all'apertura della Home, chi arriva dalla notifica con il servizio
        // gia' acceso continuerebbe a scorrere con numeri che il motore nuovo
        // interpreta diversamente, fino alla prima volta che apre l'app.
        //
        // Quello che azzera non sparisce in silenzio: resta scritto, e la Home
        // lo dice. Vedi CalibrationVersionGate.
        scope.launch {
            ServiceLocator.settings(this@AirScrollApplication)
                .applyVersionGate(BuildConfig.VERSION_NAME)
        }

        // I package aggiunti a mano dall'utente diventano profili a tutti gli
        // effetti, cosi' il motore non deve sapere da dove arrivano.
        scope.launch {
            ServiceLocator.settings(this@AirScrollApplication).settings
                .map { it.customPackages }
                .distinctUntilChanged()
                .collect { AppProfileBootstrap.syncUserPackages(it) }
        }
    }
}
