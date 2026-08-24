#!/bin/bash
# ============================================================
# SpaceKai — upstream synchronisation helper
#
# Fetches SimpMusic (upstream), reports what changed, opens a
# sync branch and merges upstream/dev into it. Conflicts are
# DETECTED and REPORTED, never resolved automatically — the
# maintainer decides.
#
# Usage: ./scripts/update-upstream.sh
# ============================================================
set -euo pipefail

UPSTREAM_REMOTE="upstream"
UPSTREAM_BRANCH="dev"          # branch followed on maxrave-dev/SimpMusic
CORE_UPSTREAM_BRANCH="dev"     # branch followed on maxrave-dev/core

info()  { echo -e "\033[1;36m[upstream]\033[0m $*"; }
warn()  { echo -e "\033[1;33m[upstream]\033[0m $*"; }
fail()  { echo -e "\033[1;31m[upstream]\033[0m $*" >&2; exit 1; }

# --- 1. Clean working tree -----------------------------------
info "Checking git status..."
if [ -n "$(git status --porcelain)" ]; then
  fail "Working tree is not clean. Commit or stash your changes first."
fi

# --- 2. Upstream remote present? -----------------------------
if ! git remote | grep -qx "$UPSTREAM_REMOTE"; then
  warn "Remote '$UPSTREAM_REMOTE' not found — adding it."
  git remote add "$UPSTREAM_REMOTE" https://github.com/maxrave-dev/SimpMusic.git
fi

# --- 3. Fetch upstream ---------------------------------------
info "Fetching $UPSTREAM_REMOTE/$UPSTREAM_BRANCH..."
git fetch "$UPSTREAM_REMOTE" "$UPSTREAM_BRANCH"

UPSTREAM_HEAD=$(git rev-parse "$UPSTREAM_REMOTE/$UPSTREAM_BRANCH")
LOCAL_HEAD=$(git rev-parse HEAD)

info "Local  HEAD: $(git log -1 --format='%h %s' HEAD)"
info "Upstream HEAD: $(git log -1 --format='%h %s' "$UPSTREAM_REMOTE/$UPSTREAM_BRANCH")"

if [ "$UPSTREAM_HEAD" = "$LOCAL_HEAD" ]; then
  info "Already up to date — nothing to do."
  exit 0
fi

AHEAD=$(git rev-list --count "$UPSTREAM_REMOTE/$UPSTREAM_BRANCH"..HEAD 2>/dev/null || echo "?")
BEHIND=$(git rev-list --count HEAD.."$UPSTREAM_REMOTE/$UPSTREAM_BRANCH" 2>/dev/null || echo "?")
info "Local is $AHEAD commit(s) ahead, $BEHIND commit(s) behind upstream."

# New upstream commits (changelog hint)
info "New upstream commits since the merge base:"
git log --oneline "$(git merge-base HEAD "$UPSTREAM_REMOTE/$UPSTREAM_BRANCH")".."$UPSTREAM_REMOTE/$UPSTREAM_BRANCH" | head -30

# --- 4. Core submodule first --------------------------------
if [ -d core/.git ]; then
  info "Synchronising core submodule..."
  (
    cd core
    if ! git remote | grep -qx "$UPSTREAM_REMOTE"; then
      git remote add "$UPSTREAM_REMOTE" https://github.com/maxrave-dev/core.git
    fi
    git fetch "$UPSTREAM_REMOTE" "$CORE_UPSTREAM_BRANCH"
    git merge-base --is-ancestor HEAD "$UPSTREAM_REMOTE/$CORE_UPSTREAM_BRANCH" \
      && info "core is up to date with upstream." \
      || warn "core has diverged from upstream — merge core/ manually (see docs/UPSTREAM.md)."
  )
fi

# --- 5. Open a sync branch ------------------------------------
DATE=$(date +%Y-%m-%d)
SYNC_BRANCH="sync/upstream-$DATE"
if git show-ref --verify --quiet "refs/heads/$SYNC_BRANCH"; then
  info "Sync branch $SYNC_BRANCH already exists — reusing it."
  git checkout "$SYNC_BRANCH"
else
  info "Creating sync branch $SYNC_BRANCH from dev..."
  git checkout -b "$SYNC_BRANCH"
fi

# --- 6. Merge upstream/dev ------------------------------------
info "Merging $UPSTREAM_REMOTE/$UPSTREAM_BRANCH into $SYNC_BRANCH..."
if git merge --no-commit --no-ff "$UPSTREAM_REMOTE/$UPSTREAM_BRANCH" 2>/dev/null; then
  info "Merge succeeded without conflicts — changes are staged on $SYNC_BRANCH."
  info "Review, then:"
  info "  git commit"
  info "  ./gradlew :composeApp:compileKotlinJvm"
  info "  git push origin $SYNC_BRANCH   (CI runs android.yml)"
  info "  merge into dev, fast-forward main, cut a release"
else
  warn "Merge has CONFLICTS — NOT resolved automatically."
  warn "You are on $SYNC_BRANCH in a conflicted state. Resolve each conflict by hand"
  warn "(see docs/UPSTREAM.md for the known conflict surfaces), then:"
  warn "  git add <resolved files> && git commit"
  warn "  ./gradlew :composeApp:compileKotlinJvm"
  warn "  git push origin $SYNC_BRANCH"
  exit 1
fi
