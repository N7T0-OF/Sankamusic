# RUNBOOK — Publication v2.0.0 (N7T0-OF/Sankamusic, branche `dev`)

> Runbook opérationnel de bout en bout : **propagation du pipeline (1-2 passes)**,
> **câblage des 5 toggles**, **publish.sh**, **CI**, **publish-draft.sh**.
> Chaque étape : commande exacte, critère de passage, points de décision.
>
> État de référence (vérifié 2026-08-26, clone du live) :
> - workspace = **v2.0.0 / code 74** (source du pipeline, `$WS` ci-dessous) ;
> - live `dev` = `632ec2f` (v1.9.0/73, pipeline **staged** avec `apply-release-pipeline.sh`
>   ANCIEN — A_FILES 15 vs 26) ; live `main` = `1930c9d` (v1.8.0/71, PAS de pipeline) ;
> - la release se fait depuis **`dev`** (branche par défaut du live) ;
> - `spotifySync` **déjà retiré** sur le live (0 occurrence) ; les **5 autres toggles**
>   étaient décoratifs — **tous réglés dans ce workspace** (voir §2) :
>   `minimalisticNavigation`, `dynamicColor`, `customPlayerInfo` câblés,
>   `landscapePlayer` + `downloadWifiOnly` retirés (WIRING-P0 option B) →
>   `audit-features.sh` = 0 FAIL ;
> - plancher anti-downgrade : `MIN_VERSION_CODE = 72` (v1.8.1) → le code 74 bat tout
>   l'historique.

Sources de vérité (vérifiées contre le code de ce workspace) : `RELEASE.md` ·
`docs/WIRING-P0.md` · `docs/ci-cd.md` · `STATUS.md` ·
`scripts/apply-release-pipeline.sh` · `scripts/publish.sh` ·
`scripts/check-pre-tag.sh` · `scripts/release.sh` · `scripts/release-publish.sh` ·
`scripts/test-gates.sh` · `scripts/pre-release-report.sh` ·
`scripts/audit-features.sh` · `scripts/verify-release.sh` ·
`scripts/publish-draft.sh`.

---

## 0. Prérequis et pre-flight

| Élément | Valeur |
| --- | --- |
| Repo | `N7T0-OF/Sankamusic` |
| Branche de release | `dev` (défaut) |
| Version cible | **v2.0.0** / versionCode **74** |
| Plancher anti-downgrade | 72 → 74 > 72 ✅ installable en mise à jour depuis tout |
| Bloqueur actuel | **0 toggle décoratif** — 5 flags câblés (`customNavigation`, `minimalisticNavigation`, `dynamicColor`, `haptics`, `customPlayerInfo`), `landscapePlayer`/`spotifySync`/`downloadWifiOnly` retirés ; `audit-features.sh` → exit 0 ; seul blocage restant : `gh auth login` (check-pre-tag §2) |
| Workspace (source) | checkout v2.0.0 — chemin noté `$WS` |

```bash
# Sur la machine de release :
export WS=/chemin/vers/workspace-v2.0.0        # la SOURCE du pipeline
cd /chemin/vers/live/Sankamusic                # le dépôt LIVE, branche dev
git status --porcelain                         # arbre propre (sinon : committer/stasher d'abord)
git fetch origin && git pull origin dev        # à jour
gh auth status                                 # gh authentifié (exigé par check-pre-tag)
```

**Pre-flight recommandé** (non bloquant) :
- nightly de la veille vert : `release-nightly-check.yml` (03:00 UTC, warn-only —
  actionlint + icônes + **10 audits + parité** + test-gates + FEATURE AUDIT
  régénéré) — un merge upstream qui casse un câblage / un hotspot / un gate est
  signalé avant la release ;
- `./scripts/apply-release-pipeline.sh "$WS" --dry-run` (voir étape 1, « pass 0 »).

> ⚠️ **Règle absolue du dépôt** : draft → assets → publish, jamais l'inverse ;
> ne **jamais** supprimer une release publiée (le tag est brûlé définitivement —
> incident réel v1.7.4 → v1.7.4-1).

---

## 1. Propagation du pipeline — 1-2 passes

Objectif : amener sur le live `dev` tout le pipeline v2.0.0 (26 fichiers A +
2 fichiers B + éditions C + changelog `74.txt`), **sans toucher aux sources de
features** (`composeApp/src`, `core/`, `desktopApp/src`, `androidApp/src` =
catégorie D, jamais copiés).

### Commandes

```bash
cd /chemin/vers/live/Sankamusic                # branche dev

# Pass 0 — prévisualisation (aucun changement) : doit afficher
#   Version: 2.0.0 (code → 74; floor was 72, live was 73)
#   changelog → fastlane/metadata/android/en-US/changelogs/74.txt
#   C1 2.0.0/74
./scripts/apply-release-pipeline.sh "$WS" --dry-run

# Pass 1 — exécution avec l'ANCIEN script staged sur le live
# (A_FILES 15). Comportement ATTENDU en fin de run : crash
#   line 180: syntax error near unexpected token `('  (exit 2)
# après avoir déposé le NOUVEAU script. Ce n'est PAS une erreur à corriger.
./scripts/apply-release-pipeline.sh "$WS" --yes

# Pass 2 — relance : le nouveau script est en place, la propagation est
# UNE passe fiable (auto-copie retirée de A_FILES + garde d'auto-install
# dans un seul bloc if..fi). Doit se terminer exit 0.
./scripts/apply-release-pipeline.sh "$WS" --yes
```

> Si le live possède DÉJÀ le nouveau script (procédure déjà exécutée une fois),
> le pass 1 ne crash pas et **une seule passe suffit** — d'où « 1-2 passes ».

### Critères de passage

**Pass 1 (ancien script)** :
- le crash `line 180: syntax error` (exit 2) **en fin de run** est le comportement
  attendu de l'auto-copie — on ne corrige rien, on relance ;
- vérifier que le nouveau script est bien installé avant de relancer :
  `grep -c "A_FILES=(" scripts/apply-release-pipeline.sh` doit montrer la liste
  `26` (ou présence du bloc « guarded self-install » en fin de fichier).

**Pass 2 (nouveau script)** — tous ces points :
- `exit 0` ;
- `Version: 2.0.0 (code → 74; floor was 72, live was 73)` — le code est calculé
  `max(code live, 72) + 1`, **jamais** copié depuis le workspace ;
- `changelog → fastlane/metadata/android/en-US/changelogs/74.txt` (sélection par
  code numérique `SRC_CODE=74`, jamais `ls | head -1`) ;
- `Done. scripts/apply-release-pipeline.sh matches the source — no upgrade pending.` ;
- 21/21 fichiers ex-manquants copiés (audits, test-gates, publish*, WIRING-P0,
  SESSION, PROVIDER-ARCHITECTURE, FEATURE-AUDIT, RELEASE-MESSAGE-v2.0.0…) ;
- `git status --porcelain` : nouveaux scripts/docs + `android-release.yml` et
  `build_and_sign_apk.sh` remplacés (`.bak` créés) + `release.yml.disabled`
  (l'ancien workflow du live désactivé — anti double-déclenchement) +
  `gradle/libs.versions.toml` édité (2.0.0/74) + `conveyor.conf` vcs-url →
  `N7T0-OF/Sankamusic` + `fastlane/.../changelogs/74.txt` ;
- `git diff --stat` : **aucun** changement sous `composeApp/src`, `core/`,
  `desktopApp/src`, `androidApp/src` (catégorie D) ;
- `bash scripts/verify-icons.sh` → PASS (icônes verrouillées).

### Points de décision

1. **`--version-name`** : ne pas l'utiliser — le workspace est déjà `2.0.0`/`74` ;
   un override forcerait une incohérence avec le changelog `74.txt`.
2. **Bloc `splits { abi {} }`** : le script affiche le rappel manuel, mais
   `release.sh` (étape 3) le supprime automatiquement (étape 2/5, backup `.bak`).
   Décision : laisser `release.sh` le faire (recommandé) — la CI échoue de toute
   façon bruyamment si plus d'un APK est produit.
3. **`release.yml.disabled`** : vérifier le backup avant de le supprimer ;
   le garder au moins jusqu'à la fin de la release.
4. **Si le pass 2 crash encore** : vérifier le retour-ligne final du script
   (`printf '\n'` manquant → « expected fi ») ; ne pas recommencer à blanc.

---

## 2. Câblage des 5 toggles (sur le live, dans `composeApp/src` + `core/data`)

Le câblage se fait **directement sur le live** (catégorie D — jamais propagé).
Les guides détaillés (imports + snippets + repères de lignes) sont dans
`docs/WIRING-P0.md` ; les numéros de ligne du snapshot sont à re-vérifier sur le live.

```bash
cd /chemin/vers/live/Sankamusic
bash scripts/audit-features.sh    # état initial attendu sur le LIVE : 5 FAIL
                                 # (spotifySync déjà retiré sur le live ; le workspace
                                 #  seul en affiche 6 — les 6 incluent spotifySync)
```

### Tableau des 5 câblages (ordre recommandé : impact croissant, risque décroissant)

| # | Toggle | Fichiers (live) | Câblage minimal | Critère audit | Test visuel obligatoire |
| --- | --- | --- | --- | --- | --- |
| 1 | `customPlayerInfo` | `NowPlayingScreen.kt` | ✅ **FAIT dans ce workspace** : `showPlayerInfo` (flag) ; OFF masque la carte paroles entière (AnimatedVisibility `&& showPlayerInfo`) + le bloc Description (`if (showPlayerInfo)`) | 0 FAIL (déjà vert) | Toggle OFF → description/paroles disparaissent ; ON → réapparaissent ; redémarrage → état persiste |
| 2 | `minimalisticNavigation` | `App.kt` + `AppBottomNavigationBar.kt` (**les deux** listes) + LiquidGlass (expect + android + jvm) | ✅ **FAIT dans ce workspace** : `minimalisticNav` (flag) passé aux deux barres ; paramètre `minimalistic` retire MixForYou dans les DEUX listes de chaque barre, tandis qu'Analytics suit `showAnalyticsTab` + LaunchedEffect repli | 0 FAIL (déjà vert) | Barre compacte (Home/Analytics/Library/Search lorsque le suivi local est actif) ; les deux styles + le rail paysage réagissent |
| 3 | `landscapePlayer` | ~~NowPlayingScreen.kt~~ | ✅ **RETIRÉ dans ce workspace** (WIRING-P0 option B) : le câblage précédent mettait le correctif artwork sous le flag, ce qui cassait `audit-landscape-player.sh` (correctif doit rester **inconditionnel**). Correctif restauré (`matchHeightConstraintsFirst = true` + commentaire SPACEKAI FIX), flag + rangée + persistance supprimés, ligne générateur repointée sur la chaîne réelle | 0 FAIL (déjà vert) | n/a — toggle supprimé ; le correctif artwork paysage reste appliqué dans les deux cas |
| 4 | `dynamicColor` | `ui/theme/Theme.kt` (construction du `colorScheme`) | ✅ **FAIT dans ce workspace** : `spaceKaiDynamicColor` (flag) → `isAmoled = if (flag) false else isDark` — la correction du « fond sombre épinglé au noir » pilotée par le flag | 0 FAIL (déjà vert) | Thème sombre + ON → surfaces non-noires ; OFF → base inchangée ; wallpaper/seed intacts |
| 5 | `downloadWifiOnly` | ~~core/data~~ | ✅ **RETIRÉ dans ce workspace** (repli B) : déclaration + rangée de réglages + persistance + ligne `wifi-only` de `generate-feature-audit.sh` supprimées | 0 FAIL (déjà vert) | n/a — toggle supprimé |

> **⚠️ Contradiction de claim résolue (2026-08-29, ce workspace — option b)** :
> `check-pre-tag.sh` échouait sur **1 contradiction de revendication** — la
> **ligne 4 de `74.txt`** (« …**landscape player**… is carried into this major
> version ») était lue par l'audit comme un claim « option accomplie » alors que
> « Player paysage = même player responsive (layout-only) » est
> **PARTIALLY IMPLEMENTED** (flag `landscapePlayer` décoratif). Résolue par la
> réécriture de la ligne 4 (`…; the landscape-player layout is still
> work-in-progress (see "Known / not done")`).**Vérifié : l'audit régénéré
> affiche 0 contradiction.** Si une telle contradiction réapparaît à l'avenir :
> soit (a) câbler `landscapePlayer` (guide WIRING-P0) puis régénérer, soit (b)
> réécrire la ligne du changelog concernée — à faire **avant** `publish.sh`.

### Critères de passage finaux

```bash
bash scripts/audit-features.sh    # → RESULT: no decorative SpaceKai flags (exit 0)
bash scripts/generate-feature-audit.sh && bash scripts/check-pre-tag.sh
                                  # → RESULT: ALL CHECKS PASSED (plus aucune raison de contenu)
```

### Points de décision

1. **Câbler vs retirer** (arbitrages tranchés dans `WIRING-P0.md`) :
   - `spotifySync` → **retiré dans ce workspace** (déclaration + rangée de réglages +
     persistance) — le live l'avait déjà retiré ;
   - `downloadWifiOnly` → **retiré dans ce workspace** (option B/repli : retirer le flag
     **et** la ligne `wifi-only` de `scripts/generate-feature-audit.sh`, déjà fait → 0
     NOT IMPLEMENTED fantôme) ;
   - `landscapePlayer` → **retiré dans ce workspace** (WIRING-P0 option B). Le
     câblage précédent (flag → `matchHeightConstraintsFirst`) cassait le gate
     `audit-landscape-player.sh`, qui exige le correctif artwork **inconditionnel**
     (WIRING-P0 : « le correctif artwork reste inconditionnel »). Correctif restauré
     à `matchHeightConstraintsFirst = true` + commentaire SPACEKAI FIX ; flag,
     rangée de réglages et persistance supprimés ; ligne `landscape-player` de
     `generate-feature-audit.sh` repointée sur la chaîne réelle (SPACEKAI FIX /
     matchHeightConstraintsFirst) → reste PARTIALLY (branche wDP>hDP absente), pas
     de NOT IMPLEMENTED fantôme.
   - `minimalisticNavigation` → **câblé dans ce workspace** : `minimalisticNav` (flag)
     passé aux deux barres ; paramètre `minimalistic` retire `MixForYou` dans les
     deux listes de chaque barre (barre + rail, liquide + translucide), tandis
     qu'Analytics suit `showAnalyticsTab` ;
   - `dynamicColor` → **câblé dans ce workspace** : `spaceKaiDynamicColor` (flag) →
     `isAmoled = if (flag) false else isDark` dans Theme.kt (wallpaper/seed intacts) ;
   - `customPlayerInfo` → **câblé dans ce workspace** : `showPlayerInfo` (flag) masque
     la carte paroles (AnimatedVisibility) + le bloc Description (NowPlayingScreen.kt).
   - **Résultat** : ce workspace affiche **0 FAIL** — `audit-features.sh` → RESULT:
     no decorative SpaceKai flags (exit 0) ; `check-pre-tag.sh` ne garde que
     **1 FAIL : gh not authenticated** (seul obstacle restant, à faire par
     l'humain : `gh auth login`).
2. **Jamais de référence morte** : chaque `if (flag)` doit piloter un rendu/une
   branche visible. Le gate et le rapport ne distinguent pas un stub d'un vrai
   câblage (le générateur récompense même le stub — simulation C de test-gates) —
   **le test visuel est la vraie garde**, ne pas le sauter.
3. **Étendue du scan** (tranché) : l'état du flag remonte via composeApp ;
   `audit-features.sh` ne scanne que `composeApp/src` (SCOPE DECISION dans
   l'en-tête du script) ; la règle est gardée par `audit-provider-arch.sh`
   (Layer integrity — FAIL si un module core importe `spacekai` ou `.ui.`).
4. Après chaque toggle : `bash scripts/audit-features.sh` doit montrer exactement
   le FAIL attendu (5 → 4 → 3 → 2 → 1 → 0) — un écart = vérifier qu'aucun autre
   toggle n'a bougé.

---

## 3. `publish.sh` — gates + tag (UNE commande, depuis le live)

```bash
cd /chemin/vers/live/Sankamusic
./scripts/publish.sh "$WS"              # avec confirmation
./scripts/publish.sh "$WS" --yes        # sans prompt (runs répétés uniquement)
```

Séquence interne (s'arrête à la première erreur, rien n'est poussé avant confirmation) :

| Étape | Commande interne | Critère de passage |
| --- | --- | --- |
| 1/4 | `scripts/check-pre-tag.sh` | `RESULT: ALL CHECKS PASSED` (git + origin + gh + audit-features vert + 0 contradiction changelog/audit + header `SpaceKai v2.0.0` + **12 code gates** (dont `check-gate-parity`) + 0 BROKEN + version lisible) |
| 2/4 | `scripts/test-gates.sh` | exit 0 — régresse les gates : **12 entrées de sortie** (11 gates + audit-features dérivé) + **21 paires (fichier\|pattern)** toutes vivantes + **3 simulations** (A spotifySync, B downloadWifiOnly, C 4 toggles) |
| 3/4 | `scripts/release.sh "$WS" --dry-run` | `DRY RUN complete` + `Version: 2.0.0 (code → 74)` + `changelog → .../74.txt` — aucun changement |
| 4/4 | confirmation `y/N` puis `scripts/release.sh "$WS"` | message `PUBLISHED. CI now builds...` + `Next step: ./scripts/publish-draft.sh [--yes]` |

`release.sh` enchaîne : apply pipeline (déjà fait, idempotent) → suppression
automatique du bloc `splits {}` (backup `.bak`) → `verify-icons.sh` →
`release-publish.sh` (commit + tag `v2.0.0` + push de la **branche courante**
(`dev`) + du tag) → rappel du draft.

`release-publish.sh` re-vérifie lui-même (avant de commit/tag/push) :
- le tag `v2.0.0` == `version-name` (miroir de la règle `validate-tag` de la CI) ;
- `version-code` (74) **strictement > `MIN_VERSION_CODE=72`** (garde anti-downgrade
  locale, même plancher que `apply-release-pipeline.sh` / `verify-release.sh`) ;
- le tag n'existe pas déjà localement ;
- icônes verrouillées (`verify-icons.sh`).

### Points de décision

- Le prompt `y/N` est **le** point d'arrêt final : après `y`, le tag `v2.0.0` est
  poussé et la CI démarre — il n'y a plus de retour en arrière propre (un tag
  poussé avec une release publiée est brûlé).
- L'APK n'est **pas** vérifié ici (c'est la CI qui le construit après le tag) —
  c'est voulu : les gates APK (1 seul APK, signature, variante release, icônes,
  parité, FEATURE AUDIT) tournent dans `pre-release-report.sh` côté CI.

---

## 4. CI — build, gates, draft (automatique après le push du tag)

Déclenché par le tag `v2.0.0` (workflow `android-release.yml`, `tags: v*`) :

| Job | Critère de passage |
| --- | --- |
| `validate-tag` | tag == `version-name` (2.0.0) ; versionCode 74 **strictement > max sur TOUS les tags** (scan complet, autorité anti-downgrade) ; icônes verrouillées |
| Build | **exactement 1 APK** `SpaceKai-v2.0.0.apk` (universel, signé) + packages desktop (AppImage, DMG, `.msix`/installeur zip) + `SHA256SUMS.txt` |
| `create-github-release` | notes = `74.txt` (copié) + bloc honnêteté injecté par `append-feature-audit-gaps.sh` (fallback : auto-génération depuis les commits si changelog absent — warn) ; `pre-release-report.sh` **PASS** (ZERO FALSE POSITIVE — aucun toggle décoratif — + **12 code gates** (dont `check-gate-parity`) + FEATURE AUDIT sans BROKEN) ; **tag stable → DRAFT** « SpaceKai v2.0.0 » avec tous les assets attachés dans le même `gh release create --draft` |
| `playstore-publish.yml` | (tags stables seulement) upload de l'APK sur le track **internal** avec les changelogs par locale |
| Discord | webhook optionnel — non bloquant |

Détails des checks de `pre-release-report.sh` (source unique de publication —
appelé par `android-release.yml` ET `publish-from-artifact.yml`) :
- **Android** : 1 seul APK, nom sans `debug/unsigned/arm64/armv7/x86/foss/universal`,
  signature `apksigner` (warn si absent) ;
- **Desktop** : AppImage + DMG + installeur Windows (zip `windows-installer` ou `.msix`)
  — warn-only (release APK-only autorisée) ;
- **Upstream** : version SimpMusic de base rapportée (warn hors clone) ;
- **Hotspots upstream** : `audit-upstream-hotspots.sh` (conveyor vcs-url + update checker) ;
- **ZERO FALSE POSITIVE** : `audit-features.sh` (aucun toggle décoratif) ;
- **UI** : `audit-settings-ui.sh` (pas d'animation de taille dans les items lazy) ;
- **Spotify** : `audit-spotify-flow.sh` (chaîne sp_dc + redirect_uri cohérent) ;
- **Paysage** : `audit-landscape-player.sh` (correctif artwork + branchement) ;
- **Mise à jour** : `audit-updater-flow.sh` (chaîne détection + URL SpaceKai) ;
- **Navigation** : `audit-navigation.sh` (styles + hide-text + rail paysage) ;
- **Dynamic Color** : `audit-dynamic-color.sh` (capacité palette + seed) ;
- **Providers** : `audit-provider-arch.sh` (design-first, aucun secret dans l'APK) ;
- **Icônes verrouillées** : `verify-icons.sh` ;
- **Perf** : `verify-perf-keys.sh` (clés lazy stables) ;
- **Parité des gates** : `check-gate-parity.sh` (release / pre-tag / nightly) ;
- **FEATURE AUDIT** : `generate-feature-audit.sh` → 0 BROKEN (bloquant) ;
  NOT IMPLEMENTED + PARTIALLY + contradictions = warn (doivent figurer dans la note) ;
- **Header du changelog** : `SpaceKai v2.0.0` (warn).

### Points de décision

- **Optionnel avant le vrai tag** : lancer le workflow manuellement avec la case
  `dry_run` (Actions tab) — build + notes + tous les checks, sans `gh release create`.
- **Gate FAIL côté CI** : la release est bloquée (draft non créé) — corriger sur
  `dev`, re-pousser le tag (ou re-cut si nécessaire) ; **aucun chemin de
  publication ne contourne les gates**.
- **Draft incomplet** (upload raté) : **laisser le draft en place**, inspecter,
  corriger, relancer — ne le supprimer que tant qu'il est draft ; ne **jamais**
  créer la release publiée puis ajouter des assets (`422 Cannot upload assets to
  an immutable release`).
- **Tag brûlé** (scénario de catastrophe) : re-cut `v2.0.0-1` — le version-code
  reste 74, l'APK `SpaceKai-v2.0.0.apk` inchangé.

---

## 5. `publish-draft.sh` — publication publique (UNE commande, après la CI)

```bash
cd /chemin/vers/live/Sankamusic
./scripts/publish-draft.sh --dry-run     # optionnel : montre ce qui serait fait
./scripts/publish-draft.sh               # avec confirmation
./scripts/publish-draft.sh --yes         # sans prompt
```

Séquence interne (vérifiée contre le script) :
1. résout le tag depuis `libs.versions.toml` → `v2.0.0` ;
2. refuse proprement si `gh` absent / release inexistante (**exit 1**) ou déjà
   publiée / pré-release CI (**exit 2**) ;
3. **re-télécharge les assets** et les vérifie avec `verify-release.sh`
   (mêmes checks que `verify-release.yml` : 1 seul APK `SpaceKai-v2.0.0.apk`,
   nom de variante release, `SHA256SUMS.txt` présent et **chaque checksum
   vérifié**, versionCode > `MIN_VERSION_CODE=72`, desktop warn-only) ;
4. exige le bloc « Connu / non terminé » dans la note : présence **ET fraîcheur**
   — la comparaison porte sur les gap lines du body (bullets `- **`) vs un
   audit régénéré localement (`generate-feature-audit.sh`) : **égalité exigée**
   (21 gap lines à l'état pré-câblage — le nombre suit l'audit, il évoluera
   après le câblage) ; différence → refus `HONESTY BLOCK IS STALE` (exit 1) ;
5. confirmation puis `gh release publish`.

`verify-release.sh` (le MÊME fichier appelé par `verify-release.yml` et par
`publish-draft.sh`) vérifie : 1 seul APK `SpaceKai-v2.0.0.apk` (sans
debug/unsigned/aligned/ABI/foss) ; présence de `SHA256SUMS.txt` et chaque
checksum correspond au fichier téléchargé ; versionCode de l'APK > 72 (garde
anti-downgrade — même `MIN_VERSION_CODE` que `apply-release-pipeline.sh` et
`release-publish.sh` : les **3 constantes doivent être mises à jour ensemble
quand un code plus haut est publié**) ; packages desktop = warn-only.

### Critères de passage

- **exit 0** = publiée ; vérifier ensuite `verify-release.yml` vert
  (re-vérification post-publication : checksums + versionCode) ;
- **exit 1** = bloquée : corriger (assets, note, audit) et relancer ;
- **exit 2** = rien à faire (déjà publiée, ou tag pré-release publié par la CI).

### Points de décision

- Le bloc d'honnêteté **doit** être présent ET à jour — ne jamais publier une
  note qui cache du non-fait (ZERO FALSE POSITIVE) ;
- après publication : **promotion Play** interne → production = décision
  **manuelle et consciente** (UI Play Console ou workflow « Promote Play Store to
  Production ») — jamais automatique.

---

## 6. Post-publication (hygiène pour la prochaine release)

```bash
# 1. Vérifier la re-vérification CI (verify-release.yml) : verte
# 2. Mettre à jour le plancher anti-downgrade : 72 → 74 dans les 3 constantes
#    (apply-release-pipeline.sh, release-publish.sh, verify-release.sh)
# 3. Mettre à jour STATUS.md (table des releases : v2.0.0 → 74 = MAX)
# 4. Nettoyer docs/RELEASE-MESSAGE-v2.0.0.md (« Règles de publication » : "(73 > 72)"
#    → "(74 > 72)") — ligne périmée de la tentative v1.9.0
```

---

## Synthèse des commandes (ordre réel, depuis le live `dev`)

```bash
# 1. Propagation (2 passes la première fois, 1 ensuite)
./scripts/apply-release-pipeline.sh "$WS" --dry-run
./scripts/apply-release-pipeline.sh "$WS" --yes     # pass 1 : crash attendu en fin de run
./scripts/apply-release-pipeline.sh "$WS" --yes     # pass 2 : exit 0, Version 2.0.0 (74)

# 2. Câblage des 5 toggles (guides WIRING-P0.md) + test visuel de chacun
bash scripts/audit-features.sh                      # 5 FAIL → ... → 0 FAIL

# 3. Gates + tag
./scripts/publish.sh "$WS"                          # ALL CHECKS PASSED → y

# 4. CI : build + pre-release-report PASS → draft « SpaceKai v2.0.0 »

# 5. Publication
./scripts/publish-draft.sh                          # exit 0 = publiée

# 6. Promotion Play (manuelle) + hygiène post-release (MIN_VERSION_CODE → 74)
```

---

## 7. Retour d'expérience — leçons de la release v2.0.0 (2026-08-29)

### La CI est la première compilation du contenu de release

Le contenu v2.0.0 n'avait **jamais été compilé** (pas de JDK sur la machine
source). À chaque push de tag, la CI (`android-release.yml` → `assembleRelease`)
était donc la toute première compile, et a révélé **4 vagues de bugs** qu'une
compilation locale pré-tag aurait attrapées en quelques minutes :

1. **Flag périmé dans le workflow** : `build_and_sign_apk.sh` ne gère plus
   `--single` (le mode APK universel est devenu le défaut), mais le workflow le
   passait encore → le `*)` du parseur appelle `print_usage` = **exit 0 silencieux**,
   aucun APK construit, upload vide, artefact absent. Symptôme violent, cause triviale.
2. **9 erreurs Kotlin dans `composeApp`** (jamais compilé) : import manquant de
   `applyPersistedSpaceKaiFeatures` ; lambdas terminales `){ klass -> }` alors que
   `reloadDestinationIfNeeded` n'est PAS le dernier paramètre (→ « too many
   arguments » sur `onSwipeToPrevious`) ; `minimalistic` utilisé dans
   `AppNavigationRail` sans être déclaré ; `AnimatedVisibility` déplacé dans un
   `Box` devient le `ColumnScope.AnimatedVisibility` (→ FQ
   `androidx.compose.animation.AnimatedVisibility`, voir MiniPlayer) ;
   `SongEntity.thumbnails` est `String?` (pas une liste → pas de
   `.lastOrNull()?.url`) ; `LocalHapticFeedback.current` lu dans un `onClick`
   non-composable (→ hoist dans le composable).
3. **Garde Sentry supprimé** : v1.9.0 avait `autoUploadProguardMapping =
   !token.isNullOrEmpty()` ; v2.0.0 l'a forcé à `true` → échec 401 de
   `:androidApp:uploadSentryProguardMappingsRelease` quand il n'y a pas de token.
4. **Décalage de ligne de l'allowlist** `audit-settings-ui.sh` : le hoist des
   haptics (+3 lignes dans `NowPlayingScreen.kt`) a déplacé le bloc « Canvas
   subtitle » de 1969-1970 à 1972-1973 → gate settings-overlap + statut BROKEN
   dérivé. **Toute  modification de lignes en amont re-casse l'allowlist.**

### Actions correctives à verrouiller pour les prochaines releases

- **Compilation pré-tag obligatoire** : avant `publish.sh`, faire une passe
  `./gradlew composeApp:compileAndroidMain androidApp:compileReleaseKotlin` (ou
  un compile de validation CI) sur le contenu de release — ne jamais pousser un
  tag dont le code n'a pas compilé au moins une fois.
- **Régénérer les audits après toute édition de code** (lignes, déplacements) :
  `audit-settings-ui.sh` et le rapport d'audit sont sensibles aux numéros de ligne.
- **Ne pas « moderniser » un garde documenté** sans porter son commentaire :
  le commentaire v1.9.0 sur `autoUploadProguardMapping` décrivait EXACTEMENT
  la 401 évitable.
- **Infra publication (2026-08-29)** : le gh local n'a pas `gh release publish`
  → utiliser `gh release edit <tag> --draft=false`. Et gh déduit le repo du
  remote git : pour publier depuis un clone dont `origin` pointe ailleurs,
  forcer `GH_REPO=<owner>/<repo>`.
- **Modes `100755`** : sur poste Windows (`core.filemode=false`), vérifier que
  `scripts/*.sh` gardent le bit exécutable dans le commit de release, sinon la
  CI les invoque en « Permission denied ».
