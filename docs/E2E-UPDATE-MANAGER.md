# E2E — Update Manager réel (protocole de test sur appareil)

> Statique et honnêteté : cette doc décrit le test **device** du P0 Update Manager.
> Tout le code de la chaîne est en place et CI-vert ; **rien ici n'est prouvé sur
> appareil** tant que ce protocole n'a pas été exécuté. Chaque maillon cite le
> fichier:ligne qui l'implémente, et chaque échec pointe le maillon à diagnostiquer
> en premier (règle : identifier LE maillon, corriger, re-tester — jamais
> « partiellement fonctionnel »).

## 0. État des versions (vérifié 2026-08-31 via API)

| Élément | Valeur |
|---|---|
| version-name / version-code du build dev | `0.3.2` / `80` |
| Dernière release publiée | `v0.3.2` (`SpaceKai-v0.3.2.apk` + `SHA256SUMS.txt`, universel signé) |

Le trigger sémantique (`isVersionNewer`, `SpaceKaiUpdatesSection.kt`) rend
installé == dernière ⇒ **aucune** mise à jour proposée. C'est le comportement
correct. Pour le test, il faut donc une version **plus récente** publiée.

## 1. Prérequis du test « ancienne → nouvelle »

1. Installez sur le téléphone le build actuel (`dev`, version 0.3.2) **ou**
   l'APK de la release `v0.3.2` publiée.
2. Publiez `v0.3.3` (version-code **81 > 80**) via le pipeline de release
   existant (tag `v0.3.3` → `android-release.yml` : 1 APK universel signé,
   SHA256SUMS.txt, notes). C'est le SEUL moyen pour que l'app trouve du neuf.

## 2. Scénario SpaceKai (chemin complet de mise à jour)

| # | Action utilisateur | Comportement attendu | Maillon de code (à ouvrir si échec) |
|---|---|---|---|
| 1 | Ouvrir Paramètres → SpaceKai → Mises à jour | « Installée v0.3.2 » ; avant vérif : « dernière : — » (pas « v— ») | `SpaceKaiUpdatesSection.kt` (SPACEKAI_VERSION = `SpaceKai.kt:48` ← VersionManager) |
| 2 | La vérification lancée à l'ouverture répond | « Dernière : v0.3.3 » | `SharedViewModel.checkForUpdate()` (`SharedViewModel.kt:1109`) → `Ytmusic.kt:601` (**API live** N7T0-OF/Sankamusic) |
| 3 | Vérifier que le bouton « Mettre à jour » apparaît | Visible car `isVersionNewer("v0.3.3","v0.3.2") == true` | Compare semantique (`UpstreamCompatibility.kt`, `isVersionNewer`) |
| 4 | Toucher « Mettre à jour » | Phase DOWNLOADING avec progression + vitesse réelles | `PlatformUpdater.android.kt:49` `downloadWithProgress` (octets lus du flux) |
| 5 | Attendre | Phase VERIFYING : « Vérification SHA-256… » | `PlatformUpdater.android.kt:54` `verifySha256` vs `SHA256SUMS.txt` de la release |
| 6 | — | Phase package-check silencieuse | `PlatformUpdater.android.kt:187` `getPackageArchiveInfo` == `com.maxrave.simpmusic` |
| 7 | — | L'installateur Android s'ouvre automatiquement | `PlatformUpdater.android.kt:83,89` FileProvider → `ACTION_VIEW` package-archive |
| 8 | Approuver l'installation | Installe **par-dessus** 0.3.2 (données conservées) | Mécanisme Android (même package, versionCode supérieur) |
| 9 | Relancer SpaceKai | « Installée v0.3.3 » | SPACEKAI_VERSION ← version-name du nouveau build |
| 10 | Vérifier paramètres/playlists/historique | Toujours présents (même package, install par-dessus) | — |

### Diagnostic d'échec (par maillon)
- **1-2 échoue** : réseau / rate-limit GitHub → état « Impossible de vérifier » (pas de faux « à jour »). Vérifier le log `SpaceKaiUpdater`/UpstreamUpdate.
- **4 échoue** : URL APK absente de la release → « Aucun APK trouvé dans la release » (`SpaceKaiUpdateManager.install`). Vérifier que la release contient bien `SpaceKai-v0.3.3.apk` universel.
- **5 échoue** : APK corrompu/tamperé → « SHA-256 verification failed » + fichier supprimé. Vérifier `SHA256SUMS.txt` (basenames identiques).
- **6 échoue** : mauvais package → « Package name mismatch — the APK is not … » + suppression. Vérifier le package du build publié.
- **7 échoue** : « Application non installée » → versionCode pas strictement supérieur (checks anti-downgrade dans `android-release.yml`/`verify-release.sh`) ou signature différente (keystore égaré).

## 3. Scénario Upstream (aucune installation d'APK officiel)

| # | État simulé | Comportement attendu | Maillon |
|---|---|---|---|
| A | Base intégrée 2.0.0 (upstream.lock) ; dernière SimpMusic = 2.0.0 | « Base utilisée : v2.0.0 · Dernière disponible : v2.0.0 · ✓ À jour » | `SPACEKAI_BASED_ON_UPSTREAM` ← BuildKonfig ← `upstream.lock` ; `Ytmusic.kt:611` (API live maxrave-dev) |
| B | SimpMusic sort 2.1.0 | « Base utilisée : v2.0.0 · Dernière disponible : v2.1.0 · ⚠ Nouvelle release officielle détectée — SpaceKai pas encore compatible » | `computeUpstreamCompatibility` (`UpstreamCompatibility.kt`) |
| C | Dans les deux cas | **Aucune** offre d'installation de l'APK SimpMusic, aucun bouton d'installation upstream | Le bloc upstream est info-only par design |

Pour simuler B sans attendre une vraie release 2.1.0 : éditer localement
`upstream.lock` (base → `2.0.0`) et observer — ou attendre la sortie réelle.
Le rebase réel passe par `scripts/update-upstream.sh` + `upstream.lock`
(étape 4 lecture / étape 8 écriture) — jamais par l'APK officiel.

## 4. Critère de sortie du P0

- [ ] Les 10 étapes du scénario SpaceKai réussies sur appareil.
- [ ] Données (paramètres, playlists, historique) préservées après mise à jour.
- [ ] Scénario upstream A ou B affiché correctement, aucune offre d'installation.
- [ ] Chaque échec éventuel ramené à un maillon précis (table ci-dessus) et corrigé,
      puis protocole rejoué.

Après ce P0 : player paysage, navigation (minimaliste flottante + rail droite),
haptics, Dynamic Color, téléchargements, widgets — puis release SpaceKai.