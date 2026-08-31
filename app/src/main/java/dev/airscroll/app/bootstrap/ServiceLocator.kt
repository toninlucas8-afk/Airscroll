package dev.airscroll.app.bootstrap

import android.content.Context
import dev.airscroll.core.gesture.FlightRecorder
import dev.airscroll.core.settings.SettingsRepository

/**
 * Iniezione delle dipendenze ridotta all'osso.
 *
 * Il progetto ha una sola dipendenza condivisa fra Activity e servizi: il
 * repository delle impostazioni. Aggiungere Hilt costerebbe piu' di quanto
 * renda.
 */
object ServiceLocator {

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    /**
     * La scatola nera degli ultimi due minuti.
     *
     * Sta qui perche' e' condivisa fra chi scrive e chi legge: il servizio ci
     * registra dentro mentre l'app viene usata, la schermata principale la
     * legge quando si preme "manda gli ultimi due minuti". Vivono nello stesso
     * processo, quindi non serve niente di piu' complicato di un oggetto solo.
     */
    val flightRecorder: FlightRecorder by lazy { FlightRecorder() }

    fun settings(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext)
                .also { settingsRepository = it }
        }
}
