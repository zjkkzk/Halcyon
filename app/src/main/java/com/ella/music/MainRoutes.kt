package com.ella.music

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE_NEW
import com.ella.music.ui.navigation.Screen

internal fun Intent.resolveShortcutRoute(): String {
    val uri = data
    if (uri != null && uri.scheme in setOf("ella", "halcyon")) {
        val host = uri.host.orEmpty()
        if (host == "search") {
            val keyword = uri.getQueryParameter("keyword")
            return Screen.LibrarySearch.createRoute(
                type = uri.getQueryParameter("type"),
                keyword = keyword,
                focus = keyword.isNullOrBlank()
            )
        }
        if (host == "shortcut") {
            uri.getQueryParameter("route")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        uri.getQueryParameter("route")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return getStringExtra(EXTRA_SHORTCUT_ROUTE)
        ?: getStringExtra(EXTRA_SHORTCUT_ROUTE_NEW)
        ?: ""
}

internal fun String?.toCurrentTabRoute(): String? {
    return when {
        this == null -> null
        this == Screen.Home.route -> Screen.Home.route
        this == Screen.Library.route -> Screen.Library.route
        this.isSearchRoute() -> Screen.LibrarySearch.createRoute()
        this.isTopLevelRoute(Screen.Playlists.baseRoute) -> Screen.Playlists.createRoute(fromDock = true)
        this.isTopLevelRoute(Screen.Folder.baseRoute) -> Screen.Folder.createRoute(fromDock = true)
        this.isTopLevelRoute(Screen.Artist.baseRoute) -> Screen.Artist.createRoute(fromDock = true)
        this.isTopLevelRoute(Screen.Album.baseRoute) -> Screen.Album.createRoute(fromDock = true)
        this == Screen.ScanSettings.route -> Screen.ScanSettings.route
        this == Screen.Settings.route -> Screen.Settings.route
        this == Screen.Analytics.route -> Screen.Analytics.route
        this == Screen.MetadataCategory.createRoute("year") -> Screen.MetadataCategory.createRoute("year")
        this == Screen.MetadataCategory.createRoute("genre") -> Screen.MetadataCategory.createRoute("genre")
        this == Screen.MetadataCategory.createRoute("composer") -> Screen.MetadataCategory.createRoute("composer")
        this == Screen.MetadataCategory.createRoute("lyricist") -> Screen.MetadataCategory.createRoute("lyricist")
        else -> null
    }
}

internal fun String?.isSearchRoute(): Boolean {
    return this?.startsWith(Screen.LibrarySearch.baseRoute) == true ||
        this == Screen.LibrarySearch.route
}

internal fun String?.isBottomDockRoute(): Boolean {
    return when {
        this == null -> false
        this.isSearchRoute() -> true
        this == Screen.Home.route -> true
        this == Screen.Library.route -> true
        this.isTopLevelRoute(Screen.Playlists.baseRoute) -> true
        this.isTopLevelRoute(Screen.Folder.baseRoute) -> true
        this.isTopLevelRoute(Screen.Artist.baseRoute) -> true
        this.isTopLevelRoute(Screen.Album.baseRoute) -> true
        this == Screen.ScanSettings.route -> true
        this == Screen.Settings.route -> true
        this == Screen.Analytics.route -> true
        this == Screen.MetadataCategory.createRoute("year") -> true
        this == Screen.MetadataCategory.createRoute("genre") -> true
        this == Screen.MetadataCategory.createRoute("composer") -> true
        this == Screen.MetadataCategory.createRoute("lyricist") -> true
        else -> false
    }
}

internal fun String?.matchesRoute(route: String): Boolean {
    return when {
        route.startsWith(Screen.LibrarySearch.baseRoute) -> this.isSearchRoute()
        route.isTopLevelRoute(Screen.Playlists.baseRoute) -> this.isTopLevelRoute(Screen.Playlists.baseRoute)
        route.isTopLevelRoute(Screen.Folder.baseRoute) -> this.isTopLevelRoute(Screen.Folder.baseRoute)
        route.isTopLevelRoute(Screen.Artist.baseRoute) -> this.isTopLevelRoute(Screen.Artist.baseRoute)
        route.isTopLevelRoute(Screen.Album.baseRoute) -> this.isTopLevelRoute(Screen.Album.baseRoute)
        else -> this == route
    }
}

private fun String?.isTopLevelRoute(baseRoute: String): Boolean =
    this == baseRoute || this?.startsWith("$baseRoute?") == true

internal fun String.isMusicSymbolOnly(): Boolean {
    val content = trim()
    if (content.isBlank()) return true

    return content.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
    }
}

internal fun Uri.toPrimaryStoragePath(): String? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val parts = documentId.split(':', limit = 2)
    val volume = parts.firstOrNull().orEmpty()
    val path = parts.getOrNull(1).orEmpty().trim('/')
    return when {
        volume.equals("primary", ignoreCase = true) && path.isBlank() -> "/storage/emulated/0"
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$path"
        else -> null
    }
}
