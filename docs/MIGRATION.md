# Migration — De SpaceKai-OLD vers Sankamusic

- **Statut** : 🟢 Étape 1 (navigation) faite — étapes suivantes à poursuivre
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
| Navigation personnalisable | 🟢 Core |  |
| Thèmes | 🎨 Theme API |  |
| Dynamic Color | 🎨 Theme API |  |
| Orientation paysage (player) | 🟢 Core |  |
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

## 5. Gestion des données existantes

- La migration de l'ancienne base de données vers la nouvelle doit être **testée** :
  perte de données = release refusée (checklist `RELEASE_GUIDE.md`).
- Ne **jamais** supprimer les données utilisateur lors d'une mise à jour.

## 6. À compléter après analyse

- [ ] Inventaire réel des fichiers/fonctions de SpaceKai-OLD (avec verdict par fonctionnalité)
- [ ] Schéma de données à migrer (tables, playlists, préférences)
- [ ] Risques de régression identifiés
