package app.xeditor.iconpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import app.xeditor.data.Assignment
import app.xeditor.data.AssignmentSource
import app.xeditor.picker.IconBitmapStore
import app.xeditor.ui.loadPackDrawable
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Produces the `icons` component of a MIUI/HyperOS theme: a nested zip of PNGs.
 *
 * Each entry gets an activity-qualified `<package>.<activity>.png` so per-activity
 * targets like share-sheet entries resolve, plus MIUI's package-fallback name
 * `<package>.png` (launcher activity wins when there is one).
 */
class IconsBuilder(
    private val context: Context,
    private val iconPackPackage: String,
) {

    /** [extraMappings]: icon-pack entries to include even for apps that aren't installed. */
    fun build(assignments: List<Assignment>, extraMappings: Map<ComponentKey, String> = emptyMap()): ByteArray {
        val bitmapStore = IconBitmapStore(context)
        val pm = context.packageManager
        val launcherActivityCache = mutableMapOf<String, String?>()
        fun launcherActivityFor(pkg: String): String? = launcherActivityCache.getOrPut(pkg) {
            runCatching { pm.getLaunchIntentForPackage(pkg)?.component?.className }.getOrNull()
        }

        return ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                val written = mutableSetOf<String>()
                // Per-package fallback: the launcher-activity icon wins when we see
                // one; otherwise keep the first themed icon as a stand-in so packages
                // without visible launchers (Print, Quick Share, MiShare) still resolve.
                val packageFallback = mutableMapOf<String, ByteArray>()
                for (a in assignments) {
                    if (a.source == AssignmentSource.SKIP) continue
                    val png = resolveIcon(a, bitmapStore) ?: continue
                    writeEntry(zos, "${a.pkg}.${a.activity}.png", png, written)
                    if (launcherActivityFor(a.pkg) == a.activity) {
                        packageFallback[a.pkg] = png
                    } else {
                        packageFallback.putIfAbsent(a.pkg, png)
                    }
                }
                for ((pkg, png) in packageFallback) {
                    writeEntry(zos, "$pkg.png", png, written)
                }
                val covered = assignments.map { it.pkg }.toSet()
                for ((key, drawable) in extraMappings) {
                    if (key.pkg in covered) continue
                    val png = loadDrawableBitmap(drawable)?.let { bmp ->
                        ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
                    } ?: continue
                    writeEntry(zos, "${key.pkg}.${key.activity}.png", png, written)
                    writeEntry(zos, "${key.pkg}.png", png, written)
                }
            }
            baos.toByteArray()
        }
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, bytes: ByteArray, written: MutableSet<String>) {
        if (!written.add(name)) return
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun resolveIcon(a: Assignment, bitmapStore: IconBitmapStore): ByteArray? {
        val bitmap = when (a.source) {
            AssignmentSource.MANUAL -> bitmapStore.load(ComponentKey(a.pkg, a.activity))
            AssignmentSource.AUTO -> a.drawable?.let { loadDrawableBitmap(it) }
            AssignmentSource.SKIP -> null
        } ?: return null
        return ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
    }

    private fun loadDrawableBitmap(drawableName: String): Bitmap? {
        val d = loadPackDrawable(context, iconPackPackage, drawableName) ?: return null
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        return bmp
    }
}
