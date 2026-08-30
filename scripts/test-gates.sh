#!/bin/bash
# test-gates.sh — regression test for the gate suite ("verify the verifier").
#
# Three layers of protection:
#   1. OUTCOME TABLE — every gate must produce its EXPECTED exit code in the
#      current code state. A gate whose logic broke (e.g. a wrong file path,
#      a grep pattern that matches nothing, a fallback that masks a failure)
#      flips its outcome and the test reports it.
#   2. PATTERN REALITY — the critical (file, pattern) pairs from the
#      2026-08-26 audit must still match the real code. A gate PASSes by
#      definition if its pattern hits; if the code drifts (upstream merge
#      removes a SpaceKai hook) the pattern stops hitting — caught here even
#      before the gate's own outcome flips.
#    outcome. A gate whose logic broke (e.g. a wrong file path, a grep
#    pattern that matches nothing, a fallback that masks a failure) flips its
#    outcome and the test reports it.
#   2. PATTERN REALITY — the critical (file, pattern) pairs from the
#      2026-08-26 audit must still match the real code. A gate PASSes by
#      definition if its pattern hits; if the code drifts (upstream merge
#      removes a SpaceKai hook) the pattern stops hitting — caught here even
#      before the gate's own outcome flips.
#   3. WIRING-PROCEDURE SIMULATIONS — the three scenarios exercised by hand on
#      2026-08-26 are now regression tests. Each backs up, mutates, asserts,
#      and restores (EXIT trap inside a subshell, so a failure can never leave
#      the tree dirty):
#        A. spotifySync removal → never a FAIL; the `spotify` row stays
#           PARTIALLY by evidence (generator patterns are the real sp_dc
#           chain, not the flag name — WIRING-P0.md §spotifySync).
#        B. downloadWifiOnly fallback B → flag + generator row both gone (no
#           evidence chain exists; leaving the row would flip it to a phantom
#           NOT IMPLEMENTED — WIRING-P0.md §Repli B).
#        C. wiring the 4 ready toggles → none of them flagged; the generator
#           rewards the stub (player-info → IMPLEMENTED static): the gate
#           cannot tell a stub from a real wiring, which is why the visual
#           test of each guide is the real guard.
#
# STATE-AGNOSTIC: the expected audit-features outcome is DERIVED from the
# actual FAIL lines (1 iff any decorative flag exists — 6 today, 0 after
# WIRING-P0 is executed on the live repo), and the simulations assert
# INVARIANTS, not fixed counts. The same test file is therefore valid before
# AND after the wiring — no edit needed at wiring time.
#
# Usage:  ./scripts/test-gates.sh
# Exit:   0 = all outcomes as expected AND all critical patterns still hit
#          AND all three wiring simulations hold.
#         1 = a gate outcome changed, a critical pattern no longer hits, or a
#          simulation assertion failed.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

pass() { echo "  PASS: $1"; }
fail() { echo "  FAIL: $1"; bad=1; }
bad=0

# ---------------------------------------------------------------------------
# 1. Outcome table.
# ---------------------------------------------------------------------------
echo "============================================"
echo "GATE REGRESSION TEST (outcomes + patterns)"
echo "============================================"
echo ""
echo "## 1. Gate outcomes (expected for this snapshot)"
echo ""

declare -A EXPECT=(
  [audit-upstream-hotspots]=0
  [audit-settings-ui]=0
  [audit-spotify-flow]=0
  [audit-landscape-player]=0
  [audit-updater-flow]=0
  [audit-navigation]=0
  [audit-dynamic-color]=0
  [audit-provider-arch]=0
  [verify-icons]=0
  [verify-perf-keys]=0
  [check-gate-parity]=0
  [audit-features]=-1         # derived: 1 iff decorative flags exist (see below)
)

for g in "${!EXPECT[@]}"; do
  if [ "$g" = "audit-features" ]; then
    # audit-features is STATE-DEPENDENT: it must exit 1 when any decorative
    # toggle exists (the release blocker), 0 when all are wired/removed. The
    # invariant that must always hold: exit code agrees with the FAIL lines it
    # prints. A gate that exits 0 while printing FAIL (phantom pass) or exits 1
    # with no FAIL (phantom blocker) is a logic break caught here.
    out=$(bash "scripts/$g.sh" 2>&1); rc=$?
    nfails=$(printf '%s\n' "$out" | grep -cE '^  FAIL: [a-z]' || true)
    if [ "$rc" -eq 0 ] && [ "$nfails" -eq 0 ]; then
      pass "audit-features → exit 0, no decorative flags (wired state)"
    elif [ "$rc" -eq 1 ] && [ "$nfails" -gt 0 ]; then
      pass "audit-features → exit 1, $nfails decorative flag(s) (blocker holds)"
    else
      fail "audit-features → exit $rc with $nfails FAIL lines — inconsistent (phantom pass/blocker)"
    fi
    continue
  fi
  bash "scripts/$g.sh" >/dev/null 2>&1
  rc=$?
  if [ "$rc" -eq "${EXPECT[$g]}" ]; then
    pass "$g → exit ${EXPECT[$g]} (as expected)"
  else
    fail "$g → exit $rc, expected ${EXPECT[$g]} — gate logic changed or code drifted"
  fi
done

# ---------------------------------------------------------------------------
# 2. Pattern reality — critical pairs audited 2026-08-26.
#    Format: "path|pattern" (path relative to repo root, pattern literal).
# ---------------------------------------------------------------------------
echo ""
echo "## 2. Critical patterns still hit the real code"
echo ""

CM="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"
AND="composeApp/src/androidMain/kotlin/com/maxrave/simpmusic"

CRITICAL=(
  "$CM/App.kt|applyPersistedSpaceKaiFeatures("
  "$CM/App.kt|isSpaceKaiFeatureEnabled(SpaceKaiFeatures::customNavigation)"
  "$CM/App.kt|LiquidGlassAppBottomNavigationBar("
  "$CM/App.kt|showLabels = !hideNavLabel"
  "$CM/App.kt|isPhoneLandscape = !isTablet && currentOrientation() == Orientation.LANDSCAPE"
  "$CM/App.kt|isShowNowPlaylistScreen && !isTabletLandscape"
  "$CM/App.kt|shouldShowUpdateDialog"
  "$CM/ui/screen/player/NowPlayingScreen.kt|aspectRatio(1f, matchHeightConstraintsFirst = true)"
  "$CM/ui/screen/player/NowPlayingScreen.kt|SPACEKAI FIX: in landscape the width is the LONG side"
  "$CM/ui/screen/login/SpotifyLoginScreen.kt|saveSpotifySpdc("
  "$CM/ui/screen/login/SpotifyLoginScreen.kt|statusUrl"
  "$CM/viewModel/LogInViewModel.kt|setSpdc"
  "$CM/ui/navigation/graph/LoginScreenGraph.kt|SpotifyLoginDestination"
  "$CM/ui/screen/home/SettingScreen.kt|spotifyLoggedIn"
  "$CM/viewModel/SharedViewModel.kt|fun checkForUpdate()"
  "$CM/viewModel/SharedViewModel.kt|checkForGithubReleaseUpdate()"
  "$CM/viewModel/SharedViewModel.kt|CheckForUpdateAt"
  "$AND/expect/ui/PlatformColorScheme.android.kt|dynamicDarkColorScheme("
  "$AND/expect/ui/PlatformColorScheme.android.kt|dynamicLightColorScheme("
  "$CM/ui/theme/Theme.kt|platformDynamicColorScheme"
  "conveyor.conf|vcs-url = \"https://github.com/N7T0-OF/Sankamusic\""
)

for entry in "${CRITICAL[@]}"; do
  file="${entry%%|*}"
  pat="${entry#*|}"
  if [ ! -f "$file" ]; then
    fail "$file missing — a gate target file disappeared"
  elif grep -qF "$pat" "$file"; then
    pass "$file → '$pat'"
  else
    fail "$file LOST the pattern '$pat' — upstream merge or refactor removed a SpaceKai hook"
  fi
done

# ---------------------------------------------------------------------------
# 3. Wiring-procedure simulations (backup → mutate → assert → restore).
#    Each runs in a subshell with an EXIT trap, so a failed assertion can
#    never leave the tree dirty.
# ---------------------------------------------------------------------------
echo ""
echo "## 3. Wiring-procedure simulations"
echo ""

SKF="$CM/spacekai/SpaceKaiFeatures.kt"
GFA="scripts/generate-feature-audit.sh"

# Each simulation runs in a SUBSHELL so its EXIT trap fires at subshell exit
# (a function-level trap in the main shell would be REPLACED by the next one
# and never run — the bug this layer's first version hit). The trap string
# EMBEDS the backup paths at build time, so evaluation never reads a local
# variable that is already gone. `bad` is set in the parent from the exit code.

sim_spotify_removal() {
  # State-agnostic invariant: with spotifySync REMOVED, the flag must never
  # appear in audit-features' FAIL list (whether 5 flags remain pre-wiring or
  # 0 remain post-wiring), and the `spotify` row must stay PARTIALLY by
  # evidence — the generator must not depend on the flag name.
  local bak; bak=$(mktemp)
  cp "$SKF" "$bak"
  trap "cp '$bak' '$SKF'; rm -f '$bak'" EXIT
  sed -i '/spotifySync/d' "$SKF"
  local fails
  fails=$(bash scripts/audit-features.sh 2>&1 | grep -cE 'FAIL: spotifySync' || true)
  [ "$fails" -eq 0 ] || { fail "A: spotifySync still flagged after removal"; return 1; }
  local spot
  bash scripts/generate-feature-audit.sh >/dev/null 2>&1   # rows go to the report FILE, not stdout
  spot=$(grep 'Login (cookie' docs/FEATURE-AUDIT-REPORT.md | head -1)
  printf '%s' "$spot" | grep -q 'PARTIALLY IMPLEMENTED' || { fail "A: spotify row must stay PARTIALLY by evidence"; return 1; }
  printf '%s' "$spot" | grep -q 'saveSpotifySpdc' || { fail "A: spotify row must cite the real sp_dc chain"; return 1; }
  pass "A: spotifySync removed → never a FAIL; spotify row stays PARTIALLY by evidence"
}

sim_wifi_fallback() {
  # State-independent invariant: with downloadWifiOnly REMOVED, the flag must
  # not appear in audit-features FAIL, and the generator row must be gone too
  # (no evidence chain exists — leaving the row would create a phantom
  # NOT IMPLEMENTED). Holds pre-wiring (5 flags remain) and post-wiring (0).
  local bak1 bak2; bak1=$(mktemp); bak2=$(mktemp)
  cp "$SKF" "$bak1"; cp "$GFA" "$bak2"
  trap "cp '$bak1' '$SKF'; cp '$bak2' '$GFA'; rm -f '$bak1' '$bak2'" EXIT
  sed -i '/downloadWifiOnly/d' "$SKF"
  sed -i '/^wifi-only|/d' "$GFA"
  local wf
  wf=$(bash scripts/audit-features.sh 2>&1 | grep -cE 'FAIL: downloadWifiOnly' || true)
  [ "$wf" -eq 0 ] || { fail "B: downloadWifiOnly still present"; return 1; }
  bash scripts/generate-feature-audit.sh >/dev/null 2>&1
  if grep -q 'wifi-only' docs/FEATURE-AUDIT-REPORT.md; then
    fail "B: wifi-only row must be removed from the generator too (no evidence chain)"
    return 1
  fi
  pass "B: downloadWifiOnly removed → flag + generator row gone (no phantom)"
}

sim_four_wirings() {
  # State-independent: with the 4 ready toggles WIRED (stub file), NONE of
  # them may appear in the audit-features FAIL list — pre-wiring that drops
  # the count by 4, post-wiring they are already real (0 FAIL). The stub
  # reward check (player-info → IMPLEMENTED) also holds in both states: the
  # gate cannot tell a stub from a real wiring, which is why the visual test
  # is the real guard.
  local tmp
  tmp="$CM/spacekai/SimWiringTmp.kt"
  cat > "$tmp" <<'EOF'
// TEMPORARY SIMULATION — deleted by test-gates.sh after the assertions.
package com.maxrave.simpmusic.spacekai
object SimWiringTmp {
    fun playerInfo(): Boolean = use(SpaceKaiFeatures::customPlayerInfo)
    fun minimalTabs(): Boolean = use(SpaceKaiFeatures::minimalisticNavigation)
    fun landscapeLayout(): Boolean = use(SpaceKaiFeatures::landscapePlayer)
    fun dynamicPalette(): Boolean = use(SpaceKaiFeatures::dynamicColor)
    private fun use(flag: Boolean): Boolean = flag
}
EOF
  trap "rm -f '$tmp'" EXIT
  local f
  f=$(bash scripts/audit-features.sh 2>&1 | grep -cE 'FAIL: (customPlayerInfo|minimalisticNavigation|landscapePlayer|dynamicColor)' || true)
  [ "$f" -eq 0 ] || { fail "C: one of the 4 ready toggles still flagged after wiring: $f"; return 1; }
  local pi
  bash scripts/generate-feature-audit.sh >/dev/null 2>&1   # rows go to the report FILE
  pi=$(grep 'player-info' docs/FEATURE-AUDIT-REPORT.md | head -1)
  printf '%s' "$pi" | grep -q 'IMPLEMENTED' || { fail "C: the stub must flip player-info to IMPLEMENTED (gate cannot tell a stub)"; return 1; }
  pass "C: 4 ready wirings wired → none flagged; stub rewarded (visual test is the real guard)"
}

( sim_spotify_removal ) || bad=1
( sim_wifi_fallback ) || bad=1
( sim_four_wirings ) || bad=1

echo ""
echo "============================================"
if [ "$bad" -eq 0 ]; then
  echo "RESULT: ALL GATE OUTCOMES AS EXPECTED, ALL CRITICAL PATTERNS ALIVE, ALL WIRING SIMULATIONS HOLD"
  exit 0
else
  echo "RESULT: GATE REGRESSION DETECTED — investigate before releasing"
  exit 1
fi