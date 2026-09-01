#!/bin/bash
# Pre-release report: checklist of every check that must pass before a
# SpaceKai release is published. Run by the release workflow before
# `gh release create`; can also be run locally before tagging.
#
# Usage:  ./scripts/pre-release-report.sh [release-dir]
#   release-dir  default: ./androidApp/build/outputs/apk/release
#
# Exit code: 0 = all critical checks passed, 1 = a critical check failed.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RELEASE_DIR="${1:-./androidApp/build/outputs/apk/release}"

VERSION_NAME=$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
VERSION_CODE=$(grep '^version-code' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
EXPECTED_APK="SpaceKai-v${VERSION_NAME}.apk"

echo "============================================"
echo "SPACEKAI PRE-RELEASE REPORT  v${VERSION_NAME} (code ${VERSION_CODE})"
echo "============================================"

fail=0
pass() { echo "  PASS: $1"; }
warn() { echo "  WARN: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

# --- Android ----------------------------------------------------------------
echo ""
echo "## Android"
APKS=$(ls "$RELEASE_DIR"/*.apk 2>/dev/null || true)
COUNT=$(printf '%s\n' "$APKS" | grep -c '\.apk$' || true)

if [ -z "$APKS" ]; then
  crit "No APK found in $RELEASE_DIR"
elif [ "$COUNT" -ne 1 ]; then
  crit "Expected exactly 1 APK, found $COUNT — refusing split/debug/unsigned sets"
else
  pass "Single APK: $(basename "$APKS")"
  case "$APKS" in
    *debug*|*unsigned*|*-arm64-v8a*|*-armeabi-v7a*|*-x86_64*|*aligned*)
      crit "APK name suggests a non-release variant: $(basename "$APKS")" ;;
    *) pass "APK name is a release variant" ;;
  esac
  # Signature check via apksigner when available.
  if command -v apksigner >/dev/null 2>&1; then
    if apksigner verify "$APKS" >/dev/null 2>&1; then
      pass "APK signature verified (apksigner)"
    else
      crit "APK signature verification FAILED (apksigner)"
    fi
  else
    warn "apksigner not on PATH — signature not verified in this environment"
  fi
  # Signing-identity pin: the APK must be signed with the SpaceKai key
  # (CN=Sankamusic Dev, verified against the genuine v0.3.0/v0.3.2 assets).
  # A rotated key would be refused by Android on install-over ("Application
  # non installée"), silently breaking the in-app update. Same pin as
  # verify-release.sh; unzip + openssl are preinstalled on ubuntu-latest.
  SPACEKAI_CERT_SHA256="D9:BA:FD:4F:AB:87:15:DB:03:B2:67:11:E3:A8:42:A9:6E:90:BA:BA:BF:1A:83:3F:63:57:A9:49:B4:09:63:7E"
  if command -v unzip >/dev/null 2>&1 && command -v openssl >/dev/null 2>&1; then
    RSA_FILE=$(unzip -Z1 "$APKS" 'META-INF/*.RSA' 2>/dev/null | head -1)
    if [ -z "$RSA_FILE" ]; then
      crit "no META-INF/*.RSA signing block found in APK — unsigned or v1-scheme-less APK"
    else
      CERT_FP=$(
        unzip -p "$APKS" "$RSA_FILE" 2>/dev/null |
          openssl pkcs7 -inform DER -print_certs 2>/dev/null |
          openssl x509 -fingerprint -sha256 -noout 2>/dev/null |
          sed 's/.*=//; s/://g' | tr '[:upper:]' '[:lower:]'
      )
      EXPECTED=$(echo "$SPACEKAI_CERT_SHA256" | sed 's/://g' | tr '[:upper:]' '[:lower:]')
      if [ -n "$CERT_FP" ] && [ "$CERT_FP" = "$EXPECTED" ]; then
        pass "APK signed with the pinned SpaceKai key (SHA-256 $SPACEKAI_CERT_SHA256)"
      else
        crit "APK signing fingerprint MISMATCH — got '$CERT_FP', expected '$SPACEKAI_CERT_SHA256'"
      fi
    fi
  else
    warn "unzip/openssl not available — signing fingerprint not verified in this environment"
  fi
fi

# --- Desktop ----------------------------------------------------------------
echo ""
echo "## Desktop"
DESKTOP_DIR="$RELEASE_DIR"
if [ -n "${DESKTOP_ARTIFACTS_DIR:-}" ] && [ -d "$DESKTOP_ARTIFACTS_DIR" ]; then
  DESKTOP_DIR="$DESKTOP_ARTIFACTS_DIR"
fi
APPIMAGE=$(ls "$DESKTOP_DIR"/*.AppImage 2>/dev/null | head -1 || true)
DMG=$(ls "$DESKTOP_DIR"/*.dmg 2>/dev/null | head -1 || true)
# The Windows release asset is the offline installer zip (install.bat + crt +
# msix), not a bare .msix — the zip is what the release page actually ships.
WINZIP=$(ls "$DESKTOP_DIR"/*windows-installer.zip 2>/dev/null | head -1 || true)
MSIX=$(ls "$DESKTOP_DIR"/*.msix 2>/dev/null | head -1 || true)

[ -n "$APPIMAGE" ] && pass "Linux AppImage: $(basename "$APPIMAGE")" || warn "No AppImage found (desktop build may not have run)"
[ -n "$DMG" ]      && pass "macOS DMG: $(basename "$DMG")"        || warn "No DMG found (desktop build may not have run)"
if [ -n "$WINZIP" ]; then
  pass "Windows installer: $(basename "$WINZIP")"
elif [ -n "$MSIX" ]; then
  pass "Windows MSIX: $(basename "$MSIX")"
else
  warn "No Windows installer found (desktop build may not have run)"
fi

# --- Upstream ---------------------------------------------------------------
echo ""
echo "## Upstream"
if git rev-parse --git-dir >/dev/null 2>&1 && git remote get-url upstream >/dev/null 2>&1; then
  UPSTREAM_VER=$(git show "upstream/main:gradle/libs.versions.toml" 2>/dev/null | grep '^version-name' | head -1 | cut -d'"' -f2 || echo "?")
  pass "Based on SimpMusic v${UPSTREAM_VER:-?}"
else
  warn "No upstream remote — cannot report upstream version (fine outside a git clone)"
fi

# --- Upstream hotspots (silent reversions to SimpMusic) ----------------------
echo ""
echo "## Upstream hotspots (silent reversion)"
if bash scripts/audit-upstream-hotspots.sh >/dev/null 2>&1; then
  pass "conveyor vcs-url + update checker point to SpaceKai (see scripts/audit-upstream-hotspots.sh)"
else
  crit "Upstream hotspot(s) drifted to SimpMusic (conveyor vcs-url or UpdateRepositoryImpl missing SpaceKaiUpdateConfig). Blocking release (see scripts/audit-upstream-hotspots.sh)"
fi

# --- SpaceKai ZERO-FALSE-POSITIVE (decorative feature toggles) ---------------
echo ""
echo "## SpaceKai feature wiring (ZERO FALSE POSITIVE)"
if bash scripts/audit-features.sh >/dev/null 2>&1; then
  pass "No decorative SpaceKai toggles (see scripts/audit-features.sh)"
else
  crit "Decorative SpaceKai feature toggle(s) — feature declared but not wired. Blocking release (see docs/FEATURE-AUDIT.md + scripts/audit-features.sh)"
fi

# --- UI overlaps (size-transform in lazy items) ------------------------------
echo ""
echo "## UI regression (size-transform in lazy items)"
if bash scripts/audit-settings-ui.sh >/dev/null 2>&1; then
  pass "No size-transform animation in lazy items (see scripts/audit-settings-ui.sh)"
else
  crit "Suspicious size-transform in a lazy file — overlap-bug risk (settings texts on top of each other). Blocking release (see scripts/audit-settings-ui.sh + docs/ci-cd.md)"
fi

# --- Spotify flow (sp_dc cookie chain + redirect_uri consistency) ------------
echo ""
echo "## Spotify flow (sp_dc chain)"
if bash scripts/audit-spotify-flow.sh >/dev/null 2>&1; then
  pass "Spotify sp_dc login chain intact; no phantom/inconsistent redirect_uri (see scripts/audit-spotify-flow.sh)"
else
  crit "Spotify flow broken — login chain or redirect_uri consistency. Blocking release (see scripts/audit-spotify-flow.sh)"
fi

# --- Landscape player (portrait-first layout regression) --------------------
echo ""
echo "## Landscape player (layout regression)"
if bash scripts/audit-landscape-player.sh >/dev/null 2>&1; then
  pass "Landscape handling intact (artwork fix + phone/tablet branching). Real landscape layout still missing — see docs/FEATURE-AUDIT.md § Player paysage"
else
  crit "Landscape player regression — artwork fix or landscape branching reverted. Blocking release (see scripts/audit-landscape-player.sh)"
fi

# --- Updater flow (check + dialog + SpaceKai releases URL) -------------------
echo ""
echo "## Updater flow (detection chain)"
if bash scripts/audit-updater-flow.sh >/dev/null 2>&1; then
  pass "Updater detection chain intact (SpaceKai releases page). Internal download/install still missing — see docs/FEATURE-AUDIT.md § Mise à jour"
else
  crit "Updater flow broken — dialog or SPACEKAI CUSTOMIZATION reverted (users sent to SimpMusic page). Blocking release (see scripts/audit-updater-flow.sh)"
fi

# --- Navigation bar (styles + hide-text + landscape rail) --------------------
echo ""
echo "## Navigation bar (wiring)"
if bash scripts/audit-navigation.sh >/dev/null 2>&1; then
  pass "Nav styles + hide-text + landscape rail intact. Real customization (sections/order/shortcuts) still missing — see docs/FEATURE-AUDIT.md § Navigation"
else
  crit "Navigation wiring broken — style selection, hide-text or landscape rail reverted. Blocking release (see scripts/audit-navigation.sh)"
fi

# --- Dynamic color (capability + dark background pinning) --------------------
echo ""
echo "## Dynamic color (capability)"
if bash scripts/audit-dynamic-color.sh >/dev/null 2>&1; then
  pass "Dynamic color capability intact (system palette + wallpaper/seed). Dark background still pinned to black — known gap, see docs/FEATURE-AUDIT.md § Dynamic Color"
else
  crit "Dynamic color capability broken — system palette or wallpaper/seed scheme removed. Blocking release (see scripts/audit-dynamic-color.sh)"
fi

# --- Provider architecture (design-first, no provider code w/o abstraction) --
echo ""
echo "## Provider architecture (design-first)"
if bash scripts/audit-provider-arch.sh >/dev/null 2>&1; then
  pass "No provider code without the MusicProvider abstraction; no secrets (see docs/PROVIDER-ARCHITECTURE.md)"
else
  crit "Provider architecture violation — provider-specific code without abstraction, or secret in APK. Blocking release (see scripts/audit-provider-arch.sh)"
fi

# --- Icons ------------------------------------------------------------------
echo ""
echo "## Icons (locked)"
if bash scripts/verify-icons.sh >/dev/null 2>&1; then
  pass "circle_app_icon.png / app_icon.png UNCHANGED"
else
  crit "Brand icons CHANGED — release blocked (see scripts/verify-icons.sh)"
fi

# --- Performance keys (unstable hashCode lazy keys) ------------------------
echo ""
echo "## Perf: lazy keys (stable)"
if bash scripts/verify-perf-keys.sh >/dev/null 2>&1; then
  pass "No unstable hashCode() lazy keys (see scripts/verify-perf-keys.sh)"
else
  crit "Unstable hashCode() lazy keys found — release blocked (see scripts/verify-perf-keys.sh)"
fi

# --- Gate parity (the 3 gate lists must never drift) -----------------------
echo ""
echo "## Gate parity (release + pre-tag + nightly)"
if bash scripts/check-gate-parity.sh >/dev/null 2>&1; then
  pass "Same gate set in pre-release-report / check-pre-tag / nightly (see scripts/check-gate-parity.sh)"
else
  crit "Gate parity drift — a gate runs in one path but not another (release vs pre-tag vs nightly). Blocking release (see scripts/check-gate-parity.sh)"
fi

# --- Feature audit (evidence-based FEATURE AUDIT report) --------------------
echo ""
echo "## Feature audit (evidence-based)"
if bash scripts/generate-feature-audit.sh >/dev/null 2>&1; then
  # The generator always exits 0; the gate interprets the generated report.
  # Table rows carry the verdict as **VERDICT**; the gaps/contradiction
  # sections use plain text, so these greps only hit the detail table.
  NBROKEN=$(grep -c '\*\*BROKEN\*\*' docs/FEATURE-AUDIT-REPORT.md || true)
  NPART=$(grep -c '\*\*PARTIALLY IMPLEMENTED\*\*' docs/FEATURE-AUDIT-REPORT.md || true)
  NNOT=$(grep -c '\*\*NOT IMPLEMENTED\*\*' docs/FEATURE-AUDIT-REPORT.md || true)
  NCONTRA=$(grep -c 'vs \*\*' docs/FEATURE-AUDIT-REPORT.md || true)
  if [ "${NBROKEN:-0}" -gt 0 ]; then
    crit "Feature audit: BROKEN feature(s) found — release blocked (see docs/FEATURE-AUDIT-REPORT.md)"
  else
    pass "Feature audit: no BROKEN feature (see docs/FEATURE-AUDIT-REPORT.md)"
  fi
  [ "${NNOT:-0}" -gt 0 ] && warn "Feature audit: ${NNOT} NOT IMPLEMENTED — must be listed in release notes 'Connu / non terminé'"
  [ "${NPART:-0}" -gt 0 ] && warn "Feature audit: ${NPART} PARTIALLY IMPLEMENTED — must be listed in release notes 'Connu / non terminé'"
  [ "${NCONTRA:-0}" -gt 0 ] && warn "Feature audit: ${NCONTRA} claim contradiction(s) — changelog/RELEASE.md claims a NOT-finished feature as done (see report)"
else
  crit "Feature audit generator failed (scripts/generate-feature-audit.sh)"
fi

# --- Changelog header (branding) -------------------------------------------
echo ""
echo "## Changelog header (branding)"
CLOG="fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"
if [ -f "$CLOG" ]; then
  HEADER=$(head -1 "$CLOG")
  case "$HEADER" in
    "SpaceKai v$VERSION_NAME"|"# SpaceKai v$VERSION_NAME") pass "changelog header '$HEADER' brands SpaceKai" ;;
    *) warn "changelog header '$HEADER' is not 'SpaceKai v$VERSION_NAME' — release body may be mis-branded" ;;
  esac
else
  warn "changelog $CLOG missing for version-code $VERSION_CODE"
fi

# --- Installation (manual) --------------------------------------------------
echo ""
echo "## Installation (manual)"
echo "  MANUAL: fresh install + upgrade from previous SpaceKai must be tested on a device/emulator"
echo "  MANUAL: if not tested, state clearly: installation réelle non testée"

# --- Summary ----------------------------------------------------------------
echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: ALL CRITICAL CHECKS PASSED"
  echo "============================================"
  exit 0
else
  echo "RESULT: CRITICAL CHECK(S) FAILED — release blocked"
  echo "============================================"
  exit 1
fi
