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
    echo "        SpaceKai releases — expecting a PASS then, a FAIL if it drifts."
else
    # Clean Architecture: core/data cannot import composeApp's SpaceKaiUpdateConfig
    # (core must not depend on the app layer), so the fork's established pattern is to
    # hard-code the fork URL in Ytmusic.kt (kotlinYtmusicScraper) where the HTTP call
    # lives, and UpdateRepositoryImpl simply calls through. Accept either location.
    YTMUSIC="$ROOT/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/Ytmusic.kt"
    if grep -q "SpaceKaiUpdateConfig" "$IMPL" 2>/dev/null; then
        echo "  PASS: $IMPL references SpaceKaiUpdateConfig"
    elif grep -q "api.github.com/repos/$EXPECTED_OWNER/$EXPECTED_REPO/releases/latest" "$YTMUSIC" 2>/dev/null; then
        echo "  PASS: $YTMUSIC points the update check at $EXPECTED_OWNER/$EXPECTED_REPO"
        echo "        (core cannot import the app's SpaceKaiUpdateConfig; URL lives here)"
    else
        echo "  FAIL: update checker is very likely pointing at maxrave-dev/SimpMusic again."
        echo "        Expected $EXPECTED_OWNER/$EXPECTED_REPO either in $IMPL"
        echo "        (SpaceKaiUpdateConfig) or in $YTMUSIC (hard-coded fork URL)."
        echo "        Re-apply the SPACEKAI FEATURE hook (see docs/SPACEKAI-ARCHITECTURE.md)."
        fail=1
    fi
fi

# --- Hotspot 3: upstream.lock (integrated base manifest, spec §28) --------------
# The app derives SPACEKAI_BASED_ON_UPSTREAM from this manifest at build time
# (BuildKonfig.upstreamBaseVersion), so the Updates screen's "Base intégrée"
# always matches the lock — never a hardcoded constant. Missing lock = the
# integrated base is untracked again.
echo ""
echo "## Hotspot 3: upstream.lock (integrated base manifest, spec §28)"
LOCK="$ROOT/upstream.lock"
if [ ! -f "$LOCK" ]; then
    echo "  FAIL: upstream.lock missing — the integrated SimpMusic base is untracked."
    echo "        Create it (repository, tag, commit, release, integrated_at,"
    echo "        core_commit, merge_state); scripts/update-upstream.sh writes it."
    fail=1
else
    LOCK_TAG=$(grep '^tag=' "$LOCK" | head -1 | cut -d= -f2- | tr -d ' ')
    LOCK_COMMIT=$(grep '^commit=' "$LOCK" | head -1 | cut -d= -f2- | tr -d ' ')
    LOCK_CORE=$(grep '^core_commit=' "$LOCK" | head -1 | cut -d= -f2- | tr -d ' ')
    if [ -z "$LOCK_TAG" ] || [ -z "$LOCK_COMMIT" ]; then
        echo "  FAIL: upstream.lock present but missing tag=/commit= — not a valid manifest."
        fail=1
    else
        echo "  PASS: upstream.lock — tag=$LOCK_TAG, commit=${LOCK_COMMIT:0:8}, core=${LOCK_CORE:0:8}"
        if grep -q "upstreamBaseVersion" "$ROOT/composeApp/build.gradle.kts" 2>/dev/null; then
            echo "  PASS: build injects the lock into BuildKonfig.upstreamBaseVersion"
        else
            echo "  WARN: build.gradle.kts does not inject upstream.lock into BuildKonfig —"
            echo "        the app base is a constant again; re-wire SPACEKAI_BASED_ON_UPSTREAM."
        fi
    fi
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
    echo "RESULT:  upstream hotspots OK (vcs-url correct; update checker points to SpaceKai;"
    echo "         upstream.lock present and injected into the build)"
else
    echo "RESULT:  UPSTREAM HOTSPOT DRIFTED — release blocked until re-wired to SpaceKai"
fi
exit "$fail"