package com.sankamusic.core.update

/**
 * Installation d'un APK téléchargé (docs/UPDATE_SYSTEM.md § 5).
 *
 * L'implémentation Android (`ACTION_INSTALL_PACKAGE` / `PackageInstaller`,
 * consentement utilisateur, données préservées) appartient au module `:app`
 * et sort du périmètre JVM vérifiable — TODO(Phase 4).
 *
 * Règle absolue : [UpdateEngine.verifyDownloadedApk] (SHA-256) doit réussir
 * AVANT tout appel à [install] ; un échec d'intégrité refuse l'installation.
 */
interface ApkInstaller {
    suspend fun install(apkBytes: ByteArray): Result<Unit>
}
