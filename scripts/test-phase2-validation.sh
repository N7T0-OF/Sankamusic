#!/usr/bin/env bash
#
# test-phase2-validation.sh — Tests du garde-fou Phase 2
#
# Exerce `check-phase2-validation.sh` sur ses 3 scénarios contractuels, SANS
# toucher au vrai manifest du repo (on passe un manifest temporaire via
# PHASE2_MANIFEST). En cas de régression du garde-fou, ce script sort ≠ 0.
#
# Scénarios :
#   1. manifest à 1.7.x, sans marqueur        → doit PASSER (exit 0)
#   2. manifest à 2.x, sans marqueur          → doit REFUSER (exit 1)
#   3. manifest à 2.x, marqueur =true (var)   → doit PASSER (exit 0)
#
# Usage : bash scripts/test-phase2-validation.sh

set -uo pipefail

GATE_SCRIPT="scripts/check-phase2-validation.sh"
COUNT=0; PASSED=0; FAILED=0

assert() { # assert <label> <actual_exit> <expected_exit>
  local label="$1" actual="$2" expected="$3"
  COUNT=$((COUNT+1))
  if [ "$actual" -eq "$expected" ]; then
    PASSED=$((PASSED+1)); echo "  ✔ $label (exit $actual)"
  else
    FAILED=$((FAILED+1)); echo "  ✘ $label (attendu $expected, obtenu $actual)"
  fi
}

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
LOCKFILE="$TMP/.phase2-gate"

# Scénario 1 : manifest 1.7.x, pas de marqueur → OK (exit 0)
cat > "$TMP/manifest-1.x.kt" <<'EOF'
package test
object TestManifest1x { const val upstreamCompatibility = "1.7.x" }
EOF
PHASE2_MANIFEST="$TMP/manifest-1.x.kt" PHASE2_GATE_FILE="$TMP/.gate1" \
  PHASE2_V2_VALIDATED="" bash "$GATE_SCRIPT" >/dev/null 2>&1
assert "1.7.x sans marqueur → passe" "$?" 0

# Scénario 2 : manifest 2.x, pas de marqueur → REFUS (exit 1)
cat > "$TMP/manifest-2x.kt" <<'EOF'
package test
object TestManifest2x { const val upstreamCompatibility = "2.0.x" }
EOF
PHASE2_MANIFEST="$TMP/manifest-2x.kt" PHASE2_GATE_FILE="$TMP/.gate2" \
  PHASE2_V2_VALIDATED="" bash "$GATE_SCRIPT" >/dev/null 2>&1
assert "2.x sans marqueur → refuse" "$?" 1

# Scénario 3 : manifest 2.x, variable =true → OK (exit 0)
PHASE2_MANIFEST="$TMP/manifest-2x.kt" PHASE2_GATE_FILE="$TMP/.gate3" \
  PHASE2_V2_VALIDATED="true" bash "$GATE_SCRIPT" >/dev/null 2>&1
assert "2.x avec marqueur → passe" "$?" 0

echo
if [ "$FAILED" -eq 0 ]; then
  echo "✅ $PASSED/$COUNT scénarios OK — garde-fou Phase 2 conforme."
  exit 0
else
  echo "❌ $FAILED scénario(s) en échec sur $COUNT — le garde-fou a régressé."
  exit 1
fi