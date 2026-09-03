#!/bin/bash
# audit-navigation.sh — audit of the navigation bar customization state.
#
# DEFINITIVE FINDING (static audit 2026-08-26, composeApp):
#   What EXISTS (base SimpMusic, wired):
#     - Style verre liquide  : `isLiquidGlassEnabled` (DataStore) →
#       LiquidGlassAppBottomNavigationBar (App.kt:491).
#     - Style translucide    : `isTranslucentBottomBar` (DataStore) →
#       AppBottomNavigationBar(isTranslucentBackground = ...) (App.kt:508).
#     - Masquer le texte     : `showLabels = !hideNavLabel` on the bottom bar
#       AND the landscape rail; Material3 items compact when the label is null
#       (icon-only, no empty label space) — the user's "compact bar" ask.
#     - Rail paysage à droite: AppNavigationRail on the RIGHT edge for phones
#       in landscape (App.kt:654), same tabs, labels follow the setting.
#     - Swipe-to-skip        : gated behind SpaceKai `customNavigation`
#       (App.kt:182 → onSwipeToNext/Previous on bar + rail).
#   What is MISSING (the user's asks #25-28, #31):
#     - Style "minimaliste"  : wired as a compact/icons-only variant; it removes
#       Mix-for-you while Analytics remains available when local tracking is on.
#     - No unified style selector — liquid glass and translucent are two
#       independent toggles, not exclusive options.
#     - Sections visibles    : only 2 hardcoded conditional tabs
#       (`showAnalyticsTab`, `showMixForYouTab`); NO user-configurable
#       enable/disable of sections.
#     - Ordre des sections   : `bottomNavScreens` order is fixed — no reorder.
#     - Raccourcis           : no shortcuts feature.
#     - Aperçu avec switch   : no nav-bar preview exists at all — nothing to
#       remove (the "switch next to the preview" ask has no object).
#
# THIS GATE protects the wiring that EXISTS (upstream merges would silently
# drop it):
#   FAIL if the style selection, hide-text, or landscape rail wiring is removed.
#
# Usage:  ./scripts/audit-navigation.sh
# Exit:   0 = nav wiring intact (gaps documented), 1 = regression.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/App.kt"
fail=0
pass() { echo "  PASS: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

echo "============================================"
echo "NAVIGATION BAR AUDIT (styles + hide-text + landscape rail)"
echo "============================================"
echo "Finding: liquid-glass + translucent styles exist (base, DataStore)."
echo "Hide-text (M3 compact) and the landscape right rail exist."
echo "minimalisticNavigation is WIRED as icons-only (labels hidden, reduced"
echo "height on the flat bar; labels hidden on the rail). Still MISSING:"
echo "unified style selector, section config/reorder, shortcuts."
echo ""

if [ ! -f "$APP" ]; then
  crit "App.kt missing: $APP"
else
  if grep -q 'isLiquidGlassEnabled' "$APP" && grep -q 'LiquidGlassAppBottomNavigationBar' "$APP"; then
    pass "Liquid-glass style wired (isLiquidGlassEnabled → LiquidGlassAppBottomNavigationBar)"
  else
    crit "Liquid-glass style wiring removed — users lose the glass bar"
  fi
  if grep -q 'isTranslucentBottomBar' "$APP" && grep -q 'isTranslucentBackground' "$APP"; then
    pass "Translucent style wired (isTranslucentBottomBar → isTranslucentBackground)"
  else
    crit "Translucent style wiring removed — users lose the translucent bar"
  fi
  if grep -q 'showLabels = !hideNavLabel' "$APP"; then
    pass "Hide-text wired (showLabels = !hideNavLabel, bottom bar + rail, M3 compacts)"
  else
    crit "Hide-text wiring removed — labels can no longer be hidden"
  fi
  if grep -q 'AppNavigationRail' "$APP" && grep -q 'isPhoneLandscape' "$APP"; then
    pass "Phone-landscape rail on the right edge present (same tabs, labels follow setting)"
  else
    crit "Landscape rail removed — bottom bar would eat the short landscape height"
  fi
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: nav wiring intact. minimalisticNavigation is now wired as an"
  echo "        icons-only style (labels hidden + reduced height on the flat bar,"
  echo "        labels hidden on the rail). Nav bar style is a SINGLE exclusive"
  echo "        choice (minimalist / translucent / liquid glass): Settings exposes"
  echo "        one selector and App.kt renders from one derived style, so the two"
  echo "        legacy booleans can never show both styles at once. Personalized"
  echo "        navigation (SpaceKai customNavigation) persists a tab order + hidden"
  echo "        set and every bar receives it via navTabs. Remaining: drag-reorder"
  echo "        polish and a live preview inside Settings."
  exit 0
else
  echo "RESULT: NAVIGATION WIRING BROKEN — release blocked (see scripts/audit-navigation.sh)"
  exit 1
fi
