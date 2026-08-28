#!/usr/bin/env bash
#
# check-upstream.sh — Vérifie la dernière release de la base upstream
# (SimpMusic) contre les plages de compatibilité déclarées par les
# fonctionnalités SpaceKai (docs/FEATURE_MANIFEST.md § 3).
#
# Utilisé par .github/workflows/upstream-check.yml (hebdomadaire + manuel) :
#   - toutes les fonctionnalités compatibles → rapport OK (exit 0)
#   - au moins une hors plage → rapport d'incompatibilité (exit 1),
#     le workflow ouvre alors une issue GitHub automatiquement.
#
# ⚠️ La liste `id|plage` ci-dessous est le MIROIR bash de
# `builtInSpaceKaiFeatures` (core/.../api/FeatureManifest.kt) : toute
# modification du manifest Kotlin doit être reportée ici (le test
# « built-in manifest contracts » couvre la partie Kotlin).
#
# Usage : bash scripts/check-upstream.sh [répertoire-de-sortie]

set -euo pipefail

REPO="maxrave-dev/SimpMusic"
OUT_DIR="${1:-build/upstream}"
OUT_FILE="$OUT_DIR/upstream-compat.txt"

# ── Fonctionnalités : miroir de builtInSpaceKaiFeatures (id|plage) ──────────
FEATURES="navigation|1.7.x
themes|*
orientation|1.7.x
player|1.7.x
haptics|*
dynamic_color|*"

# ── Logique de pattern (miroir de `upstreamMatches` en Kotlin) ───────────────
# pattern : "*" | "1.x" | "1.7.x" | "1.7.0"
# version : "1.7.2", "v1.7.2" (les pré-releases sont traitées comme stables ici ;
#            l'Adapter Kotlin, lui, les rejette — le rapport le note).
pattern_matches() {
  local pattern="$1" version="$2"
  [ "$pattern" = "*" ] && return 0
  local v="${version#v}"
  # pré-release : ne garder que la partie numérique pour la comparaison
  v="${v%%-*}"
  IFS='.' read -r maj min _ <<<"$v"
  if [[ "$pattern" == *.x ]]; then
    local core="${pattern%.x}"
    if [[ "$core" == *.* ]]; then
      IFS='.' read -r pmaj pmin <<<"$core"
      [ "$maj" = "$pmaj" ] && [ "$min" = "$pmin" ]
    else
      [ "$maj" = "$core" ]
    fi
  else
    [ "$v" = "$pattern" ]
  fi
}

# ── Récupération de la dernière release upstream ─────────────────────────────
echo "→ Récupération de la dernière release de $REPO ..."
# Surcharge testable : SIMPMUSIC_LATEST_TAG (ex. "v1.7.2") permet de vérifier
# la logique hors réseau (scripts/test-upstream.sh). Sinon, appel live à l'API.
if [ -n "${SIMPMUSIC_LATEST_TAG:-}" ]; then
  TAG="$SIMPMUSIC_LATEST_TAG"
else
  LATEST_JSON="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest")"
  TAG="$(printf '%s' "$LATEST_JSON" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  if [ -z "$TAG" ]; then
    echo "::error::Impossible de lire le tag de la dernière release de $REPO"
    exit 2
  fi
fi
VERSION="${TAG#v}"

mkdir -p "$OUT_DIR"
{
  echo "SpaceKai — rapport de compatibilité upstream"
  echo "============================================"
  echo "Généré le : $(date -u '+%Y-%m-%d %H:%M UTC')"
  echo "Upstream   : $REPO"
  echo "Version    : $TAG"
  echo ""
  printf '%-20s %-10s %-12s %s\n' "Fonctionnalité" "Plage" "Statut" "Détail"
  echo "--------------------------------------------------------------"

  compatible=0
  total=0
  while IFS='|' read -r id pattern; do
    [ -z "$id" ] && continue
    total=$((total + 1))
    if pattern_matches "$pattern" "$VERSION"; then
      compatible=$((compatible + 1))
      printf '%-20s %-10s %-12s %s\n' "$id" "$pattern" "OK" "dans la plage"
    else
      printf '%-20s %-10s %-12s %s\n' "$id" "$pattern" "HORS PLAGE" "version $TAG non couverte"
    fi
  done <<<"$FEATURES"

  echo "--------------------------------------------------------------"
  echo "Résultat : $compatible/$total fonctionnalités compatibles"
} >"$OUT_FILE"

cat "$OUT_FILE"

if [ "$compatible" -eq "$total" ]; then
  echo "✅ Upstream $TAG entièrement compatible (rappel : le contrat de chaque fonctionnalité est vérifié à la compilation par l'Adapter)."
  exit 0
else
  echo "::error::Upstream $TAG : $((total - compatible)) fonctionnalité(s) hors plage — adapter à mettre à jour avant toute release."
  exit 1
fi
