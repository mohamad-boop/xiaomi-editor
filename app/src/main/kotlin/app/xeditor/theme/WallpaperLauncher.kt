package app.xeditor.theme

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the HyperOS super wallpaper list (where Saturn / "Faraway Rings" lives).
 * SuperWallpaperListPickerActivity is registered for `theme://zhuti.xiaomi.com/superwallpaperlist`.
 * The per-wallpaper detail activity is `exported=false`, so this is the deepest
 * link we can reach — user taps Saturn from the list.
 */
object WallpaperLauncher {
    private val SUPER_WALLPAPER_LIST: Uri =
        Uri.parse("theme://zhuti.xiaomi.com/superwallpaperlist")

    fun open(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, SUPER_WALLPAPER_LIST)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
