package app.xeditor.project

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * The editor's own changes, kept apart from the opened theme so each surface can be
 * reset on its own and a set of choices can be saved as a preset and reused.
 *
 * Values are strings keyed by catalog item id ("#AARRGGBB" colours, numbers, flags);
 * pictures live next to them as `images/<id>.png`.
 */
class EditsStore(context: Context) {
    private val root = File(context.filesDir, "edits")
    private val presetsDir = File(context.filesDir, "presets")
    private val file get() = File(root, "edits.json")
    val imagesDir get() = File(root, "images")

    fun load(): Map<String, String> {
        val f = file
        if (!f.exists()) return emptyMap()
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    private fun save(map: Map<String, String>) {
        root.mkdirs()
        file.writeText(JSONObject(map).toString(2))
    }

    fun get(key: String): String? = load()[key]

    fun set(key: String, value: String?) {
        val m = load().toMutableMap()
        if (value == null) m.remove(key) else m[key] = value
        save(m)
    }

    fun imageFile(key: String) = File(imagesDir, "$key.png")
    fun hasImage(key: String) = imageFile(key).exists()

    fun setImage(key: String, png: ByteArray) {
        imagesDir.mkdirs()
        imageFile(key).writeBytes(png)
    }

    fun removeImage(key: String) {
        imageFile(key).delete()
    }

    /** A named blob outside the image namespace (e.g. the built icon-pack zip). */
    fun blob(name: String) = File(root, name)

    fun clearAll() {
        root.deleteRecursively()
    }

    /** Removes every value and picture whose key starts with one of [prefixes]. */
    fun reset(prefixes: Collection<String>) {
        fun hit(k: String) = prefixes.any { k == it || k.startsWith(it) }
        save(load().filterKeys { !hit(it) })
        imagesDir.listFiles().orEmpty().filter { hit(it.nameWithoutExtension) }.forEach { it.delete() }
    }

    fun keysWithEdits(): Set<String> =
        load().keys + imagesDir.listFiles().orEmpty().map { it.nameWithoutExtension }

    // ---- presets ----

    fun presets(): List<String> = presetsDir.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()

    fun savePreset(name: String) {
        val dst = File(presetsDir, name)
        dst.deleteRecursively()
        dst.mkdirs()
        if (root.exists()) root.copyRecursively(dst, overwrite = true)
    }

    fun applyPreset(name: String) {
        val src = File(presetsDir, name)
        if (!src.isDirectory) error("That preset could not be read and was removed")
        root.deleteRecursively()
        src.copyRecursively(root, overwrite = true)
    }

    fun deletePreset(name: String) {
        File(presetsDir, name).deleteRecursively()
    }
}

/** Key prefixes that make up each resettable surface, with a readable name. */
object EditSurfaces {
    val all: List<Pair<String, List<String>>> = listOf(
        "Theme palette" to listOf("palette."),
        "System colours" to listOf("sys."),
        "Volume dialog" to listOf("vol."),
        "Navigation bar" to listOf("nav."),
        "Launcher" to listOf("home.", "grid."),
        "Notifications panel" to listOf("notif.", "toggle."),
        "Recents background" to listOf("recents."),
        "Apps" to listOf("app."),
        "Fingerprint" to listOf("finger."),
        "Hidden elements" to listOf("hide."),
        "App icons" to listOf("icons."),
        "Status bar" to listOf("status."),
        "Advanced" to listOf("adv."),
    )

    fun edited(store: EditsStore): List<String> {
        val keys = store.keysWithEdits()
        return all.filter { (_, prefixes) -> keys.any { k -> prefixes.any { k.startsWith(it) } } }.map { it.first }
    }
}
