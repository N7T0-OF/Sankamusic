# Sécurité — Sankamusic

- **Statut** : 🟡 Squelette — à compléter (limites réelles Android à confirmer)
- **Document lié** : `docs/PLUGIN_SYSTEM.md`, `docs/UPDATE_SYSTEM.md`, `docs/BUILD_SYSTEM.md`

## 1. Objectif

Un plugin ne doit pas pouvoir :
- accéder arbitrairement aux données privées ;
- récupérer les tokens d'autres plugins ;
- exécuter n'importe quel code sans permission.

La sécurité doit être pensée **dès la conception**, pas ajoutée après coup.

## 2. Modèle de permissions

- Chaque plugin déclare ses permissions dans son manifest (voir `PLUGIN_SYSTEM.md` § 3-4).
- L'utilisateur voit les permissions **avant** activation et peut les révoquer.
- L'API refuse toute action hors permission déclarée.

## 3. Tokens et secrets

- 🚫 **Jamais** de token dans le code source, le manifest, ni commité.
- Stockage sécurisé : Android Keystore / chiffrement, isolation par plugin.
- Un plugin ne peut pas lire les tokens d'un autre plugin.

## 4. Sandbox et isolation

- Toute exception plugin est capturée ; un plugin qui plante est **désactivé
  automatiquement** sans casser l'app (voir `PLUGIN_SYSTEM.md` § 6).
- Limites réelles du sandbox Android (exécution de code arbitraire, classes dynamiques,
  réflexion) : **à documenter ici après prototype**. Ne rien promettre d'impossible.

## 5. Intégrité des mises à jour

- Chaque artefact publié est accompagné de `SHA256SUMS.txt` (voir `RELEASE_GUIDE.md`).
- L'app vérifie le SHA-256 **avant** d'installer une mise à jour.
- Échec de vérification → abandon de l'installation, aucun effet de bord.

## 6. Sécurité de la chaîne de build

- Keystore de signature **jamais** commité ; géré via les secrets du CI.
- Artefacts signés uniquement, jamais de `-unsigned` en release (le CI échoue sinon).
- Sources de mise à jour : HTTPS uniquement, origines vérifiées.

## 7. Checklist de sécurité (à compléter)

- [ ] Aucun secret dans le repository (scan automatique au CI)
- [ ] Permissions plugin affichées et révocables
- [ ] Tokens isolés (Android Keystore)
- [ ] SHA-256 vérifié avant installation
- [ ] Crash d'un plugin ne fait pas planter l'app
- [ ] Pas de code non signé exécutable en release
