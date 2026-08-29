package dev.airscroll.core.common.model

/** Fotografia dello stato del motore, usata dalla UI e dall'indicatore. */
data class EngineStatus(
    val state: EngineState = EngineState.DISABLED,
    val reason: StateChangeReason = StateChangeReason.SERVICE_TOGGLED,
    val activePackage: String? = null,
    val activeProfileName: String? = null,
    val handPresent: Boolean = false,
    val effectiveGain: Float = 1f,
    val lastError: String? = null,
)
