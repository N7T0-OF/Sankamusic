# Checklist de release — v0.1.0

> Document opérationnel daté (2026-08-27). Il **résume** les actions d'exécution
> (réf. `RELEASE_GUIDE.md`, `docs/REPO_SETUP.md`, `docs/BUILD_SYSTEM.md`) et fournit
> les **valeurs DEV** à utiliser pour la première exécution. Ne remplace pas ces
> références : il les pointe.

## Rappel de l'état vérifié (2026-08-27)

- Repo `N7T0-OF/Sankamusic` : **public**, `main` poussée (`eb24e63`), **0 release**.
- CI : run de la tête `eb24e63` = **failure** au step `Run tests` (détail ci-dessous).
- `:core`/plugins/thèmes/update : **73 tests JVM verts** (kotlinc + JRE, hors Gradle).
- Correctif de config Gradle (`app/build.gradle.kts`) : `./gradlew help` → **BUILD SUCCESSFUL**,
  le script compile désormais (n'avait plus les 6 erreurs de compilation).

### Cause du CI `Run tests` (non confirmée — 2026-08-27)

Les logs bruts du workflow Linux sont **403 sans token** (admin requise).
L'API jobs nomme le step fautif (`Run tests`) mais pas l'erreur. La reproduction
locale est **masquée** par un artefact Windows (`SdkLocator.validateSdkPath`
rejette le chemin du SDK local — `local.properties` est gitignoré, donc sans lien
avec le CI). Un `./gradlew test` réussi sur une machine Linux/macOS (chemin sans
espace, SDK installé) est nécessaire pour **nommer** la vraie cause avant tout tag.

**Ne pas tagger ni publier tant que `Run tests` n'est pas vert** (règle `RELEASE_GUIDE.md`).

## Prérequis avant tout tag

1. **CI vert** : lever l'échec `Run tests` (voir ci-dessus).
2. **4 secrets GitHub** configurés dans **Settings → Secrets and variables → Actions** :
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
3. **Keystore** DEV généré (ci-dessous) OU keystore de release réel pour la publication.

## Keystore DEV généré (⚠️ à remplacer pour une vraie publication)

Généré via `scripts/make-dev-keystore.sh`, validé (re-décodage + `keytool -list`).
**Dev uniquement** — à remplacer par un vrai keystore de release avant publication.

> 🚨 **N'affiche pas ici tes vrais secrets** : ce doc est versionné sur GitHub.
> Le base64 (`ANDROID_KEYSTORE_BASE64`), l'alias et les mots de passe DEV ont été
> générés et affichés dans le terminal lors de l'exécution du helper — ils restent
> **localement seuls**. Leur emplacement local (jamais commité) est listé ci-dessous.

Fichiers keystore locaux (jamais commités, `.gitignore` couvre `*.jks`/`*.keystore`) :
- keystore : `%TEMP%\sankamusic-verify\keystore-v010\dev-keystore.jks`
- base64 : `%TEMP%\sankamusic-verify\keystore-v010\dev-keystore.b64`

**Pour retrouver tes valeurs DEV** (sur la machine qui a exécuté `make-dev-keystore.sh`) :
```bash
cat "%TEMP%\sankamusic-verify\keystore-v010\dev-keystore.b64"          # → ANDROID_KEYSTORE_BASE64
keytool -list -keystore "%TEMP%\sankamusic-verify\keystore-v010\dev-keystore.jks" -storepass "<storepass affiché lors de la génération>"
# L'alias et les mots de passe ont été imprimés UNE SEULE FOIS dans la sortie du helper
# (bloc « Exporte dans TA session »). Le mot de passe de la clé = celui du keystore par défaut.
```

## Étapes d'exécution (poste avec Android Studio / JDK 17 / SDK)

1. **Tests** : `./gradlew test` → vert sur toutes les machines (voir prérequis CI).
2. **APK signé** (remplacer les 4 valeurs ci-dessous par **tes** secrets DEV locaux) :
   ```bash
   export ANDROID_KEYSTORE_BASE64="$(cat "%TEMP%\sankamusic-verify\keystore-v010\dev-keystore.b64")"
   export ANDROID_KEYSTORE_PASSWORD='LE_STOREPASS_LOCAL'
   export ANDROID_KEY_ALIAS='LE_ALIAS_LOCAL'
   export ANDROID_KEY_PASSWORD='LE_KEYPASS_LOCAL'  # = storepass par défaut
   scripts/release.sh          # tests → assembleRelease signé → 1 seul APK → apksigner verify → version == tag → SHA256SUMS
   ```
   (Automatise les étapes 0–4 de `RELEASE_GUIDE.md` ; n'exécute ni tag ni push ni publication.)
3. **Test d'installation** : installer l'APK (propre + mise à jour), vérifier.
4. **Git** :
   ```bash
   git push -u origin main
   git tag v0.1.0 && git push origin v0.1.0
   ```
   → déclenche `release.yml` → vérifie 1 APK signé / signature / version / checksums → crée la release **draft**.
5. **Publication** : télécharger la release depuis GitHub, `sha256sum -c SHA256SUMS.txt`,
   installer, puis **publier** la draft (étape 7 de `RELEASE_GUIDE.md`).

> Une release GitHub n'est **réussie** que si l'artefact publié a été téléchargé depuis
> GitHub puis vérifié — un build local réussi ne prouve pas une release fonctionnelle.
> Le CI échoue plutôt que de publier un artefact incertain.