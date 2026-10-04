package app.xeditor.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.xeditor.iconpack.ComponentKey

enum class AppKind { LAUNCHER, SHARE_TARGET }

data class InstalledApp(
    val key: ComponentKey,
    val label: String,
    val kind: AppKind = AppKind.LAUNCHER,
)

object InstalledApps {
    fun query(context: Context): List<InstalledApp> {
        val pm = context.packageManager

        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL,
        ).mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            InstalledApp(
                key = ComponentKey(ai.packageName, ai.name),
                label = ri.loadLabel(pm).toString(),
                kind = AppKind.LAUNCHER,
            )
        }

        val shareTargets = listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
            .flatMap { action ->
                pm.queryIntentActivities(
                    Intent(action).setType("*/*"),
                    PackageManager.MATCH_ALL,
                )
            }
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                InstalledApp(
                    key = ComponentKey(ai.packageName, ai.name),
                    label = ri.loadLabel(pm).toString(),
                    kind = AppKind.SHARE_TARGET,
                )
            }

        // Launchers win on duplicate ComponentKey — they own the package's main icon.
        val byKey = LinkedHashMap<ComponentKey, InstalledApp>()
        launchers.forEach { byKey[it.key] = it }
        shareTargets.forEach { byKey.putIfAbsent(it.key, it) }

        return byKey.values.sortedBy { it.label.lowercase() }
    }
}
