# Mise en place du repo GitHub — N7T0-OF/Sankamusic

- **Constat vérifié le 2026-08-27** (API GitHub, sans token) : le repo existe et est
  **public** mais **vide** — 0 Ko, pas de README, pas de description, pas de topics,
  pas de licence, **pas de release v0.1.0**.
- **Objectif** : remplir le repo proprement (conformément à `RELEASE_GUIDE.md`) pour
  que l'updater en-app sorte de l'état `ERROR`.

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
