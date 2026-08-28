package com.sankamusic.core.api

import com.sankamusic.core.update.SemVer
import kotlinx.serialization.Serializable

/**
 * Manifest déclaratif des fonctionnalités SpaceKai (idée `patches.json` des
 * propositions ReVanced/BetterDiscord — docs/FEATURE_MANIFEST.md).
 *
 * Chaque fonctionnalité est **indépendante** et déclare sa propre plage de
 * compatibilité avec la base SimpMusic : on peut l'activer/désactiver
 * individuellement, et une fonctionnalité incompatible avec une nouvelle
 * version upstream est simplement marquée incompatible (jamais d'APK cassée
 * silencieusement) — les autres continuent de fonctionner.
 *
 * La « détection » de l'architecture se fait par le compilateur et
 * l'[com.sankamusic.core.api.UpstreamAdapter] (voir UPSTREAM_SYSTEM.md) : pas
 * de matching de bytecode par confiance — si l'adapter ne compile plus contre
 * une nouvelle version de SimpMusic, le build échoue FORT, jamais en silence.
 */

/** Une fonctionnalité SpaceKai migrée (id stable, activable individuellement). */
@Serializable
data class SpaceKaiFeature(
    /** Identifiant stable (ex. "navigation", "adaptive_player"). */
    val id: String,
    val name: String,
    val description: String = "",
    /** Activée par défaut (port du « choisir tes patches » ReVanced). */
    val enabledByDefault: Boolean = true,
    /** Version minimale de Sankamusic requise pour cette fonctionnalité. */
    val minSankamusicVersion: String = "0.1.0",
    /**
     * Plage de compatibilité avec la base SimpMusic :
     * `"*"` (toutes), `"1.x"` (même majeure), `"1.7.x"` (même majeure+mineure),
     * ou une version exacte `"1.7.0"`.
     */
    val upstreamCompatibility: String = "*",
    /**
     * Contrat d'API requis, satisfait par l'Adapter (docs/FEATURE_MANIFEST.md § 3) :
     * même si la version upstream est dans la plage, la fonctionnalité n'est
     * compatible que si l'Adapter fournit ce contrat. `null` = pas de contrat.
     */
    val contract: String? = null,
)

/** Manifest versionné des fonctionnalités SpaceKai (sérialisable en JSON). */
@Serializable
data class SpaceKaiFeaturesManifest(
    val name: String,
    /** Version du manifest lui-même (incrémentée à chaque changement). */
    val version: String,
    /** Package de la base ciblée (ex. "com.maxrave.simpmusic"). */
    val targetPackage: String,
    val features: List<SpaceKaiFeature>,
) {
    fun featureById(id: String): SpaceKaiFeature? = features.firstOrNull { it.id == id }

    /** Erreurs de validation ; vide si le manifest est valide. */
    fun validationErrors(): List<String> = buildList {
        if (name.isBlank()) add("name ne doit pas être vide")
        if (version.isBlank()) add("version ne doit pas être vide")
        if (targetPackage.isBlank()) add("targetPackage ne doit pas être vide")
        val duplicates = features.map { it.id }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) add("ids de features dupliqués : $duplicates")
        features.forEach { feature ->
            if (feature.id.isBlank()) add("feature avec id vide")
            if (SemVer.parse(feature.minSankamusicVersion) == null) {
                add("minSankamusicVersion invalide pour '${feature.id}' : ${feature.minSankamusicVersion}")
            }
            if (!isValidUpstreamPattern(feature.upstreamCompatibility)) {
                add("upstreamCompatibility invalide pour '${feature.id}' : ${feature.upstreamCompatibility}")
            }
        }
    }

    /**
     * Vrai si la fonctionnalité est compatible avec la version upstream donnée
     * (`null` → jamais compatible : on ne peut pas confirmer).
     */
    fun isFeatureCompatible(featureId: String, upstreamVersion: String?): Boolean {
        val feature = featureById(featureId) ?: return false
        return upstreamMatches(feature.upstreamCompatibility, upstreamVersion)
    }

    /** Fonctionnalités compatibles avec la version upstream donnée (les autres sont à désactiver). */
    fun compatibleFeatures(upstreamVersion: String?): List<SpaceKaiFeature> =
        features.filter { upstreamMatches(it.upstreamCompatibility, upstreamVersion) }

    /**
     * Compatibilité complète : la version upstream doit être dans la plage ET,
     * si la fonctionnalité déclare un contrat, l'Adapter doit le satisfaire.
     */
    fun isFeatureCompatible(
        featureId: String,
        upstreamVersion: String?,
        adapter: UpstreamAdapter?,
    ): Boolean {
        val feature = featureById(featureId) ?: return false
        if (!upstreamMatches(feature.upstreamCompatibility, upstreamVersion)) return false
        val contract = feature.contract ?: return true
        // Conservateur : contrat déclaré sans Adapter → non compatible (on ne peut pas confirmer).
        return adapter != null && adapter.satisfiesContract(contract)
    }
}

/**
 * Ids stables des contrats SpaceKai (docs/FEATURE_MANIFEST.md § 3) : l'API que
 * l'[UpstreamAdapter] doit fournir pour qu'une fonctionnalité soit compatible,
 * indépendamment des numéros de version SimpMusic. Le manifest déclare ces ids
 * (`SpaceKaiFeature.contract`), l'Adapter déclare ceux qu'il fournit.
 */
object SpaceKaiContracts {
    const val NAVIGATION = "navigation-api"
    const val THEME = "theme-api"
    const val ORIENTATION = "orientation-api"
    const val PLAYER = "player-api"
    const val HAPTICS = "haptics-api"
    const val DYNAMIC_COLOR = "dynamic-color-api"

    /** Tous les contrats fournis par l'Adapter SimpMusic v1 (étapes 1-6 migrées). */
    val SIMPMUSIC_ADAPTER_V1: Set<String> =
        setOf(NAVIGATION, THEME, ORIENTATION, PLAYER, HAPTICS, DYNAMIC_COLOR)
}

/** Vrai si un pattern de compatibilité est syntaxiquement valide. */
fun isValidUpstreamPattern(pattern: String): Boolean =
    when {
        pattern == "*" -> true
        pattern.endsWith(".x") -> SemVer.parse(pattern.removeSuffix(".x") + ".0") != null
        else -> SemVer.parse(pattern) != null
    }

/**
 * Vrai si la version upstream (ex. "1.7.2" ou tag "v1.7.2") est couverte par le
 * pattern. `null` / invalide → faux (conservateur : jamais de fausse compatibilité).
 */
fun upstreamMatches(pattern: String, upstreamVersion: String?): Boolean {
    if (upstreamVersion == null) return false
    val version = SemVer.parse(upstreamVersion) ?: SemVer.parseTag(upstreamVersion) ?: return false
    return when {
        pattern == "*" -> true
        pattern.endsWith(".x") -> {
            val core = pattern.removeSuffix(".x")
            // "1" → 1.0.0 ; "1.7" → 1.7.0 (le premier essai échoue pour 4 composants).
            val bound = SemVer.parse("$core.0.0") ?: SemVer.parse("$core.0") ?: return false
            version.major == bound.major && (core.contains('.') && version.minor == bound.minor || !core.contains('.'))
        }
        else -> SemVer.parse(pattern) == version
    }
}

/** Manifest intégré des fonctionnalités migrées (miroir de docs/MIGRATION.md). */
val builtInSpaceKaiFeatures: SpaceKaiFeaturesManifest = SpaceKaiFeaturesManifest(
    name = "SpaceKai",
    version = "0.1.0",
    targetPackage = "com.maxrave.simpmusic",
    features = listOf(
        SpaceKaiFeature(
            id = "navigation",
            name = "Navigation personnalisable",
            description = "Onglets extensibles (étape 1 migration) — vérifié sur 2.0.0 (BottomNavScreen + onglets conditionnels)",
            enabledByDefault = true,
            upstreamCompatibility = "2.0.x",
            contract = SpaceKaiContracts.NAVIGATION,
        ),
        SpaceKaiFeature(
            id = "themes",
            name = "Thèmes",
            description = "Mode, source de couleur, overlay base+couche (étape 2)",
            enabledByDefault = true,
            upstreamCompatibility = "*",
            contract = SpaceKaiContracts.THEME,
        ),
        SpaceKaiFeature(
            id = "orientation",
            name = "Orientation paysage",
            description = "Politique d'orientation du player (étape 3) — vérifié sur 2.0.0 (FullscreenPlayer force/restaure)",
            enabledByDefault = true,
            upstreamCompatibility = "2.0.x",
            contract = SpaceKaiContracts.ORIENTATION,
        ),
        SpaceKaiFeature(
            id = "player",
            name = "Player",
            description = "Machine à états + file d'attente (étape 4) — moteur 2.0.0 dans maxrave-dev/core/media3",
            enabledByDefault = true,
            upstreamCompatibility = "2.0.x",
            contract = SpaceKaiContracts.PLAYER,
        ),
        SpaceKaiFeature(
            id = "haptics",
            name = "Vibration",
            description = "Retour haptique (étape 5)",
            enabledByDefault = false,
            upstreamCompatibility = "*",
            contract = SpaceKaiContracts.HAPTICS,
        ),
        SpaceKaiFeature(
            id = "dynamic_color",
            name = "Dynamic Color",
            description = "Palette Material You + règle OLED (étape 6)",
            enabledByDefault = true,
            upstreamCompatibility = "*",
            contract = SpaceKaiContracts.DYNAMIC_COLOR,
        ),
    ),
)
