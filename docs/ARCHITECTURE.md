# Architecture — Sankamusic

- **Statut** : 🟡 Squelette — à compléter après l'analyse des 4 dossiers
- **Document lié** : `ARCHITECTURE_DECISIONS.md` (ADR), `docs/UPSTREAM_SYSTEM.md`

## 1. Objectif

Sankamusic est une **plateforme indépendante** d'extension d'un lecteur de musique,
conceptuellement inspirée de BetterDiscord/Spicetify, mais **nativement Android/Kotlin
(et desktop)**. SimpMusic est un **upstream / compatibility target**, jamais un code copié.

## 2. Architecture cible (conceptuelle)

```
                  SIMPMUSIC (upstream)
                        │
                        ▼
              UpstreamAdapter (isolation)
                        │
                        ▼
             ┌────────────────────────┐
             │     SANKAMUSIC CORE    │
             │                        │
             │  UI · Player · Data    │
             │  Network · Settings    │
             └───────────┬────────────┘
                         │  SpaceKai API (stable)
        ┌────────────────┼─────────────────┐
        ▼                ▼                 ▼
   Plugin API       Theme API         Update Manager
        │                │                 │
        ▼                ▼                 ▼
   Plugins           Themes            Upstream
   (Spotify, ...)    (tokens)          compat.
```

## 3. Modules prévus

| Module | Rôle | Analyse à compléter |
|--------|------|---------------------|
| **Core** | Lifecycle, configuration, orchestration |  |
| **UI** | Écrans Compose, navigation, composants réutilisables |  |
| **Player** | Lecture audio, file d'attente, contrôles |  |
| **Data** | Modèles unifiés (UnifiedTrack, UnifiedPlaylist…), persistance, migration |  |
| **Network** | HTTP, téléchargements, cache |  |
| **Plugin API** | API publique stable pour les plugins (voir `PLUGIN_SYSTEM.md`) |  |
| **Theme API** | Tokens de thème, Dynamic Color (voir `THEME_SYSTEM.md`) |  |
| **Update Manager** | 3 catégories de mise à jour (voir `UPDATE_SYSTEM.md`) |  |
| **UpstreamAdapter** | Isolation de SimpMusic (voir `UPSTREAM_SYSTEM.md`) |  |
| **SpaceKai Extensions** | Ensemble des plugins/thèmes développés pour la plateforme |  |

## 4. Règles fondamentales

1. Le Core ne dépend **jamais** directement des classes internes de SimpMusic — tout passe
   par `UpstreamAdapter`.
2. L'API publique (SpaceKai API) reste **stable** : les détails internes sont cachés.
3. Un plugin lent ou planté ne doit **jamais** bloquer player/UI/navigation (isolation,
   coroutines, dispatchers, lazy loading).
4. Les fonctionnalités de SpaceKai-OLD sont **réimplémentées** dans la nouvelle
   architecture, jamais copiées telles quelles (voir `MIGRATION.md`).

## 5. Questions à trancher après l'analyse

- [ ] Quels concepts de BetterDiscord sont transposables à Android/Compose, et lesquels ne le sont pas ?
- [ ] Quels modules de SimpMusic doivent être exposés par l'Adapter en premier ?
- [ ] Quelle granularité pour les plugins (une APK par plugin ? fichiers ?) ?
- [ ] Quelle plateforme desktop cibler en premier (Windows / macOS / Linux) et avec quelle techno ?

## 6. Résultats de l'analyse (à remplir)

> ⚠️ Section à compléter après lecture de SimpMusic, BetterDiscord et SpaceKai-OLD.

- Forces à conserver : _(vide)_
- Points de blocage / incompatibilités : _(vide)_
- Dépendances critiques : _(vide)_
- Schéma final retenu : _(à insérer)_
