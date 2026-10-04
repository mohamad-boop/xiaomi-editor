package app.xeditor.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xeditor.project.Catalog
import app.xeditor.project.Generators
import app.xeditor.project.IconShape
import app.xeditor.project.ThemeBuilder
import app.xeditor.project.ThemeProject
import app.xeditor.util.Fod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

/** Resolves the colour a preview should show: first edited key wins, else the stock look. */
private fun Map<String, String>.col(default: Long, vararg keys: String): Color =
    keys.firstNotNullOfOrNull { parseArgb(this[it]) }?.let { Color(it) } ?: Color(default)

@Composable
fun PreviewNote() {
    Text(
        "Approximate preview — HyperOS draws the real thing.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Screen-shaped frame; content inside is laid out in screen fractions. */
@Composable
private fun PhoneFrame(aspect: Float, height: Int = 420, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(height.dp).aspectRatio(aspect)
                .clip(RoundedCornerShape(22.dp))
                .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
        ) { content() }
    }
}

@Composable
private fun WallpaperBackground(vm: EditorViewModel, revision: Int, lock: Boolean) {
    val lockFile = vm.file(ThemeProject.LOCK_WALLPAPER)
    val file = if (lock && lockFile.exists()) lockFile else vm.file(ThemeProject.WALLPAPER)
    val wp = rememberFileThumbnail(file, revision, 540)
    if (wp != null) Image(wp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF243B55), Color(0xFF141E30)))))
}

// ---------- fingerprint, at its real size and place ----------

@Composable
fun FingerprintPreview(state: EditorState, vm: EditorViewModel) {
    val fod = state.fod ?: return
    var showAnim by remember { mutableStateOf(false) }
    val hasAnim = state.edits["finger.anim"] != null
    val hidden = state.edits["hide.fingerIcon"] == "true"
    val icon = rememberFileThumbnail(vm.edits.imageFile(Catalog.FINGER_ICON), state.revision, 2048)
    val iconPx = remember(state.revision) { bitmapSize(vm.edits.imageFile(Catalog.FINGER_ICON).path) }
    val animPx = remember(state.revision) { bitmapSize(vm.edits.imageFile("finger.anim.1").path) }

    // Frames play at ~24 fps while "unlock animation" is on.
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(showAnim, state.revision) {
        if (!showAnim || !hasAnim) { frame = null; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            val frames = (1..Catalog.FINGER_FRAMES).mapNotNull { i ->
                vm.edits.imageFile("finger.anim.$i").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
            }
            var i = 0
            while (isActive && frames.isNotEmpty()) {
                val t = SystemClock.uptimeMillis()
                frame = frames[i % frames.size]; i++
                delay((42 - (SystemClock.uptimeMillis() - t)).coerceAtLeast(1))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PhoneFrame(fod.screenW.toFloat() / fod.screenH) {
            WallpaperBackground(vm, state.revision, lock = true)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val k = maxWidth / fod.screenW // dp per screen pixel
                Text(
                    "12:45", color = Color.White, fontSize = (maxWidth.value * 0.2f).sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.12f),
                )
                // Sensor outline (dashed look via low alpha) — where the finger actually goes.
                Box(
                    Modifier.offset(k * fod.x, k * fod.y).size(k * fod.w, k * fod.h)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                )
                fun placed(px: Pair<Int, Int>?): Modifier {
                    val (w, h) = px ?: return Modifier
                    val dw = w * fod.drawScale; val dh = h * fod.drawScale
                    return Modifier.offset(k * (fod.centerX - dw / 2), k * (fod.centerY - dh / 2)).size(k * dw, k * dh)
                }
                val f = frame
                if (f != null) Image(f, "Unlock animation", placed(animPx))
                else if (icon != null && !hidden) Image(icon, "Fingerprint icon", placed(iconPx))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !showAnim, onClick = { showAnim = false }, label = { Text("Icon") })
            FilterChip(selected = showAnim, onClick = { showAnim = true }, label = { Text("Unlock animation") }, enabled = hasAnim)
        }
        Text(
            buildString {
                append(
                    if (fod.known) "Drawn at your sensor's real position and size (${fod.w} px circle)."
                    else "Sensor drawn at the standard Xiaomi position and size (${fod.w} px circle).",
                )
                iconPx?.let { append(" Icon: ${it.first} px → ${(it.first * fod.drawScale).toInt()} px on screen.") }
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun bitmapSize(path: String): Pair<Int, Int>? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    return if (o.outWidth > 0) o.outWidth to o.outHeight else null
}

// ---------- Control Center + notification ----------

@Composable
fun ControlCenterPreview(e: Map<String, String>) {
    val panel = if (e["hide.notifBg"] == "true") Color(0x33000000) else e.col(0xF0202124, "sys.qsPanel", "palette.surface")
    val iconOn = e.col(0xFF3482FF, "sys.ccIconOn", "palette.accent")
    val iconOff = e.col(0xFF9AA0A6, "sys.ccIconOff", "palette.secondaryText")
    val textOn = e.col(0xFFFFFFFF, "sys.ccTextOn", "palette.text")
    val textOff = e.col(0xFFBDBDBD, "sys.ccTextOff", "palette.secondaryText")
    val label = e.col(0xFFFFFFFF, "sys.qsLabel", "palette.text")
    val track = e.col(0x40FFFFFF, "sys.brTrack", "palette.surfaceVariant")
    val fill = e.col(0xFFFFFFFF, "sys.brFill", "palette.accent")
    val dots = e.col(0xFFFFFFFF, "sys.qsDots")
    val sb = e.col(0xFFFFFFFF, "sys.sbText")
    val data = e.col(0xFF9AA0A6, "sys.qsData", "palette.secondaryText")
    val nBg = e.col(0xFF303134, "sys.headsUp", "palette.surface")
    val nTitle = e.col(0xFFFFFFFF, "sys.notifTitle", "palette.text")
    val nText = e.col(0xFFBDBDBD, "sys.notifText", "palette.secondaryText")
    val nTime = e.col(0xFF9AA0A6, "sys.notifTime", "palette.secondaryText")

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A4A6B), Color(0xFF1B2233)))).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("12:45", color = sb, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("5G  ●●●  100%", color = sb, fontSize = 11.sp)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcCard(Icons.Filled.Wifi, "Wi-Fi", true, iconOn, iconOff, textOn, textOff, Modifier.weight(1f))
                CcCard(Icons.Filled.Bluetooth, "Bluetooth", false, iconOn, iconOff, textOn, textOff, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                CcTile(Icons.Filled.FlashlightOn, "Torch", true, iconOn, iconOff, label)
                CcTile(Icons.Filled.AirplanemodeActive, "Airplane", false, iconOn, iconOff, label)
                CcTile(Icons.Filled.NotificationsActive, "Sound", true, iconOn, iconOff, label)
                CcTile(Icons.Filled.MusicNote, "Media", false, iconOn, iconOff, label)
            }
            Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(13.dp)).background(track)) {
                Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight().clip(RoundedCornerShape(13.dp)).background(fill))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("1.2 GB used this month", color = data, fontSize = 11.sp, modifier = Modifier.weight(1f))
                repeat(3) { i -> Box(Modifier.padding(2.dp).size(6.dp).clip(CircleShape).background(dots.copy(alpha = if (i == 0) 1f else 0.4f))) }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(nBg).padding(12.dp)) {
            Row {
                Text("Messages", color = nTitle, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("now", color = nTime, fontSize = 11.sp)
            }
            Text("Your theme looks great!", color = nText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CcCard(icon: ImageVector, text: String, on: Boolean, iconOn: Color, iconOff: Color, textOn: Color, textOff: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) iconOn else iconOff, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (on) textOn else textOff, fontSize = 12.sp)
    }
}

@Composable
private fun CcTile(icon: ImageVector, text: String, on: Boolean, iconOn: Color, iconOff: Color, label: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (on) iconOn.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (on) iconOn else iconOff, modifier = Modifier.size(22.dp)) }
        Text(text, color = label, fontSize = 10.sp)
    }
}

// ---------- volume dialog ----------

@Composable
fun VolumePreview(e: Map<String, String>) {
    val bg = e.col(0xE6202124, "vol.bg", "palette.surface")
    val collapsed = e.col(0xE62A2B2F, "vol.bgCollapsed", "palette.surfaceVariant")
    val fill = e.col(0xFF3482FF, "vol.fill", "palette.accent")
    val disabled = e.col(0x66FFFFFF, "vol.disabled")
    val tint = e.col(0xFFFFFFFF, "vol.tintLight", "palette.text")
    val selected = e.col(0xFF3482FF, "vol.selected", "palette.surfaceVariant")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A4A6B), Color(0xFF1B2233)))).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        for ((level, color) in listOf(0.7f to fill, 0.35f to disabled)) {
            Box(Modifier.width(52.dp).height(170.dp).clip(RoundedCornerShape(18.dp)).background(if (color == fill) bg else collapsed)) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(level).background(color))
                Icon(Icons.Filled.MusicNote, null, tint = tint, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).size(20.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (i == 0) selected else bg), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ---------- navigation handle ----------

@Composable
fun NavPreview(e: Map<String, String>) {
    val hidden = e["hide.handle"] == "true"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((bg, key, def) in listOf(Triple(Color(0xFFF2F2F2), "nav.handleOnLight", 0xFF000000), Triple(Color(0xFF1B1B1F), "nav.handleOnDark", 0xFFFFFFFF))) {
            Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.BottomCenter) {
                if (!hidden) Box(Modifier.padding(bottom = 12.dp).width(90.dp).height(5.dp).clip(CircleShape).background(e.col(def, key)))
            }
        }
    }
}

// ---------- home screen: icons and grid ----------

private data class PreviewIcon(val label: String, val bitmap: ImageBitmap)

@Composable
fun HomePreview(state: EditorState, vm: EditorViewModel, rows: Int = 2) {
    val ctx = LocalContext.current
    val e = state.edits
    val columns = e["grid.columns"]?.toIntOrNull() ?: 4
    val sizeDp = e["icons.size"]?.toIntOrNull() ?: 56
    val labelColor = e.col(0xFFFFFFFF, "icons.textColor")
    val icons by produceState(emptyList<PreviewIcon>(), state.revision, e) {
        value = withContext(Dispatchers.Default) { buildPreviewIcons(ctx, vm, e, columns * rows) }
    }
    val aspect = state.fod?.let { it.screenW.toFloat() / it.screenH } ?: 0.46f
    PhoneFrame(aspect, 380) {
        WallpaperBackground(vm, state.revision, lock = false)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A phone is ~400 dp wide, so scale real dp to this frame.
            val k = maxWidth.value / 400f
            val cellW = maxWidth / columns
            Column(Modifier.fillMaxSize().padding(top = maxHeight * 0.1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                icons.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { ic ->
                            Column(Modifier.width(cellW), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(ic.bitmap, ic.label, Modifier.size((sizeDp * k).dp))
                                Text(ic.label, color = labelColor, fontSize = (11 * k).sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (e["hide.searchBar"] != "true") {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.16f).fillMaxWidth(0.85f).height((40 * k).dp)
                        .clip(RoundedCornerShape(50)).background(e.col(0x33FFFFFF, "home.searchBgDark", "home.searchBgLight"))
                        .border(1.dp, e.col(0x00000000, "home.searchStrokeDark", "home.searchStrokeLight"), RoundedCornerShape(50)),
                )
            }
            if (e["hide.handle"] != "true") {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).width(maxWidth * 0.3f).height(3.dp)
                        .clip(CircleShape).background(e.col(0xFFFFFFFF, "nav.handleOnDark")),
                )
            }
        }
    }
}

/**
 * Renders a handful of the phone's apps the way the theme would show them: icon-pack
 * icon (recoloured) when the pack covers the app, else the app's own icon cut to the
 * chosen mask; dynamic calendar/clock and the folder icon when set.
 */
private fun buildPreviewIcons(ctx: android.content.Context, vm: EditorViewModel, e: Map<String, String>, count: Int): List<PreviewIcon> {
    val pm = ctx.packageManager
    val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }
    val shape = { k: String -> e[k]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } }
    val tint = parseArgb(e["icons.tint"]); val plate = parseArgb(e["icons.plate"])
    val plateShape = shape("icons.plateShape") ?: IconShape.SQUIRCLE
    val mask = shape("icons.mask"); val maskPlate = parseArgb(e["icons.maskPlate"])
    val pack = vm.edits.blob(ThemeBuilder.ICON_PACK_BLOB).takeIf { e["icons.pack"] == "true" && it.exists() }
        ?.let { runCatching { ZipFile(it) }.getOrNull() }
    val out = mutableListOf<PreviewIcon>()
    try {
        val accent = parseArgb(e["icons.dynAccent"]) ?: android.graphics.Color.rgb(230, 81, 0)
        val dynShape = shape("icons.dynShape") ?: IconShape.SQUIRCLE
        Generators.CalendarStyle.entries.firstOrNull { it.name == e["icons.calendar"] }?.let {
            val b = Generators.calendarFiles(it, accent, dynShape)["number_${java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)}.png"]!!
            out += PreviewIcon("Calendar", BitmapFactory.decodeByteArray(b, 0, b.size).asImageBitmap())
        }
        Generators.ClockStyle.entries.firstOrNull { it.name == e["icons.clock"] }?.let {
            val b = Generators.clockFiles(it, accent, dynShape)["icon_bg.png"]!!
            out += PreviewIcon("Clock", BitmapFactory.decodeByteArray(b, 0, b.size).asImageBitmap())
        }
        val folder = shape("icons.folder")
        if (folder != null) {
            val b = Generators.folderIcon(folder, parseArgb(e["icons.folderColor"]) ?: 0x66FFFFFF)
            out += PreviewIcon("Folder", BitmapFactory.decodeByteArray(b, 0, b.size).asImageBitmap())
        }
        for (ri in apps) {
            if (out.size >= count) break
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val packed = pack?.getEntry("$pkg.${ri.activityInfo.name}.png") ?: pack?.getEntry("$pkg.png")
            val bmp = if (packed != null) {
                val raw = pack!!.getInputStream(packed).use { BitmapFactory.decodeStream(it) }
                if (tint == null && plate == null) raw else Generators.tintIcon(raw, tint, plate, plateShape)
            } else {
                val icon = drawableToBitmap(ri.loadIcon(pm))
                if (mask != null) masked(icon, mask, maskPlate) else icon
            }
            out += PreviewIcon(label, bmp.asImageBitmap())
        }
    } finally {
        pack?.close()
    }
    return out.take(count)
}

private fun drawableToBitmap(d: android.graphics.drawable.Drawable): Bitmap {
    val b = Bitmap.createBitmap(Generators.ICON, Generators.ICON, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, Generators.ICON, Generators.ICON); d.draw(Canvas(b))
    return b
}

private fun masked(icon: Bitmap, shape: IconShape, plate: Int?): Bitmap {
    val s = Generators.ICON.toFloat()
    val out = Bitmap.createBitmap(Generators.ICON, Generators.ICON, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawPath(shape.path(s), p.apply { color = plate ?: android.graphics.Color.WHITE })
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    c.drawBitmap(icon, null, RectF(0f, 0f, s, s), p)
    return out
}

// ---------- everything at once ----------

@Composable
fun FullPreviewPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    PageScaffold("Preview theme", onBack, state, vm, "An approximation of how the theme will look on this phone.") {
        item { GroupCard("Lock screen & fingerprint") { FingerprintPreview(state, vm) } }
        item { GroupCard("Home screen") { HomePreview(state, vm, rows = 4) } }
        item { GroupCard("Control Center & notifications") { ControlCenterPreview(state.edits) } }
        item { GroupCard("Volume") { VolumePreview(state.edits) } }
        item { GroupCard("Navigation handle") { NavPreview(state.edits) } }
    }
}
