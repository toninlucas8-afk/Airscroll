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
