package app.xeditor.iconpack

import android.content.Context
import android.content.Intent

data class InstalledIconPack(val packageName: String, val label: String)

object IconPackDiscovery {
    private val PACK_ACTIONS = listOf(
        "com.novalauncher.THEMES",
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "net.oneplus.launcher.icons.ACTION_PICK_ICON",
    )

    fun listInstalled(context: Context): List<InstalledIconPack> {
        val pm = context.packageManager
        val packs = LinkedHashMap<String, String>()
        for (action in PACK_ACTIONS) {
            val results = pm.queryIntentActivities(Intent(action), 0)
            for (ri in results) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (packs.containsKey(pkg)) continue
                packs[pkg] = ri.loadLabel(pm).toString()
            }
        }
        return packs.entries
            .map { InstalledIconPack(it.key, it.value) }
            .sortedBy { it.label.lowercase() }
    }
}
