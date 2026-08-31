package com.maxrave.simpmusic.spacekai.features.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.maxrave.simpmusic.spacekai.SpaceKaiFeatures
import com.maxrave.simpmusic.spacekai.isSpaceKaiFeatureEnabled

// SPACEKAI FEATURE: centralized haptics — the single place that decides WHETHER a
// haptic fires (the `haptics` flag) and HOW STRONG it is (the intensity setting).
//
//   Haptics OFF  -> every call is a no-op (vanilla behaviour, zero cost).
//   Intensity    -> LOW = Compose's light tick (TextHandleMove); MEDIUM = the standard
//                   LongPress; HIGH = LongPress + a light follow-up pulse (a real double
//                   tick on Android). Common Compose exposes only these two base types,
//                   so Forte is a pulse pair rather than a single stronger constant.
//
// The intensity is persisted through the generic string store (key below) and restored
// at startup by `applyPersistedSpaceKaiFeatures` (SpaceKai.kt), exactly like the Boolean
// flags — so the setting survives a restart without typed DataStore keys.

/** The three selectable intensities. */
enum class HapticIntensity(val storage: String, val label: String) {
    LOW("low", "Faible"),
    MEDIUM("medium", "Moyenne"),
    HIGH("high", "Forte"),
}

object HapticManager {
    /** Generic string-store key for the persisted intensity. */
    const val KEY: String = "spacekai_haptics_intensity"

    private var intensity: HapticIntensity = HapticIntensity.MEDIUM

    /** Restore the persisted intensity (startup); unknown/absent key keeps MEDIUM. */
    fun applyPersisted(getString: (String) -> String?): HapticIntensity {
        val raw = getString(KEY)?.trim()
        intensity =
            HapticIntensity.entries.firstOrNull { it.storage == raw } ?: HapticIntensity.MEDIUM
        return intensity
    }

    fun setIntensity(value: HapticIntensity) {
        intensity = value
    }

    fun current(): HapticIntensity = intensity

    /** The base [HapticFeedbackType] for the current intensity. */
    fun pickType(): HapticFeedbackType =
        when (intensity) {
            HapticIntensity.LOW -> HapticFeedbackType.TextHandleMove
            HapticIntensity.MEDIUM, HapticIntensity.HIGH -> HapticFeedbackType.LongPress
        }

    /** Forte adds a light follow-up pulse so HIGH is audibly/visibly stronger than MEDIUM. */
    private val doublePulse: Boolean
        get() = intensity == HapticIntensity.HIGH

    /**
     * The centralized haptic entry point: fires only when the `haptics` flag is ON, at the
     * strength selected by the intensity setting. An explicit [explicitType] (rare) bypasses
     * the intensity pick — used by call sites that need a specific gesture feel.
     */
    fun onClick(
        hapticFeedback: HapticFeedback,
        explicitType: HapticFeedbackType? = null,
    ) {
        if (!isSpaceKaiFeatureEnabled(SpaceKaiFeatures::haptics)) return
        val resolved = explicitType ?: pickType()
        hapticFeedback.performHapticFeedback(resolved)
        if (explicitType == null && doublePulse) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
