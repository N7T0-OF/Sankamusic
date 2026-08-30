#!/bin/bash
# Audit the two known upstream "silent reversion" hotspots. On a SimpMusic
# upstream merge, ONE file is silently restored to upstream's value with NO
# conflict marker (surrounding code is identical), so the spacekai wiring
# drifts without any merge conflict to notice:
#
#   1. conveyor.conf -> app.vcs-url   (Conveyor derives every generated download
#      URL — download.html / .appinstaller / .exe wrapper — from it; pointing at
#      maxrave-dev/SimpMusic redirects all desktop links to the wrong repo).
#   2. core/data/.../update/UpdateRepositoryImpl.kt -> the GitHub release URL
#      the app checks for updates. Must be SpaceKai releases (SpaceKaiUpdateConfig),
#      never SimpMusic releases. (Lives in the core submodule — if the file is not
#      present in this checkout it is warn-only.)
#
# Usage:  ./scripts/audit-upstream-hotspots.sh
#         exit 0 = OK, 1 = a hotspot has drifted to upstream.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

EXPECTED_OWNER="N7T0-OF"
EXPECTED_REPO="Sankamusic"
EXPECTED_VCS="https://github.com/$EXPECTED_OWNER/$EXPECTED_REPO"

fail=0

echo "============================================"
echo "UPSTREAM HOTSPOT AUDIT (silent reversion)"
echo "============================================"

# --- Hotspot 1: conveyor.conf vcs-url ---------------------------------------
echo ""
echo "## Hotspot 1: conveyor.conf -> app.vcs-url"
CONF="conveyor.conf"
if [ ! -f "$CONF" ]; then
    echo "  WARN: $CONF not found — cannot audit Conveyor vcs-url"
else
    VCS=$(grep -E '^\s*vcs-url\s*=' "$CONF" | head -1 | sed -E 's/.*=\s*"([^"]+)".*/\1/')
    if [ -z "$VCS" ]; then
        echo "  FAIL: no vcs-url found in $CONF — Conveyor links will be wrong"
        fail=1
    elif [ "$VCS" = "$EXPECTED_VCS" ]; then
        echo "  PASS: vcs-url = $VCS"
    else
        echo "  FAIL: vcs-url = $VCS (expected $EXPECTED_VCS)"
        echo "        This silently reverted to upstream on a merge — Conveyor now"
        echo "        generates every download link against the WRONG repo. Fix it."
        fail=1
    fi
fi

# --- Hotspot 2: update checker repo URL -------------------------------------
echo ""
echo "## Hotspot 2: update checker (UpdateRepositoryImpl) -> SpaceKai releases"
# Find the file wherever the core submodule is extracted (only if present).
IMPL=$(find "$ROOT/core" "$ROOT/composeApp" -type f -name 'UpdateRepositoryImpl*.kt' 2>/dev/null | head -1)


if [ -z "$IMPL" ]; then
    echo "  WARN: UpdateRepositoryImpl not found in this checkout (core/data submodule not"
    echo "        extracted). When the full repo is present, this must reference"
    echo "        SpaceKaiUpdateConfig — expecting a PASS then, a FAIL if it drifts."
else
    if grep -q "SpaceKaiUpdateConfig" "$IMPL" 2>/dev/null; then
        echo "  PASS: $IMPL references SpaceKaiUpdateConfig"
    else
        echo "  FAIL: $IMPL does not reference SpaceKaiUpdateConfig — update checker"
        echo "        is very likely pointing at maxrave-dev/SimpMusic releases again."
        echo "        Re-apply the SPACEKAI FEATURE hook (see docs/SPACEKAI-ARCHITECTURE.md)."
        fail=1
    fi
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
    echo "RESULT:  upstream hotspots OK (vcs-url correct; update checker points to SpaceKai)"
else
    echo "RESULT:  UPSTREAM HOTSPOT DRIFTED — release blocked until re-wired to SpaceKai"
fi
exit "$fail"