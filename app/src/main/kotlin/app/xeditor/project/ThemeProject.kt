package app.xeditor.project

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ThemeMeta(
    val title: String = "My theme",
    val author: String = "Me",
    val designer: String = "Me",
    val version: String = "1.0",
    val description: String = "Made with Xiaomi Editor",
    val uiVersion: String = "10",
)

data class ThemeComponent(
    val name: String,
    val label: String,
    val sizeBytes: Long,
    val fileCount: Int,
)

/**
 * The theme being edited, kept unpacked on disk so every edit is a plain file
 * write and components we don't understand pass through untouched.
 *
 * An .mtz is a zip whose top-level entries are "components":
 *   description.xml, wallpaper/, fonts/, boots/, preview/, and nested zips named
 *   after the app they theme (icons, lockscreen, com.android.systemui, …).
 */
class ThemeProject(context: Context) {
    val dir = File(context.filesDir, "project")

    val exists: Boolean get() = File(dir, DESCRIPTION).exists()

    fun file(path: String): File = File(dir, path)

    fun newBlank(meta: ThemeMeta = ThemeMeta()) {
        dir.deleteRecursively()
        dir.mkdirs()
        writeMeta(meta)
    }

    fun importMtz(input: InputStream) {
        val staging = File(dir.parentFile, "project_import").apply { deleteRecursively(); mkdirs() }
        val root = staging.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val target = File(staging, e.name)
                if (!target.canonicalPath.startsWith(root)) continue // zip-slip guard
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zis.copyTo(it) }
                }
            }
        }
        if (!File(staging, DESCRIPTION).exists()) {
            staging.deleteRecursively()
            error("This doesn't look like a theme — description.xml is missing")
        }
        dir.deleteRecursively()
        staging.renameTo(dir)
    }

    fun write(path: String, input: InputStream) {
        val f = file(path)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { input.copyTo(it) }
    }

    fun write(path: String, bytes: ByteArray) {
        val f = file(path)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    fun remove(path: String) {
        val f = file(path)
        if (f.isDirectory) f.deleteRecursively() else f.delete()
        // Drop now-empty component folders.
        var parent = f.parentFile
        while (parent != null && parent != dir && parent.list()?.isEmpty() == true) {
            parent.delete()
            parent = parent.parentFile
        }
    }

    fun components(): List<ThemeComponent> {
        val children = dir.listFiles().orEmpty().filter { it.name != DESCRIPTION }
        return children.map { f ->
            val files = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList() else listOf(f)
            ThemeComponent(
                name = f.name,
                label = labelFor(f.name),
                sizeBytes = files.sumOf { it.length() },
                fileCount = files.size,
            )
        }.sortedWith(compareBy({ order(it.name) }, { it.label.lowercase() }))
    }

    /** Replaces a theme sound, keeping the source's audio format. */
    fun setSound(kind: String, input: java.io.InputStream, extension: String) {
        soundFile(dir, kind)?.delete()
        write("ringtones/$kind.${extension.lowercase().ifEmpty { "mp3" }}", input)
    }

    fun removeSound(kind: String) {
        soundFile(dir, kind)?.let { remove(it.relativeTo(dir).invariantSeparatorsPath) }
    }

    /** Copies the chosen top-level components of another .mtz into this theme. */
    fun copyComponents(other: java.util.zip.ZipFile, names: Set<String>) {
        val root = dir.canonicalPath + File.separator
        names.forEach { remove(it) }
        for (e in other.entries().toList()) {
            if (e.isDirectory || e.name.substringBefore('/') !in names) continue
            val target = File(dir, e.name)
            if (!target.canonicalPath.startsWith(root)) continue // zip-slip guard
            target.parentFile?.mkdirs()
            other.getInputStream(e).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        }
    }

    fun readMeta(): ThemeMeta {
        val xml = file(DESCRIPTION).takeIf { it.exists() }?.readText() ?: return ThemeMeta()
        val d = ThemeMeta()
        return ThemeMeta(
            title = tag(xml, "title") ?: d.title,
            author = tag(xml, "author") ?: d.author,
            designer = tag(xml, "designer") ?: tag(xml, "author") ?: d.designer,
            version = tag(xml, "version") ?: d.version,
            description = tag(xml, "description") ?: d.description,
            uiVersion = tag(xml, "uiVersion") ?: d.uiVersion,
        )
    }

    /**
     * Updates the fields we edit in place so anything else an imported theme
     * carries (platform, locale titles, ui version, …) survives.
     */
    fun writeMeta(meta: ThemeMeta) {
        val f = file(DESCRIPTION)
        var xml = f.takeIf { it.exists() }?.readText()?.takeIf { "</theme>" in it } ?: DEFAULT_DESCRIPTION
        for ((name, value) in listOf(
            "title" to meta.title,
            "designer" to meta.designer,
            "author" to meta.author,
            "version" to meta.version,
            "description" to meta.description,
            "uiVersion" to meta.uiVersion,
        )) {
            val re = Regex("<$name>.*?</$name>", RegexOption.DOT_MATCHES_ALL)
            val replacement = "<$name>${escape(value)}</$name>"
            xml = if (re.containsMatchIn(xml)) {
                re.replaceFirst(xml, Regex.escapeReplacement(replacement))
            } else {
                xml.replace("</theme>", "    $replacement\n</theme>")
            }
        }
        f.parentFile?.mkdirs()
        f.writeText(xml)
    }

    companion object {
        const val DESCRIPTION = "description.xml"
        const val WALLPAPER = "wallpaper/default_wallpaper.jpg"
        const val LOCK_WALLPAPER = "wallpaper/default_lock_wallpaper.jpg"
        const val ICONS = "icons"
        const val BOOT_ANIMATION = "boots/bootanimation.zip"
        const val BOOT_AUDIO = "boots/bootaudio.mp3"

        /** Theme sounds: kind -> file stem in `ringtones/` (extension kept from the source). */
        val SOUNDS = linkedMapOf("ringtone" to "Ringtone", "notification" to "Notification", "alarm" to "Alarm")

        fun soundFile(dir: File, kind: String): File? =
            File(dir, "ringtones").listFiles().orEmpty().firstOrNull { it.nameWithoutExtension == kind }

        /** MIUI reads Roboto-Regular; MIUI 12–14 MiLanProVF; HyperOS MiSansVF. */
        val FONT_FILES = listOf("fonts/Roboto-Regular.ttf", "fonts/MiLanProVF.ttf", "fonts/MiSansVF.ttf")

        private val DEFAULT_DESCRIPTION = """
            <?xml version="1.0" encoding="utf-8"?>
            <theme>
                <title>My theme</title>
                <designer>Me</designer>
                <author>Me</author>
                <version>1.0</version>
                <uiVersion>10</uiVersion>
                <platform>2</platform>
                <description>Made with Xiaomi Editor</description>
            </theme>
        """.trimIndent() + "\n"

        private val LABELS = mapOf(
            "wallpaper" to "Wallpapers",
            "icons" to "App icons",
            "fonts" to "Font",
            "boots" to "Boot animation",
            "lockscreen" to "Lock screen",
            "preview" to "Preview images",
            "com.android.systemui" to "Status bar & Control centre",
            "com.miui.home" to "Home screen",
            "com.mi.android.globallauncher" to "Home screen (POCO launcher)",
            "miui.systemui.plugin" to "Control Center & volume",
            "framework-res" to "System colours (Android)",
            "framework-miui-res" to "System colours (MIUI)",
            "com.android.contacts" to "Contacts & dialer",
            "com.android.mms" to "Messaging",
            "com.android.settings" to "Settings",
            "com.miui.securitycenter" to "Security",
            "com.android.thememanager" to "Themes app",
            "ringtones" to "Ringtones",
            "audios" to "Sounds",
            "ringtones" to "Ringtone, notification & alarm sounds",
            "powermenu" to "Power menu",
            "keyguardmusicview" to "Lock screen music player",
            "fonts_fallback" to "Fallback fonts",
            "alarmscreen" to "Alarm screen",
            "aod" to "Always-on display",
            "lockstyle" to "Lock screen style",
            "miwallpaper" to "Super wallpaper",
            "bootanimation" to "Boot animation",
        )

        private val ORDER = listOf("wallpaper", "icons", "fonts", "boots", "lockscreen")

        fun labelFor(name: String): String = LABELS[name] ?: when {
            name.startsWith("clock_") -> "Clock widget ($name)"
            name.startsWith("photoframe_") -> "Photo frame widget ($name)"
            else -> name
        }

        private fun order(name: String): Int = ORDER.indexOf(name).let { if (it < 0) ORDER.size else it }

        private fun tag(xml: String, name: String): String? =
            Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)
                ?.groupValues?.get(1)?.trim()?.let(::unescape)

        private fun escape(s: String) = s
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        private fun unescape(s: String) = s
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
    }
}
