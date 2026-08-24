package com.maxrave.simpmusic.expect

/**
 * Central haptics layer.
 *
 * Every vibration in the app goes through this object so it can be:
 *  - globally gated (ON/OFF)
 *  - scaled by a user intensity (0..1)
 *  - type-aware (navigation tick vs. slider step vs. success)
 *
 * [enabled] and [intensity] are @Volatile so the Settings ViewModel can
 * update them from a DataStore collector without touching the platform
 * vibrator. On platforms without a vibrator (Desktop), [vibrate] is a
 * no-op and [isSupported] returns false.
 */
expect object HapticManager {
    var enabled: Boolean
    var intensity: Float

    fun vibrate(type: HapticType = HapticType.SELECT)
    fun isSupported(): Boolean
}

enum class HapticType {
    /** Light tick for navigation / toggle actions. */
    SELECT,

    /** Light tick when a slider crosses a discrete threshold. */
    SLIDER_STEP,

    /** Short confirmation pulse. */
    SUCCESS,
}
