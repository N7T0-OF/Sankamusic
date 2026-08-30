# Release Process

This document describes how to ship a new **SpaceKai** release (Android +
Desktop). SpaceKai is an add-on layer over SimpMusic — see
[`docs/UPSTREAM.md`](docs/UPSTREAM.md) for how it tracks SimpMusic updates.
The pipeline is fully automated once a version tag is pushed — most of this
guide is about the two steps that must happen manually: bumping the version
and writing the changelog.

For the operational side (secrets setup, testing, debugging, known pitfalls),
see [`docs/ci-cd.md`](docs/ci-cd.md).

## Overview

```
1. Bump version            (gradle/libs.versions.toml)
2. Write changelog         (scripts/generate-fastlane-changelog.sh)
3. Tag + push              (git tag vX.Y.Z && git push --tags)
        │
        ▼
   validate-tag  ──  checks the tag matches version-name
        │
        ├── android-release.yml ── builds the single APK + desktop packages
        │        └── create-github-release ── draft (stable) or pre-release (beta/rc)
        │               + SHA256SUMS.txt + icon verification
        │
        ├── playstore-publish.yml ── uploads the single APK to Play internal track
        │
        └── (optional) publish the draft release on GitHub, promote on Play Console
```

## 0. Applying the release pipeline to the live repo

The release pipeline lives in a development workspace; the live repository
(`N7T0-OF/Sankamusic`) may still run an older workflow (e.g. the 6-APK split
build). `scripts/apply-release-pipeline.sh` brings the new pipeline over
**without touching feature sources** (`composeApp/src`, `core/`, `desktopApp/src`,
`androidApp/src` are protected):

```bash
./scripts/apply-release-pipeline.sh /path/to/workspace --dry-run   # preview
./scripts/apply-release-pipeline.sh /path/to/workspace --yes       # apply
./scripts/apply-release-pipeline.sh /path/to/workspace --yes --version-name=1.7.4
```

Key rule: **version-code is computed as max(live repo's current code, 66) + 1**
(never copied from the workspace — the live history may have moved it, e.g.
v1.7.3 shipped with code 59). 66 is the highest versionCode ever published
(the v1.1.6 series), and Android **refuses downgrades**: anyone on v1.1.6-3
(code 66) got "Application non installée" (INSTALL_FAILED_VERSION_DOWNGRADE)
installing v1.7.5 (code 61). The next release must therefore be **code 67+**.
See `apply-release-pipeline.sh` and `release-publish.sh` for the enforced
`MIN_VERSION_CODE=66` floor. The changelog is copied under the new code's
filename. The manual step it prints (removing the `splits { abi {} }` block
from `androidApp/build.gradle.kts`) is required for the single-APK build.

**The live repo's own `release.yml` is disabled** (renamed to
`release.yml.disabled`) so it cannot double-trigger with the new
`android-release.yml` on the same tag. Review the backup before deleting it.
The desktop gating uses the env + `outputs.built` pattern (`secrets` are not
readable in job-level `if` conditions — a documented GitHub limitation).

Note: the Sankamusic **default branch is `dev`**, not `main`. `release-publish.sh`
pushes the **current branch** (detected automatically), so it works from `dev`;
the tag push is what triggers the release workflow (`tags: v*` fires regardless
of branch).

## 0b. One-command release

Once the scripts are present in the live repo, the whole flow is a single
command (apply pipeline → remove ABI splits → verify icons → commit → tag →
push):

```bash
# First time only — copy the scripts from the workspace into the live repo:
cp /path/to/Sankamusic-dev/scripts/*.sh scripts/

# Then (one command — verify, preview, confirm, publish):
./scripts/publish.sh /path/to/Sankamusic-dev             # asks for confirmation

# Equivalent manual flow (what publish.sh does step by step):
./scripts/check-pre-tag.sh                               # gate: git + gh + all
                                                         # critical gates + changelog
                                                         # claim cross-check + header.
                                                         # FAIL = do NOT tag (e.g.
                                                         # decorative toggles, or a
                                                         # changelog claiming an
                                                         # unfinished feature)
./scripts/test-gates.sh                                  # gate regression: expected
                                                         # outcomes + critical patterns
./scripts/release.sh /path/to/Sankamusic-dev --dry-run   # preview
./scripts/release.sh /path/to/Sankamusic-dev             # publish
```

`publish.sh` stops at the first failure, previews the bump, and only tags/pushes
after confirmation (`--yes` skips the prompt). The script never touches feature
sources. See `scripts/release.sh` for the step-by-step.

### Making the release public (after CI built the draft)

Once the release pipeline run has finished, CI opens a **draft** release for a
stable tag (pre-release tags are published immediately). Verify and publish it
with one command — it re-downloads the assets and runs `verify-release.sh` on
them, checks the note still carries the honesty block **and that the block is
current** (gap lines compared against a freshly regenerated audit — a stale
note is refused), then publishes:

```bash
./scripts/publish-draft.sh                 # asks for confirmation
./scripts/publish-draft.sh --yes           # no prompt
./scripts/publish-draft.sh --dry-run       # show what would happen
```

Exit codes: 0 = published, 1 = blocked (fix and re-run), 2 = nothing to do
(already published, or a pre-release tag CI already published). After
publication, `verify-release.yml` re-checks the assets.

### Publishing an already-built artifact (no rebuild)

If the release APK was already built (e.g. a `main` push run produced the
`android-release` artifact but no release), the `publish-from-artifact.yml`
workflow publishes it without rebuilding: run it with the build **run ID**
(from the Actions page URL) and the **tag**. It downloads the artifact,
verifies exactly one `SpaceKai-v*.apk` + matching `SHA256SUMS.txt`, and
creates the release (draft for stable tags).

## 1. Bump the version

Edit `gradle/libs.versions.toml`:

```toml
version-name = "1.7.6"   # human-readable version, used for tags and release titles
version-code = "72"      # MUST beat the highest code ever published (71, v1.8.0)
```

The release tag must be `v<version-name>` (`v1.7.6`). The CI job `validate-tag`
fails the whole pipeline if:
- the pushed tag does not match `version-name` (pre-release suffixes like
  `v1.7.6-beta.1` are accepted), or
- `version-code` is not **strictly greater than the highest versionCode across
  ALL tags** (not just the previous one — the CI scans every tag's
  `libs.versions.toml` and takes the max). Android refuses downgrades with
  "Application non installée", so beating only the previous release is not
  enough (real incident: v1.7.5 shipped code 61 while v1.1.6-3 had 66).

## 2. Write the changelog

The Play Store listing and the GitHub release both read the fastlane changelog,
keyed by version code:

```
fastlane/metadata/android/en-US/changelogs/<VERSION_CODE>.txt
fastlane/metadata/android/vi-VN/changelogs/<VERSION_CODE>.txt   # per locale, when present
```

Generate it from git history since the previous tag:

```bash
./scripts/generate-fastlane-changelog.sh            # en-US by default
LOCALE=vi-VN ./scripts/generate-fastlane-changelog.sh
```

Edit the generated file to keep only user-facing changes. If a changelog file is
missing when a tag is pushed, the release workflow generates one automatically
from the commits, grouped by type (`### Added` / `### Fixed` / `### Other`,
merge commits and dependency-bot commits excluded) and persisted into
`fastlane/`.

## 3. Tag and push

```bash
git tag v1.7.5
git push origin v1.7.5
```

Pre-release flow (for testers):

```bash
git tag v1.7.5-beta.1
git push origin v1.7.5-beta.1
```

## 4. What CI does automatically

### GitHub release (`.github/workflows/android-release.yml`)

Triggered by any `v*` tag. Builds:
- `androidApp:assembleRelease` → **one universal APK** named
  `SpaceKai-vX.Y.Z.apk` (full flavor, signed, all ABIs)
- Desktop packages: Linux AppImage, macOS DMGs, Windows installer
- `SHA256SUMS.txt` over every published asset

Then `create-github-release`:
- **Pre-release report**: `scripts/pre-release-report.sh` runs a PASS/FAIL
  checklist (single APK, release-variant name, signature, desktop packages,
  upstream version, **SpaceKai feature wiring — ZERO FALSE POSITIVE**, locked
  icons) and **blocks the release** on any critical failure. The ZERO-FALSE-
  POSITIVE gate (`scripts/audit-features.sh`) fails the release if any SpaceKai
  feature flag is a *decorative toggle* (shown in Settings but wired to nothing);
  a flag with no real call site is not "implemented". See `docs/FEATURE-AUDIT.md`.
  Two sibling gates harden the same release: `scripts/audit-settings-ui.sh`
  (fails if a size-transform animation reappears in a lazy item — the settings-
  overlap bug) and `scripts/audit-upstream-hotspots.sh` (fails if `conveyor.conf`
  `app.vcs-url` or the update checker drift back to `maxrave-dev/SimpMusic` on an
  upstream merge). All three also warn nightly (`release-nightly-check.yml`) so a
  regression is caught the next morning, long before the release gate.
  `scripts/audit-spotify-flow.sh` protects the real Spotify login chain
  (sp_dc cookie — there is **no** OAuth PKCE or redirect_uri in this codebase;
  the reported "redirect_uri bug" is a phantom, and playlist import was never
  implemented:  the `spotifySync` toggle is decorative). It fails if any link of
  the cookie chain breaks or if an inconsistent `redirect_uri` is ever
  introduced. See `docs/FEATURE-AUDIT.md` § Spotify.
  `scripts/audit-landscape-player.sh` guards the landscape player: the real
  finding is that `NowPlayingScreen` has **no** orientation branch (portrait
  layout stretched in landscape; on phones landscape is a full-screen overlay
  of the same portrait layout — the "different screen" symptom), and the
  `landscapePlayer` flag is decorative. The gate fails if the one existing
  artwork fix or the phone/tablet landscape branching is reverted by an
  upstream merge. See `docs/FEATURE-AUDIT.md` § Player portrait/paysage.
  `scripts/audit-updater-flow.sh` guards the update flow: the **detection**
  half is real (Settings → `checkForUpdate()` → GitHub checker → dialog whose
  Download button opens the SpaceKai releases page — a SPACEKAI
  CUSTOMIZATION), but the **internal download/install is not implemented**
  (the button is a browser redirect; no SHA-256, no install intent). The gate
  fails if any link of the detection chain or the SpaceKai releases URL is
  reverted by an upstream merge. See `docs/FEATURE-AUDIT.md` § Mise à jour.
  `scripts/audit-navigation.sh` guards the nav bar: liquid-glass and
  translucent styles, hide-text (`showLabels = !hideNavLabel`, Material3
  compacts to icons), and the phone-landscape right rail all exist and are
  wired; the SpaceKai customization (minimalist style, section config,
  reorder, shortcuts)  is not implemented. The gate fails if that wiring is
  reverted by an upstream merge. See `docs/FEATURE-AUDIT.md` § Barre de
  navigation.
  `scripts/audit-dynamic-color.sh` guards Dynamic Color: the capability is
  real (system palette on Android 12+, wallpaper/custom seed via materialkolor),
  but dark backgrounds are **pinned to pure black** even with a dynamic scheme
  (`dynamicDarkColorScheme(...).copy(background = Color.Black, ...)` and
  `isAmoled = isDark`) — the confirmed "fond noir fixe" to fix. The gate fails
  if the capability (palette / scheme / selector) is removed by an upstream
  merge. See `docs/FEATURE-AUDIT.md` § Dynamic Color.
  `scripts/audit-provider-arch.sh` guards the Universal Music Platform
  direction (`docs/PROVIDER-ARCHITECTURE.md`): while the `MusicProvider`
  abstraction does not exist, any provider-specific integration code blocks
  the release (no "three broken auth systems"); once it exists, providers
  must be capability-gated and secret-free in the APK. The FEATURE AUDIT
  tracks Apple Music / Deezer / unified search / local music as
  NOT IMPLEMENTED until real code ships.
- **Feature audit**: `scripts/generate-feature-audit.sh` regenerates
  `docs/FEATURE-AUDIT-REPORT.md` at every release — an evidence-based verdict
  (IMPLEMENTED / PARTIALLY IMPLEMENTED / NOT IMPLEMENTED / BROKEN) per SpaceKai
  feature, never based on file existence alone. BROKEN blocks the release; the
  report's « ### Connu / non terminé » block MUST be pasted into the release
  notes and a feature that is not finished must never be listed as done.
  It is appended automatically to every generated release note by
  `scripts/append-feature-audit-gaps.sh` (CI + local generator), along with
  the Installation / SHA-256 sections.
  See `docs/FEATURE-AUDIT.md` § GATE, volet 4.
- **Stable tag** (`v1.7.5`) → creates a **draft** release with all artifacts
  attached and the fastlane changelog as notes. Review and click *Publish*.
- **Pre-release tag** (`v1.7.5-beta.1`) → publishes immediately as a
  **pre-release** so testers can download it.

### ⚠️ GitHub immutable releases — tag names are PERMANENTLY burned

GitHub tombstones a tag name **forever** once a *published* release that used
it is deleted. The workflow guards against this (see the guard inside
`create-github-release` and `publish-from-artifact.yml`), and so must you:

- **Always create the release as a DRAFT first, with every asset attached in
  the SAME `gh release create` call** (`--draft` + the files as arguments).
  Never create it published and then try to add assets — the API answers
  `422 Cannot upload assets to an immutable release`, and deleting the
  published release to retry **consumes the tag name permanently**.
- **NEVER delete a published release on this repo.** A deleted published
  release burns its tag: even after deleting the tag ref, recreating it fails
  with `Cannot create ref due to creations being restricted` / `tag_name was
  used by an immutable release` — and the failure is identical via the REST
  API and `git push`.
- If a release is half-created (e.g. an upload failed), **leave the draft in
  place** for inspection. Deleting it to re-create is only safe while it is
  still a *draft* — once published, deleting is irreversible.
- **Real incident (2026-08-25)**: `v1.7.4` was created published (not draft),
  deleted to retry with assets, and is now permanently burned on this repo.
  The release ships under **`v1.7.4-1`** instead (`SpaceKai-v1.7.4.apk`
  unchanged inside). Every user on 1.7.3 still gets the update dialog (the app
  compares `tagName != installed version`, so any new tag triggers it).

Recovery recipe if a tag is ever burned again:

```
# Bump to a re-cut tag — the version inside the APK stays the same.
# v1.7.5 burned  →  v1.7.5-1
# The app's update dialog still shows for users on the previous version.
```

Public release assets are exactly:
`SpaceKai-vX.Y.Z.apk` + desktop packages + `SHA256SUMS.txt` — no foss, debug,
unsigned, aligned or per-ABI variants. The desktop assets are renamed to
SpaceKai release names at the packaging stage (internal packaging identity
stays `simpmusic` — launcher, cert, `install.bat` — so upgrades and installs
keep working):

```
SpaceKai-vX.Y.Z-linux-x86_64.AppImage
SpaceKai-vX.Y.Z-windows-installer.zip   (install.bat + .crt + .msix)
SpaceKai-vX.Y.Z-mac-arm64.dmg / -mac-x64.dmg
```

### Desktop release URLs (conveyor.conf)

Conveyor derives its generated download links (`download.html`, `.appinstaller`,
`.exe` wrapper) from `app.vcs-url` in `conveyor.conf`. It must point at the
**SpaceKai** repo (`https://github.com/N7T0-OF/Sankamusic`) — not upstream
`maxrave-dev/SimpMusic`, or every generated link resolves to the wrong repo's
releases. This is a silent hot-spot on upstream merges; verify it after every
`./scripts/update-upstream.sh`.

### Icon verification

The hand-crafted brand icons (`circle_app_icon.png`, `app_icon.png`) are
**locked assets**. `validate-tag` runs `scripts/verify-icons.sh` and fails the
release if any of them changed (modified, optimized, resized or replaced).

### Play Store (`.github/workflows/playstore-publish.yml`)

Triggered by **stable** tags only (pre-releases are skipped). Builds the APK,
verifies exactly one APK exists, and uploads it to the Play **internal** track
with the per-locale changelogs. Nothing becomes public until you promote the
release.

### Discord notification

If the `DISCORD_WEBHOOK` secret is set, the release job posts an embed when
the release is created — blue for a stable draft ("ready to publish"), orange
for a pre-release — with the version, a link to the release page, and a compact
list of the attached artifacts. Non-blocking: a missing webhook only logs a
warning.

### Post-publication verification (`.github/workflows/verify-release.yml`)

Every time a release is **published** (or on manual run with a tag), the
workflow downloads the release assets and runs `scripts/verify-release.sh`:
- exactly **one** APK, named `SpaceKai-vX.Y.Z.apk` (no debug/unsigned/aligned/
  ABI-split/foss variants),
- `SHA256SUMS.txt` present and **every listed checksum matches** the downloaded
  file (plain basenames, so the file matches what users actually download),
- **versionCode downgrade guard**: the APK's manifest versionCode must be
  greater than the highest code ever published (`MIN_VERSION_CODE=66`, the
  v1.1.6 series) — otherwise Android refuses the install with "Application
  non installée". Same floor as `apply-release-pipeline.sh` /
  `release-publish.sh`; bump the constant together when a release ships a
  higher code,
- desktop packages listed as a warn-only check (an APK-only release is allowed).

Any FAIL turns the check red — a corrupted or misnamed asset is caught right
after publication instead of by the first user who reports it.

### Dry-run (test the pipeline without publishing)

From the Actions tab, run **SpaceKai Release Pipeline** manually with the
`dry_run` checkbox enabled. It builds all artifacts, generates the release
notes and runs every artifact check — but **skips `gh release create`** and
prints the notes it would have used. A safe way to validate the whole pipeline
before pushing a real tag.

### Workflow validation (`.github/workflows/validate-workflows.yml`)

Runs `actionlint` on every change to `.github/workflows/` so a broken workflow
never reaches the release pipeline.

## 5. Manual steps after CI

1. **GitHub**: open the draft release, sanity-check the notes, click *Publish*.

> ⚠️ **UI lesson (2026-08-25, commits `99c5f561` + `257a497`)** — never animate
> size (`expandVertically`/`shrinkVertically`) inside a `LazyColumn` item: the
> content is measured with unbounded height and draws over the following item
> while the layout animates (the "settings texts on top of each other" bug).
> Use `fadeIn()/fadeOut()` + `animateContentSize()` + `clipToBounds()`.
2. **Play Store**: once the internal build has been tested, promote it to
   production. Either use the Play Console UI, or run the manual workflow
   **Promote Play Store to Production** (Actions tab) — it promotes the latest
   `internal` (or `beta`/`alpha`) build to production via fastlane.

   Promotion is deliberately **not** automatic: shipping to users should always
   be a conscious decision.

## Required repository secrets

| Secret | Used by | Purpose |
| --- | --- | --- |
| `BASE_64_SIGNING_KEY` | android-release | Keystore for signing the APKs |
| `KEY_STORE_PASSWORD` / `KEY_PASSWORD` / `ALIAS` | android-release | Keystore credentials |
| `SENTRY_DSN` / `SENTRY_AUTH_TOKEN` | android-release | Sentry crash reporting (full build) |
| `LASTFM_API_KEY` / `LASTFM_SECRET` | android-release | Last.fm scrobbling (full build) |
| `CONVEYOR_SIGNING_KEY` | android-release | Desktop package signing |
| `PLAY_STORE_SERVICE_ACCOUNT` | playstore-publish | Google Play service-account JSON (base64) |
| `DISCORD_WEBHOOK` | android-release (optional) | Discord notification when a release is ready |

## Local build (without CI)

```bash
# Build the single release APK
./gradlew androidApp:assembleRelease

# Build + sign the release APK (requires KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
./build_and_sign_apk.sh --release --full
```

The output is a single universal `SpaceKai-vX.Y.Z.apk` plus `SHA256SUMS.txt`
in `androidApp/build/outputs/apk/release/`. A `--foss` build is only for local
F-Droid verification and never ships in the public release.

## FAQ — « Application non installée » (Android)

Android shows this generic message when an install is refused. The most common
causes for SpaceKai, in order of likelihood, and how to fix each:

**1. Downgrade (versionCode lower than the installed app's) — the cause that
hit v1.7.5.**

Android refuses any APK whose versionCode is not strictly greater than the
installed one, and shows exactly « Application non installée ». Real incident
(2026-08-25): the v1.1.6 series shipped versionCode 66, then v1.7.x restarted
at 58 → 61, so everyone on v1.1.6-3 (66) got this message installing v1.7.5
(61).

- **Fix**: install the latest release — **v1.7.6 ships versionCode 67**, which
  beats every code ever published, so it installs as an update from any
  previous version.
- **Check**: the app's version in Settings → Apps → SpaceKai; if it shows a
  version number whose release came after the APK you are installing, wait for
  the next release.

**2. Signature mismatch (upgrading from the official SimpMusic app).**

The official SimpMusic (`maxrave-dev`) is signed with its own key. SpaceKai
keeps the same `applicationId` (`com.maxrave.simpmusic`) but is signed with a
different key, so Android treats it as a conflicting update and refuses it.

- **Fix**: uninstall the official SimpMusic first, then install the SpaceKai
  APK. Local app data is lost on uninstall.
- **Verify a signature**: `keytool -printcert -jarfile SpaceKai-vX.Y.Z.apk` —
  the SpaceKai APKs all show `CN=LiquidFlow`,
  fingerprint `9D:8E:4A:94:1B:AD:CE:83:66:0C:CF:F3:13:A5:4E:DC:C5:F2:13:6B`.

**3. Corrupted / incomplete download.**

A truncated or corrupted APK fails to install. Verify the checksum before
installing:

```bash
sha256sum SpaceKai-vX.Y.Z.apk
# compare with the line in SHA256SUMS.txt from the release
```

**4. « Install unknown apps » permission / Play Protect.**

Make sure the browser you download from has the « Install unknown apps »
permission, and check that Play Protect is not blocking the install
(Play Store → Play Protect → scan result). The APK is signed and safe.

**5. Older device (below minSdk 26).**

SpaceKai requires Android 8.0+ (minSdk 26).

If none of the above applies, the pipeline's post-publication verification
(`verify-release.sh`) already checks the versionCode against the highest ever
published and the checksums — a release that would fail to install as an
update is flagged before you ever download it.
