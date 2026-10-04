package app.xeditor.util

import android.content.Context
import android.os.Build
import android.view.WindowManager
import app.xeditor.shizuku.ShizukuShell
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Portrait pixel size of the whole display. */
fun screenSize(ctx: Context): Pair<Int, Int> {
    val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = ctx.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val m = ctx.resources.displayMetrics
        m.widthPixels to m.heightPixels
    }
    return min(w, h) to max(w, h)
}

/**
 * Where the in-screen fingerprint sensor sits, in screen pixels, plus how the
 * system scales theme pictures: they live in drawable-xxhdpi (480 dpi) and are
 * drawn at densityDpi / 480 of their pixel size.
 */
data class Fod(
    val x: Int, val y: Int, val w: Int, val h: Int,
    val screenW: Int, val screenH: Int,
    val densityDpi: Int,
    /** False when the position is a guess because the phone didn't report one. */
    val known: Boolean,
) {
    val centerX get() = x + w / 2f
    val centerY get() = y + h / 2f
    val drawScale get() = densityDpi / 480f

    /** Picture size (px) that shows up as [percentOfSensor] % of the sensor's width. */
    fun pictureSizeFor(percentOfSensor: Int) = (w * percentOfSensor / 100f / drawScale).roundToInt().coerceAtLeast(16)

    companion object {
        fun read(ctx: Context): Fod {
            val (sw, sh) = screenSize(ctx)
            val dpi = ctx.resources.displayMetrics.densityDpi
            fun prop(key: String): String? {
                val direct = runCatching {
                    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as String
                }.getOrNull()
                if (!direct.isNullOrBlank()) return direct
                return runCatching { if (ShizukuShell.isReady(ctx)) ShizukuShell.run("getprop $key").text.trim() else null }
                    .getOrNull()?.takeIf { it.isNotBlank() }
            }
            val loc = prop("persist.vendor.sys.fp.fod.location.X_Y")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            val size = prop("persist.vendor.sys.fp.fod.size.width_height")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            if (loc?.size == 2 && size?.size == 2) return Fod(loc[0], loc[1], size[0], size[1], sw, sh, dpi, true)
            // Typical Xiaomi placement: centred, a little above the bottom edge.
            val s = (sw * 0.164f).roundToInt()
            return Fod((sw - s) / 2, (sh * 0.87f).roundToInt(), s, s, sw, sh, dpi, false)
        }
    }
}
