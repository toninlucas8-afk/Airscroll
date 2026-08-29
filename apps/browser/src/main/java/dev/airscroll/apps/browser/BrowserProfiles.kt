package dev.airscroll.apps.browser

import dev.airscroll.apps.api.AppCategory
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.apps.api.AppProfileProvider
import dev.airscroll.apps.api.ScrollTuning

/** Browser: pagine lunghe, scroll continuo, nessun bisogno di volume. */
object BrowserProfiles : AppProfileProvider {

    override val providerId: String = "browser"

    private val browserTuning = ScrollTuning(
        speedMultiplier = 1.0f,
        curveGamma = 1.8f,
        gripFractionX = 0.5f,
        gripFractionY = 0.55f,
        volumeEnabled = false,
    )

    override val profiles: List<AppProfile> = listOf(
        AppProfile(
            id = "browser.chrome",
            displayName = "Chrome",
            packageNames = setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev"),
            category = AppCategory.BROWSER,
            tuning = browserTuning,
        ),
        AppProfile(
            id = "browser.firefox",
            displayName = "Firefox",
            packageNames = setOf("org.mozilla.firefox", "org.mozilla.fenix", "org.mozilla.focus"),
            category = AppCategory.BROWSER,
            tuning = browserTuning,
        ),
        AppProfile(
            id = "browser.samsung",
            displayName = "Samsung Internet",
            packageNames = setOf("com.sec.android.app.sbrowser"),
            category = AppCategory.BROWSER,
            tuning = browserTuning,
        ),
        AppProfile(
            id = "browser.brave",
            displayName = "Brave",
            packageNames = setOf("com.brave.browser"),
            category = AppCategory.BROWSER,
            tuning = browserTuning,
        ),
        AppProfile(
            id = "browser.edge",
            displayName = "Microsoft Edge",
            packageNames = setOf("com.microsoft.emmx"),
            category = AppCategory.BROWSER,
            tuning = browserTuning,
        ),
        AppProfile(
            id = "browser.opera",
            displayName = "Opera",
            packageNames = setOf("com.opera.browser", "com.opera.gx"),
            category = AppCategory.BROWSER,
            tuning = browserTuning,
        ),
    )
}
