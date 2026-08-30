# Release notes template

Use this structure for every SpaceKai release. Replace `vX.Y.Z` with the
actual version. The CI release job writes its own auto-generated notes when no
fastlane changelog exists — this template is the target format for hand-written
notes (via `scripts/generate-fastlane-changelog.sh` then editing).

This is the format actually used by the v1.7.5 and v1.7.6 releases — keep the
sections in this order so users always find the same information.

```markdown
# SpaceKai vX.Y.Z

## 🐛 Fix majeur (si applicable — sinon supprimer cette section)

Une phrase sur ce que cette release corrige pour l'utilisateur final, en
français simple. Exemples réels :
- « La mise à jour fonctionne depuis TOUTES les versions » (fix downgrade,
  v1.7.6)
- « Le tag n'est plus verrouillé par GitHub » (fix immutable releases)

## 📱 Un seul APK universel

APK release signé (v1 + v2), universel — installable sur tous les appareils
Android (arm64-v8a, armeabi-v7a, x86_64).

- `SpaceKai-vX.Y.Z.apk` — APK universel signé, installable directement
- `SHA256SUMS.txt` — vérification d'intégrité

## ✨ Nouveautés

- 🎵 ...
- 🎨 ...
- ⚙️ ...
- 🚀 ...

## 🔧 Corrections

- 🐛 ...
- 🔧 ...

## 📦 Installation

#### Android

Télécharger **`SpaceKai-vX.Y.Z.apk`** — s'installe en mise à jour depuis
n'importe quelle version antérieure (versionCode `NN` > plus grand jamais
publié, `66`).

#### Desktop

- **Windows** : installeur `SpaceKai-vX.Y.Z-windows-installer.zip`
- **macOS** : `SpaceKai-vX.Y.Z-mac-arm64.dmg`
- **Linux** : `SpaceKai-vX.Y.Z-linux-x86_64.AppImage`

> ℹ️ Les paquets desktop sont publiés quand la clé de signature Conveyor est
> configurée sur le repo.

## 🔐 Vérification

SHA-256 disponible dans **`SHA256SUMS.txt`** :

```
<sha256>  SpaceKai-vX.Y.Z.apk
```

## ⚠️ Notes

- L'update checker de l'application pointe vers `N7T0-OF/Sankamusic` — la mise
  à jour sera proposée aux utilisateurs.
- Icônes verrouillées (`circle_app_icon.png`, `app_icon.png`) : inchangées,
  SHA-256 validés.
```

## Assets attendus

Une release publique contient **uniquement** :

```
SpaceKai-vX.Y.Z.apk                        # Android (universel, signé)
SpaceKai-vX.Y.Z-windows-installer.zip      # Windows (install.bat + msix + cert)
SpaceKai-vX.Y.Z-mac-arm64.dmg / -mac-x64.dmg  # macOS
SpaceKai-vX.Y.Z-linux-x86_64.AppImage      # Linux
SHA256SUMS.txt                             # checksums
```

Jamais de `*-debug.apk`, `*-unsigned.apk`, `*-arm64-v8a.apk`,
`*-armeabi-v7a.apk`, `*-x86_64.apk` ni de variantes foss/full dans les assets
publics.

## Règles de release (rappel — voir RELEASE.md pour le détail)

- **Tag** : `vX.Y.Z` — ne jamais réutiliser un tag d'une release supprimée
  (GitHub le brûle définitivement) ; un tag brûlé → `vX.Y.Z-1`.
- **versionCode** : doit battre le plus grand jamais publié (`66`, v1.1.6) —
  prochaine release = `68`. Un code plus bas → « Application non installée »
  (downgrade refusé par Android).
- **Pattern sûr** : draft → assets → publish. Jamais l'inverse.
- **Icônes** : `circle_app_icon.png` / `app_icon.png` verrouillées (ne jamais
  optimiser, compresser, redimensionner, recolorer, régénérer, remplacer).
