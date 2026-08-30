#!/bin/bash
# UI-regression audit: the size-transform-in-lazy-item bug ("settings texts on
# top of each other", fixed in 99c5f561 + 257a497).
#
# WHY IT EXISTS
#   AnimatedVisibility(enter = expandVertically(), exit = shrinkVertically())
#   (and the horizontal variants) inside a LazyColumn/LazyRow item{} measures
#   the content with unbounded height and can draw over the following item
#   while the layout animates. The safe pattern (see docs/ci-cd.md) is
#   fadeIn()/fadeOut() + animateContentSize() + clipToBounds().
#
#   This used to be a MANUAL checklist. It reappears silently on upstream
#   merges, so it is now an automated gate.
#
# HOW IT WORKS (no false positives by construction)
#   A size-transform animation can only overlap siblings if it lives inside a
#   lazy item. So the gate only inspects files that contain a LazyColumn or
#   LazyRow. If such a file uses a size-transform animation somewhere that is
#   NOT on the audited allowlist (every real call site was inspected by hand
#   and confirmed to be in a TopAppBar / Column / Row, not a lazy item), the
#   gate FAILS — the maintainer must re-inspect the container.
#
#   Files with no LazyColumn/LazyRow cannot contain the bug, and are skipped.
#
# Usage:  ./scripts/audit-settings-ui.sh
#         exit 0 = clean, 1 = a suspicious size-transform in a lazy file.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/composeApp/src"

# Real size-transform call sites, all inspected by hand (2026-08-26) and
# confirmed safe: they sit in a TopAppBar title/navigationIcon, a regular
# Column, or a Row — NEVER inside a LazyColumn/LazyRow item{}.
# Format: <relative-path>:<line>
ALLOWLIST_SITES=$(cat <<'EOF'
commonMain/kotlin/com/maxrave/simpmusic/App.kt:674
commonMain/kotlin/com/maxrave/simpmusic/App.kt:675
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/HomeScreen.kt:787
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/HomeScreen.kt:788
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/HomeScreen.kt:794
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/HomeScreen.kt:795
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library/LibraryScreen.kt:452
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library/LibraryScreen.kt:453
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/NowPlayingScreen.kt:1975
commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/NowPlayingScreen.kt:1976
commonMain/kotlin/com/maxrave/simpmusic/ui/component/FullWidthItems.kt:243
commonMain/kotlin/com/maxrave/simpmusic/ui/component/FullWidthItems.kt:244
commonMain/kotlin/com/maxrave/simpmusic/ui/component/FullWidthItems.kt:400
commonMain/kotlin/com/maxrave/simpmusic/ui/component/FullWidthItems.kt:401
EOF
)

# The bug only exists inside lazy items, so inspect only files that use lazy.
LAZY_FILES=$(grep -rlE "LazyColumn|LazyRow" --include=*.kt "$SRC" 2>/dev/null || true)

# Real call sites of a size-transform animation, excluding import statements and
# comments. Matches bare `expandVertically()`/`shrinkVertically()` and the
# horizontal variants (with or without a combined fade), which is the exact shape
# documented in docs/ci-cd.md as the overlap bug.
PATTERN='(expand|shrink)(Vertically|Horizontally)\(\)'

fail=0
found_any=0

for f in $LAZY_FILES; do
    rel="${f#"$SRC"/}"
    # Extract the real call lines (with file:line prefix).
    hits=$(grep -nE "$PATTERN" "$f" 2>/dev/null | grep -vE '^\s*[0-9]+:\s*(//|/\*|import )' || true)
    [ -z "$hits" ] && continue
    found_any=1
    while IFS= read -r line; do
        lineno="${line%%:*}"
        key="$rel:$lineno"
        if grep -qxF "$key" <<<"$ALLOWLIST_SITES"; then
            echo "  PASS: $key (inspected: safe container, not a lazy item)"
        else
            echo "  FAIL: $key — size-transform animation in a lazy file, not on the"
            echo "        audited allowlist. If it is INSIDE a LazyColumn/LazyRow item{},"
            echo "        it can overlap siblings (settings-overlap bug). Inspect the"
            echo "        container; if safe, add it to ALLOWLIST_SITES after review."
            fail=1
        fi
    done <<<"$hits"
done

echo ""
echo "============================================"
if [ "$found_any" -eq 0 ]; then
    echo "RESULT: no size-transform animations in lazy files — UI safe"
    exit 0
fi
if [ "$fail" -eq 0 ]; then
    echo "RESULT: UI size-transform audit passed (all call sites are safe)"
    exit 0
else
    echo "RESULT: SUSPICIOUS UI SIZE-TRANSFORM(S) — incoming overlap bug risk. Release blocked."
    exit 1
fi