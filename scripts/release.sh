#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# release.sh — Préparation et validation d'une release Sankamusic
#
# Conforme à RELEASE_GUIDE.md. Ce script automatise les étapes 0 à 4
# (tests, build, unicité de l'APK, signature, cohérence de version,
# checksums) et imprime les étapes manuelles restantes (5 à 7).
# Il NE publie RIEN : pas de tag, pas de push, pas de release GitHub.
#
# Prérequis machine : JDK 17, Android SDK (local.properties ou ANDROID_HOME).
# Signature : export ANDROID_KEYSTORE_FILE=chemin/vers/release.jks
#            (+ ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS,
#             ANDROID_KEY_PASSWORD). Sans keystore, le build produit un APK
#             non signé → le script ÉCHOUE (règle RELEASE_GUIDE.md).
#
# Usage : scripts/release.sh
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail

cd "$(dirname "$0")/.."   # se placer à la racine du projet

# ── Préconditions ─────────────────────────────────────────────────────
command -v java >/dev/null 2>&1 || { echo "ERREUR : JDK 17 requis (java introuvable)."; exit 1; }
[ -x ./gradlew ] || { echo "ERREUR : ./gradlew introuvable (wrapper non généré)."; exit 1; }

SDK_DIR=""
if [ -f local.properties ]; then
  SDK_DIR=$(grep '^sdk.dir=' local.properties | head -1 | cut -d= -f2 | tr -d '\\')
fi
[ -n "$SDK_DIR" ] || SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ] || { echo "ERREUR : Android SDK introuvable (local.properties ou ANDROID_HOME requis)."; exit 1; }

VERSION=$(grep '^SANKAMUSIC_VERSION=' gradle.properties | head -1 | cut -d= -f2 | tr -d ' ')
VERSION_CODE=$(grep '^SANKAMUSIC_VERSION_CODE=' gradle.properties | head -1 | cut -d= -f2 | tr -d ' ')
[ -n "$VERSION" ] || { echo "ERREUR : SANKAMUSIC_VERSION absent de gradle.properties."; exit 1; }
TAG="v$VERSION"
echo "== Release préparée : Sankamusic $VERSION (versionCode ${VERSION_CODE:-?}), tag prévu : $TAG =="

# ── Étape 0 — Tests ───────────────────────────────────────────────────
echo "== Étape 0/3 — Tests =="
./gradlew :core:test :plugins:hellospacekai:test :themes:exampletheme:test

# ── Étape 1 — Build release signé ─────────────────────────────────────
echo "== Étape 1/3 — Build release =="
if [ -n "${ANDROID_KEYSTORE_FILE:-}" ]; then
  [ -f "$ANDROID_KEYSTORE_FILE" ] || { echo "ERREUR : keystore introuvable : $ANDROID_KEYSTORE_FILE"; exit 1; }
  export ANDROID_KEYSTORE_BASE64
  ANDROID_KEYSTORE_BASE64=$(base64 -w0 "$ANDROID_KEYSTORE_FILE")
  [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] || { echo "ERREUR : ANDROID_KEYSTORE_PASSWORD requis."; exit 1; }
  [ -n "${ANDROID_KEY_ALIAS:-}" ] || { echo "ERREUR : ANDROID_KEY_ALIAS requis."; exit 1; }
  [ -n "${ANDROID_KEY_PASSWORD:-}" ] || { echo "ERREUR : ANDROID_KEY_PASSWORD requis."; exit 1; }
else
  echo "AVERTISSEMENT : aucun ANDROID_KEYSTORE_FILE — le build sera NON SIGNÉ et échouera au contrôle ci-dessous (voulu)."
fi
./gradlew assembleRelease

# ── Étape 2 — Vérifications réelles sur l'artefact ────────────────────
echo "== Étape 2/3 — Vérifications (unicité, signature, version, checksums) =="
APK_DIR="app/build/outputs/apk/release"
mapfile -t APKS < <(ls "$APK_DIR"/*.apk 2>/dev/null || true)
[ "${#APKS[@]}" -eq 1 ] || { echo "ERREUR : ${#APKS[@]} APK trouvés — il en faut EXACTEMENT 1 (RELEASE_GUIDE.md § 1)."; exit 1; }
case "${APKS[0]}" in
  *-debug*|*-unsigned*|*unaligned*) echo "ERREUR : artefact non valide pour publication : ${APKS[0]}"; exit 1 ;;
esac
echo "APK unique : ${APKS[0]}"

APKSIGNER=$(ls "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | tail -1)
[ -n "$APKSIGNER" ] || { echo "ERREUR : apksigner introuvable dans $SDK_DIR/build-tools."; exit 1; }
"$APKSIGNER" verify --print-certs "${APKS[0]}" | head -3

AAPT=$(ls "$SDK_DIR"/build-tools/*/aapt 2>/dev/null | tail -1)
[ -n "$AAPT" ] || { echo "ERREUR : aapt introuvable dans $SDK_DIR/build-tools."; exit 1; }
APK_VERSION=$("$AAPT" dump badging "${APKS[0]}" | grep -oP "versionName='\K[^']+")
[ "$APK_VERSION" = "$VERSION" ] || { echo "ERREUR : version de l'APK ($APK_VERSION) ≠ SANKAMUSIC_VERSION ($VERSION)."; exit 1; }
if git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null; then
  echo "ERREUR : le tag $TAG existe déjà — version déjà publiée ?"; exit 1
fi
echo "Version cohérente : APK $APK_VERSION == gradle.properties $VERSION ; tag $TAG libre."

( cd "$APK_DIR" && sha256sum *.apk > SHA256SUMS.txt && sha256sum -c SHA256SUMS.txt )

# ── Étape 3 — Prochaines actions (manuelles, non exécutées ici) ───────
echo ""
echo "== Étape 3/3 — Actions manuelles restantes (RELEASE_GUIDE.md) =="
echo "  1. Valider l'installation sur appareil/émulateur (propre ET mise à jour)."
echo "  2. Committer et pousser la branche : git add -A && git commit && git push -u origin main"
echo "  3. Créer et pousser le tag (déclenche .github/workflows/release.yml) :"
echo "       git tag $TAG"
echo "       git push origin $TAG"
echo "     → le workflow re-vérifie tout (tests, 1 APK, apksigner, version, checksums)"
echo "       et crée une release DRAFT (secrets GitHub ANDROID_KEYSTORE_* requis)."
echo "  4. Vérification post-publication (étape 7) : télécharger l'APK depuis GitHub,"
echo "     'sha256sum -c SHA256SUMS.txt', installer, puis publier la release."
echo ""
echo "== Release $VERSION préparée et validée localement (étapes 0-4). =="
