package app.xeditor.keeper

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class SetupStep(val title: String, val why: String) {
    NOTIFICATIONS(
        "Notifications",
        "Lets the keeper post a tap-to-re-apply notice when it can't open the theme manager itself.",
    ),
    BATTERY(
        "Battery optimization",
        "Turn it off so the system doesn't stop the keeper before it re-applies your theme.",
    ),
    OVERLAY(
        "Display over other apps",
        "Lets the keeper open the theme manager straight after boot.",
    ),
    MIUI_POPUP(
        "Pop-up windows in background",
        "The MIUI/HyperOS switch under Other permissions. It's needed on top of the one above.",
    ),
    AUTOSTART(
        "Autostart",
        "Without it, HyperOS never tells the keeper that the phone finished booting.",
    ),
}

/** true = done, false = still to do, null = can't be detected on this device. */
object SetupChecks {
    private const val OP_AUTO_START = 10008
    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    fun status(ctx: Context, step: SetupStep): Boolean? = when (step) {
        SetupStep.NOTIFICATIONS -> NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        SetupStep.BATTERY -> ctx.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(ctx.packageName)
        SetupStep.OVERLAY -> Settings.canDrawOverlays(ctx)
        SetupStep.MIUI_POPUP -> miuiOp(ctx, OP_BACKGROUND_START_ACTIVITY)
        SetupStep.AUTOSTART -> miuiOp(ctx, OP_AUTO_START)
    }

    fun visibleSteps(ctx: Context): List<SetupStep> =
        if (isMiui(ctx)) SetupStep.entries else SetupStep.entries - SetupStep.MIUI_POPUP - SetupStep.AUTOSTART

    fun isMiui(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo("com.miui.securitycenter", 0); true
    }.getOrDefault(false)

    /** MIUI keeps its extra permissions as private AppOps codes; read them reflectively. */
    private fun miuiOp(ctx: Context, op: Int): Boolean? = runCatching {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val m = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
        )
        (m.invoke(ops, op, ctx.applicationInfo.uid, ctx.packageName) as Int) == AppOpsManager.MODE_ALLOWED
    }.getOrNull()

    /** Settings screen for [step]; the first intent that resolves wins. */
    fun intents(ctx: Context, step: SetupStep): List<Intent> {
        val pkg = ctx.packageName
        val pkgUri = Uri.parse("package:$pkg")
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        return when (step) {
            SetupStep.NOTIFICATIONS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                details,
            )
            SetupStep.BATTERY -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            SetupStep.OVERLAY -> listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri), details)
            SetupStep.MIUI_POPUP -> listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", pkg),
                Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),
                details,
            )
            SetupStep.AUTOSTART -> listOf(
                Intent().setComponent(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                ),
                details,
            )
        }.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    /**
     * Grants every checklist permission at once through Shizuku. MIUI's own
     * switches (autostart, background pop-ups) are app-ops only some builds let the
     * shell set, so those are best effort.
     */
    fun grantAllWithShizuku(ctx: Context): List<SetupStep> {
        val pkg = ctx.packageName
        val cmds = mapOf(
            SetupStep.NOTIFICATIONS to "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            SetupStep.BATTERY to "dumpsys deviceidle whitelist +$pkg",
            SetupStep.OVERLAY to "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            SetupStep.MIUI_POPUP to "appops set $pkg $OP_BACKGROUND_START_ACTIVITY allow",
            SetupStep.AUTOSTART to "appops set $pkg $OP_AUTO_START allow",
        )
        return cmds.filter { (_, cmd) -> !app.xeditor.shizuku.ShizukuShell.run(cmd).ok }.keys.toList()
    }

    fun open(ctx: Context, step: SetupStep) {
        for (intent in intents(ctx, step)) {
            if (runCatching { ctx.startActivity(intent) }.isSuccess) return
        }
    }
}
