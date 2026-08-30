#!/usr/bin/env bash
#
# apply-release-pipeline.sh — bring the single-APK release pipeline into the
# LIVE repository (N7T0-OF/Sankamusic), without touching feature sources.
#
# The live repo still ships the old 6-APK pipeline (SimpMusic-foss/full-*).
# This workspace has the new one (1 universal APK + SHA256SUMS.txt + SpaceKai
# asset names + post-publication verification). Run THIS script from the LIVE
# repo, pointing at this workspace as the source:
#
#   ./scripts/apply-release-pipeline.sh /path/to/Sankamusic-dev [--yes] [--dry-run]
#
# Safety model — four categories:
#   A. NEW files        (never exist in the live repo → copy, no overwrite risk)
#   B. REPLACE files    (backed up as *.bak before being replaced)
#   C. EDIT IN PLACE    (targeted edits only — version lines, vcs-url)
#   D. PROTECTED        (NEVER touched — warn loudly if present in the source)
#
# Category D is the important one: composeApp/src and core/ carry the live
# repo's own features (Spotify OAuth, custom navigation, update-checker fork).
# This script must never copy those, or it would wipe the live work.

set -euo pipefail

SOURCE="${1:-}"
[ -n "$SOURCE" ] || {
  echo "Usage: $0 <workspace-dir> [--yes] [--dry-run]" >&2
  exit 64
}
SOURCE="$(cd "$SOURCE" && pwd)"
[ -f "$SOURCE/gradle/libs.versions.toml" ] || {
  echo "::error::$SOURCE does not look like the workspace (no gradle/libs.versions.toml)" >&2
  exit 1
}
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
  echo "::error::Run this from the LIVE Sankamusic git checkout." >&2
  exit 1
}

YES=false
DRY=false
VERSION_NAME_OVERRIDE=""
for arg in "$@"; do
  case "$arg" in
    --yes) YES=true ;;
    --dry-run) DRY=true ;;
    --version-name=*) VERSION_NAME_OVERRIDE="${arg#--version-name=}" ;;
  esac
done

say() { echo "[apply] $1"; }
warn() { echo "[apply] ⚠ $1"; }
err()  { echo "[apply] ✗ $1"; }

$DRY && say "DRY RUN — no files will be copied or edited."
say "Source: $SOURCE"
say "Target: $(pwd)"

# Compute the new version FIRST (needed by the changelog copy below):
# version-code is never taken from the source workspace — the live repo's own
# history has moved it (v1.7.3 was released with code 59).
#
# ⚠️ ANDROID REFUSES DOWNGRADES — the versionCode in the manifest must be
# strictly greater than EVERY versionCode ever published, not just the live
# repo's current one. Real incident (2026-08-25): the v1.1.6 series shipped
# with code 66, then v1.7.x restarted at 58→61, so anyone on v1.1.6-3 (66)
# got "Application non installée" (INSTALL_FAILED_VERSION_DOWNGRADE) when
# installing v1.7.5 (61). The rule is therefore:
#   NEW_CODE = max(live current, highest code ever published) + 1
# MIN_VERSION_CODE below is the highest versionCode found in the APKs of the
# published releases (v1.8.1 = 72, v1.8.0 = 71). Bump it if an even higher
# code was ever shipped; keep it as a floor so the next release always beats
# the full history, not just the previous tag.
#
# version-name defaults to the source's, overridable with --version-name.
NEW_NAME="${VERSION_NAME_OVERRIDE:-$(grep '^version-name' "$SOURCE/gradle/libs.versions.toml" | head -1 | cut -d'"' -f2)}"
CUR_CODE="$(grep '^version-code' gradle/libs.versions.toml | head -1 | cut -d'"' -f2 || true)"
if ! [[ "$CUR_CODE" =~ ^[0-9]+$ ]]; then
  err "Cannot read current version-code from the live repo ('$CUR_CODE')."
  exit 1
fi
MIN_VERSION_CODE=72   # highest versionCode ever published (v1.8.1 = 72; v1.8.0 = 71)
if [ "$CUR_CODE" -lt "$MIN_VERSION_CODE" ]; then
  warn "live version-code $CUR_CODE is BELOW the highest ever published ($MIN_VERSION_CODE)."
  warn "Using $MIN_VERSION_CODE as the floor so the new APK is not a downgrade."
  CUR_CODE="$MIN_VERSION_CODE"
fi
NEW_CODE=$((CUR_CODE + 1))
say "Version: $NEW_NAME (code → $NEW_CODE; floor was $MIN_VERSION_CODE, live was $CUR_CODE)"
echo ""

# --- Category D: protected paths (checked first, loudest) -------------------
PROTECTED=(composeApp/src core desktopApp/src androidApp/src)
for p in "${PROTECTED[@]}"; do
  if [ -e "$SOURCE/$p" ]; then
    warn "PROTECTED '$p' in source — NEVER copied (live repo has its own features here)."
  fi
done
echo ""

# --- Category A: new files --------------------------------------------------
A_FILES=(
  ".github/workflows/verify-release.yml"
  ".github/workflows/publish-from-artifact.yml"
  ".github/workflows/release-nightly-check.yml"
  "scripts/pre-release-report.sh"
  "scripts/verify-icons.sh"
  "scripts/verify-release.sh"
  "scripts/release-publish.sh"
  "scripts/release.sh"
  "scripts/update-upstream.sh"
  "scripts/audit-features.sh"
  "scripts/audit-settings-ui.sh"
  "scripts/audit-upstream-hotspots.sh"
  "scripts/audit-spotify-flow.sh"
  "scripts/audit-landscape-player.sh"
  "scripts/audit-updater-flow.sh"
  "scripts/audit-navigation.sh"
  "scripts/audit-dynamic-color.sh"
  "scripts/generate-feature-audit.sh"
  "scripts/append-feature-audit-gaps.sh"
  "scripts/audit-provider-arch.sh"
  "scripts/check-gate-parity.sh"
  "scripts/check-pre-tag.sh"
  "scripts/test-gates.sh"
  "scripts/publish.sh"
  "scripts/publish-draft.sh"
  "docs/UPSTREAM.md"
  "docs/PROVIDER-ARCHITECTURE.md"
  "docs/SPACEKAI-ARCHITECTURE.md"
  "docs/WIRING-P0.md"
  "docs/SESSION-2026-08-26.md"
  "docs/RELEASE-MESSAGE-v2.0.0.md"
  "docs/ci-cd.md"
  "docs/FEATURE-AUDIT.md"
  "docs/release-notes-template.md"
  "RELEASE.md"
)
say "Category A — copying NEW pipeline files:"
for f in "${A_FILES[@]}"; do
  if [ ! -f "$SOURCE/$f" ]; then
    warn "missing in source, skipped: $f"
    continue
  fi
  if $DRY; then
    say "  [dry] copy $f"
  else
    mkdir -p "$(dirname "$f")"
    cp "$SOURCE/$f" "$f"
    say "  copied $f"
  fi
done
# Changelog: copy the source's changelog for ITS OWN version-code (the newest
# one — this workspace is the source of the NEW release) under the NEW
# version-code filename (the release workflow reads
# fastlane/.../changelogs/<VERSION_CODE>.txt). Fall back to the
# numerically-highest changelog if the source lacks one for its own code.
# NB: `ls | head -1` would pick the alphabetically-first file (the OLDEST
# SimpMusic changelog, 1.txt) — always select by numeric code.
SRC_CODE="$(grep '^version-code' "$SOURCE/gradle/libs.versions.toml" | head -1 | cut -d'"' -f2 || true)"
SRC_CHANGELOG=""
[ -n "$SRC_CODE" ] && [ -f "$SOURCE/fastlane/metadata/android/en-US/changelogs/${SRC_CODE}.txt" ] && \
  SRC_CHANGELOG="$SOURCE/fastlane/metadata/android/en-US/changelogs/${SRC_CODE}.txt"
if [ -z "$SRC_CHANGELOG" ]; then
  HIGHEST="$(ls "$SOURCE"/fastlane/metadata/android/en-US/changelogs/*.txt 2>/dev/null | sed 's|.*/||; s|\.txt$||' | sort -n | tail -1 || true)"
  [ -n "$HIGHEST" ] && SRC_CHANGELOG="$SOURCE/fastlane/metadata/android/en-US/changelogs/${HIGHEST}.txt"
fi
if [ -n "$SRC_CHANGELOG" ]; then
  DST_CHANGELOG="fastlane/metadata/android/en-US/changelogs/${NEW_CODE}.txt"
  if $DRY; then
    say "  [dry] changelog → $DST_CHANGELOG"
  else
    mkdir -p "$(dirname "$DST_CHANGELOG")"
    cp "$SRC_CHANGELOG" "$DST_CHANGELOG"
    say "  changelog → $DST_CHANGELOG (from $(basename "$SRC_CHANGELOG"))"
  fi
else
  warn "no changelog found in source — release notes will be auto-generated"
fi
echo ""

# --- Category B: replace files (with backup) --------------------------------
B_FILES=(
  ".github/workflows/android-release.yml"
  "build_and_sign_apk.sh"
)
say "Category B — replacing pipeline files (backup as .bak):"
for f in "${B_FILES[@]}"; do
  if [ ! -f "$SOURCE/$f" ]; then
    warn "missing in source, skipped: $f"
    continue
  fi
  if $DRY; then
    say "  [dry] backup + replace $f"
  else
    if [ -f "$f" ]; then cp "$f" "$f.bak"; fi
    mkdir -p "$(dirname "$f")"
    cp "$SOURCE/$f" "$f"
    say "  replaced $f (backup: $f.bak)"
  fi
done
# CRITICAL: the live repo ships its OWN release workflow as `release.yml`.
# Leaving it active would make TWO workflows fire on the same tag (double
# build, double release). Disable it by renaming to a non-.yml extension.
if [ -f .github/workflows/release.yml ]; then
  if $DRY; then
    say "  [dry] DISABLE live .github/workflows/release.yml → release.yml.disabled (would double-trigger)"
  else
    mv .github/workflows/release.yml .github/workflows/release.yml.disabled
    say "  DISABLED live .github/workflows/release.yml → release.yml.disabled (review before deleting)"
  fi
else
  say "  no live release.yml to disable"
fi
echo ""

# --- Category C: edit in place ----------------------------------------------
say "Category C — targeted edits:"
# C1: version-name / version-code (gradle/libs.versions.toml) — the dependency
# pins of the live libs.versions.toml are left completely untouched; only the
# two version lines are edited. NEW_NAME/NEW_CODE were computed at the top.
if $DRY; then
  say "  [dry] version-name → $NEW_NAME, version-code → $NEW_CODE"
else
  sed -i "s|^version-name = .*|version-name = \"$NEW_NAME\"|" gradle/libs.versions.toml
  sed -i "s|^version-code = .*|version-code = \"$NEW_CODE\"|" gradle/libs.versions.toml
  say "  version-name → $NEW_NAME, version-code → $NEW_CODE"
fi
# C2: conveyor.conf vcs-url (keep the live file, fix only the repo URL)
if $DRY; then
  say "  [dry] conveyor.conf vcs-url → https://github.com/N7T0-OF/Sankamusic"
else
  if [ -f conveyor.conf ]; then
    sed -i 's|vcs-url = "https://github.com/maxrave-dev/SimpMusic"|vcs-url = "https://github.com/N7T0-OF/Sankamusic"|' conveyor.conf
    say "  conveyor.conf vcs-url fixed"
  else
    warn "  conveyor.conf not found — skipping"
  fi
fi
echo ""

# --- Manual step: androidApp ABI splits --------------------------------------
say "Category C — MANUAL step required (cannot be automated safely):"
cat <<'EOF'
  The live repo still produces 6 APKs because androidApp/build.gradle.kts has
  a `splits { abi { ... } }` block. Remove it so assembleRelease emits ONE
  universal APK. Delete this whole block if present:

      splits {
          abi {
              isEnable = true
              reset()
              include("arm64-v8a", "armeabi-v7a", "x86_64")
              isUniversalApk = true
          }
      }

  Keep the `ndk { abiFilters }` block (that is what makes the single APK
  universal). The CI validate step fails loudly if more than one APK is
  produced, so you cannot miss it.
EOF
echo ""

# --- Category D reminder -----------------------------------------------------
say "Category D — protected sources left untouched:"
for p in "${PROTECTED[@]}"; do
  say "  NOT copied: $p (live features preserved)"
done

echo ""
# Final step — guarded self-install. This MUST be the last executable block and
# a SINGLE parsed compound command (whole if..fi parsed before running). Real bug
# fixed 2026-08-26: copying the RUNNING script mid-run (apply-release-pipeline.sh
# used to be in A_FILES) makes bash resume parsing from the MODIFIED file →
# "line N: syntax error near `('". The self is therefore excluded from A_FILES
# and installed here inside one parsed block; the cp+exit below never re-reads
# the file.
SELF="$SOURCE/scripts/apply-release-pipeline.sh"
DST_SELF="scripts/apply-release-pipeline.sh"
if $DRY; then
  say "DRY RUN complete — nothing was copied or edited."
elif cmp -s "$SELF" "$DST_SELF" 2>/dev/null; then
  say "Done. scripts/apply-release-pipeline.sh matches the source — no upgrade pending."
  say "Next: review with git diff, then ./scripts/release-publish.sh --dry-run"
else
  mkdir -p "$(dirname "$DST_SELF")"
  cp "$SELF" "$DST_SELF"
  say "Installed newer scripts/apply-release-pipeline.sh."
  say "Re-run ./scripts/apply-release-pipeline.sh $SOURCE ONCE so the new list (A_FILES 26) propagates the rest."
  exit 0
fi
