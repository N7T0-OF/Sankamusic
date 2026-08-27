#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# ci-watch.sh — Sonder un workflow GitHub Actions jusqu'à sa conclusion
# et nommer le step qui a échoué.
#
# Usage :
#   scripts/ci-watch.sh [RUN_ID] [BRANCHE]
#     RUN_ID  : id du run Actions (défaut : dernier run de la branche)
#     BRANCHE : branche à interroger quand RUN_ID est omis (défaut : main)
#   scripts/ci-watch.sh --run-latest main
#
# Prérequis : curl + python3. Aucun jq requis (le JSON est parsé en python).
# Fonctionne SANS token sur un repo public : l'API GET des runs/jobs est
# publique. Les LOGS bruts (`jobs/{id}/logs`) exigent les droits admin
# (403), ce script ne les télécharge donc pas — il nomme le step fautif
# via les métadonnées des jobs, pas leur sortie.
#
# Sortie :
#   - statut du workflow (queued → in_progress → completed conclusion)
#   - la liste des steps du job, avec la conclusion de chacun
#   - le(s) step(s) en échec mis en évidence (si conclusion=failure)
#
# ⚠️ Les job-logs bruts restent 403 sans token d'admin : pour la CAUSE de
#    l'échec Gradle, il faut lire le log (token Actions:read) ou reproduire
#    localement. Ce script fournit le run-id et le job-id à réutiliser.
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail

REPO="${REPO:-N7T0-OF/Sankamusic}"
API="https://api.github.com/repos/$REPO"
UA="User-Agent: ci-watch"

# ── args ────────────────────────────────────────────────────────────────
RUN_ID="${1:-}"
BRANCH="${2:-main}"
POLL_EVERY="${CI_WATCH_POLL_EVERY:-10}"   # secondes entre deux sondages
MAX_WAIT="${CI_WATCH_MAX_WAIT:-900}"      # 15 min max

# Interpréteur python fonctionnel, et curl requis — détectés AVANT tout usage.
command -v curl >/dev/null 2>&1 || { echo "ERREUR : curl requis." >&2; exit 1; }

# Détecte un interpréteur python FONCTIONNEL. Sur Windows, le stub
# "python3" du Microsoft Store répond `command -v` mais ne s'exécute pas ;
# on préfère donc `python`, et on vérifie que le candidat répond réellement.
pick_python() {
  for cand in python python3; do
    if command -v "$cand" >/dev/null 2>&1 && "$cand" -c 'import sys; print(1)' >/dev/null 2>&1; then
      echo "$cand"; return 0
    fi
  done
  return 1
}

# PY est initialisé ici (avant fetch_run_id qui en dépend).
PY="$(pick_python || true)"
[ -n "$PY" ] || { echo "ERREUR : python3/python requis pour parser le JSON." >&2; exit 1; }

fetch_run_id() { # $1=branche → imprime le plus récent run de la branche
  curl -s -H "$UA" "$API/actions/runs?branch=$1&per_page=1" | "$PY" -c '
import sys, json
d = json.load(sys.stdin)
runs = d.get("workflow_runs", [])
if not runs:
    print(""); sys.exit(1)
r = runs[0]
print(r["id"]); sys.stderr.write(f"run {r['"'"'id'"'"']} head {r['"'"'head_sha'"'"'][:7]} {r['"'"'status'"'"']} {r['"'"'conclusion'"'"']}\n")
'
}

if [ -z "$RUN_ID" ]; then
  echo "== Dernier run de '$BRANCH' =="
  RUN_ID="$(fetch_run_id "$BRANCH")"
  [ -n "$RUN_ID" ] || { echo "ERREUR : aucun run trouvé sur la branche '$BRANCH'." >&2; exit 1; }
  echo "run-id = $RUN_ID"
fi

# ── sonde ───────────────────────────────────────────────────────────────
elapsed=0
status="queued"
while [ "$status" = "queued" ] || [ "$status" = "in_progress" ]; do
  data="$(curl -s -H "$UA" "$API/actions/runs/$RUN_ID")"
  status="$("$PY" -c 'import sys,json; print(json.load(sys.stdin).get("status",""))' <<<"$data" 2>/dev/null || true)"
  conclusion="$("$PY" -c 'import sys,json; print(json.load(sys.stdin).get("conclusion",""))' <<<"$data" 2>/dev/null || true)"
  echo "[$elapsed s] statut=$status conclusion=$conclusion"
  if [ "$((elapsed + POLL_EVERY))" -ge "$MAX_WAIT" ] && { [ "$status" = "queued" ] || [ "$status" = "in_progress" ]; }; then
    echo "ERREUR : délai max ($MAX_WAIT s) atteint, run toujours '$status'." >&2
    exit 2
  fi
  if [ "$status" = "queued" ] || [ "$status" = "in_progress" ]; then
    sleep "$POLL_EVERY"
    elapsed=$((elapsed + POLL_EVERY))
  fi
done

echo ""
echo "== Résultat workflow : $status / $conclusion (run $RUN_ID) =="
echo ""

# ── nommer le step fautif ───────────────────────────────────────────────
# Le fetch est fait DANS python (urllib) via $JOB_API_URL : évite le conflit
# pipe+heredoc qui viderait le stdin de JSON.
export JOB_API_URL="$API/actions/runs/$RUN_ID/jobs"
"$PY" - <<'PY'
import os, json, urllib.request
url = os.environ["JOB_API_URL"]
req = urllib.request.Request(url, headers={"User-Agent": "ci-watch"})
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        d = json.load(r)
except urllib.error.HTTPError as e:
    print(f"(ERREUR HTTP {e.code} : {e.reason})")
    raise SystemExit(1)
jobs = d.get("jobs", [])
if not jobs:
    print("(aucun job interrogé — workflow peut être encore en marche ou vide)")
    raise SystemExit(0)
failed = []
for j in jobs:
    print(f"JOB: {j.get('name')} | {j.get('status')} | {j.get('conclusion')}")
    for s in j.get("steps", []):
        concl = s.get("conclusion")
        flag = "  <<-- ÉCHEC" if concl == "failure" else ""
        if concl == "failure":
            failed.append(s.get("name"))
        print(f"   step: {s.get('name')} -> {concl}{flag}")
        started, done = s.get("started_at"), s.get("completed_at")
        if started or done:
            print(f"          started: {started}  completed: {done}")
print()
if failed:
    print("STEP(S) EN ÉCHEC :")
    for f in failed:
        print("  - " + f)
    print()
    print("job-id le plus pertinent à réinterroger : " + str(jobs[0].get("id")))
    print("NOTE : les job-logs bruts sont 403 sans token admin. Pour la CAUSE,")
    print("      voir via un token (Actions:read) ou reproduire localement.")
else:
    print("Aucun step en échec : le workflow est vert (ou sans job échoué).")
PY