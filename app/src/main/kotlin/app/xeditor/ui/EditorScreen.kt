package app.xeditor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xeditor.project.Catalog
import app.xeditor.util.formatSize

private enum class Page(val title: String, val icon: ImageVector, val surface: String?) {
    WALLPAPER("Wallpaper & lock screen", Icons.Outlined.Wallpaper, null),
    ICONS("App icons", Icons.Outlined.Apps, "App icons"),
    LAUNCHER("Launcher", Icons.Outlined.Home, "Launcher"),
    RECENTS("Recents background", Icons.Outlined.ViewCarousel, "Recents background"),
    PALETTE("Theme palette", Icons.Outlined.Palette, "Theme palette"),
    STATUS("Status bar", Icons.Outlined.SignalCellularAlt, "Status bar"),
    NOTIFICATIONS("Notifications panel", Icons.Outlined.Notifications, "Notifications panel"),
    SYSCOLORS("System colours", Icons.Outlined.Contrast, "System colours"),
    VOLUME("Volume dialog", Icons.Outlined.VolumeUp, "Volume dialog"),
    NAVBAR("Navigation bar", Icons.Outlined.Navigation, "Navigation bar"),
    FINGERPRINT("Fingerprint", Icons.Outlined.Fingerprint, "Fingerprint"),
    HIDE("Hide elements", Icons.Outlined.VisibilityOff, "Hidden elements"),
    FONTS("Fonts", Icons.Outlined.TextFields, null),
    APPS("Apps", Icons.Outlined.Widgets, "Apps"),
    SOUNDS("Sounds", Icons.Outlined.MusicNote, null),
    BOOT("Boot animation", Icons.Outlined.PlayCircle, null),
    DESCRIPTION("Description", Icons.Outlined.Description, null),
    PREVIEW_ALL("Preview theme", Icons.Outlined.Visibility, null),
    PREVIEW("Preview images", Icons.Outlined.PhotoLibrary, null),
    PRESETS("Presets & reset", Icons.Outlined.SettingsSuggest, null),
    CONTENTS("Everything in theme", Icons.Outlined.Inventory2, null),
    MIX("Mix & match", Icons.Outlined.Layers, null),
    ADVANCED("Advanced", Icons.Outlined.Code, "Advanced"),
}

private val SECTIONS = listOf(
    "Home & lock screen" to listOf(Page.WALLPAPER, Page.ICONS, Page.LAUNCHER, Page.RECENTS),
    "System UI" to listOf(Page.PALETTE, Page.STATUS, Page.NOTIFICATIONS, Page.SYSCOLORS, Page.VOLUME, Page.NAVBAR, Page.FINGERPRINT, Page.HIDE),
    "Apps, fonts, sounds & boot" to listOf(Page.FONTS, Page.APPS, Page.SOUNDS, Page.BOOT),
    "Theme details" to listOf(Page.PREVIEW_ALL, Page.DESCRIPTION, Page.PREVIEW, Page.PRESETS, Page.CONTENTS, Page.MIX, Page.ADVANCED),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onOpenIcons: () -> Unit,
    onOpenBootAnimation: () -> Unit,
    vm: EditorViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    val back = { page = null }

    when (page) {
        Page.WALLPAPER -> return WallpaperPage(state, vm, back)
        Page.ICONS -> return IconsPage(state, vm, back, onOpenIcons)
        Page.LAUNCHER -> return SurfacePage(Catalog.launcher, state, vm, back)
        Page.RECENTS -> return SurfacePage(Catalog.recents, state, vm, back)
        Page.PALETTE -> return SurfacePage(Catalog.palette, state, vm, back)
        Page.STATUS -> return StatusBarPage(state, vm, back)
        Page.NOTIFICATIONS -> return SurfacePage(Catalog.notifications, state, vm, back)
        Page.SYSCOLORS -> return SurfacePage(Catalog.systemColours, state, vm, back)
        Page.VOLUME -> return SurfacePage(Catalog.volume, state, vm, back)
        Page.NAVBAR -> return SurfacePage(Catalog.navBar, state, vm, back)
        Page.FINGERPRINT -> return FingerprintPage(state, vm, back)
        Page.HIDE -> return HidePage(state, vm, back)
        Page.FONTS -> return FontsPage(state, vm, back)
        Page.APPS -> return SurfacePage(Catalog.apps, state, vm, back)
        Page.DESCRIPTION -> return DescriptionPage(state, vm, back)
        Page.PREVIEW -> return PreviewPage(state, vm, back)
        Page.PREVIEW_ALL -> return FullPreviewPage(state, vm, back)
        Page.SOUNDS -> return SoundsPage(state, vm, back)
        Page.MIX -> return MixMatchPage(state, vm, back)
        Page.ADVANCED -> return AdvancedPage(state, vm, back)
        Page.PRESETS -> return PresetsPage(state, vm, back)
        Page.CONTENTS -> return ComponentsPage(state, vm, back)
        Page.BOOT, null -> Unit
    }

    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refresh(); vm.restoreSoundsIfNeeded() }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val openMtz = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importMtz) }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::listFolder) }
    var menu by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    if (showExport) ExportDialog(state, vm) { showExport = false }

    val onStartPage = !state.hasProject || state.showStart || state.folderThemes != null || state.installedThemes != null
    // From a theme, back goes to the start page rather than closing the app.
    androidx.activity.compose.BackHandler(enabled = !onStartPage) { vm.goHome() }
    // From the start page with a theme open, back returns to that theme's lists closing first.
    androidx.activity.compose.BackHandler(enabled = state.hasProject && (state.folderThemes != null || state.installedThemes != null)) { vm.closeFolder() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (state.hasProject && !onStartPage) state.meta.title else "Theme builder", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (!onStartPage) IconButton(onClick = vm::goHome) { Icon(Icons.Outlined.Home, "Start page") }
            },
            actions = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Open .mtz…") }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                        onClick = { menu = false; openMtz.launch(arrayOf("*/*")) },
                    )
                    DropdownMenuItem(
                        text = { Text("Installed themes (Shizuku)") }, leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                        onClick = { menu = false; vm.listInstalled() },
                    )
                    DropdownMenuItem(
                        text = { Text("Themes in a folder…") }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                        onClick = { menu = false; openFolder.launch(null) },
                    )
                    DropdownMenuItem(
                        text = { Text("New blank theme") }, leadingIcon = { Icon(Icons.Outlined.NoteAdd, null) },
                        onClick = { menu = false; vm.newTheme() },
                    )
                }
            },
        )

        if (onStartPage) {
            PickTheme(
                state, vm,
                onOpen = { openMtz.launch(arrayOf("*/*")) },
                onFolder = { openFolder.launch(null) },
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { StatusBanner(state.busy, state.message, vm::dismissMessage) }
            if (state.justInstalled != null && state.busy == null) item(span = { GridItemSpan(maxLineSpan) }) {
                FilledTonalButton(onClick = { vm.openThemesApp(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Open the Themes app") }
            }
            for ((title, pages) in SECTIONS) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
                items(pages, key = { it.name }) { p ->
                    val edited = p.surface != null && p.surface in state.editedSurfaces
                    Tile(p, edited) { if (p == Page.BOOT) onOpenBootAnimation() else page = p }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (state.canApplyDirectly)
                        "HyperOS resets themes it didn't sell, usually within a few hours or after a reboot. Use Apply & keep and the Keeper tab puts yours back automatically."
                    else "This HyperOS version's Themes app only applies themes that carry a Xiaomi licence, and removes others when you tap Apply. " +
                        "Without root, the last step has to go through MIUI Theme Editor, which writes those licences.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.editedSurfaces.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Edited: " + state.editedSurfaces.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { showExport = true }, enabled = state.busy == null) { Text("Export") }
                if (state.canApplyDirectly) {
                    FilledTonalButton(onClick = { vm.apply(ctx, keep = false) }, enabled = state.busy == null) { Text("Apply") }
                    Button(onClick = { vm.apply(ctx, keep = true) }, enabled = state.busy == null, modifier = Modifier.weight(1f)) {
                        Text("Apply & keep")
                    }
                } else {
                    Button(
                        onClick = { vm.handOffToThemeEditor(ctx) }, enabled = state.busy == null && state.hasThemeEditor,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.hasThemeEditor) "Install via Theme Editor" else "Apply unavailable") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tile(p: Page, edited: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().height(104.dp),
    ) {
        Box {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(p.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            }
            if (edited) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun PickTheme(state: EditorState, vm: EditorViewModel, onOpen: () -> Unit, onFolder: () -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { StatusBanner(state.busy, state.message, vm::dismissMessage) }
        item { Icon(Icons.Outlined.Brush, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary) }
        item { Text("Build a HyperOS theme", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text(
                "Start from scratch, or build on an existing theme — a .mtz, one from a folder, or one installed on your phone. " +
                    "Add icons, fonts, wallpapers, colours, sounds and a boot animation, then export or apply it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        item { Button(onClick = vm::newTheme, modifier = Modifier.fillMaxWidth()) { Text("Start from scratch") } }
        if (state.shizuku != app.xeditor.shizuku.ShizukuShell.Status.READY) item {
            ShizukuCard(
                state.shizuku,
                "Since Android 11 the themes already on your phone live in a folder apps can't read. " +
                    "Shizuku gives access, so you can edit installed themes and install yours straight into the Themes app.",
                vm::requestShizuku,
            )
        }
        item {
            OutlinedButton(
                onClick = vm::listInstalled, modifier = Modifier.fillMaxWidth(),
                enabled = state.shizuku == app.xeditor.shizuku.ShizukuShell.Status.READY,
            ) { Text("Build on an installed theme") }
        }
        val installed = state.installedThemes.orEmpty()
        if (installed.isNotEmpty()) {
            item { Text("Installed themes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }
            items(installed.size) { i ->
                val t = installed[i]
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openInstalled(t) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(t.designer.ifBlank { null }, if (t.ours) "installed by Xiaomi Editor" else null).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (t.ours) IconButton(onClick = { vm.uninstallInstalled(t) }) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.Delete, "Remove ${t.title}")
                    }
                    Text("Edit", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Build on a .mtz file") } }
        item { OutlinedButton(onClick = onFolder, modifier = Modifier.fillMaxWidth()) { Text("Build on a theme from a folder") } }
        val folder = state.folderThemes.orEmpty()
        if (folder.isNotEmpty()) {
            item { Text("Themes in that folder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }
            items(folder.size) { i ->
                val t = folder[i]
                Row(
                    Modifier.fillMaxWidth().clickable { vm.importMtz(t.uri); vm.closeFolder() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge)
                        Text(formatSize(t.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Edit", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (state.hasProject) {
            item { OutlinedButton(onClick = vm::continueEditing, modifier = Modifier.fillMaxWidth()) { Text("Continue with “${state.meta.title}”") } }
        }
    }
}

@Composable
private fun ExportDialog(state: EditorState, vm: EditorViewModel, onClose: () -> Unit) {
    val m = state.meta
    var fileName by remember { mutableStateOf(vm.safeFileName(m.title)) }
    var fileNameTouched by remember { mutableStateOf(false) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { vm.exportTo(uri); onClose() }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Export or install") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    androidx.compose.material3.OutlinedTextField(
                        m.title,
                        { t ->
                            vm.updateMeta(m.copy(title = t))
                            if (!fileNameTouched) fileName = vm.safeFileName(t)
                        },
                        label = { Text("Name in the Themes app") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    androidx.compose.material3.OutlinedTextField(
                        m.designer, { vm.updateMeta(m.copy(designer = it, author = it)) },
                        label = { Text("Designer") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    androidx.compose.material3.OutlinedTextField(
                        fileName, { fileName = it; fileNameTouched = true },
                        label = { Text("File name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { Text("In this theme", style = MaterialTheme.typography.titleSmall) }
                item {
                    val parts = state.components.map { it.label }
                    Text(
                        (if (parts.isEmpty()) "• Theme info only" else parts.joinToString("\n") { "• $it" }) + "\n" +
                            if (state.editedSurfaces.isEmpty()) "Nothing has been edited, so the theme is exported exactly as it was."
                            else "Your changes to: " + state.editedSurfaces.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    val ready = state.shizuku == app.xeditor.shizuku.ShizukuShell.Status.READY
                    Button(onClick = { vm.installToThemesApp(fileName); onClose() }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                        Text("Install to Themes app")
                    }
                    Text(
                        when {
                            !ready -> "Installing needs Shizuku (${state.shizuku.label.lowercase()})."
                            !state.canApplyDirectly -> "Lists it under My themes. This HyperOS version removes it again when you tap Apply, because it has no Xiaomi licence."
                            else -> "Lists it under My themes, ready to apply."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedButton(onClick = { saveAs.launch(vm.safeFileName(fileName)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save to a folder of my choice…")
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { vm.export(fileName); onClose() }) { Text("Save to Download") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
