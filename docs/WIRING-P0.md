# WIRING-P0 — plan de câblage des 6 toggles factices (v2.0.0)

> **Pourquoi ce document.** `audit-features.sh` bloque la release v2.0.0 tant que
> 6 flags SpaceKai sont des toggles décoratifs : déclarés, persistés, affichés
> dans les réglages — mais avec **0 référence réelle** hors déclaration/persistance/UI.
> Ce document donne, pour chacun, le point d'insertion **vérifié dans le code**
> (fichier, ligne, contexte), le câblage minimal, et le critère qui fera passer
> le gate. Il complète la carte d'extension de `docs/SPACEKAI-ARCHITECTURE.md`
> (le *où* théorique) avec le *où exact* actuel.
>
> État vérifié le 2026-08-26 sur ce snapshot (`composeApp` présent, `core/data`
> absent). Les numéros de ligne sont ceux du snapshot — à re-vérifier sur le
> dépôt live avant d'éditer.

## La règle du gate

`audit-features.sh` compte les références réelles de chaque flag **hors** des
3 fichiers `spacekai/SpaceKaiFeatures.kt`, `spacekai/SpaceKai.kt` et
`spacekai/ui/SpaceKaiSettingsSection.kt`. Une seule référence réelle (≥ 1
call site) suffit pour passer. La forme canonique :

```kotlin
if (isSpaceKaiFeatureEnabled(SpaceKaiFeatures::<flag>)) { ... }
```

Depuis cette session, le gate vérifie **aussi** le câblage de couche :
`applyPersistedSpaceKaiFeatures` doit être appelé au démarrage (App/DesktopApp)
— déjà le cas (`App.kt:175`).

---

## 1. `spotifySync` — Sync Spotify (lyrics/canvas + import)

- **État** : toggle décoratif (0 call site). Persisté (`spacekai_spotify_sync`).
- **Contexte réel** : la chaîne sp_dc existe dans la base —
  `viewModel/LogInViewModel.kt:28-37` (`saveSpotifySpdc` → `setSpdc`), écran
  `ui/screen/login/SpotifyLoginScreen.kt`. La décision P0 est documentée dans
  `docs/PROVIDER-ARCHITECTURE.md` §5bis : sp_dc pour lyrics/canvas, OAuth PKCE
  (mode dev) pour l'import — **aucun OAuth ni import n'existe aujourd'hui**.
- **Point d'insertion (visible ici)** : `LogInViewModel.kt` — la méthode
  `saveSpotifySpdc` / le chemin de persistance du cookie, et l'appel du service
  Spotify qui en découle (lyrics/canvas). Sur le live : `core/service/spotify/`
  (`SpotifyRepository` — la carte d'extension dit « decorate/wrap the
  repository »).
- **Câblage minimal honnête** : brancher le flag sur le **chemin de sync**
  existant — ex. n'exposer le toggle dans les réglages QUE si le flag est actif,
  ou gate l'appel de `saveSpotifySpdc` par le flag. Attention : un câblage
  « 1 référence pour passer le gate » qui ne pilote rien de réel est interdit
  par l'esprit ZERO FALSE POSITIVE — le flag doit contrôler une vraie
  différence de comportement.
- **Critère audit-features** : ≥ 1 référence `SpaceKaiFeatures::spotifySync`
  hors fichiers ignorés, pilotant le chemin sp_dc.

## 2. `minimalisticNavigation` — variante barre minimaliste

- **État** : câblé dans `App.kt` et les barres (plus de toggle décoratif).
- **Contexte réel** : la sélection des barres vit dans `App.kt` :
  `isLiquidGlassEnabled == TRUE` → `LiquidGlassAppBottomNavigationBar`, sinon
  `AppBottomNavigationBar` (avec `isTranslucentBackground`). Le rail est
  sélectionné ailleurs (`AppNavigationRail`, lignes ~536/655).
- **Point d'insertion** : le bloc de résolution des tabs dans `App.kt` et les
  listes par défaut des barres.
- **Câblage réalisé** : `minimalisticNavigation` retire Mix-for-you et réduit la
  hauteur de la barre plate, mais conserve Analytics lorsque le suivi local est
  actif. La même règle est utilisée par la barre, le rail et la barre verre
  Android ; la personnalisation applique ensuite l'ordre et les éléments masqués.
- **Critère** : ≥ 1 référence réelle dans App.kt ou un composable barre,
  pilotant le rendu.

## 3. `dynamicColor` — overrides SpaceKai du thème

- **État** : toggle décoratif (0 call site). La capacité base est OK
  (palette système + wallpaper/seed), le bug connu : fond sombre épinglé au noir.
- **Contexte réel** : `ui/theme/Theme.kt:120-128` — `wallpaperScheme`
  (`platformDynamicColorScheme`) et `seedColor` (`customThemeColor ?: seed`)
  puis la construction du `colorScheme`.
- **Point d'insertion** : `Theme.kt` autour de la sélection du schéma —
  appliquer l'override SpaceKai (ex. forcer la source de couleur / le seed
  SpaceKai) quand le flag est actif, sans casser la palette wallpaper/seed
  existante.
- **Câblage minimal** : un chemin « override SpaceKai » branché dans
  `Theme.kt` (fonction appelée dans la construction du colorScheme).
- **Critère** : ≥ 1 référence réelle dans Theme.kt (ou un fichier de thème).

## 4. `landscapePlayer` — layout player paysage

- **État** : toggle décoratif (0 call site). **Le correctif artwork paysage
  existe déjà et est inconditionnel** — `NowPlayingScreen.kt:1036-1042`
  (`.aspectRatio(1f, matchHeightConstraintsFirst = true)`, commenté
  « SPACEKAI FIX »). C'est un bugfix, pas la feature « layout paysage ».
- **Point d'insertion** : `NowPlayingScreen.kt` — la branche paysage
  (composition différente quand largeur > hauteur, ex. rail/contrôles latéraux),
  gateée par le flag. Le correctif artwork (bugfix) reste inconditionnel.
- **Câblage minimal** : une branche `if (isSpaceKaiFeatureEnabled(...))` qui
  choisit le layout paysage SpaceKai, le layout portrait/upstream restant la
  valeur par défaut.
- **Critère** : ≥ 1 référence réelle dans NowPlayingScreen.kt (ou un composable
  player) pilotant la branche paysage.

## 5. `downloadWifiOnly` — téléchargements Wi-Fi uniquement

- **État** : toggle décoratif (0 call site). Le toggle existe dans les réglages
  (`SpaceKaiSettingsSection.kt:138`).
- **Contexte réel** : **aucun code de téléchargement dans composeApp** — la
  logique vit dans `core/data` (absent de ce snapshot). Le point d'insertion
  est sur le dépôt live : le worker / repository de download, où la vérification
  du réseau (Wi-Fi vs cellulaire) doit être gateée par le flag.
- **Câblage minimal (live)** : dans le chemin d'enqueue du download, refuser
  (ou reporter) le téléchargement hors Wi-Fi quand le flag est actif — en
  réutilisant le `NetworkState`/connectivity existant (le design
  `PROVIDER-ARCHITECTURE.md` évoque le type `NetworkState`).
- **Critère** : ≥ 1 référence réelle (dans composeApp OU core/data, selon où
  vit l'appel), pilotant la vérification réseau du download. **À noter** :
  `audit-features.sh` ne scanne que `composeApp/src` — si le câblage vit dans
  core/data, il faut soit une référence dans composeApp (ex. passer le flag au
  ViewModel de téléchargement), soit étendre le scan du gate aux chemins core.

## 6. `customPlayerInfo` — infos player désactivables

- **État** : toggle décoratif (0 call site).
- **Contexte réel** : les lignes d'info vivent dans `NowPlayingScreen.kt` —
  ex. la rangée description à `NowPlayingScreen.kt:2455-2475` (titre
  « Description », `DescriptionView`), et les lignes artiste/titre/paroles
  autour.
- **Point d'insertion** : `NowPlayingScreen.kt` — la visibilité des lignes
  d'info (artiste, description, paroles) gateée par le flag (ex. masquer les
  lignes quand le flag est désactivé, ou rendre la sélection désactivable).
- **Câblage minimal** : conditionner le rendu des lignes d'info par
  `isSpaceKaiFeatureEnabled(SpaceKaiFeatures::customPlayerInfo)`.
- **Critère** : ≥ 1 référence réelle dans NowPlayingScreen.kt pilotant la
  visibilité des lignes d'info.

---

## Ordre recommandé (impact croissant, risque décroissant)

1. **`landscapePlayer`** et **`customPlayerInfo`** — mêmes fichiers
   (`NowPlayingScreen.kt`), câblage local, test visuel immédiat.
2. **`minimalisticNavigation`** — `App.kt`, une branche dans la sélection de
   barre, régression visuelle facile à vérifier.
3. **`dynamicColor`** — `Theme.kt`, override appliqué en amont de la
   construction du schéma.
4. **`spotifySync`** — `LogInViewModel.kt` + `core/service/spotify/` (live) —
   nécessite la décision §5bis (aucun OAuth aujourd'hui ; câbler le flag sur le
   chemin sp_dc existant sans créer de login factice).
5. **`downloadWifiOnly`** — core/data (live) — vérifier l'étendue du scan
   d'`audit-features.sh` si le câblage n'atteint pas composeApp.

## Guide pas-à-pas — câbler `customPlayerInfo` (le premier, le plus local)

Fichier : `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/NowPlayingScreen.kt`
(repères de lignes = ce snapshot, à re-vérifier sur le live).

**1. Imports** (en tête du fichier, près de l'import HapticsSpaceKai existant) :

```kotlin
import com.maxrave.simpmusic.spacekai.SpaceKaiFeatures
import com.maxrave.simpmusic.spacekai.isSpaceKaiFeatureEnabled
```

**2. Le flag est déjà déclaré** (`SpaceKaiFeatures.kt`) — rien à changer :

```kotlin
/** Custom player info line (artist · title · album) rendering. */
val customPlayerInfo: Boolean = false,
```

**3. Le câblage** — dans la composition, juste avant le bloc d'info (~:2455) :

```kotlin
// SPACEKAI FEATURE: customPlayerInfo — "infos player désactivables"
// (description, paroles). OFF = lignes d'info masquées ; ON = comportement
// par défaut (affichées). La release démarre allEnabled, donc visible ;
// l'utilisateur peut désactiver.
val showPlayerInfo = isSpaceKaiFeatureEnabled(SpaceKaiFeatures::customPlayerInfo)
```

**4. Gater le bloc description** (~:2458-2484) — envelopper le titre +
`DescriptionView` :

```kotlin
if (showPlayerInfo) {
    Text(
        text = stringResource(Res.string.description),
        style = typo().labelSmall,
        color = Color.White,
    )
    Spacer(modifier = Modifier.height(10.dp))
    DescriptionView(
        text = screenDataState.songInfoData?.description ?: "",
        onTimeClicked = { raw -> /* inchangé */ },
        onURLClicked = { url -> uriHandler.openUri(url) },
    )
}
```

**5. Gater les paroles** (bloc `LyricsView` ~:2254) — même pattern :

```kotlin
if (showPlayerInfo) {
    screenDataState.lyricsData?.let {
        LyricsView(
            lyricsData = it,
            timeLine = sharedViewModel.timeline,
            onLineClick = { f -> sharedViewModel.onUIEvent(UIEvent.UpdateProgress(f)) },
        )
    }
}
```

**6. Critère de passage du gate** :

```bash
bash scripts/audit-features.sh   # customPlayerInfo doit passer de FAIL à PASS
# → 6 FAIL deviennent 5 (spotifySync, minimalisticNavigation, dynamicColor,
#   landscapePlayer, downloadWifiOnly restent décoratifs)
```

**7. Test visuel (obligatoire avant de continuer)** : lancer l'app, ouvrir le
Now Playing, basculer le toggle `customPlayerInfo` dans les réglages SpaceKai
→ la description et les paroles doivent disparaître/réapparaître ; redémarrer
l'app → l'état doit survivre (persistance : `applyPersistedSpaceKaiFeatures`
ré-applique au démarrage).

**Le piège à éviter** : ne pas câbler par une référence morte (ex. une variable
`showPlayerInfo` calculée mais jamais utilisée) — le gate passerait mais la
feature serait toujours décorative. Chaque `if (showPlayerInfo)` doit piloter
un rendu réellement visible.

## Guide pas-à-pas — câbler `minimalisticNavigation` (variante compacte)

Fichiers : `App.kt` (sélection des barres, ~:491-506) + les deux composables de
barre (`AppBottomNavigationBar.kt`, `LiquidGlassAppBottomNavigationBar.kt`).
`BottomNavScreen` est un `sealed class` dans `LiquidGlassAppBottomNavigationBar.kt`
(:41-99) : Home, Search, Library, Analytics, MixForYou.

**1. Lire le flag dans App.kt**, près des autres états de barre :

```kotlin
// SPACEKAI FEATURE: minimalisticNavigation — variante compacte (moins de tabs).
val minimalisticNav = isSpaceKaiFeatureEnabled(SpaceKaiFeatures::minimalisticNavigation)
```

**2. Le passer aux deux barres** (comme `showAnalyticsTab`/`showMixForYouTab`) :

```kotlin
LiquidGlassAppBottomNavigationBar(
    ...,
    showAnalyticsTab = showAnalyticsTab,
    showMixForYouTab = showMixForYouTab && !minimalisticNav,  // ex. : retiré en compact
    minimalistic = minimalisticNav,                            // nouveau param
)
// idem AppBottomNavigationBar(...)
```

**3. Dans chaque barre**, ajouter le paramètre et filtrer **les DEUX listes de
tabs** — `AppBottomNavigationBar.kt` en contient deux : le `listOf` de la barre
(~:96-100) et un `listOfNotNull` du rail (~:234-240). Les deux suivent le même
schéma et doivent filtrer ensemble, sinon la barre devient compacte mais le
rail non (incohérence visuelle) :

```kotlin
minimalistic: Boolean = false,   // nouveau param de la signature

// dans les DEUX listes de tabs (barre ~:96 ET rail ~:234) :
BottomNavScreen.Home,
BottomNavScreen.MixForYou.takeIf { showMixForYouTab && !minimalistic },
BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
BottomNavScreen.Library,
BottomNavScreen.Search,
```

`LiquidGlassAppBottomNavigationBar.kt` (la variante verre liquide) a sa propre
liste — même filtre si elle est séparée.

**4. Critère de passage** : `audit-features` — `minimalisticNavigation` ne doit
plus être signalé comme décoratif ; les audits de navigation et de compilation
confirment le câblage.

**5. Test visuel** : basculer le toggle → la barre perd Mix-for-you, garde
Analytics lorsque le suivi local est actif, et garde Home/Library/Search ; les
styles liquide/translucide et le rail paysage doivent réagir de la même manière.

## Guide pas-à-pas — câbler `landscapePlayer` (branche paysage du player)

Fichier : `NowPlayingScreen.kt`. Le correctif artwork paysage (~:1036,
`matchHeightConstraintsFirst`) est un **bugfix inconditionnel** — ne pas le
mettre sous le flag. Le flag gate la **branche de layout paysage** (contrôles
latéraux / composition différente), qui n'existe pas encore.

**1. Imports** :

```kotlin
import com.maxrave.simpmusic.spacekai.SpaceKaiFeatures
import com.maxrave.simpmusic.spacekai.isSpaceKaiFeatureEnabled
```

**2. Détecter l'orientation** (NowPlayingScreen n'a aucun accès orientation
aujourd'hui — utiliser `BoxWithConstraints` au niveau du layout principal) :

```kotlin
BoxWithConstraints {
    val isLandscape = maxWidth > maxHeight
    // SPACEKAI FEATURE: landscapePlayer — layout paysage dédié quand le flag
    // est actif ; sinon le layout portrait/upstream reste la valeur par défaut.
    val spaceKaiLandscape =
        isLandscape && isSpaceKaiFeatureEnabled(SpaceKaiFeatures::landscapePlayer)

    if (spaceKaiLandscape) {
        // Branche paysage SpaceKai : artwork à gauche, contrôles à droite,
        // timeline pleine largeur (à composer — c'est la feature).
    } else {
        // Layout portrait/upstream existant (inchangé).
    }
}
```

**3. Critère de passage** : `audit-features` — `landscapePlayer` FAIL → PASS
(≥ 1 référence réelle dans NowPlayingScreen.kt), 4 → 3 FAIL.

**4. Test visuel** : téléphone en paysage + flag actif → layout paysage dédié ;
flag désactivé → layout actuel inchangé ; le correctif artwork
(`matchHeightConstraintsFirst`) reste appliqué dans les deux cas.

**Le piège** : ne pas confondre la branche paysage (feature) avec le correctif
artwork (bugfix). Si on met le correctif sous le flag, un utilisateur qui
désactive le toggle revoit le carré géant qui pousse les contrôles — la
régression revient.

## Guide pas-à-pas — câbler `spotifySync` (le cas qui exige l'honnêteté)

**Le constat (décision §5bis, `docs/PROVIDER-ARCHITECTURE.md`)** : aucun OAuth
n'existe ; le chemin réel est le cookie `sp_dc` (WebView → DataStore `setSpdc`),
qui alimente lyrics/canvas. L'import de playlists (OAuth PKCE mode dev) n'est
PAS implémenté. La consommation réelle du cookie (lyrics/canvas) vit dans
`core/service/spotify/` — **pas dans composeApp**.

**Ce qui existe dans composeApp (vérifié)** :
- `LogInViewModel.kt:28-37` — `saveSpotifySpdc` → `setSpdc` (persistance) ;
- `SettingsViewModel.kt:1723-1745` — `getSpotifyLogIn` (collecte spdc),
  `setSpotifyLogIn` (logout qui détruit lyrics/canvas/ClientToken) ;
- `SettingScreen.kt:1710-1725` — l'item Spotify (login/logout) ;
- `ModalBottomSheet.kt:3302` — le hint « your sp_dc param of spotify cookie ».

**Le câblage honnête n'est PAS dans composeApp seul.** Gater le hint ou l'item
de login par le flag serait une différence réelle mais faible, et surtout elle
ne gate pas ce que la feature promet (la sync). Deux options honnêtes :

**Option A (recommandée, sur le live)** — gate la vraie consommation : dans
`core/service/spotify/`, le chemin qui utilise le cookie (fetch lyrics/canvas)
est gateé par le flag :

```kotlin
// dans le service Spotify (live) :
val spotifySync = isSpaceKaiFeatureEnabled(SpaceKaiFeatures::spotifySync)
if (spotifySync) { /* fetch lyrics/canvas via sp_dc — le chemin réel */ }
```

Cela demande une référence au flag depuis un module core → vérifier que
`core/service/spotify` peut dépendre de `spacekai` (ou passer l'état du flag en
paramètre depuis composeApp). **Si le chemin lyrics/canvas n'est pas
restructurable dans la fenêtre de release : passer à l'option B.**

**Option B (fallback honnête)** — **retirer le toggle** plutôt que de le câbler
faux. La règle d'audit-features est symétrique : un flag retiré de
`SpaceKaiFeatures.kt` (et de `SpaceKaiSettingsSection.kt`) n'est plus un toggle
décoratif — le gate passe, et la note de release ne le promet plus (la ligne
« Login Spotify » quitte le bloc Connu / non terminé… ou y reste en NOT
IMPLEMENTED si la feature est retirée de l'audit aussi). C'est la seule option
qui garantit « jamais un login factice ».

**Procédure de retrait (vérifiée par simulation 2026-08-26)** — trois actions,
la 2 est déjà faite ici et part via A_FILES :

1. **Retirer le flag** de `SpaceKaiFeatures.kt` (déclaration + défaut),
   `SpaceKaiSettingsSection.kt` (toggle UI) et la ligne de persistance — le
   flag disparaît de l'énumération d'`audit-features` → plus de FAIL.
2. **Le générateur ne doit PAS référencer le nom du flag comme preuve** —
   `scripts/generate-feature-audit.sh` ligne `spotify` utilisait
   `spotifySync`/`SpaceKaiFeatures::spotifySync` en patterns : flag retiré,
   ces patterns font 0 hit et la ligne « Login Spotify » basculerait en **NOT
   IMPLEMENTED** (et non PARTIALLY comme promis). Corrigé ici : la ligne
   porte maintenant `flag=-` + patterns de la **chaîne réelle**
   (`saveSpotifySpdc` UI / `setSpdc` logique, toutes deux dans composeApp) →
   le verdict vient des preuves, pas du flag.
3. **Nettoyer la note de gap** — l'ancienne note gardait le résidu
   « (toggle décoratif) » qui survivrait au retrait. Corrigé ici (voir §2) :
   la note décrit la chaîne réelle (login WebView → saveSpotifySpdc →
   setSpdc → spotifyLoggedIn, gate audit-spotify-flow.sh) et l'absence
   d'OAuth PKCE / import.

**Vérification post-retrait** : `bash scripts/generate-feature-audit.sh` → la
ligne `spotify` reste **PARTIALLY IMPLEMENTED** avec les snippets de la chaîne
sp_dc ; `bash scripts/audit-features.sh` → 6 FAIL deviennent 5 (plus de
`spotifySync`) ; le tableau des flags de `docs/FEATURE-AUDIT.md` (§ annexe)
perd la ligne `spotifySync | 0 | 🔴 factice` — à retirer à la main sur le live
(ce doc source n'est pas régénéré).

**Interdit** : câbler le flag sur une brique décorative pour faire passer le
gate (ex. `showSpotifySyncLabel = flag` qui n'affiche qu'un label sans
comportement). Le gate passerait, la feature resterait fausse — exactement ce
que ZERO FALSE POSITIVE interdit.

**Critère de passage (si option A)** : ≥ 1 référence réelle de
`SpaceKaiFeatures::spotifySync` hors fichiers ignorés, pilotant le chemin
sp_dc. **Attention à l'étendue du scan** : `audit-features.sh` ne scanne que
`composeApp/src` — si le câblage vit dans core/service, il faut soit une
référence dans composeApp (passer l'état au service), soit étendre le scan aux
chemins core (voir le cas `downloadWifiOnly`).

## Guide pas-à-pas — câbler `downloadWifiOnly` (le cas core/data)

**Le constat (vérifié)** : `core/` ne contient que `service` dans ce snapshot —
**`core/data` est absent**, et aucun code de téléchargement n'existe dans
composeApp (le toggle est déclaré : `SpaceKaiSettingsSection.kt:138`, persisté :
`SpaceKai.kt:87` — rien d'autre). Toute la logique de téléchargement vit dans
`core/data` **sur le dépôt live**.

**Le point d'insertion (live)** : le worker / repository de téléchargement de
`core/data` — la vérification du réseau au moment de l'enqueue. Le design
(`PROVIDER-ARCHITECTURE.md`, type `NetworkState`) propose un état
ONLINE / OFFLINE / METERED / WIFI / VPN / LIMITED :

```kotlin
// dans le chemin d'enqueue du téléchargement (live) :
val wifiOnly = /* état du flag SpaceKai downloadWifiOnly */
val network = /* NetworkState courant (connectivité existante) */
if (wifiOnly && network != WIFI) {
    // refuser / reporter : « Téléchargement en Wi-Fi uniquement »
} else {
    // enqueue normal
}
```

**La question d'architecture à trancher (comme spotifySync)** : `audit-features.sh`
ne scanne que `composeApp/src`. Si le câblage vit dans core/data, deux options :
1. **passer l'état du flag depuis composeApp** (le ViewModel de téléchargement le
   lit et le transmet au repository — une référence réelle dans composeApp,
   le gate passe) ;
2. **étendre le scan** d'audit-features aux chemins `core/` (plus large, mais
   demande une adaptation du gate — à documenter dans audit-features.sh).

**Option fallback honnête** : **retirer le toggle** (comme spotifySync) si le
câblage core n'est pas faisable dans la fenêtre de release — un flag retiré
n'est plus un toggle décoratif, et la note de release ne le promet plus.

**Interdit** : une référence morte dans composeApp (ex. lire le flag dans un
ViewModel sans le transmettre au worker) — le gate passerait, le comportement
resterait faux.

**Critère de passage** : ≥ 1 référence réelle de `SpaceKaiFeatures::downloadWifiOnly`
hors fichiers ignorés, **pilotant la vérification réseau du téléchargement** —
pas seulement la lecture du flag.

## Guide pas-à-pas — câbler `dynamicColor` (override SpaceKai du thème)

Fichier : `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/theme/Theme.kt`
(la construction du schéma ~:118-134). La capacité base est OK (wallpaper +
seed) ; le bug connu : `isAmoled = isDark` épingle le fond sombre au noir pur.

**1. Imports** :

```kotlin
import com.maxrave.simpmusic.spacekai.SpaceKaiFeatures
import com.maxrave.simpmusic.spacekai.isSpaceKaiFeatureEnabled
```

**2. Le câblage** — une fonction SpaceKai qui produit un override, appelée dans
la construction du `colorScheme` (~:128) :

```kotlin
// SPACEKAI FEATURE: dynamicColor — override SpaceKai du schéma. Exemple réel :
// désépingler le fond sombre du noir pur (isAmoled = false) quand le flag est
// actif — la correction du bug « fond sombre épinglé au noir ».
val spaceKaiOverride =
    isSpaceKaiFeatureEnabled(SpaceKaiFeatures::dynamicColor)

val colorScheme =
    if (spaceKaiOverride) {
        rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = isDark,
            isAmoled = false,            // SpaceKai : surfaces sombres non noires
            style = PaletteStyle.TonalSpot,
            modifyColorScheme = { cs -> if (isDark) cs else cs.withNeutralLightSurfaces() },
        )
    } else {
        wallpaperScheme
            ?: rememberDynamicColorScheme(/* schéma base inchangé */)
    }
```

(La variante exacte dépend du comportement voulu — l'essentiel : le flag doit
piloter une **vraie différence** dans le schéma produit, pas une variable
inutilisée.)

**3. Critère de passage** : `audit-features` — `dynamicColor` FAIL → PASS
(≥ 1 référence réelle dans Theme.kt), 3 → 2 FAIL.

**4. Test visuel** : thème sombre + flag actif → surfaces non-noires ; flag
désactivé → comportement base inchangé (noir pur) ; le wallpaper/seed existants
continuent de fonctionner dans les deux cas.

## Décisions d'architecture (tranchées 2026-08-26)

1. **Étendue du scan d'`audit-features.sh` → DÉCISION : l'état du flag remonte
   via composeApp (option A).** Vérifié : `spacekai/` vit dans composeApp, et
   `core/` n'en dépend pas (Clean Architecture) — un câblage qui lirait le flag
   directement dans core créerait une dépendance core → composeApp, interdite.
   Règle : **chaque câblage qui touche core (Spotify, download) passe par une
   référence réelle dans composeApp** — le ViewModel/UI lit le flag et le
   transmet à core en paramètre (ou via une interface). Le scan du gate reste
   donc inchangé (composeApp), et la référence y vit naturellement. Note
   documentée dans l'en-tête d'audit-features.sh. **La règle est gardée par
   un gate** : `audit-provider-arch.sh` (§ Layer integrity) FAIL si un module
   core importe `com.maxrave.simpmusic.spacekai` ou `.ui.` — testé (faux
   import dans core → FAIL, restore → PASS).
2. **Dépendance `core/service/spotify` → `spacekai` → RÉSOLUE par la décision
   1** : pas de dépendance core → spacekai. Le service reçoit l'état du flag en
   paramètre depuis composeApp (ex. `spotifyService.syncEnabled = flag`).
3. **`spotifySync` → ARBITRAGE : option B (retirer le toggle).** Justification :
   (a) l'add-on n'a **aucune feature de sync à offrir** — l'import OAuth n'existe
   pas (§5bis) ; (b) l'option A gaterait le chemin sp_dc de la **BASE** avec un
   flag SpaceKai — quand le flag est off, la base perdrait lyrics/canvas, une
   sémantique douteuse pour un add-on ; (c) l'effort A (threader le paramètre
   composeApp → service) est moyen pour un bénéfice incertain. Effort B ≈ 15
   min (retirer la déclaration, le toggle UI, la ligne de persistance).
   Conséquence audit : la ligne « Login Spotify » reste dans le FEATURE AUDIT
   avec son verdict par preuves (chaîne sp_dc réelle — PARTIALLY), mais sans
   toggle décoratif → `audit-features` passe. **Le flag reviendra quand la
   feature de sync existera réellement** (import OAuth, décision §5bis).
4. **`downloadWifiOnly` → ARBITRAGE : option A (câbler à l'enqueue).**
   Justification : le toggle SpaceKai est le **SEUL réglage wifi-download du
   code visible** (la base n'en a pas — vérifié dans SettingScreen) ; la
   sémantique est claire (refuser le download hors Wi-Fi) ; l'effort est
   modéré (ViewModel download lit le flag + le réseau, transmet `wifiOnly` au
   repository core/data). Caveat honnête : le check a lieu à l'enqueue, pas au
   retry du worker (un download lancé en Wi-Fi poursuit en cellulaire). Si le
   câblage core/data n'est pas faisable dans la fenêtre → repli sur l'option B.

**Repli B — procédure DIFFÉRENTE de spotifySync (vérifiée par simulation
2026-08-26)** : contrairement à Spotify, il n'existe **aucune chaîne réelle** à
re-pointer dans composeApp (le câblage vit dans core/data, absent ici) — les
patterns du générateur sont le *nom du flag*. Donc :

1. Retirer le flag (déclaration + défaut + toggle UI + persistance) — comme
   spotifySync.
2. **Retirer aussi la ligne `wifi-only` de `scripts/generate-feature-audit.sh`**
   — sinon le flag disparu laisse les patterns `downloadWifiOnly`/
   `SpaceKaiFeatures::downloadWifiOnly` à 0 hit et la feature bascule en
   **NOT IMPLEMENTED fantôme** (« Aucune preuve (UI, logique, câblage) dans le
   scope vérifiable ») alors qu'elle est simplement *retirée* — le pire des
   deux mondes : une ligne de plus dans le bloc « Connu / non terminé » pour
   une feature qui n'existe plus.
3. Vérifier : `bash scripts/generate-feature-audit.sh` → compteurs à **22
   features** (1/9/9/0/2/1, sans la ligne wifi-only) ; `audit-features` 6 → 5
   FAIL ; et **zéro contradiction** — le changelog 73 ne revendique rien sur
   le wifi (vérifié : aucun claim download/wifi dans `73.txt`).

   Variante si la feature doit rester tracée : la passer en `flag=CORE`
   (verdict NON VÉRIFIABLE, logique dans core/data) au lieu de la retirer —
   mais un repli B est un retrait ; la ligne CORE n'a de sens que si le
   câblage existe réellement sur le live.
5. **Les guides customPlayerInfo, minimalisticNavigation, landscapePlayer,
   dynamicColor** sont prêts à exécuter tels quels — aucun arbitrage
d'architecture, uniquement du code + test visuel.

## État final attendu après exécution du plan

| Toggle | Décision | Effet sur audit-features |
| --- | --- | --- |
| `customPlayerInfo` | câbler (guide) | FAIL → PASS |
| `minimalisticNavigation` | câbler (guide) | FAIL → PASS |
| `landscapePlayer` | câbler (guide) | FAIL → PASS |
| `dynamicColor` | câbler (guide) | FAIL → PASS |
| `spotifySync` | **retirer** (arbitrage B) | flag disparu → plus de FAIL |
| `downloadWifiOnly` | câbler à l'enqueue (arbitrage A, repli B) | FAIL → PASS (ou flag retiré) |

Résultat : **`audit-features` vert** (0 flag décoratif) → `check-pre-tag.sh` ne
garde que les 3 obstacles naturels du dépôt live (git, origin, gh) →
`publish.sh` s'ouvre.

## Après le câblage

- `bash scripts/audit-features.sh` → **RESULT: no decorative SpaceKai flags**.
- `bash scripts/check-pre-tag.sh` → les 4 FAIL restants se réduisent aux 3
  naturels du dépôt live (git/origin/gh) — plus aucune raison de contenu.
- La note de release régénérée perd les lignes « Toggle décoratif » des flags
  câblés (le bloc « Connu / non terminé » reste pour ce qui est réellement
  partiel : ex. `spotifySync` sans import OAuth).
- **Ne jamais** « câbler » un flag par une référence morte pour faire passer le
  gate : chaque câblage doit piloter une différence de comportement réelle,
  sinon c'est un nouveau toggle décoratif qui passe le filet.

**Simulation du câblage des 4 toggles prêts (2026-08-26)** — un stub suffit
pour passer le gate, donc le TEST VISUEL est la vraie garde :

- 4 références simulées (`customPlayerInfo`, `minimalisticNavigation`,
  `landscapePlayer`, `dynamicColor`) → `audit-features` passe de **6 à 2 FAIL**
  (restent `spotifySync`, `downloadWifiOnly`), exactement comme le plan le
  promet.
- **Piège révélé** : le générateur récompense AUSSI le stub — `player-info` a
  une note de gap vide (`||`), donc UI + logique + câblage à ≥1 hit = verdict
  **IMPLEMENTED (static)** dans le rapport, alors que le comportement réel
  n'existe pas encore. Les 3 autres (`nav-minimal`, `dynamic-color`,
  `landscape-player`) restent PARTIALLY grâce à leur note de gap non vide.
- Conséquence : le gate et le rapport ne distinguent pas un stub d'un vrai
  câblage — seul le **test visuel obligatoire** de chaque guide (basculer le
  toggle → le comportement change ; redémarrer → l'état survit) le fait. Un
  câblage qui saute le test visuel peut écrire IMPLEMENTED dans la note de
  release sans que le comportement existe : c'est exactement la fausse
  confiance que cette session élimine.
