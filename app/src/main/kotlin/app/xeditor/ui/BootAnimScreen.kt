package app.xeditor.ui

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xeditor.bootanim.BootAnimReader
import app.xeditor.bootanim.FitMode
import app.xeditor.bootanim.PlayMode
import app.xeditor.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

private val BACKGROUNDS = listOf(
    android.graphics.Color.BLACK,
    android.graphics.Color.WHITE,
    0xFF1B1B1F.toInt(),
    0xFF0B1E3A.toInt(),
    0xFF2B0F2E.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootAnimScreen(
    onAddedToTheme: () -> Unit,
    vm: BootAnimViewModel = viewModel(),
) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refreshThemeFlag() }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.chooseSource(SourceKind.VIDEO, listOf(it)) }
    }
    val pickGif = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.chooseSource(SourceKind.GIF, listOf(it)) }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.chooseSource(SourceKind.IMAGES, uris)
    }
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.chooseSource(SourceKind.ZIP, listOf(it)) }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.chooseAudio(it) }
    }

    val building = s.progress != null
    val editable = s.source != null && !building

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Boot animation") })
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusBanner(if (building) null else s.busy, s.message, vm::dismissMessage) }

            item {
                SectionCard(
                    "Preview", Icons.Outlined.PlayArrow,
                    subtitle = s.built?.let {
                        "${it.info.width}×${it.info.height} · ${it.info.fps} fps · ${it.info.totalFrames} frames · ${formatSize(it.sizeBytes)}"
                    } ?: "Nothing built yet",
                ) {
                    val built = s.built
                    if (built != null) {
                        BootAnimPreview(vm.workingFile, built.revision, built.info.width, built.info.height, s.screenW, s.screenH)
                        if (built.info.width < s.screenW) Text(
                            "This animation is ${built.info.width}×${built.info.height}, smaller than your ${s.screenW}×${s.screenH} screen, " +
                                "so it shows small (as above). Resize it to fill the screen.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = vm::resizeCurrent, enabled = !building) { Text("Resize this animation") }
                    }
                    if (s.themeHasAnimation) {
                        TextButton(onClick = vm::loadFromTheme) { Text("Load the one in my theme") }
                    }
                }
            }

            item {
                SectionCard("Source", Icons.Outlined.Movie, subtitle = s.source?.let { "${it.kind.label}: ${it.name}" } ?: "Pick what to turn into a boot animation") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { pickVideo.launch("video/*") }, Modifier.weight(1f), enabled = !building) { Text("Video") }
                        FilledTonalButton(onClick = { pickGif.launch("image/gif") }, Modifier.weight(1f), enabled = !building) { Text("GIF") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { pickImages.launch("image/*") }, Modifier.weight(1f), enabled = !building) { Text("Image frames") }
                        FilledTonalButton(onClick = { pickZip.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.weight(1f), enabled = !building) {
                            Text("Existing .zip")
                        }
                    }
                    Text(
                        "Image frames play in file-name order. An existing bootanimation.zip is checked and used as it is.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (s.source != null) {
                item {
                    SectionCard("Settings", Icons.Outlined.Tune) {
                        LayoutPreview(vm, s)
                        Label("Picture size: ${s.contentScale}%")
                        Slider(
                            value = s.contentScale.toFloat(), onValueChange = { vm.setContentScale(it.toInt()) },
                            valueRange = 20f..250f, enabled = editable,
                        )
                        Label("Vertical position: ${if (s.offsetY > 0) "+" else ""}${s.offsetY}%")
                        Slider(
                            value = s.offsetY.toFloat(), onValueChange = { vm.setOffsetY(it.toInt()) },
                            valueRange = -45f..45f, enabled = editable,
                        )
                        Text(
                            "Frames are always made at your full screen size (${s.screenW}×${s.screenH}); HyperOS doesn't scale boot frames up.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Label("Compression")
                        ChipRow(app.xeditor.util.COLOR_CHOICES.map { it.first }, s.colors, editable, vm::setColors) { c ->
                            app.xeditor.util.COLOR_CHOICES.first { it.first == c }.second
                        }
                        Text(
                            "Fewer colours make much smaller files; 256 looks almost identical, 64 can show banding on smooth gradients. " +
                                "Use Resize this animation to shrink one you already built.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Label("Frame rate")
                        ChipRow(listOf(15, 24, 30, 60), s.fps, editable, vm::setFps) { "$it fps" }
                        if (s.source?.kind != SourceKind.ZIP) {
                            Label("Max length: ${s.maxSeconds} s")
                            Slider(
                                value = s.maxSeconds.toFloat(),
                                onValueChange = { vm.setMaxSeconds(it.toInt()) },
                                valueRange = 1f..20f, steps = 18, enabled = editable,
                            )
                        }
                        Label("Scaling")
                        ChipRow(FitMode.entries, s.fit, editable, vm::setFit) {
                            if (it == FitMode.FILL) "Fill screen (crop)" else "Fit (bars)"
                        }
                        Label("Playback")
                        ChipRow(PlayMode.entries, s.mode, editable, vm::setMode) {
                            if (it == PlayMode.LOOP) "Loop until booted" else "Play once, hold last frame"
                        }
                        Label("Background")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BACKGROUNDS.forEach { c ->
                                val selected = c == s.background
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(
                                            BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                            CircleShape,
                                        )
                                        .clickable(enabled = editable) { vm.setBackground(c) },
                                )
                            }
                        }
                        Text(
                            "About ${vm.estimateMb(s)} MB. Frames must be stored uncompressed, so lower the resolution or length if it gets big.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val p = s.progress
                        if (p != null) {
                            LinearProgressIndicator(
                                progress = { if (p.second == 0) 0f else p.first.toFloat() / p.second },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Frame ${p.first} of ${p.second}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = vm::cancelBuild) { Text("Cancel") }
                            }
                        } else {
                            Button(onClick = vm::build, Modifier.fillMaxWidth(), enabled = editable) { Text("Build boot animation") }
                        }
                    }
                }
            }

            item {
                SectionCard("Boot sound", Icons.Outlined.AudioFile, subtitle = s.audioName ?: "Silent (optional)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickAudio.launch("audio/*") }) { Text(if (s.audioName == null) "Choose sound" else "Change") }
                        if (s.audioName != null) TextButton(onClick = { vm.chooseAudio(null) }) { Text("Remove") }
                    }
                }
            }

            item {
                SectionCard("Use it", Icons.Outlined.SaveAlt) {
                    val ready = s.built != null && s.busy == null
                    Button(onClick = { vm.addToTheme(onAddedToTheme) }, Modifier.fillMaxWidth(), enabled = ready) {
                        Text("Add to my theme")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::saveZip, Modifier.weight(1f), enabled = ready) { Text("Save .zip") }
                        OutlinedButton(onClick = vm::exportMagisk, Modifier.weight(1f), enabled = ready) { Text("Magisk module") }
                    }
                    Text(
                        "The theme carries it as boots/bootanimation.zip. Some HyperOS builds ignore theme boot animations. On a rooted phone, flash the Magisk module instead, which always works.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, enabled: Boolean, onSelect: (T) -> Unit, label: (T) -> String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o), maxLines = 2, style = MaterialTheme.typography.labelMedium) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Plays the frames of a bootanimation.zip in a phone-shaped box, decoding each
 * frame on the fly at reduced size so long animations don't need to fit in memory.
 */
@Composable
fun BootAnimPreview(file: File, revision: Long, width: Int, height: Int, screenW: Int = width, screenH: Int = height) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember { mutableStateOf(true) }
    var position by remember(revision) { mutableStateOf(0) }

    LaunchedEffect(file, revision, playing) {
        withContext(Dispatchers.IO) {
            runCatching {
                BootAnimReader(file).use { r ->
                    val names = r.info.frameNames
                    var sample = 1
                    while (r.info.height / (sample * 2) >= 480) sample *= 2
                    val frameMs = 1000L / r.info.fps.coerceAtLeast(1)
                    while (isActive) {
                        val start = SystemClock.uptimeMillis()
                        r.decode(names[position % names.size], sample)?.let { frame = it.asImageBitmap() }
                        if (!playing) break
                        position = (position + 1) % names.size
                        delay((frameMs - (SystemClock.uptimeMillis() - start)).coerceAtLeast(1))
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // The outline is the real screen; frames are drawn at their true size inside it
        // (HyperOS doesn't upscale), so a small animation shows small here too.
        Box(
            Modifier
                .height(360.dp)
                .aspectRatio(screenW.toFloat() / screenH.coerceAtLeast(1))
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(4.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .clickable { playing = !playing },
            contentAlignment = Alignment.Center,
        ) {
            frame?.let {
                Image(
                    it, contentDescription = "Boot animation preview", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth((width.toFloat() / screenW).coerceIn(0.05f, 1f))
                        .aspectRatio(width.toFloat() / height.coerceAtLeast(1)),
                )
            }
            if (!playing) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { playing = !playing }) {
            Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play")
        }
    }
}

/** How the first frame will sit on the screen with the current size/position settings. */
@Composable
private fun LayoutPreview(vm: BootAnimViewModel, s: BootAnimState) {
    val img by androidx.compose.runtime.produceState<ImageBitmap?>(
        null, s.source, s.contentScale, s.offsetY, s.fit, s.background,
    ) {
        kotlinx.coroutines.delay(150) // let slider drags settle
        value = vm.layoutPreview(s)?.asImageBitmap()
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(220.dp).aspectRatio(s.screenW.toFloat() / s.screenH)
                .clip(RoundedCornerShape(16.dp)).background(Color.Black)
                .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            img?.let { Image(it, "Layout preview", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        }
    }
}
