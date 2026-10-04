package app.xeditor.keeper

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.xeditor.R
import app.xeditor.shizuku.ShizukuShell
import app.xeditor.theme.ThemeApplier
import java.io.File
import java.io.InputStream

/**
 * Keeps a chosen .mtz applied. HyperOS reverts imported themes (most visibly
 * after a reboot), so after every boot we hand the saved theme back to the
 * theme manager. Launching it from the background needs "Display over other
 * apps" / MIUI's "Display pop-up windows while running in the background";
 * without them we post a notification that re-applies on tap.
 */
object Keeper {
    private const val PREFS = "keeper"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NAME = "name"
    private const val KEY_PUBLISHED = "published"
    private const val KEY_LAST = "last_reapply"
    private const val CHANNEL = "keeper"
    private const val NOTIFICATION_ID = 7
    private const val BOOT_DELAY_MS = 45_000L
    /** The keeper's copy in Download/XiaomiEditor/temp, kept while the keeper is on. */
    const val KEEPER_COPY = "keeper.mtz"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun stored(ctx: Context) = File(ctx.filesDir, "keeper/theme.mtz")

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_ENABLED, on).apply()
    fun themeName(ctx: Context): String? = prefs(ctx).getString(KEY_NAME, null)?.takeIf { stored(ctx).exists() }
    fun lastReapply(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST, 0L)

    /** [published] is an existing Downloads copy of the same file, if the caller already made one. */
    fun setTheme(ctx: Context, name: String, input: InputStream, published: String? = null) {
        val f = stored(ctx)
        f.parentFile?.mkdirs()
        f.outputStream().use { input.copyTo(it) }
        prefs(ctx).edit().putString(KEY_NAME, name).putString(KEY_PUBLISHED, published).apply()
    }

    /** Path the theme manager can read; re-published if the Downloads copy vanished. */
    fun ensurePublished(ctx: Context): String? {
        val src = stored(ctx).takeIf { it.exists() } ?: return null
        prefs(ctx).getString(KEY_PUBLISHED, null)?.let { if (File(it).exists()) return it }
        val name = (prefs(ctx).getString(KEY_NAME, null) ?: "theme").let {
            if (it.endsWith(".mtz", true)) it else "$it.mtz"
        }
        val tmp = File(ctx.cacheDir, "mtz/$name").apply { parentFile?.mkdirs() }
        src.copyTo(tmp, overwrite = true)
        val published = ThemeApplier.publishTemp(ctx, tmp, KEEPER_COPY).absolutePath
        prefs(ctx).edit().putString(KEY_PUBLISHED, published).apply()
        return published
    }

    enum class Outcome { LAUNCHED, OPENED_THEMES, NOTIFIED, NO_THEME, NO_THEME_MANAGER }

    fun reapply(ctx: Context, fromBackground: Boolean): Outcome {
        val path = ensurePublished(ctx) ?: return Outcome.NO_THEME
        if (!ThemeApplier.hasThemeManager(ctx)) {
            // HyperOS 3 has no "apply this file" entry point; applying happens in the
            // Themes app itself, so point the user there.
            val themes = ctx.packageManager.getLaunchIntentForPackage("com.android.thememanager")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return Outcome.NO_THEME_MANAGER
            prefs(ctx).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            if (!fromBackground && runCatching { ctx.startActivity(themes) }.isSuccess) return Outcome.OPENED_THEMES
            notifyReapply(ctx, themes)
            return Outcome.NOTIFIED
        }
        prefs(ctx).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
        val intent = ThemeApplier.applyIntent(path)
        if (!fromBackground) {
            if (runCatching { ctx.startActivity(intent) }.isSuccess) return Outcome.LAUNCHED
            notifyReapply(ctx, intent)
            return Outcome.NOTIFIED
        }
        // Shizuku's shell user may start activities from the background; fall back to our own launch.
        val launched = launchViaShizuku(ctx, path) ||
            Settings.canDrawOverlays(ctx) && runCatching { ctx.startActivity(intent) }.isSuccess
        // From the background we can't observe whether MIUI silently swallowed the
        // launch, so always leave a tap-to-apply notification as a safety net.
        notifyReapply(ctx, intent)
        return if (launched) Outcome.LAUNCHED else Outcome.NOTIFIED
    }

    private fun launchViaShizuku(ctx: Context, path: String): Boolean = runCatching {
        if (!ShizukuShell.isReady(ctx)) return false
        ShizukuShell.run(
            "am start -n com.android.thememanager/.ApplyThemeForScreenshot -f 0x14000000 " +
                "--es api_called_from com.android.thememanager --es theme_file_path ${ShizukuShell.q(path)}",
        ).ok
    }.getOrDefault(false)

    private fun notifyReapply(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Theme keeper", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Re-applies your custom theme after the system resets it"
            }
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_keeper)
            .setContentTitle("Re-apply ${themeName(ctx) ?: "your theme"}")
            .setContentText("Tap if HyperOS reset your theme — then apply it again from My themes")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n) }
        }
    }

    /** Waits for the theme manager to finish its own boot-time reset before re-applying. */
    fun scheduleAfterBoot(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, ReapplyReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + BOOT_DELAY_MS,
            pi,
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Keeper.isEnabled(context) && Keeper.themeName(context) != null) {
            Keeper.scheduleAfterBoot(context)
        }
    }
}

class ReapplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Keeper.isEnabled(context)) return
        val pending = goAsync()
        Thread {
            try {
                Keeper.reapply(context, fromBackground = true)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
