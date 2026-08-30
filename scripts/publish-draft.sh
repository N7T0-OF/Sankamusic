#!/usr/bin/env bash
#
# publish-draft.sh — the FINAL step of a SpaceKai release: turn the CI-built
# DRAFT release into the PUBLIC release, after verifying everything the CI
# itself cannot verify from inside the build (asset integrity re-checked on a
# fresh download, honesty block present in the note).
#
# Where this sits in the chain (see RELEASE.md):
#   publish.sh            → pre-tag gates + bump + commit + tag + push
#   CI (android-release.yml) → builds APK + desktop packages, runs
#                              pre-release-report.sh, opens a DRAFT (stable
#                              tags) or publishes immediately (pre-release tags)
#   publish-draft.sh (THIS) → verify the draft, then publish it for real
#   CI (verify-release.yml) → post-publication asset verification
#
# Run this on the LIVE repository (N7T0-OF/Sankamusic), AFTER the release
# pipeline run has finished and opened the draft.
#
# Usage:
#   ./scripts/publish-draft.sh [--yes] [--dry-run]
#
# Checks before publishing (any FAIL aborts, nothing is published):
#   1. gh is installed and the release exists; refusals are precise:
#      - not a draft yet          → CI is still building (or the tag was wrong)
#      - already published        → nothing to do
#      - pre-release (published immediately by CI) → nothing to do
#   2. Assets re-downloaded fresh and verified by scripts/verify-release.sh
#      (exactly one release APK, checksums match, versionCode beats the
#      highest ever published, no forbidden variants).
#   3. The release note still carries the honesty block "Connu / non terminé"
#      (the ZERO-FALSE-POSITIVE gate: never publish a note that hides what
#      is not implemented).
#   4. Confirmation (unless --yes), then `gh release publish`.
#
# Exit: 0 = published (or dry-run preview), 1 = blocked, 2 = nothing to do.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

YES=0
DRY=0
for arg in "$@"; do
  case "$arg" in
    --yes) YES=1 ;;
    --dry-run) DRY=1 ;;
    *) echo "::error::unknown argument: $arg" >&2; exit 64 ;;
  esac
done

echo "============================================"
echo "SPACEKAI PUBLISH DRAFT — verify, then publish"
echo "============================================"
echo ""

# --- 1. gh + tag resolution -------------------------------------------------
if ! command -v gh >/dev/null 2>&1; then
  echo "::error::gh is not installed — publish-draft.sh needs the GitHub CLI." >&2
  exit 1
fi

VERSION_NAME="$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)"
if [ -z "$VERSION_NAME" ]; then
  echo "::error::Could not read version-name from gradle/libs.versions.toml" >&2
  exit 1
fi
TAG="v${VERSION_NAME}"
echo "Release tag:  $TAG"
echo ""

if ! gh release view "$TAG" --json isDraft,isPrerelease,body,name >/dev/null 2>&1; then
  echo "::error::Release '$TAG' does not exist (yet). The CI run may still be"
  echo "::error::building it, or the tag was never pushed. Wait and retry." >&2
  exit 1
fi

IS_DRAFT="$(gh release view "$TAG" --json isDraft -q .isDraft)"
IS_PRERELEASE="$(gh release view "$TAG" --json isPrerelease -q .isPrerelease)"
if [ "$IS_DRAFT" != "true" ]; then
  echo "Release '$TAG' is not a draft (isPrerelease=$IS_PRERELEASE)."
  if [ "$IS_PRERELEASE" = "true" ]; then
    echo "Pre-release tags are published immediately by CI — nothing to do."
  else
    echo "This release is already published — nothing to do."
  fi
  exit 2
fi
echo "Draft confirmed — CI finished building it."
echo ""

# --- 2. Fresh asset download + verification ---------------------------------
echo "## Assets (fresh download + verify-release.sh)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
gh release download "$TAG" --dir "$WORK" --clobber
echo "Downloaded to $WORK:"
ls -1 "$WORK" | sed 's/^/  /'
echo ""
if ! bash scripts/verify-release.sh "$WORK" "$TAG"; then
  echo ""
  echo "::error::ASSET VERIFICATION FAILED — do NOT publish this draft."
  echo "::error::Fix the release pipeline / assets and re-run." >&2
  exit 1
fi

# --- 3. Honesty block in the note -------------------------------------------
echo ""
echo "## Release note (honesty gate)"
BODY="$(gh release view "$TAG" --json body -q .body)"
if ! printf '%s' "$BODY" | grep -q 'Connu / non terminé'; then
  echo ""
  echo "::error::RELEASE NOTE HAS NO HONESTY BLOCK ('Connu / non terminé')."
  echo "::error::Never publish a release that hides what is not implemented"
  echo "::error::(ZERO FALSE POSITIVE). Fix the note (edit the draft), re-run." >&2
  exit 1
fi
# The block must be CURRENT, not just present: regenerate the audit locally
# and extract the same fenced block the CI appends (append-feature-audit-gaps.sh
# does exactly this at build time). A stale note — e.g. an audit that changed
# between the CI run and now, or a hand-edit that dropped a gap — is caught
# here instead of shipping a release whose honesty block is out of date.
bash scripts/generate-feature-audit.sh >/dev/null 2>&1
CURRENT="$(sed -n '/^```markdown$/,/^```$/p' docs/FEATURE-AUDIT-REPORT.md 2>/dev/null | sed '1d;$d')"
# Compare on the gap lines only: the CI block also carries Installation / SHA-256
# sections whose exact text may legitimately differ from the local report.
CURRENT_GAPS="$(printf '%s\n' "$CURRENT" | grep -E '^- \*\*' || true)"
BODY_GAPS="$(printf '%s\n' "$BODY" | grep -E '^- \*\*' || true)"
if [ -z "$CURRENT_GAPS" ]; then
  echo "  PASS: note carries the 'Connu / non terminé' block (local audit empty)."
elif [ "$CURRENT_GAPS" = "$BODY_GAPS" ]; then
  echo "  PASS: honesty block is current (gap lines match the fresh audit)."
else
  echo ""
  echo "::error::RELEASE NOTE HONESTY BLOCK IS STALE — the gap lines differ from"
  echo "::error::the freshly regenerated audit. Re-run append-feature-audit-gaps.sh"
  echo "::error::on the draft note (or edit the draft), then re-run." >&2
  exit 1
fi

# --- 4. Publish --------------------------------------------------------------
echo ""
echo "## Publish"
if [ "$DRY" -eq 1 ]; then
  echo "[dry-run] Would run: gh release publish $TAG"
  echo "[dry-run] Nothing was published."
  exit 0
fi
if [ "$YES" -eq 0 ]; then
  read -r -p "All checks green. Publish draft '$TAG' as a public release? [y/N] " answer
  case "$answer" in
    y|Y|yes|YES) ;;
    *) echo "Aborted — the draft is left untouched."; exit 0 ;;
  esac
fi

gh release publish "$TAG"

echo ""
echo "============================================"
echo "PUBLISHED: $TAG is now public."
echo "verify-release.yml will re-check the assets post-publication."
echo "============================================"