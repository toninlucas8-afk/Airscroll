package dev.airscroll.app.bootstrap

import dev.airscroll.apps.api.AppCategory
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.apps.api.AppProfileRegistry
import dev.airscroll.apps.browser.BrowserProfiles
import dev.airscroll.apps.reader.ReaderProfiles
import dev.airscroll.apps.social.SocialProfiles

/**
 * Unico punto in cui i moduli `apps/` vengono collegati al motore.
 *
 * Per aggiungere il supporto a una nuova app:
 *   1. crea `apps/<nome>` con un oggetto che implementa `AppProfileProvider`;
 *   2. aggiungi `include(":apps:<nome>")` in settings.gradle.kts;
 *   3. aggiungi la dipendenza in app/build.gradle.kts;
 *   4. aggiungi una riga qui sotto.
 *
 * Nessun'altra parte del progetto va toccata.
 */
object AppProfileBootstrap {

    fun install() {
        AppProfileRegistry.register(
            BrowserProfiles,
            SocialProfiles,
            ReaderProfiles,
        )
    }

    /** I package che l'utente ha aggiunto a mano dalle impostazioni. */
    fun syncUserPackages(packages: Set<String>) {
        AppProfileRegistry.setUserProfiles(
            packages.map { packageName ->
                AppProfile(
                    id = "user.$packageName",
                    displayName = packageName,
                    packageNames = setOf(packageName),
                    category = AppCategory.CUSTOM,
                )
            }
        )
    }
}
