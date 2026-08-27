# Système de build — Sankamusic

- **Statut** : 🟡 Squelette — à compléter
- **Document lié** : `RELEASE_GUIDE.md` (document maître, **fait foi**)

## 1. Objectif

Produire des builds **reproductibles** et des releases **vérifiées réellement** par le CI.
Une procédure écrite ne suffit pas : le CI doit prouver (voir `RELEASE_GUIDE.md` § 5).

## 2. Règle des artefacts

| Plateforme | Artefact final (UN SEUL) |
|------------|---------------------------|
| Android | 1 APK universel signé (jamais `-debug`, `-unsigned`, ni un APK par ABI) |
| Windows | 1 `.exe` ou `.msix` |
| macOS | 1 `.dmg` |
| Linux | 1 `.AppImage` |
| Toutes | `SHA256SUMS.txt` (re-vérifié avec `sha256sum -c`) |

Le CI **échoue** si plusieurs APK de publication sont détectés.

## 3. Source unique de vérité de la version

La version ne doit **pas** être codée en dur à plusieurs endroits. Objectif : une seule
définition (fichier de version / convention Gradle) propagée vers :

- code applicatif (affichage « À propos »)
- nom de l'artefact (`Sankamusic-vX.Y.Z.apk`)
- tag git (`vX.Y.Z`)
- titre et notes de la release GitHub

> Mécanisme exact (Gradle version catalog, génération de `BuildConfig`, workflow) :
> **à concevoir et documenter ici après choix de l'outillage.**

## 4. Builds par plateforme

- **Android** : Gradle, mode `release`, signature avec keystore (jamais commité),
  `minify`/R8 configuré, APK universel.
- **Desktop** : techno à déterminer (voir `ARCHITECTURE.md` § 5) ; les mêmes règles
  d'artefact s'appliquent.

## 5. CI/CD — vérifications réelles obligatoires

Le CI doit exécuter **et faire échouer la build** si :

- [ ] tests (unitaires + intégration) non verts ;
- [ ] plus d'un APK de publication détecté ;
- [ ] signature invalide (`apksigner verify`) ;
- [ ] `SHA256SUMS.txt` absent ou `sha256sum -c` non OK ;
- [ ] version incohérente (code ↔ tag ↔ nom de fichier ↔ notes) ;
- [ ] package name / architectures non conformes ;
- [ ] build non reproductible.

Publication : uniquement après validation ; jamais de « force-publish ».

## 6. Commandes de référence (à compléter)

```bash
# Android — build release
./gradlew assembleRelease

# Vérification de la signature
apksigner verify --print-certs app/build/outputs/apk/release/Sankamusic-vX.Y.Z.apk

# Checksums
sha256sum Sankamusic-* > SHA256SUMS.txt
sha256sum -c SHA256SUMS.txt
```

## 6bis. Signature en CI

Le keystore n'est **jamais** commité. Il est injecté au build via les secrets GitHub
(`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`) — voir `.github/workflows/release.yml`. Le `signingConfig`
Gradle doit lire ces variables d'environnement (détail exact à écrire à la création
du projet Android, Phase 2).

## 7. Workflows GitHub Actions (créés)

- `.github/workflows/ci.yml` — tests + `assembleRelease` + unicité de l'APK, sur chaque push/PR (ne publie rien).
- `.github/workflows/release.yml` — sur tag `v*` : build signé, **1 seul APK**, `apksigner verify`,
  version APK == tag git, `SHA256SUMS.txt` généré puis re-vérifié, publication en **draft**
  (publique uniquement après vérification post-publication, `RELEASE_GUIDE.md` étape 7).

> ⚠️ Ils ne deviennent effectifs que lorsque le projet Gradle Android existe (Phase 2 du
> ROADMAP). D'ici là, tout push les fera échouer — c'est le comportement voulu
> (« le CI échoue plutôt que publier »).

## 8. Squelette Gradle (créé — NON COMPILÉ)

Le squelette de build existe (Phase 2, préparation) :

- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` (version catalog),
  `gradle.properties` — **source unique de vérité de la version** : `SANKAMUSIC_VERSION`
  et `SANKAMUSIC_VERSION_CODE` y sont définis, propagés vers `versionName`, le nom d'APK
  (`Sankamusic-v<version>.apk`), `BuildConfig.SANKAMUSIC_VERSION` et le tag git.
- Wrapper Gradle 8.9 (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`).
- `app/build.gradle.kts` : `signingConfig` CI branché sur les secrets GitHub
  (`ANDROID_KEYSTORE_BASE64`…). **Sans secrets → pas de signature → APK `-unsigned` → CI échoue.**
- Module `app` minimal (manifest, ressources, `MainActivity` Compose) — **squelette de build
  uniquement**, pas l'architecture cible.

> ⚠️ **Aucune compilation effectuée** : l'environnement de travail ne dispose ni de JDK,
> ni de Gradle, ni de l'Android SDK. La première action de validation (sur une machine
> équipée) est : `./gradlew test` puis `./gradlew assembleRelease`.

## 9. À compléter

- [ ] Compilation de validation du squelette (JDK 17 + Android SDK requis)
- [ ] Détail Gradle des modules futurs : `core`, `api`, `adapter`, `plugins`…
- [ ] Commandes exactes par plateforme desktop
