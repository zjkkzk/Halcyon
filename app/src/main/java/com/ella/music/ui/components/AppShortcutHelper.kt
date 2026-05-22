package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen

private const val SHORTCUT_LIBRARY = "library"
private const val SHORTCUT_PLAYLISTS = "playlists"
private const val SHORTCUT_FOLDER = "folder"
private const val SHORTCUT_SETTINGS = "settings"

fun updateEllaDynamicShortcuts(
    context: Context,
    libraryLabel: String,
    playlistsLabel: String,
    folderLabel: String
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
    val shortcuts = listOf(
        context.buildEllaShortcut(
            id = SHORTCUT_LIBRARY,
            label = libraryLabel.ifBlank { "音乐库" },
            route = Screen.Library.route,
            iconRes = R.drawable.ic_shortcut_library,
            rank = 0
        ),
        context.buildEllaShortcut(
            id = SHORTCUT_PLAYLISTS,
            label = playlistsLabel.ifBlank { "歌单" },
            route = Screen.Playlists.route,
            iconRes = R.drawable.ic_shortcut_playlist,
            rank = 1
        ),
        context.buildEllaShortcut(
            id = SHORTCUT_FOLDER,
            label = folderLabel.ifBlank { "文件夹" },
            route = Screen.Folder.route,
            iconRes = R.drawable.ic_shortcut_folder,
            rank = 2
        )
    )
    runCatching { shortcutManager.removeDynamicShortcuts(listOf(SHORTCUT_SETTINGS)) }
    runCatching { shortcutManager.disableShortcuts(listOf(SHORTCUT_SETTINGS), "已移除") }
    runCatching { shortcutManager.dynamicShortcuts = shortcuts }
    runCatching { shortcutManager.updateShortcuts(shortcuts) }
}

fun requestPinnedEllaShortcut(
    context: Context,
    id: String,
    label: String,
    route: String
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!shortcutManager.isRequestPinShortcutSupported) return false
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(EXTRA_SHORTCUT_ROUTE, route)
    }
    val shortcut = ShortcutInfo.Builder(context, id.toShortcutId())
        .setShortLabel(label.take(10).ifBlank { "Ella Music" })
        .setLongLabel(label.ifBlank { "Ella Music" })
        .setIcon(Icon.createWithResource(context, shortcutIconForRoute(route)))
        .setIntent(intent)
        .build()
    shortcutManager.requestPinShortcut(shortcut, null)
    return true
}

private fun String.toShortcutId(): String =
    "ella_${replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)}"

private fun Context.buildEllaShortcut(
    id: String,
    label: String,
    route: String,
    iconRes: Int,
    rank: Int
): ShortcutInfo {
    return ShortcutInfo.Builder(this, id)
        .setShortLabel(label.take(10).ifBlank { "Ella Music" })
        .setLongLabel(label.ifBlank { "Ella Music" })
        .setIcon(Icon.createWithResource(this, iconRes))
        .setIntent(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ROUTE, route)
            }
        )
        .setRank(rank)
        .build()
}

private fun shortcutIconForRoute(route: String): Int = when (route) {
    Screen.Library.route -> R.drawable.ic_shortcut_library
    Screen.Playlists.route -> R.drawable.ic_shortcut_playlist
    Screen.Folder.route -> R.drawable.ic_shortcut_folder
    else -> R.drawable.ic_music_note
}
