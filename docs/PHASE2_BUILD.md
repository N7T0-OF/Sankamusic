# Phase 2 — Montage du build contre la base SimpMusic 2.0.0

**Statut** : 📋 Guide de reprise — à exécuter sur un poste avec **Android Studio / SDK**,
JDK 17+ et IDE builder. Ce document transforme le travail déjà accompli
(mapping, pont, contract tests, documentation) en une procédure exécutable.

**Lié à** : `docs/UPSTREAM_SYSTEM.md` (§ 7 mapping, § 8bis audit, § 6 licence),
`docs/RELEASE_GUIDE.md` (checklist), `docs/BUILD_SYSTEM.md`.

---

## 0. Rappel du contexte (pour ne pas re-découvrir)

- **Sankamusic** n'est pas un fork : les ajouts SpaceKai sont portés en
  plugins/thèmes à niveau source ; la **base SimpMusic** reste upstream.
- **TOOLCHAIN ACTUELLE (Sankamusic)** : Gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.20 /
  Compose BOM 2024.09.02.
- **SimpMusic 2.0.0 exige** : Gradle 9.5.1 / AGP 9.2.1 / Kotlin 2.4.10 /
  Compose BOM 2026.08.00 **+ KMP + material3-expressive alpha**.
- **Conséquence** : l'intégration d'un sous-module KMP de la base nécessite de
  monter la toolchain de Sankamusic (voir § 4 — les deux voies).
- **Licence** : base ET sous-module `maxrave-dev/core` sont **GNU GPL-3.0**
  (§ 6). Vérifier les obligations avant toute distribution.

Tout ce qui suit s'appuie sur du travail **déjà committé et testé** :
`SimpMusicAdapterV2`, `AdapterContractIntegrityTest`, le pont des conversions
`core/bridge/MediaBridgeMappings.kt` (player/library/playlists YouTube),
et le mapping des classes réelles (`UPSTREAM_SYSTEM.md` § 7).

---

## 1. Prérequis (machine cible)

- Android Studio (dernière stable) **ou** ligne de commande `sdkmanager`
  (+ `platforms;android-36`, `build-tools;36.0.0`).
- JDK **17** (recommandé pour AGP 9) — ou celui demandé par AGP 9.2.
- Accès réseau aux dépôts Google/MavenCentral (le CI les utilise déjà).
- Clones disponibles :
  - ce repo **Sankamusic** (worktree courant) ;
  - la source upstream `maxrave-dev/SimpMusic` (snapshot 2.0.0) ;
  - `maxrave-dev/core` (branche `multiplatform`) si on n'utilise pas le
    snapshot complet.

---

## 2. État du module `core` (pur)

Le module `:core` de Sankamusic est **pur** (aucune dépendance Android) :
il compile et teste seul avec kotlinc (162 tests OK) et ne dépend pas du SDK.
**Il n'a pas besoin d'être modifié pour la Phase 2** — c'est le principe validé :
le Core ne bouge pas, c'est l'Adapter qui absorbe la base.

---

## 3. La base comme dépendance — trois options (à choisir par l'équipe)

### Option A — Sous-module git (reproductible en CI, recommandé)
```bash
git submodule add https://github.com/maxrave-dev/SimpMusic.git upstream/SimpMusic
# ou, plus léger (uniquement le core) :
git submodule add -b multiplatform https://github.com/maxrave-dev/core.git upstream/core
```
> ⚠️ Nécessite la montée toolchain (§ 4) : `SimpMusic` est KMP (AGP 9).

### Option B — `includeBuild` local (rapide à tester en local)
Ajouter dans `settings.gradle.kts` :
```kotlin
includeBuild("../SimpMusic-core")   // chemin local du core cloné
```
Puis dépendre des modules `:core:domain`, `:core:media:media3` dans `:app`.

### Option C — Publication Maven locale (isolée)
Publier les modules nécessaires (`domain`, `media:media3`) du repo `core` en
Maven local puis dépendre par coordonnées :
```kotlin
implementation("com.maxrave.core:domain:...")
```
C'est la plus propre côté isolation de la licence, mais demande le setup Gradle
du repo `core` (KMP + AGP 9).

---

## 4. Monter la toolchain vers AGP 9 / Kotlin 2.4 / Gradle 9.5

C'est le **prérequis bloquant** (le build actuel est en toolchain 2024).
Sur un poste équipé, procéder en un commit `[build]` dédié et **laisser valider par le CI** :

1. `gradle/wrapper/gradle-wrapper.properties` → `gradle-9.5.1-bin.zip`.
2. `gradle/libs.versions.toml` →
   `agp = "9.2.1"`, `kotlin = "2.4.10"`, `composeBom = "2026.08.00"`,
   activer le plugin KMP si besoin.
3. Adapter `compileSdk`/`minSdk`/`targetSdk` (36) dans `:app`.
4. **Tester** : `./gradlew :core:test :core:compileDebugKotlin` puis CI.
   Le CI valide déjà un `assembleRelease` signé (RELEASE_GUIDE).
   > ⚠️ Ne PAS fusionner tant que le CI n'est pas vert sur la nouvelle toolchain.

Si la montée complète est trop risquée en une fois, **voie alternative** :
extraire isolément dans Sankamusic les seules classes nécessaires
(`MediaPlayerInterface`, `GenericMediaItem`, `GenericMediaMetadata`,
`SongEntity`, `LocalPlaylistEntity`, `PlaylistEntity`, les repos) en
déclarations compatibles, SANS le reste du KMP — à condition de respecter la
licence GPL (attributions/crédits).

---

## 5. Câbler les sous-adaptateurs dans `SimpMusicAdapterV2`

Une fois la base disponible, compléter les 3 sous-adaptateurs (échec actuel
`NotImplementedError` → implémentation réelle). **Le pont des conversions est
déjà prêt et testé** (`core/bridge/MediaBridgeMappings.kt`) — il suffit de le
relier en ~5 lignes par sous-adapter :

### 5a. Player
```kotlin
// Dans SimpMusicAdapterV2.player :
// 1. Récupérer MediaPlayerHandler (DI de la base) → handler.player : MediaPlayerInterface
// 2. Lire handler.simpleMediaState / nowPlaying pour état
// 3. sleep convertir UnifiedTrack → GenericMediaItem via toMediaItemDraft() :
//    GenericMediaItem(
//        mediaId = draft.mediaId, uri = draft.uri,
//        metadata = GenericMediaMetadata(title=draft.title, artist=draft.artist,
//            albumTitle=draft.albumTitle, artworkUri=draft.artworkUri),
//        customCacheKey = draft.customCacheKey,
//    )
// 4. play/pause/resume → playerInterface.play()/pause()/resume()
//    isPlaying → playerInterface.isPlaying
```

### 5b. Library
```kotlin
// Lire les SongEntity de la base locale (CommonRepository / requête Room)
// puis, par morceau, relier toUnifiedTrack() :
//   SongDraft(videoId, title, artistNames, durationSeconds, thumbnails).toUnifiedTrack("simpmusic")
```

### 5c. Playlists
```kotlin
// Locales : LocalPlaylistRepository.getAllLocalPlaylists() → par item
//   LocalPlaylistDraft(id=it.id, title=it.title, thumbnail=it.thumbnail,
//       tracks=it.tracks).toUnifiedPlaylist("simpmusic")
// YouTube : PlaylistEntity → YoutubePlaylistDraft(...).toUnifiedPlaylist("simpmusic")
```

Après câblage, **passer les contract tests réels** : `SimpMusicAdapterV2Test`
et `AdapterContractIntegrityTest` doivent tourner avec la base en dépendance.

---

## 6. Étendre la compatibilité

Seulement APRÈS que § 5 compile et passe :
1. `scripts/check-upstream.sh` — miroir bash → `navigation|2.0.x`,
   `orientation|2.0.x`, `player|2.0.x` (themes/haptics/dynamic_color restent `*`).
2. `docs/FEATURE_MANIFEST.md` — tableau des plages `1.7.x` → `2.0.x`.
3. `core/api/FeatureManifest.kt` — `builtInSpaceKaiFeatures` :
   `upstreamCompatibility = "2.0.x"` pour les 3 fonctionnalités.
4. Re-lancer `scripts/check-upstream.sh` : il doit passer **6/6**.
5. Fermer l'issue #1 (`Upstream SimpMusic : fonctionnalite hors plage`).
6. **Tester** : `bash scripts/check-upstream.sh build/upstream` + `git add` ...
   Puis le workflow upstream repasse au vert.

---

## 7. Vérifications finales (checklist)

- [ ] `./gradlew :core:test` vert (**162 tests attendus**) sur la toolchain montée.
- [ ] `SimpMusicAdapterV2Test` + `AdapterContractIntegrityTest` passent avec la base en dépendance.
- [ ] CI vert (`ci.yml` → `assembleRelease` signé).
- [ ] Workflow upstream vert (`upstream-check.yml` → `6/6`, Dev à Dev).
- [ ] `check-upstream.sh` 6/6.
- [ ] Licence GPL-3.0 prise en compte (attributions, obligations sources) — § 6.
- [ ] ARBRE PROPRE (aucun fichier non committé parasite).

---

## 8. Pourquoi ce découpage est correct

Le découpage respecte le principe « le Core ne bouge pas » : toute la
complexité de la base 2.0.0 est confinée dans `SimpMusicAdapterV2` (Adapter).
Les fonctionnalités SpaceKai, `SpaceKaiApi`, `SpaceKaiFeatureFlags`,
`TypedSettings` et les plugins **restent inchangés** : la migration 1.x → 2.x
est un test grandeur nature de l'architecture — c'est la démonstration du
« SpaceKai Bridge ».