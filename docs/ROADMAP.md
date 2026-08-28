# Roadmap — Sankamusic

- **Statut** : 🟡 Squelette — phases validées une à une
- **Document lié** : `docs/ARCHITECTURE.md`, `docs/MIGRATION.md`, `RELEASE_GUIDE.md`

## Règle de progression

> On ne passe à la phase suivante **que** si la phase courante est validée
> (critères de sortie remplis). Stabilité > vitesse.

## Phase 1 — Audit (en cours)

Analyse des 4 dossiers : SimpMusic (upstream), BetterDiscord (concept),
SpaceKai-OLD (fonctionnalités), et l'état actuel de Sankamusic.

**Livrables** : les sections « à compléter » des documents `docs/` (architecture,
dépendances, compatibilité, plugins, thèmes, update, build, sécurité, migration).

**Critère de sortie** : chaque document contient ses résultats d'analyse, les risques
et les incompatibilités identifiés. **Aucun code applicatif écrit.**

## Phase 2 — Prototype minimal

Construire uniquement :

- Sankamusic Core (squelette)
- Plugin API
- Theme API
- UpstreamAdapter (minimal)

**Critère de sortie** : le prototype compile, démarre et charge un plugin/theme minimal.

> **Phase 2 — build contre la base 2.0.0** : voir le guide de montage
> `docs/PHASE2_BUILD.md` (toolchain, 3 options d'intégration, câblage des
> sous-adaptateurs, extension des plages, checklist). Le Core reste inchangé.

## Phase 3 — Validation du framework

Créer et tester :

- plugin d'exemple `HelloSpaceKai` (section Home + bouton + entrée Settings)
- thème d'exemple `ExampleTheme`

Tester : installation, activation, désactivation, mise à jour, compatibilité.

**Critère de sortie** : le cycle de vie complet d'un plugin et d'un thème fonctionne,
un crash de plugin n'affecte pas l'app.

## Phase 4 — Migration des fonctionnalités

Migrer progressivement les fonctionnalités de SpaceKai-OLD (voir `MIGRATION.md`) :
navigation → thèmes → orientation → player → vibration → Dynamic Color → paramètres →
updater → Spotify → Apple Music → Deezer → widgets.

**Critère de sortie** : chaque migration testée individuellement ; données utilisateur
préservées.

## Phase 5 — Tests, CI et release

- Tests : installation propre, mise à jour, désactivation/suppression plugin, changement
  de thème, rotation, player, navigation, migration de base de données.
- CI : vérifications réelles obligatoires (voir `BUILD_SYSTEM.md` § 5).
- Release : procédure `RELEASE_GUIDE.md` (1 artefact/plateforme, SHA256SUMS, re-vérification
  depuis GitHub).

**Critère de sortie** : checklist finale du `RELEASE_GUIDE.md` cochée à 100 %.
