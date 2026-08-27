# Changelog

Toutes les modifications notables du projet Sankamusic sont documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et ce projet
adhère au [Semantic Versioning](https://semver.org/lang/fr/) (voir `RELEASE_GUIDE.md` § 3).

> **Règle :** une entrée n'est ajoutée que pour une **version réellement publiée**.
> Les entrées en cours de préparation sont marquées `[Unreleased]`.

## [0.1.0] - 2026-08-27

### Ajouté
- Fondation : guide de release (`RELEASE_GUIDE.md` — un artefact/plateforme, validation CI réelle,
  checklist 100 %), décisions d'architecture (ADR-001 à 004), documentation (`docs/`),
  `README.md`, `CHANGELOG.md`.
- Squelette Gradle Android : source unique de vérité de la version (`SANKAMUSIC_VERSION`),
  signature CI branchée sur les secrets GitHub, wrapper Gradle 8.9, module `:app` minimal Compose.
- Workflows GitHub Actions : `ci.yml` (validation) et `release.yml` (build signé, vérifications
  réelles — unicité, signature, version, checksums — publication en draft).
- Module `:core` : SpaceKai API (plugins, thèmes, update, upstream, modèles unifiés),
  `PluginEngine` (isolation des crashs), `ThemeEngine`, `UiExtensionRegistry`, moteur de mises
  à jour (`UpdateEngine`, `SemVer`, client GitHub Releases, vérification SHA-256 avant installation).
- Plugin d'exemple `HelloSpaceKai` et thème d'exemple `ExampleTheme` (Phase 3 du ROADMAP).
- Câblage applicatif : `SankamusicApp`, `DefaultSpaceKaiApi` (squelettes — **non compilés**,
  SDK Android requis).
- `scripts/release.sh` : automatisation locale de la checklist `RELEASE_GUIDE.md`.

### Notes de vérification
- 58 tests JUnit OK (compilés et exécutés avec kotlinc 2.0.20 + JRE 17, hors Gradle) :
  core, plugins, thèmes, moteur de mises à jour.
- Parser GitHub Releases et `UpdateEngine` vérifiés contre l'API réelle le 2026-08-27
  (faits documentés dans `docs/UPSTREAM_SYSTEM.md` § 8).
- Module `:app` (MainActivity, SankamusicApp, Compose) : **non compilé** — nécessite un
  Android SDK (machine équipée, ou CI après push).
- APK `Sankamusic-v0.1.0.apk` **produit et vérifié localement** le 2026-08-28 (clé DEV) :
  `assembleRelease` BUILD SUCCESSFUL (154 tâches, R8 inclus), `apksigner verify` rc=0
  (cert `CN=Sankamusic Dev`), version `0.1.0` / code `1` cohérente avec `gradle.properties`,
  `SHA256SUMS.txt` généré et re-vérifié (`sha256sum -c` → OK). Artefact hors repo
  (`%TEMP%\sankamusic-verify\apk-verify\`). À re-vérifier depuis GitHub après publication
  (étape 7 du guide) ; le `release.yml` re-vérifiera tout au tag `v0.1.0`.

### Corrigé
-

### Modifié
-

---

## [Unreleased]

### Ajouté
- Câblage updater en-app : `HttpNetworkApi` (java.net + coroutines, prouvé contre
  l'API réelle), `UpdateEngine` instancié dans `SankamusicApp` (repos réels
  `N7T0-OF/Sankamusic` et `maxrave-dev/SimpMusic`), écran Compose « Mises à jour »
  (3 catégories, état ERROR géré sans crash) — **UI Compose non compilée (SDK requis)**.
- Repo GitHub : `scripts/setup-remote.sh` + `docs/REPO_SETUP.md` (remote configuré,
  procédure identité/push/description/topics/licence/secrets/release v0.1.0 ; constat
  vérifié : repo public vide, SimpMusic GPL-3.0, BetterDiscord Apache-2.0).
- Signature : `scripts/make-dev-keystore.sh` — keystore DEV éphémère (keytool), export des
  4 secrets release.yml, cohérence vérifiée (re-décodage + keytool -list) ; jamais commité.

### Corrigé
- **CI rendu vert (verrou v0.1.0 levé)** — cause du 0s `Run tests` nommée : `gradlew` et
  `scripts/*.sh` committés en `100644` (sans bit exécutable) → `Permission denied` sur le
  runner Linux. Passés à `100755` (commit `b1b693f`).
- `app/build.gradle.kts` : correction des 6 erreurs de compilation du script (signature dans
  `signingConfigs {}`, renommage de l'APK via `BaseVariantOutputImpl.outputFileName`),
  `./gradlew help` → BUILD SUCCESSFUL (commit `eb24e63`).
- Workflows : SDK exposé via `ANDROID_HOME`/`ANDROID_SDK_ROOT` (GITHUB_ENV) au lieu d'écrire
  `local.properties` que AGP rejette localement (`SdkLocator.validateSdkPath`) — `29fd0b3`.

### Modifié
- Docs : `RELEASE_CHECKLIST.md` et `BUILD_SYSTEM.md` reflètent l'état CI vert (2026-08-27).

---

## Modèle pour une nouvelle version

```markdown
## [2.1.0] - 2026-08-27

### Ajouté
- ...

### Corrigé
- ...

### Modifié
- ...
```
