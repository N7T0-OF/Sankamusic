#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# make-dev-keystore.sh — Ensemble de signature ⚠️ DEV ⚠️ pour débloquer le CI
#
# Génère un keystore EPHEMÈRE et exporte les 4 secrets attendus par
# release.yml (ANDROID_KEYSTORE_BASE64, _PASSWORD, ANDROID_KEY_ALIAS,
# ANDROID_KEY_PASSWORD), puis VÉRIFIE la cohérence : le base64 est re-décodé
# et validé via `keytool -list`.
#
# 🚨 DEV SEULEMENT — PAS POUR PUBLICATION !
# Ce keystore sert uniquement à faire tourner le workflow de release en CI et
# à produire une APK installable. Il doit être REMPLACÉ par un vrai keystore
# de release (kept privé) AVANT toute publication réelle. Voir docs/REPO_SETUP.md.
#
# Sortie : un snippet bash à coller dans ta session, qui exporte les 4
# variables d'environnement. Le keystore (.jks) et le fichier base64 sont
# créés dans OUt (défaut : .tmp-signing/). Ils ne doivent JAMAIS être
# commités (.gitignore couvre *.jks / *.keystore).
#
# Prérequis : keytool (JDK) dans le PATH, ou JDK exposé via JAVA_HOME.
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail

cd "$(dirname "$0")/.."

# ── localiser keytool (PATH puis JAVA_HOME/bin) ─────────────────────────
KEYTOOL=$(command -v keytool || true)
if [ -z "$KEYTOOL" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
  KEYTOOL="$JAVA_HOME/bin/keytool"
fi
if [ -z "$KEYTOOL" ]; then
  echo "ERREUR : keytool introuvable (installe un JDK ou exporte JAVA_HOME)." >&2
  exit 1
fi
echo "keytool : $KEYTOOL"

# ── options (surchargeables) ─────────────────────────────────────────────
OUT="${OUT:-.tmp-signing}"
DNAME="${DNAME:-CN=Sankamusic Dev, OU=SpaceKai, O=Sankamusic, L=Paris, C=FR}"
KEYSTORE_FILE="${ANDROID_KEYSTORE_FILE:-$OUT/dev-keystore.jks}"
KEYS_VALIDITY="${KEYS_VALIDITY:-10000}"

# ── paramètres : fournis OU (par défaut) générés aléatoirement ────────────
# Alias par défaut (généré aléatoirement en mode automatique).
key_alias="${ANDROID_KEY_ALIAS:-sankamusic-dev}"
if [ -z "${ANDROID_KEY_ALIAS:-}" ] && [ -z "${ANDROID_KEYSTORE_PASSWORD:-}" ]; then
  key_alias="sankamusic-dev_$(head -c4 /dev/urandom | base64 | tr -dc 'a-z0-9' | head -c8)"
fi
ANDROID_KEY_ALIAS="$key_alias"

# Mot de passe keystore (fourni ou aléatoire) ; clé = même valeur par défaut.
ANDROID_KEYSTORE_PASSWORD="${ANDROID_KEYSTORE_PASSWORD:-dev_$(head -c6 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c12)}"
ANDROID_KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-$ANDROID_KEYSTORE_PASSWORD}"

mkdir -p "$OUT"

echo "== Génération du keystore (${KEYSTORE_VALIDITY:-$KEYS_VALIDITY} jours) =="
echo "   fichier  : $KEYSTORE_FILE"
echo "   alias    : $ANDROID_KEY_ALIAS"

# ── générer le keystore (force = on, dev uniquement) ─────────────────────
"$KEYTOOL" -genkeypair -v \
  -alias "$ANDROID_KEY_ALIAS" \
  -keyalg RSA -keysize 2048 \
  -validity "$KEYS_VALIDITY" \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$ANDROID_KEYSTORE_PASSWORD" \
  -keypass "$ANDROID_KEY_PASSWORD" \
  -dname "$DNAME"

BASE64_FILE="$OUT/dev-keystore.b64"
base64 < "$KEYSTORE_FILE" > "$BASE64_FILE"    # base64 complet du keystore .jks

ANDROID_KEYSTORE_BASE64=$(base64 < "$KEYSTORE_FILE" | tr -d '\n')
export ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD

# ── VÉRIFICATION de cohérence (re-décodage + keytool -list) ──────────────
check="$OUT/.verify.jks"
base64 -d <<< "$ANDROID_KEYSTORE_BASE64" > "$check"
if ! "$KEYTOOL" -list -keystore "$check" -storepass "$ANDROID_KEYSTORE_PASSWORD" >/dev/null 2>&1; then
  echo "ERREUR : la vérification keytool -list a échoué après re-décodage." >&2
  rm -f "$check"
  exit 1
fi
alias_in="$("$KEYTOOL" -list -keystore "$check" -storepass "$ANDROID_KEYSTORE_PASSWORD" 2>/dev/null | grep -q "$ANDROID_KEY_ALIAS" && echo yes || echo no)"
[ "$alias_in" = "yes" ] || { echo "ERREUR : l'alias n'est pas présent dans le keystore re-décodé." >&2; rm -f "$check"; exit 1; }
rm -f "$check"

echo ""
echo "== ✅ Keystore DE-CODÉ ET cohérent (re-décodage + keytool -list) =="
echo "== ⚠️  DEV UNIQUEMENT — à remplacer par un vrai keystore de release  =="
echo ""
echo "== Exporte dans TA session (ne pas mettre dans un commit) =="
echo "export ANDROID_KEYSTORE_BASE64='$ANDROID_KEYSTORE_BASE64'"
echo "export ANDROID_KEYSTORE_PASSWORD='$ANDROID_KEYSTORE_PASSWORD'"
echo "export ANDROID_KEY_ALIAS='$ANDROID_KEY_ALIAS'"
echo "export ANDROID_KEY_PASSWORD='$ANDROID_KEY_PASSWORD'"
echo ""
echo "(fichier keystore : $KEYSTORE_FILE — base64 dans $BASE64_FILE)"