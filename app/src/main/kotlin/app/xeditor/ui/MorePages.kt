package app.xeditor.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.xeditor.project.AdvancedEdits
import app.xeditor.project.ThemeProject
import app.xeditor.util.formatSize

// ---------- sounds ----------

@Composable
fun SoundsPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var pendingKind by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val k = pendingKind; pendingKind = null
        if (uri != null && k != null) vm.setSound(k, uri)
    }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    fun stop() { player.value?.release(); player.value = null; playing = null }
    DisposableEffect(Unit) { onDispose { stop() } }

    PageScaffold(
        "Sounds", onBack, state, vm,
        "A theme can carry its own ringtone, notification and alarm sounds. Applying a theme without them resets yours to the defaults, " +
            "so include your current ones to keep them.",
    ) {
        item {
            GroupCard(null) {
                ThemeProject.SOUNDS.forEach { (kind, label) ->
                    val file = remember(state.revision) { ThemeProject.soundFile(vm.file(""), kind) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                file?.let { "${it.name} · ${formatSize(it.length())}" } ?: "Not in this theme (resets to default)",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (file != null) IconButton(onClick = {
                            if (playing == kind) stop() else {
                                stop()
                                player.value = runCatching {
                                    MediaPlayer().apply { setDataSource(file.path); setOnCompletionListener { stop() }; prepare(); start() }
                                }.getOrNull()
                                playing = kind.takeIf { player.value != null }
                            }
                        }) { Icon(if (playing == kind) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, if (playing == kind) "Stop" else "Play $label") }
                        TextButton(onClick = { pendingKind = kind; picker.launch("audio/*") }) { Text(if (file == null) "Choose" else "Change") }
                        if (file != null) IconButton(onClick = { stop(); vm.removeSound(kind) }) { Icon(Icons.Outlined.DeleteOutline, "Remove $label") }
                    }
                }
                FilledTonalButton(onClick = vm::useCurrentSounds, modifier = Modifier.fillMaxWidth()) { Text("Use my current sounds") }
            }
        }
        item {
            GroupCard("Protect sound settings") {
                Text(
                    "Applying a theme can also reset sound settings like Dolby Atmos. The app saves them right before applying and puts back " +
                        "anything that changed when you return. Ringtones need Modify system settings (or Shizuku); other sound settings need Shizuku.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.canGuardSounds) {
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("Allow restoring ringtones") }
                } else {
                    Text("Ready.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- mix & match ----------

@Composable
fun MixMatchPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::openForMix) }
    var chosen by remember(state.mixName) { mutableStateOf(setOf<String>()) }
    val mine = state.components.map { it.name }.toSet()
    PageScaffold(
        "Mix & match", { vm.closeMix(); onBack() }, state, vm,
        "Take parts from another theme — its lock screen style, always-on display, alarm screen, clock widgets, super wallpaper, " +
            "status bar, sounds or anything else — and add them to yours.",
    ) {
        item { Button(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Open another theme (.mtz)…") } }
        val comps = state.mixComponents
        if (comps != null) {
            item { Text("Parts of ${state.mixName}", style = MaterialTheme.typography.titleMedium) }
            items(comps, key = { it.name }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { chosen = if (c.name in chosen) chosen - c.name else chosen + c.name },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(c.name in chosen, null)
                    Column(Modifier.weight(1f)) {
                        Text(c.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            (if (c.label != c.name) "${c.name} · " else "") + formatSize(c.sizeBytes) +
                                if (c.name in mine) " · replaces yours" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Button(onClick = { vm.copyMix(chosen) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("Add ${chosen.size} part(s) to my theme")
                }
            }
        }
    }
}

// ---------- advanced ----------

private val TYPES = listOf("color", "dimen", "integer", "bool", "string")

@Composable
fun AdvancedPage(state: EditorState, vm: EditorViewModel, onBack: () -> Unit) {
    val values = AdvancedEdits.values(state.edits)
    val pictures = AdvancedEdits.pictures(state.edits)
    var component by remember { mutableStateOf(AdvancedEdits.COMPONENTS.first().first) }
    var type by remember { mutableStateOf("color") }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var picComponent by remember { mutableStateOf(AdvancedEdits.COMPONENTS.first().first) }
    var picName by remember { mutableStateOf("") }
    val picPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && picName.isNotBlank()) vm.addAdvancedPicture(picComponent.trim(), picName.trim(), uri)
    }

    PageScaffold(
        "Advanced", onBack, state, vm,
        "Change anything the theme engine supports by its resource name — values go into the app's theme_values.xml, " +
            "pictures replace drawables. These win over every other screen. Resource names can be read from an app's APK with tools like apktool.",
    ) {
        item {
            GroupCard("Add a value") {
                ComponentField(component) { component = it }
                Chips(TYPES, type, { it }) { type = it; value = "" }
                OutlinedTextField(name, { name = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Resource name") }, singleLine = true)
                when (type) {
                    "color" -> ColorRow("Value", value.ifBlank { null }) { value = it ?: "" }
                    "bool" -> Chips(listOf("true", "false"), value, { it }) { value = it }
                    else -> OutlinedTextField(
                        value, { value = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (type == "dimen") "Value in dp" else "Value") },
                    )
                }
                Button(
                    onClick = {
                        vm.addAdvancedValue(AdvancedEdits.Value(component.trim(), type, name, value.trim()))
                        name = ""; value = ""
                    },
                    enabled = component.isNotBlank() && name.isNotBlank() && value.isNotBlank(),
                ) { Text("Add") }
            }
        }
        if (values.isNotEmpty()) item {
            GroupCard("Values (${values.size})") {
                values.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${v.name} = ${v.value}", style = MaterialTheme.typography.bodyMedium)
                            Text("${v.type} · ${v.component}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (v.type == "color") ColorChip(parseArgb(v.value))
                        IconButton(onClick = { vm.removeAdvancedValue(v) }) { Icon(Icons.Outlined.DeleteOutline, "Remove ${v.name}") }
                    }
                }
            }
        }
        item {
            GroupCard("Replace a picture") {
                ComponentField(picComponent) { picComponent = it }
                OutlinedTextField(picName, { picName = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Drawable name (without .png)") }, singleLine = true)
                FilledTonalButton(
                    onClick = { picPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = picComponent.isNotBlank() && picName.isNotBlank(),
                ) { Text("Choose picture") }
                pictures.forEach { p ->
                    ImageRow("${p.name} · ${p.component}", vm.edits.imageFile(p.imageKey), true, state.revision, onPick = {}, onRemove = { vm.removeAdvancedPicture(p) })
                }
            }
        }
    }
}

@Composable
private fun ComponentField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text("Component (app)") }, singleLine = true)
    Chips(AdvancedEdits.COMPONENTS, AdvancedEdits.COMPONENTS.firstOrNull { it.first == value }, { it.second }) { onChange(it.first) }
}
