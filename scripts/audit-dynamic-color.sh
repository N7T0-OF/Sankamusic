#!/bin/bash
# audit-dynamic-color.sh — audit of the Dynamic Color state.
#
# DEFINITIVE FINDING (static audit 2026-08-26, composeApp):
#   The CAPABILITY exists in the base SimpMusic:
#     - System palette (Android 12+): platformDynamicColorScheme →
#       dynamicDarkColorScheme / dynamicLightColorScheme
#       (PlatformColorScheme.android.kt), gated by isWallpaperDynamicColorSupported().
#     - Wallpaper / custom seed: rememberDynamicColorScheme (materialkolor) in
#       Theme.kt, chosen via SettingScreen "theme_color_source"
#       (DEFAULT / WALLPAPER / CUSTOM).
#   The user's complaint is a REAL bug, confirmed in code:
#     - PlatformColorScheme.android.kt pins dark to pure black:
#       dynamicDarkColorScheme(context).copy(background = Color.Black, surface = Color.Black)
#       ("Keep the OLED look") — so even the SYSTEM dynamic scheme has a black
#       background; only accents follow the palette.
#     - Theme.kt builds the fallback with isAmoled = isDark — the seed scheme
#       dark also pins background/surface to pure black.
#     ⇒ In dark mode the background NEVER follows Dynamic Color. That is
#       exactly "le fond noir fixe" the user reports. Fix path: keep the OLED
#       pinning only as an explicit option, not the default for dynamic schemes.
#   SpaceKai flag `dynamicColor` is DECORATIVE (0 real call sites) — it toggles
#   nothing; the base capability exists independently of it.
#   Also: 194 hardcoded Color.Black / Color(0xFF000000) occurrences across
#   composeApp (many legitimate: text over artwork, immersive ForceDarkContent
#   screens, scrims — but the count is worth a manual pass).
#
# THIS GATE protects the capability (an upstream merge must not remove it):
#   FAIL if the system palette, the wallpaper/seed scheme, or the settings
#   selector disappears. The black-pinning is documented as the known gap.
#
# Usage:  ./scripts/audit-dynamic-color.sh
# Exit:   0 = capability intact (black-pinning gap documented), 1 = regression.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CM="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"
AND="composeApp/src/androidMain/kotlin/com/maxrave/simpmusic"
THEME="$CM/ui/theme/Theme.kt"
SETTINGS="$CM/ui/screen/home/SettingScreen.kt"
ANDROID_PLATFORM="$AND/expect/ui/PlatformColorScheme.android.kt"
fail=0
pass() { echo "  PASS: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

echo "============================================"
echo "DYNAMIC COLOR AUDIT (capability exists, dark background pinned to black)"
echo "============================================"
echo "Finding: system palette (Android 12+) + wallpaper/seed schemes exist."
echo "CONFIRMED BUG: dark backgrounds are pinned to pure black even with a"
echo "dynamic scheme (PlatformColorScheme.android.kt + isAmoled = isDark) —"
echo "the 'fond noir fixe'. SpaceKai flag dynamicColor is decorative."
echo ""

# --- 1. System palette capability -------------------------------------------
if [ ! -f "$ANDROID_PLATFORM" ]; then
  crit "PlatformColorScheme.android.kt missing — system dynamic palette gone"
else
  if grep -qE 'dynamic(Dark|Light)ColorScheme\(' "$ANDROID_PLATFORM"; then
    pass "System palette wired (dynamicDark/LightColorScheme, Android 12+)"
  else
    crit "System dynamic palette removed — Android never follows the system colors"
  fi
fi
if grep -q 'fun platformDynamicColorScheme' "$CM/expect/ui/PlatformColorScheme.kt" 2>/dev/null; then
  pass "platformDynamicColorScheme expect/actual present"
else
  crit "platformDynamicColorScheme expect gone — multi-platform dynamic color removed"
fi

# --- 2. Wallpaper / seed schemes ---------------------------------------------
if [ ! -f "$THEME" ]; then
  crit "Theme.kt missing: $THEME"
else
  if grep -q 'rememberDynamicColorScheme' "$THEME"; then
    pass "Wallpaper/custom-seed scheme present (materialkolor rememberDynamicColorScheme)"
  else
    crit "rememberDynamicColorScheme removed — no wallpaper/custom theme possible"
  fi
fi

# --- 3. Settings selector -----------------------------------------------------
if [ ! -f "$SETTINGS" ]; then
  crit "SettingScreen.kt missing: $SETTINGS"
else
  if grep -q 'THEME_COLOR_WALLPAPER' "$SETTINGS" && grep -q 'THEME_COLOR_CUSTOM' "$SETTINGS"; then
    pass "Theme color-source selector present (DEFAULT / WALLPAPER / CUSTOM)"
  else
    crit "Theme color-source selector removed — users cannot pick wallpaper/custom"
  fi
fi

# --- 4. Known gap: dark background pinned to black ----------------------------
echo ""
echo "Known gap (documented, not blocking):"
if grep -q 'background = Color.Black' "$ANDROID_PLATFORM" 2>/dev/null; then
  echo "  GAP: system dark scheme overrides background/surface to Color.Black"
  echo "       (PlatformColorScheme.android.kt) — dark bg never follows the palette"
fi
if grep -q 'isAmoled = isDark' "$THEME" 2>/dev/null; then
  echo "  GAP: seed scheme built with isAmoled = isDark (Theme.kt) — dark bg pinned black"
fi
BLACKS=$(grep -rn --include='*.kt' 'Color.Black\|Color(0xFF000000)' "$CM" 2>/dev/null | grep -cv 'ui/theme/Color.kt' || true)
echo "  INFO: ${BLACKS} hardcoded Color.Black / Color(0xFF000000) in composeApp (many legit)"

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: dynamic color capability intact. Dark background pinning to black"
  echo "        remains the known gap — fix before claiming dynamic background."
  exit 0
else
  echo "RESULT: DYNAMIC COLOR CAPABILITY BROKEN — release blocked (see scripts/audit-dynamic-color.sh)"
  exit 1
fi
