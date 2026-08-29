package dev.airscroll.apps.api

import java.util.concurrent.ConcurrentHashMap

/**
 * Registro dei profili applicazione.
 *
 * I moduli si registrano una sola volta all'avvio (vedi `AppProfileBootstrap`
 * nel modulo :app). La ricerca per package e' O(1) perche' teniamo un indice.
 */
object AppProfileRegistry {

    private val providers = ConcurrentHashMap<String, AppProfileProvider>()
    private val index = ConcurrentHashMap<String, AppProfile>()

    fun register(vararg newProviders: AppProfileProvider) {
        newProviders.forEach { provider ->
            providers[provider.providerId] = provider
        }
        rebuildIndex()
    }

    fun unregister(providerId: String) {
        providers.remove(providerId)
        rebuildIndex()
    }

    /** Profili aggiunti a mano dall'utente dalle impostazioni. */
    fun setUserProfiles(userProfiles: List<AppProfile>) {
        register(
            object : AppProfileProvider {
                override val providerId = USER_PROVIDER_ID
                override val profiles = userProfiles
            }
        )
    }

    fun profileFor(packageName: String?): AppProfile? =
        packageName?.let { index[it] }

    fun all(): List<AppProfile> =
        providers.values.flatMap { it.profiles }.sortedBy { it.displayName.lowercase() }

    fun providerIds(): Set<String> = providers.keys.toSet()

    private fun rebuildIndex() {
        val rebuilt = HashMap<String, AppProfile>()
        // I profili utente vengono applicati per ultimi cosi' hanno la precedenza.
        val ordered = providers.entries.sortedBy { if (it.key == USER_PROVIDER_ID) 1 else 0 }
        ordered.forEach { (_, provider) ->
            provider.profiles.forEach { profile ->
                profile.packageNames.forEach { pkg -> rebuilt[pkg] = profile }
            }
        }
        index.keys.retainAll(rebuilt.keys)
        index.putAll(rebuilt)
    }

    const val USER_PROVIDER_ID = "user"
}
