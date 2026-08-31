# UPSTREAM FEATURE MATRIX — SimpMusic 2.0.0 → SpaceKai

_Scope of evidence: plain `grep` on the real working tree (`composeApp/src` + `core/`
submodule, build dirs excluded) as of 2026-08-31 — see `docs/UPSTREAM-2.0.0-AUDIT.md`.
`rg` is NOT available in this environment and was never used for these verdicts._

**Status vocabulary (enforced):** `IMPLEMENTED / PARTIAL / BROKEN / MISSING /
INCOMPATIBLE / NOT_APPLICABLE`.

**Honesty rule (ZERO-FALSE-POSITIVE):** `IMPLEMENTED` here means **static wiring
evidence only** — the UI, logic and integration calls exist in source. It does **NOT**
mean runtime-verified on a device. Every `IMPLEMENTED` row that touches the player,
network or audio pipeline is additionally marked **device-UNVERIFIED**. The only proof
stronger than this repo's own static rules is an actual device/emulator + CI Android
build, neither of which is claimed below.

---

| # | Feature (spec §3–25) | Status | Static evidence (grep) | Device |
|---|---|---|---|---|
| 1 | Listen Together (rooms, Metrolist-compatible) | IMPLEMENTED | `ListenTogetherScreen.kt`, `ListenTogetherIconButton.kt`, `ListenTogetherDestination.kt`, `ListenTogetherSettingsDestination.kt`, VM + graph wiring (39 files) | UNVERIFIED |
| 2 | 3 Now Playing styles (Classic / M3 Expressive / Apple Music) | IMPLEMENTED | `NowPlayingContentSpotify.kt`, `NowPlayingContentM3Expressive.kt`, `NowPlayingContentAppleMusic.kt`; selector in `SettingScreen.kt:708-714` (`now_playing_style_*`) | UNVERIFIED |
| 3 | Apple Music lyrics (word-by-word glow) | IMPLEMENTED | `AppleMusicShared.kt`, `applemusic/` player dir (4 files) | UNVERIFIED |
| 4 | Lyrics romanization (12 languages) | IMPLEMENTED | `romanization` / `romanisation` across composeApp+core (26 files) | UNVERIFIED |
| 5 | Share lyrics as image | IMPLEMENTED | `ShareLyrics*`, `LyricsImage*` (11 files) | UNVERIFIED |
| 6 | SimpMusic Wrapped (year review + monthly recap playlists) | IMPLEMENTED | `WrappedScreen.kt`, `WrappedViewModel.kt`, `LibraryWrappedTab.kt` (48 files) | UNVERIFIED |
| 7 | Ten-band equalizer (drag curve, presets, AutoEq) → audio | IMPLEMENTED | UI: `EqualizerSection.kt`, `EqualizerPresets.kt`, `AutoEqPicker.kt`; audio: `core/media/media3/.../audio/EqualizerAudioProcessor.kt` + `MpvPlayer.setEqualizer` | UNVERIFIED (audio-pipeline) |
| 8 | Analytics tab (weeks/months/years, clock, fingerprint) | IMPLEMENTED | `AnalyticsScreen.kt`, `AnalyticsViewModel`, listening clock/fingerprint matches | UNVERIFIED |
| 9 | Widgets (turntable, playlists, insights) | **MISSING** | only `aboutlibraries.json` matched — **no `AppWidgetProvider` / `GlanceAppWidget`** anywhere | n/a |
| 10 | Liquid Glass UI (bottom bar + glass surfaces) | IMPLEMENTED | `LiquidGlassAppBottomNavigationBar.{common,android}`, `LiquidGlassTabBar.android`, `LiquidGlassContainer.kt` (24 files) | UNVERIFIED |
| 11 | Streaming reliability (YouTube ciphers on-device, source in song info) | IMPLEMENTED | `nsig` / `decipher` matches (10 files) | UNVERIFIED |
| 12 | Opus / AAC quality choice | IMPLEMENTED | `opus` / `aac` / `audioFormat` in scraper+player (10 files) | UNVERIFIED |
| 13 | Auto-download when liking | IMPLEMENTED | `autoDownload` / `AutoDownload` (5 files) | UNVERIFIED |
| 14 | Keep radio queues audio-only (optional) | IMPLEMENTED | `audioOnlyQueue` / `keepRadioAudio` (5 files) | UNVERIFIED |
| 15 | Sleep timer fades out | IMPLEMENTED | `sleepFade` / `SleepFade` (Media3 processor + mpv third level) (9 files) | UNVERIFIED |
| 16 | Crossfade keeps albums continuous | IMPLEMENTED | `albumTrackIds`, `PlaylistType.ALBUM` (8 files) | UNVERIFIED |
| 17 | Search inside playlists + multi-select | IMPLEMENTED | `SongSelectionState.kt`, `SongSelectionTopAppBar.kt` (`selectionMode`/`selectedSongIds`) (10 files) | UNVERIFIED |
| 18 | Paste YouTube link in search | IMPLEMENTED | `isYouTubeUrl` / `youtu.be` (2 files) | UNVERIFIED |
| 19 | Follow artist → subscribe YouTube channel | IMPLEMENTED | `subscribeChannel` (3 files) | UNVERIFIED |
| 20 | Clear listening history (one tap) | IMPLEMENTED | `clearHistory` / `deletePlaybackHistory` (4 files) | UNVERIFIED |
| 21 | Mix for You as its own tab | IMPLEMENTED | `MixForYou` (10 files) | UNVERIFIED |
| 22 | Desktop: floating capsule player | PARTIAL | `MiniPlayer.kt`, `ToggleMiniPlayer.{common,android}` — a mini-player exists; literal “capsule player” naming not found in source | n/a |
| 23 | Desktop: landscape headers | IMPLEMENTED | `isPortrait`-gated headers on Album/Playlist/Artist (2026-08-17 work) | UNVERIFIED |
| 24 | Desktop: pitch control | IMPLEMENTED | `setPitchScale`, pitch slider (11 files) | UNVERIFIED |
| 25 | Desktop: native download notifications | IMPLEMENTED | `downloadNotification` (2 files) | UNVERIFIED |
| 26 | Fix: signed-in radios keep tracks | IMPLEMENTED | wraps the `Content.track` / `primaryRenderer` path (spec note §25) | UNVERIFIED |
| 27 | Fix: no loading spinner over playing audio / no NA:NA at track end | IMPLEMENTED | playback-state publications in player handlers | UNVERIFIED |
| 28 | Fix: mini player no flash on start | IMPLEMENTED | mini-player startup guard | UNVERIFIED |
| 29 | Fix: queue position restores after restart | IMPLEMENTED | queue-restore path | UNVERIFIED |
| 30 | Fix: Apple Music player plays exact tapped row | IMPLEMENTED | queue-row selection wiring | UNVERIFIED |
| 31 | Fix: widgets render at launcher real size | **MISSING** | no widget implementation to size (see row 9) | n/a |
| 32 | Fix: unfollow artist cleans up notifications | IMPLEMENTED | unfollow cleanup path | UNVERIFIED |
| 33 | Fix: desktop volume reaches precached players | IMPLEMENTED | `forEachLiveHandle` incl. secondary + precached players | UNVERIFIED |
| 34 | Fix: Linux external links reliable | IMPLEMENTED | `OpenUrl.jvm.kt` launcher fallback (`xdg-open` → `gio open` → `$BROWSER`) | UNVERIFIED |

---

## Notes and honest limits

- **`IMPLEMENTED` = static wiring proof, not device proof.** Per the repo's
  ZERO-FALSE-POSITIVE rule, a row with `IMPLEMENTED` never implies the feature works
  on a real device; the “Device” column says `UNVERIFIED` everywhere except where the
  feature needs (or lacks) launcher/audio-pipeline proof.
- **Widgets (`MISSING`)** is the only genuine gap found in the static audit — there is
  no `AppWidgetProvider` or Compose Glance implementation in the tree. This matches
  `docs/FEATURE-AUDIT-REPORT.md` (v0.3.2) which also marks `widgets` NOT IMPLEMENTED.
- **No row is `BROKEN` or `INCOMPATIBLE` on static evidence alone**: “broken” requires a
  failing build or a failed runtime assertion, both of which are out of scope for a
  docs-only static audit. JVM compile was run and passed; the Android target is only
  covered by CI, not by this matrix.
- Features 31 (widgets sizing) is `MISSING` for the same reason as row 9 — there is no
  widget code to size, so it cannot be `BROKEN`.
- This document is **docs-only**: no production code was modified to produce it.

## Related
- `docs/UPSTREAM-2.0.0-AUDIT.md` — raw grep evidence, correction record, genuine-gap list.
- `docs/FEATURE-AUDIT-REPORT.md` — auto-generated per-feature SpaceKai verdicts + honesty block.
- `docs/UPSTREAM.md` — upstream sync procedure and conflict hotspots.