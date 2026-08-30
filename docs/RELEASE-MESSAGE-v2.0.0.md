# Message de release v2.0.0 — prêt à coller (généré depuis le pipeline réel)

> Ce document contient le texte exact qui sera publié. **La note GitHub est
> régénérée par la CI** (`android-release.yml` : copie de `74.txt` →
> `append-feature-audit-gaps.sh`) — le contenu ci-dessous est la reproduction
> vérifiée de ce flux (généré et comparé cette session : 21 lignes de gaps,
> 0 contradiction). Le texte Play Store est le contenu de
> `fastlane/metadata/android/en-US/changelogs/74.txt` tel quel.
>
> ⚠️ Le tag ne sera **pas** poussé tant que `audit-features` n'est pas vert
> (les 6 toggles factices — plan : `docs/WIRING-P0.md`). Ce message est prêt
> pour le moment où le gate s'ouvrira.

---

## GitHub release

**Titre** (le tag détermine la version, le titre reprend le header du changelog) :

```
SpaceKai v2.0.0
```

**Corps** (tel que produit par la CI — à coller tel quel) :

```markdown
SpaceKai v2.0.0

Consolidation release — the 1.9.x feature work ships as a major version with a hardened release pipeline.

- The 1.9.x feature work (landscape navigation, navigation-bar settings, swipe-to-skip, persisted flags, home performance) is carried into this major version; the landscape-player layout is still work-in-progress (see "Known / not done")
- Release pipeline hardened end-to-end: the guard scripts are syntax-checked, gate parity is enforced across ALL release gates, and test-gates.sh now also runs 3 wiring-procedure simulations (spotifySync removal, downloadWifiOnly removal, 4-toggles wiring) that keep the procedures honest at every run
- test-gates.sh is state-agnostic: it passes both before AND after the remaining decorative toggles are wired or removed — the correct release procedure is never mis-flagged as a regression
- The feature-audit generator no longer references decorative flag names; verdicts are evidence-based (the Spotify login row is judged by the real sp_dc wiring chain, not a toggle flag)
- Releasing to the public is now a single command chain: publish.sh (gates + tag) → CI draft → publish-draft.sh (assets re-verified, honesty block present AND fresh vs the regenerated audit, then publish)
- Publication honesty guard: publish-draft.sh refuses any draft whose release note is missing the "Known / not done" block, or whose gap lines are stale vs the freshly generated audit — nothing ships hiding unfinished work

---

### Connu / non terminé
- **Barre de navigation personnalisable (styles, sections, ordre, raccourcis)** — PARTIALLY IMPLEMENTED. Seul le swipe-to-skip est câblé (App.kt). Styles verre liquide + translucide existent (base) mais pas de sélecteur; sections/ordre/raccourcis absents. Voir scripts/audit-navigation.sh.
- **Style de barre minimaliste** — PARTIALLY IMPLEMENTED. Toggle décoratif : flag 'minimalisticNavigation' — 0 référence réelle hors Settings/déclaration (audit-features.sh). Plan de câblage : docs/WIRING-P0.md
- **Barre verticale à droite en paysage** — PARTIALLY IMPLEMENTED. Rail de base (gauche) — pas de version SpaceKai à droite en paysage.
- **Dynamic Color Android (fond + surfaces)** — PARTIALLY IMPLEMENTED. Toggle décoratif : flag 'dynamicColor' — 0 référence réelle hors Settings/déclaration (audit-features.sh). Plan de câblage : docs/WIRING-P0.md
- **Orientation auto / portrait / paysage** — NOT IMPLEMENTED. Aucune preuve (UI, logique, câblage) dans le scope vérifiable
- **Player paysage = même player responsive (layout-only)** — PARTIALLY IMPLEMENTED. Toggle décoratif : flag 'landscapePlayer' — 0 référence réelle hors Settings/déclaration (audit-features.sh). Plan de câblage : docs/WIRING-P0.md
- **Infos player désactivables (artiste, description, paroles)** — PARTIALLY IMPLEMENTED. Toggle décoratif : flag 'customPlayerInfo' — 0 référence réelle hors Settings/déclaration (audit-features.sh). Plan de câblage : docs/WIRING-P0.md
- **Vibration (événements, intensité)** — PARTIALLY IMPLEMENTED. Seul onClick NowPlaying est câblé — pas d'intensité, pas d'événements réglages/navigation.
- **Fondu enchaîné : slider visuel** — NOT IMPLEMENTED. Aucune preuve UI dans composeApp; le réglage (dropdown upstream) vit ailleurs.
- **Login (cookie sp_dc) + import playlists** — PARTIALLY IMPLEMENTED. Décision P0 documentée (docs/PROVIDER-ARCHITECTURE.md §5bis) : sp_dc pour lyrics/canvas (chaîne réelle : login WebView → saveSpotifySpdc → setSpdc → spotifyLoggedIn, gate scripts/audit-spotify-flow.sh). Aucun OAuth PKCE ni import de playlists aujourd'hui. Voir scripts/audit-spotify-flow.sh.
- **Téléchargements Wi-Fi uniquement** — PARTIALLY IMPLEMENTED. Toggle décoratif : flag 'downloadWifiOnly' — 0 référence réelle hors Settings/déclaration (audit-features.sh). Plan de câblage : docs/WIRING-P0.md
- **Une seule notification par file** — NON VÉRIFIABLE. Logique dans core/data — sous-module absent de ce snapshot.
- **Sections de réglages fermées par défaut** — NOT IMPLEMENTED. Aucune preuve (UI, logique, câblage) dans le scope vérifiable
- **Mise à jour interne (check + téléchargement + installation)** — PARTIALLY IMPLEMENTED. Détection OK (réglage → checker → dialogue, cache CheckForUpdateAt, bouton = page releases SpaceKai). Téléchargement/installation internes ABSENTS — P0 n°1. Voir scripts/audit-updater-flow.sh.
- **Abstraction MusicProvider + capabilities** — NOT IMPLEMENTED. Design seul (docs/PROVIDER-ARCHITECTURE.md) — gate audit-provider-arch.sh en Mode A (aucun code provider sans l'abstraction).
- **Apple Music (MusicKit officiel, developer token + music user token)** — NOT IMPLEMENTED. Design seul — aucune implémentation. Plan factuel docs/PROVIDER-ARCHITECTURE.md §5ter : SDK Android officiel (Authentication + Media Playback), token signé côté backend/GitHub Secret (jamais dans l'APK), storefront requis. Lecture via le player MusicKit (slot par provider), pas notre MediaPlayerInterface.
- **Deezer (API/SDK officiel)** — NOT IMPLEMENTED. Design seul — aucune implémentation. Plan factuel docs/PROVIDER-ARCHITECTURE.md §5quater : PLAYBACK=false (streaming gateé premium+approbation), provider métadonnées seulement. Première étape : vérifier si la création d'apps est rouverte. Jamais de login factice.
- **Recherche multi-sources (debounce, cancellation, timeouts)** — NOT IMPLEMENTED. Design seul — moteur à construire (voir §7 du design).
- **Musique locale (MediaStore, indexation incrémentale)** — NOT IMPLEMENTED. Design seul — indexation à construire (voir §9 du design). Vérifié : MediaStore n'apparaît que dans AutoBackupWorker (sauvegarde, pas d'indexation musicale).
- **Service MediaLibraryService (browse tree)** — NON VÉRIFIABLE. Manifeste androidApp:228-231 : SimpleMediaService (exported, FGS mediaPlayback) déclare MediaSessionService + MediaLibraryService + MediaBrowserService. Classe dans core/media/media3 — sous-module absent de ce snapshot. Browse tree (playlists/albums/artistes) à confirmer sur le dépôt complet.
- **Widgets Android (play/pause, artwork, 4x2)** — NOT IMPLEMENTED. Aucun AppWidgetProvider dans tout le dépôt (composeApp, androidApp, core). À construire (spec #33).

### Installation

Android : `SpaceKai-v2.0.0.apk` — APK universel signé (arm64-v8a, armeabi-v7a, x86_64)
Windows : installer `.msix` · macOS : `.dmg` · Linux : `.AppImage`

### SHA-256

`SHA256SUMS.txt` joint à la release — vérification d'intégrité avant installation.
```

**Assets à attacher** (générés par la CI) : `SpaceKai-v2.0.0.apk` ·
`SHA256SUMS.txt` · installateurs desktop (`.msix`, `.dmg`, `.AppImage`).

---

## Play Store (listing — texte de `74.txt`)

```
SpaceKai v2.0.0

Consolidation release — the 1.9.x feature work ships as a major version with a hardened release pipeline.

- The 1.9.x feature work (landscape navigation, navigation-bar settings, swipe-to-skip, persisted flags, home performance) is carried into this major version; the landscape-player layout is still work-in-progress (see "Known / not done")
- Release pipeline hardened end-to-end: the guard scripts are syntax-checked, gate parity is enforced across ALL release gates, and test-gates.sh now also runs 3 wiring-procedure simulations (spotifySync removal, downloadWifiOnly removal, 4-toggles wiring) that keep the procedures honest at every run
- test-gates.sh is state-agnostic: it passes both before AND after the remaining decorative toggles are wired or removed — the correct release procedure is never mis-flagged as a regression
- The feature-audit generator no longer references decorative flag names; verdicts are evidence-based (the Spotify login row is judged by the real sp_dc wiring chain, not a toggle flag)
- Releasing to the public is now a single command chain: publish.sh (gates + tag) → CI draft → publish-draft.sh (assets re-verified, honesty block present AND fresh vs the regenerated audit, then publish)
- Publication honesty guard: publish-draft.sh refuses any draft whose release note is missing the "Known / not done" block, or whose gap lines are stale vs the freshly generated audit — nothing ships hiding unfinished work
```

---

## Règles de publication (rappel)

- **draft → assets → publish**, jamais l'inverse ;
- ne jamais supprimer une release publiée ;
- `verify-release.yml` contrôle les checksums et le versionCode (73 > 72) après
  publication ;
- ce message ne part **que** quand `check-pre-tag.sh` affiche
  `ALL CHECKS PASSED` (via `./scripts/publish.sh` sur le dépôt live).