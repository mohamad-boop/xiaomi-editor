package app.xeditor.shizuku

import app.xeditor.project.ThemeMeta
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The Themes app keeps installed themes unpacked in its own storage:
 *
 *   .data/content/<code>/<id>.mrc   one theme part (a nested zip, a JPEG, a font…)
 *   .data/meta/<code>/<id>.mrm      JSON describing it; the "theme" entry lists its parts
 *   .data/preview/theme/<id>/…      preview pictures
 *
 * Android 11+ hides that folder from apps, so everything goes through Shizuku.
 * Licence files (.data/rights) are deliberately never written: those are the
 * Themes app's purchase records, and faking them would sidestep Xiaomi's licensing.
 */
object ThemeStore {
    const val ROOT = "/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.data"
    private const val INSTALLED_BY = "app.xeditor"

    data class Part(val code: String, val id: String)
    data class Installed(val id: String, val title: String, val designer: String, val parts: List<Part>, val ours: Boolean)

    // mtz component name <-> Themes app resource code
    private val CODE_FOR = mapOf(
        "com.android.systemui" to "statusbar",
        "com.miui.home" to "launcher",
        "com.android.contacts" to "contact",
        "com.android.mms" to "mms",
        "boots" to "bootanimation",
    )
    private val COMPONENT_FOR = CODE_FOR.entries.associate { (k, v) -> v to k }

    private val SOUND_CODES = setOf("ringtone", "notification", "alarm")

    /** Sniffs the container so a re-exported sound keeps a matching extension. */
    private fun audioExt(b: ByteArray): String = when {
        b.size > 4 && String(b, 0, 4, Charsets.US_ASCII) == "OggS" -> "ogg"
        b.size > 4 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" -> "wav"
        b.size > 8 && String(b, 4, 4, Charsets.US_ASCII) == "ftyp" -> "m4a"
        else -> "mp3"
    }

    private fun en(o: JSONObject?, key: String) = o?.optJSONObject(key)?.let { j ->
        j.optString("en_US").ifEmpty { j.keys().asSequence().firstOrNull()?.let(j::optString) }
    }.orEmpty()

    private fun readMeta(code: String, id: String): JSONObject? =
        ShizukuShell.run("cat ${ShizukuShell.q("$ROOT/meta/$code/$id.mrm")}").takeIf { it.ok }?.let { JSONObject(it.text) }

    fun list(): List<Installed> {
        // One JSON document per line.
        val r = ShizukuShell.run("for f in $ROOT/meta/theme/*.mrm; do [ -f \"\$f\" ] && tr -d '\\n' < \"\$f\" && echo; done")
        if (!r.ok) return emptyList()
        val all = r.text.lines().filter { it.isNotBlank() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        return all.filter { (it.optJSONArray("parentResources")?.length() ?: 0) == 0 }.map { o ->
            Installed(
                id = o.getString("localId"),
                title = en(o, "titles").ifEmpty { "Untitled" },
                designer = en(o, "designers"),
                parts = parts(o),
                ours = o.optJSONObject("extraMeta")?.optString("installedBy") == INSTALLED_BY,
            )
        }.sortedBy { it.title.lowercase() }
    }

    private fun parts(o: JSONObject): List<Part> {
        val subs = o.optJSONArray("subResources") ?: return emptyList()
        return (0 until subs.length()).map { subs.getJSONObject(it) }.map { Part(it.getString("resourceCode"), it.getString("localId")) }
    }

    /** Rebuilds an installed theme as an .mtz so the editor can open it. */
    fun exportToMtz(theme: Installed, out: File) {
        val meta = readMeta("theme", theme.id)
        // Direct parts first; parts of nested "theme" entries fill in whatever is missing.
        val ordered = mutableListOf<Part>()
        fun add(ps: List<Part>): Unit = ps.forEach { p ->
            if (p.code == "theme") readMeta("theme", p.id)?.let { add(parts(it)) }
            else if (ordered.none { it.code == p.code }) ordered += p
        }
        add(theme.parts)

        ZipOutputStream(FileOutputStream(out)).use { zos ->
            fun put(name: String, bytes: ByteArray) { zos.putNextEntry(ZipEntry(name)); zos.write(bytes); zos.closeEntry() }
            put("description.xml", description(meta, theme))
            for (p in ordered) {
                val r = ShizukuShell.run("cat ${ShizukuShell.q("$ROOT/content/${p.code}/${p.id}.mrc")}")
                if (!r.ok || r.out.isEmpty()) continue
                val bytes = r.out
                val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
                when {
                    p.code == "wallpaper" && isJpeg -> put("wallpaper/default_wallpaper.jpg", bytes)
                    p.code == "lockscreen" && isJpeg -> put("wallpaper/default_lock_wallpaper.jpg", bytes)
                    p.code == "fonts" -> put("fonts/Roboto-Regular.ttf", bytes)
                    p.code == "bootanimation" -> put("boots/bootanimation.zip", bytes)
                    p.code == "bootaudio" -> put("boots/bootaudio.mp3", bytes)
                    p.code in SOUND_CODES -> put("ringtones/${p.code}.${audioExt(bytes)}", bytes)
                    else -> put(COMPONENT_FOR[p.code] ?: p.code, bytes)
                }
            }
            val previews = ShizukuShell.run("ls ${ShizukuShell.q("$ROOT/preview/theme/${theme.id}")}")
            if (previews.ok) previews.text.lines().filter { it.isNotBlank() }.forEach { name ->
                val r = ShizukuShell.run("cat ${ShizukuShell.q("$ROOT/preview/theme/${theme.id}/$name")}")
                if (r.ok) put("preview/$name", r.out)
            }
        }
    }

    private fun description(meta: JSONObject?, t: Installed): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <theme>
                <title>${esc(t.title)}</title>
                <designer>${esc(t.designer)}</designer>
                <author>${esc(en(meta, "authors"))}</author>
                <version>${esc(meta?.optString("version").orEmpty().ifEmpty { "1.0" })}</version>
                <uiVersion>10</uiVersion>
                <platform>${meta?.optInt("platform", 2) ?: 2}</platform>
                <description>${esc(en(meta, "descriptions"))}</description>
            </theme>
        """.trimIndent().toByteArray()
    }

    /**
     * Adds an .mtz to the Themes app's "My themes" list. Returns the new theme id.
     */
    fun install(mtz: File, meta: ThemeMeta): String {
        val themeId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val themeRef = JSONObject().put("localId", themeId).put("resourceCode", "theme")
            .put("extraMeta", JSONObject().put("installedBy", INSTALLED_BY)).put("metaPath", JSONObject.NULL).put("contentPath", JSONObject.NULL)
        val subs = JSONArray()
        val previewNames = JSONArray()

        ZipFile(mtz).use { z ->
            val entries = z.entries().toList().filter { !it.isDirectory }
            val hasLockZip = entries.any { it.name == "lockscreen" }
            // (resource code, entry) for every part the Themes app stores separately.
            val parts = entries.mapNotNull { e ->
                val n = e.name
                when {
                    n == "wallpaper/default_wallpaper.jpg" -> "wallpaper" to e
                    n == "wallpaper/default_lock_wallpaper.jpg" -> if (hasLockZip) null else "lockscreen" to e
                    n == "fonts/Roboto-Regular.ttf" -> "fonts" to e
                    n == "boots/bootanimation.zip" -> "bootanimation" to e
                    n == "boots/bootaudio.mp3" -> "bootaudio" to e
                    // ringtones/ringtone.ogg → "ringtone", likewise notification / alarm
                    n.startsWith("ringtones/") && n.substringAfter('/').substringBeforeLast('.') in SOUND_CODES ->
                        n.substringAfter('/').substringBeforeLast('.') to e
                    // the Themes app routes "launcher" to whichever launcher is active
                    n == "description.xml" || n == "com.mi.android.globallauncher" || '/' in n -> null
                    else -> (CODE_FOR[n] ?: n) to e
                }
            }
            if (parts.isEmpty()) error("There's nothing in this theme to install yet")

            for ((code, e) in parts) {
                val id = UUID.randomUUID().toString()
                val hash = write("$ROOT/content/$code/$id.mrc", z.getInputStream(e))
                val m = baseMeta(id, hash, e.size, meta, now).put("parentResources", JSONArray().put(themeRef))
                write("$ROOT/meta/$code/$id.mrm", m.toString().toByteArray().inputStream())
                subs.put(
                    JSONObject().put("localId", id).put("resourceCode", code).put("extraMeta", JSONObject())
                        .put("metaPath", JSONObject.NULL).put("contentPath", JSONObject.NULL),
                )
            }
            for (e in entries.filter { it.name.startsWith("preview/") }) {
                val name = e.name.substringAfterLast('/')
                write("$ROOT/preview/theme/$themeId/$name", z.getInputStream(e))
                previewNames.put(name)
            }
        }

        val theme = baseMeta(themeId, sha1(mtz), mtz.length(), meta, now)
            .put("builtInThumbnails", if (previewNames.length() > 0) JSONObject().put("fallback", previewNames) else JSONObject())
            .put("builtInPreviews", if (previewNames.length() > 0) JSONObject().put("fallback", previewNames) else JSONObject())
            .put("subResources", subs)
            .put("extraMeta", JSONObject().put("installedBy", INSTALLED_BY))
        write("$ROOT/content/theme/$themeId.mrc", ByteArray(0).inputStream())
        write("$ROOT/meta/theme/$themeId.mrm", theme.toString().toByteArray().inputStream())

        // Make the Themes app re-read its store next time it opens.
        ShizukuShell.run("am force-stop com.android.thememanager")
        return themeId
    }

    /** Removes a theme this app installed, with all its parts. */
    fun uninstall(theme: Installed) {
        if (!theme.ours) error("Only themes installed by Xiaomi Editor can be removed here")
        val cmds = theme.parts.flatMap { p ->
            listOf("rm -f ${ShizukuShell.q("$ROOT/content/${p.code}/${p.id}.mrc")}", "rm -f ${ShizukuShell.q("$ROOT/meta/${p.code}/${p.id}.mrm")}")
        } + listOf(
            "rm -f ${ShizukuShell.q("$ROOT/content/theme/${theme.id}.mrc")}",
            "rm -f ${ShizukuShell.q("$ROOT/meta/theme/${theme.id}.mrm")}",
            "rm -rf ${ShizukuShell.q("$ROOT/preview/theme/${theme.id}")}",
            "am force-stop com.android.thememanager",
        )
        ShizukuShell.check(cmds.joinToString(" ; "))
    }

    private fun baseMeta(id: String, hash: String, size: Long, meta: ThemeMeta, now: Long) = JSONObject()
        .put("localId", id).put("onlineId", JSONObject.NULL).put("assemblyId", JSONObject.NULL).put("productId", JSONObject.NULL)
        .put("hash", hash).put("platform", 17).put("miuiAdapterVersion", 3.2).put("size", size).put("updatedTime", now)
        .put("version", meta.version)
        .put("authors", JSONObject().put("en_US", meta.author))
        .put("designers", JSONObject().put("en_US", meta.designer))
        .put("titles", JSONObject().put("en_US", meta.title))
        .put("descriptions", JSONObject().put("en_US", meta.description))
        .put("builtInThumbnails", JSONObject()).put("builtInPreviews", JSONObject())
        .put("thumbnails", JSONArray()).put("previews", JSONArray())
        .put("parentResources", JSONArray()).put("subResources", JSONArray())
        .put("extraMeta", JSONObject())
        .put("metaPath", JSONObject.NULL).put("contentPath", JSONObject.NULL).put("rightsPath", JSONObject.NULL)

    /** Streams [input] to [path] as the shell user; returns its SHA-1. */
    private fun write(path: String, input: java.io.InputStream): String {
        val dir = path.substringBeforeLast('/')
        val digest = MessageDigest.getInstance("SHA-1")
        ShizukuShell.check(
            "mkdir -p ${ShizukuShell.q(dir)} && cat > ${ShizukuShell.q(path)} && chmod 660 ${ShizukuShell.q(path)}",
            java.security.DigestInputStream(input.buffered(), digest),
        )
        return digest.digest().hex()
    }

    private fun sha1(f: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        f.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
