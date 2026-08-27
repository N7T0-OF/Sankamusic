# 📦 GUIDE DE RELEASE — Sankamusic

> **Document maître des releases.** Tout agent IA (ou humain) qui publie une release
> **doit** suivre ce guide **dans l'ordre**, sans sauter d'étape.
> Une release est considérée comme **valide uniquement si la checklist finale (§ 6) est cochée à 100 %**.
> ✅ Exécution pratique (valeurs DEV, commandes, secrets, keystore) :
> **[`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md)** — à consulter en parallèle.

---

## 1. RÈGLE D'OR — Une release = UN SEUL artefact par plateforme

| Plateforme | Artefact autorisé (UN SEUL) | Extension |
|------------|------------------------------|-----------|
| Android    | 1 APK universel signé        | `.apk` |
| Windows    | 1 exécutable                 | `.exe` ou `.msix` |
| macOS      | 1 image disque               | `.dmg` |
| Linux      | 1 paquet portable            | `.AppImage` |
| Toutes     | 1 fichier de vérification    | `SHA256SUMS.txt` |

**Interdit :**
- ❌ Deux APK dans la même release (ex. : `arm64` + `x86_64`). → Un seul APK universel.
- ❌ Des artefacts « debug », « unsigned », ou de test.
- ❌ De faux fichiers (fichiers vides, placeholders, `.txt` factices) juste pour remplir une plateforme.
- ❌ Publier une release sans son `SHA256SUMS.txt`.

> Une plateforme non supportée par la version = **aucun fichier** pour cette plateforme.
> Jamais un fichier factice.

**Preuve de release — règle absolue :**
- Un build local réussi n'est **pas** une preuve de release fonctionnelle.
- Une release n'est officiellement réussie **que lorsque l'artefact publié a été téléchargé depuis GitHub puis re-vérifié** (checksum + installation).
- Le CI doit **échouer plutôt que publier un artefact incertain** : en cas de doute, échec — jamais de publication forcée.

---

## 2. CONVENTIONS DE NOMMAGE (obligatoires)

```
Sankamusic-v2.1.0.apk
Sankamusic-v2.1.0-windows-x64.exe
Sankamusic-v2.1.0-macos-universal.dmg
Sankamusic-v2.1.0-linux-x86_64.AppImage
SHA256SUMS.txt
```

Règles :
- Version **identique** dans le nom du fichier, le tag git et le titre de la release.
- Pas de `v2.1` sans patch, pas de `2.1.0` sans `v`, pas de `final`, `test`, `beta` dans le nom d'une release stable.
- Pré-releases autorisées uniquement avec suffixe : `Sankamusic-v2.2.0-rc1.apk` (et jamais comme release « latest »).

---

## 3. VERSIONING — SemVer strict

```
MAJEUR.MINEUR.PATCH
```

| Changement                                              | Exemple        |
|---------------------------------------------------------|----------------|
| Incompatibilité / refonte majeure / API cassée          | 2.0.0 → 3.0.0  |
| Nouvelle fonctionnalité compatible                      | 2.0.0 → 2.1.0  |
| Correction de bug / correctif, aucune nouvelle fonction | 2.1.0 → 2.1.1  |

Règles :
- `PATCH` = aucun changement d'API, aucune nouvelle fonctionnalité.
- La version `Sankamusic` et la version `Base SimpMusic compatible` sont **deux versions distinctes** (cf. architecture). Le numéro de release ne concerne que **Sankamusic**.
- Ne **jamais** réutiliser un numéro de version déjà publié.

---

## 4. PROCÉDURE DE RELEASE — PAS À PAS (ordre obligatoire)

> 💡 `scripts/release.sh` automatise les **étapes 0 à 4** (tests, build, unicité de l'APK,
> signature, cohérence de version, checksums) sur une machine équipée JDK 17 + Android SDK.
> Les étapes 5 à 7 (tag, publication, vérification post-pub) restent manuelles.

### Étape 0 — Préparation
- [ ] Travailler sur une branche propre, à partir de `main` à jour.
- [ ] `git pull` + vérifier qu'il n'y a pas de modifications non commitées non liées.
- [ ] Vérifier que **tous les tests passent** avant toute release (`./gradlew test` ou équivalent selon plateforme).

### Étape 1 — Version + changelog
- [ ] Incrémenter la version selon les règles SemVer (§ 3) **dans le code et les fichiers de build**.
- [ ] Rédiger le changelog de la version dans le fichier `CHANGELOG.md` (ou section release notes) avec **uniquement des faits vérifiés** :
  ```
  ## [2.1.0] - 2026-08-27
  ### Ajouté
  - ...
  ### Corrigé
  - ...
  ### Modifié
  - ...
  ```
- [ ] Commiter avec un message explicite : `release: prepare v2.1.0`.

### Étape 2 — Builds de release
- [ ] Lancer les builds **en mode release** pour **chaque plateforme** de la version.
- [ ] Ne conserver **qu'un seul artefact final par plateforme** (§ 1). Supprimer les intermédiaires (`.aab`, `-debug.apk`, builds par ABI…).
- [ ] Vérifier que chaque artefact existe, a une taille > 0 et un nom conforme (§ 2).

### Étape 3 — Validation obligatoire avant publication
- [ ] **Android** : l'APK s'installe sur un appareil/émulateur propre **et** sur une installation existante (mise à jour sans perte de données).
- [ ] **Android** : signature vérifiée (`apksigner verify --print-certs`).
- [ ] Démarrage de l'application sans crash au premier lancement.
- [ ] Test rapide des fonctionnalités critiques : lecture, navigation, mise à jour détectée.
- [ ] **Si un seul test échoue → retour à l'étape 1, ne pas publier.**

### Étape 4 — Checksums
- [ ] Générer le fichier de checksums **depuis le dossier contenant les artefacts** :
  ```bash
  sha256sum Sankamusic-* > SHA256SUMS.txt
  ```
- [ ] Vérifier que `SHA256SUMS.txt` liste **exactement** les artefacts de la release, ni plus ni moins.
- [ ] Vérification croisée :
  ```bash
  sha256sum -c SHA256SUMS.txt
  ```
  → doit afficher `OK` pour **chaque** ligne.

### Étape 5 — Tag git
- [ ] Créer le tag **après** builds + tests + checksums validés :
  ```bash
  git tag v2.1.0
  git push origin v2.1.0
  ```
- [ ] Le tag doit correspondre **exactement** au numéro de version des artefacts.

### Étape 6 — Publication GitHub Release
- [ ] Créer la release sur GitHub **uniquement avec les artefacts de l'étape 2 + `SHA256SUMS.txt`**.
- [ ] Titre : `Sankamusic v2.1.0`.
- [ ] Notes de release = changelog de l'étape 1, avec le bloc « Nouvelles fonctionnalités » visible en premier.
- [ ] Cocher « Set as the latest release » **uniquement** pour la version la plus récente et validée.
- [ ] N'attacher que les artefacts **validés par le CI** (signature, checksum, unicité) ou re-vérifiés manuellement — aucun artefact non vérifié.

### Étape 7 — Vérification post-publication (à faire **systématiquement**)
- [ ] Télécharger chaque artefact **depuis GitHub** (pas depuis le build local).
- [ ] Re-vérifier les checksums :
  ```bash
  sha256sum -c SHA256SUMS.txt
  ```
- [ ] Installer / lancer l'APK téléchargé → démarre, version affichée correcte, données conservées.
- [ ] La release GitHub ne contient aucun fichier hors liste (pas de `.aab`, `.map`, `.log`, etc.).

---

## 5. CI/CD — VÉRIFICATIONS RÉELLES OBLIGATOIRES

Une procédure écrite ne suffit pas à garantir une release : **c'est le CI qui doit
prouver** qu'elle est saine. Le CI doit **échouer** (bloquer toute publication)
si l'une des vérifications suivantes n'est pas réellement satisfaite :

| Vérification réelle exécutée par le CI | En cas d'échec |
|----------------------------------------|----------------|
| Tests (unitaires + intégration) exécutés et verts | ❌ Échec, pas de publication |
| **Exactement 1** APK de publication, universel, signé | ❌ Échec (jamais plusieurs APK) |
| Signature valide (`apksigner verify`) | ❌ Échec (pas d'APK unsigned) |
| `SHA256SUMS.txt` généré puis re-vérifié (`-c` → OK) | ❌ Échec |
| Version cohérente : code ↔ tag git ↔ nom de fichier ↔ notes | ❌ Échec |
| Package name et architectures conformes | ❌ Échec |
| Build reproductible (CI et local donnent le même résultat) | ❌ Échec |

Règles :
- **Le CI échoue plutôt que publier un artefact incertain.** Aucune exception,
  aucun « force-publish ».
- **Un build local réussi n'est pas une preuve.** La seule preuve de release est
  l'artefact publié, téléchargé depuis GitHub puis vérifié (checksum + installation).
- Un workflow qui n'effectue pas ces vérifications **ne peut pas** publier
  automatiquement.

---

## 6. CHECKLIST FINALE — À COCHER À 100 % AVANT DE DIRE « RELEASE TERMINÉE »

| # | Vérification | ☑ |
|---|--------------|---|
| 1 | Tests passent (étape 0) | ☐ |
| 2 | Version incrémentée partout, cohérente (code + tag + fichiers) | ☐ |
| 3 | Changelog rédigé et factuel | ☐ |
| 4 | Un seul artefact par plateforme publiée | ☐ |
| 5 | Aucun artefact debug/unsigned/factice | ☐ |
| 6 | SHA256SUMS.txt présent et vérifié (`-c` → OK) | ☐ |
| 7 | Tag git créé et poussé | ☐ |
| 8 | Artefacts téléchargés depuis GitHub re-vérifiés | ☐ |
| 9 | Installation + démarrage validés sur une installation propre ET une mise à jour | ☐ |
| 10 | Aucune donnée utilisateur supprimée lors de la mise à jour | ☐ |
| 11 | Le CI a réellement vérifié signature, checksum, version et unicité de l'APK (et a échoué en cas de doute) | ☐ |
| 12 | Preuve de release : l'artefact publié (téléchargé depuis GitHub) a été installé et vérifié — un build local réussi ne compte pas | ☐ |

**La release n'existe officiellement que lorsque les 12 cases sont cochées.**

---

## 7. INTERDICTIONS ABSOLUES POUR LES AGENTS IA

1. **Ne jamais** publier une release sans avoir exécuté la procédure complète dans l'ordre.
2. **Ne jamais** modifier, supprimer ou remplacer une release **déjà publiée**. → Publier une nouvelle version (`v2.1.1`).
3. **Ne jamais** mettre de fichiers non vérifiés (fichiers générés par un build interrompu, artefacts de test).
4. **Ne jamais** promettre une plateforme non réellement buildée (pas de « faux .exe »).
5. **Ne jamais** utiliser une version upstream SimpMusic non testée dans une release stable.
6. **Ne jamais** publier depuis un build local non reproductible — si possible, builder depuis CI avec les mêmes commandes documentées.
7. **Ne jamais** écrire un changelog contenant des fonctionnalités non livrées.
8. En cas de doute sur une étape → **STOP**, poser la question, ne pas deviner.
9. **Ne jamais** considérer une release comme réussie sur la base d'un build local réussi — seule la vérification de l'artefact publié (téléchargé depuis GitHub) fait foi.
10. **Ne jamais** publier malgré un échec CI ou un doute : corriger, puis republier en **nouvelle version** (jamais remplacer la release publiée).

---

## 8. RÉSUMÉ EXÉCUTABLE EN UNE LIGNE

```
CI vert (tests + signature + checksum + unicité de l'APK) → version++ → changelog
→ build release (1 artefact/plateforme) → tests d'installation → SHA256SUMS
→ tag git → GitHub Release → re-vérifier depuis GitHub (un build local ne prouve rien)
```

---

## 9. MODÈLE DE RELEASE NOTES (à copier dans GitHub)

```markdown
## Sankamusic v2.1.0

### ✨ Nouveautés
- ...

### 🔧 Corrections
- ...

### ⚙️ Compatibilité
- Base SimpMusic compatible : 1.7.2
- API plugins : v1

### 📦 Artefacts
- `Sankamusic-v2.1.0.apk`
- `Sankamusic-v2.1.0-windows-x64.exe`
- `Sankamusic-v2.1.0-macos-universal.dmg`
- `Sankamusic-v2.1.0-linux-x86_64.AppImage`
- `SHA256SUMS.txt` (vérification : `sha256sum -c SHA256SUMS.txt`)
```
