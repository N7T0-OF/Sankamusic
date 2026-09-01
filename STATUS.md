# STATUS — SpaceKai repo & release pipeline

_Last updated: 2026-08-26, workspace at **v2.0.0 (code 74)**._

## 🎯 Repo live (N7T0-OF/Sankamusic)

| Élément | État |
| --- | --- |
| Branches | `main`=`1930c9d` (v1.8.0/71, pas de pipeline) · `dev`=`632ec2f` (v1.9.0/73, pipeline staged) — **divergés** (STATUS précédent « main=dev » périmé) |
| Version (workspace) | **`2.0.0` / versionCode `74`** (`gradle/libs.versions.toml`) — le live `dev` est à 1.9.0/73 ; `apply-release-pipeline.sh` le portera à 74 (vérifié dry-run) |
| Releases publiées | v1.7.4-1 (60), v1.7.5 (61), v1.7.6 (67), v1.7.7 (68), v1.7.8 (69), v1.7.9 (70), **v1.8.0 (71)** — toutes publiques |
| Latest | **v1.8.0** — barre minimaliste avec fond, lecteur paysage plein écran, playlists Spotify automatiques dans la Bibliothèque |
| **Fix Settings UI** | commits `99c5f561` + `257a497` — publiés dans la v1.7.8 |
| Update checker | pointe vers `N7T0-OF/Sankamusic` (vérifié : `Ytmusic.kt:600-603`, branche main+dev, sous-module core `1949268a…`) |

### VersionCodes publiés (historique — le max est 71, d'où le plancher)

```
v1.0.1 → 56 · v1.1.1/2 → 57 · v1.1.3 → 58 · v1.1.4 → 59 · v1.1.5 → 60
v1.1.6-1/-3 → 66   ← MAX historique (ancien)
v1.7.2 → 58 · v1.7.3 → 59 · v1.7.4-1 → 60 · v1.7.5 → 61
v1.7.6 → 67        ← dépasse 66 → installable partout
v1.7.7 → 68 · v1.7.8 → 69 · v1.7.9 → 70 · v1.8.0 → 71   ← MAX actuel
```

Signature des APK : `CN=Sankamusic Dev`, SHA-256 `D9:BA:FD:4F:AB:87:15:DB:03:B2:67:11:E3:A8:42:A9:6E:90:BA:BA:BF:1A:83:3F:63:57:A9:49:B4:09:63:7E` (identique sur v0.3.0 et v0.3.2 ; l'ancienne mention `CN=LiquidFlow` / `9D:8E:4A:94:…` ne correspondait à aucun APK publié).

## 📦 Releases de la session

| Release | Tag | versionCode | Notes |
| --- | --- | --- | --- |
| v1.7.4-1 | `v1.7.4-1` | 60 | Tag `v1.7.4` brûlé par GitHub (immutable releases) |
| v1.7.5 | `v1.7.5` | 61 | ⚠️ Non installable en mise à jour depuis v1.1.6-3 (66) |
| v1.7.6 | `v1.7.6` | **67** | ✅ Installable en mise à jour depuis tout |
| v1.7.7 | `v1.7.7` | **68** | ✅ Fix : dialogue update → releases SpaceKai |
| v1.7.8 | `v1.7.8` | 69 | ✅ Fix Settings UI (`99c5f561` + `257a497`) |
| v1.7.9 | `v1.7.9` | 70 | ✅ |
| v1.8.0 | `v1.8.0` | **71** | ✅ Barre minimaliste avec fond, lecteur paysage, playlists Spotify auto |
| v1.8.1 | — (préparée) | **72** | ⏳ Bump toml + MIN_VERSION_CODE=71 + changelogs 72.txt faits |

## 🔒 Leçons verrouillées dans le pipeline (3 bugs réels attrapés)

### 1. Immutable releases GitHub — tag brûlé définitivement
Quand une release **publiée** est supprimée, GitHub tombe le tag_name pour
toujours (`tag_name was used by an immutable release`, même après suppression
du tag ; identique via API REST et `git push`).

- **Pattern sûr** : draft d'abord, tous les assets dans le **même**
  `gh release create --draft` (ou draft → upload → publish via API). Jamais
  créer publié puis ajouter des assets (422). Jamais supprimer une release
  publiée.
- **Garde** : `gh release view "$TAG"` refuse la création si le tag a déjà une
  release (workflows `android-release.yml` + `publish-from-artifact.yml`).

### 2. Downgrade Android — « Application non installée »
Android refuse toute APK dont le versionCode n'est pas strictement supérieur
au code installé. v1.7.5 (61) a été rejetée sur les appareils en v1.1.6-3 (66).

- **Règle** : `versionCode = max(code actuel, 66) + 1` (pas un simple +1).
- **Triple garde** : `MIN_VERSION_CODE=66` dans `apply-release-pipeline.sh`,
  `release-publish.sh`, `verify-release.sh` + CI `validate-tag` qui scanne
  **tous** les tags (pas juste le précédent).

### 3. Divers corrigés en cours de route
- `$(jq …)` Discord jamais fermé → notif cassée en silence
- `secrets` illisible dans `if` job-level → pattern env + `outputs.built`
- Branche par défaut du live = `dev` → push de la branche courante
- `release.yml` du live désactivé par `apply-release-pipeline.sh` (évite le
  double déclenchement sur un même tag)
- Header de changelog `SimpMusic v…` → `SpaceKai v…`
- **Cross-check élargi aux toggles décoratifs (2026-08-26)** : un claim du
  changelog pour une feature PARTIALLY à toggle décoratif (le pattern
  changelog-73 « one style selector (Minimalist…) ») était invisible — le
  cross-check ne couvrait que NOT IMPLEMENTED/BROKEN/NON VÉRIFIABLE. Détecté
  désormais → le rapport liste la contradiction, le nightly la signale
- **`scripts/check-pre-tag.sh` (2026-08-26)** : portier avant `release.sh` sur
  le dépôt live — git+origin, `gh` installé/authentifié, audit-features vert,
  cross-check du changelog (contradictions = FAIL), les 10 autres gates
  individuels + la parité des 3 listes, pas de BROKEN, version lisible. L'APK
  n'y est PAS vérifié (c'est la CI qui le construit après le tag). Testé : 5
  FAIL corrects sur ce snapshot (git, gh, toggles, 2 contradictions), EXIT=1

## 🛠 Pipeline (workspace → live via `apply-release-pipeline.sh`)

**Catégories** : A = nouveaux fichiers copiés · B = remplacés avec `.bak` ·
C = éditions ciblées · D = **jamais copiés** (`composeApp/src`, `core/`,
`desktopApp/src`, `androidApp/src` — les features live).

**Scripts** : `release.sh` (1 commande) · `apply-release-pipeline.sh` ·
`release-publish.sh` (commit+tag+push branche courante) · `pre-release-report.sh`
(gate) · `verify-release.sh` (post-publication) · `verify-icons.sh` (icônes
verrouillées) · `update-upstream.sh` · `generate-fastlane-changelog.sh` ·
**audits** : `audit-features.sh` (toggles factices) · `audit-settings-ui.sh`
(superposition lazy items) · `audit-upstream-hotspots.sh` (réversions
silencieuses) · `audit-spotify-flow.sh` (chaîne sp_dc) ·
`audit-landscape-player.sh` (layout paysage) ·
`audit-updater-flow.sh` (chaîne mise à jour) · `audit-navigation.sh`
(barre de navigation) · `audit-dynamic-color.sh` (Dynamic Color) ·
`audit-provider-arch.sh` (architecture providers, design-first) ·
`check-gate-parity.sh` (parité des 3 listes de gates) ·
`generate-feature-audit.sh` (rapport FEATURE AUDIT par preuves).

**Workflows** : `android-release.yml` (tag → build 1 APK → draft) ·
`publish-from-artifact.yml` (1 clic depuis un artefact déjà construit) ·
`verify-release.yml` (post-publication) · `release-nightly-check.yml` ·
`validate-workflows.yml` (actionlint).

**Gates de release** (tous dans `pre-release-report.sh`, exécuté par
`android-release.yml` ET par `publish-from-artifact.yml` (publication en
1 clic depuis un artefact) → FAIL = release bloquée, **aucun chemin de
publication ne contourne les gates**) :
- tag = version-name · versionCode > max historique (71) · icônes verrouillées
  (SHA-256) · 1 seul APK + SHA256SUMS · vérification post-publication
  (checksums + versionCode) · **toggles factices** (ZERO FALSE POSITIVE) ·
  **superposition lazy items** · **hotspots upstream** · **flux Spotify sp_dc** ·
  **player paysage** (correctif artwork + branchements) · **mise à jour**
  (chaîne détection + URL SpaceKai) · **barre de navigation** (styles +
  masquage + rail paysage) · **Dynamic Color** (capacité + fond) ·
  **architecture providers** (design-first, aucun code provider sans
  abstraction) · **FEATURE AUDIT** (BROKEN bloque ; gaps doivent figurer
  aux notes)

**Nightly** (`release-nightly-check.yml`, 03:00 UTC) : les 10 audits + la
**parité des gates** en **warning-only** + le **FEATURE AUDIT** (régénéré,
BROKEN et contradictions de revendication signalés) — un merge upstream qui
casse un câblage / l'UI / un hotspot / le login Spotify / le layout paysage /
la chaîne mise à jour / la barre de navigation / le Dynamic Color /
l'architecture providers / la parité des gates, ou un changelog qui
revendique une feature non finie, est signalé le lendemain matin, avant la
release.

## 🎯 Constat Spotify (P0 — audit statique 2026-08-26)

**« redirect_uri cassé » est une fausse piste.** Aucun OAuth PKCE n'existe :
login = WebView `Config.SPOTIFY_LOG_IN_URL` + extraction du cookie `sp_dc`
(`saveSpotifySpdc` → `setSpdc`), le flux InnerTune. `SpotifyPkce.kt` est du
code mort (jamais appelé). L'import des playlists **n'a jamais été implémenté**
(toggle `spotifySync` décoratif). Le vrai P0 : choisir la stratégie (cookie vs
OAuth officiel) puis implémenter l'import. Détails + gate :
`scripts/audit-spotify-flow.sh`, `docs/FEATURE-AUDIT.md` § Spotify.

## 🚀 Prochaine release (v2.0.0 — workspace actuel)

> **Bump v1.9.0 → v2.0.0 (2026-08-26)** : la 1.9.0 n'a jamais été publiée (bloquée
> par les 6 toggles décoratifs — ZERO FALSE POSITIVE). Le workspace est passé à
> `2.0.0`/changelog `74.txt` : les travaux de la session deviennent une **major
> release de consolidation**. L'audit des claims du changelog ci-dessous (fait
> sur `73.txt`) reste la vérité de l'état des features ; le changelog `74.txt`
> ajoute la consolidation du pipeline (gates, simulations, garde d'honnêteté).

**Audit des claims du changelog `73.txt` (vérifié contre le code, 2026-08-26) :**

| Claim du changelog | Verdict dans CE snapshot |
| --- | --- |
| Nav paysage rail à droite (mêmes tabs/style/ordre) | ✅ Vérifié (`App.kt:654`) |
| « One style selector (Minimalist / Translucent / Liquid glass) » | ⚠️ **PAS vérifié puis CORRIGÉ (2026-08-26)** — aucun sélecteur dans le code, pas de style Minimaliste (2 toggles indépendants). La ligne du changelog 73 a été réécrite honnêtement (« no unified selector yet ») → cross-check à 0 contradiction. Si le live possède réellement le sélecteur, restaurer la ligne APRÈS vérification d'audit-features |
| « Hide text » compact icons-only | ✅ Vérifié (`hideNavLabel` + M3 compact) |
| Swipe barre → track suivant/précédent (flag customNavigation) | ✅ Vérifié (`App.kt:182`) |
| Landscape player : pochette proportionnelle | ✅ Vérifié (`aspectRatio matchHeightConstraintsFirst`) |
| Flags SpaceKai persistés | ✅ Vérifié (`mergePersistedSpaceKaiFeatures`) |
| Perf home : clés lazy stables + cache disque images | ✅ Vérifié (`verify-perf-keys` PASS + `AdapterItems.kt:155` diskCachePolicy) |
| Pipeline : gardes release + touchpoints upstream | ✅ Vérifié |

**Si le live n'a pas le sélecteur de style** : le changelog 73 est à corriger avant tag — les gates (audit-features + FEATURE AUDIT) bloqueront de toute façon la release, c'est voulu.

État du workspace (vérifié 2026-08-26) :
- `gradle/libs.versions.toml` → **`2.0.0`** / code **74** (workspace et tag live) ✅
- `conveyor.conf` vcs-url → `N7T0-OF/Sankamusic` ✅ (`audit-upstream-hotspots.sh` PASS)
- Icônes verrouillées OK (`verify-icons.sh` PASS) ✅
- Chaîne Spotify sp_dc intacte (`audit-spotify-flow.sh` PASS) ✅
- **FEATURE AUDIT** généré (23 features) : 1 IMPLEMENTED, 10 PARTIALLY,
  9 NOT IMPLEMENTED, 0 BROKEN, 2 NON VÉRIFIABLE, 1 OK(gate) — les 6 toggles
  factices + les providers (5) + widgets bloquent la release tant qu'ils ne
  sont pas câblés (voulu, ZERO FALSE POSITIVE)
- **Périmètre P1 vérifié (2026-08-26)** : Android Auto = manifeste OK
  (SimpleMediaService déclare MediaLibraryService, androidApp:228-231) mais
  classe dans core/media non extraite ici → NON VÉRIFIABLE, à confirmer sur le
  dépôt complet ; Widgets = aucun AppWidgetProvider nulle part → à construire ;
  Musique locale = MediaStore seulement dans AutoBackupWorker (sauvegarde) →
  à construire (spec #10/#33, plans docs/PROVIDER-ARCHITECTURE.md §5ter-5quater)
- **Chaîne des 6 toggles vérifiée de bout en bout (2026-08-26)** : les 6 flags
  FAIL d'`audit-features.sh` (spotifySync, minimalisticNavigation, dynamicColor,
  landscapePlayer, downloadWifiOnly, customPlayerInfo) correspondent **1:1** aux
  6 lignes « Toggle décoratif » de `docs/FEATURE-AUDIT-REPORT.md` et aux 6 lignes
  du bloc « Connu / non terminé » de la note de release. `customNavigation`
  (2 sites) et `haptics` (6 sites) sont câblés. Note régénérée via le flux CI
  exact (`73.txt` → `append-feature-audit-gaps.sh`) : 21 lignes de gaps (9 NI +
  10 PARTIALLY + 2 NV), 0 contradiction, 0 « EspaceKai », lignes corrigées du
  changelog présentes telles quelles. La distinction FEATURE-AUDIT.md (référentiel
  tracké) vs FEATURE-AUDIT-REPORT.md (artefact généré, gitignored) est cohérente
  partout (gates lisent le report, docs référencent le référentiel).
- **Chasse aux compteurs dans les docs (2026-08-26)** : après le « 9 audits »
  de ci-cd.md, toutes les mentions numériques des docs/workflows ont été
  confrontées aux nombres réels : encore 3 mentions périmées dans STATUS.md
  corrigées (« les 9 audits » du nightly → 10 + parité ; « les 10 autres
  gates » du pré-tag → + parité ; liste des audits complétée avec
  `check-gate-parity.sh`). Vérifiés conformes : ci-cd.md (10 audits + parité,
  12 code gates), PROVIDER-ARCHITECTURE.md (12 code gates), compteurs du
  FEATURE AUDIT (23 = 1+10+9+0+2+1, 21 lignes de gaps). Les 2 chemins de
  publication (`android-release.yml:566`, `publish-from-artifact.yml:116`)
  invoquent `pre-release-report.sh` comme source unique — aucune liste de
  gates dupliquée dans les workflows.
- **Audit de cohérence des versions (2026-08-26)** : chaîne 1.9.0/73
  vérifiée (libs.versions.toml → changelog 73.txt header « SpaceKai v1.9.0 » →
  EXPECTED_APK SpaceKai-v1.9.0.apk → MIN_VERSION_CODE). **2 vrais bugs
  corrigés** : (1) `apply-release-pipeline.sh` copiait le changelog via
  `ls | head -1` → sélectionnait **1.txt** (le plus ancien, ère SimpMusic) au
  lieu du plus récent — la nouvelle release aurait publié les notes de la
  v1.0 ; corrigé : sélection par code numérique de la source (73), fallback
  plus élevé. (2) MIN_VERSION_CODE=71 périmé — le changelog 72.txt prouve que
  v1.8.1 (code 72) a été publié ; monté à **72** dans les 3 constantes
  (verify-release, apply-release-pipeline, release-publish) avec commentaires
  mis à jour. **Trou comblé** : aucun gate ne validait le header du changelog
  — ajout d'un check « SpaceKai v<version> » (tolérant la forme `# ` du
  générateur) dans check-pre-tag.sh 4b (bloquant) et pre-release-report
  (warn). Testé : header « SimpMusic v1.9.0 » → FAIL, forme `# SpaceKai` →
  PASS. Le garde anti-downgrade dynamique (scan des tags, android-release.yml)
  reste l'autorité au moment du tag.
- **UPSTREAM.md corrigé — App.kt était le point d'attache manquant (2026-08-26)** :
  la liste des hotspots de conflit oubliait **App.kt**, le point central des
  hooks SpaceKai (persistance :174, swipe, sélection de barre :491-506,
  analytics) — un sync qui réécrit App.kt perdrait TOUS les hooks sans
  marqueur de conflit. Ajouté en tête de liste + rappel « grep by hand » dans
  update-upstream.sh (étape 7). La table des conflits classait
  `composeApp/src/**` comme upstream-owned sans clarifier que le package
  `spacekai/` (absent chez upstream) est SpaceKai-owned et jamais touché par
  le git merge — ligne dédiée ajoutée (ne jamais le supprimer en résolvant un
  conflit). Vérifié : App.kt porte 3 marqueurs « SPACEKAI FEATURE » → le
  scanner étape 7 le détectera. Autres hotspots ajoutés : barres
  (AppBottomNavigationBar/LiquidGlass), LogInViewModel (sp_dc). Verdicts du
  FEATURE AUDIT re-spot-checkés (sections repliables = vraiment absentes,
  orientation/rail = exacts).
- **Arbitrage final A/B (2026-08-26)** : WIRING-P0.md tranche les 2 derniers
  toggles. `spotifySync` → **retirer le toggle (option B)** : l'add-on n'a
  aucune feature de sync (import OAuth inexistant §5bis), l'option A gaterait
  le chemin sp_dc de la BASE (sémantique douteuse), effort A moyen pour un
  bénéfice incertain — effort B ≈ 15 min ; l'audit garde la ligne « Login
  Spotify » avec verdict par preuves (chaîne sp_dc réelle), sans toggle
  décoratif. `downloadWifiOnly` → **câbler à l'enqueue (option A)** : le
- **Simulation du retrait de spotifySync (2026-08-26)** — l'arbitrage B
  vérifié par exécution, pas seulement par raisonnement. La simulation a
  révélé 2 corrections : (1) `generate-feature-audit.sh` utilisait le NOM du
  flag comme preuve (`spotifySync`/`SpaceKaiFeatures::spotifySync`) — flag
  retiré, la ligne « Login Spotify » basculait en NOT IMPLEMENTED (0 hit) au
  lieu du PARTIALLY promis. Corrigé : la ligne porte `flag=-` + patterns de la
  chaîne réelle (`saveSpotifySpdc` UI / `setSpdc` logique, sous composeApp) →
  verdict par preuves dans les DEUX états (flag présent ou retiré). (2) la
  note de gap gardait le résidu « (toggle décoratif) » — nettoyée (chaîne
  réelle + absence OAuth PKCE/import). Vérifié : flag retiré → spotify reste
  PARTIALLY avec snippets de la chaîne, audit-features 6 → 5 FAIL ; état
  restauré → identique au snapshot (10 PARTIALLY / 9 NOT IMPLEMENTED). La
  procédure de retrait (3 actions) est documentée dans WIRING-P0.md, le
  message de release synchronisé avec la note fraîche.
- **Simulation du repli B de downloadWifiOnly (2026-08-26)** — l'arbitrage
  disait « même procédure que spotifySync » ; la simulation prouve le
  contraire. Spotify a une chaîne réelle à re-pointer ; download n'en a
  AUCUNE dans composeApp (le câblage vit dans core/data) — les patterns du
  générateur sont le *nom du flag*. Flag retiré sans toucher le générateur →
  la ligne wifi-only bascule en **NOT IMPLEMENTED fantôme** (« Aucune preuve »)
  au lieu de disparaître. Correction : retirer la ligne `wifi-only` de
  `generate-feature-audit.sh` → compteurs à 22 features (1/9/9/0/2/1, sans la
  ligne), audit-features 6 → 5 FAIL, et zéro contradiction (le changelog 73 ne
  revendique rien sur le wifi — vérifié). Variante CORE documentée (NON
  VÉRIFIABLE) si la feature doit rester tracée. Procédure complète dans
  WIRING-P0.md §4 « Repli B — procédure DIFFÉRENTE de spotifySync ».
- **publish-draft.sh — la release publique à UNE commande (2026-08-26)** : la
  chaîne s'arrêtait au tag (« ouvrir le draft, vérifier, cliquer Publish » à la
  main). Le nouveau script finalise : sur le live, après la CI, il (1) résout
  le tag depuis libs.versions.toml et refuse proprement si gh absent / release
  inexistante (exit 1) ou déjà publiée / pré-release CI (exit 2) ; (2)
  re-télécharge les assets et les vérifie avec verify-release.sh (mêmes checks
  que verify-release.yml, avant publication) ; (3) exige le bloc « Connu / non
  terminé » dans la note (garde ZERO FALSE POSITIVE — jamais publier une note
  qui cache du non-fait) ; (4) confirme puis `gh release publish`. Testé avec
  un gh simulé : 8 branches (gh absent, release absente, pré-release, publiée,
  assets invalides refusés par le verify réel, note sans bloc d'honnêteté
  refusée, chemin heureux → publish, --dry-run sans effet). Intégré : A_FILES,
  publish.sh (message final → prochaine étape), RELEASE.md (§ « Making the
  release public »), ci-cd.md (table), SESSION. 26 scripts syntaxe OK.
- **Garde de fraîcheur du bloc d'honnêteté dans publish-draft.sh (2026-08-26)**
  : la première version vérifiait la PRÉSENCE du bloc « Connu / non terminé »
  dans la note du draft ; elle ne détectait pas un bloc STALE (audit changé
  entre la CI et la publication, ou édition manuelle qui a retiré un gap).
  publish-draft.sh régénère maintenant l'audit localement et compare les gap
  lines (`- **…** — VERDICT` 21 lignes) à celles du body du draft — différences
  → refus « HONESTY BLOCK IS STALE ». Comparaison sur les gap lines seulement
  (les sections Installation/SHA-256 peuvent légitimement différer). Testé :
  identiques → PASS, tronqué → refus, chemin heureux → publish. Cohérence de
  la chaîne vérifiée : MIN_VERSION_CODE=72 identique sur les 3 sites
  (apply-release-pipeline, release-publish, verify-release), verify-release.yml
  appelle le MÊME verify-release.sh que publish-draft.sh, et la CI injecte le
  bloc via append-feature-audit-gaps.sh (android-release.yml:555) — la garde
  retrouve donc bien dans le body ce que la CI y a écrit.
- **Passe croisée finale (2026-08-26)** : les 3 docs décrivaient la garde
  d'honnêteté comme « présence seule » alors que le script vérifie la
  fraîcheur — RELEASE.md, ci-cd.md et SESSION corrigés (« present AND
  current »). Puis les compteurs : rapport réel 1/10/9/0/2/1 = SESSION
  (« 1/10/9/0/2/1 », « 21 lignes ») = message de release (19 lignes verdicts =
  9 NI + 10 PARTIALLY). Le diff des gap lines du bloc honnêteté du message vs
  le rapport frais : **21/21 identiques** (la ligne « draft → assets →
  publish » qui semblait diverger est dans la section « Règles de publication »,
  hors bloc). 6 toggles → 6 références WIRING-P0 dans le message = 6 guides
  dans WIRING-P0.md. 3 simulations PASS dans test-gates. Aucune divergence.
- **test-gates.sh rendu AGNOSTIQUE à l'état (2026-08-26)** — un vrai piège
  découvert : le test était lié à l'état actuel (EXPECT[audit-features]=1 + 3
  simulations supposant 6 FAIL). Simulé l'état post-câblage (WIRING-P0 : 4
  toggles câblés + spotifySync/downloadWifiOnly retirés → 0 FAIL) : le test
  criait « GATE REGRESSION DETECTED » avec 4 FAIL — l'équipe live ferait ce
  que le plan dit et le test la traiterait comme une régression. Corrigé :
  (1) audit-features dérivé des FAIL lines réelles (exit 0 + 0 FAIL = wired,
  exit 1 + FAIL = blocker — l'invariant est la cohérence exit/FAIL, pas un
  chiffre) ; (2) simulations A/B/C sur des INVARIANTS (spotifySync retiré →
  jamais un FAIL + ligne spotify PARTIALLY par preuves ; downloadWifiOnly
  retiré → flag + ligne générateur disparues ; 4 toggles câblés → aucun
  flaggé + stub récompensé). Testé dans les DEUX états : 6 FAIL → PASS,
  0 FAIL (post-câblage) → PASS. Le même fichier est valide avant ET après le
  câblage — aucune édition nécessaire au moment du câblage. (Durée ~1m30, les
  simulations régénèrent l'audit.)
- **test-gates.sh couche 3 : les 3 simulations automatisées (2026-08-26)** —
  les scénarios exercés à la main deviennent des tests de régression :
  (A) retrait spotifySync → 5 FAIL + ligne spotify reste PARTIALLY par
  preuves ; (B) repli B downloadWifiOnly → 5 FAIL + ligne wifi-only retirée du
  générateur (pas de fantôme) ; (C) câblage des 4 toggles → 2 FAIL + le stub
  fait basculer player-info en IMPLEMENTED (le gate ne distingue pas stub vs
  réel — le test visuel reste la vraie garde). Chaque simulation tourne dans
  un sous-shell avec trap EXIT aux chemins embarqués → l'arbre reste propre
  après l'exécution (vérifié : spotifySync=2, downloadWifiOnly=2, wifi-only=1,
  SimWiringTmp absent). Deux pièges corrigés en route : (1) un trap EXIT de
  fonction dans le shell principal est REMPLACÉ par le trap suivant — jamais
  exécuté ; (2) le générateur écrit les lignes dans le rapport (fichier), pas
  sur stdout — les assertions doivent lire docs/FEATURE-AUDIT-REPORT.md.
- **Simulation du câblage des 4 toggles prêts (2026-08-26)** : la promesse
  plan vérifiée par exécution : 4 références simulées (customPlayerInfo,
  minimalisticNavigation, landscapePlayer, dynamicColor) → audit-features
  passe de 6 à 2 FAIL (restent spotifySync, downloadWifiOnly), exactement
  comme prévu. **Piège révélé** : le générateur récompense AUSSI le stub —
  player-info (note de gap vide) bascule en IMPLEMENTED (static) dans le
  rapport alors que le comportement réel n'existe pas ; les 3 autres restent
  PARTIALLY grâce à leur note de gap non vide. Conséquence documentée : gate
  et rapport ne distinguent pas stub vs vrai câblage — le TEST VISUEL
  obligatoire de chaque guide est la vraie garde (WIRING-P0.md § Après le
  câblage). `downloadWifiOnly` → **câbler à l'enqueue (option A)** : le
  toggle est le SEUL réglage wifi-download du code visible (la base n'en a
  pas — vérifié), sémantique claire, effort modéré (ViewModel + check réseau +
  param wifiOnly vers core/data), caveat retry documenté, repli B si le
  câblage core n'est pas faisable. Table « État final attendu » ajoutée :
  4 câblages + 1 retrait + 1 conditionnel → audit-features vert → publish.sh
  s'ouvre (git/origin/gh seuls restants).
- **Passe croisée finale des documents (2026-08-26)** : les 4 documents
  (SESSION-2026-08-26, RELEASE-MESSAGE-v1.9.0, WIRING-P0, FEATURE-AUDIT-REPORT)
  racontent la même histoire — vérifié chiffre par chiffre : verdicts du
  rapport (1/10/9/0/2/1 = 23 features) identiques aux claims de SESSION et aux
  compteurs de RELEASE-MESSAGE (9 NI + 10 PARTIALLY) ; 21 gaps = 9+10+2
  (23 − 2 entièrement faites) ; les 6 flags FAIL présents dans l'audit ET dans
  WIRING-P0 (chacun 5× dans l'audit, 8-12× dans le plan) ; points d'insertion
  de la table SESSION §6 identiques à WIRING-P0 (spotifySync :28-37,
  minimalisticNavigation :491-506, dynamicColor :120-128, landscapePlayer
  branche paysage, customPlayerInfo :2455-2475). Aucune divergence.
- **Message de release v1.9.0 prêt (2026-08-26)** :
  `docs/RELEASE-MESSAGE-v1.9.0.md` (nouveau, propagé A_FILES) — le texte
  exact à publier, généré depuis le pipeline réel (73.txt →
  append-feature-audit-gaps.sh) et **vérifié ligne par ligne** : 7/7 lignes du
  changelog présentes, 9 NOT IMPLEMENTED + 10 PARTIALLY (comme la note CI),
  7 références WIRING-P0, 0 coquille. Contient : titre + corps GitHub complet
  (changelog + bloc Connu/non terminé + Installation + SHA-256), texte Play
  Store (le contenu de 73.txt), assets attendus, et rappel des règles
  (draft → assets → publish ; vérif checksums + versionCode > 72). Encadré :
  ne part QUE quand check-pre-tag affiche ALL CHECKS PASSED.
- **Garde anti-dépendance core→composeApp (2026-08-26)** : la décision
  d'architecture est maintenant **gardée par un gate** — `audit-provider-arch.sh`
  gagne un bloc « Layer integrity » (commun aux modes A/B) : FAIL si un module
  core importe `com.maxrave.simpmusic.spacekai` ou `.ui.` (les seuls packages
  composeApp-owned ; core/data importe légitimement domain/data). Testé : état
  normal → PASS ; faux import spacekai dans core/service/spotify → FAIL
  « dependency direction violated » ; restore → PASS. Intégré sans churn des
  compteurs (le gate existant s'enrichit, pas de nouveau script). Documenté
  dans WIRING-P0.md (§ Décisions, la règle est gardée par un gate).
- **Décision d'architecture : étendue du scan (2026-08-26)** — la question
  n°1 est tranchée : **l'état du flag remonte via composeApp (option A)**.
  Vérifié : spacekai/ vit dans composeApp, core/ n'en dépend pas (Clean
  Architecture) — un câblage core → flag créerait une dépendance interdite.
  Règle documentée dans WIRING-P0.md (§ Décisions) et l'en-tête
  d'audit-features.sh (SCOPE DECISION) : chaque câblage touchant core passe
  par une référence réelle dans composeApp (ViewModel lit le flag, le passe en
  paramètre à core) — le scan du gate reste inchangé. Conséquences :
  core/service/spotify ne dépend pas de spacekai (paramètre), downloadWifiOnly
  se câble à l'enqueue depuis composeApp (caveat : check à l'enqueue, pas au
  retry). spotifySync et downloadWifiOnly restent à arbitrer A/B selon la
  fenêtre.
- **WIRING-P0.md COMPLET — 6 guides + questions ouvertes (2026-08-26)** :
  les 6 toggles ont maintenant un guide pas-à-pas : customPlayerInfo,
  minimalisticNavigation (2 listes de tabs :96/:234), landscapePlayer
  (BoxWithConstraints, correctif artwork hors flag), spotifySync (option A
  service / option B retrait), downloadWifiOnly (core/data, 2 options de
  scan) et dynamicColor (override isAmoled=false dans Theme.kt ~:128 — la
  correction du bug « fond sombre épinglé au noir »). Plus la section
  « Questions ouvertes consolidées » : étendue du scan d'audit-features
  (composeApp vs core), dépendance core/service/spotify → spacekai, et les
  arbitrages A/B pour spotifySync et downloadWifiOnly. 3 guides prêts à
  exécuter sans arbitrage (customPlayerInfo, minimalisticNavigation,
  landscapePlayer) ; 2 exigent une décision (spotifySync, downloadWifiOnly) ;
  1 prêt (dynamicColor).
- **Guide spotifySync — le cas honnêteté (2026-08-26)** : WIRING-P0.md
  documente que ce toggle ne se câble PAS honnêtement dans composeApp seul —
  la consommation réelle du cookie (lyrics/canvas) vit dans core/service/spotify
  (absent). Deux options : A (recommandée, live) gate le chemin sp_dc dans le
  service avec la question de la dépendance core→spacekai et de l'étendue du
  scan d'audit-features ; B (fallback honnête) **retirer le toggle** plutôt
  que de le câbler faux — la seule option qui garantit « jamais un login
  factice ». Interdit explicitement : câbler sur une brique décorative pour
  faire passer le gate. Repères vérifiés : saveSpotifySpdc :28, getSpotifyLogIn
  :1722, item :498, hint :3302. WIRING-P0 couvre maintenant 4 toggles.
- **Guides de câblage pour 3 toggles (2026-08-26)** : WIRING-P0.md contient
  maintenant les guides pas-à-pas de `customPlayerInfo` (description :2466 +
  paroles :2254), `minimalisticNavigation` (flag lu dans App.kt ~:155, passé
  aux deux barres ; **deux listes de tabs à filtrer** — barre ~:96-100 et rail
  ~:234-240 dans AppBottomNavigationBar.kt, plus la liste du verre liquide) et
  `landscapePlayer` (BoxWithConstraints + `maxWidth > maxHeight`, branche
  paysage gateée, correctif artwork :1036 **hors** du flag — c'est un bugfix).
  Chaque guide : imports, snippets, repères de lignes **vérifiés** (sealed
  BottomNavScreen :41, MixForYou :99, listes :96/:234), critère de passage du
  gate et test visuel. Pièges documentés : pas de référence morte, ne pas
  mettre le bugfix artwork sous le flag.
- **Guide de câblage détaillé du premier toggle (2026-08-26)** : WIRING-P0.md
  gagne une section pas-à-pas pour `customPlayerInfo` — imports exacts
  (SpaceKaiFeatures + isSpaceKaiFeatureEnabled, à ajouter dans
  NowPlayingScreen.kt), snippet de câblage (`showPlayerInfo`), bloc
  description (DescriptionView :2466) et paroles (LyricsView :2254) à gater,
  critère de passage (6 → 5 FAIL) et test visuel obligatoire (toggle →
  description/paroles disparaissent ; redémarrage → persistance). Piège
  documenté : pas de référence morte — chaque `if (showPlayerInfo)` doit
  piloter un rendu visible. Repères de lignes vérifiés contre le code
  (DescriptionView :2466 confirmé).
- **`scripts/publish.sh` — la publication à UNE commande (2026-08-26)** :
  l'entrée « prête à cliquer » pour le dépôt live. Enchaîne
  check-pre-tag.sh (git/gh + gates + changelog) → test-gates.sh (régression)
  → release.sh --dry-run (prévisualisation du bump) → confirmation (--yes
  pour sauter) → release.sh réel. S'arrête à la première erreur ; l'APK est
  délibérément exclu (la CI le construit après le tag, gates APK dans
  pre-release-report). Testé sur ce snapshot : s'arrête à l'étape 1
  (check-pre-tag FAIL attendu), EXIT=1. Propagué A_FILES + documenté dans
  RELEASE.md (flux 1-commande) et ci-cd.md (§ Publish gate).
- **`scripts/test-gates.sh` — l'auto-vérification des gates (2026-08-26)** :
  le test de régression qui automatise la passe manuelle. Deux couches :
  (1) table des résultats attendus — les 12 gates doivent produire leur exit
  code (11 PASS + audit-features FAIL, le bloqueur) ; (2) réalité des patterns
  — 21 paires critiques (fichier|pattern) de l'audit doivent matcher le code
  réel, sinon FAIL « LOST ». Testé : état normal → 12/12 + 21/21 PASS ;
  simulation de hook perdu (barre liquide) → LOST + regression détectée ;
  restore → PASS. Intégré : A_FILES, étape dédiée du nightly (hors boucle
  outX — les compteurs restent à 10 audits + parité), ci-cd.md. Un gate dont
  le pattern ne matche plus (fausse confiance) est maintenant attrapé chaque
  nuit, avant même que son verdict ne change.
- **Passe « vérifier le vérificateur » — COMPLÈTE (2026-08-26)** : les
  patterns de détection des **11 gates** confrontés au code réel, un par un.
  Spotify : 5/5 maillons (saveSpotifySpdc ×2, statusUrl ×2, setSpdc ×1,
  SpotifyLoginDestination ×2, spotifyLoggedIn ×6) + « no redirect_uri »
  confirmé. Updater : 6/6 (shouldShowUpdateDialog ×6, releasesPageUrl ×1,
  fun checkForUpdate ×1, checkForGithubReleaseUpdate ×1, CheckForUpdateAt ×1,
  sharedViewModel.checkForUpdate ×1). Upstream-hotspots : vcs-url N7T0-OF ×2,
  UpdateRepositoryImpl géré en module absent. Landscape : 4/4
  (aspectRatio matchHeightConstraintsFirst ×1, commentaire SPACEKAI FIX ×1,
  isPhoneLandscape ×1, isShowNowPlaylistScreen && !isTabletLandscape ×1).
  Dynamic-color : dynamic(Dark|Light)ColorScheme ×2. Navigation : 7/7
  (isLiquidGlassEnabled ×5, LiquidGlassAppBottomNavigationBar ×2,
  isTranslucentBottomBar ×2, isTranslucentBackground ×1, showLabels
  = !hideNavLabel ×2, AppNavigationRail ×3, isPhoneLandscape ×3).
  Settings-UI : **14/14 sites allowlistés existent ET contiennent le pattern à
  la ligne exacte**. Icons : 4/4 fichiers verrouillés présents (SHA épinglés).
  Perf-keys : pattern attrape le réel (ModalBottomSheet:3111), les 3
  exceptions sont documentées dans l'en-tête et correspondent. Provider-arch :
  Mode A PASS, Mode B testé plus tôt (faux provider → FAIL, secret → FAIL).
  Résultat : 11/11 gates PASS sur ce snapshot, aucun ne passe en trompant.
- **Étape 7b du sync upstream (2026-08-26)** : `update-upstream.sh` vérifie
  maintenant que les hooks critiques ont **survécu** au merge, même sans
  marqueur de conflit (un merge propre peut perdre un hook si upstream
  restructure la zone) : signatures exactes sur App.kt (persistance :175,
  swipe :182, barre liquide :492), conveyor.conf (`vcs-url` N7T0-OF :56) et
  UpdateRepositoryImpl (SpaceKaiUpdateConfig). Module absent → « verify on the
  live repo » (skip, pas fail). Testé : état normal → 4 OK + 1 skip ;
  signature altérée → CRITICAL « LOST » ; restore → OK. Documenté dans
  UPSTREAM.md (7b) et dans la section Next steps du script.
- **Rapport de session consolidé (2026-08-26)** : `docs/SESSION-2026-08-26.md`
  (nouveau, propagé A_FILES) — la trace complète prête à joindre à la PR de
  release : 4 bugs corrigés (B1-B4), 3 trous comblés (G1-G3), 6 docs corrigés,
  claims du changelog vérifiés, note de release (21 gaps / 6 toggles / 0
  contradiction), plan WIRING-P0, état des 4 obstacles et commandes live dans
  l'ordre.
- **Plan de câblage P0 (2026-08-26)** : `docs/WIRING-P0.md` (nouveau,
  propagé A_FILES) — pour chacun des 6 toggles factices, le point d'insertion
  **vérifié** (fichier + ligne + contexte), le câblage minimal et le critère
  audit-features. Synthèse : `minimalisticNavigation` → App.kt:491-506
  (sélection des barres) ; `dynamicColor` → Theme.kt:120-128 (wallpaperScheme/
  seedColor) ; `landscapePlayer` → NowPlayingScreen.kt (branche paysage — le
  correctif artwork ~1036 est déjà inconditionnel, c'est un bugfix) ;
  `customPlayerInfo` → NowPlayingScreen.kt:2455-2475 (rangées d'info) ;
  `spotifySync` → LogInViewModel.kt:28-37 (saveSpotifySpdc) + live
  core/service/spotify ; `downloadWifiOnly` → **core/data (absent ici)** —
  seul câblage hors composeApp, à faire sur le live, avec la question de
  l'étendue du scan d'audit-features. Les raisons « Toggle décoratif » du
  FEATURE AUDIT référencent désormais le plan (12 occurrences). Ordre
  recommandé : NowPlayingScreen d'abord, core/data en dernier. Interdit :
  câbler un flag par une référence morte pour faire passer le gate.
- **Changelog 73 vérifié ligne à ligne (2026-08-26)** : les 7 lignes
  confrontées au code réel. La ligne « flags persisted / re-applied at
  startup » (la seule sans gate dédié) est **honnête** :
  `applyPersistedSpaceKaiFeatures` appelé à App.kt:175 (LaunchedEffect au
  démarrage), `mergePersistedSpaceKaiFeatures` lu dans SpaceKaiSettingsSection,
  écriture `spacekai_<flag>` TRUE/FALSE + re-issue configSpaceKai. **Trou
  comblé** : audit-features exemptait SpaceKai.kt (liste IGNORED) donc la
  suppression de l'appel de démarrage aurait été invisible — ajout d'un check
  de câblage au niveau couche (`applyPersistedSpaceKaiFeatures` ≥ 1 call site
  hors déclaration). Testé : PASS (App.kt), FAIL simulé (App.kt masqué),
  restore PASS. **Effet de bord corrigé** : la ligne « PASS: applyPersisted… »
  polluait la map FLAG de generate-feature-audit (même pattern `^  PASS:
  <Nom>`) — l'extraction n'accepte plus que les vrais flags déclarés dans
  SpaceKaiFeatures.kt ; 0 imposteur dans le rapport, verdicts inchangés.
- **Chasse aux références fantômes (2026-08-26)** : chaque chemin cité dans
  docs/workflows/scripts confronté à l'existence réelle. **2 vrais fantômes
  corrigés** : (1) `apply-release-pipeline.sh` copiait `scripts/build_and_sign_apk.sh`
  — le fichier est à la racine → la propagation le sautait **silencieusement**
  (`warn missing in source, skipped`) et le dépôt live gardait l'ancien script
  de build ; corrigé en `build_and_sign_apk.sh`, toutes les entrées A/B
  vérifiées existantes. (2) `docs/ci-cd.md` citait `CollapsibleSection.kt`
  comme pattern de référence — fichier inexistant ; remplacé par les vrais
  sites allowlistés (`FullWidthItems.kt:243/400`, `NowPlayingScreen.kt:1961`).
  Exemple de changelog périmé (`57.txt`) → placeholder `<VERSION_CODE>.txt`.
  Faux positifs écartés : `scripts/X.sh`/`audit-X.sh` (placeholders du
  commentaire de check-gate-parity), `release.yml` (décrit l'état du repo live,
  renommé `.disabled`), basenames de docs (résolus sous scripts/ et
  .github/workflows/).
- **Parité des gates verrouillée (2026-08-26)** : nouveau `check-gate-parity.sh`
  — extrait les invocations réelles (pas les mentions) des 3 chemins
  (`pre-release-report.sh`, `check-pre-tag.sh`, `release-nightly-check.yml`) et
  FAIL si un gate manque dans l'un d'eux. Les 11 gates code sont en parité.
  Intégré comme gate bloquant dans `pre-release-report.sh` (13e) et `check-pre-tag.sh`,
  et 11e audit warn-only du nightly. Testé : simulation de gate fantôme → FAIL
  avec « missing/extra », restauration → PASS. Au passage, corrigé une dérive
  documentaire réelle : `docs/ci-cd.md` disait « the 9 audits » alors que le
  nightly en exécute 10 depuis l'ajout d'`audit-provider-arch` (out10=) — mis à
  jour en 10 audits + parité ; « all 11 gates » → 12 code gates dans
  `ci-cd.md`/`publish-from-artifact.yml`/`PROVIDER-ARCHITECTURE.md`.

- **Préparation v2.0.0 (2026-08-26)** : la 1.9.0 n'a jamais été publiée (bloquée
  par les 6 toggles décoratifs). Le workspace devient **v2.0.0 / code 74** :
  `gradle/libs.versions.toml` (name→2.0.0, code→74), `SPACEKAI_VERSION=2.0.0`,
  nouveau `fastlane/.../changelogs/74.txt` (header EXACTEMENT `SpaceKai v2.0.0`
  pour le check de marque), `docs/RELEASE-MESSAGE-v2.0.0.md` (généré depuis le
  pipeline réel : corps = 74.txt, bloc honnêteté 21/21 gap lines identiques au
  rapport frais, texte Play Store = 74.txt), `apply-release-pipeline.sh` A_FILES
  → `RELEASE-MESSAGE-v2.0.0.md`, v1.9.0 supprimé. Le bump code→74 est obligatoire
  pour que le pipeline copie le BON changelog (SRC_CODE=74 → 74.txt) — resté à
  73, il aurait propagé les notes v1.9.0 dans la release v2.0.0. Validation :
  header check PASS, 26/26 syntaxe, parity PASS, test-gates PASS, zéro résidu
  1.9.0 dans les fichiers courants (hors historique SESSION/généré).
- **Répétition générale — clone du live (2026-08-26)** : git présent et réseau
  GitHub OK ; cloné `N7T0-OF/Sankamusic` (main ET dev, shallow, read-only).
  **Découvertes factuelles** : (a) `main`=`1930c9d` (v1.8.0/71, PAS de pipeline), `dev`
  =`632ec2f` (v1.9.0/73, pipeline staged) — les branches ont DIVERGÉ (l'ancien
  « main=dev » est périmé) ; le release se fait depuis `dev`. (b) `dev` a un
  `apply-release-pipeline.sh` ANCIEN (A_FILES=15 vs 26, `<scripts/build_and_sign_apk.sh>`
  path mort) — le dry-run ne propage que 15 fichiers + le nouveau script,
  pas les 21 nouveaux (audits, test-gates, publish*, generate-feature-audit,
  WIRING-P0/SESSION/PROVIDER/FEATURE-AUDIT) : il faut propager EN 2 FOIS (1er
  run ancien → installe le nouveau script, 2e run → emporte les 21). (c) dry-run
  `Version: 2.0.0 (code → 74)` + `changelog → .../74.txt` + C1 `2.0.0/74` — la
  prépa v2.0.0 du workspace est **validée de bout en bout** contre le live.  (d) live `dev` : `spotifySync` DÉJÀ retiré (0 occurrence), mais les 5 autres
  toggles toujours décoratifs (0 réf. réelle) et `audit-features.sh` ABSENT —
  le gate qui bloque n'est pas encore déployé sur le live. → le VRAI bloqueur
  à deux volets : propager le pipeline complet PUIS traiter les 5 toggles.
- **Exécution de la propagation sur clone — bug réel confirmé (2026-08-26)** :
  la procédure « 2 passes » s'est avérée plus subtile que le dry-run ne le
  laissait croire. Pass 1 réel (ancien script staged + `--yes`) → catégorie A
  copie 15 fichiers DONT `scripts/apply-release-pipeline.sh` (le nouveau), puis
  bash continue de parser le FICHIER MODIFIE (pas une continuation du programme
  en cours) → **`line 180: syntax error near unexpected token `('` (exit 2)**, la
  version reste 1.9.0/73 et 74.txt n'est pas créé. Pass 2 (relancer le NOUVEAU
  script désormais en place) → exit 0, `Version: 2.0.0 (code → 74)`, 21/21
  ex-manquants copiés, C1 2.0.0/74, changelog → 74.txt, header PASS. Leçon :
  un crash au pass 1 n'est pas une erreur à corriger — c'est le comportement
  attendu de l'auto-copie ; on RELANCE simplement (le nouveau script est déjà
  installé), pas de recommencement à blanc.
- **Correction du crash d'auto-copie — propagation à UNE passe (2026-08-26)** :
  le bug est maintenant FIXÉ dans le workspace `scripts/apply-release-pipeline.sh`.
  Deux changements : (a) `scripts/apply-release-pipeline.sh` est RETIRÉ de sa
  propre liste A_FILES (il ne se copiait plus lui-même en plein run) ; (b) une
  garde d'auto-install en fin de script (`if $DRY / elif cmp -s self source /
  else cp + exit 0`) dans UN SEUL bloc composé — bash analyse tout le `if..fi`
  avant d'exécuter, donc le `cp`+`exit 0` ne relit jamais le fichier modifié.
  Vérifié : repro minimal de l'auto-copy → crash reproduit ; fix pattern → exit 0
  propre ; run réel one-pass sur clone → exit 0, 21/21 ex-manquants copiés,
  version 2.0.0/74, changelog 74.txt, self « matches source — no upgrade pending ».
  Un test annexe avec `echo >>` sur le script (SANS `\n` final) a momentanément
  faussé un run (`fi#...` → « expected fi ») : ce n'était pas la garde mais le
  manque de retour-ligne final — corrigé au passage (`printf '\n'`). Le nouveau
  script propage désormais TOUT en une passe fiable ; l'ancien script du live
  crash encore au pass 1 (inchangeable à distance), mais dès que le nouveau est
  installé, les passes suivantes sont propres et uniques.

Étapes restantes (ordre réel, vérifié par clone du live 2026-08-26) :
1. **Propager le pipeline en 1-2 passes (script corrigé, procédure exécutée et prouvée
   sur clone 2026-08-26)** : le script staged de `dev` a une A_FILES ANCIENNE (15 vs
   26) et l'ancienne auto-copie crashait (bash parse le fichier qu'il vient de
   remplacer → `line 180: syntax error`). Le WORKSPACE corrigé a retiré `apply-release-pipeline.sh`
   de sa propre A_FILES et installe le self dans un garde final (un seul bloc `if..fi`),
   donc **une fois le nouveau script installé, la propagation est UNE passe fiable**
   (vérifié : run réel clone → exit 0, 21/21 ex-manquants, version 2.0.0/74, changelog
   74.txt, header PASS). Sur le live : le vieux script crashra au 1er run APRÈS avoir
   déposé le nouveau ; on RELANCE simplement (le nouveau est déjà installé, plus de crash).
   Le run résout `Version: 2.0.0 (code → 74)` et copie `changelogs/74.txt`.
2. câbler les 5 toggles factices restants (ou retirer) → `audit-features.sh` vert
   (vérifié sur le live : `spotifySync` DÉJÀ retiré, `minimalisticNavigation` /
   `dynamicColor` / `landscapePlayer` / `downloadWifiOnly` / `customPlayerInfo`
   toujours 0 référence réelle)
3. `./scripts/release.sh /path/workspace --dry-run` depuis le repo live, puis sans `--dry-run`
   (bump → 2.0.0/74 → commit + tag + push)
4. CI construit → draft `SpaceKai v2.0.0` → **publish-draft.sh** (assets re-vérifiés + bloc d'honnêteté frais → Publish)
5. `verify-release.yml` vérifie checksums + versionCode 74 > max historique

⚠️ Toujours : draft → assets → publish (jamais l'inverse) ; ne jamais supprimer
une release publiée ; le version-code doit battre **tout** l'historique (71).

## 📄 Docs

`RELEASE.md` (process complet + FAQ « Application non installée ») ·
`docs/ci-cd.md` (incl. **UI audit checklist pre-release** — 3 axes : animations de
 taille dans items lazy, AnimatedVisibility par défaut, maxLines) · `docs/UPSTREAM.md` ·
`docs/SPACEKAI-ARCHITECTURE.md` · `docs/release-notes-template.md` ·
`docs/FEATURE-AUDIT.md` (le référentiel manuel) + `docs/FEATURE-AUDIT-REPORT.md`
(généré par `generate-feature-audit.sh` à chaque release, gitignored).
