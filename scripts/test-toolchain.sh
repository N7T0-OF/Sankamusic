#!/usr/bin/env bash
#
# test-toolchain.sh — Tests du script check-toolchain.sh (audit d'environnement)
#
# Exerce check-toolchain.sh sur des environnements simulés (fausses commandes
# java/adb/sdkmanager, ANDROID_HOME temporaire, catalog et wrapper temporaires,
# état Git forcé) pour verrouiller son comportement PASS/FAIL.
#
# Scénarios :
#   1. environnement ABSENT  -> une case rouge  → rc=1 (FAIL, stop)
#   2. environnement CONFORME -> toutes vertes   → rc=0 (PASS)
#   3. environnement PARTIEL -> une seule rouge  → rc=1 (une case suffit à bloquer)
#
# Usage : bash scripts/test-toolchain.sh

set -uo pipefail

SCRIPT="scripts/check-toolchain.sh"
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

# Un catalog minimal valide (les valeurs n'affectent pas le rc si les fichiers existent).
CAT="$TMP/versions.toml"
cat > "$CAT" <<'EOF'
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
composeBom = "2024.09.02"
androidSdk = "35"
EOF

# Un wrapper temporaire valide.
WRAP="$TMP/wrapper.properties"
printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.9-bin.zip\n' > "$WRAP"

# ── Scénario 1 : environnement absent → rc=1 ───────────────────────────
# On force ANDROID_HOME vers un chemin vide, PATH sans outils.
rc=$(ANDROID_HOME="$TMP/none" ANDROID_SDK_ROOT="" \
     PATH="/usr/bin:/bin" \
     CHECK_TOOLCHAIN_CATALOG="$CAT" CHECK_TOOLCHAIN_WRAPPER="$WRAP" \
     CHECK_TOOLCHAIN_GIT="dirty" bash "$SCRIPT" >/dev/null 2>&1; echo $?)
assert "env absent → rc=1" "$rc" "1"

# ── Scénario 2 : environnement conforme → rc=0 ──────────────────────────
mkdir -p "$TMP/bin" "$TMP/sdk/platforms/android-35"
# fausses commandes
printf '#!/usr/bin/env bash\necho openjdk version "17.0.9" 2>/dev/null\n' > "$TMP/bin/java"
printf '#!/usr/bin/env bash\nexit 0\n' > "$TMP/bin/adb"
printf '#!/usr/bin/env bash\nexit 0\n' > "$TMP/bin/sdkmanager"
chmod +x "$TMP/bin/java" "$TMP/bin/adb" "$TMP/bin/sdkmanager"

rc=$(ANDROID_HOME="$TMP/sdk" \
     PATH="$TMP/bin:/usr/bin:/bin" \
     CHECK_TOOLCHAIN_CATALOG="$CAT" CHECK_TOOLCHAIN_WRAPPER="$WRAP" \
     CHECK_TOOLCHAIN_GIT="clean" bash "$SCRIPT" >/dev/null 2>&1; echo $?)
assert "env conforme → rc=0" "$rc" "0"

# ── Scénario 3 : environnment partiel (vide) → rc=1 ─────────────────────
rc=$(ANDROID_HOME="$TMP/sdk" \
     PATH="$TMP/bin:/usr/bin:/bin" \
     CHECK_TOOLCHAIN_CATALOG="$CAT" CHECK_TOOLCHAIN_WRAPPER="$WRAP" \
     CHECK_TOOLCHAIN_GIT="dirty" bash "$SCRIPT" >/dev/null 2>&1; echo $?)
assert "git sale seul → rc=1" "$rc" "1"

echo
if [ "$FAILED" -eq 0 ]; then
  echo "✅ $PASSED/$COUNT scénarios OK — check-toolchain.sh conforme."
  exit 0
else
  echo "❌ $FAILED scénario(s) en échec sur $COUNT — l'audit toolchain a régressé."
  exit 1
fi