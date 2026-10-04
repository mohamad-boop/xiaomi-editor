package app.xeditor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.xeditor.data.Assignment
import app.xeditor.data.AssignmentSource
import app.xeditor.iconpack.ComponentKey
import app.xeditor.picker.IconBitmapStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, key1 = pkg) {
        value = withContext(Dispatchers.IO) { loadAppIcon(ctx, pkg) }
    }.value
}

@Composable
fun rememberThemedIcon(assignment: Assignment?): ImageBitmap? {
    val ctx = LocalContext.current
    val key = assignment?.let { "${it.pkg}/${it.activity}/${it.source}/${it.drawable}" }
    return produceState<ImageBitmap?>(initialValue = null, key1 = key) {
        value = withContext(Dispatchers.IO) { loadThemedIcon(ctx, assignment) }
    }.value
}

private fun loadAppIcon(ctx: Context, pkg: String): ImageBitmap? = runCatching {
    val info = ctx.packageManager.getApplicationInfo(pkg, 0)
    drawableToImageBitmap(info.loadIcon(ctx.packageManager))
}.getOrNull()

private fun loadThemedIcon(ctx: Context, a: Assignment?): ImageBitmap? {
    if (a == null) return null
    return when (a.source) {
        AssignmentSource.MANUAL -> {
            val raw = IconBitmapStore(ctx).load(ComponentKey(a.pkg, a.activity)) ?: return null
            drawableToImageBitmap(BitmapDrawable(ctx.resources, raw), tint = android.graphics.Color.BLACK)
        }
        AssignmentSource.AUTO -> {
            val drawable = a.drawable ?: return null
            loadPackDrawable(ctx, a.iconPackPackage, drawable)?.let {
                drawableToImageBitmap(it, tint = android.graphics.Color.BLACK)
            }
        }
        AssignmentSource.SKIP -> null
    }
}

/**
 * Loads a drawable from another package using that package's own context + theme.
 * This matters for icon packs whose vector drawables reference theme attributes
 * (e.g., Arcticons' tints) — loading via the foreign package's theme makes them
 * resolve correctly. Tries dpi 640/480/320 in that order.
 */
fun loadPackDrawable(ctx: Context, packPkg: String, drawableName: String): Drawable? {
    val packCtx = runCatching { ctx.createPackageContext(packPkg, 0) }.getOrNull() ?: return null
    val res = packCtx.resources
    val id = res.getIdentifier(drawableName, "drawable", packPkg)
    if (id == 0) {
        android.util.Log.w("IconPacker", "drawable not found: $drawableName in $packPkg")
        return null
    }
    val attempts: List<() -> Drawable?> = listOf(
        { res.getDrawable(id, packCtx.theme) },
        { res.getDrawable(id, null) },
        { res.getDrawableForDensity(id, 480, packCtx.theme) },
        { res.getDrawableForDensity(id, 320, null) },
    )
    for (attempt in attempts) {
        val d = runCatching(attempt).getOrNull()
        if (d != null) return d
    }
    android.util.Log.w("IconPacker", "all loads failed for $drawableName in $packPkg")
    return null
}

private fun drawableToImageBitmap(d: Drawable, tint: Int? = null): ImageBitmap {
    val size = 192
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    if (d is BitmapDrawable && d.bitmap != null) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            if (tint != null) {
                colorFilter = android.graphics.PorterDuffColorFilter(
                    tint, android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
        }
        canvas.drawBitmap(d.bitmap, null, android.graphics.Rect(0, 0, size, size), paint)
    } else {
        d.setBounds(0, 0, size, size)
        if (tint != null) {
            d.colorFilter = android.graphics.PorterDuffColorFilter(
                tint, android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        d.draw(canvas)
    }
    return bmp.asImageBitmap()
}
