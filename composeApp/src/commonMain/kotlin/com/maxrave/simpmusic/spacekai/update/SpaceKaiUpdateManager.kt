package com.maxrave.simpmusic.spacekai.update

import com.maxrave.domain.data.model.update.UpdateData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SPACEKAI FEATURE: explicit state machine for the in-app update flow. Replaces
 * the old fire-and-forget download+install call so the UI can show real progress
 * (bytes/time/speed), a genuine SHA-256 verification step, and honest error/retry
 * states instead of a silent browser fallback.
 *
 * The *check* (detect a newer release) is already owned by SharedViewModel /
 * UpdateRepository; this manager owns only the download → SHA-256 verify →
 * package-install pipeline, with these states:
 *
 *   UPDATE_AVAILABLE -> DOWNLOADING -> VERIFYING
 *        -> READY_TO_INSTALL -> INSTALLING -> SUCCESS
 *   any -> FAILED (retryable) ; READY_TO_INSTALL -> CANCELLED
 *
 * No faked progress: [downloadedBytes]/[totalBytes] flow straight from the
 * platform downloader as the bytes are written to disk.
 */
enum class SpaceKaiUpdatePhase {
    UPDATE_AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    INSTALLING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class SpaceKaiUpdateState(
    val phase: SpaceKaiUpdatePhase = SpaceKaiUpdatePhase.UPDATE_AVAILABLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val error: String? = null,
    val tagName: String? = null,
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isBusy: Boolean
        get() =
            phase == SpaceKaiUpdatePhase.DOWNLOADING ||
                phase == SpaceKaiUpdatePhase.VERIFYING ||
                phase == SpaceKaiUpdatePhase.INSTALLING

    val isTerminal: Boolean
        get() =
            phase == SpaceKaiUpdatePhase.SUCCESS ||
                phase == SpaceKaiUpdatePhase.FAILED ||
                phase == SpaceKaiUpdatePhase.CANCELLED
}

/** Result of a completed update attempt, reported back onto the shared state. */
sealed interface SpaceKaiUpdateResult {
    data object Success : SpaceKaiUpdateResult
    data object Cancelled : SpaceKaiUpdateResult
    data class Failure(val reason: String) : SpaceKaiUpdateResult
}

object SpaceKaiUpdateManager {
    private val _state = MutableStateFlow(SpaceKaiUpdateState())
    val state: StateFlow<SpaceKaiUpdateState> = _state.asStateFlow()

    /**
     * Drives the full download -> SHA-256 verify -> install pipeline for [update].
     * The platform updater performs the honest steps and reports byte-level progress;
     * this object mirrors it into the shared state and normalizes the terminal result.
     */
    suspend fun install(update: UpdateData): SpaceKaiUpdateResult {
        val apkUrl = update.apkUrl
        if (apkUrl.isNullOrBlank()) {
            _state.value =
                _state.value.copy(
                    phase = SpaceKaiUpdatePhase.FAILED,
                    error = "Aucun APK trouvé dans la release.",
                )
            return SpaceKaiUpdateResult.Failure("Aucun APK trouvé dans la release.")
        }
        _state.value =
            _state.value.copy(
                phase = SpaceKaiUpdatePhase.DOWNLOADING,
                downloadedBytes = 0L,
                totalBytes = 0L,
                bytesPerSecond = 0L,
                error = null,
                tagName = update.tagName,
            )
        val outcome =
            PlatformUpdater.install(
                apkUrl = apkUrl,
                checksumsUrl = update.checksumsUrl,
                onProgress = { downloaded, total, speed ->
                    _state.value =
                        _state.value.copy(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            bytesPerSecond = speed,
                        )
                },
                onPhase = { phase ->
                    _state.value = _state.value.copy(phase = phase)
                },
            )
        // Mirror the terminal result into the shared state so the UI's terminal
        // branches ("✓ Mise à jour installée" / "Mise à jour annulée" / "❌ …") are
        // actually reachable. Before this mapping, a successful hand-off left the
        // phase stuck at DOWNLOADING, so `isBusy` stayed true forever.
        when (outcome) {
            is SpaceKaiUpdateResult.Success ->
                _state.value =
                    _state.value.copy(
                        phase = SpaceKaiUpdatePhase.SUCCESS,
                        error = null,
                    )
            is SpaceKaiUpdateResult.Cancelled ->
                _state.value =
                    _state.value.copy(
                        phase = SpaceKaiUpdatePhase.CANCELLED,
                        error = null,
                    )
            is SpaceKaiUpdateResult.Failure ->
                _state.value =
                    _state.value.copy(
                        phase = SpaceKaiUpdatePhase.FAILED,
                        error = outcome.reason,
                    )
        }
        return outcome
    }

    /** Forget the current flow (e.g. on retry or dismissing the progress). */
    fun reset() {
        _state.value = SpaceKaiUpdateState()
    }
}

/** Platform-provided update executor. Implemented per target (android/jvm). */
internal expect object PlatformUpdater {
    suspend fun install(
        apkUrl: String,
        checksumsUrl: String?,
        onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long) -> Unit,
        onPhase: (SpaceKaiUpdatePhase) -> Unit,
    ): SpaceKaiUpdateResult
}