# Décisions d'Architecture (ADR)

Ce fichier enregistre les décisions d'architecture importantes de Sankamusic.
**Toute décision structurante doit y être ajoutée avant d'être implémentée.**

Format d'une entrée :

```markdown
## ADR-NNN — Titre de la décision
- **Statut** : Proposée | Acceptée | Dépréciée
- **Date** : AAAA-MM-JJ
- **Contexte** : pourquoi cette décision est nécessaire.
- **Décision** : ce qui a été décidé.
- **Conséquences** : ce que ça coûte / ce que ça permet.
- **Alternatives envisagées** : options écartées et pourquoi.
```

---

## ADR-001 — Sankamusic est un projet indépendant, pas un fork de SimpMusic
- **Statut** : Acceptée
- **Date** : 2026-08-27
- **Contexte** : l'ancien projet (SpaceKai-OLD) était un fork de SimpMusic devenu
  incohérent après accumulation de correctifs. Maintenir un fork classique rend les
  mises à jour upstream impossibles à gérer.
- **Décision** : Sankamusic est construit à partir de zéro comme plateforme indépendante.
  SimpMusic est traité comme un **upstream / compatibility target**, jamais comme le
  repository recopié. BetterDiscord n'est qu'une référence conceptuelle, pas une source de code.
- **Conséquences** : coût initial plus élevé, mais mises à jour upstream isolées dans une
  couche d'adaptation. Les 3 projets de référence sont en lecture seule.
- **Alternatives** : fork + merge régulier de l'upstream (rejeté : fragile, non maintenable).

## ADR-002 — Une release = un seul artefact par plateforme
- **Statut** : Acceptée
- **Date** : 2026-08-27
- **Contexte** : les releases précédentes contenaient des artefacts multiples,
  debug ou non signés → « Application non installée ».
- **Décision** : chaque release publie exactement **1 APK universel signé** (Android),
  et au plus 1 artefact par plateforme desktop (`.exe`/`.msix`, `.dmg`, `.AppImage`),
  plus `SHA256SUMS.txt`. Aucun fichier factice pour une plateforme non supportée.
  Voir `RELEASE_GUIDE.md`.
- **Conséquences** : liste d'artefacts courte et prévisible ; le CI échoue si plusieurs
  APK de publication sont détectés.

## ADR-003 — La preuve de release = artefact publié re-vérifié depuis GitHub
- **Statut** : Acceptée
- **Date** : 2026-08-27
- **Contexte** : un build local réussi ne prouve rien sur la release publiée.
- **Décision** : une release n'est officiellement réussie que lorsque l'artefact publié a
  été téléchargé depuis GitHub puis vérifié (checksum + installation). Le CI doit
  **échouer plutôt que publier un artefact incertain**. Aucun « force-publish ».
- **Conséquences** : toute publication passe par des vérifications réelles ; un doute
  bloque la release, jamais l'inverse.

## ADR-004 — Séparation des trois niveaux de mise à jour
- **Statut** : Acceptée
- **Date** : 2026-08-27
- **Contexte** : il faut distinguer les mises à jour du Core, des plugins et de la base
  upstream pour ne jamais remplacer un composant critique par une version non testée.
- **Décision** : le système de mise à jour gère trois catégories distinctes :
  1. Sankamusic Core (release GitHub du projet) ;
  2. Plugins / thèmes (indépendants du Core quand l'architecture le permet) ;
  3. Compatibilité upstream SimpMusic (jamais installée automatiquement sans validation).
  API cible : `UpdateManager.checkSankamusicUpdate()`, `checkPluginUpdates()`,
  `checkUpstreamCompatibility()`. Voir `docs/UPDATE_SYSTEM.md`.
- **Conséquences** : une nouvelle version de SimpMusic ne peut pas casser l'installation
  sans passer par le processus de validation + release.

---

## Décisions à venir (à trancher après l'analyse des 4 dossiers)

- Choix des langages/plateformes desktop (Kotlin Multiplatform vs Electron vs autre).
- Mécanisme d'isolation des plugins (limites réelles Android).
- Source unique de vérité de la version (Gradle ↔ code ↔ tag ↔ release).
- Format de distribution des plugins (fichier `.spk` ? dépôt distant ?).
