# CI/CD Guide for Maintainers

Operational reference for the release pipeline. For the step-by-step release
flow (bump → changelog → tag → publish), see [`RELEASE.md`](../RELEASE.md).

## Workflows at a glance

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `android-release.yml` | tag `v*`, manual, `main` push | Builds the single `SpaceKai-vX.Y.Z.apk` + desktop packages + `SHA256SUMS.txt`; creates the GitHub release (draft / pre-release) |
| `playstore-publish.yml` | tag `v*` (stable only) | Builds the APK and uploads it to the Play **internal** track |
| `playstore-promote.yml` | manual | Promotes internal/beta/alpha → production |
| `validate-workflows.yml` | change to `.github/workflows/**` | Runs `actionlint` on all workflows |
| `release-nightly-check.yml` | nightly 03:00 UTC | Lightweight config check (actionlint + version/version-code/changelog + **icon SHA-256** + the 10 audits — wiring, UI overlap, upstream hotspots, Spotify flow, landscape player, updater, navigation, dynamic color, perf keys, provider architecture — + **gate parity** (the release/pre-tag/nightly gate lists must stay in sync) + **gate regression test** (`test-gates.sh`: every gate's expected outcome + critical SpaceKai patterns still hitting real code) + FEATURE AUDIT with claim cross-check, all **warn-only**) — the full build already runs on every tag and `main` push |
| `verify-release.yml` | release **published**, manual | Downloads the published assets and runs `scripts/verify-release.sh` — exactly one universal APK, no forbidden variants, every `SHA256SUMS.txt` checksum matches |
| `publish-draft.sh` (local, live repo) | after the draft exists | The final step: re-downloads the draft assets, runs `verify-release.sh` on them, checks the note carries the honesty block **and that it is current** (gap lines vs a freshly regenerated audit), then `gh release publish`. Exit 2 = already published / pre-release (nothing to do) |
| `publish-from-artifact.yml` | manual (run_id + tag) | Publishes an **already-built** artifact as a release: downloads it from the given workflow run, verifies exactly one `SpaceKai-v*.apk` + matching `SHA256SUMS.txt`, **runs the full pre-release report (all 12 code gates + APK — no bypass path)**, then creates the release (draft for stable tags, pre-release for `-*` tags) |
| `android.yml` | `dev` push / PR | FOSS release APK for the dev branch |
| `desktop-package.yml` | `dev` push / PR | Desktop packages for the dev branch |

## Secrets setup

All secrets live in **Settings → Secrets and variables → Actions**.

| Secret | Required by | How to obtain |
| --- | --- | --- |
| `BASE_64_SIGNING_KEY` | android-release | `base64 -w0 simpmusic.jks` (your keystore) |
| `KEY_STORE_PASSWORD` | android-release | Your keystore password |
| `KEY_PASSWORD` | android-release | Your key password |
| `ALIAS` | android-release | Your key alias |
| `SENTRY_DSN` / `SENTRY_AUTH_TOKEN` | android-release | Sentry project settings |
| `LASTFM_API_KEY` / `LASTFM_SECRET` | android-release | Last.fm API account |
| `CONVEYOR_SIGNING_KEY` | android-release | Conveyor signing key (desktop) |
| `PLAY_STORE_SERVICE_ACCOUNT` | playstore-* | Play Console → Setup → API access → service account JSON, **base64-encoded** |
| `DISCORD_WEBHOOK` | android-release (optional) | Discord channel → Integrations → Webhooks |

### Play Store service account (the fiddly one)

1. Play Console → **Setup → API access** → *Create new service account*.
2. Grant it **Upload releases** permission (and *View app information*).
3. Download the JSON credentials, encode to base64:
   ```bash
   base64 -w0 credentials.json
   ```
4. Paste the base64 string as the `PLAY_STORE_SERVICE_ACCOUNT` secret.

The app must be **created in the Play Console** (even if empty) before the first
upload, and the service account must be linked to it.

## Testing without publishing

- **Dry-run**: Actions → *SpaceKai Release Pipeline* → check `dry_run`. It builds
  everything, generates the notes and validates artifacts, but skips
  `gh release create`. Watch the job log for the notes it would have used.
- **Pre-release report**: `scripts/pre-release-report.sh` prints a PASS/FAIL
  checklist (APK, signature, desktop, upstream, **SpaceKai feature wiring**,
  **UI size-transform**, upstream hotspots, Spotify flow, landscape player,
  updater flow, navigation, dynamic color, icons, perf keys, **FEATURE AUDIT**)
  and exits non-zero on any critical failure. Run it locally before tagging
  (`./scripts/pre-release-report.sh`) or let the workflow gate the release.
  The automated gates (all run nightly too, warn-only — a red nightly tells
  you an upstream merge drifted something before the release does):
  - `scripts/audit-features.sh` (**ZERO FALSE POSITIVE**) — fails if any
    SpaceKai feature flag is a decorative toggle (wired to no behaviour), see
    `docs/FEATURE-AUDIT.md`.
  - `scripts/audit-settings-ui.sh` — fails if a size-transform animation
    appears in a `LazyColumn`/`LazyRow` lazy file outside the audited allowlist
    (the settings-overlap bug).
  - `scripts/audit-upstream-hotspots.sh` — fails if `conveyor.conf`
    `app.vcs-url` or the update checker drift back to SimpMusic.
  - `scripts/audit-spotify-flow.sh` — the real Spotify login is a `sp_dc`
    cookie flow, **not** OAuth PKCE (no `redirect_uri` exists — the reported
    "redirect_uri bug" is a phantom); fails if the cookie chain breaks or an
    inconsistent `redirect_uri` is ever introduced.
  - `scripts/audit-landscape-player.sh` — `NowPlayingScreen` has no orientation
    branch (portrait-first); fails if the artwork fix or the phone/tablet
    landscape branching is reverted.
  - `scripts/audit-updater-flow.sh` — detection chain (check + dialog → SpaceKai
    releases page) is real, internal download/install is missing; fails if the
    chain or the SPACEKAI CUSTOMIZATION is reverted.
  - `scripts/audit-navigation.sh` — liquid-glass + translucent styles,
    hide-text, landscape right rail exist; fails if that wiring is reverted.
  - `scripts/audit-dynamic-color.sh` — capability exists but dark backgrounds
    are pinned to black (confirmed bug); fails if the capability is removed.
  - `scripts/verify-perf-keys.sh` — fails on unstable `hashCode()` lazy keys.
  - `scripts/generate-feature-audit.sh` — regenerates `docs/FEATURE-AUDIT-REPORT.md`
    (evidence-based verdicts per SpaceKai feature); BROKEN blocks, the
    « Connu / non terminé » block must be pasted into the release notes, and
    the claim cross-check flags release notes claiming a NOT-finished feature.
- **`actionlint`**: pushed with `validate-workflows.yml`; it fails the PR if a
  workflow is malformed, so review its output before merging workflow changes.

## Known pitfalls (learned the hard way)

- **`paths-ignore` does not apply to tag pushes.** A `tags: v*` trigger runs on
  any tag regardless of changed files. This is why the release workflow triggers
  reliably on tags.
- **`version-code` must beat the highest code EVER published — not just the
  previous tag.** Android refuses downgrades with "Application non installée"
  (INSTALL_FAILED_VERSION_DOWNGRADE). Real incident (2026-08-25): the v1.1.6
  series shipped code 66, then v1.7.x restarted at 58→61, so anyone on
  v1.1.6-3 (66) could not install v1.7.5 (61). The floor is
  `MIN_VERSION_CODE=66` in `apply-release-pipeline.sh` / `release-publish.sh`,
  and `validate-tag` scans every tag's toml and takes the max versionCode.
- **`gh` needs full history for release notes.** The `create-github-release`
  job checks out with `fetch-depth: 0`; without it, `git tag`/`git log` only see
  the shallow clone and notes would be empty.
- **Shellcheck would flag the release scripts.** `validate-workflows.yml`
  disables shellcheck/pyflakes on purpose — the scripts legitimately use `ls` in
  command substitution. Keep that disabled or the lint job fails on noise.
- **The Play Store upload needs exactly one APK.** The ABI-split removal means
  `assembleRelease` produces a single universal APK; `playstore-publish.yml`
  verifies the count is 1 and fails otherwise.
- **Pre-release tags are skipped by the Play Store job.** `v1.7.0-beta.1` builds
  a GitHub pre-release but never touches the Play Store (guarded by
  `!contains(github.ref_name, '-')`).
- **No Conveyor signing key → desktop steps skip, release stays APK-only.**
  `secrets` cannot be read in a job-level `if` (documented GitHub limitation), so
  `build-desktop-packages` exposes `DESKTOP_SIGNING_KEY_SET` (job env → `outputs.built`)
  and every desktop step is gated on `env.DESKTOP_SIGNING_KEY_SET == 'true'`;
  `wrap-mac-dmg` and the release-job desktop downloads are gated on
  `needs.build-desktop-packages.outputs.built == 'true'`. The release job uses the
  `always() && !cancelled() && !contains(needs.*.result, 'failure')` pattern so
  a skipped desktop build never blocks the APK release, while a FAILED desktop
  build still blocks it (spec: never publish a partially broken release).
- **The brand icons are locked.** `scripts/verify-icons.sh` pins the SHA-256 of
  `circle_app_icon.png` / `app_icon.png`; `validate-tag` and the nightly check
  fail if any were modified, optimized or replaced. Never edit these files.
- **The public release ships one APK only.** The foss variant is built only for
  local F-Droid verification and never appears in the GitHub release assets.
- **`SHA256SUMS.txt` must contain plain basenames.** The pipeline generates it
  with `(cd dir && sha256sum basename)` so each line matches the file as
  downloaded from the release page — never workspace-relative paths. Git Bash's
  `sha256sum` also prefixes names with `*` (binary marker); `verify-release.sh`
  strips it.
- **Desktop assets are renamed at the staging step only.** The internal
  packaging identity stays `simpmusic` (launcher `bin/simpmusic`, `simpmusic.crt`,
  `simpmusic-*.x64.msix`, `install.bat` globs) so installs keep working; the
  release-page names become `SpaceKai-vX.Y.Z-linux-x86_64.AppImage`,
  `SpaceKai-vX.Y.Z-windows-installer.zip`, `SpaceKai-vX.Y.Z-mac-<arch>.dmg`.
- **`conveyor.conf` `app.vcs-url` must stay on the SpaceKai repo.** Conveyor
  derives all generated download URLs from it; an upstream merge silently
  restores `maxrave-dev/SimpMusic`. Check it after every sync.
- **`verify-release.sh` expects the renamed asset names** — a release whose
  assets still carry `SimpMusic-*` / `simpmusic-*` prefixes fails the
  post-publication check and is flagged immediately.
- **Never animate size inside a `LazyColumn` item — fade only.**
  `AnimatedVisibility(enter = expandVertically(), exit = shrinkVertically())`
  (or `expandHorizontally`/`shrinkHorizontally`) inside a `LazyColumn`
  `item {}` measures the content with unbounded height and can draw the full
  text over the following item while the layout is still animating — the
  "settings texts rendered on top of each other" bug (2026-08-25, fixed in
  `99c5f561` + `257a497`). The safe pattern is `fadeIn()/fadeOut()` on the
  `AnimatedVisibility` plus `animateContentSize()` for the smooth height
  change (layout-driven, siblings always pushed correctly) plus
  `clipToBounds()` on the container. A size animation is only safe in a
  regular `Column`/`Row`, never in a lazy item. Check every new
  `AnimatedVisibility` for which container it lives in.

## Bumping versions

`gradle/libs.versions.toml`:

```toml
version-name = "1.7.6"   # drives the tag (v1.7.6) and release title
version-code = "72"      # MUST beat the highest code ever published (71, v1.8.0)
```

Keep the fastlane changelog in sync:

```bash
./scripts/generate-fastlane-changelog.sh            # en-US
LOCALE=vi-VN ./scripts/generate-fastlane-changelog.sh
```

### Publish gate (`scripts/publish.sh`) and pre-tag check (`scripts/check-pre-tag.sh`)

The one-command entry point on the live repo is **`scripts/publish.sh`** — it
runs the pre-tag check, then the gate regression test, previews the release
bump, and only tags/pushes after confirmation. The manual equivalent:

```bash
./scripts/publish.sh /path/to/Sankamusic-dev          # verify → preview → confirm → publish
./scripts/publish.sh /path/to/Sankamusic-dev --yes    # no prompt

# manual:
./scripts/check-pre-tag.sh    # FAIL = do NOT tag
./scripts/test-gates.sh       # gate regression: outcomes + critical patterns
./scripts/release.sh /path/to/Sankamusic-dev --dry-run
./scripts/release.sh /path/to/Sankamusic-dev
```

It verifies what release.sh needs (git + origin, `gh` installed & authenticated),
then runs every critical gate individually (upstream hotspots, feature wiring,
settings UI, Spotify flow, landscape player, updater, nav, dynamic color,
provider architecture, icons, lazy keys, no BROKEN) — and, crucially, the
**changelog claim cross-check**: a release note that claims a NOT-finished
feature as done (the changelog-73 pattern: “one style selector
(Minimalist/Translucent/Liquid glass)” while the Minimalist toggle is
decorative) blocks the tag here, before CI ever builds. The APK itself is
NOT checked (the CI builds it after the tag); it is gated in
`pre-release-report.sh` inside the release workflows.

## UI audit checklist (pre-release)

Every release should pass a quick UI audit of the screens that were touched, plus
one sweep of the whole `composeApp` tree. The three axes below are what caught
real bugs in 2026-08-25 (v1.7.8, commits `99c5f561` + `257a497`):

### 1. Size-transform animations inside lazy items (the overlap bug)

Search for `expandVertically` / `shrinkVertically` (and the horizontal variants)
in `composeApp/src`:

```bash
grep -rn "expandVertically\|shrinkVertically\|expandHorizontally\|shrinkHorizontally" \
  --include="*.kt" composeApp/src | grep -v build
```

For each hit, check the container:

- **Inside a `LazyColumn`/`LazyRow` `item {}` → DANGEROUS.** The content is
  measured with unbounded height and draws over the following item while the
  layout animates. Replace with `fadeIn()`/`fadeOut()` on the `AnimatedVisibility`
  + `animateContentSize()` on the container + `clipToBounds()`. Reference
  patterns: the allowlisted sites in `scripts/audit-settings-ui.sh` (e.g.
  `FullWidthItems.kt:243-244/400-401`, `NowPlayingScreen.kt:1961-1962`).
- **Inside a regular `Column`/`Row` or a `TopAppBar` icon → safe.** The layout
  pushes siblings correctly there.

> This is now **automated** for lazy files: `scripts/audit-settings-ui.sh` scans
> every `.kt` containing a `LazyColumn`/`LazyRow` and fails if a size-transform
> animation appears at a line not on the hand-audited allowlist. It runs in
> `pre-release-report.sh` (blocking) and nightly (warn-only). When you add a
> size-transform outside a lazy item, add it to that script's `ALLOWLIST_SITES`
> only *after* inspecting the container.

### 2. `AnimatedVisibility` defaults

An `AnimatedVisibility` with no explicit `enter`/`exit` uses
`fadeIn + expandIn` / `fadeOut + shrinkOut` — scale+clip animations that do
**not** change the layout size, so they cannot overlap siblings. These are fine
even inside lazy items; only size-transform animations (`expandVertically`
et al.) are the problem.

### 3. `maxLines` caps

- Settings-style descriptions (title + subtitle rows) should **not** be capped
  (`maxLines = Int.MAX_VALUE`) so long text wraps instead of being cut off.
- Track/album titles in cards and grids keep their intentional cap
  (`maxLines = 2`) — a card title must not expand.

Also confirm: sections that should start collapsed default to
`initiallyExpanded = false`, and icons marked locked (`circle_app_icon.png`,
`app_icon.png`) are untouched.

## Debugging a failed release

1. Open the failed run in the Actions tab; each job's log shows `::error::` lines.
2. `validate-tag` failures: the message states which check failed (tag mismatch,
   version-code not increased).
3. `create-github-release` failures: usually a missing artifact — the step fails
   loudly with "Missing APK artifacts" rather than publishing an empty release.
4. Play Store upload failures: check the service account has **Upload releases**
   permission and the app exists in the console.
