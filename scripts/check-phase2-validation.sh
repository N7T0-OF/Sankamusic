#!/usr/bin/env bash
#
# check-phase2-validation.sh — GARDE-FOU CI de la Phase 2 (docs/PHASE2_BUILD.md § 8-ci)
#
# Règle (protocole de validation) :
#   Il est INTERDIT d'étendre la compatibilité d'un Adapter à `2.x` tant que
#   la Phase 2 (compile → contract tests → APK → smoke tests) n'est pas
#   PROUVÉE sur un poste Android équipé ET déclarée via le marqueur
#   `PHASE2_V2_VALIDATED=true`.
#
# Ce script fait échouer le build si :
#   * un Adapter SimpMusic déclare une plage de compatibilité `2.x` (ou une
#     version 2.x) dans le code, ET
#   * le marqueur PHASE2_V2_VALIDATED n'est pas présent (fichier .env CI,
#     workflow_dispatch input, ou variable d'environnement).
#
# Le CI (ci.yml) appelle ce script avant `assembleRelease` : ainsi une
# modification humaine `compatibility: "1.7.x"` → `"2.x"` SANS la validation
# Android faite est REFUSÉE (build rouge), pas « à vue de nez ».
#
# Usage : bash scripts/check-phase2-validation.sh
#   - Marchés d'entrée : les fichiers .kt (adapter + Clé des plages) et le
#     manifest intégré.
#   - Sortie : exit 0 si OK, exit 1 si une plage 2.x est détectée sans marqueur.

set -uo pipefail

# Le marqueur de validation (déclaré APRÈS passer toute la Phase 2 gate).
# Il est porté par un FICHIER committé (reproductible) et/ou une variable CI
# (PHASE2_V2_VALIDATED=true en secret — jamais publier la preuve en clair).
GATE_FILE="${PHASE2_GATE_FILE:-.phase2-v2-validated}"
GATE_VAR="${PHASE2_V2_VALIDATED:-}"
CREATE_GATE_FILE="${CREATE_PHASE2_GATE_FILE:-false}"

# Cible à inspecter (chemin relatif au repo).
ADAPTER_DIR="core/src/main/java/com/sankamusic/core/update"

echo "→ Garde-fou Phase 2 : vérification de l'extension 2.x"
echo "  Marqueur attendu : fichier '$GATE_FILE' ($([ -e "$GATE_FILE" ] && echo 'présent' || echo 'ABSENT')) ou variable PHASE2_V2_VALIDATED=$([ "$GATE_VAR" = "true" ] && echo 'true' || echo 'non') "

# Chercher toute extension des PLAGES DU MANIFEST intégré à `2.x` — c'est ici
# qu'on déclare une fonctionnalité compatible 2.x. On cible `FeatureManifest.kt`
# (builtInSpaceKaiFeatures) et NON l'Adapter (SimpMusicAdapterV2.compatibility
# = "2.0.x" est légitime : l'existence d'un Adapter ne prouve pas 2.x).
MANIFEST="core/src/main/java/com/sankamusic/core/api/FeatureManifest.kt"
declare -i found=0
HITS="$(grep -nE 'upstreamCompatibility *= *"(2\.x|2\.[0-9]+.*x)' "$MANIFEST" 2>/dev/null || true)"
if [ -n "$HITS" ]; then
  found=1
  echo "  Manifest déclarant une plage '2.x' :"
  echo "$HITS" | sed 's/^/    - /'
fi

# Une plage 2.x trouvée → exiger le marqueur (fichier OU variable = true).
if [ "$found" -eq 1 ]; then
  if [ -e "$GATE_FILE" ] || [ "$GATE_VAR" = "true" ]; then
    echo "✅ Marqueur présent : la Phase 2 est déclarée validée (compile + contract + APK + smoke)."
    exit 0
  fi
  echo "::error::Plage de compatibilité '2.x' détectée SANS marqueur '$GATE_FILE'." >&2
  echo "::error::Refusé : la Phase 2 (compile → contract tests → build APK → smoke tests)" >&2
  echo "::error::doit être validée sur un poste Android équipé AVANT d'étendre à 2.x." >&2
  echo "::error::Voir docs/PHASE2_BUILD.md § 6 (Validation Gate). Ne PAS forcer 2.x." >&2
  exit 1
fi

echo "✅ Aucune plage '2.x' détectée dans le manifest : les fonctionnalités restent en 1.7.x (ou antérieures) — OK."
exit 0

# Note : pour créer le marqueur UNIQUEMENT après le passage manuel de la gate
# (sur un poste équipé), générer avec la bonne preuve :
#   echo "validated=$(date -u +%F) adapter-v2-build-ok" > "$GATE_FILE"
# et committer ce fichier explicitement (pas généré à la volée).