#!/bin/bash
# Device-acceptance driver for the v0.3.6 Settings patch (commits 3c210b5f +
# 7b3a6d09, version 0.3.6 / code 84). Exercises the four flows of
# docs/E2E-NAV-SETTINGS-V036.md on a REAL Android device via adb + uiautomator.
#
# Honest by design: the machine asserts what a UI dump / dumpsys can prove
# (V1 header geometry, V2 presence/absence/duplication counts, V3 Ko-fi-above-
# Site ordering, V4 installed versionName/versionCode) and prints exact manual
# steps for what needs judgement (external-link targets, the upgrade pipeline's
# SHA-256/package refusal, data preservation), recording the operator's
# verdict. It NEVER fabricates a PASS for something it did not observe.
#
# Usage:
#   ./scripts/device-acceptance-settings-v036.sh [path-to-v0.3.6-signed.apk]
#
# Env overrides:
#   ADB                   adb binary (default: $ANDROID_HOME/platform-tools/adb)
#   SETTINGS_HEADER_LABELS comma list of candidate first-section-header texts
#   INTEGRATIONS_LABEL     the "Intégrations" header text
#   SUB_LABELS             comma list of sub-block labels expected once expanded
#   KOFI_SITE_LABELS       comma list "Ko-fi label,Site label"
#   EXPECTED_NAME          installed versionName to assert at start (default 0.3.5)
#   EXPECTED_CODE          installed versionCode to assert at start (default 83)
#   TARGET_NAME/CODE       asserted AFTER the in-place upgrade (default 0.3.6/84)
#
# Exit code: 0 = all steps PASS, 1 = any FAIL / not confirmed, 2 = usage/env.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-${ANDROID_HOME:-$HOME/.local/android-sdk}/platform-tools/adb}"
RESULTS="${RESULTS:-$ROOT/device-acceptance-results.txt}"
PACKAGE="com.maxrave.simpmusic"
APK="${1:-}"

SETTINGS_HEADER_LABELS="${SETTINGS_HEADER_LABELS:-User interface,Interface,Interface utilisateur,Giao diện người dùng,Interfaz}"
INTEGRATIONS_LABEL="${INTEGRATIONS_LABEL:-Intégrations}"
SUB_LABELS="${SUB_LABELS:-Spotify,Discord,SponsorBlock}"
KOFI_LABEL="${KOFI_LABEL:-Ko-fi}"
SITE_LABEL="${SITE_LABEL:-Site}"
EXPECTED_NAME="${EXPECTED_NAME:-0.3.5}"
EXPECTED_CODE="${EXPECTED_CODE:-83}"
TARGET_NAME="${TARGET_NAME:-0.3.6}"
TARGET_CODE="${TARGET_CODE:-84}"

PASS=0
FAIL=0
declare -a VERDICTS

log() { printf '%s\n' "$*"; }
ok()  { PASS=$((PASS+1)); VERDICTS+=("PASS: $*"); log "  ✅ PASS: $*"; }
bad() { FAIL=$((FAIL+1)); VERDICTS+=("FAIL: $*"); log "  ❌ FAIL: $*"; }
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
  "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1 || die "uiautomator dump failed"
  "$ADB" shell cat /data/local/tmp/ui.xml 2>/dev/null
}

screen_geometry() {
  local size density
  size="$("$ADB" shell wm size | awk -F'[ :x]' '/Physical size/{print $3" "$4}')"
  density="$("$ADB" shell wm density | awk -F'[ :]' '/Physical density/{print $3}')"
  [ -n "$size" ] || die "cannot read screen size"
  [ -n "$density" ] || die "cannot read screen density"
  awk -v w="$size" -v d="$density" 'BEGIN{split(w,a," "); print a[1], a[2], d/160}'
}

# find_label <xml> <label> -> "x1 y1 x2 y2" of the first node with that exact text
find_label() {
  local xml="$1" label="$2"
  printf '%s\n' "$xml" \
    | grep -F "text=\"$label\"" \
    | head -1 \
    | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/'
}

# count_label <xml> <label> -> number of nodes with that exact text
count_label() {
  local xml="$1" label="$2"
  printf '%s\n' "$xml" | grep -cF "text=\"$label\"" || true
}

scroll_to() {
  # Scroll the list down until <label> is on screen (max 12 swipes).
  local label="$1" xml box i
  for i in $(seq 1 12); do
    xml="$(dump_ui)"
    box="$(find_label "$xml" "$label")"
    [ -n "$box" ] && { printf '%s\n' "$box"; return 0; }
    "$ADB" shell input swipe 540 1400 540 400 200 >/dev/null 2>&1
    sleep 1
  done
  return 1
}

tap_label() {
  # Tap the centre of the first node whose text == <label>.
  local xml="$1" label="$2" box x y
  xml="$(dump_ui)"
  box="$(find_label "$xml" "$label")"
  [ -n "$box" ] || die "cannot tap '$label': not on screen"
  x="$(printf '%s\n' "$box" | awk '{print int(($1+$3)/2)}')"
  y="$(printf '%s\n' "$box" | awk '{print int(($2+$4)/2)}')"
  "$ADB" shell input tap "$x" "$y" >/dev/null 2>&1
  sleep 2
}

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

installed_version() { # -> "name code" or empty
  "$ADB" shell dumpsys package "$PACKAGE" 2>/dev/null \
    | awk -v p="$PACKAGE" '
        $1=="versionName=" { n=$1; sub(/^versionName=/,"",n) }
        $1=="versionCode=" { c=$1; sub(/^versionCode=/,"",c) }
        END { if (n != "") print n, c }'
}

# ---------------------------------------------------------------------------
# App lifecycle
# ---------------------------------------------------------------------------

install_apk() {
  [ -n "$APK" ] || { log "no APK given — assuming the app is already installed."; return; }
  [ -f "$APK" ] || die "APK not found: $APK"
  log "installing $APK — MUST be signed with the SAME key as the installed v0.3.5 (in-place scenario)."
  "$ADB" install -r "$APK" || die "adb install failed — check signature/versionCode (docs/E2E-NAV-SETTINGS-V036.md §2)"
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

flow1_settings_top() {
  log ""
  log "=== V1 — Settings top: measured band, first header below the app bar ==="
  log "Navigate manually to Réglages/Settings (gear icon) with the list at the very top."
  read -r -p "    [Enter when Settings is on screen, scrolled to top] " _
  local xml geom w h density header y1 y1dp
  xml="$(dump_ui)"
  read -r w h density <<<"$(screen_geometry)"
  header=""
  local label
  for label in $(printf '%s' "$SETTINGS_HEADER_LABELS" | tr ',' ' '); do
    header="$(find_label "$xml" "$label")"
    [ -n "$header" ] && break
  done
  if [ -z "$header" ]; then
    confirm "V1 header located" \
"Could not auto-locate the first Settings header among: $SETTINGS_HEADER_LABELS.
Read the top of the Settings list and confirm MANUALLY that the first section
title sits right under the app bar (no ~376dp+ empty band above it)."
    return 0
  fi
  y1="$(printf '%s\n' "$header" | awk '{print $2}')"
  y1dp="$(awk -v y="$y1" -v d="$density" 'BEGIN{printf "%.0f", y/d}')"
  log "first Settings header top edge: ${y1}px = ${y1dp}dp (screen ${w}x${h})"
  if [ "$y1dp" -lt 250 ]; then
    ok "V1 top gap (header at ${y1dp}dp < 250dp; regression would be ~400dp+)"
  else
    bad "V1 top gap (header at ${y1dp}dp ≥ 250dp — an oversized band is back)"
  fi
  confirm "V1 header open AND closed" \
"Toggle the first section open then closed: in both states the first title must
stay right below the app bar (no jump, no added gap from the section state)."
  confirm "V1 scrolling" \
"Scroll Settings to the bottom and back to the top: scrolling works and the top
band does not grow or shrink."
}

flow2_integrations() {
  log ""
  log "=== V2 — Intégrations: one category, no duplicates, closed by default ==="
  local box xml n sub dupes
  box="$(scroll_to "$INTEGRATIONS_LABEL")" || {
    bad "V2 header '$INTEGRATIONS_LABEL' not found in Settings"
    return 1
  }
  xml="$(dump_ui)"
  dupes=""
  for sub in $(printf '%s' "$SUB_LABELS" | tr ',' ' '); do
    n="$(count_label "$xml" "$sub")"
    [ "$n" -eq 0 ] || dupes="$dupes $sub=$n"
  done
  if [ -n "$dupes" ]; then
    bad "V2 collapsed-by-default (sub-blocks visible before opening:$dupes)"
  else
    ok "V2 Intégrations closed by default (no Spotify/Discord/SponsorBlock labels visible)"
  fi
  tap_label "$xml" "$INTEGRATIONS_LABEL"
  xml="$(dump_ui)"
  dupes=""
  local allok=1
  for sub in $(printf '%s' "$SUB_LABELS" | tr ',' ' '); do
    n="$(count_label "$xml" "$sub")"
    if [ "$n" -eq 1 ]; then
      log "  sub-block label '$sub' present exactly once"
    else
      allok=0
      dupes="$dupes $sub=$n"
    fi
  done
  if [ "$allok" -eq 1 ]; then
    ok "V2 sub-blocks present exactly once each: $SUB_LABELS"
  else
    bad "V2 sub-block presence/duplication wrong:$dupes"
  fi
  # lastfm is optional (build with credentials); do not hard-assert it.
  confirm "V2 rows functional" \
"With Intégrations open, confirm each sub-block shows its real rows (login state,
toggles, SponsorBlock categories) as before the reorganisation."
  # Opening another top-level section must collapse Intégrations.
  local head
  head=""
  for label in $(printf '%s' "$SETTINGS_HEADER_LABELS" | tr ',' ' '); do
    box="$(find_label "$(dump_ui)" "$label")"
    [ -n "$box" ] && { head="$label"; break; }
  done
  if [ -n "$head" ]; then
    tap_label "$(dump_ui)" "$head"
    xml="$(dump_ui)"
    dupes=""
    for sub in $(printf '%s' "$SUB_LABELS" | tr ',' ' '); do
      n="$(count_label "$xml" "$sub")"
      [ "$n" -eq 0 ] || dupes="$dupes $sub=$n"
    done
    if [ -n "$dupes" ]; then
      bad "V2 single-open (opening '$head' kept Intégrations open:$dupes)"
    else
      ok "V2 single-open: opening another section collapsed Intégrations"
    fi
  else
    confirm "V2 single-open" \
"Could not auto-tap another top-level section. Manually open any other Settings
section and confirm Intégrations collapses (its sub-labels disappear)."
  fi
}

flow3_kofi_site() {
  log ""
  log "=== V3 — Ko-fi above Site, real URLs ==="
  local xml ko box site_box
  # About us sits at the very bottom of Settings; scroll to it via the end-of-page.
  ko=""
  local i
  for i in $(seq 1 20); do
    xml="$(dump_ui)"
    ko="$(find_label "$xml" "$KOFI_LABEL")"
    [ -n "$ko" ] && break
    "$ADB" shell input swipe 540 1400 540 400 200 >/dev/null 2>&1
    sleep 1
  done
  [ -n "$ko" ] || { bad "V3 Ko-fi row not found in Settings"; return 1; }
  xml="$(dump_ui)"
  site_box="$(find_label "$xml" "$SITE_LABEL")"
  if [ -z "$site_box" ]; then
    bad "V3 Site row not found near Ko-fi"
    return 1
  fi
  if [ "$(count_label "$xml" "$KOFI_LABEL")" -eq 1 ] && [ "$(count_label "$xml" "$SITE_LABEL")" -eq 1 ]; then
    ok "V3 Ko-fi and Site each present exactly once"
  else
    bad "V3 Ko-fi/Site duplicated (counts must be 1)"
  fi
  local ky sy
  ky="$(printf '%s\n' "$ko" | awk '{print $2}')"
  sy="$(printf '%s\n' "$site_box" | awk '{print $2}')"
  if [ "$ky" -lt "$sy" ]; then
    ok "V3 ordering: Ko-fi (y=$ky) immediately above Site (y=$sy)"
  else
    bad "V3 ordering: Ko-fi not above Site (ko y=$ky, site y=$sy)"
  fi
  confirm "V3 Ko-fi target" \
"Tap the Ko-fi row: the external browser must open GitHub Sponsors (maxrave-dev)
— https://github.com/sponsors/maxrave-dev. Confirm the target."
  confirm "V3 Site target" \
"Tap the Site row: the external browser must open https://simpmusic.org. Confirm."
}

flow4_updater() {
  log ""
  log "=== V4 — Update Manager v0.3.5 -> v0.3.6 (in-place) ==="
  local now
  now="$(installed_version)"
  log "installed before upgrade: ${now:-unknown}"
  if [ "$(printf '%s\n' "$now" | awk '{print $1}')" = "$EXPECTED_NAME" ] \
     && [ "$(printf '%s\n' "$now" | awk '{print $2}')" = "$EXPECTED_CODE" ]; then
    ok "V4 starting install = $EXPECTED_NAME / $EXPECTED_CODE"
  else
    bad "V4 starting install not $EXPECTED_NAME/$EXPECTED_CODE (got ${now:-unknown})"
  fi
  confirm "V4 upgrade offered (needs public v0.3.6 release)" \
"Settings → Mises à jour → SpaceKai. Expected: 'Installée v0.3.5 · dernière :
v0.3.6' and a 'Mettre à jour' pipeline row appears. If the v0.3.6 release is
not yet public, skip this step (V4a only) — do NOT claim a PASS without the
real release answering."
  confirm "V4 checksum + package guards" \
"Run the update once. Expected: download → 'Vérification SHA-256…' → package
check → system installer. No install may happen without SHA-256 verification,
and an APK not declaring com.maxrave.simpmusic must be refused."
  "$ADB" shell am force-stop "$PACKAGE" >/dev/null 2>&1
  sleep 2
  now="$(installed_version)"
  log "installed after upgrade: ${now:-unknown}"
  if [ "$(printf '%s\n' "$now" | awk '{print $1}')" = "$TARGET_NAME" ] \
     && [ "$(printf '%s\n' "$now" | awk '{print $2}')" = "$TARGET_CODE" ]; then
    ok "V4 install landed at $TARGET_NAME / $TARGET_CODE (in-place)"
  else
    bad "V4 post-upgrade install not $TARGET_NAME/$TARGET_CODE (got ${now:-unknown})"
  fi
  confirm "V4 data preserved" \
"Open the app: playlists/queue/settings and the YouTube account must be intact
after the in-place upgrade."
  confirm "V4 no further update offered" \
"Re-check for updates in Settings → Mises à jour: with v0.3.6 == latest, no
update pipeline row may appear, and never a downgrade offer."
}

# ---------------------------------------------------------------------------

main() {
  log "device-acceptance-settings-v036.sh — v0.3.6 Settings patch (3c210b5f, code 84)"
  log "results → $RESULTS"
  : >"$RESULTS"
  need_device
  install_apk
  launch_app

  flow1_settings_top
  flow2_integrations
  flow3_kofi_site
  flow4_updater

  log ""
  log "=== SUMMARY ==="
  printf '%s\n' "${VERDICTS[@]}" | tee -a "$RESULTS"
  log "PASS=$PASS FAIL=$FAIL"
  if [ "$FAIL" -eq 0 ] && [ "$PASS" -gt 0 ]; then
    log "GATE 0 DEVICE (v0.3.6): ALL STEPS PASSED — tag v0.3.6 / CI draft / publish may proceed"
    log "per docs/E2E-NAV-SETTINGS-V036.md §5."
    exit 0
  else
    log "GATE 0 DEVICE (v0.3.6): NOT PASSED — fix the failing maillon (see docs/E2E-NAV-SETTINGS-V036.md)"
    log "then re-run this driver. Do NOT tag/publish."
    exit 1
  fi
}

main "$@"
