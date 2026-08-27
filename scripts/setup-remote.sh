#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# setup-remote.sh — Remplir et paramétrer le repo GitHub N7T0-OF/Sankamusic
#
# Constat (2026-08-27) : le repo est vide — pas de README poussé, pas de
# description/topics/licence, pas de release v0.1.0.
#
# Ce script exécute uniquement les actions sûres et idempotentes (remote,
# branche) ; tout le reste est affiché commande par commande pour que TU
# décides (identité, push, métadonnées, licence, secrets, release).
#
# Usage : scripts/setup-remote.sh
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail

cd "$(dirname "$0")/.."

REPO="N7T0-OF/Sankamusic"
REMOTE_URL="https://github.com/$REPO.git"

echo "== 0. Identité git (à faire UNE fois, avec TON nom/email GitHub) =="
if [ -n "$(git config user.email 2>/dev/null)" ]; then
  echo "   identité déjà configurée : $(git config user.name) <$(git config user.email)>"
else
  echo "   git config user.name  \"TonNom\""
  echo "   git config user.email \"ton-email@github.com\""
  echo "   Puis, pour corriger l'auteur du commit existant (avant tout push) :"
  echo "   git commit --amend --author='TonNom <ton-email@github.com>' --reset-author"
fi

echo ""
echo "== 1. Remote origin (idempotent) =="
if git remote get-url origin >/dev/null 2>&1; then
  echo "   origin déjà défini : $(git remote get-url origin)"
else
  git remote add origin "$REMOTE_URL"
  echo "   origin ajouté : $REMOTE_URL"
fi
BRANCH=$(git branch --show-current)
[ "$BRANCH" = "main" ] || { echo "   ATTENTION : branche courante '$BRANCH' (attendue : main) — git branch -M main"; }

echo ""
echo "== 2. Pousser la fondation (le README s'affichera sur GitHub) =="
echo "   git push -u origin main"

echo ""
echo "== 3. Description + topics (gh CLI si disponible, sinon API avec token) =="
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  echo "   gh repo edit $REPO \\"
  echo "     --description 'Plateforme musicale indépendante (plugins, thèmes, extensions) — base compatible SimpMusic, inspirée de BetterDiscord' \\"
  echo "     --add-topic android --add-topic kotlin --add-topic jetpack-compose \\"
  echo "     --add-topic music-player --add-topic plugins --add-topic themes"
else
  echo "   gh CLI non authentifiée. Alternative (token en variable GITHUB_TOKEN) :"
  echo "   curl -X PATCH -H 'Authorization: Bearer \$GITHUB_TOKEN' \\"
  echo "     https://api.github.com/repos/$REPO -d '{\"description\":\"...\",\"topics\":[\"android\",\"kotlin\",\"music-player\"]}'"
fi

echo ""
echo "== 4. Licence (choix juridique — voir docs/REPO_SETUP.md) =="
echo "   Ajouter le fichier LICENSE puis : git add LICENSE && git commit && git push"

echo ""
echo "== 5. Keystore de signature (jamais commité) =="
echo "   keytool -genkeypair -v -keystore release.keystore -alias sankamusic \\"
echo "     -keyalg RSA -keysize 2048 -validity 10000"
echo "   base64 -w0 release.keystore   (→ valeur du secret ANDROID_KEYSTORE_BASE64)"

echo ""
echo "== 6. Secrets GitHub (Settings → Secrets and variables → Actions) =="
echo "   ANDROID_KEYSTORE_BASE64 | ANDROID_KEYSTORE_PASSWORD | ANDROID_KEY_ALIAS | ANDROID_KEY_PASSWORD"

echo ""
echo "== 7. Release v0.1.0 =="
echo "   Sur une machine avec Android Studio (JDK 17 + SDK) :"
echo "   export ANDROID_KEYSTORE_FILE=release.keystore ANDROID_KEYSTORE_PASSWORD=... \\"
echo "          ANDROID_KEY_ALIAS=sankamusic ANDROID_KEY_PASSWORD=..."
echo "   scripts/release.sh"
echo "   puis : git tag v0.1.0 && git push origin v0.1.0"
echo "   → le workflow release.yml re-vérifie tout et crée la release DRAFT."

echo ""
echo "== Détails complets : docs/REPO_SETUP.md =="
