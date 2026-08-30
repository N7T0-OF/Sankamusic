# UPSTREAM STATUS

Diagnostic objectif — SpaceKai / Sankamusic vs SimpMusic upstream.
Méthode : faits vérifiés dans le dépôt local (historique git, merge-bases,
tags, diff), pas des suppositions.

---

## Déclaration courte

**SpaceKai N'EST PAS une imitation de SimpMusic.** C'est un fork profond de
SimpMusic (historique commun de 1454+ commits), qui reprend le vrai moteur
(player Media3, `core/media/media3`, scraper `kotlinYtmusicScraper`, données
Room, réseau). Le seul vrai décalage : **la base upstream intégrée (1.7.x)
est en retard d'une version** — l'upstream a publié `v2.0.0`.

Ce document fige le diagnostic pour piloter la migration 1.7.x → 2.0.0.

---

## Champs demandés par le cahier des charges (§3)

| Champ | Valeur (vérifié le 2026-08-30) |
|---|---|
| Current SimpMusic upstream | `maxrave-dev/SimpMusic`, branch `dev`/`main` |
| Latest release | **`v2.0.0`** (2026-08-28) |
| Integrated release | **`1.7.x`** — `merge-base` HEAD↔`v2.0.0` = `11d3eaf3` (base 1.7.4) |
| Compatibility | À établir après migration ; actuellement base intégrée ≠ base publiée |
| Version SpaceKai | `0.2.2` (code 77) — la release publiée ne contient PAS encore la migration |
| Adapter version | alignée sur `SPACEKAI_BASED_ON_UPSTREAM` (actuellement `1.7.0`) |

- Commits entre le merge-base et `v2.0.0` : **24**
- Fichiers affectés : **116**
- Insertions/délétions (brut) : **~9 824 / ~2 327**

---

## Provenance (preuves)

- `git remote -v` → remote `upstream` = `https://github.com/maxrave-dev/SimpMusic.git`
- `git merge-base HEAD upstream/dev` et `HEAD upstream/main` → **merge-base trouvés** :
  l'historique SpaceKai descend réellement de l'historique upstream.
- Tag `v2.0.0` présent localement ; `merge-base HEAD v2.0.0` = `11d3eaf3`.
- Le vrai moteur est en `core/` (sous-module) : `core/media/media3/.../exoplayer/`
  contient `CrossfadeExoPlayerAdapter.kt`, `ExoPlayerAdapter.kt`,
  `DelegatingForwardingPlayer.kt` ; `core/service/kotlinYtmusicScraper/` contient
  `YouTube.kt`, `Ytmusic.kt`, `extractor/`, `parser/`, `models/`.

## Attention au diff brut merge-base..v2.0.0

Ce diff mélange deux directions et ne doit PAS être lu comme « ce que v2.0.0
apporte » :
- des fichiers **SpaceKai existants** (`composeApp/.../spacekai/*.kt`) y figurent
  car le merge-base est antérieur à leur création ;
- des fichiers **upstream 2.0.0 absents de HEAD** (vrais apports upstream) —
  ex. `HapticManager.*`, `CollapsibleSection.kt`, `MinimalisticAppBottomNavigationBar.kt`,
  `SmartShuffle.kt`, `SpotifySync*`, `PlaylistExport.kt`, `NavigationBarStyleSelector.kt`.

## Vrais apports upstream identifiés (absents de HEAD)

- `composeApp/src/androidMain/kotlin/com/maxrave/simpmusic/expect/HapticManager.android.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/expect/HapticManager.kt`
- `composeApp/src/jvmMain/kotlin/com/maxrave/simpmusic/expect/HapticManager.jvm.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/extension/PlaylistDuration.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/extension/PlaylistExport.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/CollapsibleSection.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/MinimalisticAppBottomNavigationBar.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/NavigationBarStyleSelector.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/SmartShuffle.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/navigation/destination/login/SpotifySyncDestination.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/login/SpotifySyncScreen.kt`
- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SpotifySyncViewModel.kt`

## Fichiers divergents (conflit potentiel de réconciliation)

Fichiers présents DES DEUX CÔTÉS mais avec des contenus différents (SpaceKai a
sa propre évolution, upstream a la sienne). Résolution = réconcilier, pas écraser :

`composeApp/...App.kt, HomeScreen.kt, LibraryScreen.kt, SettingScreen.kt,
NowPlayingScreen.kt, MiniPlayer.kt, ArtistScreen.kt, PlaylistScreen.kt,
LocalPlaylistScreen.kt, CreditScreen.kt, ModalBottomSheet.kt, MoodCategoryCard.kt,
LyricsView.kt, EndOfPage.kt, ReviewDialog.kt, Theme.kt,
ui/theme, ui/navigation/graph/LoginScreenGraph.kt,
viewModel/SharedViewModel.kt, viewModel/SettingsViewModel.kt,
AppBottomNavigationBar.kt, LiquidGlassAppBottomNavigationBar.{common,android,jvm}.kt,
FilePicker.{common,android}.kt, PlatformColorScheme.android.kt,
strings.xml / app_name.xml, andApp/Desk topApp, .github/workflows, scripts/*`

---

## Modules manquants (à re-établir après migration)

- Espace : aucun module de moteur manquant — `core/media/media3`, scraper,
  données, réseau, download présents.
- À VERIFIER après le merge : dépendances/versions du fichier de catalogue
  (`gradle/libs.versions.toml`) et signatures Endpoints dont le format a changé en 2.0.0.

## Fonctionnalités à vérifier une à une (checklist §14)

| Fonction | Statut attendu après migration |
|---|---|
| Home | ⏳ à re-tester en runtime |
| Search | ⏳ |
| Player / Queue | ⏳ (moteur présent, adapter à re-vérifier vs 2.0.0) |
| Lyrics | ⏳ |
| Artist / Album / Playlist | ⏳ |
| History | ⏳ |
| Downloads | ⏳ |
| Authentication | ⏳ |
| YouTube Music (scraper) | ⏳ (parser peut suivre les changements API 2026) |
| Audio / Video / Background playback | ⏳ |
| Casting | ⏳ |
| Settings / Notifications / Cache / Database / Network | ⏳ à re-valider en build |

Statut : tous ⏳ tant que le merge + build + test appareil ne sont pas faits.
NE RIEN déclarer PASS avant vérification runtime (§2, §14, §25).

---

## Stratégie de migration recommandée

1. Branche dédiée depuis HEAD (ex. `migrate/upstream-2.0.0`).
2. Merge de `v2.0.0` (ou `upstream/main`) dans cette branche.
3. Résoudre :
   - pour les fichiers upstream-apports (absents de HEAD) : ADOPTER upstream,
     puis re-réappliquer la couche SpaceKai par-dessus quand pertinente ;
   - pour les fichiers divergents : réconcilier feature par feature,
     en conservant la persistance SpaceKai et les fonctionnalités SpaceKai
     déjà réelles (updater deux-blocs, flags persistants) ;
   - `SPACEKAI_BASED_ON_UPSTREAM` → `2.0.0` une fois adapté.
4. Rebaser la couche SpaceKai (nav, thèmes, haptics, landscape, custom-player,
   settings, plugins) par-dessus la nouvelle base — pas l'inverse.
5. Compilation, audits, tests appareil (installation neuve + mise à jour +
   données conservées), puis seulement release.

## Règles de conduite (§2, §5, §12, §17, §18, §19)

- JAMAIS installer l'APK officiel SimpMusic par-dessus SpaceKai (§5).
- Signature SpaceKai persistante ; package conservé si utile à la migration (§12).
- Données utilisateur conservées (§13).
- Aucune release tant qu'une fonction critique est FAIL (§18/§19).
- Un seul APK + SHA256SUMS (RELEASE_GUIDE.md).