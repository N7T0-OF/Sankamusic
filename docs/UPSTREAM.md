# Upstream synchronization (SpaceKai ← SimpMusic)

SpaceKai is an **add-on layer** over SimpMusic, not a copy-and-edit fork.
SimpMusic remains the upstream engine; SpaceKai adds branding, features and
customizations on top. This document explains how to keep SpaceKai in sync
with new SimpMusic releases **without** redoing SpaceKai work by hand.

## Repositories

| | Repository | Branch followed |
| --- | --- | --- |
| **Upstream** | `https://github.com/maxrave-dev/SimpMusic.git` | `main` |
| **SpaceKai** | `https://github.com/N7T0-OF/Sankamusic` | `main` (release) |

## Version tracking

SpaceKai is always based on a specific SimpMusic version. Keep this up to date
after every sync:

| Field | Where | Example |
| --- | --- | --- |
| SpaceKai version | `gradle/libs.versions.toml` → `version-name` / `version-code` | `2.0.0` / `73` |
| Based on SimpMusic | this file, **Current upstream** below | `v1.7.0` |

**Current upstream:** SimpMusic `v1.7.0` (code 56, latest tag — verified 2026-08-26) — SpaceKai ships `2.0.0` / code `73`

## Architecture principle

```
SimpMusic upstream
      ↓  (merge/rebase, resolve conflicts by hand)
SpaceKai patch layer   ← SpaceKai customizations live here
      ↓
SpaceKai features + branding + UI
      ↓
SpaceKai release (APK + desktop packages)
```

Never overwrite SpaceKai changes with upstream blindly. When a SpaceKai
feature can be implemented without touching upstream code, prefer:

- extension / wrapper / interface / configuration
- extra composable / repository decorator / service / module / adapter
- a `// SPACEKAI FEATURE` / `// SPACEKAI CUSTOMIZATION` comment marking the spot

## Synchronization procedure

One command:

```bash
./scripts/update-upstream.sh
```

What it does (never auto-publishes):

1. Checks the working tree is clean.
2. Adds/verifies the `upstream` git remote.
3. Fetches `upstream/main`.
4. Reports the upstream version available vs. the local version.
5. Creates a `upstream-sync` branch and merges upstream into it.
6. Prints a report: changed files, conflicts to resolve by hand.
7. **Scans the upstream-changed files for `SPACEKAI` markers** and lists every
   file upstream touched that also carries a SpaceKai hook — the silent-break
   hotspots that must be re-applied by hand even when the merge reports no
   conflict (the update-checker URL is the classic one).
7b. **Verifies the critical hooks survived the merge** — exact-signature checks
   on `App.kt` (persistence re-apply, swipe, liquid-glass bar), `conveyor.conf`
   (`vcs-url` = N7T0-OF) and `UpdateRepositoryImpl` (SpaceKaiUpdateConfig). A
   clean merge can still drop a hook when upstream restructures the region
   around it; 7b reports it as LOST even without a conflict marker. Modules not
   checked out locally are flagged "verify on the live repo", not failed.

Manual fallback:

```bash
git remote add upstream https://github.com/maxrave-dev/SimpMusic.git
git fetch upstream
git checkout -b upstream-sync
git merge upstream/main --no-edit   # resolve conflicts by hand
```

### Conflict policy

If upstream changed a file that also contains SpaceKai changes, **never** pick
`ours` or `theirs` automatically. The maintainer decides, file by file:

```
UPSTREAM CHANGED:  PlayerScreen.kt
SPACEKAI CHANGED:  PlayerScreen.kt
CONFLICT:          PlayerScreen.kt   → resolve manually, re-apply SpaceKai bits
```

## SpaceKai vs upstream files

| Kind | Paths | Policy |
| --- | --- | --- |
| **SpaceKai-owned** | `docs/`, `scripts/`, `RELEASE.md`, `fastlane/` (metadata), branding resources (`app_name`, icons) | Keep SpaceKai versions on conflict |
| **Upstream-owned** | `core/*`, `composeApp/src/**` (except `spacekai/`), `androidApp/src/**`, `desktopApp/` | Take upstream, re-apply SpaceKai changes on top |
| **SpaceKai-owned, under composeApp** | `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/spacekai/` | **Never present upstream** — the git merge leaves it untouched (upstream has no such files); never delete/replace it when resolving conflicts |
| **Locked assets** | `circle_app_icon.png`, `app_icon.png` (all copies) | **Never modify** — verified by `scripts/verify-icons.sh` |

## Known conflict hotspots

These files carry SpaceKai customizations and are frequently touched upstream:

- **`composeApp/src/commonMain/.../App.kt` — the central hook point.**
  Carries the startup persistence re-apply (`applyPersistedSpaceKaiFeatures`,
  ~L174), the nav-bar swipe wiring (`onSwipeToNext/Previous`), the bar-style
  selection (~L491-506: liquid-glass vs translucent) and the analytics-tab
  gating. Upstream rewrites App.kt often; every sync must re-check ALL of
  these hooks (grep `SPACEKAI` in App.kt).
- `composeApp/src/commonMain/.../ui/screen/player/` (player UI, landscape, haptics)
- `composeApp/src/commonMain/.../ui/screen/home/SettingScreen.kt`
- `composeApp/src/commonMain/.../ui/theme/` (SpaceKai theming)
- `composeApp/src/commonMain/.../ui/component/AppBottomNavigationBar.kt` +
  `LiquidGlassAppBottomNavigationBar.kt` (swipe-to-skip, bar variants)
- `composeApp/src/commonMain/.../viewModel/LogInViewModel.kt` (sp_dc chain,
  future `spotifySync` hook)
- `androidApp/src/main/AndroidManifest.xml` (deep links, label)
- `gradle/libs.versions.toml` (version bump on every sync)
- `core/data/.../update/UpdateRepositoryImpl.kt` — the update checker URL is
  **silently reverted** to `maxrave-dev/SimpMusic` on merge (no conflict marker,
  surrounding code identical). After every sync, grep for `maxrave-dev/SimpMusic`
  in `core/data/.../update/` and re-apply `SpaceKaiUpdateConfig.latestReleaseApiUrl`
  (owner `N7T0-OF`, repo `Sankamusic`). See `SPACEKAI-ARCHITECTURE.md` → Update checker.
- `conveyor.conf` → `app.vcs-url` — **silently reverted** to
  `maxrave-dev/SimpMusic` on merge; Conveyor then generates every download URL
  (`download.html`, `.appinstaller`, `.exe` wrapper) pointing at the *wrong*
  repo's releases. Must stay `https://github.com/N7T0-OF/Sankamusic`.

## Test procedure (before any release)

1. `./scripts/verify-icons.sh` — icons must be unchanged.
2. `./gradlew test` and `./gradlew lint`.
3. `./gradlew androidApp:assembleRelease` + `apksigner verify`.
4. Install the APK on a device/emulator: fresh install, **upgrade** from the
   previous SpaceKai, playback, navigation, Spotify, settings.
5. If no device is available, state clearly: *installation réelle non testée*.

## Release procedure

See [`RELEASE.md`](../RELEASE.md) for the full flow. Summary:

1. Bump `version-name` / `version-code` in `gradle/libs.versions.toml`.
2. Generate the changelog: `./scripts/generate-fastlane-changelog.sh`.
3. Push tag `vX.Y.Z` → CI builds the single `SpaceKai-vX.Y.Z.apk` +
   desktop packages + `SHA256SUMS.txt` → draft release for manual publish.
4. **Never publish without validation.**

## License (GPL-3.0)

SimpMusic is **GPL-3.0**. SpaceKai is a derivative work, so:

- SpaceKai must be distributed under GPL-3.0 with the license notice preserved.
- The original copyright and attribution must stay intact (do not present
  SpaceKai as the original project).
- Source code must be made available for any distributed binaries.
- SpaceKai itself may not be relicensed to a closed/commercial license.

Keep the `LICENSE` file and the upstream credits intact in every release.
