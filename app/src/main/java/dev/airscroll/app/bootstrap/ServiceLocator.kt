package dev.airscroll.app.bootstrap

import android.content.Context
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

    fun settings(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext)
                .also { settingsRepository = it }
        }
}
