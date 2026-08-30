#!/bin/bash
# audit-updater-flow.sh — audit of the update flow that EXISTS today.
#
# DEFINITIVE FINDING (static audit 2026-08-26, composeApp):
#   The DETECTION half of the updater is real and correctly pointed at SpaceKai:
#     - Settings → "Check for update" → SharedViewModel.checkForUpdate()
#       (SettingScreen.kt:2469), which writes a "CheckForUpdateAt" timestamp
#       (the cache the user asked for, SharedViewModel.kt:969) and calls
#       updateRepository.checkForGithubReleaseUpdate() (core/data, upstream).
#     - On success: _updateResponse + showedUpdateDialog = true
#       (SharedViewModel.kt:980) → AlertDialog in App.kt:773 with Download/Cancel.
#     - Download button carries a SPACEKAI CUSTOMIZATION: it opens
#       SpaceKaiUpdateConfig.releasesPageUrl (the SpaceKai releases page), NOT
#       the upstream SimpMusic page — correct, because handing users the
#       upstream APK (different signing key) yields "Application non installée".
#   The DOWNLOAD/INSTALL half does NOT exist:
#     - the Download button is a browser redirect (openUrl), there is NO
#       internal APK download, NO SHA-256 verification, NO install intent, NO
#       download-state UI (Téléchargement… / Installation prête / Échec), and
#       NO APK selection (SpaceKai-vX.X.X.apk vs debug/arm64 splits).
#
#   What to implement (the real P0 #1, next coding session):
#     replace the openUrl button with: internal download of the release APK
#     (GitHub API → asset matching `SpaceKai-v<version>.apk`), SHA-256 check,
#     then Android ACTION_VIEW install intent (user confirms — the OS
#     restriction). Keep the timestamp cache; never call GitHub per recomposition.
#
# THIS GATE protects the chain that exists against upstream merges:
#   FAIL if the update dialog or its SPACEKAI CUSTOMIZATION is reverted
#   (users would be sent back to the SimpMusic download page).
#
# Usage:  ./scripts/audit-updater-flow.sh
# Exit:   0 = chain intact (download/install still missing, documented), 1 = broken.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"
APP="$SRC/App.kt"
VM="$SRC/viewModel/SharedViewModel.kt"
SETTINGS="$SRC/ui/screen/home/SettingScreen.kt"
fail=0
pass() { echo "  PASS: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

echo "============================================"
echo "UPDATER FLOW AUDIT (detection exists, download/install missing)"
echo "============================================"
echo "Finding: check + dialog are real and point at SpaceKai releases"
echo "(SPACEKAI CUSTOMIZATION in App.kt). Download button = browser redirect;"
echo "no internal APK download, no SHA-256, no install intent, no state UI."
echo ""

# --- 1. Update dialog + SPACEKAI CUSTOMIZATION -----------------------------
if [ ! -f "$APP" ]; then
  crit "App.kt missing: $APP"
else
  if grep -q 'shouldShowUpdateDialog' "$APP" && grep -q 'AlertDialog' "$APP"; then
    pass "Update dialog present (App.kt)"
  else
    crit "Update dialog gone — users can no longer see new versions"
  fi
  if grep -q 'SpaceKaiUpdateConfig.releasesPageUrl' "$APP"; then
    pass "Download button points at the SPACEKAI releases page (SPACEKAI CUSTOMIZATION intact)"
  else
    crit "SPACEKAI CUSTOMIZATION REVERTED — Download would hand users the SimpMusic page/APK (signature mismatch → 'Application non installée')"
  fi
fi

# --- 2. Checker entry points ------------------------------------------------
if [ ! -f "$VM" ]; then
  crit "SharedViewModel.kt missing: $VM"
else
  if grep -q 'fun checkForUpdate()' "$VM"; then
    pass "SharedViewModel.checkForUpdate() present"
  else
    crit "checkForUpdate() removed — Settings 'Check for update' does nothing"
  fi
  if grep -q 'checkForGithubReleaseUpdate()' "$VM"; then
    pass "GitHub release checker called (impl lives in core/data, upstream)"
  else
    crit "checkForGithubReleaseUpdate() removed — no release check happens"
  fi
  if grep -q '"CheckForUpdateAt"' "$VM"; then
    pass "Update-check timestamp cache present (no per-recomposition GitHub call)"
  else
    crit "Timestamp cache removed — the checker may run every recomposition"
  fi
fi

if [ ! -f "$SETTINGS" ]; then
  crit "SettingScreen.kt missing: $SETTINGS"
else
  if grep -q 'sharedViewModel.checkForUpdate()' "$SETTINGS"; then
    pass "Settings 'Check for update' item wired"
  else
    crit "Settings 'Check for update' item gone — no user entry point"
  fi
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: updater detection chain intact (SpaceKai releases)."
  echo "        Internal download/install still MISSING — the P0 to implement."
  exit 0
else
  echo "RESULT: UPDATER FLOW BROKEN — release blocked (see scripts/audit-updater-flow.sh)"
  exit 1
fi
