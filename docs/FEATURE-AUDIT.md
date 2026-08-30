# FEATURE AUDIT — SpaceKai (« ZERO FALSE POSITIVE »)

> **Règle d'or (celle qui a été violée par le passé).** Une fonctionnalité n'est
> **IMPLEMENTED** que si les 5 conditions sont remplies :
>
> 1. son UI existe,
> 2. sa logique existe,
> 3. elle est réellement reliée au système concerné (pas un toggle décoratif),
> 4. elle fonctionne réellement,
> 5. elle ne provoque pas de régression.
>
> Le critère #3 est le piège principal de ce dépôt : un flag SpaceKai peut exister
> dans `SpaceKaiFeatures.kt`, être affiché comme interrupteur dans
> `SpaceKaiSettingsSection.kt`, être persistant DataStore… **et pourtant ne rien
> câbler.** Ce document vérifie chaque fonctionnalité par **preuve de code**
> (lignes réelles où le flag pilote un comportement), jamais « parce qu'un fichier
> existe ».

- **Espace audité** : `/d/JEUX/Sankamusic-dev` (snapshot sans `.git`, sous-module `core`
  partiellement extrait — **`core/data` et `core/domain` absents**).
- **Date** : 2026-08-26 (après préparation v2.0.0 / code 74).
- **Méthode** : grep du câblage réel de chaque flag (`SpaceKaiFeatures::<flag>` appelé
  hors des fichiers de déclaration / réglages), inspection des écrans concernés.
- **Exécution automatique** : `./scripts/audit-features.sh` (voir § GATE).

> ⚠️ **Limite d'audit** : le code des bugs P0 suivants vit dans `core/data` et
> `core/domain`, **non extraits dans cet espace**. Ils sont donc marqués
> `⚠️ NON AUDITABLE ICI` — il est IMPOSSIBLE de les déclarer quoi que ce soit sans
> le sous-module complet : Spotify OAuth + refresh token, import playlists,
> `UpdateRepositoryImpl` (update checker), Wi-Fi-only downloads, notifications.

---

## 📊 Synthèse

| # | Fonctionnalité | Statut réel | Preuve de code |
|---|---|---|---|
| 1 | Mise à jour interne (téléchargement + install) | 🔴 **NOT IMPLEMENTED** | voir détail |
| 2 | Player identique portrait/paysage (sans recherche vidéo) | 🟢 **OK dans ce code** | `NowPlayingScreen.kt:1036`, `App.kt:654` |
| 3 | Spotify OAuth `redirect_uri` | 🟠 **PAS CE DÉPÔT** (sp_dc + WebView) / non auditable | `SpotifyLoginScreen.kt` |
| 4 | Barre de navigation personnalisable | 🔴 **NOT IMPLEMENTED** | `App.kt:182` = swipe-only |
| 5 | Haptics | 🟢 **IMPLEMENTED** | 2 vrais call sites |
| 6 | Dynamic Color Android (fond inclus) | 🔴 **NOT WIRED** | 0 référence |
| 7 | Orientation auto/portrait/paysage (verrouillage) | 🔴 **NOT IMPLEMENTED** (enum existe, aucun réglage) | `expect/Orientation.kt`, `.android.kt` |
| 8 | Fondu enchaîné slider vs dropdown | 🟠 **PARTIAL / à vérifier** dans core | — |
| 9 | Vibration réglages (intensité, événements) | 🔴 **PARTIAL** (haptics player seulement) | `NowPlayingScreen.kt:2609` |
| 10 | Wi-Fi only downloads | 🔴 **NOT WIRED** (flag) / non auditable | 0 référence + core absent |
| 11 | Notification téléchargement unique | 🔴 **non auditable** (core/data absent) | — |
| 12 | Réglages sections repliées par défaut | 🔴 **NOT IMPLEMENTED** (liste plate) | `SettingScreen.kt` LazyColumn |
| 13 | Réglages sans superposition (anim OK) | 🟢 **CORRIGÉ** | `fadeIn/fadeOut` + clipToBounds |
| 14 | Personnalisation barre d'action (actions/ordre) | 🔴 **NOT IMPLEMENTED** | rien trouvé |
| 15 | Performance home (keys stables) | 🟢 **CORRIGÉ + gate** | `verify-perf-keys.sh` |
| 16 | Icônes verrouillées (app/circle) | 🟢 **CORRIGÉ + gate** | `verify-icons.sh` |
| 17 | Architecture upstream (git upstream) | 🟢 **SCRIPTÉ + documenté** | `update-upstream.sh`, `UPSTREAM.md` |

---

## 🔍 Détail par fonctionnalité

### 1. Barre de navigation personnalisable — 🟠 2 styles OK + masquage texte OK + rail paysage OK, mais personnalisation (sections/ordre/raccourcis) ABSENTE

Audit statique complet (App.kt + composants) :

**Ce qui existe et fonctionne (base SimpMusic, câblé) :**
- **Style verre liquide** : `isLiquidGlassEnabled` (DataStore) →
  `LiquidGlassAppBottomNavigationBar` (`App.kt:491`) ;
- **Style translucide** : `isTranslucentBottomBar` →
  `AppBottomNavigationBar(isTranslucentBackground = …)` (`App.kt:508`) ;
- **Masquer le texte** : `showLabels = !hideNavLabel` (bottom bar + rail,
  `App.kt:507/660`) — les items Material3 se **compactent** quand le label est
  null (icône seule, pas de hauteur vide réservée) ;
- **Paysage** : rail à **droite** pour téléphone paysage (`App.kt:654`),
  mêmes tabs, labels suivent le réglage ;
- **Swipe-to-skip** : `customNavigation` (`App.kt:182` → barre + rail).

**Ce qui manque (les demandes #25-28, #31) :**
- **Style minimaliste** : n'existe pas comme style distinct ; le flag SpaceKai
  `minimalisticNavigation` est **décoratif** (0 référence réelle) ;
- **pas de sélecteur de style unifié** — verre liquide et translucide sont deux
  toggles indépendants, pas des options exclusives ;
- **sections visibles** : seulement 2 tabs conditionnels codés en dur
  (`showAnalyticsTab`, `showMixForYouTab`) — pas de configuration utilisateur ;
- **ordre / réordonnancement** : `bottomNavScreens` est fixe ;
- **raccourcis** : aucun ;
- **aperçu avec switch** : aucun aperçu de barre n'existe — rien à supprimer.

👉 À implémenter : un sélecteur de style (translucide / verre liquide /
minimaliste) + configuration des sections (visibles, ordre, raccourcis) qui
pilote `bottomNavScreens`. Le gate `audit-navigation.sh` protège le câblage
existant (styles, masquage, rail paysage) contre les réversions upstream.

### 2. Player portrait/paysage — 🟠 PAS de recherche vidéo (bon), mais layout paysage ABSENT (le « mauvais système »)

Audit statique complet de `NowPlayingScreen.kt` (2640 lignes) + `App.kt` :

**Ce qui est confirmé bon :**
- **Aucune recherche vidéo à la rotation** : pas de branche qui cherche un format
  vidéo paysage ni de rechargement média. La rotation ne recrée pas le player.
  (`FullscreenPlayer.kt` est un destination **manuel** de vidéo plein écran,
  jamais déclenché par la rotation.)
- `App.kt:654` — téléphone paysage : nav déplacée à droite (`AppNavigationRail`).
- `App.kt:671` — tablette paysage : vue scindée avec la file de lecture.

**Ce qui manque (le vrai problème) :**
- `NowPlayingScreen.kt` **n'a AUCUNE branche d'orientation** : c'est un layout
  **portrait-first** étiré en paysage. Sur **téléphone en paysage**, le player
  s'affiche en **overlay plein écran** par-dessus l'app (`App.kt:727`,
  `isShowNowPlaylistScreen && !isTabletLandscape`) — le même arrangement
  portrait comprimé : pochette surdimensionnée, contrôles en bas, timeline
  étroite. C'est le « player transformé en autre écran » que tu décris.
- Le seul correctif existant est l'artwork (`aspectRatio(1f,
  matchHeightConstraintsFirst = true)`, `NowPlayingScreen.kt:1043`) — il évite
  le « rectangulaire » mais ne redistribue rien.
- Le flag SpaceKai `landscapePlayer` est **décoratif** (0 référence réelle) —
  aucune implémentation SpaceKai du player paysage n'existe.

**⇒ Le fix à faire** (layout-only, sans toucher au portrait) : ajouter une
branche `wDP > hDP` **dans** `NowPlayingScreen`/`NowPlayingScreenContent` qui
redistribue le même contenu horizontalement : pochette centrée, timeline pleine
largeur, contrôles accessibles. Le gate `audit-landscape-player.sh` protège le
correctif existant contre les réversions upstream.

### 3. Spotify — 🔴 constat définitif : PAS d'OAuth PKCE, PAS de redirect_uri, PAS d'import de playlists

Audit statique complet (composeApp + core/service, 2026-08-26) :

- Le login est une **WebView chargée à `Config.SPOTIFY_LOG_IN_URL`**
  (`SpotifyLoginScreen.kt:116`) qui extrait le cookie **`sp_dc`**
  (`saveSpotifySpdc` → `dataStoreManager.setSpdc`) — le flux InnerTune,
  **pas un OAuth**.
- **`SpotifyPkce.kt` (core/service/spotify) est du code mort** : `SpotifyPkce`,
  `generateCodeVerifier`, `codeChallenge` n'ont **aucun appel** dans composeApp
  ni core/service.
- **Aucun `redirect_uri` n'existe dans tout le repo** — le diagnostic
  « redirect_uri cassé » est une **fausse piste** : rien à corriger, et corriger
  un redirect_uri ne peut pas réparer la synchro des playlists.
- **L'import/synchro des playlists n'a jamais été implémenté** : le toggle
  SpaceKai `spotifySync` est **décoratif** (0 référence réelle, audité par
  `audit-features.sh`), et aucun code d'import n'existe dans composeApp.

👉 **Ce qu'il faut réellement faire** (P0 réel, pas le redirect_uri) :
1. décider de la stratégie — cookie sp_dc (ce que fait l'app, aucune
   credential Spotify Developer nécessaire, mais API limitée et ToS grise)
   **ou** OAuth officiel (exige une app Spotify Developer enregistrée, des
   credentials embarquées, et un redirect_uri déclaré — impossible pour un
   APK FOSS distribué sans compte propre) ;
2. implémenter l'import de playlists sur la stratégie retenue (UI + logique +
   intégration) — aujourd'hui il n'existe que le toggle.

Le gate `scripts/audit-spotify-flow.sh` protège la chaîne sp_dc existante
(chaque maillon : écran, statut, persistence, nav, réglages) et échoue si un
redirect_uri incohérent est un jour introduit (règle : UNE seule valeur).

### 3bis. Mise à jour interne — 🟠 détection OK, téléchargement/installation ABSENT (P0 n°1)

Audit statique complet de la chaîne (composeApp) :

**Ce qui existe et fonctionne (vérifié) :**
- Réglages → « Check for update » → `SharedViewModel.checkForUpdate()`
  (`SettingScreen.kt:2469`) ;
- cache timestamp `CheckForUpdateAt` (`SharedViewModel.kt:969`) — pas d'appel
  GitHub à chaque recomposition ✅ ;
- `updateRepository.checkForGithubReleaseUpdate()` (implémentation upstream
  dans core/data) ;
- dialogue de mise à jour (`App.kt:773`) avec bouton Download/Cancel ;
- **SPACEKAI CUSTOMIZATION** : le bouton Download ouvre
  `SpaceKaiUpdateConfig.releasesPageUrl` (la page releases SpaceKai) — correct,
  donner l'APK upstream (clé de signature différente) → « Application non
  installée ».

**Ce qui manque (le P0, ta checklist §1-7) :**
- **téléchargement interne de l'APK** — le bouton est un redirect navigateur ;
- **vérification SHA-256** avant installation ;
- **intent d'installation Android** (ACTION_VIEW, confirmation utilisateur — la
  limite OS) ;
- **sélection de l'APK** (`SpaceKai-vX.X.X.apk`, exclusion debug/arm64/…) ;
- **états UI** (Téléchargement… / Installation prête / Échec).

👉 À implémenter : remplacer `openUrl` par téléchargement interne + SHA-256 +
intent install. Le gate `audit-updater-flow.sh` protège la chaîne existante
(notamment la SPACEKAI CUSTOMIZATION, cible de réversion upstream).

### 4. Haptics — 🟢 IMPLEMENTED

```
HapticsSpaceKai.kt:40    if (isSpaceKaiFeatureEnabled(SpaceKaiFeatures::haptics)) { ... }
NowPlayingScreen.kt:2609  HapticsSpaceKai.onClick(LocalHapticFeedback.current)
```

✅ UI + logique + câblage (bouton play du player). **Câblage étroit** : seul le
bouton play vibre. Le reste de la demande (vibration changement de section,
activation désactivation réglage, slider fondu, intensité) **n'est pas implémenté**.

### 5–10. Flags SpaceKai — 🔴 6 sur 8 sont des toggles FACTICES

Vérification `grep -rn "SpaceKaiFeatures::<flag>"` hors
`SpaceKaiFeatures.kt`/`SpaceKaiSettingsSection.kt`/`SpaceKai.kt` :

| Flag | Réf. réelles | Statut |
|---|---|---|
| `haptics` | 2 | 🟢 implémenté |
| `customNavigation` | 1 (swipe-only) | 🟠 partiel |
| `minimalisticNavigation` | **0** | 🔴 factice |
| `dynamicColor` | **0** | 🔴 factice |
| `landscapePlayer` | **0** | 🔴 factice |
| `downloadWifiOnly` | **0** | 🔴 factice |
| `customPlayerInfo` | **0** | 🔴 factice |
| `spotifySync` | **0** | 🔴 factice |

**C'est la cause directe du symptôme** « j'ai activé la fonction dans les réglages et
il ne se passe rien » : `SpaceKaiSettingsSection.kt` enregistre la valeur, `configSpaceKai`
la ré-affiche… et aucun comportement n'est branché dessus.

### Réglages — superposition corrigée, sections repliées absentes

- 🟢 **Corrigé** (`RELEASE.md`, commits `99c5f561` + `257a497`) : `SettingScreen.kt`
  utilise `AnimatedVisibility` + `fadeIn/fadeOut` et non `expandVertically/shrinkVertically`
  dans un LazyColumn, avec `clipToBounds` — plus de chevauchement de textes.
- 🔴 **Pas de sections repliées par défaut** : la liste des réglages est plate
  (`LazyColumn` + `SettingItem`). Il n'y a pas de « Interface > / Contenu > / … »
  fermés au lancement. C'est un chantier UI à faire dans `composeApp`.
- 🔴 **Barre d'action** (activer/désactiver actions, ordre) : rien trouvé → à créer.

### Orientation (verrouillage) — 🔴 non câblé

L'infra existe (`enum class Orientation`, `expect fun currentOrientation()`,
`Orientation.android.kt`/`jvm.kt`), utilisée uniquement pour adapter la **nav**
(`App.kt:435-438`). Il n'y a **aucun réglage** « Orientation : Auto / Portrait /
Paysage » et aucun verrouillage (`setRequestedOrientation` n'apparaît que dans le
lecteur vidéo plein écran). Item #34 (et #7 ci-dessus) → **NOT IMPLEMENTED**.

### Dynamic Color (fond) — 🟠 capacité OK, fond sombre épinglé au noir (BUG CONFIRMÉ)

Audit statique complet du thème (`Theme.kt`, `PlatformColorScheme.android.kt`) :

**La capacité existe (base SimpMusic, câblée) :**
- palette **système** Android 12+ : `platformDynamicColorScheme` →
  `dynamicDarkColorScheme`/`dynamicLightColorScheme` ;
- **fond d'écran / seed personnalisé** : `rememberDynamicColorScheme`
  (materialkolor), choisi via `theme_color_source`
  (DEFAULT / WALLPAPER / CUSTOM, `SettingScreen.kt:641-643`).

**Le bug confirmé (ta plainte « fond noir fixe ») :**
- `PlatformColorScheme.android.kt` : `dynamicDarkColorScheme(context).copy(
  background = Color.Black, surface = Color.Black)` — même avec le scheme
  **système**, le fond sombre est épinglé au noir (« Keep the OLED look ») ;
- `Theme.kt` : `rememberDynamicColorScheme(isAmoled = isDark)` — le scheme seed
  sombre épingle aussi le fond au noir.
- **⇒ en mode sombre, le fond ne suit JAMAIS le Dynamic Color** — seuls les
  accents viennent de la palette. 188 `Color.Black` en dur dans composeApp.

👉 Fix : ne garder l'épinglage OLED que comme option explicite, pas par défaut
pour les schemes dynamiques, et câbler le flag SpaceKai `dynamicColor` (actuellement
décoratif) pour forcer le scheme dynamique. Gate : `audit-dynamic-color.sh`.

### Fondu (-enchaîné) — 🟠 dropdown localisé, slider à faire

Le réglage de durée du fondu est un **dropdown « micro fenêtre »** (la demande
#37 : le remplacer par un slider visuel) : `SettingScreen.kt:1225-1265` — un
`SettingAlertState`/`selectOne` avec valeurs Auto, 1s, 2s, 3s, 5s, 8s, 10s, 12s,
15s, 20s, 30s.

👉 À faire : remplacer ce bloc `onClick → setAlertData(selectOne=…)` par un
slider visuel (1-15s) à valeur immédiatement visible, sans micro fenêtre — avec
vibration au changement si le flag haptics est actif. La valeur finale appelée
`viewModel.setCrossfadeDuration(duration)` ne change pas.

### Performance home — 🟢 corrigé + gate

- Clés lazy stables (index keys, plus de `hashCode()` de données recréées) — fix du
  « home feed » du 2026-08-26,
- garde automatique : `scripts/verify-perf-keys.sh` (incluse dans
  `pre-release-report.sh`).

### Icônes — 🟢 verrouillées + gate

`scripts/verify-icons.sh` + `verify-icons.sh` dans le gate : toute modification ou
optimisation de `circle_app_icon.png` / `app_icon.png` bloque la release.

### Architecture upstream — 🟢 scriptée

`scripts/update-upstream.sh` + `UPSTREAM.md` + `SPACEKAI-ARCHITECTURE.md` : layer
feature-flag gated, hotspots de conflit documentés (update-checker URL & `conveyor.conf`
`vcs-url`).

---

## ⚠️ Items NON AUDITABLES dans ce snapshot

Ces fonctionnalités vivent dans les sous-modules **absents** (`core/data`,
`core/domain`) : il est **interdit de les déclarer** sur la foi de ce dossier seul.

- Spotify OAuth PKCE / refresh token / token storage / logout / expiration (#15)
- Import / synchronisation playlists Spotify (#16)
- `UpdateRepositoryImpl.checkForGithubReleaseUpdate` → pointe-t-il réellement
  `SpaceKaiUpdateConfig` ? (#1)
- Téléchargement Wi-Fi only + pause/reprise + notification unique playlist (#10, #11)
- Fondu enchaîné durée/slider durée côté lecture (#8)

---

## 🔒 GATE « ZERO FALSE POSITIVE »

Le gate a dix volets.

**Volet automatique 1 — toggles factices** — `scripts/audit-features.sh`
(`pre-release-report.sh` → bloquant ; `release-nightly-check.yml` → warning) :
- échoue si un flag SpaceKai n'a **aucun** câblage réel hors fichiers de
  déclaration/réglages (« toggle factice ») ;
- liste les toggles factices bloquant la release.

**Volet automatique 3 — hotspots upstream (réversions silencieuses)** —
`scripts/audit-upstream-hotspots.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning) :
- FAIL si `conveyor.conf` → `app.vcs-url` ne pointe plus sur `N7T0-OF/Sankamusic`
  (Conveyor dérive alors tous les liens de téléchargement desktop vers le mauvais repo),
- FAIL si `UpdateRepositoryImpl.kt` (dépôt complet) ne référence pas
  `SpaceKaiUpdateConfig` (update checker revenu aux releases SimpMusic),
- WARN si le fichier n'est pas présent (sous-module `core/` non extrait — non
  vérifiable dans ce snapshot).

**Volet automatique 2 — régression UI (superposition des réglages)** —
`scripts/audit-settings-ui.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning) :
- scanne les fichiers contenant `LazyColumn`/`LazyRow` et FAIL si une animation
  de taille (`expandVertically`/`shrinkVertically`/variantes horizontales)
  apparaît à une ligne non inspectée manuellement (allowlist) — c'est la cause
  du bug « textes des réglages superposés » (fix `99c5f561` + `257a497`) ;
- *non* automatisé (trop de faux positifs) : la règle maxLines sur les
  descriptions et les sections repliées par défaut restent une **checklist
  manuelle** (`docs/ci-cd.md` § UI audit) tant qu'aucun pattern automatique
  fiable n'existe.

**Volet automatique 4 — FEATURE AUDIT généré (rapport par preuves)** —
`scripts/generate-feature-audit.sh` (`pre-release-report.sh` → gate ;
`release-nightly-check.yml` → détection précoce ; `apply-release-pipeline.sh`
→ propagé au dépôt live) :
- génère `docs/FEATURE-AUDIT-REPORT.md` à **chaque release** : pour chaque
  fonctionnalité SpaceKai, preuves grep (UI + logique + câblage) et verdict
  IMPLEMENTED (static) / PARTIALLY IMPLEMENTED / NOT IMPLEMENTED / BROKEN /
  NON VÉRIFIABLE / OK (gate) — jamais de verdict sans preuve ;
- réutilise le verdict de `audit-features.sh` (un flag décoratif ⇒ la feature
  est PARTIALLY IMPLEMENTED, pas « done ») et celui de `audit-settings-ui.sh`
  (échec ⇒ la feature Paramètres est BROKEN) ;
- produit le bloc « ### Connu / non terminé » prêt à coller dans les notes de
  release (règle : une feature non finie DOIT être listée explicitement) ;
  `scripts/append-feature-audit-gaps.sh` l'ajoute automatiquement à TOUTE note
  générée (CI + générateur local) avec la section Installation/SHA-256 ;
- **claim cross-check** : signale les lignes du changelog / RELEASE.md qui
  revendiquent une feature classée NON terminée (lignes négatives ignorées) ;
- dans `pre-release-report.sh` : BROKEN ⇒ **bloquant**, NOT IMPLEMENTED /
  PARTIALLY / contradictions ⇒ **warn** (les gaps doivent figurer aux notes).

**Volet automatique 5 — flux Spotify (chaîne sp_dc + cohérence redirect_uri)** —
`scripts/audit-spotify-flow.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning ; `apply-release-pipeline.sh` → propagé) :
- documente le constat : login Spotify = cookie `sp_dc` (WebView), PAS d'OAuth
  PKCE, aucun `redirect_uri` dans le repo — « redirect_uri cassé » est une
  fausse piste ; l'import de playlists n'existe pas (toggle décoratif) ;
- FAIL si un maillon de la chaîne cookie casse (merge upstream) :
  `saveSpotifySpdc(` absent de l'écran, regex `statusUrl` disparue, `setSpdc`
  absent du ViewModel, destination non enregistrée, item Réglages absent ;
- FAIL si un jour un `redirect_uri` est introduit avec **plusieurs valeurs
  distinctes** (règle P0 : une seule URI, strictement identique partout).

**Volet automatique 6 — player paysage (régression du layout)** —
`scripts/audit-landscape-player.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning ; `apply-release-pipeline.sh` → propagé) :
- documente le constat : `NowPlayingScreen` n'a **aucune branche d'orientation**
  (layout portrait-first) ; téléphone paysage = overlay plein écran portrait
  comprimé (le « mauvais système ») ; `landscapePlayer` décoratif ;
- FAIL si le correctif artwork existant (`aspectRatio(1f,
  matchHeightConstraintsFirst = true)` dans `NowPlayingScreen.kt`) disparaît
  (merge upstream qui réintroduit le « player rectangulaire ») ;
- FAIL si la détection `isPhoneLandscape` ou le garde `!isTabletLandscape`
  disparaissent d'`App.kt`.

**Volet automatique 7 — mise à jour (chaîne détection + URL SpaceKai)** —
`scripts/audit-updater-flow.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning ; `apply-release-pipeline.sh` → propagé) :
- documente le constat : la **détection** existe (réglage → checker → dialogue,
  cache `CheckForUpdateAt`), le bouton Download ouvre la page releases SpaceKai
  (SPACEKAI CUSTOMIZATION) ; le **téléchargement/installation interne n'existe
  pas** (P0 n°1 à implémenter) ;
- FAIL si le dialogue disparaît, si `checkForUpdate()` /
  `checkForGithubReleaseUpdate()` / le cache timestamp sont retirés, si
  l'entrée Réglages disparaît, ou si `SpaceKaiUpdateConfig.releasesPageUrl` est
  réverté (utilisateurs renvoyés à la page SimpMusic → APK mal signé).

**Volet automatique 8 — barre de navigation (styles + masquage + rail)** —
`scripts/audit-navigation.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning ; `apply-release-pipeline.sh` → propagé) :
- documente le constat : verre liquide + translucide existent (base), masquer le
  texte compacte réellement (M3), rail paysage à droite OK ; minimaliste,
  sections, ordre, raccourcis, aperçu → absents ;
- FAIL si la sélection de style (`isLiquidGlassEnabled` /
  `isTranslucentBottomBar`), le câblage `showLabels = !hideNavLabel`, ou le
  rail paysage (`isPhoneLandscape` + `AppNavigationRail`) disparaissent
  (merge upstream).

**Volet automatique 9 — Dynamic Color (capacité + fond)** —
`scripts/audit-dynamic-color.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning ; `apply-release-pipeline.sh` → propagé) :
- documente le constat : capacité présente (palette système + wallpaper/seed),
  mais fond sombre épinglé au noir même avec un scheme dynamique (bug confirmé,
  `PlatformColorScheme.android.kt` + `isAmoled = isDark`) ;
- FAIL si la palette système (`dynamicDark/LightColorScheme`),
  `platformDynamicColorScheme`, `rememberDynamicColorScheme`, ou le sélecteur
  `theme_color_source` disparaissent (merge upstream).

**Volet automatique 10 — architecture providers (design-first)** —
`scripts/audit-provider-arch.sh` (`pre-release-report.sh` → bloquant ;
`release-nightly-check.yml` → warning ; `apply-release-pipeline.sh` → propagé) :
- direction Universal Music Platform (docs/PROVIDER-ARCHITECTURE.md) : abstraction
  `MusicProvider` + capabilities, un seul moteur OAuth, jamais de secret dans
  l'APK, jamais de scraping ;
- **Mode A** (état actuel, design seul) : FAIL si du code provider-spécifique
  apparaît avant l'abstraction — le mode de défaillance « trois systèmes
  d'auth cassés » ;
- **Mode B** (abstraction présente) : FAIL si un provider n'implémente pas
  `MusicProvider`, n'est pas capability-gated, ou contient un secret en dur.

**Garde-fou visuel** — cette page `docs/FEATURE-AUDIT.md` :
- indique le statut réel (IMPLEMENTED / PARTIALLY IMPLEMENTED / NOT IMPLEMENTED /
  BROKEN / NON AUDITABLE) au moment de l'audit ;
- **détection nightly (warning)** : si un merge upstream ré-introduit un toggle factice
  ou une animation de taille dans un lazy item, `release-nightly-check.yml` lève
  un `::warning::` chaque nuit (détection avant la release) ;
- doit être mise à jour **avant chaque release**, pas après ;
- le rapport machine (volet 4) est la **preuve** du statut de cette page :
  si les deux divergent, c'est la page qui a tort.
- les lignes 🔴 restent visibles tant qu'elles ne sont pas réellement câblées.

> **Règle finale** : NE PAS déclarer une fonctionnalité « faite » parce qu'un fichier
> existe. CODE + UI + INTÉGRATION + TEST. Un toggle qui ne pilote aucun comportement
> est un toggle factice et bloque la release.