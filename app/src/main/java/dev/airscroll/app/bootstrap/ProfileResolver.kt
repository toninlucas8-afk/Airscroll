package dev.airscroll.app.bootstrap

import dev.airscroll.apps.api.AppProfile
import dev.airscroll.apps.api.AppProfileRegistry
import dev.airscroll.core.settings.AirScrollSettings

/** Risolve il package in primo piano tenendo conto dei profili disattivati. */
object ProfileResolver {

    fun resolve(packageName: String?, settings: AirScrollSettings): AppProfile? {
        val profile = AppProfileRegistry.profileFor(packageName) ?: return null
        if (profile.id in settings.disabledProfileIds) return null
        return profile
    }
}
