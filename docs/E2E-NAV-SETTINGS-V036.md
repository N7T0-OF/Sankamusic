# E2E — Patch Settings v0.3.6 (protocole de test sur appareil)

> Statique et honnêteté : ce protocole décrit le test **device** du patch
> Settings v0.3.6 — « bande haute mesurée + lignes Ko-fi / Site + section
> Intégrations » — commits `3c210b5f` + `a0d52fb4` (patch, dont le lien réel
> Ko-fi `ko-fi.com/souanpt`) et `7b3a6d09` (bump 0.3.6 / code 84), au-dessus de
> la v0.3.5 publiée (tag `v0.3.5`, code 83). Le code est
> CI-vert (gates shell, `compileKotlinMetadata`, `jvmTest`, `assembleRelease`) ;
> **rien ici n'est prouvé sur appareil** tant que ce protocole n'a pas été
> exécuté. Chaque flux cite le fichier:ligne qui l'implémente, et chaque échec
> pointe le maillon à diagnostiquer en premier (règle : identifier LE maillon,
> corriger, re-tester — jamais « partiellement fonctionnel »).

## 1. Quatre comportements à prouver

| # | Comportement | Implémentation (ouvrir si échec) |
|---|---|---|
| V1 | L'écran Settings ouvre avec le premier titre de section **juste sous la barre d'app** (la bande haute est mesurée sur la vraie barre, pas un 64dp codé en dur), section ouverte ET fermée ; le défilement fonctionne | `SettingScreen.kt` — item `glow_anchor` (~675-693) : `barBand = measuredPx − innerPadding.calculateTopPadding()`, `settingsTopBarHeightPx` alimenté par `onSizeChanged` sur la `TopAppBar` Settings (~3450) ; repli 64dp seulement avant la première mesure |
| V2 | Une section **Intégrations** contient exactement Spotify, Discord, SponsorBlock (et Last.fm si le build l'a) ; fermée par défaut ; aucune duplication ailleurs dans Settings ; ouvrir une autre section la replie | `SettingScreen.kt` — `item(key = "integrations_header")` (~1888) + `if (expandedSettingsSection == "integrations")` (~1898-2128) ; `expandedSettingsSection` démarre `null` (fermé par défaut, single-open) |
| V3 | Dans la zone À propos, une ligne **Ko-fi** est immédiatement au-dessus d'une ligne **Site** ; chacune ouvre la bonne cible externe — Ko-fi → `https://ko-fi.com/souanpt` (mainteneur, aussi déclaré `ko_fi: souanpt` dans `.github/FUNDING.yml`), Site → `https://simpmusic.org` (seule URL de site que porte ce dépôt ; le fork ne déclare aucun homepage GitHub) | `SettingScreen.kt` — corps `about_us` (~2814-2833) : `uriHandler.openUri("https://ko-fi.com/souanpt")` puis `uriHandler.openUri("https://simpmusic.org")` |
| V4 | Update Manager : v0.3.5 installée → v0.3.6 proposée puis installée en place (mêmes clé/applicationId, code 84 > 83), données conservées ; après installation : plus rien à mettre à jour (ni downgrade) ; SHA-256 + package-name vérifiés avant installation | `SpaceKaiUpdatesSection.kt` (UI) ; `SpaceKaiUpdateManager.runPipeline` (download → SHA-256 → package → install) ; `PlatformUpdater.android.kt` (`checkPackage` refuse ≠ `com.maxrave.simpmusic`) ; détection : `UpdateRepositoryImpl.kt:22-43` + `Ytmusic.kt:600-601` (repo `N7T0-OF/Sankamusic` uniquement) |

## 2. Prérequis (important)

1. **Un téléphone branché** (débogage USB) ou un émulateur, `adb devices` le liste.
2. **APK v0.3.6 signé avec la MÊME clé que la v0.3.5 installée** — le test est
   un upgrade in-place (données conservées). L'artefact CI « Build release APK
   (FOSS) » / la release v0.3.6 publiée est signé avec le keystore de release ;
   c'est l'artefact à utiliser, PAS l'APK unsigned local d'`assembleRelease`.
3. **versionCode** : le build est en `0.3.6` / `84`. Installé par-dessus une
   v0.3.5 publiée (même signature), `adb install -r` réussit et conserve les
   données. Ne jamais tester par-dessus une version **plus récente** (le
   downgrade doit être refusé, pas contourné).
4. Pour le flux V4 complet (détection → proposition → installation), la release
   **v0.3.6 doit être publique** (le checker lit `releases/latest` de
   `N7T0-OF/Sankamusic`). Si la publication n'est pas encore faite, V4a teste
   l'installation in-place de l'APK v0.3.6 et V4b (détection) est différé à
   après publication — ne jamais déclarer V4b vert sans la release réelle.
5. Feature flags SpaceKai : la release build démarre avec `allEnabled` ; le
   patch n'introduit aucun nouveau flag.

## 3. Driver automatisé

```bash
# Depuis la racine du dépôt, téléphone branché (v0.3.5 déjà installée) :
./scripts/device-acceptance-settings-v036.sh <chemin-v0.3.6.apk>
# ou sans APK si déjà installé :
./scripts/device-acceptance-settings-v036.sh
```

Le driver vérifie l'appareil, installe (optionnel), lance l'app, puis exécute
les flux **en interactif** : dumps `uiautomator` pour les assertions
automatisables (V1 géométrie, V2 présence/absence/duplications, V3 ordre
Ko-fi→Site, V4 versions installées via `dumpsys`), imprime les étapes manuelles
exactes pour le reste, demande la confirmation de l'opérateur à chaque verdict,
et écrit `device-acceptance-results.txt`. `exit 0` seulement si tout est PASS.
Il ne fabrique jamais un PASS pour ce qu'il n'a pas observé.

## 4. Flux détaillés (si exécution manuelle)

### V1 — Bande haute mesurée

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | Ouvrir Réglages | La liste s'ouvre en haut, première section repliée | `SettingScreen.kt` | — |
| 2 | Observer l'espace au-dessus du premier titre (« Interface » / « User interface ») | Titre juste sous la barre d'app (bord haut ≈ barre de statut + hauteur de barre, pas ~376dp ni plus) | `glow_anchor` (~675-693) | Grand vide → la bande codée en dur est revenue OU `onSizeChanged` sur la TopAppBar a disparu (bande = repli 64dp seulement, géométrie identique à l'ancien code — vérifier l'état mesuré) |
| 3 | Ouvrir la première section | Même départ : pas de décalage ajouté par l'ouverture | idem | L'ouverture change la géométrie → la bande dépend de l'état de section (elle ne doit pas) |
| 4 | Refermer, puis défiler jusqu'en bas et revenir en haut | Scrolling normal, bande inchangée en haut | `settingListState` | — |

Automatisé : le driver mesure `y` du premier titre et vérifie
`y/density < 250dp` (post-fix ≈ 90-115dp avec barre de statut).

### V2 — Section Intégrations

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | Défiler jusqu'à la section « Intégrations » | Un seul titre « Intégrations » ; **aucun** label Spotify/Discord/SponsorBlock visible (fermée par défaut) | `expandedSettingsSection = null` | Sous-blocs visibles sans ouverture → le corps n'est plus conditionné par `if (expandedSettingsSection == "integrations")` |
| 2 | Toucher « Intégrations » | Le corps s'ouvre : labels Spotify, Discord, SponsorBlock (et Last.fm si dispo), chacun **une seule fois**, avec ses lignes | `item(spotify/discord/lastfm/sponsor_block)` (~1899-2128) | Doublon → une ancienne section autonome existe encore ailleurs ; label absent → sous-bloc supprimé par erreur |
| 3 | Ouvrir une autre section de premier niveau (ex. « Interface ») | « Intégrations » se replie (labels disparus) | single-open `expandedSettingsSection` | Reste ouvert → le header a perdu son toggle single-open |
| 4 | Vérifier ailleurs dans Settings | Aucune entrée Spotify/Discord/SponsorBlock hors Intégrations (anciennes entrées supprimées, pas seulement déplacées) | diff `3c210b5f` | Entrée fantôme → ancienne section non retirée |

### V3 — Ko-fi + Site

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | Défiler jusqu'en bas, ouvrir « À propos » | Lignes « Ko-fi » puis « Site » dans cet ordre, immédiatement l'une sous l'autre, une seule fois chacune | corps `about_us` (~2814-2833) | Ordre inversé ou écart → les deux SettingItem ne sont pas contigus |
| 2 | Toucher « Ko-fi » | Le navigateur externe s'ouvre sur `ko-fi.com/souanpt` | `uriHandler.openUri("https://ko-fi.com/souanpt")` ; sous-titre « … (ko-fi.com/souanpt) » visible dans le dump UI | Mauvais site → URL changée/inventée |
| 3 | Revenir, toucher « Site » | Le navigateur s'ouvre sur `simpmusic.org` | `uriHandler.openUri("https://simpmusic.org")` ; sous-titre « … (simpmusic.org) » visible dans le dump UI | Mauvais site → URL changée/inventée |
| 4 | (Cohérence) Écran À propos/Crédits | Toujours sa propre ligne « Buy me a coffee » (hors scope v0.3.6, constaté, non bloquant) | `CreditScreen.kt:201` | — |

### V4 — Update Manager v0.3.5 → v0.3.6

| # | Action | Comportement attendu | Maillon | Diagnostic si échec |
|---|---|---|---|---|
| 1 | Vérifier l'installation de départ | versionName `0.3.5`, versionCode `83` | `dumpsys package …` | Autre version → scénario invalide |
| 2 | (V4b, après publication v0.3.6) Réglages → Mises à jour → vérifier | La ligne SpaceKai affiche « Installée v0.3.5 · dernière : v0.3.6 » et une action « Mettre à jour » (pipeline) apparaît | `SpaceKaiUpdatesSection` + `isVersionNewer` | Pas de v0.3.6 proposée → repo/URL dérivé (SimpMusic) ou release non publique |
| 3 | Lancer la mise à jour | Téléchargement → « Vérification SHA-256… » → installation Android ; aucune installation si le checksum ne correspond pas ou si le package n'est pas `com.maxrave.simpmusic` | `SpaceKaiUpdateManager` / `PlatformUpdater.android.kt` | Passe sans vérification → pipeline court-circuité |
| 4 | Après installation | versionName `0.3.6`, versionCode `84` ; données et compte YouTube intacts | `dumpsys` + inspection | Données perdues → clé/signature différente ou `install` sans `-r` |
| 5 | Re-vérifier dans l'app | Plus aucune mise à jour proposée (v0.3.6 == dernière) ; jamais de bouton de downgrade si installée > dernière | `isVersionNewer` (sémantique, pas string) | Encore proposée → comparaison de versions cassée |
| 6 | (Résistance) | Vérifier SHA-256 avec un APK corrompu → refus avant installation ; package ≠ → refus | idem | Installe quand même → garde-fous retirés |

## 5. Critère de sortie du patch v0.3.6 (Gate 0 device)

- [ ] V1 : premier titre juste sous la barre d'app, section ouverte ET fermée, scrolling OK (automatisé : `< 250dp`).
- [ ] V2 : « Intégrations » fermée par défaut ; à l'ouverture : Spotify, Discord, SponsorBlock (et Last.fm si dispo) exactement une fois chacun ; aucune entrée ailleurs ; une autre section la replie.
- [ ] V3 : Ko-fi immédiatement au-dessus de Site ; chaque ligne ouvre la bonne cible externe (URLs du dépôt uniquement).
- [ ] V4 : v0.3.5 (83) → v0.3.6 (84) en place, données conservées, SHA-256 + package-name vérifiés, plus rien à mettre à jour après, jamais de downgrade.
- [ ] Chaque échec ramené à un maillon précis (colonnes « Maillon »), corrigé, puis protocole rejoué.

Après Gate 0 vert seulement : tag `v0.3.6` → CI → draft → publication → V4b
(re-détection contre la release réelle). Jamais avant validation appareil réelle.
