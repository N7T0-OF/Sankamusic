package com.sankamusic.core.settings

import com.sankamusic.core.api.SpaceKaiFeaturesManifest
import com.sankamusic.core.api.UpstreamAdapter
import com.sankamusic.core.api.builtInSpaceKaiFeatures

/**
 * Flags SpaceKai-OLD (étape 7 migration — docs/MIGRATION.md).
 *
 * Port des 8 bascules de `SpaceKaiSettingsSection` / `SpaceKaiFeatures`
 * (clés persistées, valeurs "true"/"false") avec le lien vers la
 * fonctionnalité du manifest quand elle existe déjà.
 */
enum class SpaceKaiFlag(
    val key: String,
    /** Id de la fonctionnalité du manifest correspondante, ou null si pas encore migrée. */
    val manifestFeatureId: String?,
    val default: Boolean,
) {
    SPOTIFY_SYNC("spotify_sync", null, false),
    CUSTOM_NAVIGATION("custom_navigation", "navigation", true),
    MINIMALISTIC_NAVIGATION("minimalistic_navigation", null, false),
    DYNAMIC_COLOR("dynamic_color", "dynamic_color", false),
    LANDSCAPE_PLAYER("landscape_player", "orientation", false),
    HAPTICS("haptics", "haptics", false),
    DOWNLOAD_WIFI_ONLY("download_wifi_only", null, false),
    CUSTOM_PLAYER_INFO("custom_player_info", null, false),
    ;

    /** Clé de préférence persistée (préfixe porté de `SPACEKAI_FLAG_PREFIX`). */
    fun preferenceKey(): String = "spacekai.$key"

    /** Préférence booléenne de ce flag (défaut = [default]). */
    fun preference(): Preference<Boolean> = booleanPreference(preferenceKey(), default)
}

/** Clé de préférence d'une fonctionnalité du manifest (défaut = `enabledByDefault`). */
fun featureFlagKey(featureId: String): String = "spacekai.feature.$featureId.enabled"

/** Préférence booléenne d'une fonctionnalité du manifest. */
fun featureFlagPreference(featureId: String, enabledByDefault: Boolean): Preference<Boolean> =
    booleanPreference(featureFlagKey(featureId), enabledByDefault)

/**
 * État d'une fonctionnalité du manifest :
 *
 *  1. la fonctionnalité doit exister dans le manifest ;
 *  2. elle doit être **compatible** avec la version upstream donnée
 *     (`null` → jamais compatible, on ne peut pas confirmer) ;
 *  3. sinon : la préférence persistée, ou `enabledByDefault` si absente.
 *
 * Une fonctionnalité incompatible est simplement désactivée — jamais d'APK
 * cassée silencieusement (docs/FEATURE_MANIFEST.md).
 */
object SpaceKaiFeatureFlags {

    fun isEnabled(
        typed: TypedSettings,
        featureId: String,
        upstreamVersion: String?,
        manifest: SpaceKaiFeaturesManifest = builtInSpaceKaiFeatures,
    ): Boolean {
        val feature = manifest.featureById(featureId) ?: return false
        if (!manifest.isFeatureCompatible(featureId, upstreamVersion)) return false
        return typed.get(featureFlagPreference(featureId, feature.enabledByDefault))
    }

    /**
     * Variante avec Adapter : la compatibilité exige AUSSI que le contrat de la
     * fonctionnalité soit fourni par l'Adapter (docs/FEATURE_MANIFEST.md § 3).
     */
    fun isEnabled(
        typed: TypedSettings,
        featureId: String,
        upstreamVersion: String?,
        adapter: UpstreamAdapter?,
        manifest: SpaceKaiFeaturesManifest = builtInSpaceKaiFeatures,
    ): Boolean {
        val feature = manifest.featureById(featureId) ?: return false
        if (!manifest.isFeatureCompatible(featureId, upstreamVersion, adapter)) return false
        return typed.get(featureFlagPreference(featureId, feature.enabledByDefault))
    }

    /** Variante sur un [StringSettings] brut. */
    fun isEnabled(
        settings: StringSettings,
        featureId: String,
        upstreamVersion: String?,
        adapter: UpstreamAdapter?,
        manifest: SpaceKaiFeaturesManifest = builtInSpaceKaiFeatures,
    ): Boolean = isEnabled(TypedSettings(settings), featureId, upstreamVersion, adapter, manifest)

    /** Variante sur un [StringSettings] brut (tests, intégrations simples). */
    fun isEnabled(
        settings: StringSettings,
        featureId: String,
        upstreamVersion: String?,
        manifest: SpaceKaiFeaturesManifest = builtInSpaceKaiFeatures,
    ): Boolean = isEnabled(TypedSettings(settings), featureId, upstreamVersion, manifest)

    /** Active/désactive une fonctionnalité (persisté). */
    fun setEnabled(
        typed: TypedSettings,
        featureId: String,
        enabled: Boolean,
        manifest: SpaceKaiFeaturesManifest = builtInSpaceKaiFeatures,
    ) {
        val feature = manifest.featureById(featureId) ?: return
        typed.set(featureFlagPreference(featureId, feature.enabledByDefault), enabled)
    }

    /** Variante sur un [StringSettings] brut. */
    fun setEnabled(
        settings: StringSettings,
        featureId: String,
        enabled: Boolean,
        manifest: SpaceKaiFeaturesManifest = builtInSpaceKaiFeatures,
    ) {
        setEnabled(TypedSettings(settings), featureId, enabled, manifest)
    }
}
