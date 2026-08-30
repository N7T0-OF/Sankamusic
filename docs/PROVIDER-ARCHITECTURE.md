# SpaceKai — Universal Music Provider Architecture (design)

_Status: DESIGN ONLY (P1). Nothing in this document is implemented yet. The
release gate (`audit-provider-arch.sh`) blocks adding any provider-specific
code before this abstraction exists — the failure mode to avoid is "three
broken auth systems instead of one"._

## 1. Goals & non-goals

SpaceKai's long-term direction: a **universal music client** — one UI, one
library, one queue, several sources (YouTube Music, Spotify, Apple Music,
Deezer, later Tidal/Qobuz/SoundCloud, and local files).

**Goals (this design):**
- ONE `MusicProvider` abstraction; providers are pluggable without touching
  the player, the library UI or the search UI.
- Capability-based UI: a provider never shows a button for a feature it does
  not support.
- ONE robust auth/OAuth system reused by every OAuth-capable provider.
- A `PlaylistTransferEngine` with a confidence-scored matcher (ISRC-first).
- Unified models (`UnifiedTrack`, …) so the player is provider-agnostic.
- Offline/cache levels per content type.

**Non-goals (explicit):**
- No 20 providers at once (§75 of the spec). Order: YouTube Music (exists) →
  Spotify (fix first) → Apple Music → Deezer → Local → others.
- **No scraping** of private endpoints. Only official APIs/SDKs (Apple
  MusicKit, Deezer API/SDK). If a capability is not legally/technically
  available for a provider, it is simply not in that provider's capabilities —
  never a fake login or a broken button.
- No provider-specific code outside the provider module (enforced by the
  gate).

## 2. Where it lives in the codebase

```
spacekai/                                   (existing add-on layer)
   ├── provider/
   │   ├── MusicProvider.kt                 interface + capabilities
   │   ├── UnifiedTrack.kt / UnifiedAlbum / UnifiedArtist / UnifiedPlaylist
   │   ├── ProviderRegistry.kt              Koin-registered singletons
   │   ├── auth/                            ONE OAuth engine (see §5)
   │   └── transfer/PlaylistTransferEngine.kt
   ├── SpaceKaiFeatures.kt                  + appleMusic / deezer / unifiedSearch /
   │                                            widgets / localMusic flags (off)
   └── SpaceKaiModule.kt                    registers the providers
```

Provider implementations live in their own module or package
(`provider/spotify/`, `provider/applemusic/`, `provider/deezer/`, …) — never
inline in UI screens. The existing `core/service/spotify` module (models +
`SpotifyPkce`, currently dead code) becomes the base of `provider/spotify/`.

## 3. `MusicProvider` interface

```kotlin
interface MusicProvider {
    val id: String                       // "spotify", "applemusic", ...
    val displayName: String
    val capabilities: Set<ProviderCapability>

    // Connection / account state
    val connectionState: StateFlow<ProviderConnectionState>  // Disconnected / Connected / Error(reason)

    // Search
    suspend fun search(query: String, types: Set<SearchType>): Result<SearchResults>
    suspend fun searchById(id: String): Result<UnifiedTrack?>     // for deep links / share

    // Library
    suspend fun getLibrary(): Result<LibrarySnapshot>             // playlists/albums/artists if supported
    suspend fun getPlaylist(id: String): Result<UnifiedPlaylist>
    suspend fun createPlaylist(title: String): Result<String>     // only if CREATE_PLAYLISTS
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit>
    suspend fun removeFromPlaylist(playlistId: String, trackIds: List<String>): Result<Unit>

    // Likes (only if LIKES)
    suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit>
    suspend fun getLiked(): Result<List<UnifiedTrack>>

    // Streams / playback (only if PLAYBACK)
    suspend fun resolveStream(track: UnifiedTrack, quality: StreamQuality): Result<StreamSource>
}
```

Every method is gated by `capabilities`. A provider that cannot create
playlists simply does not implement `createPlaylist` (interface default =
`Result.failure(UnsupportedCapability)`), and the UI hides the button.

**ProviderCapability** (spec §2): `SEARCH, PLAYBACK, PLAYLISTS, CREATE_PLAYLISTS,
LIBRARY, LIKES, ALBUMS, ARTISTS, LYRICS, DOWNLOAD, RADIO, RECOMMENDATIONS,
HISTORY, SYNC, SHARE`.

## 4. Unified models

```kotlin
data class UnifiedTrack(
    val provider: String,            // which provider owns this instance
    val providerTrackId: String,     // id inside that provider
    val title: String,
    val artists: List<String>,
    val album: String?,
    val albumId: String?,
    val artworkUrl: ArtworkRef,      // thumbnail / medium / large (§19)
    val durationMs: Long?,
    val isExplicit: Boolean,
    val isPlayable: Boolean,
    val sourceUrl: String?,          // deep link / share
    val lyricsUrl: String?,
    val isLiked: Boolean,
    val isrc: String?,               // the matcher's first key (§14)
)
```

One *recording* may have several `UnifiedTrack` instances (one per provider
where it exists). The **Library** keeps a local `unified_track` row with the
providers that have it, so a liked track is a single concept across sources.

## 5. Auth — ONE engine, no secrets in the APK

Single `OAuthProvider` core reused by every OAuth service:

```
OAuthFlow(providerConfig)   // PKCE (S256), state, verifier persisted
   ├── authorizeUrl()
   ├── handleCallback(uri)  // deep link: spacekai://auth/<provider>?code=...
   ├── exchangeCode(code, verifier)   // verifier MUST be the one generated
   │                                   // before authorize (gate-checked)
   ├── refreshToken()       // 401 → refresh once → retry; never for 403/409
   └── tokenStore           // DataStore, per-provider, with expiry
```

Rules (hard):
- **Never** embed a private key / client secret in the APK (Apple MusicKey
  private key, Deezer client secret). Apple Music needs a signed **Developer
  Token** (Team ID + Key ID + private key) — that signing happens on a
  backend/GitHub Secret and the APK receives only the token; the **Music User
  Token** is fetched at runtime per user. If no backend is available, the
  feature is marked NOT IMPLEMENTED rather than shipped broken.
- `redirect_uri` is ONE value per provider, byte-identical across app,
  request and developer dashboard — the `audit-spotify-flow.sh` rule
  generalizes to every provider.
## 5bis. Spotify strategy decision (P0 — decided 2026-08-26, evidence-based)

Facts (Spotify official docs + policy, May 2025):
- **No audio streaming via the Web API.** The public API serves metadata only;
  playable audio requires the Web Playback SDK, which needs Spotify's prior
  written approval for commercial use — not feasible for a FOSS client.
  ⇒ **`PLAYBACK = false` for Spotify, always.** Playback stays YouTube
  Music / Local. This is a capability, not a bug.
- **Lyrics are not in the public API.** The current `sp_dc` cookie flow
  (WebView → cookie → internal endpoints) is the only way to get Spotify
  lyrics/canvas today (the InnerTune method). It is unofficial, breakable and
  ToS-grey, but it is the reality. ⇒ **KEEP sp_dc for lyrics/canvas.**
- **Extended Web API quota is locked to organizations** (May 15, 2025 policy:
  “reserved for apps with established, scalable, and impactful use cases”;
  applications only from organizations, security/privacy/licensing review).
  A FOSS project cannot get extended quota. BUT **development mode remains
  accessible for personal use** — standard quota is enough for on-demand,
  rate-limited playlist import/sync.

Decision — two tracks, one provider:
1. **Track A (keep): sp_dc** for lyrics + canvas. No change, gated by the
   `spotifySync` flag (currently decorative — wiring is the P0).
2. **Track B (new, official): OAuth PKCE + Web API (development mode)** for
   playlist import/sync — the user's actual request. PKCE needs no client
   secret (client ID is public), the redirect_uri is declared once in the
   Spotify dashboard and must be byte-identical (the `audit-spotify-flow.sh`
   rule generalizes). Standard quota + rate limiter + cache + on-demand only;
   respect 429/403 (never retry 401/403/redirect_uri_mismatch).

Consequence for the old diagnosis: there is no OAuth today, so no
`redirect_uri` to “fix” — the phantom. When Track B ships, the redirect_uri
matters for real and the audit rule already guards it.

Fallback: if Spotify later restricts standard-quota distributed apps, Track B
falls back to sp_dc-based playlist import (documented limitation, never a
fake login).

## 5ter. Apple Music plan (P1 — evidence-based, 2026-08-26)

Facts (developer.apple.com/musickit — official):
- **MusicKit for Android is official** and ships two libraries:
  - **Authentication** — sign-in, developer token + **music user token** at
    runtime (prompts the user; helps install the Apple Music app if missing);
  - **Media Playback** — plays songs/albums/playlists without leaving the app,
    with **Lock screen / background control**.
- **Apple Music API** — available for Apple platforms, **Android**, and web:
  search, catalog, recommendations, recently played (HISTORY), favorites
  (LIKES — add to favorites, filter by favorite artists), create/modify
  playlists (PLAYLISTS).
- **Developer token** must be a JWT signed with the media identifier private
  key (Certificates, Identifiers & Profiles) — **signed server-side via a
  GitHub Secret / backend; NEVER in the APK**. The music user token comes from
  the Authentication library at runtime.
- **Storefront (region)** is required on every API call — store it per user.
- Stability risk (documented): forum reports of the Android SDK breaking after
  Apple Music app updates — pin the SDK version and treat MusicKit as a
  moving target.

Capabilities for `applemusic`:
`SEARCH, PLAYBACK, PLAYLISTS, LIBRARY, LIKES, ALBUMS, ARTISTS,
RECOMMENDATIONS, HISTORY` — `DOWNLOAD` no (no offline in the SDK),
`SHARE` via catalog URLs.

**Playback architecture decision (the honest one):** the MusicKit Media
Playback library is its OWN player — it does not emit streams our
`MediaPlayerInterface` (ExoPlayer/mpv) could play. Two options:
- (a) The unified player gets a per-provider playback slot: YTM/Local tracks
  play in the main player, Apple Music tracks hand off to the MusicKit player
  (UI stays one player; engine routes per track). Recommended.
- (b) `PLAYBACK = false` for Apple Music, deep-link to the Apple Music app.

Either way the **UI is unchanged** — the choice is internal to the playback
engine. This is decided at implementation time, not designed away.

## 5quater. Deezer plan (P1 — evidence-based, 2026-08-26)

Facts (developers.deezer.com + community):
- Deezer API exists with OAuth, search, playlists, albums, artists, favorites,
  personal data. Premium/HiFi/Family users get streaming-level access.
- **App creation may be paused** (community reports 2024: “they haven't been
  allowing any app creation for some time”) — the FIRST step is to check
  whether a new application can be registered at all.
- Audio streaming for third parties is gated (premium + approval) — treat
  Deezer as a **metadata provider**: search, library, playlists, favorites,
  import/sync. **`PLAYBACK = false`** unless/until an approved SDK path exists.
- The Android package name must be declared in the Deezer app configuration.

Capabilities for `deezer` (target):
`SEARCH, PLAYLISTS, LIBRARY, LIKES, ALBUMS, ARTISTS` — no PLAYBACK, no
DOWNLOAD, no RADIO. Playback fallback chain (§64) skips it.

If app registration is closed, the provider is marked NOT IMPLEMENTED with
that exact reason in the release notes — never a fake login.

## 6. PlaylistTransferEngine + matcher (§13-15)

```kotlin
class PlaylistTransferEngine(
    private val sources: ProviderRegistry,
) {
    suspend fun transfer(
        from: MusicProvider, playlistId: String,
        to: MusicProvider, targetPlaylistId: String,
    ): TransferReport
}
```

Matching score, in order of priority (§65):
1. **ISRC equal** → 100%
2. title + artist + duration (±2s) → ≥90%
3. title + artist → ≥75%
4. title only → ≥50%, marked "uncertain"

`TransferReport` (spec §15):
```
✓ 96 imported     (score ≥ 90)
⚠ 3 to check      (50-89)   → UI lists them for manual choice
✕ 1 not found     (< 50)
```

No auto-replace below confidence 90. Sync uses `providerRevision` +
`lastSync` + a playlist hash — never a polling loop (§16), and never a
bidirectional loop (only SpaceKai → service when `CREATE_PLAYLISTS`, only
service → SpaceKai on explicit sync or a local revision check).

## 7. Unified search (§23-25)

One `SearchViewModel` fanning out to the enabled providers with **controlled
parallelism**: debounce (≥300 ms), cancellation of the previous query, per-
provider timeout, partial results rendered as they arrive, filters
(Toutes / per source / Morceaux / Albums / Artistes / Playlists). A slow or
failing provider never blocks the others (§57).

## 8. Player independence (§20-22)

The player already goes through `MediaPlayerInterface` (core/media). The
provider layer maps `UnifiedTrack` → a stream (`resolveStream`) — the player
never knows the provider. The queue can mix providers (each track carries its
own `provider`); the previous/next prefetch is per-track via the owning
provider's `resolveStream` (§29).

## 9. Offline / cache (§17-19)

- `LocalMusicProvider` (P2): MediaStore scan, **incremental indexing**
  (Room + last-modified cursor), never a full storage scan per launch.
- Cache levels: artwork (thumbnail/medium/large per context), metadata,
  search (short TTL), playlists, lyrics — separate TTLs, single
  `imageLoader` with memory + disk cache (Coil `diskCachePolicy` already used
  in `AdapterItems.kt`).
- `NetworkState` (ONLINE / OFFLINE / METERED / WIFI / VPN / LIMITED) gates
  heavy operations; "Économiser les données" lowers artwork size and defers
  sync.

## 10. Priority order & gates

| Priority | Work | Gate / audit |
| --- | --- | --- |
| P0 | Fix Spotify, updater, player paysage, nav, settings, Dynamic Color, vibration | existing 12 code gates + FEATURE AUDIT |
| P1 | MusicProvider abstraction + `provider/spotify/` first | `audit-provider-arch.sh` |
| P1 | Apple Music (MusicKit), Deezer (API/SDK) | per-provider gate (auth rules) |
| P1 | PlaylistTransferEngine, widgets, Android Auto, offline/cache | — |
| P2 | Local, Last.fm, Tidal, Qobuz, SoundCloud | — |

**`audit-provider-arch.sh`** (see `docs/FEATURE-AUDIT.md` § volet): as long as
the `MusicProvider` interface does not exist, FAIL if any provider-specific
integration code appears in the codebase (other than settings labels/feature
flags). Once the abstraction exists, it switches to: FAIL if a provider
implementation is not capability-gated or ships an auth path with secrets in
the APK.

## References

- Spotify Web API authorization (PKCE recommended for mobile):
  https://developer.spotify.com/documentation/web-api/concepts/authorization
- Spotify “Updating the Criteria for Web API Extended Access” (May 15, 2025 —
  extended quota reserved for organizations, development mode stays for
  personal use): https://developer.spotify.com/blog/2025-04-15-updating-the-criteria-for-web-api-extended-access
- Web Playback SDK commercial-use restriction:
  https://community.spotify.com/t5/Spotify-for-Developers/.../td-p/6879704
- Apple MusicKit user authentication (developer token + music user token):
  https://developer.apple.com/documentation/applemusicapi/user-authentication-for-musickit
- Deezer API: https://developers.deezer.com/api

## 11. What we are NOT doing

- No scraping of Apple/Deezer/Spotify private endpoints.
- No fake "connected" states.
- No provider-specific code in screens.
- No new provider before Spotify P0 is resolved (the user's own ordering).
- No release claim of any of this until the FEATURE AUDIT marks it IMPLEMENTED.
