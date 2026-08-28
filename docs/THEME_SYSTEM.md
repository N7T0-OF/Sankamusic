# Système de thèmes — Sankamusic

- **Statut** : 🟢 Étape 2 (migration SpaceKai) faite — mode, source de couleur,
  overlay « base + couche » ; rendu MaterialTheme Compose à relier par l'UI (Phase 2/3)
- **Document lié** : `docs/ARCHITECTURE.md`, `docs/PLUGIN_SYSTEM.md`, `docs/MIGRATION.md`

## 1. Objectif

Permettre de personnaliser **l'apparence complète** de Sankamusic sans reconstruire
l'application : couleurs, surfaces, typographie, formes, transparence, navigation, player,
widgets.

> ⚠️ **Pas de CSS comme système principal.** Android utilise Jetpack Compose :
> les thèmes s'appuient sur un système de **tokens**.

## 2. Tokens de thème — `SpaceKaiThemeTokens`

Système central de personnalisation (conceptuel) :

| Catégorie | Exemples de tokens |
|-----------|--------------------|
| Couleurs | `primary`, `secondary`, `background`, `surface`, `onSurface`, `accent` |
| Surfaces | élévations, opacités, transparence |
| Typographie | familles, tailles, graisses |
| Formes | rayons (`radius`), coins, marges |
| Navigation | style (Classic / Translucent / Liquid Glass / Minimalistic), taille |
| Player | artwork, timeline, contrôles |
| Widgets | couleurs, formes, tailles |

Un thème = un ensemble de valeurs de tokens + règles de remplacement.

**Modèle « base + couche » (BetterDiscord)** : chaque thème s'applique sur une
**base** (`ThemeDefinition.base` : `"dark"` ou `"light"`) et n'écrase que les
champs qu'il modifie explicitement — `base.overlay(tokens)` dans
[`ThemeEngine.activate`] (seuls les champs ≠ valeurs par défaut sont remplacés,
le reste de la base est conservé). Exemple : le thème `AMOLED` du module
`:themes:exampletheme` ne surcharge que fond/surfaces (noir pur) et garde les
couleurs, formes et typographie de la base sombre.

## 3. Structure d'un thème (conceptuelle)

```json
{
  "id": "com.souanpt.spacekai.theme.amoled",
  "name": "AMOLED",
  "version": "1.0.0",
  "apiVersion": 1,
  "base": "dark",
  "tokens": {
    "color.background": "#000000",
    "color.surface": "#0A0A0A",
    "radius.card": 16
  }
}
```

Un thème peut cibler : couleurs, surfaces, typographie, formes, transparence,
navigation, player, widgets.

## 4. Dynamic Color Android + clair/sombre

Porté depuis SpaceKai-OLD (`AppTheme` : themeMode / themeColorSource /
customThemeColor) dans `core/api/ThemeSettings.kt` :

- **Mode** : `ThemeMode` — LIGHT / DARK / SYSTEM, retenu par le `ThemeEngine`
  (défaut DARK, comme SpaceKai-OLD). Modes gérés par tokens, pas par duplication
  d'écrans.
- **Source de couleur** : `ThemeColorSource` — DEFAULT (graine de l'app),
  **WALLPAPER (Dynamic Color Android / Material You)** et CUSTOM (couleur de
  graine choisie par l'utilisateur, parsée par `parseThemeColorHex`
  « RRGGBB »/« AARRGGBB »). Une source CUSTOM exige un seed : sans lui, échec
  propre (`ThemeEngine.setColorSource`), l'état ne change jamais en cas d'erreur.
- **Bases intégrées** : palette claire (défauts Material 3) et palette sombre
  (Material 3 dark, fond/surfaces AMOLED-friendly) dans le `ThemeEngine` ;
  `activate` retourne `base.overlay(tokens)`.

⚠️ Le **rendu effectif** (mapping tokens → MaterialTheme Compose, Dynamic Color
via `platformDynamicColorScheme`-équivalent, mode clair/sombre appliqué) est
assuré par l'UI du Core — pas encore relié (étape ultérieure).

## 5. Édition en direct (Live Editing)

Inspiré de BetterDiscord : modifier l'apparence **sans reconstruire** l'app quand c'est
techniquement possible.

- `Theme Editor` : couleurs, tailles, marges, radius, transparence, blur, typographie.
- Les changements s'appliquent **immédiatement** (recomposition Compose).
- Enregistrement + export du thème.

> Limites réelles (recomposition partielle, performances) : **à documenter après prototype**.

## 6. Cycle de vie d'un thème

```
Installé → Actif → Désactivé → Supprimé
```

Changer de thème ne doit **jamais** casser l'app : validation du thème avant activation,
retour au thème précédent en cas d'erreur.

## 7. À compléter après analyse

- [ ] Liste exacte des tokens nécessaires à la parité fonctionnelle avec SpaceKai-OLD
- [ ] UI Overrides : `replace` / `extend` / `decorate` / `hide` / `reorder`
  (PlayerArtwork, NavigationBar, HomeSection, SettingsSection…)
- [x] Thème d'exemple `ExampleTheme` — créé (`:themes:exampletheme`, tokens complets
  couleurs/surfaces/typographie/navigation/player, testé via le ThemeEngine)
- [ ] Interaction Theme API ↔ Plugin API (un plugin peut-il fournir un thème ?)
