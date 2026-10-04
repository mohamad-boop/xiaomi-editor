package app.xeditor.project

import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Turns the opened theme plus the editor's edits into an .mtz.
 *
 * App components inside an .mtz are nested zips (`com.android.systemui`, `icons`, …)
 * holding `theme_values.xml` (colours, dimens, integers) and replacement pictures
 * under `res/drawable-xxhdpi/`. Edits are merged into those: existing values are
 * overridden by name, replaced pictures drop every density of the old one, and
 * anything not touched is copied through unchanged.
 */
class ThemeBuilder(private val project: ThemeProject, private val edits: EditsStore) {

    private class Patch {
        val values = LinkedHashMap<Pair<ValueType, String>, String>()
        val entries = LinkedHashMap<String, ByteArray>()
        val replacedDrawables = mutableSetOf<String>()
        var dropOldAppIcons = false

        /**
         * Dark mode reads `nightmode/res/…` first, so replaced pictures go in both places
         * unless [dayOnly]: then the old dark copy is dropped and dark mode falls back to it.
         */
        fun drawable(name: String, png: ByteArray, dayOnly: Boolean = false) {
            entries["res/drawable-xxhdpi/$name.png"] = png
            if (!dayOnly) entries["nightmode/res/drawable-xxhdpi/$name.png"] = png
            replacedDrawables += name
        }

        val isEmpty get() = values.isEmpty() && entries.isEmpty() && !dropOldAppIcons
    }

    private val patches = LinkedHashMap<String, Patch>()
    private fun patch(component: String) = patches.getOrPut(component) { Patch() }

    fun build(out: File) {
        collect()
        out.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(out)).use { zos ->
            val top = project.dir.listFiles().orEmpty().sortedBy { if (it.name == ThemeProject.DESCRIPTION) "" else it.name }
            for (f in top) {
                val p = patches.remove(f.name)
                when {
                    p != null && f.isFile -> zos.putPatched(f.name, f, p, out)
                    f.isDirectory -> f.walkTopDown().filter { it.isFile }.forEach { zos.putFile(it.relativeTo(project.dir).invariantSeparatorsPath, it) }
                    else -> zos.putFile(f.name, f)
                }
            }
            // Components the opened theme didn't have yet.
            for ((name, p) in patches) if (!p.isEmpty) zos.putPatched(name, null, p, out)
        }
    }

    // ---- gathering edits ----

    private fun collect() {
        val v = edits.load()
        fun color(key: String): Int? = v[key]?.let { runCatching { Color.parseColor(it) }.getOrNull() }

        // Palette first so explicit colours from other screens override it.
        val ordered = listOf(Catalog.palette) + (Catalog.genericSurfaces - Catalog.palette)
        for (surface in ordered) for (item in surface.items) when (item) {
            is ColorItem -> v[item.id]?.let { value -> item.targets.forEach { patch(it.component).values[ValueType.COLOR to it.name] = value } }
            is NumberItem -> v[item.id]?.toIntOrNull()?.let { n ->
                val value = if (item.type == ValueType.DIMEN) dp(n.toFloat()) else n.toString()
                item.targets.forEach { patch(it.component).values[item.type to it.name] = value }
            }
            is ImageItem -> if (edits.hasImage(item.id)) {
                val png = edits.imageFile(item.id).readBytes()
                item.targets.forEach { patch(it.component).drawable(it.name, png) }
            }
        }

        for (h in Catalog.hideItems) if (v[h.id] == "true") {
            val blank = transparentPng()
            h.images.forEach { patch(it.component).drawable(it.name, blank) }
            h.colors.forEach { patch(it.component).values[ValueType.COLOR to it.name] = "#00000000" }
        }

        collectIcons(v, ::color)
        collectStatusBar(v)
        collectFingerprintAnimation(v)
        collectAdvanced(v)
    }

    /** Free-form overrides from the Advanced page; last, so they win over everything else. */
    private fun collectAdvanced(v: Map<String, String>) {
        for (o in AdvancedEdits.values(v)) {
            val type = ValueType.entries.firstOrNull { it.tag == o.type } ?: continue
            val value = if (type == ValueType.DIMEN && o.value.toFloatOrNull() != null) dp(o.value.toFloat()) else o.value
            patch(o.component).values[type to o.name] = value
        }
        for (p in AdvancedEdits.pictures(v)) {
            val f = edits.imageFile(p.imageKey)
            if (f.exists()) patch(p.component).drawable(p.name, f.readBytes())
        }
    }

    private fun collectIcons(v: Map<String, String>, color: (String) -> Int?) {
        val icons = patch(Comp.ICONS)
        if (v["icons.removeOld"] == "true") icons.dropOldAppIcons = true

        val shape = { key: String -> v[key]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } }

        // Icon-pack icons, recoloured if asked.
        val pack = edits.blob(ICON_PACK_BLOB)
        if (v["icons.pack"] == "true" && pack.exists()) {
            val tint = color("icons.tint")
            val plate = color("icons.plate")
            val plateShape = shape("icons.plateShape") ?: IconShape.SQUIRCLE
            ZipFile(pack).use { z ->
                for (e in z.entries().toList().filter { !it.isDirectory }) {
                    val raw = z.getInputStream(e).use(InputStream::readBytes)
                    val bytes = if (tint == null && plate == null) raw else {
                        BitmapFactory.decodeByteArray(raw, 0, raw.size)?.let { Generators.tintIcon(it, tint, plate, plateShape).png() } ?: raw
                    }
                    icons.entries["res/drawable-xxhdpi/${e.name}"] = bytes
                }
            }
        }

        // Shape (mask) for icons the theme doesn't cover, and the folder icon.
        when (v["icons.mask"]) {
            null -> Unit
            "custom" -> if (edits.hasImage("icons.mask.custom")) icons.drawable("icon_mask", edits.imageFile("icons.mask.custom").readBytes())
            else -> shape("icons.mask")?.let { s ->
                icons.drawable("icon_mask", Generators.iconMask(s))
                color("icons.maskPlate")?.let { icons.drawable("icon_background", Generators.iconBackground(s, it)) }
            }
        }
        when (v["icons.folder"]) {
            null -> Unit
            "custom" -> if (edits.hasImage("icons.folder.custom")) {
                val png = edits.imageFile("icons.folder.custom").readBytes()
                icons.drawable("icon_folder", png); icons.drawable("icon_folder_light", png)
            }
            else -> shape("icons.folder")?.let { s ->
                val c = color("icons.folderColor") ?: 0x66FFFFFF
                val png = Generators.folderIcon(s, c)
                icons.drawable("icon_folder", png); icons.drawable("icon_folder_light", png)
            }
        }

        // Dynamic icons.
        val accent = color("icons.dynAccent") ?: Color.rgb(230, 81, 0)
        val dynShape = shape("icons.dynShape") ?: IconShape.SQUIRCLE
        v["icons.calendar"]?.let { s ->
            runCatching { Generators.CalendarStyle.valueOf(s) }.getOrNull()?.let { style ->
                val files = Generators.calendarFiles(style, accent, dynShape)
                for (pkg in CALENDAR_PACKAGES) files.forEach { (name, bytes) -> icons.entries["fancy_icons/$pkg/$name"] = bytes }
            }
        }
        v["icons.clock"]?.let { s ->
            runCatching { Generators.ClockStyle.valueOf(s) }.getOrNull()?.let { style ->
                val files = Generators.clockFiles(style, accent, dynShape)
                for (pkg in CLOCK_PACKAGES) files.forEach { (name, bytes) -> icons.entries["fancy_icons/$pkg/$name"] = bytes }
            }
        }

        // Launcher extras: icon size and label colour.
        v["icons.size"]?.toIntOrNull()?.let { size ->
            for (c in listOf(Comp.HOME, Comp.POCO_HOME)) for (n in listOf("config_icon_width", "config_icon_height")) {
                patch(c).values[ValueType.DIMEN to n] = dp(size.toFloat())
            }
        }
        v["icons.textColor"]?.let { value ->
            for (c in listOf(Comp.HOME, Comp.POCO_HOME)) for (n in listOf("icon_title_text", "icon_title_text_light")) {
                patch(c).values[ValueType.COLOR to n] = value
            }
        }
    }

    /** Old dynamic-icon folders are replaced wholesale, so drop them before re-adding. */
    private fun replacesFancy(entry: String, p: Patch): Boolean {
        if (!entry.startsWith("fancy_icons/")) return false
        val pkg = entry.removePrefix("fancy_icons/").substringBefore('/')
        return p.entries.keys.any { it.startsWith("fancy_icons/$pkg/") }
    }

    private fun collectStatusBar(v: Map<String, String>) {
        val sys = patch(Comp.SYSUI)
        v["status.signal"]?.let { s ->
            runCatching { Generators.SignalStyle.valueOf(s) }.getOrNull()?.let { style ->
                for (lvl in 0..4) {
                    val white = Generators.signalIcon(style, lvl, Color.WHITE).png()
                    val black = Generators.signalIcon(style, lvl, Color.BLACK).png()
                    for (suffix in listOf("", "_tint", "_no_voice", "_no_voice_tint")) sys.drawable("stat_sys_signal_$lvl$suffix", white)
                    for (suffix in listOf("_darkmode", "_no_voice_darkmode")) sys.drawable("stat_sys_signal_$lvl$suffix", black)
                }
            }
        }
        v["status.wifi"]?.let { s ->
            runCatching { Generators.WifiStyle.valueOf(s) }.getOrNull()?.let { style ->
                for (lvl in 0..4) {
                    val white = Generators.wifiIcon(style, lvl, Color.WHITE).png()
                    sys.drawable("stat_sys_wifi_signal_$lvl", white)
                    sys.drawable("stat_sys_wifi_signal_${lvl}_tint", white)
                    sys.drawable("stat_sys_wifi_signal_${lvl}_darkmode", Generators.wifiIcon(style, lvl, Color.BLACK).png())
                }
            }
        }
    }

    private fun collectFingerprintAnimation(v: Map<String, String>) {
        if (v["finger.anim"] == null) return
        val frames = (1..Catalog.FINGER_FRAMES).map { edits.imageFile("finger.anim.$it") }
        if (!frames.all { it.exists() }) return
        val sys = patch(Comp.SYSUI)
        // Read each frame once; every animation style shares the same bytes.
        val bytes = frames.map { it.readBytes() }
        for (style in Catalog.fingerStyles) {
            // 24 frames × 6 styles is already large; a dark-mode copy would double it for no visible change.
            bytes.forEachIndexed { i, b -> sys.drawable("gxzw_${style}_recognizing_anim_${i + 1}", b, dayOnly = true) }
        }
    }

    // ---- writing ----

    /**
     * Writes the patched component to a temp file next to [out] and streams it in:
     * components with fingerprint frames reach tens of MB, too big to build in memory.
     */
    private fun ZipOutputStream.putPatched(name: String, base: File?, p: Patch, out: File) {
        val tmp = File(out.parentFile, ".component.tmp")
        try {
            patchZip(base, p, tmp)
            putFile(name, tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun patchZip(base: File?, p: Patch, dest: File) {
        ZipOutputStream(FileOutputStream(dest).buffered()).use { zos ->
            var values: String? = null
            var nightValues: String? = null
            base?.inputStream()?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    while (true) {
                        val e = zis.nextEntry ?: break
                        if (e.isDirectory) continue
                        val name = e.name
                        when {
                            name == VALUES -> values = zis.readBytes().toString(Charsets.UTF_8)
                            name == NIGHT_VALUES -> nightValues = zis.readBytes().toString(Charsets.UTF_8)
                            name in p.entries -> Unit
                            isReplacedDrawable(name, p) -> Unit
                            p.dropOldAppIcons && isAppIcon(name) -> Unit
                            replacesFancy(name, p) -> Unit
                            else -> zos.put(name, zis.readBytes())
                        }
                    }
                }
            }
            if (p.values.isNotEmpty() || values != null) zos.put(VALUES, mergeValues(values, p.values).toByteArray())
            nightValues?.let { zos.put(NIGHT_VALUES, mergeValues(it, p.values).toByteArray()) }
            for ((name, bytes) in p.entries) zos.put(name, bytes)
        }
    }

    private fun isReplacedDrawable(entry: String, p: Patch): Boolean {
        if (p.replacedDrawables.isEmpty()) return false
        val m = DRAWABLE_ENTRY.find(entry) ?: return false
        return m.groupValues[1] in p.replacedDrawables
    }

    /** App icons are named after packages/activities, so they always contain a dot. */
    private fun isAppIcon(entry: String): Boolean {
        if (entry.startsWith("fancy_icons/")) return false
        val file = entry.substringAfterLast('/')
        if (!file.endsWith(".png", true)) return false
        val inRes = entry.startsWith("res/drawable") || !entry.contains('/')
        return inRes && file.removeSuffix(".png").contains('.')
    }

    companion object {
        const val ICON_PACK_BLOB = "icons_pack.zip"
        private const val VALUES = "theme_values.xml"
        private const val NIGHT_VALUES = "nightmode/theme_values.xml"
        private val DRAWABLE_ENTRY = Regex("(?:^|/)res/drawable[^/]*/([^/]+?)(?:\\.9)?\\.(?:png|webp|jpg)$")
        val CALENDAR_PACKAGES = listOf("com.android.calendar", "com.xiaomi.calendar", "com.google.android.calendar")
        val CLOCK_PACKAGES = listOf("com.android.deskclock", "com.google.android.deskclock")

        fun dp(v: Float) = String.format(Locale.US, "%.6fdp", v)

        fun mergeValues(xml: String?, values: Map<Pair<ValueType, String>, String>): String {
            var s = xml?.takeIf { "</MIUI_Theme_Values>" in it }
                ?: "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<MIUI_Theme_Values>\n</MIUI_Theme_Values>\n"
            for ((key, value) in values) {
                val (type, name) = key
                val line = "<${type.tag} name=\"$name\">$value</${type.tag}>"
                val re = Regex("<${type.tag}\\s+name=\"${Regex.escape(name)}\"[^>]*>.*?</${type.tag}>", RegexOption.DOT_MATCHES_ALL)
                s = if (re.containsMatchIn(s)) re.replace(s, Regex.escapeReplacement(line))
                else s.replace("</MIUI_Theme_Values>", "$line\n</MIUI_Theme_Values>")
            }
            return s
        }
    }
}

private fun ZipOutputStream.put(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name)); write(bytes); closeEntry()
}

private fun ZipOutputStream.putFile(name: String, f: File) {
    putNextEntry(ZipEntry(name)); f.inputStream().buffered().use { it.copyTo(this) }; closeEntry()
}
