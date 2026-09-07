# Checklist de release — v0.4.0

> Document opérationnel daté (2026-09-07). Trace la release **v0.4.0** de Sankamusic
> (`RELEASE_GUIDE.md` § 4-6). Première release publiée depuis `main` après la
> refonte Paramètres (PR #4) ; items 9/10/12 confirmés par la validation
> appareil de l'utilisateur (le CI ne peut pas les prouver).

## État vérifié (2026-09-07)

- Version : `SANKAMUSIC_VERSION=0.4.0`, `SANKAMUSIC_VERSION_CODE=3` (source unique
  `gradle.properties`). La plage 0.3.x est **sautée** : déjà publiée par la ligne
  SpaceKai/`dev` (v0.3.0 → v0.3.5, v0.3.6 draft) — règle « ne jamais réutiliser un
  numéro publié » (`RELEASE_GUIDE.md` § 3).
- `CHANGELOG.md` : section `[0.4.0] - 2026-09-07` — refonte Paramètres inspirée de
  Convx (PR #4), persistance réelle des préférences, 13 tests JVM nouveaux ; faits
  limités au CI vérifié (run 34162738533).
- Commit `release: prepare v0.4.0` (**c02dfa24**) poussé sur `main`, tag **v0.4.0**
  poussé → déclenche `release.yml`.
- CI `release.yml` au tag `v0.4.0` : **success** (run **34163767435**, 21:36:45 →
  21:39:10 UTC) — exactement 1 APK universel, signature `apksigner` OK, version APK
  == tag, checksums générés puis re-vérifiés, **draft créée**.
- Re-vérification des assets de la draft (download GitHub frais) :
  `sha256sum -c SHA256SUMS.txt` → **OK** (`Sankamusic-v0.4.0.apk`, 1 161 296 octets).
- Validation appareil (étape 3/7 — effectuée par l'utilisateur, 2026-09-07) :
  installation + démarrage sans crash, version affichée 0.4.0, fonctionnalités
  critiques OK (navigation, Paramètres, recherche, thème), mise à jour sans perte
  de données.
- Publication : draft publiée (`gh release edit v0.4.0 --draft=false --latest`) →
  **https://github.com/N7T0-OF/Sankamusic/releases/tag/v0.4.0** (2026-09-07T21:53:57Z),
  badge **Latest** pris à SpaceKai v0.3.5. Assets : exactement l'APK + `SHA256SUMS.txt`.

## Checklist § 6 (RELEASE_GUIDE.md)

| # | Vérification | État |
|---|--------------|------|
| 1 | Tests passent (étape 0) | ☑ CI vert : `:app` compilé (debug+release), tests `:core` verts dont les 13 nouveaux (run 34162738533, PR #4) |
| 2 | Version incrémentée, cohérente (code+tag+fichier) | ☑ 0.4.0 / code 3 / tag v0.4.0 / nom APK / notes (vérifiée par le CI au tag) |
| 3 | Changelog rédigé et factuel | ☑ `[0.4.0] - 2026-09-07` |
| 4 | Un seul artefact par plateforme publiée | ☑ 1 APK universel (gate CI « exactly one release APK ») |
| 5 | Aucun artefact debug/unsigned/factice | ☑ (CI rejette `-debug`/`-unsigned`/`unaligned`) |
| 6 | SHA256SUMS.txt présent et vérifié | ☑ (`-c` → OK en CI et sur download frais) |
| 7 | Tag git créé et poussé | ☑ v0.4.0 (sur commit c02dfa24) |
| 8 | Artefacts re-téléchargés depuis GitHub re-vérifiés | ☑ sha256 `-c` OK |
| 9 | Installation + démarrage (propre ET mise à jour) | ☑ confirmé par l'utilisateur (validation appareil) |
| 10 | Aucune donnée supprimée lors de la mise à jour | ☑ confirmé par l'utilisateur (réglages conservés) |
| 11 | Le CI a réellement vérifié signature, checksum, version, unicité | ☑ (run 34163767435 success) |
| 12 | Preuve de release : artefact publié installé et vérifié | ☑ APK publié installé, lancé, version 0.4.0 affichée (validation utilisateur) |
