#!/bin/bash
# check-gate-parity.sh — prove the three release-pipeline gate lists never drift.
#
# The same code gates must run in ALL THREE paths:
#   - scripts/pre-release-report.sh              (release-time, blocking)
#   - scripts/check-pre-tag.sh                   (pre-tag, blocking)
#   - .github/workflows/release-nightly-check.yml (nightly, warn-only)
#
# A gate added to one path and forgotten in another silently widens the gap
# between what the release report blocks and what CI blocks — the "phantom
# gate" pattern. This script extracts the ACTUAL invocations (not mentions in
# comments/messages) from each file and fails on any difference.
#
# Extraction: grep the invocation forms `bash scripts/X.sh`, `gate scripts/X.sh`
# and `./scripts/X.sh` for X in the audit-*/verify-* family. Messages that only
# *mention* a script ("see scripts/audit-X.sh") are not invocations and never
# match because they lack the bash/gate/./ prefix.
#
# Usage:  ./scripts/check-gate-parity.sh
# Exit:   0 = the three lists are identical; 1 = drift detected.
#
# Run by: pre-release-report.sh (critical gate) and release-nightly-check.yml
# (warn-only audit), so a drift is caught both at release time and nightly.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

extract() { # file -> sorted unique gate script basenames (invocations only)
  local f="$1"
  grep -oE '(bash |gate |\./)scripts/(audit|verify)-[a-z-]+\.sh' "$f" 2>/dev/null \
    | sed -E 's#.*/##' | sort -u
}

echo "============================================"
echo "SPACEKAI GATE PARITY CHECK (3 gate lists)"
echo "============================================"

A=$(extract scripts/pre-release-report.sh)
B=$(extract scripts/check-pre-tag.sh)
C=$(extract .github/workflows/release-nightly-check.yml)
U=$(printf '%s\n%s\n%s\n' "$A" "$B" "$C" | sort -u)

if [ -z "$U" ]; then
  echo "FAIL: no gate scripts extracted — check the invocation grep patterns"
  exit 1
fi

ok=1
check_pair() { # label reference actual
  local label="$1" want="$2" got="$3"
  if [ "$want" != "$got" ]; then
    echo "  FAIL: $label gate list differs from the reference set:"
    echo "        missing: $(comm -23 <(printf '%s\n' "$want") <(printf '%s\n' "$got") | tr '\n' ' ')"
    echo "        extra:   $(comm -13 <(printf '%s\n' "$want") <(printf '%s\n' "$got") | tr '\n' ' ')"
    ok=0
  else
    echo "  PASS: $label — identical gate list"
  fi
}

check_pair "pre-release-report.sh"       "$U" "$A"
check_pair "check-pre-tag.sh"            "$U" "$B"
check_pair "release-nightly-check.yml"   "$U" "$C"

N=$(printf '%s\n' "$U" | grep -c .)
echo ""
echo "Reference set ($N gates):"
printf '  %s\n' "$U"
echo ""
if [ "$ok" -eq 1 ]; then
  echo "RESULT: ALL 3 GATE LISTS IN SYNC — no phantom gate possible"
  exit 0
else
  echo "RESULT: GATE PARITY DRIFT — a gate runs in one path but not another."
  echo "        Add the missing invocation to every path before releasing."
  exit 1
fi
