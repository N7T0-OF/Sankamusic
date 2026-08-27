# Mise en place du repo GitHub — N7T0-OF/Sankamusic

- **Historique** : le repo existait (vérifié le 2026-08-27 via API, sans token) mais était
  **vide** ; la branche `main` a depuis été poussée (README, docs, `:core`, CI).
- **État au 2026-08-27** : `main` poussée, README/LICENSE affichés ; **pas encore de
  release v0.1.0** (l'updater reste en `ERROR` tant qu'aucune release n'existe).
- **Objectif** : remplir le repo proprement (conformément à `RELEASE_GUIDE.md`) pour
  que l'updater en-app sorte de l'état `ERROR`.

## B. Suivi du push et du CI (2026-08-27)

| Point | Détail |
|-------|--------|
| Push de `main` | **Réussi** — 3 commits (`4df8a82`, `68c8dac`, `e63ba58`), README et LICENSE
  visibles sur GitHub. |
| Workflow `CI` | Run #1 → **failure** au step `./gradlew test`. |
| Garde-fou | Le CI a correctement sauté `Build release APK` et `Enforce exactly one
  release APK` (règle `RELEASE_GUIDE.md` : on ne publie pas d'artefact non vérifié). |

**Cause probable** : les modules sont des bibliothèques/UI Android (`:core`,
`:plugins`, `:themes`, `:app` Compose) → `./gradlew` nécessite **l'Android SDK**,
qui n'est pas pré-installé sur un runner `ubuntu-latest` libre. Les tests JUnit sont
verts **hors Gradle** (kotlinc + JRE temporaires, ~73 tests), mais le build complet
n'a pas encore tourné sur machine équipée. Le log brut GitHub est protégé par droits
admin (levée 403 sans token) — diagnostic confirmé par la séquence des steps, pas
par le texte du log.

**Issue recommandée** : sur une machine avec Android Studio, lancer
`./gradlew :core:test :plugins:hellospacekai:test :themes:exampletheme:test
assembleRelease`, corriger ce qui échoue, re-pousser → CI vert → tag+pousser
`v0.1.0` → `release.yml` vérifie et crée une release **DRAFT**.

---

## Étape 0 — Identité git (une seule fois) + réparation de l'historique

```bash
git config user.name  "TonNom"
git config user.email "ton-email@github.com"
```

> Les commits de cet historique ont été créés avec une identité locale temporaire
> (aucune identité n'était configurée). **Avant tout push**, une fois ton `user.name`
> / `user.email` configuré ci-dessus, répare **tout l'historique d'un coup** avec :
> ```bash
> git rebase --root --exec 'git commit --amend --reset-author --no-edit'
> ```
> Cette commande rejoue chaque commit avec ta nouvelle identité, sans modifier le
> message. Vérifie ensuite avec `git log` que tous les auteurs sont bons, puis pousse.

## Étape 1 — Remote + push (le README s'affichera sur GitHub)

```bash
git remote add origin https://github.com/N7T0-OF/Sankamusic.git
git push -u origin main
```

## Étape 2 — Description + topics (projet « activé »)

Avec la CLI GitHub (`gh`) :

```bash
gh repo edit N7T0-OF/Sankamusic \
  --description "Plateforme musicale indépendante (plugins, thèmes, extensions) — base compatible SimpMusic, inspirée de BetterDiscord. Android + desktop." \
  --add-topic android --add-topic kotlin --add-topic jetpack-compose \
  --add-topic music-player --add-topic plugins --add-topic themes
```

Sans `gh`, via l'API (token en variable) :

```bash
curl -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" \
  https://api.github.com/repos/N7T0-OF/Sankamusic \
  -d '{"description":"...","topics":["android","kotlin","music-player"]}'
```

## Étape 3 — Licence

- **Upstream SimpMusic = GPL-3.0** (vérifié le 2026-08-27) ; BetterDiscord = Apache-2.0.
- Si Sankamusic réutilise du code SimpMusic (adapter, base), **GPL-3.0 est la licence
  cohérente** (Apache-2.0 est compatible en inclusion dans un projet GPL-3.0).
- Ajouter le fichier `LICENSE` à la racine, puis :

```bash
git add LICENSE && git commit -m "docs: add LICENSE" && git push
```

## Étape 4 — Keystore de signature + secrets CI

### Réponse rapide — keystore de DEV éphémère (débloquer le CI)

Pour tester le workflow de release sans attendre le vrai keystore, le helper
`scripts/make-dev-keystore.sh` génère un keystore **DEV** et exporte les 4
secrets :

```bash
# clef / alias / mots de passe générés aléatoirement, affichés une seule fois
scripts/make-dev-keystore.sh
# → colle la sortie (`export ...`) dans ta session, copie les 4 valeurs
#   dans les secrets GitHub (Actions), puis tag / push
```

Le helper VÉRIFIE la cohérence : il re-décode le base64 et valide avec
`keytool -list` (et le script imprime « ✅ cohérent » uniquement si c'est bon).
Il écrit le `.jks` et le `.b64` dans `OUT` (défaut `.tmp-signing/`, jamais
commité), et n'accepte un `keytool` fourni que s'il le trouve dans le PATH ou
via `JAVA_HOME/bin`.

> 🚨 **DEV UNIQUEMENT.** Il sert à débloquer le CI et à produire un APK
> installable. **Remplace-le par un vrai keystore de release, gardé privé,
> AVANT toute publication réelle.**

### Vrai keystore de release (avant publication)

```bash
keytool -genkeypair -v -keystore release.keystore -alias sankamusic \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore   # Windows : certutil -encode ou openssl base64
```

> 🔒 `release.keystore` ne doit **jamais** être commité (`.gitignore` le protège :
> `*.jks`, `*.keystore`).

Secrets GitHub (**Settings → Secrets and variables → Actions**) :

| Secret | Valeur |
|--------|--------|
| `ANDROID_KEYSTORE_BASE64` | contenu base64 du keystore |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ANDROID_KEY_ALIAS` | alias (`sankamusic`) |
| `ANDROID_KEY_PASSWORD` | mot de passe de la clé |

Sans ces secrets, `release.yml` produit un APK non signé → le CI échoue (voulu).

## Étape 5 — Release v0.1.0 (l'updater sort de l'état ERROR)

Sur une machine avec JDK 17 + Android SDK (ex. ton poste avec Android Studio) :

```bash
export ANDROID_KEYSTORE_FILE=release.keystore
export ANDROID_KEYSTORE_PASSWORD=... ANDROID_KEY_ALIAS=sankamusic ANDROID_KEY_PASSWORD=...
scripts/release.sh          # tests + APK signé unique + signature + version + checksums
```

Valide l'installation (propre + mise à jour), puis :

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Le workflow `release.yml` re-vérifie tout et crée la release **DRAFT** sur GitHub.
Vérification post-publication (`RELEASE_GUIDE.md` étape 7) : télécharger l'APK depuis
GitHub, `sha256sum -c SHA256SUMS.txt`, installer → puis publier la release.

## Récapitulatif des commandes

```bash
git config user.name "TonNom" && git config user.email "ton-email@github.com"
git rebase --root --exec 'git commit --amend --reset-author --no-edit'   # répare tout l'historique
git remote add origin https://github.com/N7T0-OF/Sankamusic.git
git push -u origin main
gh repo edit N7T0-OF/Sankamusic --description "..." --add-topic android ...
# + keystore, secrets, puis :
git tag v0.1.0 && git push origin v0.1.0
```
