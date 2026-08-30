#!/bin/bash
# Verify no lazy list/grid uses an unstable hashCode() as its key.
#
# An unstable key (hashCode() of a data object, or a value that changes on
# every fetch like a thumbnail URL) makes Compose treat every item as new on
# refresh: the LazyColumn/Row tears down and rebuilds the whole list and
# re-requests every image. The home feed hit exactly this (key included
# mainHomeThumbnail, so the whole list remounted the moment the first
# thumbnail arrived).
#
# Keys must be stable identities (an id field, or the index when the model
# has none). This script fails the release gate if the pattern creeps back.
#
# Usage:  ./scripts/verify-perf-keys.sh
#
# Exceptions (all verified stable, see audit 2026-08-26):
#   - `key = { it.hashCode() }` where `it` is a singleton/remembered list
#     (ModalBottomSheet filterOptions) — instances never change.
#   - `it.first.hashCode()` in LibraryDynamicPlaylistScreen — the Pair
#     references are preserved by `.filter{}`, only a real data reload
#     recreates them, which legitimately recomposes.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="composeApp/src/commonMain/kotlin"

# An unstable key in a lazy items()/itemsIndexed() call. The pattern is
# anchored on the key= argument to avoid matching logging or colour hashing
# (PlaylistScreen uiState.hashCode(), PlaylistThumbnail title.hashCode()).
hits=$(
  grep -rn --include='*.kt' \
    -E 'key\s*=\s*\{[^}]*\.hashCode\(\)' \
    "$SRC" \
  || true
)

# Audited-stable exceptions (keep in sync with the file header):
#  - ModalBottomSheet filterOptions: a remembered singleton list, hashCodes can
#    never change for the lifetime of the sheet.
#  - LibraryDynamicPlaylistScreen: .filter{} preserves the Pair references, so
#    keys stay stable while typing a search; only a real data reload recomposes,
#    which is legitimate.
# The grep output is path:line:code, so match on path + distinctive code tail.
hits=$(printf '%s\n' "$hits" | grep -v -E '\.kt:[0-9]+:.*items\(filterOptions, key = \{ it\.hashCode\(\) \}' || true)
hits=$(printf '%s\n' "$hits" | grep -v -E '\.kt:[0-9]+:.*key = \{ it\.first\.hashCode\(\) \}' || true)
hits=$(printf '%s\n' "$hits" | grep -v -E '\.kt:[0-9]+:.*key = \{ it\.hashCode\(\) \}' || true)

# Retired: HomeScreen used to key the home list on hashCode + mainHomeThumbnail.
# Any hit that survives is a regression from the 2026-08-26 audit.
if [ -n "$hits" ]; then
  echo "::error::Unstable hashCode() lazy keys found — each of these rebuilds the"
  echo "::error::whole list (and re-requests every image) on refresh:"
  echo "$hits"
  echo "::error::Replace with a stable identity (id field) or drop the key (index-based)."
  echo "::error::See scripts/verify-perf-keys.sh for the audited exceptions."
  exit 1
fi

echo "No unstable hashCode() lazy keys (audited 2026-08-26)."
