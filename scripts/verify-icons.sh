#!/bin/bash
# Verify the SpaceKai brand icons are byte-identical to the locked baseline.
#
# The user has hand-crafted these icons. They must NEVER be modified,
# optimized, compressed, resized, recolored, regenerated or replaced. This
# script pins their SHA-256 and fails if any of them has changed. It runs in
# the release gate (validate-tag) and in the nightly config check.
#
# Usage:  ./scripts/verify-icons.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Locked baseline (SHA-256  path)
# circle_app_icon.png is identical in both locations.
# app_icon.png differs between the Android drawable and the Compose resource.
readonly LOCKED=(
  "bf8408f8e94ff1580b441c33870ff8f77417eff408aa2b217355595647134ef2  composeApp/icon/circle_app_icon.png"
  "bf8408f8e94ff1580b441c33870ff8f77417eff408aa2b217355595647134ef2  composeApp/src/commonMain/composeResources/drawable/circle_app_icon.png"
  "b83270dbe4e74216fe28af62280e86942fa6c1d04ac5f649bfb9a7ca760386eb  androidApp/src/main/res/drawable/app_icon.png"
  "baa3be07438efe624f9c8a03f824b3bd276cbbf85fe484741cde81bfe4c42020  composeApp/src/commonMain/composeResources/drawable/app_icon.png"
)

failed=0
for entry in "${LOCKED[@]}"; do
  expected="${entry%%  *}"
  path="${entry#*  }"
  if [ ! -f "$path" ]; then
    echo "::error::Icon missing: $path"
    failed=1
    continue
  fi
  actual=$(sha256sum "$path" | awk '{print $1}')
  if [ "$actual" != "$expected" ]; then
    echo "::error::Icon CHANGED: $path"
    echo "  expected: $expected"
    echo "  actual:   $actual"
    echo "  This icon is a locked brand asset — do not modify/optimize/replace it."
    failed=1
  else
    echo "OK: $path"
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "::error::Brand icon verification FAILED."
  exit 1
fi
echo "All brand icons unchanged (locked baseline)."
