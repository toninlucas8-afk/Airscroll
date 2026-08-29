package dev.airscroll.core.common.model

/**
 * Profili distanza. In [AUTO] il guadagno viene ricavato dalla dimensione
 * apparente della mano confrontata con quella registrata in calibrazione.
 */
enum class DistanceProfile(val fixedGain: Float?) {
    NEAR(0.72f),
    MEDIUM(1.0f),
    FAR(1.55f),
    AUTO(null);
}
