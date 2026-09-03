# Checklist — Release v0.3.5 (à exécuter UNIQUEMENT après Gate 0 appareil)

> Ordre strict : ce document se déclenche **après** que le protocole
> `docs/E2E-NAV-SETTINGS-V035.md` a tourné sur un téléphone réel et que
> `scripts/device-acceptance-nav-settings.sh` est sorti `exit 0`
> (« GATE 0 DEVICE: ALL STEPS PASSED »). Tant que ce n'est pas le cas :
> **ne rien exécuter de ce document** (pas de correction de générateur, pas de
> bump, pas de tag, pas de publication).
>
> État de référence vérifié dans le dépôt (2026-09-03) :
> version-name `0.3.4` / version-code `82` (`gradle/libs.versions.toml:3-4`) ;
> tag `v0.3.4` existant ; `dev` = `db002314` (PR #2 squash-merge).
> Chaque étape cite la commande exacte et le fichier:ligne à ouvrir en cas
> d'échec — jamais « à peu près ».

## 0. Porte d'entrée — Gate 0 appareil (non négociable)

- [ ] `docs/E2E-NAV-SETTINGS-V035.md` exécuté : F1 (gap Settings), F2 (Analytics
      en compact), F3 (masquer l'onglet sélectionné → bascule), F4 (masquer
      Search → FAB retiré) **tous PASS sur un téléphone réel**, données v0.3.4
      conservées.
- [ ] `scripts/device-acceptance-nav-settings.sh` → `exit 0`.
- [ ] Aucun échec « compensé » : chaque flux a été observé, pas supposé.

Si Gate 0 n'est pas vert → retour au protocole (diagnostic par maillon), puis
re-jouer. **Interdits tant que Gate 0 n'est pas vert** : correction générateur,
bump, tag, draft, publication.

---

## 1. Corriger le générateur de changelog (défaut connu, différé jusqu'ici)

### 1.1 Le défaut (vérifié)

`scripts/generate-fastlane-changelog.sh` calcule le tag précédent par :

```bash
PREV_TAG=$(git tag --sort=-v:refname | sed -n '2p')
```

Avec la ligne 0.3.x, `--sort=-v:refname` classe **toute** l'histoire par
version : `v2.0.0, v1.9.0, v1.8.0, v1.7.9, …` **puis** `v0.3.4, v0.3.3, …`
(la majeure 2 > 0). `sed -n '2p'` renvoie donc **`v1.9.0`** — pas `v0.3.4` —
et le range `v1.9.0..HEAD` couvre des centaines de commits d'une autre époque.
C'est pourquoi `82.txt` (v0.3.4) a dû être écrit à la main : le générateur
était inutilisable sur la ligne 0.3.x.

### 1.2 La correction attendue

Le « tag précédent » doit être **le plus grand tag strictement inférieur au
`version-name` courant** (v0.3.4 pour une release 0.3.5), quelle que soit la
ligne 1.x/2.x. Exemple de règle à implémenter :

```bash
VERSION_NAME=$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
PREV_TAG=$(git tag --sort=-v:refname | while read -r t; do
  # garder le premier tag classé STRICTEMENT sous la version courante
  [ "$(printf '%s\n%s\n' "$VERSION_NAME" "${t#v}" | sort -V | head -1)" = "${t#v}" ] \
    && [ "$t" != "v$VERSION_NAME" ] && { echo "$t"; break; }
done)
```

(Le `sed -n '2p'` actuel suffisait quand les releases étaient consécutives en
haut du classement — v1.7.x → v2.0.0 ; il est faux depuis le passage à 0.3.x.)

### 1.3 Après la correction — périmètre v0.3.5 (policy patch release)

- [ ] Corriger le script **et** le vérifier : le range doit être
      `v0.3.4..HEAD` et lister uniquement les commits de la PR #2
      (`db002314`, squash `fix(p0): settings top gap, Analytics in compact
      nav, shared nav-selection contract`).
- [ ] Régénérer `fastlane/metadata/android/en-US/changelogs/83.txt` :
      `./scripts/generate-fastlane-changelog.sh` (après le bump — §2).
- [ ] **Réduire au périmètre réel** (style du `82.txt` : récit honnête +
      bullets précis). Ne PAS importer en bloc la liste « Connu / non
      terminé » de l'audit global dans « What's changed » (elle appartient à
      l'audit, pas à une release patch) :
  - `FIX` — l'espace vertical en tête de Settings (~376dp → inset barre
    d'app), visible après le correctif de l'ancre du glow ambiant ;
  - `FIX` — Analytics reste disponible en navigation compacte quand le
    tracking local est ON (seul Mix-for-you est retiré par le mode compact) ;
  - `FIX` — contrat de sélection nav partagé (masquer l'onglet sélectionné
    bascule vers une destination visible ; masquer Search retire son FAB ;
    « tout masquer » garde l'accueil) ;
  - retirer le bruit « Other » (PRs docs/protocole/checklist : non
    utilisateur).
- [ ] **Garder le bloc d'honnêteté** ajouté par
      `scripts/append-feature-audit-gaps.sh` : `publish-draft.sh` exige sa
      présence **et** sa fraîcheur (égalité avec un audit régénéré) — ne
      jamais le supprimer, ne jamais publier une note qui cache du non-fait.
- [ ] Régénérer les audits sensibles aux lignes (leçon release v2.0.0,
      `RUNBOOK §7`) après toute édition de code : `bash scripts/test-gates.sh`
      + `bash scripts/audit-navigation.sh` (l'allowlist
      `audit-settings-ui.sh` vit sur des numéros de ligne).

---

## 2. Bump version — un seul point de vérité

La version n'est lue QUE dans `gradle/libs.versions.toml` (consommateurs :
`androidApp/build.gradle.kts:24-29` → `libs.versions.version.code/name` ;
BuildConfig `versionName`/`versionCode` dans `composeApp` ;
`android-release.yml` lit le toml pour le validate-tag). **Aucun autre fichier
à éditer.**

| Fichier:ligne | Avant | Après |
|---|---|---|
| `gradle/libs.versions.toml:3` `version-name` | `"0.3.4"` | `"0.3.5"` |
| `gradle/libs.versions.toml:4` `version-code` | `"82"` | `"83"` |

- [ ] Vérifier la valeur effective **avant** d'éditer (ne pas supposer 82/83) :
      `grep -E '^(version-name|version-code)' gradle/libs.versions.toml`.
- [ ] `83 > 82` = dernier code publié (tag `v0.3.4`) → installable en mise à
      jour depuis v0.3.4. Plancher `MIN_VERSION_CODE=72` inchangé (3 scripts :
      `apply-release-pipeline.sh:82`, `release-publish.sh:68`,
      `verify-release.sh`) — 83 > 72, aucune édition.
- [ ] Commit du bump **sur `dev`** (message style repo : `release(0.3.5): bump
      to v0.3.5 (code 83) + honest changelog`) — avec le `83.txt` du §1.

---

## 3. Gates pré-tag (aucun tag sans eux)

Depuis `dev`, à jour, arbre propre :

```bash
./scripts/check-pre-tag.sh        # → RESULT: ALL CHECKS PASSED (sinon : NE PAS tagger)
bash scripts/test-gates.sh        # → exit 0
```

- [ ] `check-pre-tag.sh` : git/origin/gh + audits + **0 contradiction
      changelog/audit** (un claim « fait » alors que l'audit dit
      PARTIALLY/NOT IMPLEMENTED bloque — leçon v2.0.0 : réécrire la ligne du
      changelog concernée, ne jamais tricher).
- [ ] `gh auth status` OK (exigé par check-pre-tag).

**Compilation pré-tag obligatoire** (leçon RUNBOOK §7 : « la CI est la
première compilation du contenu de release » — ne jamais pousser un tag dont
le code n'a pas compilé au moins une fois) :

```bash
./gradlew :composeApp:jvmTest :composeApp:compileKotlinMetadata
./gradlew :androidApp:assembleRelease    # variante release (FOSS) réelle
```

- [ ] Les trois passes vertes ; l'APK local produit reste un artefact local
      (unsigned) — jamais l'artefact de publication.

---

## 4. Tag v0.3.5 + push

Ordre : **draft → assets → publish, jamais l'inverse** ; ne jamais supprimer
une release publiée (tag brûlé → re-cut `v0.3.5-1`).

```bash
git tag -a v0.3.5 -m "SpaceKai v0.3.5 (code 83)"        # sur le commit de bump, dev
git push origin v0.3.5
```

Ou via le chemin `scripts/release.sh`/`release-publish.sh` (commit + tag +
push de `dev` + du tag, avec re-vérifications : tag == version-name, code >
72, icônes verrouillées).

- [ ] Le tag `v0.3.5` == `version-name` `0.3.5` (règle miroir du
      `validate-tag` CI, `android-release.yml:42-55`).
- [ ] `version-code` 83 strictement > max sur **tous** les tags
      (`android-release.yml:60-91` scanne chaque tag — max actuel 82).
- [ ] Ne **pas** tagger depuis la branche d'une PR non mergée — tagger le
      commit de bump présent sur `dev`.

---

## 5. CI — build + draft (automatique après le push du tag)

Workflow `android-release.yml` (déclencheur `tags: v*`) :

| Job | Critère |
|---|---|
| `validate-tag` | tag == version-name ; versionCode 83 > max historique ; icônes verrouillées |
| Build | **exactement 1 APK** `SpaceKai-v0.3.5.apk` (universel, signé) + packages desktop + `SHA256SUMS.txt` |
| `create-github-release` | notes = `83.txt` + bloc honnêteté (`append-feature-audit-gaps.sh`) ; `pre-release-report.sh` PASS ; tag stable → **DRAFT** « SpaceKai v0.3.5 » assets attachés |

- [ ] Laisser la CI finir (build complet ≈ 15-25 min) — poll le check
      `build`/les jobs, ne pas publier avant.
- [ ] Vérifier le **draft** : 1 APK `SpaceKai-v0.3.5.apk` + `SHA256SUMS.txt`
      (+ desktop) présents, notes `83.txt` lisibles.
- [ ] Échec d'un gate CI → corriger sur `dev`, re-pousser le tag (ou re-cut) —
      **aucun chemin de publication ne contourne les gates**.

---

## 6. Publication (une seule commande, après la CI verte)

```bash
./scripts/publish-draft.sh --dry-run     # optionnel
./scripts/publish-draft.sh               # confirmation → exit 0 = publiée
```

- [ ] `publish-draft.sh` re-télécharge les assets et les vérifie
      (`verify-release.sh` : 1 APK, `SHA256SUMS.txt` + chaque checksum,
      versionCode > 72) ; exige le bloc d'honnêteté présent ET frais.
- [ ] `exit 1` → corriger (assets/note/audit) et relancer ; `exit 2` → rien à
      faire (déjà publié).
- [ ] Après publication : `verify-release.yml` vert (re-vérification
      post-publication) ; `gh api /releases/latest` → `v0.3.5`.
- [ ] **Vrai scénario utilisateur final** : une v0.3.4 installée détecte
      v0.3.5 (Settings → Updates), télécharge, vérifie SHA-256 + signature,
      installe par-dessus, données conservées, puis se déclare à jour —
      cf. `docs/E2E-UPDATE-MANAGER.md`.

---

## 7. Hygiène post-release (prochaine release)

- [ ] Table des versions / `STATUS.md` : `v0.3.5 → 83 = MAX`.
- [ ] `MIN_VERSION_CODE` : inchangé (72) tant que 83 > 72 — ne l'éditer que
      si une release future approchait d'un code ≤ 72 (improbable).
- [ ] Mettre à jour la table « État des versions » de
      `docs/E2E-UPDATE-MANAGER.md` et le présent document (référence 0.3.4/82
      → 0.3.5/83).
- [ ] Promotion Play (interne → production) : décision manuelle et consciente,
      jamais automatique.

---

## Rappel — ce qui reste interdit même après Gate 0 vert

- Publier une note qui **cache du non-fait** (ZERO FALSE POSITIVE) ;
- supprimer une release publiée ou réutiliser un tag (brûlé) ;
- publier avant le draft vert, ou ajouter des assets à une release publiée
  (`422 Cannot upload assets to an immutable release`) ;
- déclarer une validation appareil qui n'a pas réellement été exécutée.
