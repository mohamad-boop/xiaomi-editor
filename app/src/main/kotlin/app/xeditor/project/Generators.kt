package app.xeditor.project

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun Bitmap.png(): ByteArray = ByteArrayOutputStream().use {
    compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray()
}

fun transparentPng(): ByteArray = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).png()

/** Icon silhouettes offered for the app-icon mask and the folder icon. */
enum class IconShape(val label: String) {
    CIRCLE("Circle"), SQUIRCLE("Squircle"), ROUNDED("Rounded square"), SQUARE("Square"),
    TEARDROP("Teardrop"), HEXAGON("Hexagon"), FLOWER("Flower"), STAR("Star");

    fun path(size: Float): Path {
        val p = Path()
        val r = size / 2f
        when (this) {
            CIRCLE -> p.addCircle(r, r, r, Path.Direction.CW)
            SQUARE -> p.addRect(0f, 0f, size, size, Path.Direction.CW)
            ROUNDED -> p.addRoundRect(RectF(0f, 0f, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW)
            SQUIRCLE -> {
                // Superellipse |x|^4 + |y|^4 = 1
                for (i in 0..360) {
                    val a = i * PI / 180
                    val c = cos(a); val sn = sin(a)
                    val x = r + r * (Math.signum(c) * Math.sqrt(kotlin.math.abs(c))).toFloat()
                    val y = r + r * (Math.signum(sn) * Math.sqrt(kotlin.math.abs(sn))).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            TEARDROP -> {
                p.addRoundRect(
                    RectF(0f, 0f, size, size),
                    floatArrayOf(r, r, r, r, size * 0.12f, size * 0.12f, r, r),
                    Path.Direction.CW,
                )
            }
            HEXAGON -> polygon(p, r, 6, r, 30.0)
            FLOWER -> {
                for (i in 0..720) {
                    val a = i * PI / 360
                    val rr = r * (0.86 + 0.14 * cos(6 * a))
                    val x = r + rr * cos(a); val y = r + rr * sin(a)
                    if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                }
                p.close()
            }
            STAR -> {
                for (i in 0 until 10) {
                    val a = -PI / 2 + i * PI / 5
                    val rr = if (i % 2 == 0) r.toDouble() else r * 0.62
                    val x = r + rr * cos(a); val y = r + rr * sin(a)
                    if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                }
                p.close()
            }
        }
        return p
    }

    private fun polygon(p: Path, r: Float, n: Int, radius: Float, startDeg: Double) {
        for (i in 0 until n) {
            val a = Math.toRadians(startDeg + i * 360.0 / n)
            val x = r + radius * cos(a); val y = r + radius * sin(a)
            if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        p.close()
    }

    fun render(size: Int, color: Int, inset: Float = 0f): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.translate(inset, inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawPath(path(size - inset * 2), paint)
        return b
    }
}

object Generators {
    const val ICON = 192

    /** [src] centred and scaled to fit a transparent [size]×[size] square. */
    fun fitSquare(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val s = min(size.toFloat() / src.width, size.toFloat() / src.height)
        val w = src.width * s; val h = src.height * s
        Canvas(out).drawBitmap(
            src, null, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    /** icon_mask.png: white silhouette the engine cuts unthemed icons with. */
    fun iconMask(shape: IconShape) = shape.render(ICON, Color.WHITE).png()

    /** icon_background.png: plate drawn behind unthemed icons. */
    fun iconBackground(shape: IconShape, color: Int) = shape.render(ICON, color).png()

    fun folderIcon(shape: IconShape, color: Int) = shape.render(ICON, color, inset = 6f).png()

    /**
     * Recolours an icon-pack icon. Line packs like Arcticons are single-colour
     * glyphs, so a SRC_IN tint recolours them cleanly; the optional plate sits behind.
     */
    fun tintIcon(src: Bitmap, tint: Int?, plate: Int?, shape: IconShape): Bitmap {
        val out = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (plate != null) c.drawPath(shape.path(ICON.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate })
        val inset = if (plate != null) ICON * 0.18f else 0f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (tint != null) paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, null, RectF(inset, inset, ICON - inset, ICON - inset), paint)
        return out
    }

    // ---- dynamic calendar ----

    enum class CalendarStyle(val label: String) { TILE("Tile"), PAGE("Tear-off page"), RING("Ring"), MINIMAL("Minimal") }

    /** fancy_icons/<pkg>/ — manifest + one picture per day, picked by #date at runtime. */
    fun calendarFiles(style: CalendarStyle, accent: Int, shape: IconShape): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        files["manifest.xml"] = """
            <?xml version="1.0" encoding="utf-8"?>
            <Icon frameRate="0" height="192" screenWidth="1080" useVariableUpdater="DateTime.Day" version="1" width="192">
                <VariableBinders>
                    <BroadcastBinder action="android.intent.action.TIME_SET" />
                    <BroadcastBinder action="android.intent.action.DATE_CHANGED" />
                </VariableBinders>
                <Image align="center" alignV="center" src="number.png" srcid="#date" x="96" y="96" />
            </Icon>
        """.trimIndent().toByteArray()
        for (day in 1..31) files["number_$day.png"] = calendarDay(style, accent, shape, day).png()
        return files
    }

    private fun calendarDay(style: CalendarStyle, accent: Int, shape: IconShape, day: Int): Bitmap {
        val s = ICON.toFloat()
        val b = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        fun centerText(str: String, y: Float, size: Float, color: Int) {
            text.textSize = size; text.color = color
            c.drawText(str, s / 2, y - (text.descent() + text.ascent()) / 2, text)
        }
        when (style) {
            CalendarStyle.TILE -> {
                fill.color = accent; c.drawPath(shape.path(s), fill)
                centerText(day.toString(), s / 2, s * 0.5f, Color.WHITE)
            }
            CalendarStyle.PAGE -> {
                c.save(); c.clipPath(shape.path(s))
                fill.color = Color.WHITE; c.drawRect(0f, 0f, s, s, fill)
                fill.color = accent; c.drawRect(0f, 0f, s, s * 0.3f, fill)
                c.restore()
                centerText(day.toString(), s * 0.63f, s * 0.44f, Color.rgb(34, 34, 34))
            }
            CalendarStyle.RING -> {
                fill.color = Color.WHITE; c.drawPath(shape.path(s), fill)
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    setStyle(Paint.Style.STROKE); strokeWidth = s * 0.07f; color = accent; strokeCap = Paint.Cap.ROUND
                }
                val pad = s * 0.2f
                c.drawArc(RectF(pad, pad, s - pad, s - pad), -90f, 360f * day / 31f, false, ring)
                centerText(day.toString(), s / 2, s * 0.32f, accent)
            }
            CalendarStyle.MINIMAL -> {
                fill.color = Color.rgb(28, 28, 30); c.drawPath(shape.path(s), fill)
                centerText(day.toString(), s / 2, s * 0.5f, accent)
            }
        }
        return b
    }

    // ---- dynamic clock ----

    enum class ClockStyle(val label: String) { CLASSIC("Classic"), DARK("Dark"), ACCENT("Accent face") }

    fun clockFiles(style: ClockStyle, accent: Int, shape: IconShape): Map<String, ByteArray> {
        val (face, hands, second) = when (style) {
            ClockStyle.CLASSIC -> Triple(Color.WHITE, Color.rgb(34, 34, 34), accent)
            ClockStyle.DARK -> Triple(Color.rgb(28, 28, 30), Color.WHITE, accent)
            ClockStyle.ACCENT -> Triple(accent, Color.WHITE, Color.WHITE)
        }
        val bg = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
        val c = Canvas(bg)
        c.drawPath(shape.path(ICON.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = face })
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hands; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; alpha = 170 }
        for (i in 0 until 12) {
            val a = i * PI / 6
            val r1 = 70.0; val r2 = if (i % 3 == 0) 56.0 else 62.0
            c.drawLine((96 + r1 * sin(a)).toFloat(), (96 - r1 * cos(a)).toFloat(), (96 + r2 * sin(a)).toFloat(), (96 - r2 * cos(a)).toFloat(), tick)
        }
        fun hex(color: Int) = "#%08x".format(color)
        val manifest = """
            <?xml version="1.0" encoding="utf-8"?>
            <Icon frameRate="1" height="192" screenWidth="1080" useVariableUpdater="DateTime.Second" version="1" width="192">
                <Var name="hour_angle" expression="#hour12*30+#minute/2"/>
                <Var name="minute_angle" expression="#minute*6+#second/10"/>
                <Var name="second_angle" expression="#second*6"/>
                <Image x="96" y="96" align="center" alignV="center" src="icon_bg.png"/>
                <Rectangle x="96" y="58" w="8" h="40" align="center" pivotX="4" pivotY="38" cornerRadius="4,4" fillColor="${hex(hands)}" rotation="#hour_angle" antiAlias="true"/>
                <Rectangle x="96" y="38" w="5" h="60" align="center" pivotX="2.5" pivotY="58" cornerRadius="2.5,2.5" fillColor="${hex(hands)}" rotation="#minute_angle" antiAlias="true"/>
                <Rectangle x="96" y="32" w="2" h="72" align="center" pivotX="1" pivotY="64" fillColor="${hex(second)}" rotation="#second_angle" antiAlias="true"/>
                <Circle x="96" y="96" r="5" fillColor="${hex(second)}" antiAlias="true"/>
            </Icon>
        """.trimIndent()
        return linkedMapOf("manifest.xml" to manifest.toByteArray(), "icon_bg.png" to bg.png())
    }

    // ---- status bar ----

    enum class SignalStyle(val label: String) { BARS("Bars"), ROUNDED("Rounded bars"), DOTS("Dots"), THIN("Thin bars") }
    enum class WifiStyle(val label: String) { ARCS("Arcs"), DOTS("Dots"), BARS("Bars") }

    /** Level 0..4, drawn white (tinted by the system) or black for light backgrounds. */
    fun signalIcon(style: SignalStyle, level: Int, color: Int): Bitmap {
        val w = 60; val h = 48
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 70 }
        for (i in 0 until 4) {
            val p = if (i < level) on else off
            when (style) {
                SignalStyle.DOTS -> c.drawCircle(8f + i * 14.5f, h - 10f, 6f, p)
                else -> {
                    val bw = if (style == SignalStyle.THIN) 6f else 10f
                    val gap = (w - 4 * bw) / 4f
                    val x = gap / 2 + i * (bw + gap)
                    val top = h - (h - 6f) * (i + 1) / 4f
                    val rad = if (style == SignalStyle.ROUNDED) bw / 2 else 1.5f
                    c.drawRoundRect(RectF(x, top, x + bw, h.toFloat()), rad, rad, p)
                }
            }
        }
        return b
    }

    fun wifiIcon(style: WifiStyle, level: Int, color: Int): Bitmap {
        val s = 54
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 70 }
        when (style) {
            WifiStyle.ARCS -> {
                val cx = s / 2f; val cy = s - 6f
                c.drawCircle(cx, cy, 5f, if (level >= 1) on else off)
                for (i in 1..3) {
                    val r = 4f + i * 12f
                    val p = Paint(if (level >= i + 1) on else off).apply {
                        setStyle(Paint.Style.STROKE); strokeWidth = 7f; strokeCap = Paint.Cap.ROUND
                    }
                    c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -135f, 90f, false, p)
                }
            }
            WifiStyle.DOTS -> for (i in 0 until 4) c.drawCircle(s / 2f, s - 7f - i * 13f, 5f + (3 - i) * 0.5f, if (i < level) on else off)
            WifiStyle.BARS -> for (i in 0 until 4) {
                val bh = 8f + i * 12f
                c.drawRoundRect(RectF(4f + i * 12.5f, s - 4f - bh, 13f + i * 12.5f, s - 4f), 3f, 3f, if (i < level) on else off)
            }
        }
        return b
    }

    // ---- fingerprint unlock animation ----

    enum class FingerEffect(val label: String) { ORIGINAL("Original"), PULSE("Pulse"), SPIN("Spin"), GLOW("Glow"), NONE("No animation") }

    /** Frames are produced lazily, one at a time: at 600 % each is ~1200 px square. */
    fun fingerFrames(sources: List<Bitmap>, effect: FingerEffect, size: Int = 360): Sequence<Bitmap> =
        (0 until Catalog.FINGER_FRAMES).asSequence().map { i ->
            val src = if (effect == FingerEffect.ORIGINAL && sources.size > 1) sources[i * sources.size / Catalog.FINGER_FRAMES] else sources.first()
            val t = i / Catalog.FINGER_FRAMES.toFloat()
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            // Glow needs a little room for its blur; everything else fills the canvas.
            val base = min(size.toFloat() / src.width, size.toFloat() / src.height) * (if (effect == FingerEffect.GLOW) 0.85f else 1f)
            var scale = base
            c.save()
            when (effect) {
                FingerEffect.PULSE -> scale = base * (0.82f + 0.18f * sin(t * 2 * PI).toFloat().let { (it + 1) / 2 })
                FingerEffect.SPIN -> c.rotate(360f * t, size / 2f, size / 2f)
                FingerEffect.GLOW -> {
                    val glow = Paint(paint).apply {
                        maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
                        alpha = (90 + 140 * (sin(t * 2 * PI).toFloat() + 1) / 2).toInt()
                    }
                    val w = src.width * base * 1.08f; val h = src.height * base * 1.08f
                    c.drawBitmap(src, null, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), glow)
                }
                else -> Unit
            }
            val w = src.width * scale; val h = src.height * scale
            c.drawBitmap(src, null, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), paint)
            c.restore()
            out
        }
}
