#!/usr/bin/env bash
#
# check-toolchain.sh — Audit d'environnement pour la Phase 2 (PHASE2_BUILD.md étape 1)
#
# Vérifie l'outillage nécessaire au build Android ET son état Git, et affiche un
# rapport PASS/FAIL ligne par ligne, avec un résumé final. C'est la preuve
# exigée par le protocole de validation avant toute tentative d'intégration
# SimpMusic 2.0.0.
#
# Les versions alignées sont lues depuis le version catalog (source unique) ;
# la cible Phase 2 est rappelée en repère (docs/PHASE2_BUILD.md § 4).
#
# Sortie : exit 0 si TOUTES les vérifications PASS, exit 1 sinon (une case rouge
# = on ne passe PAS à l'étape 2 / on ne touche pas aux plages 2.x).
#
# Usage : bash scripts/check-toolchain.sh
#   (À exécuter sur le poste Android équipé, puis coller la sortie complète.)
#
# Surcharges (pour tests uniquement, jamais utilisées en production) :
#   CHECK_TOOLCHAIN_CATALOG  chemin du version catalog
#   CHECK_TOOLCHAIN_WRAPPER  chemin du gradle-wrapper.properties (défaut gradle/wrapper/...)
#   CHECK_TOOLCHAIN_GIT      force le résultat Git : "clean" ou "dirty" (sinon, réel via git)

set -uo pipefail

CATALOG="${CHECK_TOOLCHAIN_CATALOG:-gradle/libs.versions.toml}"
WRAPPER_FILE="${CHECK_TOOLCHAIN_WRAPPER:-gradle/wrapper/gradle-wrapper.properties}"
commit_ok=1

# ── Petites helpers ─────────────────────────────────────────────────────
val() { # val <key> -> valeur entre guillemets de `key = "..."`
  grep -E "^[[:space:]]*$1 *=" "$CATALOG" | cut -d'"' -f2 | head -1
}

report() { # report <label> <status PASS|FAIL> <detail>
  printf '%-22s %-5s %s\n' "$1" "$2" "$3"
  if [ "$2" != "PASS" ]; then commit_ok=0; fi
}

has() { # has <cmd>
  command -v "$1" >/dev/null 2>&1
}

# ── Versions alignées depuis le catalog (source unique) ─────────────────
AGP_WANT="$(val agp)"
KOTLIN_WANT="$(val kotlin)"
BOM_WANT="$(val composeBom)"
SDK_WANT="$(val androidSdk)"

echo "=== ETAPE 1 — AUDIT ENVIRONNEMENT / TOOLCHAIN ==="
echo "Versions preenregistrees (version catalog):"
echo "  AGP=${AGP_WANT:-?}  Kotlin=${KOTLIN_WANT:-?}  BOM=${BOM_WANT:-?}  androidSdk=${SDK_WANT:-?}"
echo "Rappel cible Phase 2 (docs/PHASE2_BUILD.md § 4): AGP 9.2.1, Kotlin 2.4.10, BOM 2026.08, android-36, Gradle 9.5.1."
echo

# 1. JDK
if has java; then
  JV="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([^"]+)".*/\1/')"
  report "JDK" "PASS" "java present ($JV)"
else
  report "JDK" "FAIL" "java introuvable dans le PATH"
fi

# 2. Android SDK
ADIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [ -n "$ADIR" ] && [ -d "$ADIR" ]; then
  report "Android SDK" "PASS" "$ADIR"
else
  report "Android SDK" "FAIL" "ANDROID_HOME/SDK_ROOT vides ou dossier inexistant"
fi

# 3. android-<SDK> plateformes
PLAT_DIR="$ADIR/platforms/android-${SDK_WANT}"
if [ -d "$PLAT_DIR" ]; then
  report "android-${SDK_WANT}" "PASS" "$PLAT_DIR"
else
  report "android-${SDK_WANT}" "FAIL" "dossier ${PLAT_DIR} absent"
fi

# 4. adb / sdkmanager
if has adb && has sdkmanager; then
  report "adb/sdkmanager" "PASS" "les deux presents"
elif has adb; then
  report "adb/sdkmanager" "FAIL" "sdkmanager absent du PATH"
elif has sdkmanager; then
  report "adb/sdkmanager" "FAIL" "adb absent du PATH"
else
  report "adb/sdkmanager" "FAIL" "ni adb ni sdkmanager dans le PATH"
fi

# 5. Gradle wrapper
WRAP="$(grep -oE 'gradle-[0-9.]+-bin\.zip' "$WRAPPER_FILE" 2>/dev/null | head -1)"
if [ -n "$WRAP" ]; then
  report "Gradle wrapper" "PASS" "wrapper $WRAP"
else
  report "Gradle wrapper" "FAIL" "fichier wrapper introuvable/invalide"
fi

# 6-8. AGP / Kotlin / BOM — lisibles depuis le catalog (présence garantie si le fichier est là)
for pair in "AGP:$AGP_WANT" "Kotlin:$KOTLIN_WANT" "BOM:$BOM_WANT"; do
  label="${pair%%:*}"; wantv="${pair##*:}"
  if [ -n "$wantv" ] && [ -f "$CATALOG" ]; then
    report "$label" "PASS" "$wantv (catalog)"
  else
    report "$label" "FAIL" "version ${label} absente du catalog"
  fi
done

# 9. État Git
case "${CHECK_TOOLCHAIN_GIT:-}" in
  clean) report "Git" "PASS" "(forcé) arbre propre" ;;
  dirty) report "Git" "FAIL" "(forcé) arbre NON propre" ;;
  *) if git rev-parse --git-dir >/dev/null 2>&1; then
       HEAD="$(git rev-parse --short HEAD)"
       if [ -z "$(git status --porcelain)" ]; then
         report "Git" "PASS" "$HEAD, arbre propre"
       else
         report "Git" "FAIL" "$HEAD, arbre NON propre"
       fi
     else
       report "Git" "FAIL" "pas de dépôt git"
     fi ;;
esac

echo
echo "--------------------------------------------------------------"
if [ "$commit_ok" -eq 1 ]; then
  echo "✅ TOOLCHAIN: TOUTES LES CASES VERDIES (PASS) — étape 1 conforme."
  exit 0
else
  echo "❌ TOOLCHAIN: AU MOINS UNE CASE ROUGE (FAIL)."
  echo "   Ne PAS passer à l'étape 2 / ne PAS étendre 2.x / ne PAS fermer l'issue #1."
  exit 1
fi