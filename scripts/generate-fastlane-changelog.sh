#!/bin/bash
# Generate the fastlane changelog for the current version from git history.
#
# The Play Store listing reads fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt.
# Run this before tagging a release so the changelog exists and the release
# workflow uses it for the GitHub release notes.
#
# Usage:  ./scripts/generate-fastlane-changelog.sh
# Env:    LOCALE (default: en-US)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOCALE="${LOCALE:-en-US}"

VERSION_CODE=$(grep '^version-code' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
VERSION_NAME=$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
CHANGELOG="fastlane/metadata/android/${LOCALE}/changelogs/${VERSION_CODE}.txt"

if [ -z "$VERSION_CODE" ]; then
  echo "::error::Could not read version-code from gradle/libs.versions.toml" >&2
  exit 1
fi

# Find the previous version tag: the GREATEST tag strictly below the current
# version-name. `git tag --sort=-v:refname | sed -n '2p'` used to work while
# releases were consecutive at the top of the sort (v1.x -> v2.0.0), but on
# the 0.3.x line the second-highest tag overall is v1.9.0, not the previous
# 0.3.x release — the range would cover hundreds of commits from another era.
# (82.txt had to be written by hand for that reason.)
PREV_TAG=$(git tag --sort=-v:refname | while read -r t; do
  v="${t#v}"
  # keep the first tag sorted STRICTLY below the current version-name
  if [ "$v" != "$VERSION_NAME" ] && [ "$(printf '%s\n%s\n' "$VERSION_NAME" "$v" | sort -V | head -1)" = "$v" ]; then
    echo "$t"
    break
  fi
done)

if [ -n "$PREV_TAG" ]; then
  RANGE="$PREV_TAG..HEAD"
  echo "Generating changelog for v${VERSION_NAME} (code ${VERSION_CODE}) since ${PREV_TAG}..."
else
  RANGE="HEAD"
  echo "No previous tag found; generating changelog from full history..."
fi

# Collect commit subjects once: no merges, no dependency-bot noise.
COMMITS=$(git log "$RANGE" --no-merges --pretty=format:'%s' \
  | grep -viE '^(bump .*|update dependency|chore\(deps\)|build\(deps\)|dependabot|renovate|⬆|🔧|merge (pull request|branch))' || true)
# Group conventional-commit subjects by type; anything that does not match
# falls into a generic list. Mirrors the CI release-notes logic so the local
# and CI-generated changelogs stay identical.
{
  # SPACEKAI FEATURE: the changelog header carries the SpaceKai brand.
  echo "# SpaceKai v${VERSION_NAME}"
  echo ""
  echo "> Released $(git log -1 --pretty=format:'%cs')"
  echo ""
  echo "## What's changed"
  echo ""
  echo "### Added"
  echo "$COMMITS" | grep -E '^(feat|feature)\(' | sed -E 's/^(feat|feature)(\([^)]*\))?: ?//' | sed 's/^/- /' || true
  echo ""
  echo "### Fixed"
  echo "$COMMITS" | grep -E '^(fix|bugfix)\(' | sed -E 's/^(fix|bugfix)(\([^)]*\))?: ?//' | sed 's/^/- /' || true
  echo ""
  echo "### Other"
  echo "$COMMITS" | grep -vE '^(feat|feature|fix|bugfix)' | sed -E 's/^[a-z]+(\([^)]*\))?: ?//' | sed 's/^/- /' || true
} > "$CHANGELOG"

# Append the FEATURE AUDIT gaps + Installation/SHA-256 (ZERO-FALSE-POSITIVE:
# a release note must list every not-finished feature).
bash scripts/append-feature-audit-gaps.sh "$CHANGELOG"

echo "Wrote $CHANGELOG"
echo "---"
cat "$CHANGELOG"