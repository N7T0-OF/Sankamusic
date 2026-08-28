# Migration — De SpaceKai-OLD vers Sankamusic

- **Statut** : 🟢 Étapes 1 (navigation), 2 (thèmes) et 3 (orientation) faites — étapes suivantes à poursuivre
- **Document lié** : `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`

## 1. Objectif

Récupérer les **fonctionnalités** de SpaceKai-OLD dans la nouvelle architecture
**sans copier son architecture**. SpaceKai-OLD est une source de fonctionnalités et
d'idées, pas un code à déplacer tel quel.

**Modèle cible (BetterDiscord)** : la base = **SimpMusic upstream** (mise à jour
reçue depuis `maxrave-dev/SimpMusic`), et SpaceKai = une **couche d'ajouts**
portés comme plugins/thèmes par-dessus cette base. SpaceKai-OLD contient tout
SimpMusic mélangé à la couche SpaceKai → on ne récupère **que les ajouts**
(§ 3), jamais le code de la base (il vient d'upstream, voir `UPSTREAM_SYSTEM.md`).
Quand SimpMusic sort une nouvelle version, la couche est re-vérifiée (adapter
1.7.x) au lieu d'être ré-écrite — comme BetterDiscord s'adapte à chaque mise à
jour de Discord, sans ré-écrire Discord.

## 2. Méthode

Pour chaque fonctionnalité, décider :

| Décision | Signification |
|----------|---------------|
| 🟢 **Core** | appartient au cœur de Sankamusic |
| 🟣 **Plugin** | devient un plugin SpaceKai |
| 🎨 **Thème** | devient un thème |
| 🔴 **Supprimée / reconstruite** | non conservée ou entièrement réécrite |

Règle : si l'ancienne implémentation est incompatible avec la nouvelle architecture,
on **réimplémente** — jamais on force une intégration fragile.

## 3. Inventaire des fonctionnalités SpaceKai-OLD (à compléter)

| Fonctionnalité | Décision prévue | Notes d'analyse |
|----------------|-----------------|-----------------|
| Navigation personnalisable | 🟢 Core | ✅ étape 1 faite |
| Thèmes | 🎨 Theme API | ✅ étape 2 faite (mode, source, overlay) |
| Dynamic Color | 🎨 Theme API | 🟡 partiel (source WALLPAPER déclarée, rendu UI à relier) |
| Orientation paysage (player) | 🟢 Core | ✅ étape 3 faite (modèle + réglage ; rendu UI étape 4) |
| Vibration | 🟢 Core |  |
| Spotify (OAuth PKCE, playlists) | 🟣 Plugin |  |
| Apple Music | 🟣 Plugin |  |
| Deezer | 🟣 Plugin |  |
| Téléchargement Wi-Fi | 🟢 Core |  |
| Widgets | 🟣 Plugin |  |
| Updater | 🟢 Core (UpdateManager) |  |
| Paramètres | 🟢 Core |  |

## 4. Ordre de migration (indicatif)

1. navigation → 2. thèmes → 3. orientation → 4. player → 5. vibration →
6. Dynamic Color → 7. paramètres → 8. updater → 9. Spotify → 10. Apple Music →
11. Deezer → 12. widgets

> L'ordre final est confirmé après validation du prototype (voir `ROADMAP.md`).
> Chaque migration est **testée** avant la suivante ; pas de migration groupée non validée.

### Étape 1 — Navigation (faite)

Portée depuis SpaceKai-OLD (`customNavigation`) :

- **Onglets extensibles** : `NavigationTab` (id, label, priority, iconName) dans
  `core/api/UiExtension.kt` ; `UiExtensionRegistry.navigationTabs()` trié par
  priorité ; onglets par défaut fournis par l'app (Accueil, Bibliothèque,
  Recherche, Paramètres).
- **Barre de navigation Compose** dans `MainActivity` : pilotée par le registre,
  icône résolue par `iconName` (convention "home"/"library"/"search"/"settings").
- **Plugin d'exemple** : `HelloSpaceKaiPlugin` ajoute un onglet « Hello »
  (priorité 40) en `onEnable`, le retire en `onDisable`.

Non porté (à faire dans une étape ultérieure) : swipe horizontal sur la barre
pour sauter de piste (dépend du player), variante minimaliste, icônes custom.

### Étape 2 — Thèmes (faite)

Porté depuis SpaceKai-OLD (`AppTheme` : themeMode / themeColorSource /
customThemeColor, `parseThemeColorHex`) :

- **Mode** : `ThemeMode` (LIGHT / DARK / SYSTEM) dans `core/api/ThemeSettings.kt`,
  retenu par `ThemeEngine` (`setMode`/`mode`).
- **Source de couleur** : `ThemeColorSource` (DEFAULT / WALLPAPER / CUSTOM) +
  couleur de graine custom (seed), `parseThemeColorHex` ("RRGGBB"/"AARRGGBB"),
  validation (CUSTOM exige un seed, échec propre). Le WALLPAPER = Dynamic Color
  Android (rendu Material You à relier par l'UI).
- **Modèle « base + couche »** : `SpaceKaiThemeTokens.overlay(other)` fusionne
  réellement (seuls les champs ≠ défauts sont remplacés) ; `ThemeEngine.activate`
  retourne `base.overlay(tokens)` avec bases clair/sombre intégrées selon
  `ThemeDefinition.base`.
- **Câblage** : `ThemeApi` étendu (`setMode`, `setColorSource`), `DefaultSpaceKaiApi`
  branché sur le vrai `ThemeEngine`. Thème `AMOLED` minimal ajouté au module
  `:themes:exampletheme` (démo overlay : fond/surfaces noirs sur base sombre).

Non porté (à faire dans une étape ultérieure) : rendu MaterialTheme Compose des
tokens (mapping UI du Core), Dynamic Color effectif (WALLPAPER), live editing
(Theme Editor), modes de navigation glass/translucent.

### Étape 3 — Orientation paysage du player (faite)

Porté depuis SpaceKai-OLD (`expect/ui/Orientation.kt`, flag `landscape_player`,
`FullscreenPlayer.android.kt` — force `SCREEN_ORIENTATION_LANDSCAPE` en plein
écran et restaure l'orientation d'origine à la sortie) :

- **Modèle** : `Orientation` (PORTRAIT / LANDSCAPE / UNSPECIFIED) et
  `PlayerOrientationMode` (FOLLOW_SYSTEM / FORCE_LANDSCAPE) dans
  `core/api/PlayerOrientation.kt`.
- **Décision pure** : `resolvePlayerOrientation(mode, current)` — en
  FORCE_LANDSCAPE le player est toujours en paysage, sinon il suit l'orientation
  courante (testée).
- **Réglage persistable** : clé `player.orientation` (`SettingsKeys`),
  valeurs `"system"` / `"landscape"` (`toPreferenceValue` /
  `parsePlayerOrientationMode`, `effectivePlayerOrientationMode` → défaut sûr).
- **Parité flag** : `playerOrientationModeFromFeatureFlag(landscapePlayer)` —
  le booléen SpaceKai-OLD `landscape_player` mappe sur FORCE_LANDSCAPE.

Non porté (à faire) : l'application effective de l'orientation par l'UI du
player (étape 4 — `requestedOrientation` Android, plein écran immersif,
restauration à la sortie), et le toggle dans l'écran Paramètres (étape 7).

## 5. Gestion des données existantes

- La migration de l'ancienne base de données vers la nouvelle doit être **testée** :
  perte de données = release refusée (checklist `RELEASE_GUIDE.md`).
- Ne **jamais** supprimer les données utilisateur lors d'une mise à jour.

## 6. À compléter après analyse

- [ ] Inventaire réel des fichiers/fonctions de SpaceKai-OLD (avec verdict par fonctionnalité)
- [ ] Schéma de données à migrer (tables, playlists, préférences)
- [ ] Risques de régression identifiés
