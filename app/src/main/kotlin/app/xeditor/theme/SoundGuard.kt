package app.xeditor.theme

import android.content.Context
import android.provider.Settings
import app.xeditor.shizuku.ShizukuShell
import org.json.JSONObject

/**
 * Applying a full theme makes the theme manager reset sound settings the theme
 * doesn't carry (ringtones, and on some phones Dolby / sound effects). Snapshot
 * them right before applying and put back anything the apply changed.
 *
 * Ringtone keys can be restored with "Modify system settings"; the rest only
 * through Shizuku's shell user.
 */
object SoundGuard {
    private const val PREFS = "sound_guard"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val KEY_TAKEN = "taken_at"
    /** Restores stop being attempted this long after an apply. */
    const val WINDOW_MS = 10 * 60_000L

    private val RINGTONE_KEYS = listOf(Settings.System.RINGTONE, Settings.System.NOTIFICATION_SOUND, Settings.System.ALARM_ALERT)
    private val SOUND_PATTERN = Regex("dolby|misound|dts|audio_?effect|sound_?effect|surround|spatial", RegexOption.IGNORE_CASE)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** "namespace/key" -> value for every sound setting we can see. */
    private fun read(ctx: Context): Map<String, String?> {
        val out = linkedMapOf<String, String?>()
        RINGTONE_KEYS.forEach { out["system/$it"] = runCatching { Settings.System.getString(ctx.contentResolver, it) }.getOrNull() }
        if (ShizukuShell.isReady(ctx)) {
            for (ns in listOf("system", "secure", "global")) {
                val r = runCatching { ShizukuShell.run("settings list $ns") }.getOrNull() ?: continue
                if (!r.ok) continue
                r.text.lineSequence().forEach { line ->
                    val k = line.substringBefore('=', "")
                    if (k.isNotEmpty() && SOUND_PATTERN.containsMatchIn(k)) out["$ns/$k"] = line.substringAfter('=')
                }
            }
        }
        return out
    }

    fun snapshot(ctx: Context) {
        val o = JSONObject()
        read(ctx).forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        prefs(ctx).edit().putString(KEY_SNAPSHOT, o.toString()).putLong(KEY_TAKEN, System.currentTimeMillis()).apply()
    }

    /** True while a recent apply might still change sound settings. */
    fun active(ctx: Context) = System.currentTimeMillis() - prefs(ctx).getLong(KEY_TAKEN, 0) < WINDOW_MS

    /**
     * Puts back every setting that changed since [snapshot]. Returns the names that
     * were restored and the ones that changed but couldn't be (missing permission).
     */
    fun restore(ctx: Context): Pair<List<String>, List<String>> {
        val saved = prefs(ctx).getString(KEY_SNAPSHOT, null)?.let { JSONObject(it) } ?: return emptyList<String>() to emptyList()
        val now = read(ctx)
        val restored = mutableListOf<String>(); val failed = mutableListOf<String>()
        for (k in saved.keys()) {
            val before = if (saved.isNull(k)) null else saved.getString(k)
            if (!now.containsKey(k) || now[k] == before || before == null) continue
            val (ns, key) = k.split('/', limit = 2)
            val ok = when {
                ns == "system" && key in RINGTONE_KEYS && Settings.System.canWrite(ctx) ->
                    runCatching { Settings.System.putString(ctx.contentResolver, key, before) }.getOrDefault(false)
                ShizukuShell.isReady(ctx) ->
                    runCatching { ShizukuShell.run("settings put $ns $key ${ShizukuShell.q(before)}").ok }.getOrDefault(false)
                else -> false
            }
            (if (ok) restored else failed) += key
        }
        return restored to failed
    }

    fun canRestoreRingtones(ctx: Context) = Settings.System.canWrite(ctx) || ShizukuShell.isReady(ctx)
}
