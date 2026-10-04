package app.xeditor.theme

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.xeditor.util.Downloads
import java.io.File

sealed class ApplyResult {
    data class Launched(val via: String, val publishedAt: File?) : ApplyResult()
    data class NoHandler(val exportedTo: File) : ApplyResult()
}

private const val THEME_MANAGER_PKG = "com.android.thememanager"
// Reverse-engineered from MIUI Theme Tester (com.arteneta.miuithemetester).
// Their HomeActivity invokes this exact component with `theme_file_path` pointing
// to an absolute filesystem path (NOT a content:// URI).
private const val APPLY_FOR_SCREENSHOT = "com.android.thememanager.ApplyThemeForScreenshot"

object ThemeApplier {

    /** Intent that makes the system theme manager apply the .mtz at [path]. */
    fun applyIntent(path: String): Intent = Intent().apply {
        component = ComponentName(THEME_MANAGER_PKG, APPLY_FOR_SCREENSHOT)
        putExtra("api_called_from", THEME_MANAGER_PKG)
        putExtra("theme_file_path", path)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun hasThemeManager(context: Context): Boolean =
        context.packageManager.resolveActivity(applyIntent("/"), 0) != null

    /**
     * The thememanager process needs filesystem-readable access to the .mtz, so
     * the file goes to Downloads first and the theme manager gets that path.
     */
    fun publish(context: Context, mtz: File): File =
        Downloads.save(context, mtz, mtz.name, replace = true)

    /** A short-lived copy for the theme manager to read while applying; see [Downloads.cleanTemp]. */
    fun publishTemp(context: Context, mtz: File, name: String): File =
        Downloads.save(context, mtz, name, subfolder = Downloads.TEMP_DIR, replace = true)

    /** Opens the theme manager on [published] (the Downloads copy of [mtz]). */
    fun launch(context: Context, mtz: File, published: File): ApplyResult {
        if (hasThemeManager(context)) {
            runCatching {
                context.startActivity(applyIntent(published.absolutePath))
                return ApplyResult.Launched("theme manager", published)
            }
        }

        // Fallback: chooser via FileProvider URI. MTZ Tester and similar pick this up.
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", mtz)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context.packageManager.resolveActivity(viewIntent, 0) != null) {
            context.startActivity(
                Intent.createChooser(viewIntent, "Apply theme").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return ApplyResult.Launched("chooser", published)
        }
        return ApplyResult.NoHandler(published)
    }
}
