#!/bin/bash
# publish.sh — ONE command to verify everything and publish the next SpaceKai
# version. The "ready-to-click" entry point for the LIVE repo.
#
# Sequence (stops at the first failure, nothing is pushed before confirmation):
#   1. check-pre-tag.sh   — git/gh + all gates + changelog-vs-audit + header + version
#   2. test-gates.sh      — gate regression: expected outcomes + critical patterns alive
#   3. release.sh --dry-run — preview the bump (no changes)
#   4. CONFIRM (unless --yes) — then release.sh for real: bump → commit → tag → push
#
# The APK is deliberately NOT checked here: CI builds and signs it AFTER the
# tag; the APK gates run in pre-release-report.sh inside android-release.yml /
# publish-from-artifact.yml (the release is a DRAFT until that passes).
#
# Usage (run from the LIVE Sankamusic checkout):
#   ./scripts/publish.sh /path/to/Sankamusic-dev          # with confirmation
#   ./scripts/publish.sh /path/to/Sankamusic-dev --yes    # no prompt
# Exit: 0 = published (or dry-run preview shown), 1 = blocked.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SOURCE="${1:-}"
CONFIRM=1
for arg in "$@"; do [ "$arg" = "--yes" ] && CONFIRM=0; done

if [ -z "$SOURCE" ]; then
  echo "::error::usage: $0 <workspace-dir> [--yes]" >&2
  exit 64
fi

echo "============================================"
echo "SPACEKAI PUBLISH — verify, preview, publish"
echo "============================================"
echo ""

# --- 1. Pre-tag gate --------------------------------------------------------
echo "## 1/4  Pre-tag check (git + gh + gates + changelog)"
if ! bash scripts/check-pre-tag.sh; then
  echo ""
  echo "::error::PRE-TAG CHECK FAILED — fix the failures above, do NOT publish."
  exit 1
fi

# --- 2. Gate regression -----------------------------------------------------
echo ""
echo "## 2/4  Gate regression (outcomes + critical patterns)"
if ! bash scripts/test-gates.sh; then
  echo ""
  echo "::error::GATE REGRESSION DETECTED — a gate outcome changed or a critical"
  echo "::error::SpaceKai pattern no longer hits the code. Do NOT publish."
  exit 1
fi

# --- 3. Dry-run preview -----------------------------------------------------
echo ""
echo "## 3/4  Release preview (dry-run — no changes)"
if ! bash scripts/release.sh "$SOURCE" --dry-run; then
  echo ""
  echo "::error::DRY-RUN FAILED — release.sh cannot proceed. Fix and retry."
  exit 1
fi

# --- 4. Real publish (with confirmation) ------------------------------------
echo ""
echo "## 4/4  Publish for real"
if [ "$CONFIRM" -eq 1 ]; then
  read -r -p "Everything above is green. Tag and push v* now? [y/N] " answer
  case "$answer" in
    y|Y|yes|YES) ;;
    *) echo "Aborted — nothing was tagged or pushed."; exit 0 ;;
  esac
fi

bash scripts/release.sh "$SOURCE" || {
  echo "::error::release.sh FAILED after confirmation — see its output."
  exit 1
}

echo ""
echo "============================================"
echo "PUBLISHED. CI now builds the APK + desktop packages and opens a DRAFT"
echo "release — pre-release-report.sh runs there."
echo ""
echo "Next step (once the draft exists):"
echo "  ./scripts/publish-draft.sh [--yes]   # verify assets + honesty block, publish"
echo "============================================"
