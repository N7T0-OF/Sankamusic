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

## 7. À compléter

- Liste des dépendances réelles de SimpMusic (Gradle) : _(vide)_
- Mapping Adapter ↔ classes SimpMusic : _(vide)_
- Version upstream actuelle de référence : **v2.0.0** (source auditée le 2026-08-28 — § 8bis)

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

- **Conséquence** : `SimpMusicAdapter` passe en **v2** (version 2.0.0, plage
  `2.0.x`, adapterVersion 2) ; les plages du manifest des fonctionnalités
  navigation / orientation / player passent de `1.7.x` à `2.0.x`.
- ⚠️ Le sous-module `core` est un repo séparé (`maxrave-dev/core`, branche
  `multiplatform`) : à intégrer avec le clone (submodule) en Phase 2.
