package app.xeditor.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xeditor.iconpack.IconPack
import app.xeditor.iconpack.IconPackDrawables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded icons shared across picker sessions; keyed by pack + drawable name. */
private val iconCache = LruCache<String, ImageBitmap>(400)

/**
 * Pick one icon for [appLabel] from the whole pack: a search box, suggested matches
 * for the app first, then every icon; or a picture from the gallery, or the pack's
 * own picker when it has one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerScreen(
    pack: IconPack,
    appLabel: String,
    appPackage: String,
    onPicked: (Bitmap) -> Unit,
    onPackPicker: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val all by produceState(emptyList<String>(), pack.packageName) {
        value = withContext(Dispatchers.IO) { IconPackDrawables.list(ctx, pack) }
    }
    val suggested = remember(all) { IconPackDrawables.suggestions(all, appLabel, appPackage) }
    val q = query.trim().lowercase().replace(' ', '_')
    val shown = remember(all, q) { if (q.isEmpty()) all else all.filter { q in it } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()?.let(onPicked)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Icon for $appLabel", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search ${all.size} icons in ${pack.label}") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
            singleLine = true,
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("From gallery")
            }
            OutlinedButton(onClick = onPackPicker) { Text("Pack's own picker") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (q.isEmpty() && suggested.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Suggested for $appLabel") }
                items(suggested, key = { "s:$it" }) { name -> IconCell(pack.packageName, name) { pick(ctx, pack, name)?.let(onPicked) } }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("All icons") }
            }
            if (all.isNotEmpty() && shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("No icons match “$query”") }
            }
            items(shown, key = { it }) { name -> IconCell(pack.packageName, name) { pick(ctx, pack, name)?.let(onPicked) } }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun IconCell(packPkg: String, name: String, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val img by produceState(iconCache.get("$packPkg/$name"), packPkg, name) {
        if (value == null) value = withContext(Dispatchers.IO) {
            loadPackDrawable(ctx, packPkg, name)?.let { render(it, 128) }?.asImageBitmap()?.also { iconCache.put("$packPkg/$name", it) }
        }
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            img?.let { Image(it, name, Modifier.size(52.dp)) }
        }
        Text(name, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-size render of the chosen icon for the theme (192 px like the rest of the icons). */
private fun pick(ctx: android.content.Context, pack: IconPack, name: String): Bitmap? =
    loadPackDrawable(ctx, pack.packageName, name)?.let { render(it, 192) }

private fun render(d: android.graphics.drawable.Drawable, size: Int): Bitmap {
    if (d is BitmapDrawable && d.bitmap != null) return Bitmap.createScaledBitmap(d.bitmap, size, size, true)
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, size, size); d.draw(Canvas(b))
    return b
}
