package app.xeditor.ui

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xeditor.keeper.Keeper
import app.xeditor.project.Catalog
import app.xeditor.project.EditSurfaces
import app.xeditor.project.EditsStore
import app.xeditor.project.Generators
import app.xeditor.project.ThemeBuilder
import app.xeditor.project.ThemeComponent
import app.xeditor.project.ThemeMeta
import app.xeditor.project.ThemeProject
import app.xeditor.project.png
import app.xeditor.project.AdvancedEdits
import app.xeditor.theme.SoundGuard
import app.xeditor.shizuku.ShizukuShell
import app.xeditor.shizuku.ThemeStore
import app.xeditor.theme.ApplyResult
import app.xeditor.theme.ThemeApplier
import app.xeditor.util.Downloads
import app.xeditor.util.Fod
import app.xeditor.util.displayNameOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.max

private const val THEME_EDITOR = "com.mixapplications.miuithemeeditor"
private const val APPLY_COPY = "apply.mtz"
/** How long an apply copy stays around for the theme manager to read. */
private const val TEMP_LIFETIME_MS = 3 * 60_000L
const val FINGER_ICON_SRC = "finger.src.icon"
const val FINGER_ICON_SIZE = "finger.iconSize"
const val FINGER_ANIM_SIZE = "finger.animSize"
const val FINGER_ANIM_COLORS = "finger.animColors"
/** Stock HyperOS icon: 158 px drawn at 520 dpi ≈ 80 % of a 210 px sensor. */
const val DEFAULT_ICON_PCT = 80
const val DEFAULT_ANIM_PCT = 100

data class FolderTheme(val name: String, val uri: Uri, val size: Long)

data class EditorState(
    val hasProject: Boolean = false,
    val meta: ThemeMeta = ThemeMeta(),
    val components: List<ThemeComponent> = emptyList(),
    val edits: Map<String, String> = emptyMap(),
    val editedSurfaces: List<String> = emptyList(),
    val presets: List<String> = emptyList(),
    val folderThemes: List<FolderTheme>? = null,
    val shizuku: ShizukuShell.Status = ShizukuShell.Status.NOT_INSTALLED,
    /** Themes in the Themes app, read through Shizuku; null until listed. */
    val installedThemes: List<ThemeStore.Installed>? = null,
    /** Start page shown while a theme is open (home button / back). */
    val showStart: Boolean = false,
    /** Title of the theme just installed, so the UI can point at it. */
    val justInstalled: String? = null,
    /** False on HyperOS 3+, whose Themes app only applies themes carrying a Xiaomi licence. */
    val canApplyDirectly: Boolean = true,
    val hasThemeEditor: Boolean = false,
    /** Fingerprint sensor placement on this phone, for sizing and previews. */
    val fod: Fod? = null,
    /** System setting is_theme_fod_animation; null when it can't be read (no Shizuku). */
    val themeFodAnimation: Boolean? = null,
    /** Components of the theme opened for mix & match; null when none is open. */
    val mixComponents: List<ThemeComponent>? = null,
    val mixName: String? = null,
    /** Ringtones can be put back after applying (Modify system settings or Shizuku). */
    val canGuardSounds: Boolean = false,
    /** Bumped on every write so thumbnails reload. */
    val revision: Int = 0,
    val busy: String? = null,
    val message: String? = null,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val project = ThemeProject(app)
    val edits = EditsStore(app)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var metaSave: Job? = null

    init {
        refresh()
        // Leftover apply copies from earlier sessions.
        viewModelScope.launch(Dispatchers.IO) { cleanTemp() }
    }

    private fun cleanTemp() = runCatching {
        val keep = if (Keeper.isEnabled(ctx)) setOf(Keeper.KEEPER_COPY) else emptySet()
        Downloads.cleanTemp(ctx, TEMP_LIFETIME_MS, keep)
    }

    fun file(path: String): File = project.file(path)

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                migrateFingerprint()
                EditorState(
                    hasProject = project.exists,
                    meta = project.readMeta(),
                    components = project.components(),
                    edits = edits.load(),
                    editedSurfaces = EditSurfaces.edited(edits),
                    presets = edits.presets(),
                    shizuku = ShizukuShell.status(ctx),
                    canApplyDirectly = ThemeApplier.hasThemeManager(ctx),
                    hasThemeEditor = runCatching { ctx.packageManager.getPackageInfo(THEME_EDITOR, 0) }.isSuccess,
                    fod = Fod.read(ctx),
                    themeFodAnimation = readThemeFodAnimation(),
                    canGuardSounds = SoundGuard.canRestoreRingtones(ctx),
                )
            }
            _state.update {
                snapshot.copy(
                    folderThemes = it.folderThemes, installedThemes = it.installedThemes, justInstalled = it.justInstalled,
                    mixComponents = it.mixComponents, mixName = it.mixName, showStart = it.showStart,
                    busy = it.busy, message = it.message,
                    revision = it.revision + 1,
                )
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun work(label: String, block: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = label, message = null) }
            val msg = runCatching { block() }.getOrElse {
                if (it is OutOfMemoryError) "Ran out of memory building this theme. Try smaller fingerprint or boot animation sizes."
                else it.message ?: it.toString()
            }
            _state.update { it.copy(busy = null, message = msg) }
            refresh()
        }
    }

    // ---- opening ----

    fun newTheme() = work("Creating theme…") {
        withContext(Dispatchers.IO) { project.newBlank(); edits.clearAll() }
        _state.update { it.copy(showStart = false) }
        "Started a new theme"
    }

    fun importMtz(uri: Uri) = work("Opening theme…") {
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { project.importMtz(it) }
                ?: error("Can't read that file")
            edits.clearAll()
        }
        _state.update { it.copy(showStart = false, folderThemes = null) }
        "Opened ${ctx.displayNameOf(uri)}"
    }

    /** Lists the .mtz files in a folder the user picked (e.g. MIUI/theme or Download). */
    fun listFolder(tree: Uri) = work("Reading themes…") {
        ctx.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val found = withContext(Dispatchers.IO) {
            val out = mutableListOf<FolderTheme>()
            fun walk(docId: String, depth: Int) {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
                ctx.contentResolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0); val name = c.getString(1); val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < 2) walk(id, depth + 1)
                        } else if (name.endsWith(".mtz", true)) {
                            out += FolderTheme(name.removeSuffix(".mtz"), DocumentsContract.buildDocumentUriUsingTree(tree, id), c.getLong(3))
                        }
                    }
                }
            }
            walk(DocumentsContract.getTreeDocumentId(tree), 0)
            out.sortedBy { it.name.lowercase() }
        }
        _state.update { it.copy(folderThemes = found) }
        if (found.isEmpty()) "No .mtz files in that folder" else null
    }

    fun closeFolder() = _state.update { it.copy(folderThemes = null, installedThemes = null) }

    fun goHome() = _state.update { it.copy(showStart = true) }

    fun continueEditing() = _state.update { it.copy(showStart = false, folderThemes = null, installedThemes = null) }

    // ---- Shizuku: the Themes app's own store ----

    fun requestShizuku() = ShizukuShell.requestPermission { refresh() }

    fun listInstalled() = work("Reading the Themes app…") {
        if (!ShizukuShell.isReady(ctx)) error("Connect Shizuku first")
        val list = withContext(Dispatchers.IO) { ThemeStore.list() }
        _state.update { it.copy(installedThemes = list) }
        if (list.isEmpty()) "No themes found in the Themes app" else null
    }

    fun openInstalled(t: ThemeStore.Installed) = work("Opening ${t.title}…") {
        withContext(Dispatchers.IO) {
            val tmp = File(ctx.cacheDir, "installed.mtz")
            ThemeStore.exportToMtz(t, tmp)
            tmp.inputStream().use { project.importMtz(it) }
            tmp.delete()
            edits.clearAll()
        }
        _state.update { it.copy(installedThemes = null, folderThemes = null, showStart = false) }
        "Opened ${t.title}"
    }

    fun uninstallInstalled(t: ThemeStore.Installed) = work("Removing…") {
        withContext(Dispatchers.IO) { ThemeStore.uninstall(t) }
        _state.update { s -> s.copy(installedThemes = s.installedThemes?.filter { it.id != t.id }) }
        "Removed ${t.title} from the Themes app"
    }

    /** Builds the theme and puts it straight into the Themes app's "My themes". */
    fun installToThemesApp(fileName: String) = work("Installing into the Themes app…") {
        if (!ShizukuShell.isReady(ctx)) error("Connect Shizuku first")
        withContext(Dispatchers.IO) { ThemeStore.install(buildMtz(fileName), _state.value.meta) }
        _state.update { it.copy(justInstalled = _state.value.meta.title) }
        "Installed. In Themes, open My account → Themes, tap “${_state.value.meta.title}” and tap Apply."
    }

    /**
     * HyperOS 3 only applies licensed themes, and only MIUI Theme Editor writes
     * those. Save the finished theme where its Browse button looks and open it.
     */
    fun handOffToThemeEditor(launch: Context) = work("Building theme…") {
        val saved = withContext(Dispatchers.IO) { ThemeApplier.publish(ctx, buildMtz()) }
        withContext(Dispatchers.IO) { SoundGuard.snapshot(ctx) }
        launch.packageManager.getLaunchIntentForPackage(THEME_EDITOR)?.let { launch.startActivity(it) }
            ?: error("MIUI Theme Editor isn't installed. The theme was saved to ${saved.absolutePath}")
        "Saved as ${saved.name} in Download. In MIUI Theme Editor: Browse → pick ${saved.name} → Start → Next → Install, " +
            "then tap the “(Apply me)” copy in Themes."
    }

    fun openThemesApp(launch: Context) {
        launch.packageManager.getLaunchIntentForPackage("com.android.thememanager")?.let { launch.startActivity(it) }
    }

    // ---- description ----

    fun updateMeta(meta: ThemeMeta) {
        _state.update { it.copy(meta = meta) }
        metaSave?.cancel()
        metaSave = viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) { project.writeMeta(meta) }
        }
    }

    // ---- generic edits ----

    fun setValue(key: String, value: String?) {
        edits.set(key, value)
        _state.update { it.copy(edits = edits.load(), editedSurfaces = EditSurfaces.edited(edits)) }
    }

    fun setImage(key: String, uri: Uri, maxPx: Int) = work("Saving picture…") {
        withContext(Dispatchers.IO) { edits.setImage(key, decodeCapped(uri, maxPx).png()) }
        null
    }

    fun removeImage(key: String) = work("Removing…") {
        withContext(Dispatchers.IO) { edits.removeImage(key) }
        null
    }

    fun resetSurfaces(names: Set<String>) = work("Resetting…") {
        withContext(Dispatchers.IO) {
            val prefixes = EditSurfaces.all.filter { it.first in names }.flatMap { it.second }
            edits.reset(prefixes)
            if ("App icons" in names) edits.blob(ThemeBuilder.ICON_PACK_BLOB).delete()
        }
        "Reset ${names.size} surface(s)"
    }

    // ---- presets ----

    fun savePreset(name: String) = work("Saving preset…") {
        if (name.isBlank()) error("Enter a name first")
        withContext(Dispatchers.IO) { edits.savePreset(name.trim()) }
        "Preset saved"
    }

    fun applyPreset(name: String) = work("Applying preset…") {
        withContext(Dispatchers.IO) { edits.applyPreset(name) }
        "Preset applied"
    }

    fun deletePreset(name: String) = work("Deleting…") {
        withContext(Dispatchers.IO) { edits.deletePreset(name) }
        null
    }

    // ---- wallpapers ----

    fun setWallpaper(uri: Uri, lock: Boolean) = work("Saving wallpaper…") {
        withContext(Dispatchers.IO) {
            val bmp = decodeCapped(uri, 3200)
            val path = if (lock) ThemeProject.LOCK_WALLPAPER else ThemeProject.WALLPAPER
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            project.write(path, out.toByteArray())
        }
        if (lock) "Lock screen wallpaper set" else "Home wallpaper set"
    }

    fun removeWallpaper(lock: Boolean) = work("Removing…") {
        withContext(Dispatchers.IO) {
            project.remove(if (lock) ThemeProject.LOCK_WALLPAPER else ThemeProject.WALLPAPER)
        }
        null
    }

    /**
     * Sets the theme's wallpapers the normal Android way. They survive even if
     * HyperOS later resets the rest of the theme.
     */
    fun applyWallpapersNow() = work("Setting wallpaper…") {
        withContext(Dispatchers.IO) {
            val wm = WallpaperManager.getInstance(ctx)
            val home = project.file(ThemeProject.WALLPAPER)
            val lock = project.file(ThemeProject.LOCK_WALLPAPER)
            if (!home.exists() && !lock.exists()) error("Choose a wallpaper first")
            if (home.exists()) home.inputStream().use { wm.setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
            val lockSrc = if (lock.exists()) lock else home
            lockSrc.inputStream().use { wm.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
        }
        "Wallpaper set. It stays even if the rest of the theme is reset."
    }

    // ---- fonts ----

    fun setFont(uri: Uri) = work("Adding font…") {
        val bytes = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Can't read that font")
        }
        installFont(bytes)
        "Font set to ${ctx.displayNameOf(uri)}"
    }

    fun setSystemFont(file: File) = work("Adding font…") {
        installFont(withContext(Dispatchers.IO) { file.readBytes() })
        "Font set to ${file.nameWithoutExtension}"
    }

    private suspend fun installFont(bytes: ByteArray) = withContext(Dispatchers.IO) {
        // Reject non-fonts before they end up in a theme that breaks text rendering.
        val tmp = File(ctx.cacheDir, "font_check.ttf").apply { writeBytes(bytes) }
        runCatching { android.graphics.Typeface.Builder(tmp).build() }.getOrNull()
            ?: error("Please select a valid TrueType font (.ttf)")
        tmp.delete()
        ThemeProject.FONT_FILES.forEach { project.write(it, bytes) }
    }

    // ---- preview images ----

    fun addPreview(kind: String, uri: Uri) = work("Adding preview…") {
        withContext(Dispatchers.IO) {
            val dir = project.file("preview").apply { mkdirs() }
            val n = (0..50).first { !File(dir, "preview_${kind}_$it.jpg").exists() && !File(dir, "preview_${kind}_$it.png").exists() }
            val out = java.io.ByteArrayOutputStream()
            decodeCapped(uri, 1600).compress(Bitmap.CompressFormat.JPEG, 92, out)
            project.write("preview/preview_${kind}_$n.jpg", out.toByteArray())
        }
        "Preview added"
    }

    fun removePath(path: String) = work("Removing…") {
        withContext(Dispatchers.IO) { project.remove(path) }
        null
    }

    fun removeComponent(name: String) = work("Removing…") {
        withContext(Dispatchers.IO) { project.remove(name) }
        "Removed ${ThemeProject.labelFor(name)}"
    }

    // ---- fingerprint ----
    //
    // The picked pictures are kept as sources; the theme pictures are re-rendered from
    // them at "percent of the sensor" sizes, converted to pixels for this screen.

    private fun fod(): Fod = _state.value.fod ?: Fod.read(ctx)
    private fun pct(key: String, default: Int) = edits.get(key)?.toIntOrNull() ?: default

    fun setFingerIcon(uri: Uri) = work("Saving fingerprint icon…") {
        withContext(Dispatchers.IO) {
            edits.setImage(FINGER_ICON_SRC, decodeCapped(uri, 1024).png())
            renderFingerIcon()
        }
        null
    }

    fun setFingerIconSize(percent: Int) {
        edits.set(FINGER_ICON_SIZE, percent.toString())
        viewModelScope.launch {
            withContext(Dispatchers.IO) { renderFingerIcon() }
            refresh()
        }
    }

    fun removeFingerIcon() = work("Removing…") {
        withContext(Dispatchers.IO) { edits.reset(listOf(FINGER_ICON_SRC, FINGER_ICON_SIZE, Catalog.FINGER_ICON)) }
        null
    }

    /** Themes edited before sizes existed have rendered pictures but no sources; adopt them. */
    private fun migrateFingerprint() {
        if (edits.hasImage(Catalog.FINGER_ICON) && !edits.hasImage(FINGER_ICON_SRC)) {
            edits.imageFile(Catalog.FINGER_ICON).copyTo(edits.imageFile(FINGER_ICON_SRC), overwrite = true)
            renderFingerIcon()
        }
        if (edits.get("finger.anim") != null && !edits.hasImage("finger.src.anim.0")) {
            (1..Catalog.FINGER_FRAMES).forEach { i ->
                edits.imageFile("finger.anim.$i").takeIf { it.exists() }?.copyTo(edits.imageFile("finger.src.anim.${i - 1}"), overwrite = true)
            }
            edits.set("finger.anim", Generators.FingerEffect.ORIGINAL.name)
            renderFingerAnimation()
        }
    }

    private fun renderFingerIcon() {
        val src = edits.imageFile(FINGER_ICON_SRC).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } ?: return
        val size = fod().pictureSizeFor(pct(FINGER_ICON_SIZE, DEFAULT_ICON_PCT))
        edits.setImage(Catalog.FINGER_ICON, Generators.fitSquare(src, size).png())
    }

    fun setFingerAnimation(uri: Uri, effect: Generators.FingerEffect) = work("Making animation…") {
        withContext(Dispatchers.IO) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("This image could not be read. Try another picture or GIF.")
            val sources = gifFrames(bytes) ?: listOf(
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("This image could not be read. Try another picture or GIF."),
            )
            edits.reset(listOf("finger.src.anim."))
            sources.forEachIndexed { i, b -> edits.setImage("finger.src.anim.$i", Generators.fitSquare(b, 720).png()) }
            edits.set("finger.anim", effect.name)
            renderFingerAnimation()
        }
        "Unlock animation set"
    }

    fun setFingerAnimOptions(effect: Generators.FingerEffect? = null, percent: Int? = null, colors: Int? = null) {
        effect?.let { edits.set("finger.anim", it.name) }
        percent?.let { edits.set(FINGER_ANIM_SIZE, it.toString()) }
        colors?.let { edits.set(FINGER_ANIM_COLORS, it.toString()) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { renderFingerAnimation() }
            refresh()
        }
    }

    private fun renderFingerAnimation() {
        val effect = Generators.FingerEffect.entries.firstOrNull { it.name == edits.get("finger.anim") } ?: return
        val sources = (0 until Catalog.FINGER_FRAMES).mapNotNull { i ->
            edits.imageFile("finger.src.anim.$i").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        }
        if (sources.isEmpty()) return
        val size = fod().pictureSizeFor(pct(FINGER_ANIM_SIZE, DEFAULT_ANIM_PCT))
        val colors = pct(FINGER_ANIM_COLORS, 256)
        Generators.fingerFrames(sources, effect, size).forEachIndexed { i, f ->
            edits.setImage("finger.anim.${i + 1}", if (colors > 0) app.xeditor.util.PalettePng.encode(f, colors) else f.png())
            f.recycle()
        }
        sources.forEach { it.recycle() }
    }

    private fun readThemeFodAnimation(): Boolean? = runCatching {
        if (!ShizukuShell.isReady(ctx)) return@runCatching null
        ShizukuShell.run("settings get system is_theme_fod_animation").text.trim() == "1"
    }.getOrNull()

    fun setThemeFodAnimation(on: Boolean) = work("Updating…") {
        withContext(Dispatchers.IO) {
            if (!ShizukuShell.isReady(ctx)) error("Connect Shizuku first")
            ShizukuShell.check("settings put system is_theme_fod_animation ${if (on) 1 else 0}")
        }
        if (on) "Theme unlock animation on. Lock the screen and touch the sensor to try it." else "Back to the animation chosen in Settings"
    }

    fun clearFingerAnimation() = work("Removing…") {
        withContext(Dispatchers.IO) { edits.reset(listOf("finger.anim", "finger.src.anim.", FINGER_ANIM_SIZE)) }
        null
    }

    @Suppress("DEPRECATION")
    private fun gifFrames(bytes: ByteArray): List<Bitmap>? {
        if (bytes.size < 6 || String(bytes, 0, 3) != "GIF") return null
        val movie = Movie.decodeStream(ByteArrayInputStream(bytes)) ?: return null
        val dur = movie.duration().coerceAtLeast(1)
        return (0 until Catalog.FINGER_FRAMES).map { i ->
            val b = Bitmap.createBitmap(movie.width().coerceAtLeast(1), movie.height().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            movie.setTime(i * dur / Catalog.FINGER_FRAMES)
            movie.draw(Canvas(b), 0f, 0f)
            b
        }
    }

    // ---- sounds ----

    fun setSound(kind: String, uri: Uri) = work("Adding sound…") {
        withContext(Dispatchers.IO) {
            val ext = audioExtension(ctx.contentResolver.getType(uri), ctx.displayNameOf(uri))
            ctx.contentResolver.openInputStream(uri)?.use { project.setSound(kind, it, ext) } ?: error("Can't read that file")
        }
        "${ThemeProject.SOUNDS[kind]} set"
    }

    fun removeSound(kind: String) = work("Removing…") {
        withContext(Dispatchers.IO) { project.removeSound(kind) }
        null
    }

    /** Copies the phone's current ringtone, notification and alarm sounds into the theme. */
    fun useCurrentSounds() = work("Copying your sounds…") {
        val types = mapOf(
            "ringtone" to android.media.RingtoneManager.TYPE_RINGTONE,
            "notification" to android.media.RingtoneManager.TYPE_NOTIFICATION,
            "alarm" to android.media.RingtoneManager.TYPE_ALARM,
        )
        val missed = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            for ((kind, type) in types) {
                val uri = android.media.RingtoneManager.getActualDefaultRingtoneUri(ctx, type)
                val ok = uri != null && runCatching {
                    val ext = audioExtension(ctx.contentResolver.getType(uri), uri.lastPathSegment.orEmpty())
                    ctx.contentResolver.openInputStream(uri)?.use { project.setSound(kind, it, ext) } != null
                }.getOrDefault(false)
                if (!ok) missed += ThemeProject.SOUNDS[kind]!!
            }
        }
        if (missed.isEmpty()) "Your current sounds are now part of the theme"
        else "Copied what Android allows. Pick these by hand: ${missed.joinToString(", ")}"
    }

    private fun audioExtension(mime: String?, name: String): String = when {
        mime?.contains("ogg") == true -> "ogg"
        mime?.contains("mpeg") == true || mime?.contains("mp3") == true -> "mp3"
        mime?.contains("wav") == true -> "wav"
        mime?.contains("aac") == true || mime?.contains("mp4") == true -> "m4a"
        name.contains('.') -> name.substringAfterLast('.').lowercase()
        else -> "mp3"
    }

    /** After an apply, put back sound settings the theme manager reset. */
    fun restoreSoundsIfNeeded() {
        if (!SoundGuard.active(ctx)) return
        viewModelScope.launch {
            val (restored, failed) = withContext(Dispatchers.IO) { SoundGuard.restore(ctx) }
            val msg = listOfNotNull(
                restored.takeIf { it.isNotEmpty() }?.let { "Put back sound settings the theme reset: ${it.joinToString(", ")}." },
                failed.takeIf { it.isNotEmpty() }?.let { "Couldn't restore ${it.joinToString(", ")} — allow Modify system settings or connect Shizuku." },
            ).joinToString(" ")
            if (msg.isNotEmpty()) _state.update { it.copy(message = msg) }
        }
    }

    // ---- mix & match ----

    private val mixFile get() = File(ctx.cacheDir, "mix.mtz")

    fun openForMix(uri: Uri) = work("Reading theme…") {
        val comps = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { input -> mixFile.outputStream().use { input.copyTo(it) } }
                ?: error("Can't read that file")
            java.util.zip.ZipFile(mixFile).use { z ->
                z.entries().toList().filter { !it.isDirectory && it.name != ThemeProject.DESCRIPTION }
                    .groupBy { it.name.substringBefore('/') }
                    .map { (name, es) -> ThemeComponent(name, ThemeProject.labelFor(name), es.sumOf { it.size }, es.size) }
                    .sortedBy { it.label.lowercase() }
            }
        }
        _state.update { it.copy(mixComponents = comps, mixName = ctx.displayNameOf(uri)) }
        if (comps.isEmpty()) "That theme has nothing to take" else null
    }

    fun copyMix(names: Set<String>) = work("Copying…") {
        withContext(Dispatchers.IO) { java.util.zip.ZipFile(mixFile).use { project.copyComponents(it, names) } }
        _state.update { it.copy(mixComponents = null, mixName = null) }
        "Added ${names.size} part(s) from the other theme"
    }

    fun closeMix() = _state.update { it.copy(mixComponents = null, mixName = null) }

    // ---- advanced ----

    fun addAdvancedValue(v: AdvancedEdits.Value) {
        val list = AdvancedEdits.values(edits.load()).filterNot { it.component == v.component && it.name == v.name && it.type == v.type } + v
        setValue(AdvancedEdits.VALUES_KEY, AdvancedEdits.encodeValues(list))
    }

    fun removeAdvancedValue(v: AdvancedEdits.Value) =
        setValue(AdvancedEdits.VALUES_KEY, AdvancedEdits.encodeValues(AdvancedEdits.values(edits.load()) - v))

    fun addAdvancedPicture(component: String, name: String, uri: Uri) = work("Saving picture…") {
        withContext(Dispatchers.IO) {
            val key = "adv.img.${component}.${name}".replace(Regex("[^A-Za-z0-9._]"), "_")
            edits.setImage(key, decodeCapped(uri, 2048).png())
            val list = AdvancedEdits.pictures(edits.load()).filterNot { it.component == component && it.name == name } +
                AdvancedEdits.Picture(component, name, key)
            edits.set(AdvancedEdits.PICTURES_KEY, AdvancedEdits.encodePictures(list))
        }
        null
    }

    fun removeAdvancedPicture(p: AdvancedEdits.Picture) = work("Removing…") {
        withContext(Dispatchers.IO) {
            edits.removeImage(p.imageKey)
            edits.set(AdvancedEdits.PICTURES_KEY, AdvancedEdits.encodePictures(AdvancedEdits.pictures(edits.load()) - p))
        }
        null
    }

    // ---- export ----

    fun safeFileName(name: String) =
        name.replace(Regex("[\\/:*?\"<>|]"), "_").trim().removeSuffix(".mtz").ifEmpty { "theme" } + ".mtz"

    private fun buildMtz(fileName: String = safeFileName(_state.value.meta.title)): File {
        metaSave?.cancel()
        project.writeMeta(_state.value.meta)
        val out = File(ctx.cacheDir, "mtz/${safeFileName(fileName)}")
        ThemeBuilder(project, edits).build(out)
        return out
    }

    /** Saves into Download under [fileName]. */
    fun export(fileName: String) = work("Building theme…") {
        val saved = withContext(Dispatchers.IO) { ThemeApplier.publish(ctx, buildMtz(fileName)) }
        "Saved to ${saved.absolutePath}"
    }

    /** Saves to a location the user picked with the system "Save as" screen. */
    fun exportTo(target: Uri) = work("Building theme…") {
        withContext(Dispatchers.IO) {
            try {
                val mtz = buildMtz(ctx.displayNameOf(target))
                ctx.contentResolver.openOutputStream(target, "wt")?.use { os -> mtz.inputStream().use { it.copyTo(os) } }
                    ?: error("This folder cannot be written to. Please choose a different one.")
            } catch (t: Throwable) {
                // The system already created the (empty) file; don't leave it behind.
                runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, target) }
                throw t
            }
        }
        "Saved as ${ctx.displayNameOf(target)}"
    }

    fun apply(launchContext: Context, keep: Boolean) = work("Building theme…") {
        // HyperOS 3's Themes app dropped the direct "apply this file" entry point, so
        // the theme goes into its own store instead and is applied from My themes.
        if (!ThemeApplier.hasThemeManager(ctx)) {
            if (!ShizukuShell.isReady(ctx)) error("On this HyperOS version themes are installed through Shizuku. Connect Shizuku first.")
            val mtz = withContext(Dispatchers.IO) { buildMtz() }
            withContext(Dispatchers.IO) { ThemeStore.install(mtz, _state.value.meta) }
            if (keep) {
                withContext(Dispatchers.IO) { mtz.inputStream().use { Keeper.setTheme(ctx, mtz.name, it) } }
                Keeper.setEnabled(ctx, true)
            }
            _state.update { it.copy(justInstalled = _state.value.meta.title) }
            openThemesApp(launchContext)
            return@work "Installed. In Themes, open My account → Themes, tap “${_state.value.meta.title}” and tap Apply."
        }
        val mtz = withContext(Dispatchers.IO) { buildMtz() }
        val published = withContext(Dispatchers.IO) { ThemeApplier.publishTemp(ctx, mtz, APPLY_COPY) }
        if (keep) {
            // The keeper publishes its own long-lived copy when it needs one.
            withContext(Dispatchers.IO) { mtz.inputStream().use { Keeper.setTheme(ctx, mtz.name, it) } }
            Keeper.setEnabled(ctx, true)
        }
        // Give the theme manager time to read the copy, then remove it.
        viewModelScope.launch(Dispatchers.IO) { delay(TEMP_LIFETIME_MS); cleanTemp() }
        withContext(Dispatchers.IO) { SoundGuard.snapshot(ctx) }
        when (val result = ThemeApplier.launch(launchContext, mtz, published)) {
            is ApplyResult.Launched ->
                if (keep) "Sent to the theme manager — the keeper will re-apply it after reboots"
                else "Sent to the theme manager"
            is ApplyResult.NoHandler -> "No theme manager found. Saved to ${result.exportedTo.absolutePath}"
        }
    }

    private fun decodeCapped(uri: Uri, maxSide: Int): Bitmap {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = max(info.size.width, info.size.height)
            if (side > maxSide) {
                val s = maxSide.toFloat() / side
                decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            }
        }
    }
}
