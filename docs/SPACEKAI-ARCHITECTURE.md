# SpaceKai add-on architecture

SpaceKai is an **add-on layer** over SimpMusic, not a copy-and-edit fork. The
goal: keep receiving SimpMusic upstream updates without redoing SpaceKai work
by hand. This document describes the layer itself — how it is wired, where
each feature hooks into upstream, and how to add a feature without touching
upstream code more than necessary.

For the git-level synchronization (fetch, merge, conflicts) see
[`UPSTREAM.md`](UPSTREAM.md).

## The layer

```
SimpMusic upstream (core/, composeApp/, androidApp/, desktopApp/)
        │  never modified except for thin, marked hooks
        ▼
spacekai/  (composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/spacekai/)
   ├── SpaceKaiFeatures.kt      feature flags (data class + defaults)
   ├── SpaceKai.kt              configSpaceKai() / isSpaceKaiAvailable() / currentFeatures()
   ├── SpaceKaiModule.kt        Koin module (spacekaiModule)
   └── SpaceKaiUpdateConfig.kt  release repo source of truth (N7T0-OF/Sankamusic)
        ▼
SpaceKai-gated UI / behaviour (guarded by isSpaceKaiFeatureEnabled(...))
```

Everything SpaceKai does is **feature-flagged**. A flag that is off is dead
code that cannot conflict with upstream. The layer is handed its flags at
startup via `configSpaceKai(...)` — the exact same shape as `configLastfm` /
`configCrashlytics`:

- `androidApp/.../SimpMusicApplication.kt` → `configSpaceKai(SpaceKaiFeatures.allEnabled)` + `loadKoinModules(spacekaiModule)`
- `composeApp/src/jvmMain/.../DesktopApp.kt` → same

If an upstream merge removes the `configSpaceKai` call, the app simply behaves
as vanilla SimpMusic (`isSpaceKaiAvailable()` returns false). Nothing breaks.

## Feature flags (spec §7)

Defined in `SpaceKaiFeatures.kt`:

| Flag | Default | What it gates |
| --- | --- | --- |
| `spotifySync` | off | Spotify sync / Canvas / lyrics customizations layered on the Spotify service |
| `customNavigation` | off | Custom bottom navigation (tabs, order, icons) |
| `minimalisticNavigation` | off | Minimalistic navigation variant |
| `dynamicColor` | off | SpaceKai dynamic colour overrides on the theme |
| `landscapePlayer` | off | Landscape-aware Now Playing layout |
| `haptics` | off | Haptic feedback on player / navigation — **implemented** (see below) |
| `downloadWifiOnly` | off | Wi-Fi-only download toggle in Settings |
| `customPlayerInfo` | off | Custom player info line rendering |

Guard code with:

```kotlin
if (isSpaceKaiFeatureEnabled(SpaceKaiFeatures::haptics)) { ... }
// or inject: val features: SpaceKaiFeatures by inject()  (from spacekaiModule)
```

## Extension-point map

Prefer **extension over modification** (spec §6). For each feature, this is
where it hooks into upstream — the file to *read* is upstream, the SpaceKai
code lives in `spacekai/` (or a sibling package) and is called from the hook.

| Feature | Hook point (upstream file) | SpaceKai approach |
| --- | --- | --- |
| `spotifySync` | `core/service/spotify/` (SpotifyRepository) | Decorate / wrap the repository; add a `SpotifySyncSpaceKai` service instead of editing the upstream repository |
| `customNavigation` | `composeApp/.../ui/navigation/` (NavHost, bottom bar) | Compose a SpaceKai tab list / `BottomNavScreen` set; keep upstream's as the default. Also gates the **nav-bar swipe to skip tracks** (`swipeToSkip` in `AppBottomNavigationBar.kt`, wired from `App.kt` via `onSwipeToNext` / `onSwipeToPrevious` → `UIEvent.Next` / `UIEvent.SkipToPrevious`; left = next, right = previous) |
| `minimalisticNavigation` | same as above | Flag toggles the compact variant; same navigation composable |
| `dynamicColor` | `composeApp/.../ui/theme/` | SpaceKai `ColorScheme` override applied on top of the upstream theme |
| `landscapePlayer` | `composeApp/.../ui/screen/player/` | Extra landscape composable branch, gated by the flag |
| `haptics` | player / navigation click handlers | `HapticsSpaceKai` wrapper around existing click handlers — **done**: `NowPlayingScreen.kt` play/pause button calls `HapticsSpaceKai.onClick(LocalHapticFeedback.current)` |
| `downloadWifiOnly` | `composeApp/.../ui/screen/home/SettingScreen.kt` | An extra `SettingItem` in the Settings column, gated by the flag |
| *(all)* | Settings screen | `SpaceKaiSettingsSection` (`spacekai/ui/`) renders one toggle per flag — visible only when `isSpaceKaiAvailable()` |
| `customPlayerInfo` | Now Playing info row | SpaceKai composable swapped in place of the upstream info row |
| *(all)* | update checker | `core/data/.../update/UpdateRepositoryImpl.checkForGithubReleaseUpdate()` must hit **SpaceKai** releases, not SimpMusic's. Source of truth: `SpaceKaiUpdateConfig` (`spacekai/`) — owner `N7T0-OF`, repo `Sankamusic`. See **Update checker** below |

Mark every touchpoint in upstream files with:

```kotlin
// SPACEKAI FEATURE
// SPACEKAI CUSTOMIZATION
// UPSTREAM COMPATIBILITY
```

## Adding a feature (step by step)

1. Add a flag in `SpaceKaiFeatures.kt` (with a sensible default).
2. Write the SpaceKai code in its own file under `spacekai/` (or a
   `spacekai/features/<name>/` package when it grows).
3. Hook it into the upstream extension point (see map above) with a
   `// SPACEKAI FEATURE` marker and a `isSpaceKaiFeatureEnabled(...)` guard.
4. When the feature needs UI strings, add them to
   `composeApp/src/commonMain/composeResources/values/strings.xml` (and the
   Crowdin locales later — never edit translation files directly).
5. Update the feature table in this file.
6. Run `./scripts/pre-release-report.sh` before release; the report includes
   the feature checklist.

## Flag persistence (runtime toggles)

The Settings section (`SpaceKaiSettingsSection`) lets the user flip every flag
at runtime. Toggles are persisted as generic DataStore string keys
(`spacekai_<flag>`, value `true`/`false`) through `SharedViewModel.getString` /
`putString` — the same mechanism as `hide_nav_label` — so the SpaceKai layer
needs no typed keys in the core submodule.

Two call sites share one merge function (`mergePersistedSpaceKaiFeatures` in
`spacekai/SpaceKai.kt`), so they can never drift apart:

- **Startup** — `App()` runs `applyPersistedSpaceKaiFeatures(getString)` in a
  `LaunchedEffect`, re-issuing `configSpaceKai` with the stored flags merged
  over the build-time defaults. A user who turned a flag OFF keeps it off after
  a restart.
- **Settings** — each toggle writes its key AND re-issues `configSpaceKai`, so
  `isSpaceKaiFeatureEnabled(...)` reflects the change immediately.

The merge rule: a stored `true`/`false` (an explicit user choice) wins over the
build-time default; an absent key keeps the default. Because flags are now
persisted, `isSpaceKaiAvailable()` means "the layer was configured at all" — it
stays true even with every flag off, or the Settings section would hide itself
with no way back.

## Update checker (spec §25/§26)

SpaceKai users must be offered **SpaceKai** releases only — never SimpMusic
releases (those lack the SpaceKai layer). The upstream app checks
`maxrave-dev/SimpMusic` releases; that URL lives in the `core/data` module
(`UpdateRepositoryImpl.checkForGithubReleaseUpdate()`), which is upstream code.

`SpaceKaiUpdateConfig` (`spacekai/SpaceKaiUpdateConfig.kt`) is the single
source of truth:

```kotlin
object SpaceKaiUpdateConfig {
    const val repoOwner: String = "N7T0-OF"
    const val repoName: String = "Sankamusic"
    val latestReleaseApiUrl: String = "https://api.github.com/repos/N7T0-OF/Sankamusic/releases/latest"
}
```

Wire it into `UpdateRepositoryImpl`:

```kotlin
// SPACEKAI FEATURE: point the update checker at SpaceKai releases.
val url = "${SpaceKaiUpdateConfig.latestReleaseApiUrl}"
```

**This is a silent-break hotspot.** Upstream merges restore the SimpMusic repo
URL without any conflict marker (the surrounding code is identical), so after
every `./scripts/update-upstream.sh` run, grep for `maxrave-dev/SimpMusic` in
`core/data/.../update/` and re-apply the line. The `updateChannel` setting
(GitHub / F-Droid) is untouched — SpaceKai ships via GitHub releases.

## Conflict policy on upstream merges

- Files **owned by SpaceKai** (`spacekai/`, `docs/`, `scripts/`, branding,
  `fastlane/` metadata, `RELEASE.md`): keep the SpaceKai version.
- Files **owned by upstream** (`core/*`, `composeApp/src/**`, `androidApp/`,
  `desktopApp/`): take upstream, then re-apply the marked SpaceKai hooks on
  top. The `// SPACEKAI FEATURE` markers make this a search-and-reapply, not a
  guess.
- **Never** resolve a conflict with a blind `ours`/`theirs`. See
  [`UPSTREAM.md`](UPSTREAM.md) for the full procedure.

## Branding

The app is named **SpaceKai** (see `app_name.xml` / `desktopApp` display-name).
The upstream package `com.maxrave.simpmusic` is preserved so upgrades from a
previous SpaceKai build keep working. The brand icons
(`circle_app_icon.png`, `app_icon.png`) are **locked** — see
[`scripts/verify-icons.sh`](../scripts/verify-icons.sh).

**Performance keys**: lazy lists must never key on `hashCode()` of recreated
data (it rebuilds the whole list and re-requests every image on refresh — the
home feed's original bug, fixed 2026-08-26 with index keys).
[`scripts/verify-perf-keys.sh`](../scripts/verify-perf-keys.sh) fails the
release gate if the pattern creeps back; it runs inside `pre-release-report.sh`
and knows the audited-stable exceptions (singleton filter lists, filter-
preserved Pair references).