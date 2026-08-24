package com.maxrave.simpmusic.expect

actual object HapticManager {
    actual var enabled: Boolean = true
    actual var intensity: Float = 1f

    actual fun isSupported(): Boolean = false

    actual fun vibrate(type: HapticType) {
        // Desktop has no vibration hardware — no-op.
    }
}
