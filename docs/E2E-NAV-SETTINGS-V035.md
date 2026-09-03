# E2E — Patch navigation / Settings v0.3.5 (protocole de test sur appareil)

> Statique et honnêteté : ce protocole décrit le test **device** du patch
> « Settings top gap + Analytics en navigation compacte + contrat de sélection
> nav partagé » — PR #2, squash-merge dans `dev` en `db002314`
> (commit de travail `0435a759`). Le code est CI-vert (gates shell, `jvmTest`
> dont 11 cas `NavCustomizationTest`, `compileKotlinMetadata`, `assembleRelease`)
> ; **rien ici n'est prouvé sur appareil** tant que ce protocole n'a pas été
> exécuté. Chaque flux cite le fichier:ligne qui l'implémente, et chaque échec
> pointe le maillon à diagnostiquer en premier (règle : identifier LE maillon,
> corriger, re-tester — jamais « partiellement fonctionnel »).

## 1. Quatre comportements à prouver

| # | Comportement | Implémentation (ouvrir si échec) |
|---|---|---|
| F1 | L'écran Settings n'a plus un trou de ~376dp au-dessus du premier titre de section | `SettingScreen.kt` — `Spacer(Modifier.height(64.dp))` juste avant l'item `user_interface` |
| F2 | L'onglet Analytics reste visible en navigation compacte (minimaliste) quand le tracking local est ON ; seul Mix-for-you est retiré par le mode compact | `NavCustomization.kt` — `defaultNavTabs()` (`Analytics.takeIf { showAnalyticsTab }`, `MixForYou.takeIf { showMixForYouTab && !minimalistic }`) |
| F3 | Masquer via la personnalisation l'onglet **actuellement sélectionné** fait basculer vers une destination visible (aucun écran orphelin, aucun onglet fantôme en surbrillance) | `App.kt` — `LaunchedEffect(effectiveNavTabs…)` (route fallback) ; `resolveNavSelection` / `resolveNavSelectionIndex` consommés par les 3 renderers (barre plate, rail, glass bar Android) |
| F4 | Masquer Search supprime son contrôle partout (FAB Search sur la barre plate et la barre glass) — aucun contrôle ne pointe une destination cachée | `AppBottomNavigationBar.kt` et `LiquidGlassAppBottomNavigationBar.android.kt` — FAB rendu seulement si `bottomNavScreens.any { it == BottomNavScreen.Search }` |

## 2. Prérequis (important)

1. **Un téléphone branché** (débogage USB) ou un émulateur, `adb devices` le liste.
2. **APK signé avec la MÊME clé que la v0.3.4 installée** — le test est un
   upgrade in-place (données conservées). Le build CI « Build release APK
   (FOSS) » (workflow sur le PR event) produit un APK signé avec le keystore de
   release ; c'est l'artefact à utiliser, PAS l'APK unsigned local de
   `assembleRelease`.
3. **versionCode** : le build dev est en `0.3.4` / `82`. Installé par-dessus une
   v0.3.4 publiée (même code, même signature), `adb install -r` réussit et
   conserve les données. Ne pas tester par-dessus une version **plus récente**.
4. Feature flags SpaceKai : la release build démarre avec `allEnabled`, donc
   `customNavigation` et `minimalisticNavigation` sont disponibles dans
   Paramètres → SpaceKai. Si un flag a été persisté OFF, le réactiver d'abord.

## 3. Driver automatisé

```bash
# Depuis la racine du dépôt, téléphone branché :
./scripts/device-acceptance-nav-settings.sh <chemin.apk>
# ou sans APK si déjà installé :
./scripts/device-acceptance-nav-settings.sh
```

Le driver vérifie l'appareil, installe (optionnel), lance l'app, puis exécute
les quatre flux **en interactif** : il dumps la hiérarchie UI
(`uiautomator dump`) pour les assertions automatisables (F1, F2-présence),
imprime les étapes manuelles exactes pour le reste, demande la confirmation de
l'opérateur à chaque verdict, et écrit un résumé final dans
`device-acceptance-results.txt`. `exit 0` seulement si tout est PASS.

## 4. Flux détaillés (si exécution manuelle)

### F1 — Trou Settings

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | Ouvrir Réglages (icône engrenage depuis la bibliothèque / profil) | La liste s'ouvre en haut | `SettingScreen.kt` | — |
| 2 | Observer l'espace au-dessus du premier titre de section | Titre (« User interface » / « Interface » / traduit) visible juste sous la barre d'app — écart < ~200dp, PAS ~376dp+ | `SettingScreen.kt` — `Spacer(64.dp)` avant l'item `user_interface` | Re-trouver l'écart ≈ 376dp → le spacer a régressé (ou l'ancre du glow ambiant a été recollée au-dessus du premier item) |

Automatisé : le driver mesure `y` du premier titre de section dans le dump et
vérifie `y/density < 250dp` (post-fix ≈ 100-150dp avec barre de statut ;
pré-fix ≈ 400dp+).

### F2 — Analytics en navigation compacte

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | Réglages → Listening history / tracking : activer le tracking local | — | `SettingsViewModel` | — |
| 2 | Réglages → SpaceKai : activer « minimalisticNavigation » (mode compact) ; style de barre au choix | — | flags SpaceKai | — |
| 3 | Revenir à l'accueil | Les libellés de la barre sont compactés, **l'onglet Analytics reste présent** | `defaultNavTabs()` dans `NavCustomization.kt` | Onglet Analytics absent → `showAnalyticsTab` faux (tracking OFF) OU la barre utilise encore une liste locale qui filtre Analytics en compact (ancien code `!minimalistic`) |
| 4 | Si NON connecté à YouTube (session anonyme) | Pas d'onglet « Mix » — attendu dans tous les cas en compact | idem | Onglet Mix présent en compact → la condition `!minimalistic` a disparu de Mix-for-you |

### F3 — Masquer l'onglet sélectionné (bascule immédiate)

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | Activer `customNavigation` (SpaceKai) et se rendre sur l'onglet Analytics | — | — | — |
| 2 | Réglages → SpaceKai → « Navigation personnalisée » → couper l'interrupteur Analytics | L'app quitte Analytics **immédiatement** et affiche une destination visible (premier onglet) ; aucun écran « Analytics » orphelin | `App.kt` — `LaunchedEffect(effectiveNavTabs…)` navigate vers `effectiveNavTabs.first().destination` | Reste bloqué sur Analytics → l'effet route ne se déclenche pas (vérifier que `customNavTabs` change bien : `spaceKaiNavHidden` collecté dans App.kt) |
| 3 | Vérifier la surbrillance de la barre | Un onglet visible est en surbrillance (jamais aucun, jamais Analytics fantôme) | `resolveNavSelectionIndex` dans les 3 renderers | Rien en surbrillance → l'effet `LaunchedEffect(bottomNavScreens…)` du renderer manque / `selectedIndex` hors liste |
| 4 | Variante bord : masquer **tous** les onglets | Il reste au minimum l'accueil (barre jamais vide, pas de crash) | `ensureUsableNavTabs()` | Barre vide / crash → `ensureUsableNavTabs` contourné (liste passée non normalisée) |

### F4 — Masquer Search

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | (Prérequis F3) Dans l'éditeur de navigation, masquer Search | Disparaît de la barre/rail | résolveur partagé | Encore visible → la barre utilise sa propre liste |
| 2 | Style barre plate (Translucent/Minimalist) | Le **FAB rond Search** disparaît à côté de la capsule | `AppBottomNavigationBar.kt` — `if (bottomNavScreens.any { it == Search })` | FAB encore là → condition absente/contournée |
| 3 | Style Liquid glass | Idem : pas de FAB glass Search | `LiquidGlassAppBottomNavigationBar.android.kt` | FAB encore là → condition absente/contournée |
| 4 | Re-montrer Search | Le FAB revient | idem (inverse) | Ne revient pas → la liste n'est pas re-résolue |

## 5. Critère de sortie du patch v0.3.5 (Gate 0 device)

- [ ] F1 : écart Settings < ~200dp (automatisé par le driver).
- [ ] F2 : Analytics présent en compact + tracking ON ; Mix absent en compact.
- [ ] F3 : masquer l'onglet sélectionné bascule vers une destination visible ; surbrillance toujours valide ; « tout masquer » garde l'accueil.
- [ ] F4 : masquer Search retire son FAB (barre plate **et** glass) ; re-montrer le restaure.
- [ ] Données et compte YouTube intacts après l'installation in-place.
- [ ] Chaque échec ramené à un maillon précis (colonnes « Maillon » ci-dessus), corrigé, puis protocole rejoué.

Après Gate 0 vert seulement : corriger le générateur de changelog si besoin,
puis bump `0.3.5` / versionCode `83` (à vérifier dans le dépôt), tag `v0.3.5`,
CI → draft → publication — jamais avant validation appareil réelle.
