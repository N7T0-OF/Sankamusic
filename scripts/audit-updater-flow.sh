#!/bin/bash
# audit-updater-flow.sh — audit of the update flow.
#
# DEFINITIVE FINDING (static audit, post-P0-1, composeApp):
#   The DETECTION half of the updater is real and correctly pointed at SpaceKai:
#     - Settings → "Check for update" → SharedViewModel.checkForUpdate()
#       (which writes a "CheckForUpdateAt" timestamp cache and calls
#       updateRepository.checkForGithubReleaseUpdate() in core/data, upstream).
#     - On success: _updateResponse + showedUpdateDialog = true
#       → AlertDialog in App.kt with Download/Cancel.
#     - Download button carries a SPACEKAI CUSTOMIZATION pointing at
#       SpaceKaiUpdateConfig.releasesPageUrl (the SpaceKai releases page), NOT
#       the upstream SimpMusic page — correct, because handing users the
#       upstream APK (different signing key) yields "Application non installée".
#   P0-1 (implemented in composeApp/spacekai/update/): the Download button now
#     drives the internal updater — GitHub API → asset `SpaceKai-v<version>.apk`
#     (universal only) → real byte-progress download → SHA-256 verification
#     against SHA256SUMS.txt → package-name check (getPackageArchiveInfo, the
#     APK must be the running app's package) → Android Package Installer via
#     FileProvider,
#     driven by a state machine (DOWNLOADING / VERIFYING / READY_TO_INSTALL /
#     INSTALLING / SUCCESS / FAILED). Desktop falls back to the browser.
#     This half is compile + jvm + gate + Android-CI verified; the full
#     device path (download → SHA → install) is NOT device-verified.
#
# THIS GATE protects the detection chain against upstream merges:
#   FAIL if the update dialog or its SPACEKAI CUSTOMIZATION is reverted
#   (users would be sent back to the SimpMusic download page).
#
# Usage:  ./scripts/audit-updater-flow.sh
# Exit:   0 = chain intact (including the P0-1 updater files), 1 = broken.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"
APP="$SRC/App.kt"
VM="$SRC/viewModel/SharedViewModel.kt"
SETTINGS="$SRC/ui/screen/home/SettingScreen.kt"
UPDATE="$SRC/spacekai/update"
fail=0
pass() { echo "  PASS: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

echo "============================================"
echo "UPDATER FLOW AUDIT (detection + P0-1 internal download/install)"
echo "============================================"
echo "Finding: check + dialog are real and point at SpaceKai releases"
echo "(SPACEKAI CUSTOMIZATION in App.kt). P0-1 adds the internal APK"
echo "download (SpaceKaiUpdateManager + PlatformUpdater) with SHA-256"
echo "verification and the Android Package Installer."
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

# --- 3. P0-1 internal download/install (composeApp) ------------------------
if [ -f "$UPDATE/SpaceKaiUpdateManager.kt" ]; then
  pass "P0-1 SpaceKaiUpdateManager present ($UPDATE/SpaceKaiUpdateManager.kt)"
  if grep -q 'object SpaceKaiUpdateManager' "$UPDATE/SpaceKaiUpdateManager.kt"; then
    pass "SpaceKaiUpdateManager object present"
  else
    crit "SpaceKaiUpdateManager object missing"
  fi
  if grep -q 'fun install' "$UPDATE/SpaceKaiUpdateManager.kt"; then
    pass "Install entry point present"
  else
    crit "Install entry point missing"
  fi
  if grep -q 'PlatformUpdater' "$UPDATE/SpaceKaiUpdateManager.kt"; then
    pass "PlatformUpdater expect/actual referenced"
  else
    crit "PlatformUpdater not referenced — download/install not wired"
  fi
else
  crit "P0-1 SpaceKaiUpdateManager.kt missing — internal download/install gone or reverted"
fi
if grep -q 'SettingsCollapsibleHeader' "$SETTINGS" && grep -q 'expandedSettingsSection' "$SETTINGS"; then
  pass "P0-2 settings collapse present (collapsed by default, single-open)"
else
  crit "P0-2 settings collapse missing — Settings sections no longer collapsible"
fi

# --- 4. Package-name gate before install (Android actual) ---------------------
AND_UPDATE="composeApp/src/androidMain/kotlin/com/maxrave/simpmusic/spacekai/update"
if [ -f "$AND_UPDATE/PlatformUpdater.android.kt" ]; then
  if grep -q 'getPackageArchiveInfo' "$AND_UPDATE/PlatformUpdater.android.kt"; then
    pass "Package-name check present (getPackageArchiveInfo — mismatched APK refused before install)"
  else
    crit "Package-name check missing — a mismatched APK could be installed blind"
  fi
else
  crit "PlatformUpdater.android.kt missing — Android install pipeline gone"
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: updater chain intact (SpaceKai releases) AND P0-1/P0-2 files present."
  echo "        Full device path (download -> SHA -> install) NOT device-verified —"
  echo "        see feature-audit report ('device run NON VERIFIE')."
  exit 0
else
  echo "RESULT: UPDATER FLOW BROKEN — release blocked (see scripts/audit-updater-flow.sh)"
  exit 1
fi
