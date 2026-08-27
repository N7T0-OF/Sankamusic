# Checklist de release — v0.1.0

> Document opérationnel daté (2026-08-27). Il **résume** les actions d'exécution
> (réf. `RELEASE_GUIDE.md`, `docs/REPO_SETUP.md`, `docs/BUILD_SYSTEM.md`) et fournit
> les **valeurs DEV** à utiliser pour la première exécution. Ne remplace pas ces
> références : il les pointe.

## Rappel de l'état vérifié (2026-08-27)

- Repo `N7T0-OF/Sankamusic` : **public**, `main` poussée (`b1b693f`), **0 release**.
- **CI : VERT** — run de la tête `b1b693f` = `completed / success` (`Run tests`, `Build release
  APK`, `Enforce exactly one release APK` tous verts). Le verrou CI est levé.
- `:core`/plugins/thèmes/update + `:app` : **`./gradlew test` complet = 147 tâches, BUILD SUCCESSFUL**
  (vérifié localement via `ANDROID_HOME`), et le CI exécute le même flux en vert.

### Cause du CI `Run tests` (nommée et résolue — 2026-08-27)

Le step `Run tests` échouait en **0s** car `gradlew` et `scripts/*.sh` étaient committés
en mode **`100644`** (sans bit exécutable) → sur le runner Linux, `./gradlew test` mourait
immédiatement en `Permission denied`. Le poste Windows masquait ce symptôme (`chmod +x` sur
disque, vérification de mode différente). **Correctif** : les 5 fichiers sont passés à
`100755` (commit `b1b693f`). Un correctif orthogonal (`29fd0b3`) expose le SDK via
`ANDROID_HOME`/`ANDROID_SDK_ROOT` au lieu d'écrire `local.properties` — la voie que AGP
rejetait localement (`SdkLocator.validateSdkPath`).

**Le CI est désormais vert** : la règle « pas de tag avec CI rouge » de `RELEASE_GUIDE.md`
n'est plus un frein.

## Prérequis avant tout tag

1. ~~CI vert~~ — ✅ **acquis** (run `b1b693f` = success).
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

> ✅ **Déjà fait le 2026-08-28 (preuve locale)** : l'APK signé `Sankamusic-v0.1.0.apk`
> a été construit et vérifié (signature `apksigner` rc=0, version `0.1.0` cohérente,
> `SHA256SUMS.txt` re-vérifié). Artefact hors repo : `%TEMP%\sankamusic-verify\apk-verify\`.
> Les étapes ci-dessous restent valables pour re-produire et surtout pour le CI/tag.

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