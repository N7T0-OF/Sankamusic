package com.maxrave.simpmusic.spacekai.features.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.maxrave.simpmusic.spacekai.SpaceKaiFeatures
import com.maxrave.simpmusic.spacekai.isSpaceKaiFeatureEnabled

// SPACEKAI FEATURE: haptics
//
// Light tactile feedback on player / navigation interactions. Uses Compose's
// common `HapticFeedback` API (`LocalHapticFeedback.current`), which is a
// real vibration on Android and a no-op on Desktop — so this helper is safe
// to call on every platform. It is a wrapper around existing click handlers,
// never a modification of upstream logic.

/**
 * Performs haptic feedback when the `haptics` feature flag is on.
 *
 * Call inside a click handler (or any gesture callback) with the current
 * [HapticFeedback] instance:
 *
 * ```kotlin
 * HapticsSpaceKai.onClick(LocalHapticFeedback.current)
 * ```
 *
 * When the flag is off this is a no-op, so hooking it into an upstream click
 * handler costs nothing for a vanilla build.
 */
object HapticsSpaceKai {
    /**
     * Standard confirmation tick on a click/toggle.
     *
     * @param type feedback strength; defaults to [HapticFeedbackType.LongPress]
     *   (the common type available on every Compose platform).
     */
    fun onClick(
        hapticFeedback: HapticFeedback,
        type: HapticFeedbackType = HapticFeedbackType.LongPress,
    ) {
        if (isSpaceKaiFeatureEnabled(SpaceKaiFeatures::haptics)) {
            hapticFeedback.performHapticFeedback(type)
        }
    }
}