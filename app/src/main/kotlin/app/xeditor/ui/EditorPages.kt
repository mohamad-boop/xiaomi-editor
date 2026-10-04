package app.xeditor.ui

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xeditor.project.Catalog
import app.xeditor.project.ColorItem
import app.xeditor.project.EditSurfaces
import app.xeditor.project.Generators
import app.xeditor.project.IconShape
import app.xeditor.project.ImageItem
import app.xeditor.project.NumberItem
import app.xeditor.project.Surface
import app.xeditor.project.ThemeProject
import app.xeditor.theme.WallpaperLauncher
import app.xeditor.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- shared building blocks ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    state: EditorState,
    vm: EditorViewModel,
    hint: String? = null,
    content: LazyListScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { StatusBanner(state.busy, state.message, vm::dismissMessage) }
            if (hint != null) item {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun GroupCard(title: String?, content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun ColorRow(label: String, value: String?, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val c = parseArgb(value)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        ColorChip(c, Modifier.clickable { picking = true })
        if (value != null) {
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "Reset $label") }
        } else {
            TextButton(onClick = { picking = true }) { Text("Edit") }
        }
    }
    if (picking) {
        ColorPickerDialog(label, c ?: android.graphics.Color.WHITE, { picking = false }) { onChange(it); picking = false }
    }
}

@Composable
fun ImageRow(label: String, file: File, has: Boolean, revision: Int, onPick: () -> Unit, onRemove: () -> Unit) {
    val thumb = rememberFileThumbnail(file, revision, 256)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (has && thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onPick) { Text(if (has) "Change" else "Choose") }
        if (has) IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove $label") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Chips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

/** One image picker shared by a page: remember which key asked, launch, deliver. */
@Composable
fun rememberImagePicker(onPicked: (key: String, uri: android.net.Uri) -> Unit): (String) -> Unit {
    var pendingKey by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val k = pendingKey; pendingKey = null
        if (uri != null && k != null) onPicked(k, uri)
    }
    return { key ->
        pendingKey = key
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

// ---------- catalog-driven surfaces ----------

@Composable
fun SurfacePage(surface: Surface, state: EditorState, vm: EditorViewModel, onBack: () -> Unit, extra: LazyListScope.() -> Unit = {}) {
    val maxFor = surface.items.filterIsInstance<ImageItem>().associate { it.id to it.maxPx }
    val pick = rememberImagePicker { key, uri -> vm.setImage(key, uri, maxFor[key] ?: 1440) }
    PageScaffold(surface.title, onBack, state, vm, surface.hint) {
        when (surface.id) {
            "palette", "syscolors" -> item(key = "preview") { GroupCard("Preview") { ControlCenterPreview(state.edits); PreviewNote() } }
            "volume" -> item(key = "preview") { GroupCard("Preview") { VolumePreview(state.edits); PreviewNote() } }
            "navbar" -> item(key = "preview") { GroupCard("Preview") { NavPreview(state.edits) } }
            "launcher" -> item(key = "preview") { GroupCard("Preview") { HomePreview(state, vm, rows = 4); PreviewNote() } }
        }
        extra()
        for (group in surface.groups) item(key = group.title ?: surface.id) {
            GroupCard(group.title) {
                for (it in group.items) when (it) {
                    is ColorItem -> ColorRow(it.label, state.edits[it.id]) { v -> vm.setValue(it.id, v) }
                    is NumberItem -> NumberRow(it, state.edits[it.id]) { v -> vm.setValue(it.id, v) }
                    is ImageItem -> ImageRow(
                        it.label, vm.edits.imageFile(it.id), vm.edits.hasImage(it.id), state.revision,
                        onPick = { pick(it.id) }, onRemove = { vm.removeImage(it.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberRow(item: NumberItem, value: String?, onChange: (String?) -> Unit) {
    val n = value?.toIntOrNull()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(n?.let { "$it${item.unit}" } ?: "Unchanged", color = MaterialTheme.colorScheme.primary)
            if (n != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "Reset ${item.label}") }
        }
        Slider(
            value = (n ?: item.default).toFloat(),
            onValueChange = { onChange(it.toInt().toString()) },
            valueRange = item.min.toFloat()..item.max.toFloat(),
            steps = (item.max - item.min - 1).coerceAtLeast(0).coerceAtMost(100),
        )
    }
}

// ---------- wallpapers ----------

@Composable
fun WallpaperPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var pickingLock by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setWallpaper(it, pickingLock) }
    }
    fun pick(lock: Boolean) {
        pickingLock = lock
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    PageScaffold("Wallpaper & lock screen", onBack, state, vm) {
        item {
            GroupCard(null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WallpaperSlot("Home", vm.file(ThemeProject.WALLPAPER), state.revision, Modifier.weight(1f), { pick(false) }, { vm.removeWallpaper(false) })
                    WallpaperSlot("Lock screen", vm.file(ThemeProject.LOCK_WALLPAPER), state.revision, Modifier.weight(1f), { pick(true) }, { vm.removeWallpaper(true) })
                }
                FilledTonalButton(onClick = vm::applyWallpapersNow, modifier = Modifier.fillMaxWidth()) { Text("Set as phone wallpaper now") }
                Text(
                    "Set the normal Android way, so it stays even if HyperOS resets the rest of the theme.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { WallpaperLauncher.open(ctx) }) { Text("Browse HyperOS super wallpapers") }
            }
        }
    }
}

@Composable
private fun WallpaperSlot(label: String, file: File, revision: Int, modifier: Modifier, onPick: () -> Unit, onRemove: () -> Unit) {
    val thumb = rememberFileThumbnail(file, revision)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 19.5f).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) Image(thumb, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        Row {
            TextButton(onClick = onPick) { Text(if (thumb == null) "Choose" else "Change") }
            if (thumb != null) IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove $label wallpaper") }
        }
    }
}

// ---------- app icons ----------

@Composable
fun IconsPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit, onOpenIconPack: () -> Unit) {
    val e = state.edits
    val pick = rememberImagePicker { key, uri -> vm.setImage(key, uri, Generators.ICON) }
    PageScaffold("App icons", onBack, state, vm) {
        item { GroupCard("Preview") { HomePreview(state, vm); PreviewNote() } }
        item {
            GroupCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Remove old icons", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Deletes the theme's own app icons, so apps you don't re-theme show their default icon.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(e["icons.removeOld"] == "true", { vm.setValue("icons.removeOld", if (it) "true" else null) })
                }
            }
        }
        item {
            GroupCard("Icon pack") {
                Text(
                    if (e["icons.pack"] == "true") "Icon pack icons included" else "Import a whole icon pack, then pick or skip icons app by app.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenIconPack) { Text(if (e["icons.pack"] == "true") "Edit icons" else "Import icon pack") }
                    if (e["icons.pack"] == "true") TextButton(onClick = { vm.setValue("icons.pack", null) }) { Text("Remove") }
                }
                HorizontalDivider()
                Text("Recolour all icon-pack icons", style = MaterialTheme.typography.labelLarge)
                ColorRow("Icon colour", e["icons.tint"]) { vm.setValue("icons.tint", it) }
                ColorRow("Background plate", e["icons.plate"]) { vm.setValue("icons.plate", it) }
                if (e["icons.plate"] != null) {
                    Chips(IconShape.entries, IconShape.entries.firstOrNull { it.name == e["icons.plateShape"] } ?: IconShape.SQUIRCLE, { it.label }) {
                        vm.setValue("icons.plateShape", it.name)
                    }
                }
            }
        }
        item {
            GroupCard("Icon mask") {
                Text("Shape used for apps the theme has no icon for.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MaskChooser(e["icons.mask"], onShape = { vm.setValue("icons.mask", it) }, onCustom = { pick("icons.mask.custom"); vm.setValue("icons.mask", "custom") })
                if (e["icons.mask"] != null && e["icons.mask"] != "custom") ColorRow("Plate behind icons", e["icons.maskPlate"]) { vm.setValue("icons.maskPlate", it) }
                Text("Folder icon", style = MaterialTheme.typography.labelLarge)
                MaskChooser(e["icons.folder"], onShape = { vm.setValue("icons.folder", it) }, onCustom = { pick("icons.folder.custom"); vm.setValue("icons.folder", "custom") })
                if (e["icons.folder"] != null && e["icons.folder"] != "custom") ColorRow("Folder colour", e["icons.folderColor"]) { vm.setValue("icons.folderColor", it) }
            }
        }
        item {
            GroupCard("Dynamic icons") {
                Text("Calendar", style = MaterialTheme.typography.labelLarge)
                Chips(listOf<Generators.CalendarStyle?>(null) + Generators.CalendarStyle.entries, Generators.CalendarStyle.entries.firstOrNull { it.name == e["icons.calendar"] }, { it?.label ?: "Current" }) {
                    vm.setValue("icons.calendar", it?.name)
                }
                Text("Clock", style = MaterialTheme.typography.labelLarge)
                Chips(listOf<Generators.ClockStyle?>(null) + Generators.ClockStyle.entries, Generators.ClockStyle.entries.firstOrNull { it.name == e["icons.clock"] }, { it?.label ?: "Current" }) {
                    vm.setValue("icons.clock", it?.name)
                }
                if (e["icons.calendar"] != null || e["icons.clock"] != null) {
                    ColorRow("Accent", e["icons.dynAccent"]) { vm.setValue("icons.dynAccent", it) }
                    Chips(IconShape.entries, IconShape.entries.firstOrNull { it.name == e["icons.dynShape"] } ?: IconShape.SQUIRCLE, { it.label }) {
                        vm.setValue("icons.dynShape", it.name)
                    }
                    DynamicPreview(e)
                }
            }
        }
        item {
            GroupCard("Extras") {
                val size = e["icons.size"]?.toIntOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Icon size", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(size?.let { "${it}dp" } ?: "Unchanged", color = MaterialTheme.colorScheme.primary)
                    if (size != null) IconButton(onClick = { vm.setValue("icons.size", null) }) { Icon(Icons.Outlined.Close, "Reset icon size") }
                }
                Slider((size ?: 56).toFloat(), { vm.setValue("icons.size", it.toInt().toString()) }, valueRange = 40f..80f)
                ColorRow("Icon label colour", e["icons.textColor"]) { vm.setValue("icons.textColor", it) }
            }
        }
    }
}

@Composable
private fun MaskChooser(current: String?, onShape: (String?) -> Unit, onCustom: () -> Unit) {
    val options = listOf<String?>(null) + IconShape.entries.map { it.name } + "custom"
    Chips(options, current, { o -> when (o) { null -> "Current"; "custom" -> "Own picture…"; else -> IconShape.valueOf(o).label } }) {
        if (it == "custom") onCustom() else onShape(it)
    }
}

@Composable
private fun DynamicPreview(e: Map<String, String>) {
    val accent = parseArgb(e["icons.dynAccent"]) ?: android.graphics.Color.rgb(230, 81, 0)
    val shape = IconShape.entries.firstOrNull { it.name == e["icons.dynShape"] } ?: IconShape.SQUIRCLE
    val bitmaps by produceState(emptyList<androidx.compose.ui.graphics.ImageBitmap>(), e["icons.calendar"], e["icons.clock"], accent, shape) {
        value = withContext(Dispatchers.Default) {
            buildList {
                Generators.CalendarStyle.entries.firstOrNull { it.name == e["icons.calendar"] }?.let {
                    val f = Generators.calendarFiles(it, accent, shape)["number_27.png"]!!
                    add(android.graphics.BitmapFactory.decodeByteArray(f, 0, f.size).asImageBitmap())
                }
                Generators.ClockStyle.entries.firstOrNull { it.name == e["icons.clock"] }?.let {
                    val f = Generators.clockFiles(it, accent, shape)["icon_bg.png"]!!
                    add(android.graphics.BitmapFactory.decodeByteArray(f, 0, f.size).asImageBitmap())
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        bitmaps.forEach { Image(it, null, Modifier.size(64.dp)) }
    }
}

// ---------- status bar ----------

@Composable
fun StatusBarPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val e = state.edits
    PageScaffold("Status bar", onBack, state, vm, "Battery keeps the system style; signal and Wi-Fi are drawn fresh in the style you pick.") {
        item {
            GroupCard("Signal") {
                StylePreviewRow(Generators.SignalStyle.entries.map { s -> s.name to { lvl: Int -> Generators.signalIcon(s, lvl, android.graphics.Color.WHITE) } })
                Chips(listOf<Generators.SignalStyle?>(null) + Generators.SignalStyle.entries, Generators.SignalStyle.entries.firstOrNull { it.name == e["status.signal"] }, { it?.label ?: "Current" }) {
                    vm.setValue("status.signal", it?.name)
                }
            }
        }
        item {
            GroupCard("Wi-Fi") {
                StylePreviewRow(Generators.WifiStyle.entries.map { s -> s.name to { lvl: Int -> Generators.wifiIcon(s, lvl, android.graphics.Color.WHITE) } })
                Chips(listOf<Generators.WifiStyle?>(null) + Generators.WifiStyle.entries, Generators.WifiStyle.entries.firstOrNull { it.name == e["status.wifi"] }, { it?.label ?: "Current" }) {
                    vm.setValue("status.wifi", it?.name)
                }
            }
        }
        item { GroupCard("In context") { ControlCenterPreview(e); PreviewNote() } }
        item {
            GroupCard("Text") {
                ColorRow("Status bar text & clock", e["sys.sbText"]) { vm.setValue("sys.sbText", it) }
                ColorRow("Over light wallpapers", e["sys.sbTextDark"]) { vm.setValue("sys.sbTextDark", it) }
            }
        }
    }
}

@Composable
private fun StylePreviewRow(styles: List<Pair<String, (Int) -> android.graphics.Bitmap>>) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0xFF202124)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        styles.forEach { (key, draw) ->
            val img = remember(key) { draw(3).asImageBitmap() }
            Image(img, key, Modifier.size(28.dp))
        }
    }
}

// ---------- fingerprint ----------

@Composable
fun FingerprintPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val e = state.edits
    val fod = state.fod
    val iconPick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setFingerIcon) }
    var effect by remember { mutableStateOf(Generators.FingerEffect.entries.firstOrNull { it.name == e["finger.anim"] } ?: Generators.FingerEffect.PULSE) }
    val anim = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.setFingerAnimation(it, effect) } }
    val hasIcon = vm.edits.hasImage(FINGER_ICON_SRC)
    val hasAnim = e["finger.anim"] != null

    PageScaffold("Fingerprint", onBack, state, vm, "Shown only on phones with an in-screen fingerprint sensor.") {
        item { GroupCard("Preview") { FingerprintPreview(state, vm) } }
        item {
            GroupCard("Fingerprint icon") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { iconPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (hasIcon) "Change picture" else "Choose picture")
                    }
                    if (hasIcon) TextButton(onClick = vm::removeFingerIcon) { Text("Remove") }
                }
                SizeSlider("Size", e[FINGER_ICON_SIZE]?.toIntOrNull() ?: DEFAULT_ICON_PCT, fod, enabled = hasIcon) { vm.setFingerIconSize(it) }
            }
        }
        item {
            GroupCard("Unlock animation") {
                Text(
                    "A GIF, or a picture to animate, for when a finger touches the sensor. It repeats while the finger is on the sensor, " +
                        "whichever animation style is chosen in Settings. Make it bigger than the sensor for effects that spill around it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Chips(Generators.FingerEffect.entries, effect, { it.label }) {
                    effect = it
                    if (hasAnim) vm.setFingerAnimOptions(effect = it)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { anim.launch("image/*") }) { Text(if (hasAnim) "Replace" else "Choose picture or GIF") }
                    if (hasAnim) TextButton(onClick = vm::clearFingerAnimation) { Text("Remove") }
                }
                SizeSlider("Size", e[FINGER_ANIM_SIZE]?.toIntOrNull() ?: DEFAULT_ANIM_PCT, fod, enabled = hasAnim, max = 600) { vm.setFingerAnimOptions(percent = it) }
                ThemeFodSwitch(state, vm)
                Text("Compression", style = MaterialTheme.typography.labelLarge)
                val colors = e[FINGER_ANIM_COLORS]?.toIntOrNull() ?: 256
                Chips(app.xeditor.util.COLOR_CHOICES.map { it.first }, colors, { c -> app.xeditor.util.COLOR_CHOICES.first { it.first == c }.second }) {
                    if (hasAnim) vm.setFingerAnimOptions(colors = it)
                }
                if (hasAnim) {
                    val frameBytes = (1..Catalog.FINGER_FRAMES).sumOf { vm.edits.imageFile("finger.anim.$it").length() }
                    Text(
                        "In the theme: ${formatSize(frameBytes * Catalog.fingerStyles.size)} (24 frames for each of the ${Catalog.fingerStyles.size} animation styles).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Size as a percentage of the sensor, up to 4× for effects larger than it. The value
 * is applied when the finger lifts so each drag re-renders once.
 */
@Composable
private fun SizeSlider(label: String, percent: Int, fod: app.xeditor.util.Fod?, enabled: Boolean, max: Int = 400, onDone: (Int) -> Unit) {
    var v by remember(percent) { mutableStateOf(percent.toFloat()) }
    val px = fod?.pictureSizeFor(v.toInt())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("${v.toInt()}% of sensor" + (px?.let { " · $it px" } ?: ""), color = MaterialTheme.colorScheme.primary)
    }
    Slider(v, { v = it }, onValueChangeFinished = { onDone(v.toInt()) }, valueRange = 20f..max.toFloat(), enabled = enabled)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf(60, 80, 100, 150, 250, 400, 600).filter { it <= max }).forEach { p ->
            FilterChip(selected = v.toInt() == p, onClick = { v = p.toFloat(); onDone(p) }, label = { Text("$p%") }, enabled = enabled)
        }
    }
}

// ---------- hide elements ----------

@Composable
fun HidePage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    PageScaffold("Hide elements", onBack, state, vm, "Switch off parts of the system UI. Each one comes back when you uncheck it.") {
        item {
            GroupCard(null) {
                Catalog.hideItems.forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setValue(h.id, if (state.edits[h.id] == "true") null else "true") },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(state.edits[h.id] == "true", null)
                        Spacer(Modifier.width(8.dp))
                        Text(h.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

// ---------- fonts ----------

@Composable
fun FontsPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val hasFont = state.components.any { it.name == "fonts" }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::setFont) }
    val current = remember(state.revision, hasFont) {
        if (!hasFont) null else runCatching { FontFamily(Typeface.createFromFile(vm.file(ThemeProject.FONT_FILES.first()))) }.getOrNull()
    }
    val systemFonts by produceState(emptyList<File>()) {
        value = withContext(Dispatchers.IO) {
            File("/system/fonts").listFiles().orEmpty()
                .filter { it.extension.lowercase() in setOf("ttf", "otf") && !it.name.contains("Emoji", true) && it.length() < 12_000_000 }
                .distinctBy { it.nameWithoutExtension.substringBefore('-') }
                .sortedBy { it.name.lowercase() }
        }
    }
    PageScaffold("Fonts", onBack, state, vm) {
        item {
            GroupCard("Theme font") {
                if (current != null) Text("The quick brown fox jumps over the lazy dog 0123456789", fontFamily = current, style = MaterialTheme.typography.titleMedium)
                else Text("System font", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")) }) {
                        Text("Choose a custom .ttf font")
                    }
                    if (hasFont) TextButton(onClick = { vm.removeComponent("fonts") }) { Text("Remove") }
                }
            }
        }
        item { Text("Built-in fonts on this phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(systemFonts, key = { it.path }) { f ->
            val fam = remember(f) { runCatching { FontFamily(Typeface.createFromFile(f)) }.getOrNull() }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.setSystemFont(f) }.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.nameWithoutExtension, fontFamily = fam, style = MaterialTheme.typography.titleMedium)
                    Text(formatSize(f.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Use", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------- description ----------

@Composable
fun DescriptionPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val m = state.meta
    PageScaffold("Description", onBack, state, vm) {
        item {
            GroupCard(null) {
                OutlinedTextField(m.title, { vm.updateMeta(m.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(m.designer, { vm.updateMeta(m.copy(designer = it)) }, Modifier.fillMaxWidth(), label = { Text("Designer") }, singleLine = true)
                OutlinedTextField(m.author, { vm.updateMeta(m.copy(author = it)) }, Modifier.fillMaxWidth(), label = { Text("Author") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(m.version, { vm.updateMeta(m.copy(version = it)) }, Modifier.weight(1f), label = { Text("Version") }, singleLine = true)
                    OutlinedTextField(m.uiVersion, { vm.updateMeta(m.copy(uiVersion = it)) }, Modifier.widthIn(max = 150.dp), label = { Text("MIUI version") }, singleLine = true)
                }
                OutlinedTextField(m.description, { vm.updateMeta(m.copy(description = it)) }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3)
            }
        }
    }
}

// ---------- preview images ----------

private val PREVIEW_KINDS = listOf("cover" to "Cover", "launcher" to "Home screen", "lockscreen" to "Lock screen", "icons" to "Icons", "fonts" to "Fonts")

@Composable
fun PreviewPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(PREVIEW_KINDS.first().first) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.addPreview(kind, it) } }
    val files = remember(state.revision) {
        vm.file("preview").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
    }
    PageScaffold("Preview images", onBack, state, vm, "Pictures the Themes app shows for this theme.") {
        item {
            GroupCard("Add") {
                Chips(PREVIEW_KINDS.map { it.first }, kind, { k -> PREVIEW_KINDS.first { it.first == k }.second }) { kind = it }
                FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Add picture") }
            }
        }
        items(files, key = { it.name }) { f ->
            val t = rememberFileThumbnail(f, state.revision, 300)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp, 120.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    t?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(12.dp))
                Text(f.nameWithoutExtension, Modifier.weight(1f))
                IconButton(onClick = { vm.removePath("preview/${f.name}") }) { Icon(Icons.Outlined.DeleteOutline, "Remove ${f.name}") }
            }
        }
    }
}

// ---------- presets & reset ----------

@Composable
fun PresetsPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var toReset by remember { mutableStateOf(setOf<String>()) }
    PageScaffold("Presets & reset", onBack, state, vm) {
        item {
            GroupCard("Presets") {
                Text("Save the current choices under a name and use them again in another theme.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Preset name") }, singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        if (name.trim() in state.presets) confirm = "A preset named “${name.trim()}” already exists. Replace it?" to { vm.savePreset(name) }
                        else vm.savePreset(name)
                    }) { Text("Save") }
                }
                if (state.presets.isEmpty()) Text("No presets saved yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.presets.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { confirm = "Apply “$p”? This replaces every choice in the current theme." to { vm.applyPreset(p) } }) { Text("Apply") }
                        IconButton(onClick = { confirm = "Delete the preset “$p”?" to { vm.deletePreset(p) } }) { Icon(Icons.Outlined.DeleteOutline, "Delete $p") }
                    }
                }
            }
        }
        item {
            GroupCard("Reset edits") {
                Text("Pick the surfaces to undo. Only edited ones can be selected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditSurfaces.all.forEach { (label, _) ->
                    val edited = label in state.editedSurfaces
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(label in toReset, { on -> toReset = if (on) toReset + label else toReset - label }, enabled = edited)
                        Text(label, color = if (edited) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                OutlinedButton(
                    onClick = { vm.resetSurfaces(toReset); toReset = emptySet() },
                    enabled = toReset.isNotEmpty(),
                ) { Text("Reset selected") }
            }
        }
    }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("No") } },
        )
    }
}

// ---------- everything in the theme ----------

@Composable
fun ComponentsPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    PageScaffold("Everything in this theme", onBack, state, vm, "Parts you don't edit are kept as they are. Remove any you don't want.") {
        items(state.components, key = { it.name }) { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        (if (c.label != c.name) "${c.name} · " else "") + formatSize(c.sizeBytes),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.removeComponent(c.name) }) { Icon(Icons.Outlined.DeleteOutline, "Remove ${c.label}") }
            }
        }
    }
}

/**
 * HyperOS only plays a theme's unlock animation while the system setting
 * `is_theme_fod_animation` is on; otherwise it uses the style picked in Settings.
 */
@Composable
private fun ThemeFodSwitch(state: EditorState, vm: EditorViewModel) {
    val on = state.themeFodAnimation
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Use the theme's unlock animation", style = MaterialTheme.typography.bodyLarge)
            Text(
                when (on) {
                    null -> "Needs Shizuku to read and change this system setting."
                    true -> "On. Turn off to go back to the animation chosen in Settings."
                    false -> "Off: HyperOS plays the animation chosen in Settings instead. Experimental on HyperOS 3 — it may still ignore theme animations."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on == true, onCheckedChange = { vm.setThemeFodAnimation(it) }, enabled = on != null)
    }
}
