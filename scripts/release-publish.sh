#!/usr/bin/env bash
#
# release-publish.sh — commit, tag and push a SpaceKai release.
#
# Run this on the REAL repository (N7T0-OF/Sankamusic), NOT in a plain copy:
# it needs a git checkout with an `origin` remote. It never creates the
# GitHub release itself — CI does that when the tag is pushed, and the
# release lands as a DRAFT for a stable tag (publish it by hand on the
# releases page, per the release process).
#
# Usage:
#   ./scripts/release-publish.sh              # commit, tag v<version>, push
#   ./scripts/release-publish.sh --dry-run    # show exactly what would happen
#
# Steps:
#   1. Verify we are inside a git checkout with an origin remote.
#   2. Verify the tag to be created matches version-name in
#      gradle/libs.versions.toml (same rule as the CI validate-tag job).
#   3. Verify the brand icons are untouched (scripts/verify-icons.sh).
#   4. Commit the working tree (all SpaceKai changes for this release).
#   5. Create tag v<version-name> and push it + the current branch to origin
#      (the Sankamusic default branch is 'dev'; pushing 'main' would fail).
#   6. Print the next manual step: publish the draft release on GitHub.

set -euo pipefail

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  echo "== DRY RUN — nothing will be committed, tagged or pushed =="
  echo ""
fi

# 1. Git checkout + origin remote
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "::error::Not a git checkout. Run this from the real Sankamusic repository." >&2
  exit 1
fi
if ! git remote get-url origin >/dev/null 2>&1; then
  echo "::error::No 'origin' remote configured." >&2
  exit 1
fi
ORIGIN_URL="$(git remote get-url origin)"
CURRENT_BRANCH="$(git branch --show-current)"
if [ -z "$CURRENT_BRANCH" ]; then
  echo "::error::Could not determine the current branch (detached HEAD?)." >&2
  exit 1
fi
echo "Repo:      $ORIGIN_URL"
echo "Branch:    $CURRENT_BRANCH"

# 2. Tag must match version-name (mirror of the CI validate-tag rule)
VERSION_NAME="$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)"
VERSION_CODE="$(grep '^version-code' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)"
TAG="v${VERSION_NAME}"
echo "Version:   $VERSION_NAME  (code $VERSION_CODE)"
echo "Tag:       $TAG"

if [ -z "$VERSION_NAME" ]; then
  echo "::error::Could not read version-name from gradle/libs.versions.toml" >&2
  exit 1
fi
# ANDROID REFUSES DOWNGRADES: the versionCode must beat EVERY code ever
# published, not just the previous tag. Highest shipped = 72 (v1.8.1;
# v1.8.0 = 71). v1.7.5 shipped as 61 and was rejected by anyone on v1.1.6-3
# (66) with "Application non installée" (INSTALL_FAILED_VERSION_DOWNGRADE).
# See apply-release-pipeline.sh for the full incident write-up.
MIN_VERSION_CODE=72
if ! [[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || [ "$VERSION_CODE" -le "$MIN_VERSION_CODE" ]; then
  echo "::error::version-code $VERSION_CODE must be > $MIN_VERSION_CODE (the highest" >&2
  echo "::error::code ever published). Bump gradle/libs.versions.toml first — e.g." >&2
  echo "::error::code $((MIN_VERSION_CODE + 1)) for the next release. Android refuses" >&2
  echo "::error::downgrades with 'Application non installée'." >&2
  exit 1
fi
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "::error::Tag $TAG already exists locally. Delete it first if you intend to re-tag." >&2
  exit 1
fi

# 3. Brand icons must be untouched (locked assets)
if [ -x scripts/verify-icons.sh ]; then
  echo ""
  echo "-- Verifying locked icons --"
  scripts/verify-icons.sh
fi

# 4. Commit the working tree
echo ""
echo "-- Commit --"
if git diff --cached --quiet 2>/dev/null && git diff --quiet 2>/dev/null; then
  echo "Working tree clean — nothing to commit."
else
  if $DRY_RUN; then
    echo "[dry-run] Would commit:"
    git status --short | head -40
  else
    git add -A
    git commit -m "SpaceKai v$VERSION_NAME

Release pipeline: single universal APK + SHA256SUMS, desktop assets
renamed SpaceKai-v$VERSION_NAME-*, post-publication verification,
SpaceKai update config, locked icons, add-on layer features."
  fi
fi

# 5. Tag + push
echo ""
echo "-- Tag + push --"
if $DRY_RUN; then
  echo "[dry-run] Would run: git tag $TAG"
  echo "[dry-run] Would run: git push origin $CURRENT_BRANCH"
  echo "[dry-run] Would run: git push origin $TAG"
else
  git tag "$TAG"
  git push origin "$CURRENT_BRANCH"
  git push origin "$TAG"
fi

# 6. Next manual step
echo ""
if $DRY_RUN; then
  echo "DRY RUN complete — no changes were made."
  echo "Run ./scripts/release-publish.sh for real when ready."
else
  echo "== Pushed. CI is now building the release for $TAG =="
  echo ""
  echo "Next (manual, by design):"
  echo "  1. Watch the run:  Actions → 'SpaceKai Release Pipeline'"
  echo "  2. When done, open the DRAFT release at:"
  echo "     https://github.com/N7T0-OF/Sankamusic/releases"
  echo "  3. Check the assets (1 APK + SHA256SUMS.txt + desktop packages) and click Publish."
  echo ""
  echo "After publication, the 'Verify published release' workflow checks the assets."
fi