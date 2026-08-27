# Système de plugins — Sankamusic

- **Statut** : 🟡 Squelette — à compléter après analyse de BetterDiscord (référence conceptuelle)
- **Document lié** : `docs/ARCHITECTURE.md`, `docs/SECURITY.md`

## 1. Objectif

Permettre d'étendre Sankamusic **sans modifier le Core** : nouveaux écrans, paramètres,
sources musicales, intégrations, thèmes, widgets. Inspiré de BetterDiscord/Spicetify,
mais avec une architecture native Android/Kotlin — **pas de copie de leur code**.

## 2. Cycle de vie d'un plugin

```
Installed → Enabled → Running → Disabled → Removed
                     │
                     └─ Crash → auto-disable (l'app continue de fonctionner)
```

Cycle de vie exposé (conceptuel) :

```kotlin
interface SpaceKaiPlugin {
    val manifest: PluginManifest
    fun onLoad()
    fun onEnable()
    fun onDisable()
    fun onUnload()
}
```

## 3. Manifest de plugin (conceptuel)

```json
{
  "id": "com.souanpt.spacekai.spotify",
  "name": "Spotify Sync",
  "author": "Souanpt",
  "version": "1.0.0",
  "apiVersion": 1,
  "minSankamusicVersion": "2.0.0",
  "maxSankamusicVersion": "2.x",
  "permissions": ["playlist.read", "playlist.write"],
  "dependencies": []
}
```

Champs obligatoires à valider à l'installation : `id` unique, `version`,
`apiVersion`, plage de compatibilité Sankamusic.

## 4. Permissions

Un plugin n'a **jamais** accès à tout par défaut. Permissions envisagées :

| Permission | Accès |
|------------|-------|
| `player.read` / `player.control` | lecture / contrôle du lecteur |
| `library.read` | bibliothèque |
| `playlist.read` / `playlist.write` | playlists (lecture / écriture) |
| `download.read` / `download.write` | téléchargements |
| `theme.modify` / `navigation.modify` | thèmes / navigation |
| `network` / `storage` | réseau / stockage |

Interface obligatoire : l'utilisateur doit pouvoir **voir les permissions** d'un plugin
avant activation, et les révoquer.

## 5. API publique (conceptuelle, à stabiliser)

```kotlin
SpaceKai.player
SpaceKai.library
SpaceKai.playlists
SpaceKai.navigation
SpaceKai.theme
SpaceKai.settings
SpaceKai.downloads
SpaceKai.network
```

Règles : API stable, détails internes cachés, changements uniquement en version majeure.

## 6. Isolation et robustesse

- Toute exception plugin est **capturée** : un plugin ne fait jamais planter l'app.
- Un plugin qui plante est **désactivé automatiquement** avec un message :
  « Le plugin X a été désactivé après une erreur. »
- Les opérations lourdes passent par coroutines + dispatchers dédiés ; un plugin lent ne
  bloque ni le player ni la navigation.
- Limites réelles du sandbox Android : **à documenter ici après analyse** (voir SECURITY.md).

## 7. Mises à jour indépendantes

Les plugins doivent pouvoir être mis à jour **sans reconstruire le Core** quand
l'architecture le permet (ex. : Spotify Plugin 1.3 → 1.4). Voir `UPDATE_SYSTEM.md`.

## 8. À compléter après analyse

- [ ] Modèle de distribution des plugins (fichier ? dépôt ? clé de signature ?)
- [x] Points d'extension concrets de l'UI — contrats créés (`HomeSection`, `SettingsEntry`,
      `PlayerAction`, `UiExtensionApi` + `UiExtensionRegistry`) ; rendu Compose à venir (Phase 2/3)
- [ ] Format de chargement/déchargement à chaud possible sur Android
- [x] Plugin d'exemple `HelloSpaceKai` — créé (manifest + lifecycle + section Home + entrée
      Settings via les points d'extension UI) ; bouton player à ajouter
