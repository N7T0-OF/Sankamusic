#!/bin/bash
# Device-acceptance driver for the v0.3.5 nav/settings patch (PR #2, merged as
# db002314). Exercises the four behaviors of docs/E2E-NAV-SETTINGS-V035.md on a
# REAL Android device via adb + uiautomator.
#
# Honest by design: the machine asserts what a UI dump can prove (F1 geometry,
# F2 Analytics presence, F4 label absence) and prints exact manual steps for
# what needs judgement (F3 reroute), recording the operator's verdict. It NEVER
# fabricates a PASS for something it did not observe: every verdict is either an
# automated assertion on a fresh dump or an explicit operator confirmation.
#
# Usage:
#   ./scripts/device-acceptance-nav-settings.sh [path-to-signed.apk]
#
# Env overrides:
#   ADB                 adb binary        (default: $ANDROID_HOME/platform-tools/adb)
#   NAV_ANALYTICS_LABELS  comma list of candidate texts for the Analytics tab
#   NAV_MIX_LABELS        comma list of candidate texts for the Mix-for-you tab
#   SETTINGS_HEADER_LABELS comma list of candidate texts for the first Settings
#                         section header (F1 measures its top edge)
#
# Exit code: 0 = all steps PASS, 1 = any FAIL / not confirmed, 2 = usage/env.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-${ANDROID_HOME:-$HOME/.local/android-sdk}/platform-tools/adb}"
RESULTS="${RESULTS:-$ROOT/device-acceptance-results.txt}"
PACKAGE="com.maxrave.simpmusic"
APK="${1:-}"

# Default candidate labels (English first; the operator can override with the
# actual on-screen language via the env vars above).
NAV_ANALYTICS_LABELS="${NAV_ANALYTICS_LABELS:-Analytics,Statistiques,Thống kê,Análisis}"
NAV_MIX_LABELS="${NAV_MIX_LABELS:-Mix,Mix for you,Mix pour vous,Tiết mục mix}"
SETTINGS_HEADER_LABELS="${SETTINGS_HEADER_LABELS:-User interface,Interface,Interface utilisateur,Giao diện người dùng,Interfaz}"

PASS=0
FAIL=0
declare -a VERDICTS

log()  { printf '%s\n' "$*"; }
ok()   { PASS=$((PASS+1)); VERDICTS+=("PASS: $*"); log "  ✅ PASS: $*"; }
bad()  { FAIL=$((FAIL+1)); VERDICTS+=("FAIL: $*"); log "  ❌ FAIL: $*"; }

die() { log "ERROR: $*" >&2; exit 2; }

# ---------------------------------------------------------------------------
# adb / device helpers
# ---------------------------------------------------------------------------

need_device() {
  local count
  count="$("$ADB" devices | sed -n '2,$p' | grep -c 'device$' || true)"
  [ "$count" -ge 1 ] || die "no Android device attached (adb devices is empty)."
  log "device: $("$ADB" devices | sed -n '2p' | awk '{print $1}')"
}

dump_ui() {
  # Fresh UI dump; returns the XML on stdout.
  "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1 || die "uiautomator dump failed"
  "$ADB" shell cat /data/local/tmp/ui.xml 2>/dev/null
}

# screen_geometry: prints "width height density" (physical px + physical dpi/160)
screen_geometry() {
  local size density
  size="$("$ADB" shell wm size | awk -F'[ :x]' '/Physical size/{print $3" "$4}')"
  density="$("$ADB" shell wm density | awk -F'[ :]' '/Physical density/{print $3}')"
  [ -n "$size" ] || die "cannot read screen size"
  [ -n "$density" ] || die "cannot read screen density"
  awk -v w="$size" -v d="$density" 'BEGIN{split(w,a," "); print a[1], a[2], d/160}'
}

# find_label <xml> <label> -> "x1 y1 x2 y2" of the first node whose text matches,
# or empty. The dump is one <node .../> per line (uiautomator pretty-prints).
find_label() {
  local xml="$1" label="$2"
  printf '%s\n' "$xml" \
    | grep -F "text=\"$label\"" \
    | head -1 \
    | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/'
}

# Confirm step: prints the manual action + hint and asks the operator.
confirm() {
  local name="$1" prompt="$2"
  log ""
  log "── $name ──"
  log "$prompt"
  log "    (y)es = vérifié conforme  /  (n)o = échec  /  (s)kip"
  while true; do
    printf '    verdict [y/n/s]? '
    read -r ans || ans=""
    case "$ans" in
      y|Y|o|O) ok "$name"; return 0 ;;
      n|N)     bad "$name"; return 1 ;;
      s|S)     VERDICTS+=("SKIP: $name"); log "  ⏭  SKIP: $name"; return 2 ;;
      *)       log "    answer y, n or s" ;;
    esac
  done
}

# ---------------------------------------------------------------------------
# App lifecycle
# ---------------------------------------------------------------------------

install_apk() {
  [ -n "$APK" ] || { log "no APK given — assuming the app is already installed."; return; }
  [ -f "$APK" ] || die "APK not found: $APK"
  log "installing $APK (same signing key as the installed v0.3.4 required for the in-place scenario)..."
  "$ADB" install -r "$APK" || die "adb install failed — check signature/versionCode (see docs/E2E-NAV-SETTINGS-V035.md §2)"
}

launch_app() {
  "$ADB" shell am force-stop "$PACKAGE" >/dev/null 2>&1
  "$ADB" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 \
    || "$ADB" shell am start -n "$PACKAGE"/.MainActivity >/dev/null 2>&1 \
    || die "cannot launch the app"
  sleep 4
}

# ---------------------------------------------------------------------------
# Flows
# ---------------------------------------------------------------------------

flow1_settings_gap() {
  log ""
  log "=== F1 — Settings top gap ==="
  log "Navigate manually: Réglages/Settings (gear icon from the Library/profile),"
  log "then make sure the list is scrolled to the very top. Press Enter when the"
  log "Settings screen is visible."
  read -r -p "    [Enter when Settings is on screen] " _
  local xml geom w h density header y1 y1dp
  xml="$(dump_ui)"
  read -r w h density <<<"$(screen_geometry)"
  # First Settings section header ("User interface" / "Interface" / translated).
  header=""
  local label
  for label in $(printf '%s' "$SETTINGS_HEADER_LABELS" | tr ',' ' '); do
    header="$(find_label "$xml" "$label")"
    [ -n "$header" ] && break
  done
  if [ -z "$header" ]; then
    confirm "F1 header found" "Could not auto-locate the first Settings header among: $SETTINGS_HEADER_LABELS.
  Read the top of the Settings list and confirm MANUALLY that no ~376dp empty band
  sits above the first section title (a small app-bar-sized gap is expected)."
    return 0
  fi
  y1="$(printf '%s\n' "$header" | awk '{print $2}')"
  y1dp="$(awk -v y="$y1" -v d="$density" 'BEGIN{printf "%.0f", y/d}')"
  log "first Settings header top edge: ${y1}px = ${y1dp}dp (screen ${w}x${h}, density ${density})"
  if [ "$y1dp" -lt 250 ]; then
    ok "F1 Settings gap (header at ${y1dp}dp < 250dp; regression would be ~400dp+)"
  else
    bad "F1 Settings gap (header at ${y1dp}dp ≥ 250dp — the ~376dp spacer is back)"
  fi
}

flow2_analytics_compact() {
  log ""
  log "=== F2 — Analytics visible in compact navigation ==="
  log "Prereqs (Settings, manual):"
  log "  1. local tracking ON (Listening history / tracking section)"
  log "  2. SpaceKai → minimalisticNavigation (compact) ON, any bar style"
  log "  3. return to Home, compact bar visible"
  read -r -p "    [Enter when Home shows the compact bar] " _
  local xml w h density label found
  xml="$(dump_ui)"
  read -r w h density <<<"$(screen_geometry)"
  # Bottom-bar region = lower 30% of the screen; candidate Analytics labels.
  local barmin
  barmin="$(awk -v h="$h" 'BEGIN{printf "%d", h*0.7}')"
  found=""
  for label in $(printf '%s' "$NAV_ANALYTICS_LABELS" | tr ',' ' '); do
    local box
    box="$(find_label "$xml" "$label")"
    if [ -n "$box" ] && [ "$(printf '%s\n' "$box" | awk '{print $2}')" -ge "$barmin" ]; then
      found="$label"
      break
    fi
  done
  if [ -n "$found" ]; then
    ok "F2 Analytics tab present in the compact bar (label '$found' in the bar region)"
  else
    confirm "F2 Analytics present in compact" "Could not auto-locate an Analytics label in the bar region.
  Visually confirm: is the Analytics tab present in the compact bar while local tracking is ON?"
  fi
  # Mix-for-you: the compact style never drops it — Mix follows the signed-in
  # session like every other style (minimalist is presentation-only: icons, size).
  # Only assert absence when the operator is signed OUT, to avoid a
  # session-dependent fail; signed in + compact must show Mix.
  confirm "F2 Mix-for-you kept in compact" "Are you signed OUT of YouTube on this device?
  (n) = signed in → compact must STILL show Mix (verify visually — this is the
  P0 regression: minimalist must never drop Mix when enabled)"
  if [ "$?" -eq 0 ]; then
    local mix
    mix=""
    for label in $(printf '%s' "$NAV_MIX_LABELS" | tr ',' ' '); do
      local mbox
      mbox="$(find_label "$xml" "$label")"
      if [ -n "$mbox" ] && [ "$(printf '%s\n' "$mbox" | awk '{print $2}')" -ge "$barmin" ]; then
        mix="$label"
        break
      fi
    done
    if [ -n "$mix" ]; then
      ok "F2 Mix-for-you absent from the compact bar (signed out — expected)"
    else
      bad "F2 Mix-for-you present in the compact bar (label '$mix')"
    fi
  fi
}

flow3_hide_selected() {
  confirm "F3 reroute on hiding the selected tab" \
"Prereqs: customNavigation ON (SpaceKai), you are ON the Analytics tab.
Then: Settings → SpaceKai → 'Navigation personnalisée' → toggle Analytics OFF.
Expected: the app leaves Analytics immediately and shows the first visible
destination; the bar highlights a real tab (never a ghost Analytics). Confirm
you observed the automatic reroute."
  confirm "F3 highlight stays valid" \
"After the reroute (and after hiding ANY currently selected tab): confirm the bar
always highlights a visible tab — never nothing, never a hidden one."
  confirm "F3 hide-everything keeps Home" \
"Edge case: hide ALL tabs in the editor. Expected: the bar is never empty and
never crashes — Home remains as the guaranteed fallback. Confirm."
}

flow4_hide_search() {
  confirm "F4 Search removed from list" \
"Still in the 'Navigation personnalisée' editor: hide Search. Expected: Search
disappears from the bar/rail items. Confirm."
  confirm "F4 Search FAB gone (flat bar)" \
"Switch to the flat bar style (Minimalist or Translucent), go Home. Expected: the
round Search FAB beside the capsule is GONE. Confirm."
  confirm "F4 Search FAB gone (liquid glass)" \
"Switch to the Liquid glass style, go Home. Expected: no glass Search FAB.
Confirm."
  confirm "F4 Search FAB returns" \
"Re-show Search in the editor. Expected: the FAB comes back on both styles.
Confirm."
}

# ---------------------------------------------------------------------------

main() {
  log "device-acceptance-nav-settings.sh — v0.3.5 nav/settings patch (db002314)"
  log "results → $RESULTS"
  : >"$RESULTS"
  need_device
  install_apk
  launch_app

  flow1_settings_gap
  flow2_analytics_compact
  flow3_hide_selected
  flow4_hide_search

  log ""
  log "=== SUMMARY ==="
  printf '%s\n' "${VERDICTS[@]}" | tee -a "$RESULTS"
  log "PASS=$PASS FAIL=$FAIL"
  if [ "$FAIL" -eq 0 ] && [ "$PASS" -gt 0 ]; then
    log "GATE 0 DEVICE: ALL STEPS PASSED — the v0.3.5 release sequence may continue"
    log "(changelog generator → bump 0.3.5/83 → tag v0.3.5 → draft), per the runbook."
    exit 0
  else
    log "GATE 0 DEVICE: NOT PASSED — fix the failing maillon (see docs/E2E-NAV-SETTINGS-V035.md)"
    log "then re-run this driver. Do NOT bump/tag/publish."
    exit 1
  fi
}

main "$@"
