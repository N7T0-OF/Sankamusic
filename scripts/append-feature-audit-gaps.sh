#!/bin/bash
# append-feature-audit-gaps.sh — append the honesty block to release notes.
#
# ZERO-FALSE-POSITIVE requirement: a release note MUST list every SpaceKai
# feature that is not finished ("Ne jamais masquer une fonctionnalité
# manquante"). This helper:
#   1. regenerates docs/FEATURE-AUDIT-REPORT.md (evidence-based verdicts),
#   2. extracts its ready-to-paste "### Connu / non terminé" block,
#   3. appends it (plus the Installation / SHA-256 section) to the given
#      release-notes file.
#
# Used by BOTH:
#   - scripts/generate-fastlane-changelog.sh (local regeneration),
#   - android-release.yml (CI auto-generated notes), so every auto-generated
#     release note ships the gaps — never a silent "all done".
#
# Usage:  ./scripts/append-feature-audit-gaps.sh <notes-file>
# Exit:   0 always (appending is best-effort; the release gate enforces).

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

NOTES="${1:-}"
if [ -z "$NOTES" ]; then
  echo "::error::usage: append-feature-audit-gaps.sh <notes-file>" >&2
  exit 64
fi

REPORT="docs/FEATURE-AUDIT-REPORT.md"
VERSION=$(grep '^version-name' gradle/libs.versions.toml 2>/dev/null | head -1 | cut -d'"' -f2 || echo "?")

# 1. Regenerate the report so the gaps are current (runs all evidence audits).
bash scripts/generate-feature-audit.sh >/dev/null 2>&1

# 2. Idempotency guard: a hand-written changelog produced by
#    generate-fastlane-changelog.sh already carries the honesty block (this
#    script is invoked by BOTH local regeneration and CI, so appending again
#    duplicates the block and trips publish-draft.sh's freshness gate, which
#    counts gap lines versus the fresh audit).
if grep -q '^### Connu / non terminé' "$NOTES"; then
  echo "Notes already carry the 'Connu / non terminé' block — skipping duplicate append."
  exit 0
fi

# 3. Extract the fenced "### Connu / non terminé" block (single fenced block).
BLOCK=$(sed -n '/^```markdown$/,/^```$/p' "$REPORT" 2>/dev/null | sed '1d;$d')
if [ -z "$BLOCK" ]; then
  echo "WARN: no 'Connu / non terminé' block in $REPORT — gaps not appended" >&2
fi

{
  echo ""
  echo "---"
  echo ""
  if [ -n "$BLOCK" ]; then
    echo "$BLOCK"
  else
    echo "### Connu / non terminé"
    echo ""
    echo "Aucune donnée de gaps disponible (rapport FEATURE AUDIT manquant)."
  fi
  echo ""
  echo "### Installation"
  echo ""
  echo "Android : \`SpaceKai-v${VERSION}.apk\` — APK universel signé (arm64-v8a, armeabi-v7a, x86_64)"
  echo "Windows : installer \`.msix\` · macOS : \`.dmg\` · Linux : \`.AppImage\`"
  echo ""
  echo "### SHA-256"
  echo ""
  echo "\`SHA256SUMS.txt\` joint à la release — vérification d'intégrité avant installation."
  echo ""
} >> "$NOTES"

echo "Appended FEATURE AUDIT gaps + Installation/SHA-256 to $NOTES"
