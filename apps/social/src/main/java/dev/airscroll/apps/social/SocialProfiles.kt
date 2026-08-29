package dev.airscroll.apps.social

import dev.airscroll.apps.api.AppCategory
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.apps.api.AppProfileProvider
import dev.airscroll.apps.api.ScrollTuning

/**
 * Social e feed verticali.
 *
 * Qui lo scorrimento e' "a schede": conviene una curva piu' reattiva e una
 * velocita' massima piu' alta, cosi' un movimento deciso passa al post
 * successivo invece di fermarsi a meta'.
 */
object SocialProfiles : AppProfileProvider {

    override val providerId: String = "social"

    private val feedTuning = ScrollTuning(
        speedMultiplier = 1.15f,
        curveGamma = 1.6f,
        gripFractionX = 0.5f,
        gripFractionY = 0.6f,
        volumeEnabled = true,
    )

    private val reelsTuning = feedTuning.copy(
        speedMultiplier = 1.45f,
        curveGamma = 1.35f,
        gripFractionY = 0.65f,
    )

    override val profiles: List<AppProfile> = listOf(
        AppProfile(
            id = "social.instagram",
            displayName = "Instagram",
            packageNames = setOf("com.instagram.android"),
            category = AppCategory.SOCIAL,
            tuning = reelsTuning,
        ),
        AppProfile(
            id = "social.tiktok",
            displayName = "TikTok",
            packageNames = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            category = AppCategory.SOCIAL,
            tuning = reelsTuning,
        ),
        AppProfile(
            id = "social.youtube",
            displayName = "YouTube",
            packageNames = setOf("com.google.android.youtube"),
            category = AppCategory.MEDIA,
            tuning = feedTuning,
        ),
        AppProfile(
            id = "social.reddit",
            displayName = "Reddit",
            packageNames = setOf("com.reddit.frontpage"),
            category = AppCategory.SOCIAL,
            tuning = feedTuning,
        ),
        AppProfile(
            id = "social.x",
            displayName = "X",
            packageNames = setOf("com.twitter.android"),
            category = AppCategory.SOCIAL,
            tuning = feedTuning,
        ),
        AppProfile(
            id = "social.facebook",
            displayName = "Facebook",
            packageNames = setOf("com.facebook.katana"),
            category = AppCategory.SOCIAL,
            tuning = feedTuning,
        ),
        AppProfile(
            id = "social.whatsapp",
            displayName = "WhatsApp",
            packageNames = setOf("com.whatsapp"),
            category = AppCategory.SOCIAL,
            tuning = feedTuning.copy(speedMultiplier = 0.85f, volumeEnabled = false),
        ),
    )
}
