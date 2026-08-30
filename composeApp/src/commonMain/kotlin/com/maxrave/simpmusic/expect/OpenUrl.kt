package com.maxrave.simpmusic.expect

expect fun openUrl(url: String)

// SPACEKAI FEATURE: install the given release APK in-app. On Android this downloads the
// file and hands it to the system package installer (ACTION_VIEW via FileProvider), so the
// user never has to leave the app or manually uninstall. Elsewhere it falls back to the browser.
expect fun installApk(url: String?)

expect fun shareUrl(
    title: String,
    url: String,
)