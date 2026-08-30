#!/bin/bash
# Pre-tag check: the gate between "ready to release" and "release.sh".
# Run on the LIVE repo (full checkout with core/, git, gh) immediately before
# ./scripts/release.sh. Prevents tagging a release whose changelog claims
# features the audit says are not finished, or whose gates fail.
#
# Usage:  ./scripts/check-pre-tag.sh
# Exit code: 0 = ALL CHECKS PASSED — release.sh may proceed.
#           1 = at least one critical check failed — do NOT tag.
#
# Deliberately NOT checked here: the APK. release.sh bumps the version and the
# CI builds + signs the artifact AFTER the tag; the APK gates run in
# pre-release-report.sh inside android-release.yml / publish-from-artifact.yml.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0
pass() { echo "  PASS: $1"; }
warn() { echo "  WARN: $1"; }
crit() { echo "  FAIL: $1"; fail=$((fail + 1)); }

echo "============================================"
echo "SPACEKAI PRE-TAG CHECK  (run before release.sh)"
echo "============================================"

# ---------------------------------------------------------------------------
# 1. Git — release.sh must commit, tag and push.
# ---------------------------------------------------------------------------
echo ""
echo "## 1. Git"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  pass "git repository initialized"
else
  crit "not a git repository — release.sh cannot commit/tag/push here"
fi
if [ -n "$(git remote get-url origin 2>/dev/null)" ]; then
  pass "origin remote configured"
else
  crit "no origin remote — release.sh cannot push"
fi

# ---------------------------------------------------------------------------
# 2. gh — the release flow publishes through the GitHub CLI.
# ---------------------------------------------------------------------------
echo ""
echo "## 2. GitHub CLI"
if command -v gh >/dev/null 2>&1; then
  pass "gh installed"
  if gh auth status >/dev/null 2>&1; then
    pass "gh authenticated"
  else
    crit "gh not authenticated — run: gh auth login"
  fi
else
  crit "gh not installed — the release workflow needs the GitHub CLI"
fi

# ---------------------------------------------------------------------------
# 3. SpaceKai feature wiring (ZERO FALSE POSITIVE).
# ---------------------------------------------------------------------------
echo ""
echo "## 3. Feature wiring (no decorative toggles)"
if bash scripts/audit-features.sh >/dev/null 2>&1; then
  pass "audit-features: no decorative SpaceKai toggles"
else
  crit "audit-features FAIL — decorative toggle(s): a feature is declared but not wired. Wire them or remove them (see docs/FEATURE-AUDIT.md)"
fi

# ---------------------------------------------------------------------------
# 4. Changelog claim cross-check — the changelog-73 pattern: a release note
#    that claims a NOT-finished feature as done. This is the check that must
#    catch "one style selector (Minimalist / …)" before the tag, not after.
# ---------------------------------------------------------------------------
echo ""
echo "## 4. Changelog vs audit (claim cross-check)"
if bash scripts/generate-feature-audit.sh >/dev/null 2>&1; then
  ncontra=$(grep -c 'vs \*\*' docs/FEATURE-AUDIT-REPORT.md || true)
  if [ "${ncontra:-0}" -eq 0 ]; then
    pass "no claim contradiction — the changelog does not claim a NOT-finished feature as done"
  else
    crit "${ncontra} claim contradiction(s) in the changelog — a feature the audit classifies as not finished is claimed as working. Fix the changelog line or wire the feature (see docs/FEATURE-AUDIT-REPORT.md → Contradictions)"
  fi
else
  crit "generate-feature-audit.sh failed to run"
fi

# 4b. Changelog header must brand the fork, not upstream. A "SimpMusic v…"
#     first line (the exact reversion the upstream-hotspots gate hunts in
#     code) would ship in the release body unchecked. Accept both the
#     generator's "# SpaceKai vX" and the committed "SpaceKai vX" forms.
echo ""
echo "## 4b. Changelog header (branding)"
VNAME_4B=$(grep '^version-name' gradle/libs.versions.toml 2>/dev/null | head -1 | cut -d'"' -f2)
VCODE_4B=$(grep '^version-code' gradle/libs.versions.toml 2>/dev/null | head -1 | cut -d'"' -f2)
CLOG_4B="fastlane/metadata/android/en-US/changelogs/${VCODE_4B}.txt"
if [ -f "$CLOG_4B" ]; then
  HEADER_4B=$(head -1 "$CLOG_4B")
  case "$HEADER_4B" in
    "SpaceKai v$VNAME_4B"|"# SpaceKai v$VNAME_4B")
      pass "changelog header '$HEADER_4B' brands SpaceKai and matches version-name" ;;
    *)
      crit "changelog header '$HEADER_4B' is not 'SpaceKai v$VNAME_4B' — a SimpMusic-branded or mismatched release note could ship" ;;
  esac
else
  crit "changelog $CLOG_4B missing for version-code $VCODE_4B"
fi

# ---------------------------------------------------------------------------
# 5. All critical gates, one by one (APK presence excluded — CI builds it).
# ---------------------------------------------------------------------------
echo ""
echo "## 5. Critical gates"
gate() { # gate <script> <description>
  local s="$1" d="$2"
  if bash "$s" >/dev/null 2>&1; then pass "$d"; else crit "$d — see $s"; fi
}
gate scripts/audit-upstream-hotspots.sh "upstream hotspots point to SpaceKai (conveyor vcs-url + update checker)"
gate scripts/audit-settings-ui.sh      "no size-transform animation in lazy items (settings overlap)"
gate scripts/audit-spotify-flow.sh     "Spotify sp_dc chain intact; no phantom redirect_uri"
gate scripts/audit-landscape-player.sh "landscape player handling intact (artwork fix + branching)"
gate scripts/audit-updater-flow.sh     "updater detection chain intact (SpaceKai releases page)"
gate scripts/audit-navigation.sh       "nav wiring intact (styles, hide-text, landscape rail)"
gate scripts/audit-dynamic-color.sh    "dynamic color capability intact (system palette + seed)"
gate scripts/audit-provider-arch.sh    "provider architecture respected (design-first, no secrets)"
gate scripts/verify-icons.sh           "locked icons unchanged"
gate scripts/verify-perf-keys.sh       "stable lazy-list keys (no full-list rebuild)"
gate scripts/check-gate-parity.sh      "gate parity: release / pre-tag / nightly lists in sync"

echo ""
echo "## 6. Feature audit (no BROKEN)"
nbroken=$(grep -c '\*\*BROKEN\*\*' docs/FEATURE-AUDIT-REPORT.md || true)
if [ "${nbroken:-0}" -eq 0 ]; then
  pass "no BROKEN feature (see docs/FEATURE-AUDIT-REPORT.md)"
else
  crit "BROKEN feature(s) found — release blocked"
fi

# ---------------------------------------------------------------------------
# 7. Version coherence (cheap sanity before the bump).
# ---------------------------------------------------------------------------
echo ""
echo "## 7. Version coherence"
VNAME=$(grep '^version-name' gradle/libs.versions.toml 2>/dev/null | head -1 | cut -d'"' -f2)
VCODE=$(grep '^version-code' gradle/libs.versions.toml 2>/dev/null | head -1 | cut -d'"' -f2)
if [ -n "$VNAME" ] && [ -n "$VCODE" ]; then
  pass "version $VNAME (code $VCODE) readable from libs.versions.toml"
else
  crit "could not read version-name / version-code from gradle/libs.versions.toml"
fi

# ---------------------------------------------------------------------------
echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: ALL CHECKS PASSED — release.sh may proceed."
  echo "Next: ./scripts/release.sh . --dry-run  (preview), then without --dry-run."
  exit 0
else
  echo "RESULT: ${fail} CRITICAL CHECK(S) FAILED — do NOT tag. Fix the failures above first."
  exit 1
fi
