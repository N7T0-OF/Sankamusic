#!/bin/bash
# ZERO-FALSE-POSITIVE audit: fail the release if any SpaceKai feature flag is a
# "decorative toggle" — i.e. it exists, is shown in Settings, but does not drive
# any real behaviour outside the declaration / settings files.
#
# WHY THIS EXISTS
#   Several releases shipped flags that were listed as "implemented" yet were only
#   toggles in SpaceKaiSettingsSection.kt with zero code wired to them. A flag that
#   does nothing but flip a DataStore key is a "false positive feature".
#
# HOW IT WORKS
#   For each flag in SpaceKaiFeatures, count the real call sites of
#   `SpaceKaiFeatures::<flag>` / `.<flag>` / `features.<flag>` OUTSIDE the three
#   files that only declare / persist / render the toggle:
#     - spacekai/SpaceKaiFeatures.kt      (declaration + defaults)
#     - spacekai/SpaceKai.kt              (config + persistence merge)
#     - spacekai/ui/SpaceKaiSettingsSection.kt  (settings toggles)
#   A flag with 0 real references is a decorative toggle -> FAIL (blocks release).
#
# Running directly prints a report and exits non-zero when a decorative toggle is
# found. `pre-release-report.sh` calls it as a critical gate.
#
# A non-flag usage inside the three ignored files could in theory exist without
# being a toggle; none does today — keep the grep patterns exact.
#
# SCOPE DECISION (2026-08-26, see docs/WIRING-P0.md): this gate scans
# composeApp/src only. core/ cannot read the flags (spacekai/ lives in
# composeApp; Clean Architecture forbids a core -> composeApp dependency), so
# every wiring that touches core (Spotify service, downloads) must SURFACE
# through composeApp: the ViewModel/UI reads the flag and passes its state to
# core as a parameter. The real reference therefore lives in composeApp and
# this scan finds it — do not extend the scan to core/ paths.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"

# Files that legitimately mention the flags without wiring behaviour.
IGNORED='spacekai/SpaceKaiFeatures.kt|spacekai/SpaceKai.kt|spacekai/ui/SpaceKaiSettingsSection.kt'

# A flag is a real, wired feature if it has >=MIN_CALL_SITES real references.
MIN_CALL_SITES="${MIN_CALL_SITES:-1}"

fail=0

audit_flag() {
    local flag="$1"
    # Everything that references the flag, minus the three toggle/declaration files.
    # Both greps (qualified `SpaceKaiFeatures::flag` and bare `flag`) are unioned and
    # deduped so a single line is never double-counted.
    local refs
    refs=$(grep -rn --include='*.kt' "SpaceKaiFeatures::$flag\b" "$SRC" 2>/dev/null | grep -Ev "$IGNORED" || true)
    local props
    props=$(grep -rn --include='*.kt' "\b$flag\b" "$SRC" 2>/dev/null | grep -Ev "$IGNORED" || true)

    local all
    all=$(printf '%s\n%s\n' "$refs" "$props" | grep -Ev '^$' | sort -u)
    local count=0
    if [ -n "$all" ]; then count=$(printf '%s\n' "$all" | grep -c . ); fi

    if [ "$count" -ge "$MIN_CALL_SITES" ]; then
        echo "  PASS: $flag — $count real call site(s)"
    else
        echo "  FAIL: $flag — DECORATIVE TOGGLE (0 real call sites; only Settings/declaration)"
        [ -n "$all" ] && printf '        %s\n' "$all" | head -5
        fail=1
    fi
}

echo "============================================"
echo "SPACEKAI ZERO-FALSE-POSITIVE AUDIT (flags)"
echo "============================================"
echo "(flags with 0 real call sites outside Settings/declaration are decorative)"
echo ""

if [ ! -d "$SRC" ]; then
    echo "FAIL: source dir not found: $SRC"
    echo "Run from the repo root; composeApp/ must be present."
    exit 1
fi

# Enumerate the flag names from SpaceKaiFeatures.kt field declarations.
FLAGS=$(grep -oE 'val [a-zA-Z]+: Boolean =' "$SRC/spacekai/SpaceKaiFeatures.kt" | sed -E 's/val ([a-zA-Z]+):.*/\1/')

if [ -z "$FLAGS" ]; then
    echo "WARN: no flags parsed from SpaceKaiFeatures.kt — nothing to audit"
    exit 0
fi

for f in $FLAGS; do
    audit_flag "$f"
done

# Layer-level wiring: the persistence machinery must be CALLED from outside
# spacekai/SpaceKai.kt, or the changelog claim "flags survive a restart" is
# decorative at the layer level. SpaceKai.kt is in the IGNORED set above, so
# the flag loop cannot see this — it needs its own check. The settings section
# reading the merge does not count; only applyPersistedSpaceKaiFeatures being
# invoked at startup (App/DesktopApp) proves the re-apply actually happens.
echo ""
echo "Persistence wiring:"
PERSIST_CALLS=$(grep -rn --include='*.kt' "applyPersistedSpaceKaiFeatures" "$SRC" 2>/dev/null | grep -v "spacekai/SpaceKai.kt" || true)
PC=$(printf '%s\n' "$PERSIST_CALLS" | grep -Ev '^$' | grep -c . || true)
if [ "${PC:-0}" -ge 1 ]; then
    echo "  PASS: applyPersistedSpaceKaiFeatures wired — $PC call site(s) outside the declaration (flags survive a restart)"
else
    echo "  FAIL: applyPersistedSpaceKaiFeatures NEVER called at startup — 'toggles survive a restart' is decorative"
    fail=1
fi

echo ""
echo "============================================"
if [ "$fail" -eq 0 ]; then
    echo "RESULT: no decorative SpaceKai flags — feature wiring OK"
    exit 0
else
    echo "RESULT: DECORATIVE TOGGLES FOUND — feature declared but not wired."
    echo "        Wire the flagged features (CODE + UI + INTEGRATION) before release,"
    echo "        or remove their toggle — do NOT ship them as 'implemented'."
    exit 1
fi