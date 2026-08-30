#!/bin/bash
# generate-feature-audit.sh — automatic FEATURE AUDIT report.
#
# The machine half of the "ZERO FALSE POSITIVE" rule:
#   "Ne jamais marquer 'implemented' uniquement parce qu'un fichier existe."
#
# For every SpaceKai feature it gathers EVIDENCE (grep hits) for the three
# things a real feature needs — UI, logic, wiring — and classifies the feature
# from that evidence, never from the existence of a flag or a file alone:
#
#   IMPLEMENTED (static)   UI + logic + wiring all have evidence, no gap.
#   PARTIALLY IMPLEMENTED  UI/logic exists but wiring is missing (decorative
#                          toggle) or a documented gap limits the feature.
#   NOT IMPLEMENTED        no evidence at all.
#   BROKEN                 known regression marker present (see settings UI
#                          gate — size-transform animation inside a lazy item,
#                          the documented settings-overlap bug).
#   NON VÉRIFIABLE         logic lives in a part of the repo not present in
#                          this snapshot (core/data submodule).
#   OK (gate)              covered by a dedicated automated gate that passes.
#
# "IMPLEMENTED (static)" means "the wiring exists in the source" — it is NOT a
# runtime proof. The manual test checklist in pre-release-report.sh
# (## Installation) still applies to every feature. The report says so.
#
# The report also contains:
#   - a ready-to-paste "### Connu / non terminé" block for the release notes
#     (the rule: a feature that is not finished MUST be listed explicitly),
#   - a claim cross-check: lines of the latest changelog / RELEASE.md that
#     claim a feature classified NOT IMPLEMENTED / BROKEN / NON VÉRIFIABLE.
#
# Output: docs/FEATURE-AUDIT-REPORT.md (regenerated on every run;
#         gitignored — it is an artifact, the tracked doc is FEATURE-AUDIT.md)
# Usage:  ./scripts/generate-feature-audit.sh
# Exit:   0 always (generation). The release gate (pre-release-report.sh)
#         interprets the report: BROKEN blocks, gaps must appear in notes.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="composeApp/src/commonMain/kotlin/com/maxrave/simpmusic"
# Files that only declare / persist / render a feature (never wiring).
DECL='spacekai/SpaceKaiFeatures.kt|spacekai/SpaceKai.kt|spacekai/ui/SpaceKaiSettingsSection.kt'
OUT="docs/FEATURE-AUDIT-REPORT.md"
NOW=$(date -u +"%Y-%m-%dT%H:%MZ")
VERSION=$(grep '^version-name' gradle/libs.versions.toml 2>/dev/null | head -1 | cut -d'"' -f2 || echo "?")
CORE_PRESENT="no"; [ -d core/data ] && CORE_PRESENT="yes"

if [ ! -d "$SRC" ]; then
  echo "FAIL: source dir not found: $SRC"
  echo "Run from the repo root; composeApp/ must be present."
  exit 1
fi

# ---------------------------------------------------------------------------
# 1. Flag verdicts from audit-features.sh (wiring truth for the 8 flags).
# ---------------------------------------------------------------------------
# Only accept names that are ACTUAL feature flags: audit-features.sh now also
# reports layer-level wiring ("PASS/FAIL: applyPersistedSpaceKaiFeatures…")
# which matches the same `^  PASS: <Name>` extraction pattern but is not a
# flag. Filtering by the declared flag list keeps the map free of impostors.
declare -A FLAG
KNOWN_FLAGS=$(grep -oE 'val [a-zA-Z]+: Boolean =' "$SRC/spacekai/SpaceKaiFeatures.kt" | sed -E 's/val ([a-zA-Z]+):.*/\1/')
while IFS= read -r f; do
  [ -n "$f" ] && printf '%s\n' "$KNOWN_FLAGS" | grep -qx "$f" && FLAG["$f"]="PASS"
done < <(bash scripts/audit-features.sh 2>/dev/null | sed -n 's/^  PASS: \([A-Za-z][A-Za-z0-9]*\).*/\1/p')
while IFS= read -r f; do
  [ -n "$f" ] && printf '%s\n' "$KNOWN_FLAGS" | grep -qx "$f" && FLAG["$f"]="FAIL"
done < <(bash scripts/audit-features.sh 2>/dev/null | sed -n 's/^  FAIL: \([A-Za-z][A-Za-z0-9]*\).*/\1/p')

# ---------------------------------------------------------------------------
# 2. Feature database. Fields:
#    id | category | label | flag | ui_pattern | logic_pattern | gap_note | own_file
#    "-" = none. flag "CORE" = logic lives in core/data (non verifiable here).
# ---------------------------------------------------------------------------
FEATURES=$(cat <<'EOF'
nav-custom|Navigation|Barre de navigation personnalisable (styles, sections, ordre, raccourcis)|customNavigation|customNavigation|SpaceKaiFeatures::customNavigation|Seul le swipe-to-skip est câblé (App.kt). Styles verre liquide + translucide existent (base) mais pas de sélecteur; sections/ordre/raccourcis absents. Voir scripts/audit-navigation.sh.|
nav-minimal|Navigation|Style de barre minimaliste|minimalisticNavigation|minimalisticNavigation|SpaceKaiFeatures::minimalisticNavigation|Le style 'minimaliste' n'existe pas comme style distinct — flag décoratif. Voir scripts/audit-navigation.sh.|
nav-hide-label|Navigation|Masquer le texte de la barre (compacte)|-|hideNavLabel|showLabels = !hideNavLabel||
nav-landscape|Navigation|Barre verticale à droite en paysage|-|AppNavigationRail|AppNavigationRail|Rail de base (gauche) — pas de version SpaceKai à droite en paysage.|
dynamic-color|Thème|Dynamic Color Android (fond + surfaces)|dynamicColor|dynamicColor|SpaceKaiFeatures::dynamicColor|Capacité base OK (palette système + wallpaper/seed) mais fond sombre épinglé au noir — bug confirmé. Flag décoratif. Voir scripts/audit-dynamic-color.sh.|
orientation|Interface|Orientation auto / portrait / paysage|-|-|-||
landscape-player|Player|Player paysage = même player responsive (layout-only)|SPACEKAI FIX|matchHeightConstraintsFirst|-|Aucune branche d'orientation dans NowPlayingScreen (portrait-first étiré) — téléphone paysage = overlay plein écran portrait comprimé. Fix : branche wDP>hDP layout-only. Le toggle SpaceKai a été retiré (WIRING-P0 option B) — le correctif artwork `matchHeightConstraintsFirst` reste inconditionnel. Voir scripts/audit-landscape-player.sh.|
player-info|Player|Infos player désactivables (artiste, description, paroles)|customPlayerInfo|customPlayerInfo|SpaceKaiFeatures::customPlayerInfo||
haptics|Vibration|Vibration (événements, intensité)|haptics|haptics|HapticsSpaceKai|Seul onClick NowPlaying est câblé — pas d'intensité, pas d'événements réglages/navigation.|features/haptics/HapticsSpaceKai.kt
crossfade-slider|Audio|Fondu enchaîné : slider visuel|-|-|-|Aucune preuve UI dans composeApp; le réglage (dropdown upstream) vit ailleurs.|
spotify|Spotify|Login (cookie sp_dc) + import playlists|-|saveSpotifySpdc|setSpdc|Décision P0 documentée (docs/PROVIDER-ARCHITECTURE.md §5bis) : sp_dc pour lyrics/canvas (chaîne réelle : login WebView → saveSpotifySpdc → setSpdc → spotifyLoggedIn, gate scripts/audit-spotify-flow.sh). Aucun OAuth PKCE ni import de playlists aujourd'hui. Voir scripts/audit-spotify-flow.sh.|
dl-notif|Téléchargements|Une seule notification par file|CORE|-|-|Logique dans core/data — sous-module absent de ce snapshot.|
settings-collapsed|Paramètres|Sections de réglages fermées par défaut|-|SettingsCollapsibleHeader|expandedSettingsSection|Réglages repliables (chewron rotatif, une section ouverte, corps non composé si fermée). Statique — runtime à confirmer sur appareil. Voir scripts/audit-settings-ui.sh.|ui/screen/home/SettingScreen.kt
settings-overlap|Paramètres|Pas de superposition de textes|-|-|-|Verdict = gate audit-settings-ui.sh.|
updater|Mise à jour|Mise à jour interne (check + téléchargement + installation)|-|SpaceKaiUpdatesSection|SpaceKaiUpdateManager|Détection OK + téléchargement APK universel + SHA-256 avant installation + Package Installer Android (expect/actual PlatformUpdater). Détection=CORE, download/install=composeApp. Vérifié compile+jvm+gate+CI Android; device run NON VÉRIFIÉ. Voir scripts/audit-updater-flow.sh.|spacekai/update/SpaceKaiUpdateManager.kt
provider-arch|Providers|Abstraction MusicProvider + capabilities|-|-|-|Design seul (docs/PROVIDER-ARCHITECTURE.md) — gate audit-provider-arch.sh en Mode A (aucun code provider sans l'abstraction).|
apple-music|Providers|Apple Music (MusicKit officiel, developer token + music user token)|-|-|-|Design seul — aucune implémentation. Plan factuel docs/PROVIDER-ARCHITECTURE.md §5ter : SDK Android officiel (Authentication + Media Playback), token signé côté backend/GitHub Secret (jamais dans l'APK), storefront requis. Lecture via le player MusicKit (slot par provider), pas notre MediaPlayerInterface.|
deezer|Providers|Deezer (API/SDK officiel)|-|-|-|Design seul — aucune implémentation. Plan factuel docs/PROVIDER-ARCHITECTURE.md §5quater : PLAYBACK=false (streaming gateé premium+approbation), provider métadonnées seulement. Première étape : vérifier si la création d'apps est rouverte. Jamais de login factice.|
unified-search|Providers|Recherche multi-sources (debounce, cancellation, timeouts)|-|-|-|Design seul — moteur à construire (voir §7 du design).|
local-music|Providers|Musique locale (MediaStore, indexation incrémentale)|-|-|-|Design seul — indexation à construire (voir §9 du design). Vérifié : MediaStore n'apparaît que dans AutoBackupWorker (sauvegarde, pas d'indexation musicale).|
android-auto|Android Auto|Service MediaLibraryService (browse tree)|CORE|-|-|Manifeste androidApp:228-231 : SimpleMediaService (exported, FGS mediaPlayback) déclare MediaSessionService + MediaLibraryService + MediaBrowserService. Classe dans core/media/media3 — sous-module absent de ce snapshot. Browse tree (playlists/albums/artistes) à confirmer sur le dépôt complet.|
widgets|Widgets|Widgets Android (play/pause, artwork, 4x2)|-|-|-|Aucun AppWidgetProvider dans tout le dépôt (composeApp, androidApp, core). À construire (spec #33).|
EOF
)

# ---------------------------------------------------------------------------
# 3. Classification helpers.
# ---------------------------------------------------------------------------
# Exclusion regex for logic evidence: declaration files + the feature's own
# file. Joining ONLY non-empty parts matters — an empty alternative in a
# grep -E pattern matches every line and would filter everything out.
excl_for() { # excl_for <own_file> → "$DECL" or "$DECL|$own"
  if [ -n "$1" ]; then echo "$DECL|$1"; else echo "$DECL"; fi
}
hits() { # hits <pattern> <exclude> — count grep hits, excludes declaration files
  local pat="$1" excl="$2"
  [ "$pat" = "-" ] && { echo 0; return; }
  local n
  n=$(grep -rEn --include='*.kt' "$pat" "$SRC" 2>/dev/null | grep -Ev "$excl" | grep -c . || true)
  echo "${n:-0}"
}
snippet() { # snippet <pattern> <exclude> <max> — first hits as "file:line: text"
  local pat="$1" excl="$2" max="$3" i=0
  [ "$pat" = "-" ] && return
  grep -rEn --include='*.kt' "$pat" "$SRC" 2>/dev/null | grep -Ev "$excl" | head -"$max" | while IFS= read -r l; do
    echo "      $l" | cut -c1-140
  done
}

# ---------------------------------------------------------------------------
# 4. Changelog for the claim cross-check (highest numeric version-code file).
# ---------------------------------------------------------------------------
CHANGELOG=""
LATEST=$(ls fastlane/metadata/android/en-US/changelogs/*.txt 2>/dev/null | sed 's|.*/||; s|\.txt$||' | sort -n | tail -1 || true)
if [ -n "$LATEST" ]; then CHANGELOG="fastlane/metadata/android/en-US/changelogs/${LATEST}.txt"; fi

# ---------------------------------------------------------------------------
# 5. Classify + emit the report.
# ---------------------------------------------------------------------------
report=""
declare -A VERDICT_COUNT
for v in "IMPLEMENTED (static)" "PARTIALLY IMPLEMENTED" "NOT IMPLEMENTED" "BROKEN" "NON VÉRIFIABLE" "OK (gate)"; do
  VERDICT_COUNT["$v"]=0
done

rows=""
gaps=""
contradictions=""

while IFS='|' read -r id cat label flag ui logic gap own; do
  [ -z "$id" ] && continue

  ui_hits=0; logic_hits=0; verdict=""; reason=""
  [ "$ui" != "-" ] && ui_hits=$(hits "$ui" "$DECL")
  [ "$logic" != "-" ] && logic_hits=$(hits "$logic" "$(excl_for "$own")")

  # Special: settings-overlap takes its verdict from the dedicated gate.
  if [ "$id" = "settings-overlap" ]; then
    if bash scripts/audit-settings-ui.sh >/dev/null 2>&1; then
      verdict="OK (gate)"
      reason="audit-settings-ui.sh passe — aucune animation de taille dans un lazy item (le bug de superposition est couvert)"
    else
      verdict="BROKEN"
      reason="audit-settings-ui.sh a détecté une animation de taille dans un lazy item (textes superposés)"
    fi
  elif [ "$flag" = "CORE" ]; then
    verdict="NON VÉRIFIABLE"
    reason="${gap:-Logique dans core/data — sous-module absent de ce snapshot (core présent: $CORE_PRESENT)}"
  elif [ "$flag" != "-" ] && [ "${FLAG[$flag]:-}" = "FAIL" ]; then
    verdict="PARTIALLY IMPLEMENTED"
    reason="Toggle décoratif : flag '$flag' — 0 référence réelle hors Settings/déclaration (audit-features.sh). Plan de câblage : docs/WIRING-P0.md"
  elif [ "$logic_hits" -gt 0 ] && [ "$ui_hits" -gt 0 ] && [ "$gap" = "" ]; then
    verdict="IMPLEMENTED (static)"
    reason="UI + logique + câblage présents (preuve statique; runtime à confirmer par la checklist manuelle)"
  elif [ "$logic_hits" -gt 0 ] || [ "$ui_hits" -gt 0 ]; then
    verdict="PARTIALLY IMPLEMENTED"
    reason="$gap"
    [ -z "$reason" ] && reason="UI ou logique seule trouvée (pas les deux + câblage)"
  else
    verdict="NOT IMPLEMENTED"
    reason="$gap"
    [ -z "$reason" ] && reason="Aucune preuve (UI, logique, câblage) dans le scope vérifiable"
  fi

  VERDICT_COUNT["$verdict"]=$((VERDICT_COUNT["$verdict"] + 1))

  # Evidence lines.
  ev=""
  if [ "$ui" != "-" ]; then
    ev="$ev
      UI : pattern '$ui' → ${ui_hits} hit(s)"
    ev="$ev
$(snippet "$ui" "$DECL" 3)"
  fi
  if [ "$logic" != "-" ]; then
    ev="$ev
      LOGIC : pattern '$logic' (hors déclaration) → ${logic_hits} hit(s)"
    ev="$ev
$(snippet "$logic" "$(excl_for "$own")" 3)"
  fi
  if [ "$flag" != "-" ] && [ "$flag" != "CORE" ]; then
    ev="$ev
      FLAG : $flag → ${FLAG[$flag]:-non audité}"
  fi

  rows="$rows
| \`$id\` | $cat | $label | **$verdict** | $reason |"

  if [ "$verdict" != "IMPLEMENTED (static)" ] && [ "$verdict" != "OK (gate)" ]; then
    gaps="$gaps
- **$label** — $verdict. $reason"
  fi

  # Claim cross-check: NOT IMPLEMENTED / BROKEN / NON VÉRIFIABLE features must
  # not be claimed as working in the release notes — AND so must features whose
  # flag is a decorative toggle (PARTIALLY IMPLEMENTED, reason 'Toggle
  # décoratif'): claiming "style selector (Minimalist…)" while the toggle does
  # nothing is the exact changelog-73 pattern. Scans ONLY the fastlane
  # changelog (the actual user-facing release note) — RELEASE.md / docs are
  # internal and legitimately describe the gaps, so scanning them produced
  # false positives. Negation-aware in BOTH French and English.
  deco_partial=false
  if [ "$verdict" = "PARTIALLY IMPLEMENTED" ] && printf '%s' "$reason" | grep -q "Toggle décoratif"; then deco_partial=true; fi
  if [ "$verdict" = "NOT IMPLEMENTED" ] || [ "$verdict" = "BROKEN" ] || [ "$verdict" = "NON VÉRIFIABLE" ] || [ "$deco_partial" = "true" ]; then
    kw=$(printf '%s' "$label" | cut -d' ' -f1)
    [ -n "$kw" ] || continue
    [ -n "$CHANGELOG" ] && [ -f "$CHANGELOG" ] || continue
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      if ! printf '%s' "$line" | grep -qiE '\b(non|ne|pas|jamais|sans|absent|manquant|incomplet|à faire|todo|en cours|no|not|never|without|missing|unfinished|pending|wip|aucun|aucune)\b'; then
        contradictions="$contradictions
  - [$CHANGELOG] « $(printf '%s' "$line" | cut -c1-110) » vs **$label** = $verdict"
      fi
    done < <(grep -iE "\b${kw}\b" "$CHANGELOG" | head -3)
  fi
done <<< "$FEATURES"

# ---------------------------------------------------------------------------
# 6. Assemble the markdown report.
# ---------------------------------------------------------------------------
{
  echo "# FEATURE AUDIT — SpaceKai v${VERSION}"
  echo ""
  echo "_Généré automatiquement par \`scripts/generate-feature-audit.sh\` le ${NOW}._"
  echo "_Règle ZERO FALSE POSITIVE : aucune fonctionnalité n'est marquée IMPLEMENTED sans preuve (UI + logique + câblage). IMPLEMENTED (static) = le câblage existe dans le code — pas une preuve de fonctionnement runtime (voir checklist manuelle § Installation du pre-release-report)._"
  echo ""
  echo "Contexte du snapshot : \`core/data\` présent = **$CORE_PRESENT** (un 'no' rend les features à logique core NON VÉRIFIABLES, pas NOT IMPLEMENTED)."
  echo ""
  echo "## Résumé"
  echo ""
  echo "| Verdict | Nombre |"
  echo "|---|---|"
  for v in "IMPLEMENTED (static)" "PARTIALLY IMPLEMENTED" "NOT IMPLEMENTED" "BROKEN" "NON VÉRIFIABLE" "OK (gate)"; do
    echo "| $v | ${VERDICT_COUNT[$v]} |"
  done
  echo ""
  echo "## Détail par fonctionnalité"
  echo ""
  echo "| Feature | Catégorie | Fonctionnalité | Verdict | Justification |"
  echo "|---|---|---|---|---|"
  echo "$rows" | grep -v '^$'
  echo ""
  echo "## Preuves"
  echo ""
  # Per-feature evidence blocks (recompute compactly).
  while IFS='|' read -r id cat label flag ui logic gap own; do
    [ -z "$id" ] && continue
    ui_hits=0; logic_hits=0
    [ "$ui" != "-" ] && ui_hits=$(hits "$ui" "$DECL")
    [ "$logic" != "-" ] && logic_hits=$(hits "$logic" "$(excl_for "$own")")
    echo "### \`$id\` — $label"
    if [ "$ui" != "-" ]; then
      echo "- UI pattern \`$ui\` → $ui_hits hit(s)"
      snippet "$ui" "$DECL" 2 | sed 's/^/  /'
    fi
    if [ "$logic" != "-" ]; then
      echo "- Logic pattern \`$logic\` (hors déclaration) → $logic_hits hit(s)"
      snippet "$logic" "$(excl_for "$own")" 2 | sed 's/^/  /'
    fi
    if [ "$flag" != "-" ] && [ "$flag" != "CORE" ]; then
      echo "- Flag \`$flag\` → ${FLAG[$flag]:-non audité}"
    fi
    echo ""
  done <<< "$FEATURES"
  echo "## Connu / non terminé (à coller dans les notes de release)"
  echo ""
  echo "> SpaceKai — fonctionnalités non finies ou non vérifiables dans ce snapshot. Une release NE DOIT PAS les présenter comme faites."
  echo ""
  echo '```markdown'
  echo "### Connu / non terminé"
  echo "$gaps" | grep -v '^$'
  echo '```'
  echo ""
  echo "## Contradictions de revendication (changelog vs audit)"
  echo ""
  if [ -n "$contradictions" ]; then
    echo "> ⚠ Des lignes du changelog / RELEASE.md semblent revendiquer une fonctionnalité classée NON terminée :"
    echo "$contradictions" | grep -v '^$'
  else
    echo "Aucune contradiction détectée (lignes négatives — 'non', 'absent', 'à faire'… — ignorées)."
  fi
  echo ""
  echo "_Fin du rapport — régénéré à chaque release par pre-release-report.sh._"
} > "$OUT"

# ---------------------------------------------------------------------------
# 7. Console summary.
# ---------------------------------------------------------------------------
echo "============================================"
echo "FEATURE AUDIT — SpaceKai v${VERSION} (${NOW})"
echo "============================================"
for v in "IMPLEMENTED (static)" "PARTIALLY IMPLEMENTED" "NOT IMPLEMENTED" "BROKEN" "NON VÉRIFIABLE" "OK (gate)"; do
  printf '  %-24s %s\n' "$v" "${VERDICT_COUNT[$v]}"
done
echo ""
echo "Rapport écrit : $OUT"
if [ -n "$contradictions" ]; then
  echo "⚠ Contradictions de revendication détectées — voir le rapport."
fi
echo "============================================"
exit 0
