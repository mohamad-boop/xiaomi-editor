package app.xeditor.bootanim

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class FitMode { FIT, FILL }

enum class PlayMode {
    /** Loop the whole clip until the system finishes booting. */
    LOOP,
    /** Play the clip through once (even if boot finishes early), then hold the last frame. */
    ONCE_THEN_HOLD,
}

data class BootAnimSpec(
    val width: Int,
    val height: Int,
    val fps: Int,
    val mode: PlayMode,
    val fit: FitMode,
    val background: Int,
    /** Size of the picture relative to the fitted size (1 = fills per [fit]). */
    val contentScale: Float = 1f,
    /** Vertical shift as a fraction of the screen height (negative = up). */
    val offsetY: Float = 0f,
    /** Palette size for smaller frames (see PalettePng); 0 keeps full colour. */
    val colors: Int = 0,
)

/**
 * Writes an Android `bootanimation.zip`:
 *
 *   bootanimation.zip   (every entry STORED — the boot animator mmaps frames, it can't inflate)
 *     ├── desc.txt      "W H FPS" then one line per part: "<p|c> <loops> <pause> <folder>"
 *     ├── part0/00000.png …
 *     └── part1/00000.png   (ONCE_THEN_HOLD only: the held last frame, looped)
 */
object BootAnimBuilder {

    suspend fun build(
        source: FrameSource,
        spec: BootAnimSpec,
        out: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        val total = source.frameCount
        val canvasBitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var lastPng: ByteArray? = null
        var written = 0

        out.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(out)).use { zos ->
            zos.putStored("desc.txt", descTxt(spec).toByteArray())
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val frame = runCatching { source.frame(i, spec.width, spec.height, spec.fit) }.getOrNull()
                if (frame != null) {
                    render(canvas, frame, spec, paint)
                    frame.recycle()
                    val png = if (spec.colors > 0) app.xeditor.util.PalettePng.encode(canvasBitmap, spec.colors) else canvasBitmap.toPng()
                    zos.putStored("part0/%05d.png".format(written), png)
                    lastPng = png
                    written++
                }
                onProgress(i + 1, total)
            }
            val held = lastPng ?: error("None of the frames could be decoded")
            if (spec.mode == PlayMode.ONCE_THEN_HOLD) {
                zos.putStored("part1/00000.png", held)
            }
        }
        canvasBitmap.recycle()
    }

    fun descTxt(spec: BootAnimSpec): String = buildString {
        append("${spec.width} ${spec.height} ${spec.fps}\n")
        when (spec.mode) {
            PlayMode.LOOP -> append("p 0 0 part0\n")
            PlayMode.ONCE_THEN_HOLD -> {
                append("c 1 0 part0\n")
                append("p 0 0 part1\n")
            }
        }
    }

    /** One frame laid out exactly as the build would, shrunk by [downscale] for on-screen preview. */
    fun renderPreview(frame: Bitmap, spec: BootAnimSpec, downscale: Int): Bitmap {
        val w = (spec.width / downscale).coerceAtLeast(1)
        val h = (spec.height / downscale).coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(1f / downscale, 1f / downscale)
        render(c, frame, spec, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun render(canvas: Canvas, frame: Bitmap, spec: BootAnimSpec, paint: Paint) {
        canvas.drawColor(spec.background)
        val (fw, fh) = scaledSize(frame.width, frame.height, spec.width, spec.height, spec.fit)
        val w = fw * spec.contentScale
        val h = fh * spec.contentScale
        val left = (spec.width - w) / 2f
        val top = (spec.height - h) / 2f + spec.offsetY * spec.height
        canvas.drawBitmap(frame, null, RectF(left, top, left + w, top + h), paint)
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use {
        compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }
}

internal fun ZipOutputStream.putStored(name: String, bytes: ByteArray) {
    val crc = CRC32().apply { update(bytes) }
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        this.crc = crc.value
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

data class BootAnimPart(val folder: String, val loops: Int, val frames: List<String>)

data class BootAnimInfo(
    val width: Int,
    val height: Int,
    val fps: Int,
    val parts: List<BootAnimPart>,
) {
    val totalFrames: Int get() = parts.sumOf { it.frames.size }
    val frameNames: List<String> get() = parts.flatMap { it.frames }
}

/** Reads a bootanimation.zip for validation and in-app preview. */
class BootAnimReader(file: File) : AutoCloseable {
    private val zip = ZipFile(file)
    val info: BootAnimInfo = parse()

    private fun parse(): BootAnimInfo {
        val desc = zip.getEntry("desc.txt") ?: error("desc.txt is missing — not a boot animation")
        val lines = zip.getInputStream(desc).bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val header = lines.firstOrNull()?.split(Regex("\\s+")) ?: error("desc.txt is empty")
        val w = header.getOrNull(0)?.toIntOrNull() ?: error("Bad desc.txt header")
        val h = header.getOrNull(1)?.toIntOrNull() ?: error("Bad desc.txt header")
        val fps = header.getOrNull(2)?.toIntOrNull() ?: 30

        val entries = zip.entries().toList().filter { !it.isDirectory }.map { it.name }
        val parts = lines.drop(1).mapNotNull { line ->
            val f = line.split(Regex("\\s+"))
            if (f.size < 4 || f[0] !in setOf("p", "c", "f")) return@mapNotNull null
            val folder = f[3]
            val frames = entries
                .filter { it.startsWith("$folder/") && it.substringAfterLast('.').lowercase() in FRAME_EXT }
                .sorted()
            BootAnimPart(folder, f[1].toIntOrNull() ?: 0, frames)
        }
        if (parts.sumOf { it.frames.size } == 0) error("No frames found in any part")
        return BootAnimInfo(w, h, fps, parts)
    }

    /** True when some entry is compressed — the boot animator would silently skip it. */
    val hasCompressedEntries: Boolean
        get() = zip.entries().toList().any { !it.isDirectory && it.method != ZipEntry.STORED }

    fun decode(name: String, sampleSize: Int): Bitmap? {
        val e = zip.getEntry(name) ?: return null
        return zip.getInputStream(e).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
    }

    /** Rewrites every entry STORED, which is what the boot animator requires. */
    fun repackStored(out: File) {
        ZipOutputStream(FileOutputStream(out)).use { zos ->
            for (e in zip.entries().toList().filter { !it.isDirectory }.sortedBy { it.name }) {
                zos.putStored(e.name, zip.getInputStream(e).use(InputStream::readBytes))
            }
        }
    }

    override fun close() = zip.close()

    companion object {
        private val FRAME_EXT = setOf("png", "jpg", "jpeg", "webp")
    }
}

/**
 * A flashable Magisk module that overlays the stock boot animation. For rooted
 * devices, where the theme route is ignored.
 */
object MagiskModuleExporter {
    fun export(bootZip: File, out: File) {
        val bytes = bootZip.readBytes()
        ZipOutputStream(FileOutputStream(out)).use { zos ->
            fun put(name: String, data: ByteArray) {
                zos.putNextEntry(ZipEntry(name)); zos.write(data); zos.closeEntry()
            }
            put("module.prop", MODULE_PROP.toByteArray())
            put("META-INF/com/google/android/updater-script", "#MAGISK\n".toByteArray())
            put("META-INF/com/google/android/update-binary", UPDATE_BINARY.toByteArray())
            // Xiaomi ships it in /product/media; some builds still read /system/media.
            put("system/product/media/bootanimation.zip", bytes)
            put("system/media/bootanimation.zip", bytes)
        }
    }

    private val MODULE_PROP = """
        id=xeditor_bootanimation
        name=Custom boot animation (Xiaomi Editor)
        version=v1
        versionCode=1
        author=Xiaomi Editor
        description=Replaces the stock boot animation with one made in Xiaomi Editor.
    """.trimIndent() + "\n"

    // Magisk's standard module_installer.sh stub.
    private val UPDATE_BINARY = """
        #!/sbin/sh
        umask 022
        ui_print() { echo "${'$'}1"; }
        require_new_magisk() {
          ui_print "*******************************"
          ui_print " Please install Magisk v20.4+! "
          ui_print "*******************************"
          exit 1
        }
        OUTFD=${'$'}2
        ZIPFILE=${'$'}3
        mount /data 2>/dev/null
        [ -f /data/adb/magisk/util_functions.sh ] || require_new_magisk
        . /data/adb/magisk/util_functions.sh
        [ ${'$'}MAGISK_VER_CODE -lt 20400 ] && require_new_magisk
        install_module
        exit 0
    """.trimIndent() + "\n"
}
