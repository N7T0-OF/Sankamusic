#!/bin/bash
# Synchronize SpaceKai with the upstream SimpMusic repository.
#
# SpaceKai is an add-on layer over SimpMusic. This script pulls the latest
# upstream changes into the current branch WITHOUT overwriting SpaceKai
# customizations: conflicts are reported for the maintainer to resolve by
# hand. Nothing is pushed or published automatically.
#
# Usage:  ./scripts/update-upstream.sh
# Env:    UPSTREAM_URL (default: https://github.com/maxrave-dev/SimpMusic.git)
#         UPSTREAM_BRANCH (default: main)
#         SYNC_BRANCH (default: upstream-sync)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/maxrave-dev/SimpMusic.git}"
UPSTREAM_BRANCH="${UPSTREAM_BRANCH:-main}"
SYNC_BRANCH="${SYNC_BRANCH:-upstream-sync}"
LOCK="$ROOT/upstream.lock"

echo "============================================"
echo "SpaceKai ← SimpMusic upstream synchronization"
echo "============================================"
echo "Upstream:  $UPSTREAM_URL ($UPSTREAM_BRANCH)"
echo "Sync branch: $SYNC_BRANCH"
echo ""

# --- 1. Sanity checks -------------------------------------------------------
if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "::error::Not a git repository. Run this from the SpaceKai checkout."
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "::error::Working tree is not clean. Commit or stash your changes first."
  git status --short
  exit 1
fi

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "[1/8] Current branch: $CURRENT_BRANCH (clean)"

# --- 2. Upstream remote -----------------------------------------------------
if ! git remote get-url upstream >/dev/null 2>&1; then
  echo "[2/8] Adding upstream remote: $UPSTREAM_URL"
  git remote add upstream "$UPSTREAM_URL"
else
  echo "[2/8] Upstream remote already configured: $(git remote get-url upstream)"
fi

# --- 3. Fetch upstream ------------------------------------------------------
echo "[3/8] Fetching upstream..."
git fetch upstream "$UPSTREAM_BRANCH"

# --- 4. Report available version --------------------------------------------
echo "[4/8] Comparing versions"
echo ""
echo "------------------------------------------------------------"
echo "Local version:   v$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'\"' -f2) (code $(grep '^version-code' gradle/libs.versions.toml | head -1 | cut -d'\"' -f2))"
UPSTREAM_VERSION=$(git show "upstream/$UPSTREAM_BRANCH:gradle/libs.versions.toml" 2>/dev/null | grep '^version-name' | head -1 | cut -d'"' -f2 || echo "?")
echo "Upstream version: v${UPSTREAM_VERSION:-?}"
if [ -f "$LOCK" ]; then
  LOCKED_TAG=$(grep '^tag=' "$LOCK" | head -1 | cut -d= -f2- | tr -d ' ')
  echo "Locked base (upstream.lock): ${LOCKED_TAG:-?}"
else
  echo "Locked base (upstream.lock): none — will be created after the merge"
fi
echo "------------------------------------------------------------"
echo ""

# --- 5. Create a sync branch and merge --------------------------------------
if git rev-parse --verify "$SYNC_BRANCH" >/dev/null 2>&1; then
  echo "[5/8] Reusing existing sync branch: $SYNC_BRANCH"
  git checkout "$SYNC_BRANCH"
else
  echo "[5/8] Creating sync branch: $SYNC_BRANCH"
  git checkout -b "$SYNC_BRANCH"
fi

echo "Merging upstream/$UPSTREAM_BRANCH into $SYNC_BRANCH..."
echo "NOTE: conflicts are left for manual resolution — nothing is auto-chosen."
if git merge "upstream/$UPSTREAM_BRANCH" --no-edit; then
  echo "Merge completed with no conflicts."
else
  echo ""
  echo "::warning::Merge finished with conflicts. Resolve them manually, then:"
  echo "  git add <resolved files> && git commit"
fi

# --- 6. Report --------------------------------------------------------------
echo "[6/8] Sync report"
echo ""
echo "============================================"
echo "SYNC REPORT"
echo "============================================"
echo "SpaceKai branch:    $CURRENT_BRANCH"
echo "Sync branch:        $SYNC_BRANCH"
echo "Upstream:           $UPSTREAM_URL ($UPSTREAM_BRANCH)"
echo "Upstream version:   v${UPSTREAM_VERSION:-?}"
echo ""
if [ -n "$(git diff --name-only --diff-filter=U)" ]; then
  echo "CONFLICTS (resolve by hand, then commit):"
  git diff --name-only --diff-filter=U | sed 's/^/  /'
else
  echo "Conflicts: none"
fi
echo ""

# --- 7. SpaceKai touchpoints hit by upstream --------------------------------
# Files upstream changed that carry a SPACEKAI marker are silent-break
# hotspots: the merge may look clean while dropping the SpaceKai behaviour
# (the update-checker URL is the classic one — see docs/UPSTREAM.md). List
# them so the maintainer re-applies the marked hooks by hand. This mirrors
# the conflict rule in docs/SPACEKAI-ARCHITECTURE.md: upstream-owned files
# take upstream, then the SPACEKAI FEATURE hooks go back on top.
echo "[7/8] Scanning upstream-changed files for SpaceKai markers..."
CHANGED=$(git diff --name-only "HEAD...upstream/$UPSTREAM_BRANCH" || true)
if [ -z "$CHANGED" ]; then
  echo "  (no upstream-changed files to scan — nothing to re-apply)"
else
  echo "  Upstream changed $(echo "$CHANGED" | wc -l | tr -d ' ') file(s); checking for SPACEKAI markers..."
  echo ""
  echo "SPACEKAI TOUCHPOINTS TO RE-APPLY (files upstream changed that contain SpaceKai hooks):"
  echo "------------------------------------------------------------"
  found=0
  while IFS= read -r file; do
    if [ -f "$file" ] && grep -nE '// SPACEKAI (FEATURE|CUSTOMIZATION|FIX)|SPACEKAI FEATURE|SPACEKAI CUSTOMIZATION' "$file" >/dev/null 2>&1; then
      echo "  $file"
      grep -nE '// SPACEKAI (FEATURE|CUSTOMIZATION|FIX)' "$file" | sed 's/^/      L/' | head -5
      found=1
    fi
  done <<< "$CHANGED"
  if [ "$found" -eq 0 ]; then
    echo "  (none — no SpaceKai hook sits in a file upstream touched)"
  fi
  echo "------------------------------------------------------------"
  echo "  Also grep upstream-owned areas by hand for known silent-break spots:"
  echo "    composeApp/.../App.kt  (SPACEKAI hooks: persistence, swipe, bar styles)"
  echo "    core/data/.../update/UpdateRepositoryImpl.kt  (SimpMusic release URL)"
fi

# --- 7b. Critical hook survival (even without a conflict marker) ------------
# A clean merge can still LOSE a SpaceKai hook when upstream restructures the
# region around it (no conflict marker, but the behaviour is gone). The marker
# scan above only lists files upstream changed that STILL carry a marker; this
# pass checks that the critical hooks — the ones whose loss is invisible —
# still exist in the merged tree. File absent = module not checked out here,
# verify on the live repo (skip, not fail).
echo ""
echo "[7b] Critical SpaceKai hook survival (post-merge)..."
CRITICAL_HOOKS=(
  "composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/App.kt|applyPersistedSpaceKaiFeatures("
  "composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/App.kt|isSpaceKaiFeatureEnabled(SpaceKaiFeatures::customNavigation)"
  "composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/App.kt|LiquidGlassAppBottomNavigationBar("
  "conveyor.conf|vcs-url = \"https://github.com/N7T0-OF/Sankamusic\""
  "core/data/src/main/kotlin/com/maxrave/simpmusic/data/repository/UpdateRepositoryImpl.kt|SpaceKaiUpdateConfig"
)
hooks_missing=0
for entry in "${CRITICAL_HOOKS[@]}"; do
  file="${entry%%|*}"
  sig="${entry#*|}"
  if [ ! -f "$file" ]; then
    echo "  ?   $file — not present in this checkout (module absent); verify on the live repo"
  elif grep -qF "$sig" "$file"; then
    echo "  OK  $file contains: $sig"
  else
    echo "  !!  $file LOST: $sig — re-apply the SpaceKai hook (silent-break!)"
    hooks_missing=1
  fi
done
if [ "$hooks_missing" -ne 0 ]; then
  echo "  -> CRITICAL: at least one SpaceKai hook was dropped by the merge — re-apply before testing."
fi

# --- 8. Write upstream.lock -------------------------------------------------
# The lock is the single source of truth for "base intégrée": the app derives
# SPACEKAI_BASED_ON_UPSTREAM from it at build time (BuildKonfig.upstreamBaseVersion),
# so the Updates screen's "Base intégrée" always matches this manifest. Record the
# upstream ref the merged tree is based on, the pinned core submodule commit, and
# the merge state. The maintainer reviews and commits it with the sync.
echo ""
echo "[8/8] Writing upstream.lock..."
NEW_COMMIT=$(git rev-parse "upstream/$UPSTREAM_BRANCH" 2>/dev/null || echo "")
NEW_TAG=$(git tag --points-at "upstream/$UPSTREAM_BRANCH" 2>/dev/null | head -1)
[ -z "$NEW_TAG" ] && [ -n "$UPSTREAM_VERSION" ] && NEW_TAG="v$UPSTREAM_VERSION"
CORE_COMMIT=$(git -C "$ROOT/core" rev-parse HEAD 2>/dev/null || echo "?")
HAS_CONFLICTS=$(git diff --name-only --diff-filter=U 2>/dev/null | head -1)
STATE="clean"
[ -n "$HAS_CONFLICTS" ] && STATE="conflicts"
{
  echo "# SpaceKai upstream lock — single source of truth for the integrated SimpMusic base."
  echo "# Auto-maintained by scripts/update-upstream.sh; review and commit after a sync."
  echo "repository=$UPSTREAM_URL"
  echo "tag=${NEW_TAG:-?}"
  echo "commit=${NEW_COMMIT:-?}"
  echo "release=${NEW_TAG:-?}"
  echo "integrated_at=$(date +%Y-%m-%d)"
  echo "core_commit=${CORE_COMMIT:-?}"
  echo "merge_state=$STATE"
} > "$LOCK"
echo "  Wrote $LOCK (tag=${NEW_TAG:-?}, state=$STATE) — review and commit it."

echo ""
echo "Next steps (do NOT auto-publish):"
echo "  1. Resolve any conflicts above (never blindly pick ours/theirs)."
echo "  2. Re-apply the SpaceKai touchpoints listed above — and any hook the"
echo "     7b check reported as LOST (a clean merge can still drop a hook)."
echo "  3. Run ./scripts/pre-release-report.sh — it gates EVERYTHING:"
echo "     decorative toggles, UI overlap, upstream hotspots, Spotify flow,"
echo "     landscape player, updater chain, navigation, dynamic color, icons,"
echo "     perf keys, and the FEATURE AUDIT (see docs/FEATURE-AUDIT.md)."
echo "  4. Test (build + install) before considering a release."
echo "  5. Merge the sync branch back into your release branch when ready."
echo "============================================"
