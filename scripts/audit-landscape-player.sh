#!/bin/bash
# audit-landscape-player.sh — audit of the REAL landscape-player behaviour.
#
# DEFINITIVE FINDING (static audit 2026-08-26, composeApp):
#   NowPlayingScreen.kt (2640 lines) has NO orientation branch — it is a
#   portrait-first layout that is stretched in landscape. There is no
#   `isTablet`, no `Orientation.LANDSCAPE`, no horizontal redistribution.
#
#   How the app currently renders the player:
#     - Tablet landscape : NowPlayingScreenContent is embedded INLINE in the
#       main layout (App.kt:712 — a real responsive right panel, same content).
#     - Phone landscape  : NowPlayingScreen is shown as a FULL-SCREEN overlay
#       on top of the app (App.kt:727 `isShowNowPlaylistScreen && !isTabletLandscape`).
#       Because the layout is portrait-first, the user sees a "different screen"
#       — the same portrait arrangement squeezed into landscape (controls at
#       the bottom, artwork oversized) — which is the "wrong system" symptom.
#     - FullscreenPlayer.kt is a SEPARATE manual destination (fullscreen video
#       UI), NOT triggered by rotation.
#     - SpaceKai flag `landscapePlayer` = decorative (0 real call sites).
#
#   What the fix requires (for the next coding session):
#     add a landscape branch INSIDE NowPlayingScreen/NowPlayingScreenContent
#     (`wDP > hDP`): same content, redistributed horizontally — artwork
#     centered, timeline across the full width, controls accessible. Layout
#     ONLY: no media reload, no video search, no new player instance.
#
# THIS GATE protects the ONE landscape fix that exists today (without it the
# main artwork is "trapped in a portrait rectangle", the documented symptom):
#   FAIL if `.aspectRatio(1f, matchHeightConstraintsFirst = true)` disappears
#   from NowPlayingScreen.kt (an upstream merge reverting the SPACEKAI FIX).
#   FAIL if the phone-landscape / tablet-landscape branching in App.kt is
#   removed (nav rail on the right, inline tablet panel).
#
# Usage:  ./scripts/audit-landscape-player.sh
# Exit:   0 = current landscape handling intact, 1 = regression detected.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

NP="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/NowPlayingScreen.kt"
APP="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/App.kt"
fail=0
pass() { echo "  PASS: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

echo "============================================"
echo "LANDSCAPE PLAYER AUDIT (portrait-first layout, no orientation branch)"
echo "============================================"
echo "Finding: NowPlayingScreen.kt has NO orientation branch. Tablet landscape"
echo "renders it inline (same content); phone landscape renders the SAME"
echo "portrait-first layout as a full-screen overlay — the 'different screen'"
echo "symptom. FullscreenPlayer is a manual video destination, not rotation."
echo "Fix path: landscape branch (wDP > hDP) inside NowPlayingScreen — same"
echo "content redistributed horizontally, layout ONLY."
echo ""

# --- 1. The one existing landscape fix (main artwork square) ----------------
if [ ! -f "$NP" ]; then
  crit "NowPlayingScreen.kt missing: $NP"
else
  if grep -q 'aspectRatio(1f, matchHeightConstraintsFirst = true)' "$NP"; then
    pass "Main artwork landscape fix present (matchHeightConstraintsFirst)"
  else
    crit "Main artwork fix REMOVED — 'player trapped in a portrait rectangle' returns (SPACEKAI FIX reverted)"
  fi
  if grep -q 'SPACEKAI FIX: in landscape the width is the LONG side' "$NP"; then
    pass "SPACEKAI FIX comment present"
  else
    warn2=1
    echo "  WARN: SPACEKAI FIX comment removed (fix may still be there)"
  fi
fi

# --- 2. App-level landscape branching (nav rail + inline tablet panel) -------
if [ ! -f "$APP" ]; then
  crit "App.kt missing: $APP"
else
  if grep -q 'isPhoneLandscape = !isTablet && currentOrientation() == Orientation.LANDSCAPE' "$APP"; then
    pass "Phone-landscape detection present (nav moves to the right edge)"
  else
    crit "Phone-landscape detection removed — bottom bar eats the short landscape height"
  fi
  if grep -q 'isShowNowPlaylistScreen && !isTabletLandscape' "$APP"; then
    pass "Phone-landscape full-screen overlay guarded by !isTabletLandscape"
  else
    crit "Phone-landscape overlay guard removed — tablet inline panel may regress"
  fi
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: current landscape handling intact. (Real landscape layout still"
  echo "        missing — SpaceKai landscapePlayer flag is decorative; see docs/FEATURE-AUDIT.md § Player paysage.)"
  exit 0
else
  echo "RESULT: LANDSCAPE PLAYER REGRESSION — release blocked (see scripts/audit-landscape-player.sh)"
  exit 1
fi
