package com.sankamusic.core.api

/**
 * Permissions déclarables par un plugin (voir docs/PLUGIN_SYSTEM.md § 4 et
 * docs/SECURITY.md § 2). Un plugin n'a JAMAIS accès à tout par défaut :
 * l'utilisateur voit les permissions demandées avant activation et peut les révoquer.
 */
object Permissions {
    const val PLAYER_READ = "player.read"
    const val PLAYER_CONTROL = "player.control"

    const val LIBRARY_READ = "library.read"

    const val PLAYLIST_READ = "playlist.read"
    const val PLAYLIST_WRITE = "playlist.write"

    const val DOWNLOAD_READ = "download.read"
    const val DOWNLOAD_WRITE = "download.write"

    const val THEME_MODIFY = "theme.modify"
    const val NAVIGATION_MODIFY = "navigation.modify"

    const val NETWORK = "network"
    const val STORAGE = "storage"

    /** Toutes les permissions connues — sert à la validation du manifest. */
    val ALL: Set<String> = setOf(
        PLAYER_READ, PLAYER_CONTROL,
        LIBRARY_READ,
        PLAYLIST_READ, PLAYLIST_WRITE,
        DOWNLOAD_READ, DOWNLOAD_WRITE,
        THEME_MODIFY, NAVIGATION_MODIFY,
        NETWORK, STORAGE,
    )
}
