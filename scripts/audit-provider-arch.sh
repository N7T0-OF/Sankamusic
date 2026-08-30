#!/bin/bash
# audit-provider-arch.sh — the Universal Music Platform architecture gate.
#
# The user's rule: "NE PAS coder Spotify, Apple Music et Deezer comme trois
# systèmes totalement indépendants" — the failure mode is three broken auth
# systems. This gate enforces the ORDER (architecture first, providers after)
# and the RULES (capability-gated, no secrets in the APK).
#
# Mode A — abstraction NOT built yet (current state, design only):
#   FAIL if provider-specific INTEGRATION code appears in the codebase
#   (AppleMusicProvider, DeezerProvider, ... or api clients), because it
#   would be a hardcoded system outside the abstraction. Settings labels and
#   feature-flag declarations are allowed (they are UI/declaration only).
#
# Mode B — abstraction present (spacekai/provider/MusicProvider.kt exists):
#   FAIL if a provider implementation is not capability-gated, or if a secret
#   (private key, client secret, long api key) is hardcoded in a provider dir
#   — never ship an exploitable key in the APK.
#
# Design: docs/PROVIDER-ARCHITECTURE.md
# Usage:  ./scripts/audit-provider-arch.sh
# Exit:   0 = architecture respected, 1 = violation.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"
SVC="core/service"
fail=0
pass() { echo "  PASS: $1"; }
crit() { echo "  FAIL: $1"; fail=1; }

echo "============================================"
echo "PROVIDER ARCHITECTURE GATE (design-first, capability-based)"
echo "============================================"
echo "Rule: never add provider code before the MusicProvider abstraction;"
echo "never ship secrets in the APK; never show a button for an unsupported"
echo "capability. Design: docs/PROVIDER-ARCHITECTURE.md"
echo ""

ARCH_FILE="$SRC/spacekai/provider/MusicProvider.kt"

# Allowed mentions: feature flags + settings labels only.
DECL='spacekai/SpaceKaiFeatures.kt|spacekai/SpaceKai.kt|spacekai/ui/SpaceKaiSettingsSection.kt'
# Provider-specific integration markers (classes, api clients, auth flows).
PATTERN='AppleMusicProvider|DeezerProvider|TidalProvider|QobuzProvider|SoundCloudProvider|LocalMusicProvider|AppleMusicApi|DeezerApi|MusicKit|MusicKitPlayerController'

if [ ! -f "$ARCH_FILE" ]; then
  echo "Mode A — MusicProvider abstraction NOT built yet (design only):"
  echo "  no provider-specific integration code may exist."
  HITS=$(grep -rnE "$PATTERN" "$SRC" "$SVC" 2>/dev/null | grep -Ev "$DECL" || true)
  if [ -n "$HITS" ]; then
    crit "Provider-specific code found WITHOUT the abstraction — the '3 broken auth systems' failure mode:"
    printf '%s\n' "$HITS" | head -8
  else
    pass "No provider-specific integration code (abstraction missing is the only state allowed)"
  fi
else
  echo "Mode B — abstraction present: providers must implement it, be"
  echo "  capability-gated, and never hardcode secrets."
  # Each provider must implement MusicProvider.
  PROV=$(grep -rlE ': MusicProvider' "$SRC/spacekai/provider" 2>/dev/null || true)
  if [ -z "$PROV" ]; then
    crit "MusicProvider interface exists but NO implementation : MusicProvider"
  else
    pass "Provider implementation(s): $(printf '%s\n' "$PROV" | sed 's|.*/||' | tr '\n' ' ')"
  fi
  # No hardcoded secrets in provider dirs.
  SEC=$(grep -rnE --include='*.kt' '-----BEGIN|client_secret[[:space:]]*=|clientSecret[[:space:]]*=|api[_-]?key[[:space:]]*=[[:space:]]*"[A-Za-z0-9]{16,}"|PRIVATE[[:space:]]*KEY' "$SRC/spacekai/provider" 2>/dev/null || true)
  if [ -n "$SEC" ]; then
    crit "Hardcoded secret/private-key pattern in provider dirs (never ship an exploitable key):"
    printf '%s\n' "$SEC" | head -6
  else
    pass "No hardcoded secret pattern in provider dirs"
  fi
  # Capability gating: every capability check must guard the implementation.
  # (light check: provider files must reference their capabilities set)
  CAP=$(grep -rlE 'capabilities' "$SRC/spacekai/provider" 2>/dev/null | wc -l | tr -d ' ')
  if [ "$CAP" -eq 0 ]; then
    crit "No provider file declares capabilities — buttons would show for unsupported features"
  else
    pass "$CAP provider file(s) declare capabilities"
  fi
fi

# --- 3. Layer integrity (Clean Architecture) --------------------------------
# core/ must never import a composeApp-owned package. The scope decision
# (docs/WIRING-P0.md, 2026-08-26): spacekai/ lives in composeApp, so a core
# module reading a flag directly would create a forbidden core -> composeApp
# dependency — flags surface through composeApp as parameters instead. This
# check guards that rule in both modes (it is independent of the provider
# abstraction). core/data legitimately imports domain/data; only the
# composeApp-owned packages (spacekai, ui) are violations.
echo ""
echo "Layer integrity (core must not depend on composeApp):"
LAYR=$(grep -rnE '^import com\.maxrave\.simpmusic\.(spacekai|ui)\.' core 2>/dev/null || true)
if [ -n "$LAYR" ]; then
  crit "core imports a composeApp-owned package (spacekai/ui) — dependency direction violated:"
  printf '%s\n' "$LAYR" | head -6
else
  pass "No core -> composeApp import (flags surface through composeApp as parameters)"
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
  echo "RESULT: provider architecture respected. (Abstraction still to build — P1.)"
  exit 0
else
  echo "RESULT: PROVIDER ARCHITECTURE VIOLATION — release blocked (see docs/PROVIDER-ARCHITECTURE.md)"
  exit 1
fi
