# Checklist de release — v0.2.0

> Document opérationnel daté (2026-08-29). Trace la release **v0.2.0** de Sankamusic
> (`RELEASE_GUIDE.md` § 4-6). Les items **manuels** (installation réelle) restent à
> cocher par un humain après publication — le CI ne peut pas les prouver.

## État vérifié (2026-08-29)

- Version : `SANKAMUSIC_VERSION=0.2.0`, `SANKAMUSIC_VERSION_CODE=2` (source unique `gradle.properties`).
- `CHANGELOG.md` : bloc `[Unreleased]` (prépa Phase 2) finalisé en `[0.2.0] - 2026-08-29`.
- Commit `release: prepare v0.2.0` (**bbdd128**) + tag **v0.2.0** → **1cde505**, `main` poussée.
- CI `release.yml` au tag `v0.2.0` : **success** — 1 seul APK universel, signature
  `apksigner` OK, version APK == tag, checksums générés puis re-vérifiés, draft créé.
- Publication : draft publié (`gh release edit --draft=false`) → release publique v0.2.0
  (https://github.com/N7T0-OF/Sankamusic/releases/tag/v0.2.0).
- Re-vérification post-pub (download GitHub frais) : `sha256sum -c SHA256SUMS.txt` → **OK**.

## Checklist § 6 (RELEASE_GUIDE.md)

| # | Vérification | État |
|---|--------------|------|
| 1 | Tests passent (étape 0) | ☑ CI vert ; 162 tests JUnit documentés ; `:app` compilé par CI (SDK requis localement) |
| 2 | Version incrémentée, cohérente (code+tag+fichier) | ☑ 0.2.0 / code 2 / tag v0.2.0 / nom APK / notes |
| 3 | Changelog rédigé et factuel | ☑ |
| 4 | Un seul artefact par plateforme publiée | ☑ 1 APK universel (vérifié par le CI) |
| 5 | Aucun artefact debug/unsigned/factice | ☑ (CI rejette `-debug`/`-unsigned`/`unaligned`) |
| 6 | SHA256SUMS.txt présent et vérifié | ☑ (`-c` → OK en CI et sur download frais) |
| 7 | Tag git créé et poussé | ☑ v0.2.0 → 1cde505 |
| 8 | Artefacts re-téléchargés depuis GitHub re-vérifiés | ☑ sha256 `-c` OK |
| 9 | Installation + démarrage (propre ET mise à jour) | ☐ **manuel** — appareil/émulateur |
| 10 | Aucune donnée supprimée lors de la mise à jour | ☐ **manuel** |
| 11 | Le CI a réellement vérifié signature, checksum, version, unicité | ☑ (run success) |
| 12 | Preuve de release : artefact publié installé et vérifié | ☐ **manuel** |
