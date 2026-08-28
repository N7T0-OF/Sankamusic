package com.sankamusic.core.update

import com.sankamusic.core.api.SpaceKaiFeature
import com.sankamusic.core.api.SpaceKaiFeaturesManifest
import com.sankamusic.core.api.UpstreamAdapter
import com.sankamusic.core.api.upstreamMatches

/**
 * Rapport de compatibilité SpaceKai ↔ base upstream (docs/FEATURE_MANIFEST.md § 3
 * et docs/UPSTREAM_SYSTEM.md § 5).
 *
 * La compatibilité d'une fonctionnalité est décidée par DEUX critères :
 *
 *  1. la version upstream est dans la plage déclarée (`upstreamCompatibility`) ;
 *  2. si la fonctionnalité déclare un contrat, l'Adapter le fournit
 *     (`UpstreamAdapter.satisfiesContract`).
 *
 * Règle conservatrice : une fonctionnalité dont la compatibilité ne peut pas
 * être **confirmée** (version upstream inconnue, contrat non fourni) est
 * simplement marquée incompatible — jamais d'APK cassée silencieusement.
 * Les autres fonctionnalités continuent de fonctionner.
 */

/** Statut de compatibilité d'une fonctionnalité avec la base upstream. */
enum class CompatibilityStatus {
    /** Version dans la plage ET contrat satisfait (si déclaré). */
    COMPATIBLE,

    /** Version upstream hors de la plage déclarée. */
    VERSION_OUT_OF_RANGE,

    /** Version dans la plage mais l'Adapter ne fournit pas le contrat requis. */
    CONTRACT_NOT_SATISFIED,

    /** Version upstream inconnue (`null`) — on ne peut pas confirmer. */
    UNKNOWN_UPSTREAM,

    /** Id de fonctionnalité inconnu du manifest. */
    FEATURE_UNKNOWN,
}

/** Résultat de compatibilité d'une seule fonctionnalité. */
data class FeatureCompatibility(
    val featureId: String,
    val compatible: Boolean,
    val status: CompatibilityStatus,
    val reason: String,
)

/** Rapport complet : une entrée par fonctionnalité du manifest. */
data class CompatibilityReport(
    val upstreamVersion: String?,
    val adapterVersion: Int?,
    val features: List<FeatureCompatibility>,
) {
    val totalCount: Int get() = features.size
    val compatibleCount: Int get() = features.count { it.compatible }
    val incompatibleCount: Int get() = totalCount - compatibleCount

    /** Résumé lisible, ex. « 6/6 features compatible ». */
    fun summary(): String = "$compatibleCount/$totalCount features compatible"

    fun feature(featureId: String): FeatureCompatibility? =
        features.firstOrNull { it.featureId == featureId }
}

/** Calcule le rapport de compatibilité (manifest + version upstream + adapter). */
object CompatibilityReporter {

    fun report(
        manifest: SpaceKaiFeaturesManifest,
        upstreamVersion: String?,
        adapter: UpstreamAdapter?,
    ): CompatibilityReport = CompatibilityReport(
        upstreamVersion = upstreamVersion,
        adapterVersion = adapter?.info?.adapterVersion,
        features = manifest.features.map { feature ->
            featureCompatibility(manifest, feature, upstreamVersion, adapter)
        },
    )
}

/**
 * Compatibilité d'une seule fonctionnalité — source unique de vérité, utilisée
 * par `SpaceKaiFeatureFlags` et par l'UI (écran Paramètres).
 */
fun featureCompatibility(
    manifest: SpaceKaiFeaturesManifest,
    feature: SpaceKaiFeature,
    upstreamVersion: String?,
    adapter: UpstreamAdapter?,
): FeatureCompatibility {
    if (manifest.featureById(feature.id) == null) {
        return FeatureCompatibility(
            featureId = feature.id,
            compatible = false,
            status = CompatibilityStatus.FEATURE_UNKNOWN,
            reason = "feature '${feature.id}' inconnue du manifest",
        )
    }
    if (upstreamVersion == null) {
        return FeatureCompatibility(
            featureId = feature.id,
            compatible = false,
            status = CompatibilityStatus.UNKNOWN_UPSTREAM,
            reason = "version upstream inconnue — compatibilité non confirmable",
        )
    }
    if (!upstreamMatches(feature.upstreamCompatibility, upstreamVersion)) {
        return FeatureCompatibility(
            featureId = feature.id,
            compatible = false,
            status = CompatibilityStatus.VERSION_OUT_OF_RANGE,
            reason = "version $upstreamVersion hors de la plage ${feature.upstreamCompatibility}",
        )
    }
    val contract = feature.contract
    if (contract != null && (adapter == null || !adapter.satisfiesContract(contract))) {
        return FeatureCompatibility(
            featureId = feature.id,
            compatible = false,
            status = CompatibilityStatus.CONTRACT_NOT_SATISFIED,
            reason = "contrat '$contract' non fourni par l'Adapter",
        )
    }
    return FeatureCompatibility(
        featureId = feature.id,
        compatible = true,
        status = CompatibilityStatus.COMPATIBLE,
        reason = "version dans la plage et contrat satisfait",
    )
}
