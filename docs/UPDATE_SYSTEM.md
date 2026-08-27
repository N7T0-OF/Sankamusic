# Système de mise à jour — Sankamusic

- **Statut** : 🟡 Squelette — à compléter
- **Document lié** : `RELEASE_GUIDE.md`, `docs/UPSTREAM_SYSTEM.md`, `docs/PLUGIN_SYSTEM.md`

## 1. Objectif

L'utilisateur ne doit **jamais** gérer GitHub manuellement. L'application vérifie,
télécharge, vérifie l'intégrité et installe les mises à jour — **sans jamais supprimer
les données utilisateur**.

## 2. Trois catégories distinctes (ADR-004)

| Catégorie | Source | Comportement |
|-----------|--------|--------------|
| **Sankamusic Core** | Releases GitHub `N7T0-OF/Sankamusic` | Vérification + installation APK (consentement utilisateur) |
| **Plugins / thèmes** | Dépôts de plugins (à définir) | Mise à jour indépendante du Core quand possible |
| **Compatibilité upstream** | SimpMusic (upstream) | Jamais installée automatiquement sans validation → nouvelle release Sankamusic |

## 3. API cible — `UpdateManager`

```kotlin
interface UpdateManager {
    suspend fun checkSankamusicUpdate(): UpdateStatus
    suspend fun checkPluginUpdates(): List<PluginUpdate>
    suspend fun checkUpstreamCompatibility(): UpstreamStatus
}
```

- `UpdateStatus` : version installée, version disponible, changelog, taille, date,
  SHA-256, état (à jour / disponible / non compatible).
- Les vérifications sont **non bloquantes** (coroutines) et jamais déclenchées au
  démarrage sans consentement réseau raisonnable.

## 4. Interface utilisateur

```
Paramètres → À propos → Mises à jour
```

Affichage :
- Sankamusic : version actuelle, dernière version, statut (✓ à jour / mise à jour dispo).
- Base SimpMusic compatible : version actuelle, statut compatibilité.
- Plugins : liste des mises à jour disponibles (`☑ Core`, `☑ Spotify`, …) + [ Tout mettre à jour ].

## 5. Flux de mise à jour Android

```
Détection d'une mise à jour
  ↓
Téléchargement automatique (avec progression)
  ↓
Vérification SHA-256 (échec → abandon, aucune installation)
  ↓
« Redémarrer pour installer » (consentement utilisateur)
  ↓
Installateur Android
  ↓
Nouvelle version — données utilisateur intactes
```

Règles :
- 🔒 Vérifier l'intégrité **avant** toute installation.
- 🚫 **Jamais** supprimer les données utilisateur lors d'une mise à jour (testé à chaque release).
- 🚫 Pas de mise à jour silencieuse de composants critiques.
- 🚫 Pas de downgrade vers une version inférieure.

## 6. Détection de version

La version détectée dans l'app doit correspondre à **une source unique de vérité**
(voir `BUILD_SYSTEM.md`) : code ↔ build ↔ tag git ↔ nom d'artefact ↔ release notes.

## 7. Desktop (Windows / macOS / Linux)

Le même UpdateManager doit couvrir les plateformes desktop en respectant
`RELEASE_GUIDE.md` (un artefact par plateforme). Mécanismes spécifiques
(auto-updater desktop) : **à documenter après choix de la techno desktop**.

## 8. À compléter

- [x] Format exact de l'API GitHub Releases utilisée (JSON : tag, assets, sha256…) —
      **vérifié contre l'API réelle le 2026-08-27** (parser OK sur `maxrave-dev/SimpMusic`
      et `N7T0-OF/Sankamusic` ; voir docs/UPSTREAM_SYSTEM.md § 8)
- [ ] Gestion des pré-releases (`rc`, `beta`) : jamais proposées comme stable
- [ ] Politique réseau (Wi-Fi uniquement ? taille max ?)
- [ ] Dépôt des plugins : structure et signatures
