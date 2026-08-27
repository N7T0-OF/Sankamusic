package com.sankamusic.core.api

/**
 * Gestion des mises à jour (voir docs/UPDATE_SYSTEM.md).
 *
 * Trois catégories DISTINCTES (ADR-004) :
 *  1. Sankamusic Core  → [checkSankamusicUpdate]
 *  2. Plugins/thèmes   → [checkPluginUpdates]
 *  3. Compatibilité upstream SimpMusic → [checkUpstreamCompatibility]
 *
 * Les vérifications sont non bloquantes (suspend) et ne déclenchent JAMAIS
 * l'installation d'une version upstream non validée.
 */
interface UpdateManager {
    suspend fun checkSankamusicUpdate(): UpdateStatus
    suspend fun checkPluginUpdates(): List<PluginUpdate>
    suspend fun checkUpstreamCompatibility(): UpstreamStatus
}

enum class UpdateState {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    INCOMPATIBLE,
    ERROR,
}

/** Statut de mise à jour de Sankamusic (source : releases GitHub N7T0-OF/Sankamusic). */
data class UpdateStatus(
    val installedVersion: String,
    val availableVersion: String? = null,
    val changelog: String? = null,
    val downloadSizeBytes: Long? = null,
    val sha256: String? = null,
    val publishedAt: String? = null,
    val state: UpdateState = UpdateState.UP_TO_DATE,
)

/** Mise à jour d'un plugin indépendante du Core. */
data class PluginUpdate(
    val pluginId: String,
    val fromVersion: String,
    val toVersion: String,
)

enum class UpstreamCompatibilityState {
    /** La base installée est la version compatible attendue. */
    COMPATIBLE,

    /** Une nouvelle version upstream existe ; l'Adapter doit être vérifié/mis à jour. */
    NEEDS_ADAPTER_UPDATE,

    /** Version upstream incompatible avec l'Adapter actuel. */
    INCOMPATIBLE,
}

/** Statut de compatibilité avec la base SimpMusic (jamais mise à jour automatiquement). */
data class UpstreamStatus(
    val upstreamName: String,
    val installedUpstreamVersion: String,
    val availableUpstreamVersion: String? = null,
    val adapterVersion: Int,
    val compatibility: String? = null,
    val state: UpstreamCompatibilityState = UpstreamCompatibilityState.COMPATIBLE,
)
