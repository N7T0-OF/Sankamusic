# E2E device checklist — SpaceKai updater v0.3.7 (merge gate for Sankamusic#5 / core#1)

Ground truth: the code reviewed on tips `37bf53ae` (parent) / `062a348` (core),
plus the deterministic APK resolver described in P3 below. Scope: every claim
below is observable on one Android device; JVM tests cover selection/state logic
only, not the download, package-check, or install pipeline.

---

## 0. Prerequisites (all must hold before step 1)

| # | Prerequisite | How to check |
|---|---|---|
| P1 | Physical Android device (API 26+), USB debugging on | `adb devices` shows the device |
| P2 | Baseline **v0.3.6 (code 84) SpaceKai** installed, **signed with the SpaceKai release key** — the same key that will sign the 0.3.7 candidate. A signature mismatch makes PackageInstaller refuse the upgrade and **falsely fails T1** | `adb shell dumpsys package com.maxrave.simpmusic \| grep -E "versionName\|versionCode"`; `apksigner verify --print-certs <apk>` on baseline and candidate must print the same cert |
| P3 | A published GitHub release on `N7T0-OF/Sankamusic` tagged `v0.3.7` with `SHA256SUMS.txt` and exactly one **canonical universal release APK**. The current pipeline name is `SpaceKai-v0.3.7.apk`; the temporary fixture name `sankamusic-v0.3.7-universal-release.apk` is also an exact accepted name. Upload order is irrelevant: `resolveApkUrl` matches the release-tag-derived names, ignores ABI/debug variants, and returns no URL if more than one canonical candidate exists | `gh release view v0.3.7 --json assets` ; verify the accepted APK name and that no second canonical APK is present |
| P4 | `SHA256SUMS.txt` format: one line per asset, `<sha256-hex>  <filename>.apk` (hash first, filename last). The verifier matches **by hash only** — any line whose leading hex equals the downloaded APK's digest and ends in `.apk` | `sha256sum <accepted canonical APK>` matches the manifest line |
| P5 | For beta tests: a published **temporary prerelease fixture** tagged `v0.3.8-beta.1` with the same canonical asset shape; this fixture is not a production release | `gh release view v0.3.8-beta.1` |
| P6 | Wi-Fi on; GitHub reachable from the device (no VPN/proxy that MITMs TLS — SHA-256 is integrity within the GitHub trust domain, not authenticity) | open the releases page on-device |

### Build the candidate APK (exact task)

```bash
# The pinned compottie:2.2.2-compose-1.12-SNAPSHOT was pruned from the remote and
# CI currently fails on it. The -I script below is a VERIFICATION-ONLY workaround
# (it changes no build file); create it locally ONCE, then build:
mkdir -p .freebuff
cat > .freebuff/compottie-substitution.init.gradle.kts <<'EOF'
allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.github.alexzhirkevich" &&
                requested.version == "2.2.2-compose-1.12-SNAPSHOT") {
                useVersion("2.2.4-compose-1.12-SNAPSHOT")
                because("Pruned pinned snapshot; verification-only substitution.")
            }
        }
    }
}
EOF

# FOSS variant (what CI publishes as "Build release APK (FOSS)"):
./gradlew androidApp:assembleRelease -PisFullBuild=false -I .freebuff/compottie-substitution.init.gradle.kts
# Full variant (default; gradle.properties sets isFullBuild=true):
./gradlew androidApp:assembleRelease -I .freebuff/compottie-substitution.init.gradle.kts
# Output: androidApp/build/outputs/apk/release/androidApp-release.apk
```

- The `-I` init script is the **verification-only** workaround for the pruned
  `compottie:2.2.2-compose-1.12-SNAPSHOT` pin (CI fails on it today); it is untracked
  and changes no build file. Once the pin is fixed in `gradle/libs.versions.toml`,
  drop the `-I` flag and this paragraph.
- The local release APK is **unsigned** (no `signingConfig`): either sign it with the
  SpaceKai release key (`apksigner sign --ks ...`) or take the **CI-signed** artifact
  from a green `Build release APK` job. Never sideload an unsigned APK — T1 would fail
  at the OS signature check for the wrong reason.
- Do **not** build with `isFullBuild` flipped relative to the artifact you actually
  publish: the E2E must test the exact binary users will receive.
- Everything above uses only standard tooling (`adb`, `apksigner`, `gh`, `sha256sum`)
  plus the one init script defined here — no other file from the developer's machine
  is required.

### Evidence kit (capture on every test)

- `adb logcat -s SpaceKaiUpdater:*` (TAG `SpaceKaiUpdater`) — progress, SHA-256 verdict,
  package check, failure reasons.
- `adb screenrecord /sdcard/e2e-T<n>.mp4` or screenshots at each phase (progress row,
  system installer prompt, result row).
- `adb shell dumpsys package com.maxrave.simpmusic | grep -E "versionName|versionCode"` before/after.
- The UI itself: Settings → SpaceKai Updates section row phases.

---

### Fixture policy

`v0.3.8-beta.1` and the negative-test releases are **temporary, explicitly named
fixtures**, not production releases. Use a fresh immutable fixture tag for each
variant (for example `v0.3.8-e2e-no-checksum`, `v0.3.8-e2e-bad-checksum`,
`v0.3.8-e2e-tampered`, and `v0.3.8-e2e-debug-only`); never delete/reuse a
published tag or mutate one fixture between tests. Run T4–T8 with the **Beta**
channel when the fixture is a GitHub prerelease, and clean up or archive the
fixtures after evidence is collected. The GitHub checksum still proves
integrity only within the GitHub trust domain; the independent installation
barrier is the Android signing-key check plus the package/version checks.

---

## Test matrix

### T1 — Full positive upgrade 0.3.6 → 0.3.7 (Stable, default channel)

1. Baseline 0.3.6 installed (P2). Settings → SpaceKai Updates: channel **Stable**, auto-check **on**.
2. Fresh launch (or tap the update row) so `checkForSpaceKaiUpdate()` runs.
3. Dialog "Update available" appears — gated by `isVersionNewer("v0.3.7", "v0.3.6")`.
4. Tap download. Watch phases: `Téléchargement… x.x Mo · y Ko/s` → `Vérification SHA-256…` → `Installation…`.
5. System Package Installer prompt appears (via FileProvider authority `com.maxrave.simpmusic.FileProvider`, `<cache-path>` wired in `provider_paths.xml`). Accept.
6. App relaunches on 0.3.7.

**PASS if:** phases observed in order; installer shows the app name; after install `versionName=0.3.7`, `versionCode` = the 0.3.7 code; settings/Favorites survive (same `applicationId`, same key). **FAIL if:** any phase skipped, silent browser fallback, "Application non installée", or data loss.

### T2 — Upstream compatibility: 2.0 → 2.1 → future (detection/reporting only; no upstream install)

1. Latest upstream = `v2.0.0` → label `✓ À jour avec la dernière release officielle` (tested base).
2. Upstream publishes `v2.1.0` (declared, untested base) → label `⚠ Nouvelle release officielle (v2.1.0) — base supportée, validation appareil en cours`. This proves **detection and declarative compatibility reporting only**; it does not prove that a SpaceKai build based on 2.1.0 has been rebuilt or validated on-device.
3. Upstream publishes `v2.2.0` (undeclared) → label `⚠ Nouvelle release officielle détectée — SpaceKai pas encore compatible`; no upstream APK, download, or install action is offered.

**PASS if:** the three labels render exactly as above and no upstream APK is ever offered for download or installation. **This test is not evidence that an upstream SimpMusic 2.0 → 2.1 automatic installation works**: upstream remains info-only by design because its APK has a different signing identity. A real 2.1 device-validation claim requires a separately published SpaceKai build based on 2.1 and a new T1-equivalent upgrade test.

Simulate without waiting by using a test-only matrix entry or a controlled build, but re-build and re-sign before treating the result as device evidence.

### T3 — Stable user is NEVER offered a prerelease

Fixture: publish **only** the temporary `v0.3.8-beta.1` prerelease (no newer stable).

1. Channel **Stable**, installed 0.3.7. Check for updates.
2. `checkForGithubReleaseUpdate(includePrereleases=false)` hits `/releases/latest`, which **excludes prereleases** → candidate is 0.3.7 or 404.

**PASS if:** **no** update dialog or install/download action appears (the ordinary `Mettre à jour` check row may remain visible); logcat shows no resolved prerelease URL. **FAIL if** any beta tag/URL appears in UI or logcat.

### T4 — Beta user gets the NEWEST prerelease fixture

1. Channel **Beta** (Settings → SpaceKai Updates → Canal SpaceKai). Installed 0.3.7.
2. Published fixture state: stable `v0.3.7`, prerelease `v0.3.8-beta.1` (newest). Check for updates.
3. `selectSpaceKaiUpdate` filters candidates newer than installed, max by semantic version → `v0.3.8-beta.1` offered.

**PASS if:** dialog/row shows `v0.3.8-beta.1`; installation succeeds with the same pipeline as T1. This is a temporary prerelease fixture, not a production release.

**T4b — tie-break (stable wins at equal version):** with stable `v0.3.8` and prerelease `v0.3.8-beta.2` both published, Beta must offer **stable** `v0.3.8`. **T4c — staleness:** with installed `v0.3.8` and only `v0.3.7-beta.5` newer-than-nothing published, **no** dialog (prerelease older than installed is filtered out).

### T5 — Negative: temporary release WITHOUT SHA256SUMS.txt refuses

Fixture: in **Beta** channel, publish a fresh temporary prerelease such as `v0.3.8-e2e-no-checksum` with the canonical APK only and no checksums file.

**PASS if:** download completes, phase shows `Vérification SHA-256…` then FAILED with `Aucun checksum SHA-256 fourni par la release — installation refusée (APK non vérifiable).`; the temp file is deleted (logcat); **no** installer prompt. **FAIL if** the installer is ever reached (an unverifiable APK must never install).

### T6 — Negative: temporary release with CORRUPTED SHA256SUMS.txt refuses

Fixture: in **Beta** channel, publish a fresh temporary prerelease such as `v0.3.8-e2e-bad-checksum` with a wrong hash for the canonical APK.

**PASS if:** FAILED with `SHA-256 verification failed - the APK is corrupt or tampered.`; file deleted; no installer prompt. Corrupting the manifest *or* the APK (below) must behave identically.

### T7 — Negative: temporary release with TAMPERED APK refuses

Fixture: in **Beta** channel, publish a fresh temporary prerelease such as `v0.3.8-e2e-tampered` with a correct `SHA256SUMS.txt`, but flip one byte in the published APK (keep the original hash in the manifest).

**PASS if:** same as T6 — hash mismatch → refusal, file deleted, no installer prompt.

### T8 — Negative: no debug/unsigned fallback

Fixture: in **Beta** channel, publish a fresh temporary prerelease such as `v0.3.8-e2e-debug-only` carrying **only** `sankamusic-v0.3.8-debug.apk` (or any asset matching an excluded token).

**PASS if:** `resolveApkUrl` yields no URL, no `-debug` URL is downloaded, and no installer prompt appears. The global dialog may offer the SpaceKai releases page as the safe fallback; it must never hand the debug APK to the downloader or installer. `adb logcat` must contain no `-debug` URL.

### T9 — Optional (hardening): package-name mismatch refuses

Fixture: a temporary release whose APK is a **different** app (package name ≠ `com.maxrave.simpmusic`), correctly hashed.

**PASS if:** after SHA-256, FAILED with `Package name mismatch - the APK is not com.maxrave.simpmusic (got <pkg>).`; file deleted; no installer prompt. (Covered by `getPackageArchiveInfo`.)

### T10 — Negative: re-cut tag never phantom-triggers

Fixture: installed `v0.3.7`, publish tag `v0.3.7-1` (same triple, re-cut). **PASS if:** no dialog/row (`isVersionNewer` compares `0.3.7` == `0.3.7`). (Same guard that fixed the vanilla phantom dialog.)

---

## Cleanup between tests

- Between positive/negative runs: `adb shell pm clear com.maxrave.simpmusic` (resets DataStore — channel/auto-check prefs) then reinstall the right baseline.
- T3–T8 use immutable temporary fixtures. Run them in an isolated release set or
  archive each fixture after evidence is captured; never mutate/reuse a published
  release tag. T3 uses the Stable channel to prove prereleases stay hidden; T4–T8
  use the Beta channel to consume prerelease fixtures.
- After each refusal test, confirm the cache holds no residual APK: `adb shell run-as com.maxrave.simpmusic ls cache/` (works on debug builds; on release builds rely on logcat deletion lines + `pm clear`).

## Final gate

All of T1–T8 **must be executed and PASS** (T9/T10 are recommended, not
blocking), with the temporary fixtures explicitly identified in the evidence kit.
The gate requires the fixture tests, but a fixture is not a production release and
may be archived after the run. T2 contributes only upstream **detection/reporting**
evidence, not proof of automatic installation of an official SimpMusic APK.
Evidence attached → the merge gate on Sankamusic#5 / core#1 is lifted. Any
deviation from the expected strings/phases above is a bug report against the updater, not a pass.
