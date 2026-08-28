#!/usr/bin/env bash
#
# test-upstream.sh — Tests du script check-upstream.sh (logique hors réseau)
#
# Exerce la génération du rapport upstream SANS appeler l'API live, via la
# surcharge `SIMPMUSIC_LATEST_TAG`. Utile pour couvrir la partie pure :
# pattern_matches + agrégation des plages (6 fonctionnalités).
#
# Scénarios :
#   1. tag v1.7.2 (dans toutes les plages 1.7.x)   → 6/6, exit 0
#   2. tag v2.0.0 (hors plages 1.7.x)              → 3/6, exit 1
#   3. tag v3.1.0 (rupture majeure)                → 3/6, exit 1  (cohérent)
#
# Usage : bash scripts/test-upstream.sh

set -uo pipefail

SCRIPT="scripts/check-upstream.sh"
COUNT=0; PASSED=0; FAILED=0

assert() { # assert <label> <actual> <expected>
  local label="$1" actual="$2" expected="$3"
  COUNT=$((COUNT+1))
  if [ "$actual" = "$expected" ]; then
    PASSED=$((PASSED+1)); echo "  ✔ $label"
  else
    FAILED=$((FAILED+1)); echo "  ✘ $label (attendu \"$expected\", obtenu \"$actual\")"
  fi
}

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP"

run_report() { # run_report <tag> -> prints "SUMMARY_RATIO" and returns script's exit code
  local tag="$1"
  local dir="$TMP/out-$tag"
  local out rc
  SIMPMUSIC_LATEST_TAG="$tag" bash "$SCRIPT" "$dir" >/dev/null 2>&1
  rc=$?
  out=$(grep -oE 'Résultat : [0-9]+/[0-9]+' "$dir/upstream-compat.txt" 2>/dev/null || echo "pas de rapport")
  echo "$out|$rc"
}

# 1. v1.7.2 : toutes les plages 1.7.x sont couvertes → 6/6, exit 0
r=$(run_report "v1.7.2")
ratio="${r%%|*}"; rc="${r##*|}"
assert "v1.7.2 → 6/6 ($ratio)" "$ratio" "Résultat : 6/6"
assert "v1.7.2 → exit 0" "$rc" "0"

# 2. v2.0.0 : navigation/orientation/player (1.7.x) hors plage → 3/6, exit 1
r=$(run_report "v2.0.0")
ratio="${r%%|*}"; rc="${r##*|}"
assert "v2.0.0 → 3/6 ($ratio)" "$ratio" "Résultat : 3/6"
assert "v2.0.0 → exit 1" "$rc" "1"

# 3. v3.1.0 : rupture majeure → toujours 3/6 (seuls themes/haptics/dynamic_color = *)
r=$(run_report "v3.1.0")
ratio="${r%%|*}"; rc="${r##*|}"
assert "v3.1.0 → 3/6 ($ratio)" "$ratio" "Résultat : 3/6"
assert "v3.1.0 → exit 1" "$rc" "1"

echo
if [ "$FAILED" -eq 0 ]; then
  echo "✅ $PASSED/$COUNT scénarios OK — check-upstream.sh conforme (logique hors réseau)."
  exit 0
else
  echo "❌ $FAILED scénario(s) en échec sur $COUNT — le rapport upstream a régressé."
  exit 1
fi