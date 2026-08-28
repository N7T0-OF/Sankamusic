# Manifest de fonctionnalités — SpaceKai

- **Statut** : 🟢 Implémenté (Core) — manifest intégré + matching de compatibilité testé
- **Document lié** : `docs/MIGRATION.md`, `docs/UPSTREAM_SYSTEM.md`

## 1. Objectif

Adopter la partie utile des propositions « patches.json / ReVanced » : un
manifest **déclaratif et versionné** des fonctionnalités SpaceKai, où chaque
fonctionnalité est **indépendante** et déclare sa propre plage de compatibilité
avec la base SimpMusic. C'est la base du futur « choisis tes fonctionnalités »
(activation/désactivation individuelles) et du contrôle de compatibilité
**par fonctionnalité** (pas seulement global).

## 2. Le modèle (Core)

`core/api/FeatureManifest.kt` :

```kotlin
@Serializable
data class SpaceKaiFeature(
    val id: String,                  // "navigation", "adaptive_player"…
    val name: String,
    val description: String = "",
    val enabledByDefault: Boolean = true,
    val minSankamusicVersion: String = "0.1.0",
    val upstreamCompatibility: String = "*",  // "*" | "1.x" | "1.7.x" | "1.7.0"
)

@Serializable
data class SpaceKaiFeaturesManifest(
    val name: String,        // "SpaceKai"
    val version: String,     // version du manifest
    val targetPackage: String,  // "com.maxrave.simpmusic" (base)
    val features: List<SpaceKaiFeature>,
)
```

Le manifest est **sérialisable en JSON** (kotlinx.serialization) : il peut être
distribué/chargé comme un `patches.json`, versionné dans le dépôt.

## 3. Compatibilité par fonctionnalité

Plages supportées (`upstreamMatches(pattern, version)` — fonction pure testée) :

| Pattern | Couvre | Exemple |
|---------|--------|---------|
| `"*"` | toutes les versions | `"*"` couvre `2.0.0` |
| `"1.x"` | toute la majeure 1 | `"1.x"` couvre `1.9.9`, pas `2.0.0` |
| `"1.7.x"` | la majeure+mineure 1.7 | `"1.7.x"` couvre `1.7.99`, pas `1.8.0` |
| `"1.7.0"` | version exacte | seul `1.7.0` |

Règles (conservatrices, comme `UPSTREAM_SYSTEM.md`) :

- version upstream **null / invalide** → jamais compatible (on ne peut pas confirmer) ;
- tags git acceptés (`"v1.7.2"`) ;
- une fonctionnalité incompatible avec une nouvelle version SimpMusic est
  simplement **désactivée** — les autres continuent de fonctionner, jamais
  d'APK cassée silencieusement.

> 🚨 Pas de matching de bytecode « par confiance » (proposition ReVanced) :
> la détection d'architecture est faite par **le compilateur + l'adapter**
> (`UPSTREAM_SYSTEM.md`). Si l'adapter ne compile plus contre une nouvelle
> version de SimpMusic, le build échoue FORT — jamais en silence.

### Contrats d'API (Compatibility Contracts)

Au-delà de la version, chaque fonctionnalité peut déclarer un **contrat**
(`SpaceKaiFeature.contract`, ex. `"player-api"`) : l'API SpaceKai stable dont
elle dépend. La compatibilité exige alors AUSSI que l'adapter fournisse ce
contrat (`UpstreamAdapter.satisfiesContract`) — indépendamment des numéros de
version SimpMusic. C'est le « SpaceKai Bridge » des propositions : les
fonctionnalités ne connaissent que le contrat, l'adapter absorbe les
changements d'architecture upstream.

```kotlin
// FeatureManifest.kt
val contract: String? = null   // null = pas de contrat (plage suffit)

// UpstreamAdapter.kt
fun satisfiesContract(contractId: String): Boolean = false
```

Ids stables définis dans `SpaceKaiContracts` (navigation-api, theme-api,
orientation-api, player-api, haptics-api, dynamic-color-api) ; l'Adapter v1
les fournit tous. **Invariant testé** : tout contrat du manifest intégré doit
être satisfait par l'Adapter (`SimpMusicAdapterTest`).

### Rapport de compatibilité

`core/update/CompatibilityReport.kt` produit le rapport par fonctionnalité
(`CompatibilityReporter.report(manifest, version, adapter)`), avec un statut
précis pour chaque feature :

| Statut | Sens |
|--------|------|
| `COMPATIBLE` | version dans la plage ET contrat satisfait |
| `VERSION_OUT_OF_RANGE` | version hors plage déclarée |
| `CONTRACT_NOT_SATISFIED` | version dans la plage, contrat non fourni |
| `UNKNOWN_UPSTREAM` | version inconnue → jamais de fausse compatibilité |
| `FEATURE_UNKNOWN` | id inconnu du manifest |

Résumé lisible (`6/6 features compatible`) ; c'est la source unique utilisée
par `SpaceKaiFeatureFlags` et l'écran Paramètres.

## 4. Manifest intégré

`builtInSpaceKaiFeatures` (Core) — miroir de `docs/MIGRATION.md` :

| id | Fonctionnalité | Compat upstream | Défaut |
|----|----------------|-----------------|--------|
| `navigation` | Navigation personnalisable (étape 1) | `1.7.x` | on |
| `themes` | Thèmes — mode, source, overlay (étape 2) | `*` | on |
| `orientation` | Orientation paysage du player (étape 3) | `1.7.x` | on |
| `player` | Player — machine à états + file (étape 4) | `1.7.x` | on |
| `haptics` | Vibration (étape 5) | `*` | off |
| `dynamic_color` | Dynamic Color + OLED (étape 6) | `*` | on |

## 5. À venir

- [x] Écran « Fonctionnalités » dans Paramètres (activation/désactivation individuelles)
- [ ] Chargement du manifest depuis une ressource JSON (au lieu de la constante)
- [x] Workflow CI « compatibilité » (`upstream-compat.yml`) : vérifie
      périodiquement la dernière release SimpMusic (`scripts/check-upstream.sh`),
      publie le rapport (artefact `upstream-compat`) et ouvre une issue
      automatique si une fonctionnalité sort de sa plage
