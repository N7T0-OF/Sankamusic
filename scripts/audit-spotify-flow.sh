#!/bin/bash
# audit-spotify-flow.sh — audit of the REAL Spotify login flow.
#
# DEFINITIVE FINDING (static audit 2026-08-26, composeApp + core/service):
#   SimpMusic / SpaceKai does NOT use Spotify OAuth PKCE. There is no
#   authorization URL, no redirect_uri, no code_verifier exchange anywhere in
#   the codebase. Login is the InnerTune-style WebView flow: the user signs in
#   at accounts.spotify.com inside a WebView, the app extracts the `sp_dc`
#   cookie and persists it (DataStore `setSpdc`). That cookie powers Spotify
#   lyrics + canvas.
#
#   Consequences for the reported P0 ("Spotify playlist sync broken,
#   redirect_uri"):
#   - "redirect_uri" is a PHANTOM: no OAuth exists, so no redirect_uri can be
#     mismatched. "Fixing the redirect_uri" cannot fix playlist sync.
#   - Spotify playlist IMPORT was never implemented: the SpaceKai `spotifySync`
#     toggle is decorative (0 real call sites), and no playlist-import code
#     exists in composeApp. A sp_dc cookie is not an official OAuth token.
#
# THIS GATE protects the flow that DOES exist:
#   FAIL if any link of the cookie chain breaks (upstream merge regression):
#     - the login screen no longer calls saveSpotifySpdc,
#     - the status-page regex (silent failure when the status URL carries a
#       query string) is gone,
#     - LogInViewModel no longer persists sp_dc (setSpdc),
#     - SpotifyLoginDestination is not registered in the nav graph,
#     - the Settings Spotify item (login/logout) is gone.
#   FAIL if a redirect_uri IS ever introduced inconsistently: every occurrence
#   of a redirect_uri value in the codebase must be byte-identical, so a
#   half-migrated OAuth cannot ship with a mismatched URI (the P0 rule).
#
# Usage:  ./scripts/audit-spotify-flow.sh
# Exit:   0 = cookie chain intact (and no inconsistent redirect_uri), 1 = broken.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"
fail=0
pass() { echo "  PASS: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

echo "============================================"
echo "SPOTIFY FLOW AUDIT (real flow: sp_dc cookie, NOT OAuth PKCE)"
echo "============================================"
echo "Finding: no authorization URL / redirect_uri / code_verifier exists in the"
echo "codebase. Login = WebView at accounts.spotify.com, cookie sp_dc extracted"
echo "and persisted via DataStore. 'redirect_uri' bug reports are a phantom —"
echo "playlist import was never implemented (decorative spotifySync toggle)."
echo ""

LOGIN="$SRC/ui/screen/login/SpotifyLoginScreen.kt"
VM="$SRC/viewModel/LogInViewModel.kt"
GRAPH="$SRC/ui/navigation/graph/LoginScreenGraph.kt"
SETTINGS="$SRC/ui/screen/home/SettingScreen.kt"

# --- 1. Cookie chain -------------------------------------------------------
if [ ! -f "$LOGIN" ]; then
  crit "Spotify login screen missing: $LOGIN"
else
  if grep -q 'saveSpotifySpdc(' "$LOGIN"; then
    pass "Login screen extracts sp_dc (saveSpotifySpdc)"
  else
    crit "Login screen no longer calls saveSpotifySpdc — cookie never saved"
  fi
  if grep -q 'statusUrl' "$LOGIN"; then
    pass "Login screen detects the accounts.spotify.com status page (statusUrl regex)"
  else
    crit "Status-page detection gone — login 'succeeds' without saving sp_dc"
  fi
fi

if [ ! -f "$VM" ]; then
  crit "LogInViewModel missing: $VM"
else
  if grep -q 'setSpdc' "$VM"; then
    pass "LogInViewModel persists sp_dc (setSpdc)"
  else
    crit "LogInViewModel no longer persists sp_dc — session not stored"
  fi
fi

if [ ! -f "$GRAPH" ]; then
  crit "LoginScreenGraph missing: $GRAPH"
else
  if grep -q 'SpotifyLoginDestination' "$GRAPH"; then
    pass "SpotifyLoginDestination registered in the nav graph"
  else
    crit "SpotifyLoginDestination not registered — login unreachable"
  fi
fi

if [ ! -f "$SETTINGS" ]; then
  crit "SettingScreen missing: $SETTINGS"
else
  if grep -q 'spotifyLoggedIn' "$SETTINGS"; then
    pass "Settings Spotify item (login/logout) present"
  else
    crit "Settings Spotify item gone — user cannot reach login or logout"
  fi
fi

# --- 2. redirect_uri consistency (future-proof: OAuth must not half-ship) ---
echo ""
echo "redirect_uri consistency (any OAuth added later must use ONE uri):"
URIS=$(grep -rhoE 'redirect_uri[=:][^&"'"'"') ]*' "$SRC" core 2>/dev/null | sort -u || true)
COUNT=$(printf '%s\n' "$URIS" | grep -c 'redirect_uri' || true)
if [ -z "$URIS" ]; then
  pass "No redirect_uri in the codebase (consistent with: no OAuth)"
elif [ "$COUNT" -eq 1 ]; then
  pass "Single redirect_uri value across the codebase:"
  printf '        %s\n' "$URIS"
else
  crit "MULTIPLE distinct redirect_uri values ($COUNT) — OAuth mismatch risk:"
  printf '        %s\n' "$URIS"
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: Spotify sp_dc flow intact — no phantom redirect_uri shipped."
  echo "        (Playlist import still NOT implemented: spotifySync is decorative.)"
  exit 0
else
  echo "RESULT: SPOTIFY FLOW BROKEN — release blocked (see scripts/audit-spotify-flow.sh)"
  exit 1
fi
