# Système upstream — SimpMusic

- **Statut** : 🟢 Base déclarée (Adapter v1) — sous-adaptateurs à relier après audit
- **Document lié** : `docs/ARCHITECTURE.md`, `docs/UPDATE_SYSTEM.md`

## 1. Objectif

Sankamusic doit suivre les évolutions de SimpMusic **sans dépendre directement de ses
classes internes**. Une couche d'abstraction (`UpstreamAdapter`) isole les différences
entre versions.

## 2. Principe

```
SimpMusic v1 → Adapter v1 → API Sankamusic (stable)
SimpMusic v2 → Adapter v2 → API Sankamusic (stable)
```

Sankamusic ne connaît **que** l'Adapter. Si l'API upstream change, seul l'Adapter doit
être adapté **autant que possible** ; le Core reste stable.

## 3. Modèle de compatibilité

Informations que le système connaît (valeurs de référence vérifiées le
2026-08-27 contre l'API GitHub réelle, codées dans `SimpMusicAdapter`) :

```text
UPSTREAM
  repository   : maxrave-dev/SimpMusic
  version      : 1.7.0
  adapter      : 1
  compatibility: 1.7.x
```

| Champ | Signification |
|-------|---------------|
| `upstream.version` | version de la base SimpMusic intégrée |
| `adapter` | version de l'Adapter (incrémentée quand il change) |
| `compatibility` | plage de versions SimpMusic couvertes |

## 4. Sous-adaptateurs envisagés

| Adaptateur | Rôle | État |
|------------|------|------|
| `MusicPlayerAdapter` | lecture, file d'attente, contrôles | 🔴 non relié (échec explicite) |
| `LibraryAdapter` | bibliothèque | 🔴 non relié (échec explicite) |
| `PlaylistAdapter` | playlists | 🔴 non relié (échec explicite) |
| `NavigationAdapter` | navigation | à analyser |
| `ThemeAdapter` | thèmes | à analyser |
| `SettingsAdapter` | paramètres | à analyser |
| `DownloadAdapter` | téléchargements | à analyser |

> Les sous-adaptateurs non reliés lèvent `NotImplementedError` (jamais de
> comportement simulé). À réviser après analyse réelle de SimpMusic : certains
> adaptateurs peuvent être inutiles ou insuffisants. **Ne pas en créer artificiellement.**

## 5. Processus de mise à jour upstream

1. Détecter la nouvelle version SimpMusic disponible.
2. Vérifier la compatibilité avec l'Adapter actuel.
3. Analyser les changements (API, comportements, dépendances).
4. Déterminer si l'Adapter doit être mis à jour.
5. Tester sur une build de validation.
6. Publier **uniquement une release Sankamusic validée** (voir `RELEASE_GUIDE.md`).

> 🚨 **Jamais** de remplacement automatique d'un composant critique par une version
> upstream non testée. La compatibilité est un processus contrôlé, pas un auto-update.

## 6. Risques à documenter après analyse

- [ ] Quelles classes/API SimpMusic sont réellement utilisées par Sankamusic ?
- [ ] Quelles parties de SimpMusic changent le plus souvent entre versions ?
- [ ] Quelles parties sont impossibles à isoler (dépendances en dur) ?
- [ ] Licences : que peut-on réutiliser, avec quels crédits obligatoires ?

## 7. Mapping Adapter ↔ classes SimpMusic 2.0.0 (audit source 2026-08-28)

Source : snapshot local `SimpMusic-dev` (2.0.0) + `maxrave-dev/core` cloné
(branche `multiplatform`) — packages et signatures réels.

### Player (sous-adaptateur `MusicPlayerAdapter`)

| API SpaceKai | Classe réelle SimpMusic 2.0.0 | Détail |
|--------------|-------------------------------|--------|
| `play(track)` / `pause()` / `resume()` / `isPlaying` | `com.maxrave.domain.mediaservice.player.MediaPlayerInterface` | Contrat réel : `play()`, `pause()`, `stop()`, `seekTo(positionMs)`, `seekTo(index, pos)`, `seekBack/Forward/Next/Previous`, `prepare()`, `setMediaItem/addMediaItem/removeMediaItem/moveMediaItem/clearMediaItems/replaceMediaItem`, `getMediaItemAt`, `getCurrentMediaTimeLine`, `getUnshuffledIndex`, `isPlaying` |
| — | `com.maxrave.media3.exoplayer.ExoPlayerAdapter` (Android) | Implémentation media3/ExoPlayer de l'interface (constructeur `(exoPlayer: ExoPlayer)` + `MediaPlayerListener`)
| — | `com.simpmusic.media_jvm.mpv.MpvPlayerAdapter` (desktop) | Implémentation MPV côté desktop
| `UnifiedTrack` → item de lecture | `com.maxrave.domain.data.player.GenericMediaItem` | `GenericMediaItem(mediaId, uri, metadata: GenericMediaMetadata, customCacheKey)` — mapping : `mediaId = track.id`, `metadata` = titre/artistes/pochette
| État du player | `com.maxrave.domain.mediaservice.handler.MediaPlayerHandler` | Handler consommé par l'app (`BaseViewModel`) : `player`, `StateFlow`s (`simpleMediaState`, `nowPlaying`, `queueData`, `controlState`, `nowPlayingState`, `currentSongIndex`…), `onPlayerEvent`, `toggleLike`, `addMediaItemList`, `playMediaItemInMediaSource` |

### Bibliothèque (`LibraryAdapter`)

| API SpaceKai | Classe réelle SimpMusic 2.0.0 | Détail |
|--------------|-------------------------------|--------|
| `tracks()` → `UnifiedTrack` | `com.maxrave.domain.data.entities.SongEntity` | Modèle réel de la bibliothèque : `videoId`, `title`, `artistId`/`artistName`, `duration`/`durationSeconds`, `thumbnails`, `inLibrary`… (table Room)
| — | `com.maxrave.domain.repository.CommonRepository` | Accès base locale (cookies, notifications, récents) ; la bibliothèque de morceaux s'appuie sur la base locale + l'API YouTube Music (`HomeRepository`, parsers)

### Playlists (`PlaylistAdapter`)

| API SpaceKai | Classe réelle SimpMusic 2.0.0 | Détail |
|--------------|-------------------------------|--------|
| `playlists()` → `UnifiedPlaylist` | `com.maxrave.domain.repository.LocalPlaylistRepository` | `getAllLocalPlaylists(): Flow<List<LocalPlaylistEntity>>`, `getLocalPlaylist(id)`, `updateLocalPlaylistTracks`…
| — | `com.maxrave.domain.data.entities.PlaylistEntity` / `LocalPlaylistEntity` | Modèles réels (playlists YouTube + locales)

### Implémentation prévue (Phase 2 — build contre la base)

- `SimpMusicAdapterV2.player` → `MusicPlayerAdapter` implémenté sur
  `MediaPlayerHandler.player` (conversion `UnifiedTrack` ↔ `GenericMediaItem`),
  avec `MediaPlayerListener` pour alimenter position/durée du `PlayerController`.
- `SimpMusicAdapterV2.library` → lecture des `SongEntity` de la base locale
  (ou API), conversion vers `UnifiedTrack`.
- `SimpMusicAdapterV2.playlists` → `LocalPlaylistRepository.getAllLocalPlaylists()`
  conversion vers `UnifiedPlaylist`.
- **Pré-câblé (2026-08-28)** : les conversions player pures sont déjà
  implémentées et testées dans `core/bridge/MediaBridgeMappings.kt`
  (`UnifiedTrack` ↔ `MediaItemDraft`, miroir de `GenericMediaItem`/
  `GenericMediaMetadata`) — il ne restera que le câblage final (~5 lignes)
  dans l'Adapter V2.
- Les conversions sont pures et testables une fois la dépendance présente ;
  tant qu'elle ne l'est pas, les sous-adaptateurs échouent explicitement
  (`NotImplementedError` — jamais de comportement simulé).

### Blocage toolchain (constat 2026-08-28) — le mur de l'intégration build

Intégrer la base 2.0.0 comme dépendance compilable est bloqué par la TOOLCHAIN :

| Composant | Sankamusic (build actuel) | SimpMusic 2.0.0 |
|-----------|---------------------------|------------------|
| Gradle wrapper | 8.9 | 9.5.1 |
| AGP | 8.5.2 | 9.2.1 |
| Kotlin | 2.0.20 | 2.4.10 |
| Compose BOM | 2024.09.02 | 2026.08.00 (+ material3-expressive 1.5.0-alpha26, KMP) |
| Android SDK local | aucun | — |

Un sous-module KMP de la base exigeant AGP 9 / Kotlin 2.4 / Gradle 9.5 nepeut pas être compilé dans un build Sankamusic sur AGP 8.5 / Kotlin 2.0 / Gradle 8.9.
Le mapping et les conversions (`core/bridge/MediaBridgeMappings.kt`) sont prêts
et testés SANS dépendance (155 tests OK) ; le câblage final des sous-adaptateurs
restera à faire après une montée d'outils OU une extraction isolée des seuls
modules nécessaires (domain/`MediaPlayerInterface`/`GenericMediaItem`, data
`SongEntity`/`LocalPlaylistRepository`) dans une version compatible.

- Version upstream actuelle de référence : **v1.7.0** (base intégrée, Adapter v1) ;
  **v2.0.0 auditée** le 2026-08-28 (§ 8bis) — intégration build EN SUSPENS
  (blocage toolchain, voir ci-dessus)

## 8. Faits vérifiés sur l'API réelle (2026-08-27)

Observations faites via `api.github.com` (sans token), à re-vérifier à chaque
mise à jour upstream :

- **Upstream réel** : `maxrave-dev/SimpMusic` (PseudoReso/SimpMusic n'existe pas).
- **Dernière release stable** : `v1.7.0`, publiée le 2026-08-07 ; 30 releases
  listées (27 stables, 3 pré-releases — les pré-releases sont ignorées par le moteur).
- **Chaque release contient 8 APK** (foss/full × arm64-v8a/armeabi-v7a/universal/x86_64)
  et **aucun `SHA256SUMS.txt`** → aucune vérification d'intégrité possible côté
  upstream. Notre repo doit, lui, toujours publier **un APK universel unique**
  + `SHA256SUMS.txt` (RELEASE_GUIDE.md).
- **Repo cible `N7T0-OF/Sankamusic`** : existe, public, mais **zéro release** →
  l'updater en-app reste en état `ERROR` (propre, sans crash) jusqu'à la
  publication de la première release.

## 8bis. Audit source SimpMusic v2.0.0 (2026-08-28)

Déclenché par le workflow upstream-compat : la dernière release stable est
passée à **v2.0.0** (publiée le 2026-08-28T17:36Z). La source a été auditée
(snapshot local `SimpMusic-dev` version-name 2.0.0 / code 57 + repo
`maxrave-dev/core`, branche `multiplatform`) :

- **Restructuration majeure** : passage KMP consolidé ; le moteur audio est
  extrait dans le sous-module **`maxrave-dev/core`** (`media/media3` — media3
  conservé comme moteur, `media-jvm` pour desktop).
- **Les 6 points d'intégration des contrats SpaceKai existent toujours** :

| Contrat | Élément vérifié dans 2.0.0 | Verdict |
|---------|------------------------------|---------|
| navigation | `BottomNavScreen` (enum) + `AppBottomNavigationBar` avec onglets conditionnels (`takeIf { showXTab }`) | ✅ |
| thème | `AppTheme(themeMode, themeColorSource, customThemeColor, liquidGlassEnabled)` — même famille, + nouveau param liquid glass | ✅ |
| orientation | `FullscreenPlayer.android.kt` force `SCREEN_ORIENTATION_LANDSCAPE` + restaure l'orientation d'origine | ✅ |
| player | `FullscreenPlayer`/`NowPlayingScreen`/`PlayerControlLayout` + moteur media3 (sous-module) | ✅ |
| haptique | ajout SpaceKai (la base n'en a pas — rien à casser) | ✅ |
| dynamic color | `platformDynamicColorScheme` + `rememberDynamicColorScheme(seed, isDark, isAmoled)` + OLED | ✅ |

- **Conséquence** : **`SimpMusicAdapterV2`** créé (version 2.0.0, plage
  `2.0.x`, adapterVersion 2) — premier test de résistance de l'architecture,
  sans toucher au Core. ⚠️ **Les plages du manifest restent `1.7.x`**
  (navigation / orientation / player) : l'existence d'un Adapter ne prouve
  pas la compatibilité — l'extension à `2.x` ne se fera qu'après validation
  des contract tests (`AdapterContractIntegrityTest`) et du build Phase 2
  contre la source 2.0.0.
- ⚠️ Le sous-module `core` est un repo séparé (`maxrave-dev/core`, branche
  `multiplatform`) : à intégrer avec le clone (submodule) en Phase 2.
