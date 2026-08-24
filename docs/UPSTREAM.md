# Upstream Tracking (SimpMusic → SpaceKai)

SpaceKai is a **customization layer over SimpMusic**, not a rewritten fork. SimpMusic
(`maxrave-dev/SimpMusic`) is the upstream engine; SpaceKai adds its own features, UI and
branding on top. This document explains how to keep both in sync.

## Repositories

| Role      | Repository                                  |
|-----------|---------------------------------------------|
| Upstream  | `https://github.com/maxrave-dev/SimpMusic`  |
| SpaceKai  | `https://github.com/N7T0-OF/Sankamusic`     |

The `core` module is a **git submodule**:

| Role      | Repository                              |
|-----------|-----------------------------------------|
| Upstream  | `https://github.com/maxrave-dev/core`   |
| SpaceKai  | `https://github.com/N7T0-OF/core` (fork) |

## Current baseline

- Main repo branch followed: `upstream/dev`
- Core submodule branch followed: `upstream/dev` (in the submodule)
- The two repositories share history (verified: `git merge-base dev upstream/dev` exists).
- Versioning is **independent**: SpaceKai version (e.g. `1.7.3`) is not the SimpMusic
  version. The app reports its own version via `gradle/libs.versions.toml`.

## Remote setup

```bash
git remote add upstream https://github.com/maxrave-dev/SimpMusic.git
cd core && git remote add upstream https://github.com/maxrave-dev/core.git && cd ..
```

## Synchronisation procedure

Never merge upstream blindly. Run the helper script — it fetches, reports and
**detects conflicts without resolving them automatically**:

```bash
./scripts/update-upstream.sh
```

What the script does:

1. checks `git status` is clean (refuses to run otherwise)
2. fetches `upstream` (main repo, depth = full history on the followed branch)
3. reports the new upstream version available
4. creates a sync branch `sync/upstream-<date>`
5. attempts `git merge upstream/dev` into it
6. reports conflicts **without** choosing ours/theirs
7. prints a summary report

### Manual steps for the maintainer

1. `git fetch upstream`
2. `git merge upstream/dev` (or rebase) on a sync branch
3. **Resolve conflicts by hand.** Rules:
   - Files under `core/` are handled in the submodule first (same procedure, in `core/`).
   - Files that are pure SpaceKai additions (`composeApp/.../ui/icon/*`, `SpaceKai*`,
     `docs/`, `scripts/`) are kept as-is — upstream cannot conflict with them.
   - Files that carry both upstream and SpaceKai changes are the real conflicts:
     decide per hunk. Mark SpaceKai edits with `// SPACEKAI CUSTOMIZATION` when feasible.
4. Run the checks: `./gradlew :composeApp:compileKotlinJvm` (fast local signal),
   then let CI run `android.yml` on the pushed branch.
5. When green, merge the sync branch into `dev`, fast-forward `main`, and cut a release.

> The installed app **never** updates straight to a SimpMusic release. Users always get a
> SpaceKai release built from this repository.

## Known conflict surfaces

Files where SpaceKai intentionally diverges from upstream:

| File / area                                  | Why                                    |
|----------------------------------------------|----------------------------------------|
| `gradle/libs.versions.toml`                  | SpaceKai version, update-checker repo  |
| `composeApp/.../ui/icon/*`                   | Material Symbols set (auto-generated)  |
| `androidApp/build.gradle.kts`                | single-APK release flag               |
| `.github/workflows/*`                        | SpaceKai release pipeline             |
| `core/.../DataStoreManager*`                 | SpaceKai OAuth keys + settings        |
| `core/service/spotify/*`                     | Spotify playlist sync (SpaceKai)      |
| `build_and_sign_apk.sh`                      | single-APK signing                    |
| `composeApp/.../App.kt`                      | nav styles, deep links, haptics       |
| `composeApp/.../SettingScreen.kt`            | SpaceKai settings sections            |

## Icons — LOCKED (never change)

`circle_app_icon.png` and `app_icon.png` are **locked SpaceKai assets**. Do not optimize,
recompress, resize, recolor, regenerate or replace them — in any release, ever. Verify
before/after any operation:

```bash
sha256sum composeApp/src/commonMain/composeResources/drawable/circle_app_icon.png \
           androidApp/src/main/res/drawable/app_icon.png
```

## Release procedure

1. `./gradlew :composeApp:compileKotlinJvm` (and `androidApp:assembleRelease` if an SDK is available)
2. Push `dev`, fast-forward `main` → triggers `.github/workflows/release.yml`
3. The workflow builds: **one** universal signed APK (`SpaceKai-vX.Y.Z.apk`) + `SHA256SUMS.txt`,
   plus desktop packages (AppImage / mac .zip→.dmg / Windows installer) when the Conveyor
   signing key is configured
4. Attach the artifacts to a GitHub Release `vX.Y.Z` — only after human validation

## License

GPL-3.0. Upstream license and copyright notices are preserved and must not be removed.
SpaceKai is not presented as the original project.
