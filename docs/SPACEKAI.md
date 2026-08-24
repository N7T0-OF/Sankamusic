# SpaceKai layer architecture

SpaceKai is a **layer over SimpMusic**. The upstream engine (`maxrave-dev/SimpMusic`) is the
base; SpaceKai adds features, UI and branding on top. Everything SpaceKai owns is kept in
identifiable files so that upstream merges (see `docs/UPSTREAM.md`) only ever conflict with
the small set of files where SpaceKai *must* touch upstream code.

```
SimpMusic upstream (base engine)
        │
        ▼
┌───────────────────────────────┐
│ SpaceKai layer               │
│  · core/common SpaceKaiFeatures│
│  · composeApp UI / navigation │
│  · Spotify sync (OAuth PKCE)  │
│  · haptics / themes / player  │
│  · branding (locked icons)    │
└───────────────────────────────┘
        │
        ▼
  SpaceKai APK + desktop packages
```

## Feature flags

`core/common/.../SpaceKaiFeatures.kt` is the single registry. Each flag gates the feature's
**entry points** (UI rows, deep links, nav destinations). Flags are compile-time constants —
R8 strips a disabled feature's UI branch.

| Flag | Feature |
|------|---------|
| `SPOTIFY_SYNC` | Spotify playlist import (OAuth PKCE) |
| `HAPTICS` | Vibration settings + intensity slider |
| `MINIMALISTIC_NAVIGATION` | Minimalistic nav bar style |
| `CUSTOM_NAVIGATION` | Nav bar style selector |
| `DYNAMIC_COLOR` | Dynamic color fixes + custom color |
| `LANDSCAPE_PLAYER` | Full-screen landscape player |
| `SPACEKAI_UPDATE_CHECKER` | Update checker → SpaceKai releases |
| `CUSTOM_PLAYER_INFO` | (planned, off) player info toggles |
| `DOWNLOAD_WIFI_ONLY` | (planned, off) |

## SpaceKai-owned files (safe from upstream merges)

These are pure additions — upstream never writes them, so they can never conflict:

```
core/common/.../common/SpaceKaiFeatures.kt
core/service/spotify/.../SpotifyPkce.kt
core/service/spotify/.../model/response/spotify/playlist/*.kt
core/service/spotify/.../SpotifyOAuthTokenResponse.kt
core/domain/.../repository/SpotifySyncRepository.kt
core/data/.../repository/SpotifySyncRepositoryImpl.kt
composeApp/.../viewModel/SpotifySyncViewModel.kt
composeApp/.../ui/screen/login/SpotifySyncScreen.kt
composeApp/.../ui/navigation/destination/login/SpotifySyncDestination.kt
docs/UPSTREAM.md
docs/SPACEKAI.md
scripts/update-upstream.sh
```

## Files that touch upstream code (expected conflict surface)

These carry both upstream and SpaceKai edits. An upstream sync will conflict here; resolve by
hand (see `docs/UPSTREAM.md` → "Known conflict surfaces"):

- `gradle/libs.versions.toml` — SpaceKai version, update-checker repo
- `androidApp/build.gradle.kts` — `singleReleaseApk` flag
- `build_and_sign_apk.sh` — single-APK signing
- `.github/workflows/release.yml` — SpaceKai release pipeline
- `composeApp/.../App.kt` — deep links, nav styles
- `composeApp/.../SettingScreen.kt` — SpaceKai settings sections
- `composeApp/.../ui/component/` — nav bar components
- `core/.../DataStoreManager*` — SpaceKai settings keys
- `core/service/spotify/.../SpotifyClient.kt` + `Spotify.kt` — OAuth endpoints (additions, low risk)

## How to add a SpaceKai feature

1. Add a flag to `SpaceKaiFeatures` (`true` once ready).
2. Put the implementation in a new, identifiable file (`FooBar*`), never interleaved with
   upstream code.
3. Gate the entry point (UI row / deep link) behind the flag.
4. Mark any unavoidable upstream edit with `// SPACEKAI CUSTOMIZATION`.
5. Add the file to the "SpaceKai-owned" list above if it is a pure addition.
6. Verify with `./gradlew :composeApp:compileKotlinJvm` locally; the CI check runs on dev.

## Locked assets

`circle_app_icon.png` and `app_icon.png` are locked SpaceKai branding — never modified by any
build or optimization step (SHA-256 verified, see `docs/UPSTREAM.md`).
