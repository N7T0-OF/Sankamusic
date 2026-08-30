#!/usr/bin/env bash
#
# release.sh — ONE command to publish the next SpaceKai version.
#
# Run this from the LIVE repository (N7T0-OF/Sankamusic), pointing at the
# workspace that holds the release pipeline:
#
#   ./scripts/release.sh /path/to/Sankamusic-dev --dry-run   # preview (no changes)
#   ./scripts/release.sh /path/to/Sankamusic-dev             # publish for real
#
# It chains the whole flow:
#   1. apply-release-pipeline.sh   — copy pipeline files, bump version
#      (live version-code + 1), disable the live release.yml, never touch
#      feature sources (composeApp/src, core/, desktopApp/src, androidApp/src).
#   2. Remove the ABI `splits {}` block from androidApp/build.gradle.kts
#      (backed up as .bak) so assembleRelease emits ONE universal APK.
#   3. Verify the locked icons.
#   4. Commit, tag v<version>, push current branch + tag (release-publish.sh).
#   5. Print the manual step: publish the draft release on GitHub.
#
# Nothing is published automatically beyond the push — a stable tag creates a
# DRAFT release that you publish by hand (deliberate, spec: human validation).

set -euo pipefail

SOURCE="${1:-}"
[ -n "$SOURCE" ] || {
  echo "Usage: $0 <workspace-dir> [--dry-run]" >&2
  exit 64
}
DRY=false
for arg in "$@"; do [ "$arg" = "--dry-run" ] && DRY=true; done

# Run from the LIVE repo: scripts/release.sh (and the others) must already be
# present here (apply-release-pipeline.sh copies them). Do NOT cd — the whole
# flow operates on the current working directory.
echo "=============================================="
echo " SPACEKAI RELEASE — $(pwd)"
echo "=============================================="
$DRY && echo "== DRY RUN — nothing will be committed, tagged or pushed =="

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
  echo "::error::Not a git checkout. Copy the scripts into the LIVE Sankamusic repo and run from there." >&2
  exit 1
}

# --- 1. Apply the pipeline ------------------------------------------------
echo ""
echo "[1/5] Applying release pipeline from $SOURCE ..."
if $DRY; then
  bash scripts/apply-release-pipeline.sh "$SOURCE" --dry-run
else
  bash scripts/apply-release-pipeline.sh "$SOURCE" --yes
fi

# --- 2. Remove ABI splits (so the build emits ONE universal APK) ----------
echo ""
echo "[2/5] Removing ABI splits block from androidApp/build.gradle.kts ..."
APP_GRADLE="androidApp/build.gradle.kts"
if [ ! -f "$APP_GRADLE" ]; then
  echo "::error::$APP_GRADLE not found" >&2
  exit 1
fi
if grep -q '^[[:space:]]*splits[[:space:]]*{' "$APP_GRADLE"; then
  if $DRY; then
    echo "  [dry] would remove the splits {} block (backup: $APP_GRADLE.bak)"
  else
    cp "$APP_GRADLE" "$APP_GRADLE.bak"
    # Remove the whole `splits { ... }` block (brace-balanced) with awk.
    awk '
      BEGIN { depth = 0; inblock = 0 }
      {
        if (!inblock && $0 ~ /^[ \t]*splits[ \t]*\{/) { inblock = 1; depth = 1; next }
        if (inblock) {
          n = gsub(/\{/, "{", $0); depth += n
          n = gsub(/\}/, "}", $0); depth -= n
          if (depth <= 0) { inblock = 0 }
          next
        }
        print
      }
    ' "$APP_GRADLE" > "$APP_GRADLE.tmp" && mv "$APP_GRADLE.tmp" "$APP_GRADLE"
    echo "  removed splits {} block (backup: $APP_GRADLE.bak)"
  fi
else
  echo "  no splits block present — single-APK build already configured"
fi

# --- 3. Verify locked icons -----------------------------------------------
echo ""
echo "[3/5] Verifying locked icons ..."
bash scripts/verify-icons.sh

# --- 4. Commit + tag + push -----------------------------------------------
echo ""
echo "[4/5] Publishing (commit + tag + push) ..."
if $DRY; then
  bash scripts/release-publish.sh --dry-run
else
  bash scripts/release-publish.sh
fi

# --- 5. Manual step --------------------------------------------------------
echo ""
echo "[5/5] Done."
VERSION_NAME=$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
if $DRY; then
  echo "DRY RUN complete. Run ./scripts/release.sh $SOURCE (no --dry-run) when ready."
else
  echo "CI is building SpaceKai v$VERSION_NAME. When the run finishes:"
  echo "  1. Open https://github.com/N7T0-OF/Sankamusic/releases"
  echo "  2. Open the DRAFT 'SpaceKai v$VERSION_NAME'"
  echo "  3. Check the assets (1 APK + SHA256SUMS.txt + desktop) and click Publish."
fi