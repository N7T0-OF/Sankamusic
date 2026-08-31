# UPSTREAM 2.0.0 — AUDIT AND FEATURE MATRIX

_Method: plain `grep` on the real working tree (`composeApp/src` + `core/` submodule,
build dirs excluded). All counts are **files matching**. Last run: 2026-08-31._

> **Correction record (important).** An earlier scan reported “all 2.0.0 features
> MISSING (0 matches)”. That was a **tooling artifact**: `rg` is not installed in this
> environment, so every `rg` call returned 0. Re-run with `grep`, every feature in the
> SimpMusic 2.0.0 release notes below is **present in the tree**. Nothing here claims
> runtime behaviour — this is **static presence + wiring evidence** only. Device runs
> are NOT part of this audit (no device in this environment).

---

## 1. Corrected verdict (static, on this tree)

The tree **is** SimpMusic 2.0.0 territory: the 3 Now Playing styles, Listen Together,
ten-band EQ (wired into the real audio engine), Wrapped, Analytics tab, romanisation,
share-lyrics-as-image, cipher decode, AAC/Opus paths, auto-download, album-crossfade,
sleep-timer fade and the desktop items are all present as Kotlin source.

## 2. Feature matrix (against the v2.0.0 changelog)

| Feature (changelog) | Present? | Static wiring evidence |
|---|---|---|
| **Listen Together** (rooms, Metrolist-compatible) | ✅ 39 files | `ListenTogetherScreen.kt`, `ListenTogetherIconButton.kt`, `ListenTogetherDestination.kt` |
| **3 Now Playing styles** (Classic / M3 Expressive / Apple Music) | ✅ 11 files | `NowPlayingContentSpotify.kt`, `NowPlayingContentM3Expressive.kt`, `NowPlayingContentAppleMusic.kt`, selector in `SettingScreen.kt:708-714` (`now_playing_style_*`) |
| **Apple Music lyrics** (word-by-word glow) | ✅ 4 files | `AppleMusicShared.kt`, `applemusic/` player dir |
| **Lyrics romanisation** (12 langs) | ✅ 26 files | `romanization`/`romanisation` across composeApp+core |
| **Share lyrics as image** | ✅ 11 files | `ShareLyrics*`, `LyricsImage*` |
| **SimpMusic Wrapped** (year review + monthly recap) | ✅ 48 files | `WrappedScreen.kt`, `WrappedViewModel.kt`, `LibraryWrappedTab.kt` |
| **Ten-band equalizer** (drag curve, presets, AutoEq) | ✅ 3 files (core) + UI | `EqualizerSection.kt`, `EqualizerPresets.kt`, `AutoEqPicker.kt`, **`core/media/media3/.../audio/EqualizerAudioProcessor.kt` (applied to real buffers)** + `MpvPlayer.setEqualizer` |
| **Analytics tab** (weeks/months/years, clock, fingerprint) | ✅ 11 files | `AnalyticsScreen.kt`, `AnalyticsViewModel`, listening clock/fingerprint substrings |
| **New widgets** (turntable, playlists, insights) | ❌ **NOT buildable in commonMain** | only `aboutlibraries.json` matched; **no `AppWidgetProvider`/`GlanceAppWidget`** (matches FEATURE-AUDIT → widgets NOT IMPLEMENTED) |
| **Liquid glass UI** | ✅ 24 files | `LiquidGlassAppBottomNavigationBar.{common,android}`, `LiquidGlassTabBar.android`, `LiquidGlassContainer.kt` |
| **Ciphers decoded on-device** / source in song info | ✅ 10 files | `nsig`/`decipher` matches in composeApp+core |
| **Opus / AAC quality** | ✅ 10 files | `opus`/`aac`/`audioFormat` in scraper+player |
| **Auto-download on like** | ✅ 5 files | `autoDownload`/`AutoDownload` |
| **Radio audio-only optional** | ✅ 5 files | `audioOnlyQueue` / `keepRadioAudio` |
| **Sleep timer fades out** | ✅ 9 files | `sleepFade`/`SleepFade` (Media3 processor + mpv 3rd level) |
| **Crossfade keeps albums continuous** | ✅ 8 files | `albumTrackIds`, `PlaylistType.ALBUM` |
| **Search inside playlists + multi-select** | ✅ 10 files | `SongSelectionState.kt`, `SongSelectionTopAppBar.kt` (`selectionMode`/`selectedSongIds`) |
| **Paste YouTube link in search** | ✅ 2 files | `isYouTubeUrl`/`youtu.be` |
| **Follow artist → subscribe YT channel** | ✅ 3 files | `subscribeChannel` |
| **Clear listening history** | ✅ 4 files | `clearHistory`/`deletePlaybackHistory` |
| **Mix for You as own tab** | ✅ 10 files | `MixForYou` |
| **Desktop: floating capsule player** | ➖ partial | `MiniPlayer.kt`, `ToggleMiniPlayer.{common,android}` (mini-player exists; “capsule player” naming not found) |
| **Desktop: landscape headers** | ✅ (see §3) | `isPortrait`-gated headers on Album/Playlist/Artist (2026-08-17 work) |
| **Desktop: pitch control** | ✅ 11 files | `setPitchScale`, pitch slider |
| **Desktop: native download notifications** | ✅ 2 files | `downloadNotification` |

### Features with 0 raw matches but present under different spellings (re-checked)

- **multi-select songs** → `SongSelectionState.kt` / `SongSelectionTopAppBar.kt` (component, not `MultiSelect` keyword).
- **Now Playing style selector** → `NowPlayingStyle`/`now_playing_style` (upper/lower mismatch in the first probe).

---

## 3. Genuine gaps / NOT IMPLEMENTED (from FEATURE-AUDIT-REPORT.md v0.3.2)

These are the **SpaceKai-layer** features the repo's own audit flags as unfinished —
none were claimed done. They are separate from the upstream 2.0.0 base, which is present:

- `widgets` — NOT IMPLEMENTED (no AppWidgetProvider anywhere).
- `provider-arch` / `apple-music` / `deezer` / `local-music` / `unified-search` — design-only (MusicProvider abstraction).
- `android-auto` browse tree — NON VÉRIFIABLE (core submodule).
- SpaceKai-layer partials: `nav-custom`, `nav-minimal`, dynamic-color dark background pinned black, `landscape-player`, `haptics` (play-only), `crossfade-slider`.

---

## 4. What “based on SimpMusic 2.0.0” means here (honest)

- **Engine + UI = upstream 2.0.0 tree, present.** `git log` shows milestone `b749cca5`
  “finish 2.0.0 migration” on the base; `core` submodule pinned to branch
  `spacekai-upstream-2.0.0`.
- **A release may not claim a 2.0.0 feature “works”** merely because its files exist —
  that is this repo's own ZERO-FALSE-POSITIVE rule. Static presence ≠ runtime proof.
- The two things that need a **device/emulator** before any “it works” claim:
  1. actual audio pipeline (EQ audible, Opus/AAC switch), 2. full in-app flows
  (Listen Together sync, Wrapped from real listening data, widget launchpad on the launcher).

## 5. Related docs

- `docs/FEATURE-AUDIT-REPORT.md` — auto-generated, per-feature SpaceKai verdicts + honesty block.
- `docs/UPSTREAM.md` — upstream sync procedure (`scripts/update-upstream.sh`) + conflict hotspots.
- `docs/SPACEKAI-ARCHITECTURE.md` — feature-flag layer model.