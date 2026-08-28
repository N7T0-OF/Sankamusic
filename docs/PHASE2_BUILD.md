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
  (UPSTREAM_SYSTEM.md § 6). Vérifier les obligations avant toute distribution.

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
Sur un poste équipé, procéder en un commit `[build]` dédié et **laisser valider
par le CI** :

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
// 3. Convertir UnifiedTrack → GenericMediaItem via toMediaItemDraft() :
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

### 5c. Playlists (locales + YouTube)
```kotlin
// Locales : LocalPlaylistRepository.getAllLocalPlaylists() → par item
//   LocalPlaylistDraft(id=it.id, title=it.title, thumbnail=it.thumbnail,
//       tracks=it.tracks).toUnifiedPlaylist("simpmusic")
// YouTube : PlaylistEntity → YoutubePlaylistDraft(...).toUnifiedPlaylist("simpmusic")
```

---

## 6. 🔒 PROTOCOLE DE VALIDATION (obligatoire — ordre verrouillé)

> **RÈGLE ABSOLUE** : tant que les 3 sous-adaptateurs n'ont PAS réellement
> **compilé et passé leurs contract tests contre les sources SimpMusic 2.0.0**,
> **aucune extension des plages du manifest `→ 2.x`**. Aucun raccourci du type
> « ça semble fonctionner » ne doit exister : la validation est la SEULE porte
> d'entrée vers `2.x`.

### VERROU (à mémoriser et à imposer)

```
INTERDIT : étendre compatibility de 1.7.x vers 2.x avant validation complète.

ORDRE OBLIGATOIRE :
 1.  Identifier / mettre à jour SimpMusic 2.0.0 dans l'Adapter
 2.  Monter SimpMusicAdapterV2
 3.  Compiler les 3 sous-adaptateurs (player / library / playlists locales + YouTube)
 4.  Contract tests (contre les sources réelles, pas des fakes)
 5.  Tests Core (≥ 162)
 6.  Build Android (APK debug)
 7.  Installer l'APK
 8.  Smoke tests fonctionnels (9 points)
 9.  CompatibilityReporter = PASS
 10. Seulement maintenant → étendre les plages à 2.x + fermer l'issue #1
```

### 🚦 PHASE 2 VALIDATION GATE

```
┌─────────────────────────────────────┐
│  PHASE 2 VALIDATION GATE           │
├─────────────────────────────────────┤
│  Adapter V2 compile       [ ]       │
│  Player contract          [ ]       │
│  Library contract         [ ]       │
│  Playlist contract        [ ]       │
│  Core tests               [ ]       │
│  Android build            [ ]       │
│  Installation             [ ]       │
│  Smoke tests              [ ]       │
│  Compatibility report     [ ]       │
└─────────────────────────────────────┘

SI UNE CASE = ❌
 → NE PAS déclarer 2.x compatible
 → NE PAS fermer l'issue #1
```

> ⚙️ Cette porte est aussi imposée TECHNiquement par le CI : le workflow
> `ci.yml` vérifie la présence du marqueur `PHASE2_V2_VALIDATED=true` avant
> d'autoriser une plage d'Adapter `2.x` (voir § 8-ci). Un humain ne peut pas
> étendre seul sans le prouver.

Les étapes **doivent** être exécutées dans l'ordre ; on ne passe à l'étape
suivante que si la précédente est entièrement verte.

### Étape 1 — Prérequis (machine équipée)
- [ ] Android SDK présent (`ANDROID_HOME`) + `platforms;android-36`, `build-tools;36.0.0`.
- [ ] JDK 17 (ou celui requis par AGP 9).
- [ ] Gradle wrapper à jour (Gradle **9.5.1**, § 4).
- [ ] AGP **9.2.1** + Kotlin **2.4.10** (libs.versions.toml, § 4).
- [ ] NDK **si** un module natif de la base l'exige.
- [ ] source SimpMusic 2.0.0 accessible (snapshot ou clone).

### Étape 2 — Arbre de travail propre
- [ ] `git status` → **propre** (aucun fichier non committé).
- [ ] Travailler sur une branche dédiée `feat/phase2-adapter-v2` (jamais
  directement sur `main` tant que le protocole n'est pas terminé).

### Étape 3 — Câbler les 3 sous-adaptateurs dans `SimpMusicAdapterV2`
- [ ] **Player** (§ 5a) compilé.
- [ ] **Library** (§ 5b) compilé.
- [ ] **Playlists locales** (§ 5c) compilé.
- [ ] **Playlists YouTube** (§ 5c) compilé.
- [ ] Le pont `core/bridge/MediaBridgeMappings.kt` reste la SEULE source des conversions.

### Étape 4 — Compilation complète
- [ ] `./gradlew :core:compileKotlin :core:compileDebugKotlin` OK.
- [ ] `./gradlew :app:assembleDebug` (ou compile) OK avec la base en dépendance.

### Étape 5 — Contract tests (contre les sources 2.0.0, PAS des fakes)
- [ ] `SimpMusicAdapterV2Test` vert (compilé contre `MediaPlayerInterface`,
  `GenericMediaItem`, `SongEntity`, `LocalPlaylistEntity`, `PlaylistEntity`).
- [ ] `AdapterContractIntegrityTest` vert : chaque contrat déclaré a ses
  opérations EXERCÉES sur les vraies implémentations de la base (pas simulées).
- [ ] `MediaBridgeMappingsTest` + `LibraryPlaylistBridgeTest` verts.
- [ ] Total ≥ **162 tests** attendus (peut monter avec les nouveaux).

### Étape 6 — Build APK
- [ ] `./gradlew assembleRelease` (via CI si pas de SDK local : push branche)
  → UN SEUL APK universel signé (RELEASE_GUIDE « unicité »).
- [ ] CI `ci.yml` **vert** sur la branche montée.

### Étape 7 — Installation + smoke tests sur appareil/émulateur
Installer l'APK debug du § 6 puis vérifier **chacun** :
- [ ] **Lancement** (démarre, pas de crash).
- [ ] **Navigation** (onglets Accueil/Bibliothèque/Recherche/Paramètres + plugins).
- [ ] **Bibliothèque** (liste des morceaux, conversion `SongEntity` → `UnifiedTrack`).
- [ ] **Playlists** (locales + YouTube affichées, pas de doublons).
- [ ] **Player** (play/pause/next/seek, file, état, position/durée).
- [ ] **Orientation** (mode paysage forcé + restauration, étape 3).
- [ ] **Paramètres** (thèmes, toggles de fonctionnalités, persistance).
- [ ] **Haptique** (vibrations quand activé).
- [ ] **Dynamic Color** (Material You / OLED en sombre).

> Chaque case défaillante = retour à l'étape 3 ou 5 ; JAMAIS de passage au
> vert tant qu'un smoke test échoue.

### Étape 8 — Rapport de compatibilité (avant extension)
- [ ] Lancer `bash scripts/check-upstream.sh build/upstream` : le rapport doit
  indiquer **3/6** (plages encore `1.7.x`) — CONSERVATEUR et attendu.
  Ce n'est PAS une anomalie : tant qu'on n'a pas étendu, V2 n'est pas « actif ».
- [ ] Le workflow `upstream-check.yml` reste cohérent avec le manifest (pas de
  divergence code ↔ doc).

### Étape 9 — SEULEMENT ICI : étendre à `2.x`
Une fois TOUT le § 6 (étapes 1-8) vert :
1. `core/api/FeatureManifest.kt` — `builtInSpaceKaiFeatures` :
   `upstreamCompatibility = "2.0.x"` pour navigation / orientation / player.
2. `docs/FEATURE_MANIFEST.md` — tableau des plages idem.
3. `scripts/check-upstream.sh` — miroir bash idem.
4. Relancer `check-upstream.sh` : **6/6**.
5. Commit + push → le workflow `upstream-check.yml` repasse au vert.
6. **Fermer l'issue #1** (`Upstream SimpMusic : fonctionnalite hors plage`),
   en citant le commit qui étendait les plages. Ne PAS la fermer avant.

### Étape 10 — Vérifications finales (checklist de sortie)
- [ ] Arbre PROPRE.
- [ ] `:core` compilé + tests ≥ **162** verts (toolchain 2026).
- [ ] Contract tests V2 contre la base 2.0.0 verts.
- [ ] APK debug installé + **tous** les smoke tests passés (§ 6 étape 7).
- [ ] CI vert (`ci.yml`) sur la branche montée.
- [ ] `check-upstream.sh` **6/6**.
- [ ] Workflow upstream vert.
- [ ] Issue #1 **fermée**.
- [ ] Licence GPL-3.0 prise en compte (attributions, obligations sources) — UPSTREAM § 6.

---

## 7. Pourquoi ce protocole est strict (et correct)

Le but n'EST PLUS « faire marcher SpaceKai sur 2.0 » en soi : c'est
**prouver que SpaceKai peut changer d'Adapter sans changer son Core ni ses
fonctionnalités.**

- `SimpMusicAdapterV2` confine TOUTE la complexité de la base 2.0.0 ;
  `SpaceKaiApi`, `FeatureManifest`, `SpaceKaiFeatureFlags`, `TypedSettings` et
  les plugins **restent inchangés**.
- La séquence compile → contract tests → APK → smoke tests → plages verrouille
  chaque niveau AVANT la suite : on n'étend `2.x` que **prouvé**, jamais
  « à vue de nez ».
- Une future SimpMusic **3.0** deviendra simplement `SimpMusicAdapterV3` — le
  même Core et les mêmes fonctionnalités — sans transformer le projet en fork :
  c'est la démonstration du « Bridge ».

---

## 8. 🛡️ Garde-fou CI (obligation technique, pas seulement documentaire)

Le guide est un document ; le **CI le fait respecter**. `scripts/check-phase2-validation.sh`
(raccordé dans `.github/workflows/ci.yml`, étape « Phase 2 validation gate »)
refuse le build si le **manifest intégré** (`core/api/FeatureManifest.kt`)
déclare une fonctionnalité en `2.x` **sans marqueur de validation**.

- **Cible** : `upstreamCompatibility = "2.x"` / `"2.0.x"` dans `builtInSpaceKaiFeatures` (le seul endroit où on étend la compatibilité).
  L'Adapter `SimpMusicAdapterV2.compatibility = "2.0.x"` n'est PAS bloqué :
  l'existence d'un Adapter ne prouve pas 2.x — seule la plage du manifest compte.
- **Marqueur** : fichier `.phase2-v2-validated` (commité APRÈS avoir passé toute
  la gate) **ou** variable CI secrète `PHASE2_V2_VALIDATED=true`. Sans l'un des
  deux, une plage `2.x` → **exit 1 -> CI rouge**.

Quatre scripts de vérification coexistent donc :

| Script | Rôle |
|--------|------|
| `scripts/check-upstream.sh` | Détecte la nouvelle version SimpMusic (workflow `upstream-check.yml`, hebdo) et signale une sortie de plage |
| `scripts/test-upstream.sh` | Couvre la logique pure de `check-upstream.sh` (pattern_matches + agrégation des plages) hors réseau — lancé par `ci.yml` sur chaque push |
| `scripts/check-phase2-validation.sh` | Imposé par `ci.yml` à chaque push : interdit d'étendre le manifest à `2.x` sans la validation Phase 2 accomplie |
| `scripts/test-phase2-validation.sh` | Couvre le garde-fou (3 scénarios) — lancé par `ci.yml` AVANT la gate |

C'est la fermeture de la boucle : on ne peut pas déclarer `2.x` « à vue de nez »
tant qu'un poste Android n'a pas produit la preuve (compile + contract tests +
APK + smoke tests) via le marqueur correspondant.

**Le garde-fou est lui-même testé** : `scripts/test-phase2-validation.sh`
exerce `check-phase2-validation.sh` sur ses 3 scénarios contractuels (1.7.x sans
marqueur → passe ; 2.x sans marqueur → refuse ; 2.x avec marqueur → passe) via
manifest temporaires — **jamais** le vrai manifest du repo. `ci.yml` lance ce
test via l'étape « Test the Phase 2 validation gate itself » AVANT la gate : si
le garde-fou régressait (par exemple une regex qui cesserait d'attraper `2.x`),
le CI casserait et cette faille ne passerait pas silencieusement. `PHASE2_MANIFEST`
et `PHASE2_GATE_FILE` rendent ces scénarios testables sans toucher à la cible réelle.