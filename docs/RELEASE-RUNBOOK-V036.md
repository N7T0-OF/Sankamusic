# Runbook de release SpaceKai v0.3.6 — post Gate 0 (exécution opérateur)

> Ce runbook décrit, **dans l'ordre**, la séquence complète après un Gate 0
> appareil **vert** pour la v0.3.6 : changelog → tag → CI → draft → publication
> → V4b. Il est conçu pour être exécuté **tel quel** sur la machine de
> release/validation, sans décision d'architecture ni nouveau commit de code.
>
> État de gel de départ (à vérifier avant toute étape) :
>
> ```text
> HEAD             73db19ba (ou tout commit validé sur dev après Gate 0 vert)
> versionName      0.3.6
> versionCode      84
> branche          dev
> working tree     propre
> ahead of origin  5 commits (3c210b5f, 7b3a6d09, e0175cbb, a0d52fb4, 73db19ba)
> tag v0.3.6       ABSENT (dernier tag : v0.3.5)
> draft            ABSENT
> ```
>
> **Règle absolue : si une étape échoue, on s'arrête et on diagnostique le
> maillon (fichier:ligne) — on ne « rattrape » pas en contournant le garde-fou.**

---

## 0. Prérequis — Gate 0 appareil réellement vert

- [ ] APK v0.3.6 **signé avec la même clé que v0.3.5** (jamais l'APK unsigned).
- [ ] `adb devices -l` → au moins un téléphone réel.
- [ ] Le driver a rendu **exit 0 + ALL PASS** :
  ```bash
  ./scripts/device-acceptance-settings-v036.sh SpaceKai-v0.3.6.apk
  ```
- [ ] `device-acceptance-results.txt` contient PASS pour V1, V2, V3, V4
      (lignes `PASS:`), `PASS=4 FAIL=0`, et le verdict final
      `GATE 0 DEVICE (v0.3.6): ALL STEPS PASSED`.

**Piège de l'exit code — à connaître avant de s'appuyer sur le 0 :** le
driver n'exige que `FAIL == 0 && PASS > 0` pour rendre 0 ; un verdict
`SKIP` (touche « s ») n'incrémente ni PASS ni FAIL et ne bloque donc **pas**
l'exit 0. Un Gate 0 réellement vert exige donc **zéro ligne `SKIP:`** dans
`device-acceptance-results.txt` — notamment les étapes V4/V4b d'opérateur
(détection, checksum/package, données, plus-rien-à-mettre-à-jour) doivent
être répondues **y**, pas **s**. Si le fichier contient un `SKIP:`, ce n'est
pas un ALL PASS : rejouer la ou les étapes manquantes jusqu'à PASS complet.

**Si ce n'est pas le cas : STOP. Ne pas tagger. Rejouer le protocole
(`docs/E2E-NAV-SETTINGS-V036.md` §3-5) après correction du maillon fautif.**

---

## 1. Changelog — déjà prêt, à vérifier seulement

Le changelog `fastlane/metadata/android/en-US/changelogs/84.txt` a été généré
et rogné dans les commits `7b3a6d09` + `a0d52fb4` (narratif Ko-fi corrigé).
La CI l'utilisera tel quel pour les notes de la release.

Vérifications :
```bash
cd ~/Sankamusic-dev
git status --short --branch                 # propre, sur dev
grep -n '^version-name\|^version-code' gradle/libs.versions.toml   # 0.3.6 / 84
test -f fastlane/metadata/android/en-US/changelogs/84.txt && echo OK
grep -n 'ko-fi.com/souanpt' fastlane/metadata/android/en-US/changelogs/84.txt
grep -c 'maxrave-dev' fastlane/metadata/android/en-US/changelogs/84.txt || true  # 0 attendu
```

Si un des contrôles échoue : STOP — corriger le fichier concerné, valider,
**puis seulement** continuer. Ne pas régénérer par-dessus : le narratif est
délibérément rogné au scope réel (le générateur ne sert que s'il manque).

> La CI appelle `scripts/append-feature-audit-gaps.sh` elle-même pour ajouter
> le bloc « Connu / non terminé » + Installation/SHA-256 aux notes de release —
> le fichier en dépôt n'a pas besoin de les porter pour le draft (le draft les
> aura quand même).

---

## 2. Gate locale optionnelle avant tag (recommandée, ~3 min)

```bash
export JAVA_HOME="C:/Users/kai24/.gradle/jdks/microsoft-21-amd64-windows.2"   # machine locale
bash scripts/test-gates.sh
./gradlew :composeApp:compileKotlinMetadata
./gradlew :composeApp:jvmTest
```

Sur la machine de release (pas ce poste), `scripts/pre-release-report.sh` sera
exécuté **par la CI** avant création du draft — pas besoin de le lancer à la main.

---

## 3. Tag v0.3.6

```bash
cd ~/Sankamusic-dev
git tag -a v0.3.6 -m "SpaceKai v0.3.6 (code 84)"
```

- Tag **annoté** (comme v0.3.5).
- Le tag doit pointer le commit validé par Gate 0 — par défaut `73db19ba`
  (HEAD actuel de dev).
- **Ne pas tagger sur la machine sans appareil** : le tag n'a de sens qu'une
  fois le Gate 0 réellement vert.

---

## 4. Push dev + tag → déclenche la CI

```bash
git push origin dev
git push origin v0.3.6
```

Le push du tag `v*` déclenche `.github/workflows/android-release.yml` :

| Job | Rôle | Échec = ? |
|---|---|---|
| `validate-tag` | tag == `v$(version-name)` (v0.3.6) **et** versionCode 84 > plus grand code jamais publié (83) | STOP |
| `build-full-release` | APK release signé (même clé) | STOP |
| `build-desktop-packages` | AppImage/DMG/MSI (Conveyor) — peut être skippé sans clé Conveyor | warning |
| `wrap-mac-dmg` | DMG macOS | warning si skippé |
| `create-github-release` | notes (84.txt) + SHA256SUMS + `gh release create --draft` | STOP |

Vérifier l'apparition du run :
```bash
gh run list --workflow=android-release.yml --limit 3
gh run watch <run-id> --exit-status
```

---

## 5. Vérifier le draft (avant publication)

La CI crée un **draft** (`--draft`) : jamais publié automatiquement pour un
tag stable. Vérifier :

```bash
gh release view v0.3.6 --json name,tagName,isDraft,assets,body --jq \
  '{name,isDraft,assets: [.assets[].name], body_head: (.body[0:120])}'
```

Attendu :
- `isDraft: true`
- assets : exactement **1 APK** `SpaceKai-v0.3.6.apk` (jamais split/debug/unsigned),
  les artefacts desktop si présents, et `SHA256SUMS.txt`.
- body : commence par `# SpaceKai v0.3.6` (notes issues de `84.txt` + bloc
  audit + SHA-256).

Vérification d'intégrité de l'APK du draft (facultatif mais recommandé avant
publication) :
```bash
gh release download v0.3.6 --pattern '*.apk' --dir /tmp/v036-draft
(cd /tmp/v036-draft && sha256sum -c ../sha256 2>/dev/null; sha256sum SpaceKai-v0.3.6.apk)
# comparer avec la ligne SHA256SUMS.txt du draft
```

---

## 6. Publier le draft

```bash
gh release edit v0.3.6 --draft=false
```

Après publication :
- [ ] `gh release view v0.3.6 --json isDraft` → `false`
- [ ] `gh release view v0.3.6 --json url` → URL publique
- [ ] `/releases/latest` répond avec v0.3.6 (pour l'Update Manager)

**Garde-fous CI à ne jamais violer** (commentés dans `android-release.yml`) :
- Ne **jamais** supprimer une release publiée : le `tag_name` est brûlé à
  jamais (« tag_name was used by an immutable release ») — un upload échoué
  doit laisser le draft en place pour inspection.
- Ne **jamais** créer la release publiée puis y ajouter des assets en deux
  temps (422) — tout est attaché dans le même `gh release create`.
- Si le tag est déjà brûlé : ne pas réessayer le même tag ; bumper
  (`v0.3.6-1`) après décision.

---

## 7. V4b — re-détection contre la release réelle (après publication)

L'Update Manager interroge `https://api.github.com/repos/N7T0-OF/Sankamusic/releases/latest`
(`Ytmusic.kt:600-601`). Tant que v0.3.6 n'était pas publique, V4b était
impossible ; **maintenant il l'est**.

Sur le téléphone de validation (celui du Gate 0) :

```bash
# 1. Depuis v0.3.5 installée (scénario le plus strict) :
adb shell am start -n com.maxrave.simpmusic/.MainActivity
# Réglages → Mises à jour → SpaceKai
#   → « Installée v0.3.5 · dernière : v0.3.6 » + bouton « Mettre à jour »
#   → lancer la mise à jour : téléchargement → « Vérification SHA-256… » →
#     package-check (com.maxrave.simpmusic) → installateur système

# 2. Après installation, re-vérifier :
adb shell dumpsys package com.maxrave.simpmusic | grep -E 'versionName|versionCode'
#   → versionName 0.3.6 / versionCode 84
#   → données + compte YouTube intacts

# 3. Dans l'app : Re-vérifier → « ✓ À jour », plus aucune mise à jour proposée,
#    jamais de downgrade.
```

Le driver `device-acceptance-settings-v036.sh` couvre déjà ce flux dans
`flow4_updater` (étapes « upgrade offered (needs public release) »,
« checksum + package guards », « data preserved », « no further update ») —
le rejouer **après publication** valide V4b. Résultat attendu : exit 0 + ALL
PASS, **sans aucune ligne `SKIP:`** dans `device-acceptance-results.txt`
(voir §0 — un SKIP ne bloque pas l'exit 0, donc seul l'absence de SKIP prouve
que V4b a été réellement exécuté, pas contourné).

---

## 8. Post-publication — vérifications finales

```bash
gh release list --limit 3                          # v0.3.6 visible, publié
git ls-remote --tags origin v0.3.6                 # tag présent sur origin
git status --short --branch                        # dev propre, à jour (ou ahead si suite)
```

- [ ] Draft → publié, assets complets, SHA256SUMS correct.
- [ ] V4b exit 0 + ALL PASS sur téléphone.
- [ ] Rien d'autre n'a été modifié (pas de nouveau commit de code post-Gate 0
      sans validation).

---

## Critères d'arrêt immédiat

- Gate 0 non vert → **jamais de tag**.
- `validate-tag` rouge → tag ≠ version-name, ou versionCode ≤ 83 → ne pas
  forcer : corriger le tag ou le bump, revalider.
- Draft incomplet (APK manquant, plusieurs APK, SHA256SUMS absent) → ne pas
  publier : laisser le draft, inspecter les logs CI, corriger.
- V4b rouge après publication → diagnostiquer le maillon de l'Update Manager
  (détection, checksum, package, signature) et corriger dans un patch v0.3.7 —
  ne pas « réparer » en publiant à nouveau un v0.3.6 modifié.