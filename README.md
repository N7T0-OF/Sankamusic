# Sankamusic

Nouveau projet **indépendant** de lecteur de musique (Android + desktop), conçu comme une
plateforme extensible — plugins, thèmes, extensions — dans l'esprit de
BetterDiscord / Spicetify / Vencord, avec **SimpMusic** comme base upstream de compatibilité.

> ⚠️ **Projet en phase de conception.** Aucun code applicatif n'a encore été écrit.
> L'ordre est : analyse → documentation → validation de l'architecture → implémentation.

## Documents maîtres

| Document | Rôle |
|----------|------|
| `RELEASE_GUIDE.md` | Procédure de release : **un seul artefact par plateforme**, validation CI réelle, checklist 100 % |
| `REPO_SETUP.md` | Mise en place du repo GitHub (identité, push, description/topics, licence, secrets, release v0.1.0) |
| `ARCHITECTURE_DECISIONS.md` | Décisions d'architecture enregistrées (ADR) — à lire avant tout choix |
| `CHANGELOG.md` | Journal des versions publiées |
| `docs/` | Analyse et conception (architecture, plugins, thèmes, upstream, update, build, sécurité, migration, roadmap) |

## Rôles des dossiers de référence

| Dossier | Rôle |
|---------|------|
| `Sankamusic` | 🟢 **Projet à construire** (ici) — seul dossier modifiable |
| `SimpMusic` | 🔵 Upstream / base de compatibilité — lecture/analyse uniquement |
| `BetterDiscord` | 🟣 Référence conceptuelle plugins/themes/extensions — lecture/analyse uniquement |
| `SpaceKai-OLD` | 🟠 Ancien projet — source de fonctionnalités à récupérer — lecture/analyse uniquement |

## Principe fondamental

- Sankamusic doit évoluer **indépendamment** de SimpMusic.
- SimpMusic doit pouvoir évoluer **sans casser** Sankamusic (couche `UpstreamAdapter`).
- Les plugins SpaceKai doivent évoluer **indépendamment** du Core.
- Aucune release sans vérification réelle par le CI et re-vérification depuis GitHub (voir `RELEASE_GUIDE.md`).
