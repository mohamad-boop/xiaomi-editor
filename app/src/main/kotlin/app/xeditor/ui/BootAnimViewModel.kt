package app.xeditor.ui

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xeditor.bootanim.BootAnimBuilder
import app.xeditor.bootanim.BootAnimInfo
import app.xeditor.bootanim.BootAnimReader
import app.xeditor.bootanim.BootAnimSpec
import app.xeditor.bootanim.FitMode
import app.xeditor.bootanim.FrameSource
import app.xeditor.bootanim.GifFrameSource
import app.xeditor.bootanim.ImageSequenceSource
import app.xeditor.bootanim.MagiskModuleExporter
import app.xeditor.bootanim.PlayMode
import app.xeditor.bootanim.VideoFrameSource
import app.xeditor.bootanim.ZipFrameSource
import app.xeditor.project.ThemeProject
import app.xeditor.util.Downloads
import app.xeditor.util.displayNameOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

enum class SourceKind(val label: String) { VIDEO("Video"), GIF("GIF"), IMAGES("Images"), ZIP("bootanimation.zip") }

data class SourceInfo(val kind: SourceKind, val name: String, val uris: List<Uri>)

data class BuiltAnimation(
    val info: BootAnimInfo,
    val sizeBytes: Long,
    /** Bumped whenever the working file changes so the preview restarts. */
    val revision: Long,
)

data class BootAnimState(
    val source: SourceInfo? = null,
    val screenW: Int = 1080,
    val screenH: Int = 2400,
    /** Picture size in % of the fitted size; the frame itself is always full screen. */
    val contentScale: Int = 100,
    /** Vertical shift in % of screen height. */
    val offsetY: Int = 0,
    /** Palette size for the frames; 0 = full colour. */
    val colors: Int = 256,
    val fps: Int = 30,
    val maxSeconds: Int = 6,
    val mode: PlayMode = PlayMode.ONCE_THEN_HOLD,
    val fit: FitMode = FitMode.FILL,
    val background: Int = Color.BLACK,
    val progress: Pair<Int, Int>? = null,
    val built: BuiltAnimation? = null,
    val audioName: String? = null,
    val themeHasAnimation: Boolean = false,
    val busy: String? = null,
    val message: String? = null,
) {
    // HyperOS draws boot frames at their own pixel size (no upscaling), so frames
    // are always rendered at the full screen size.
    val outW: Int get() = screenW
    val outH: Int get() = screenH
}

class BootAnimViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val workDir = File(app.cacheDir, "bootanim").apply { mkdirs() }
    val workingFile = File(workDir, "bootanimation.zip")
    private val audioFile = File(workDir, "bootaudio.mp3")
    /** Frames re-rendered when the source is an existing boot animation. */
    private val sourceZip = File(workDir, "source.zip")
    private val project = ThemeProject(app)

    private val _state = MutableStateFlow(BootAnimState())
    val state: StateFlow<BootAnimState> = _state.asStateFlow()

    private var buildJob: Job? = null

    init {
        val (w, h) = screenSize()
        _state.update { it.copy(screenW = w, screenH = h) }
        viewModelScope.launch {
            if (!workingFile.exists() && project.file(ThemeProject.BOOT_ANIMATION).exists()) {
                withContext(Dispatchers.IO) { project.file(ThemeProject.BOOT_ANIMATION).copyTo(workingFile, true) }
            }
            reloadBuilt()
            refreshThemeFlag()
        }
    }

    private fun screenSize() = app.xeditor.util.screenSize(ctx)

    fun refreshThemeFlag() {
        _state.update { it.copy(themeHasAnimation = project.file(ThemeProject.BOOT_ANIMATION).exists()) }
    }

    private suspend fun reloadBuilt() {
        val built = withContext(Dispatchers.IO) {
            if (!workingFile.exists()) null else runCatching {
                BootAnimReader(workingFile).use {
                    BuiltAnimation(it.info, workingFile.length(), workingFile.lastModified() + System.nanoTime())
                }
            }.getOrNull()
        }
        _state.update { it.copy(built = built) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setContentScale(p: Int) = _state.update { it.copy(contentScale = p) }
    fun setOffsetY(p: Int) = _state.update { it.copy(offsetY = p) }
    fun setColors(c: Int) = _state.update { it.copy(colors = c) }

    /** Re-render the current animation (e.g. to fill the screen or change its size). */
    fun resizeCurrent() = work("Preparing…") {
        withContext(Dispatchers.IO) { workingFile.copyTo(sourceZip, overwrite = true) }
        val info = _state.value.built?.info
        _state.update {
            it.copy(
                source = SourceInfo(SourceKind.ZIP, "Current animation", emptyList()),
                fps = info?.fps ?: it.fps,
                fit = FitMode.FIT,
                mode = if ((info?.parts?.size ?: 1) > 1) PlayMode.ONCE_THEN_HOLD else PlayMode.LOOP,
            )
        }
        "Adjust the size below, then tap Build"
    }
    fun setFps(f: Int) = _state.update { it.copy(fps = f) }
    fun setMaxSeconds(s: Int) = _state.update { it.copy(maxSeconds = s) }
    fun setMode(m: PlayMode) = _state.update { it.copy(mode = m) }
    fun setFit(f: FitMode) = _state.update { it.copy(fit = f) }
    fun setBackground(c: Int) = _state.update { it.copy(background = c) }

    fun chooseSource(kind: SourceKind, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val sorted = if (kind == SourceKind.IMAGES) uris.sortedBy { ctx.displayNameOf(it).lowercase() } else uris
        val name = if (sorted.size == 1) ctx.displayNameOf(sorted[0]) else "${sorted.size} images"
        _state.update { it.copy(source = SourceInfo(kind, name, sorted), message = null) }
        if (kind == SourceKind.ZIP) importZip(sorted[0])
    }

    private fun work(label: String, block: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = label, message = null) }
            val msg = runCatching { block() }.getOrElse { it.message ?: it.toString() }
            _state.update { it.copy(busy = null, message = msg) }
        }
    }

    private fun importZip(uri: Uri) = work("Checking boot animation…") {
        withContext(Dispatchers.IO) {
            val tmp = File(workDir, "import.zip")
            ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: error("Can't read that file")
            val repacked = BootAnimReader(tmp).use { r ->
                if (r.hasCompressedEntries) { r.repackStored(workingFile); true } else false
            }
            if (!repacked) tmp.copyTo(workingFile, overwrite = true)
            workingFile.copyTo(sourceZip, overwrite = true)
            tmp.delete()
            reloadBuilt()
            val info = BootAnimReader(workingFile).use { it.info }
            _state.update { it.copy(fps = info.fps, fit = FitMode.FIT) }
            val small = info.width < _state.value.screenW
            listOfNotNull(
                if (repacked) "Imported — it was compressed, so it was repacked the way Android needs it." else "Imported.",
                if (small) "It's ${info.width}×${info.height}, smaller than your screen, so it would show small. Tap Build to re-render it full screen." else null,
            ).joinToString(" ")
        }
    }

    fun build() {
        val s = _state.value
        val source = s.source ?: return
        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            _state.update { it.copy(busy = "Building…", progress = 0 to 1, message = null) }
            val spec = BootAnimSpec(
                s.outW, s.outH, s.fps, s.mode, s.fit, s.background,
                contentScale = s.contentScale / 100f, offsetY = s.offsetY / 100f, colors = s.colors,
            )
            val maxFrames = if (source.kind == SourceKind.ZIP) 900 else s.maxSeconds * s.fps
            val tmp = File(workDir, "building.zip")
            val msg = try {
                withContext(Dispatchers.Default) {
                    openSource(source, s.fps, maxFrames).use { fs ->
                        BootAnimBuilder.build(fs, spec, tmp) { done, total ->
                            _state.update { it.copy(progress = done to total) }
                        }
                    }
                    tmp.copyTo(workingFile, overwrite = true)
                    tmp.delete()
                }
                reloadBuilt()
                "Built. Check the preview, then add it to your theme."
            } catch (e: CancellationException) {
                tmp.delete()
                "Cancelled"
            } catch (e: Throwable) {
                tmp.delete()
                "Build failed: ${e.message ?: e}"
            }
            _state.update { it.copy(busy = null, progress = null, message = msg) }
        }
    }

    /** First source frame laid out with the current settings, for the layout preview. */
    suspend fun layoutPreview(s: BootAnimState): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val source = s.source ?: return@withContext null
        runCatching {
            openSource(source, s.fps, 1).use { fs ->
                val frame = fs.frame(0, s.outW / 4, s.outH / 4, s.fit) ?: return@use null
                val spec = BootAnimSpec(
                    s.outW, s.outH, s.fps, s.mode, s.fit, s.background,
                    contentScale = s.contentScale / 100f, offsetY = s.offsetY / 100f,
                )
                BootAnimBuilder.renderPreview(frame, spec, 4)
            }
        }.getOrNull()
    }

    fun cancelBuild() {
        buildJob?.cancel()
    }

    private fun openSource(src: SourceInfo, fps: Int, maxFrames: Int): FrameSource = when (src.kind) {
        SourceKind.VIDEO -> VideoFrameSource(ctx, src.uris[0], fps, maxFrames)
        SourceKind.GIF -> GifFrameSource(ctx, src.uris[0], fps, maxFrames)
        SourceKind.IMAGES -> ImageSequenceSource(ctx, src.uris.take(maxOf(maxFrames, 1)))
        SourceKind.ZIP -> ZipFrameSource(sourceZip, maxFrames)
    }

    fun chooseAudio(uri: Uri?) {
        if (uri == null) {
            audioFile.delete()
            _state.update { it.copy(audioName = null) }
            return
        }
        work("Copying sound…") {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { input -> audioFile.outputStream().use { input.copyTo(it) } }
                    ?: error("Can't read that file")
            }
            _state.update { it.copy(audioName = ctx.displayNameOf(uri)) }
            null
        }
    }

    fun addToTheme(onAdded: () -> Unit) = work("Adding to theme…") {
        if (!workingFile.exists()) error("Build or import a boot animation first")
        withContext(Dispatchers.IO) {
            if (!project.exists) project.newBlank()
            workingFile.inputStream().use { project.write(ThemeProject.BOOT_ANIMATION, it) }
            if (audioFile.exists()) {
                audioFile.inputStream().use { project.write(ThemeProject.BOOT_AUDIO, it) }
            } else {
                project.remove(ThemeProject.BOOT_AUDIO)
            }
        }
        refreshThemeFlag()
        onAdded()
        "Added to your theme"
    }

    fun loadFromTheme() = work("Loading…") {
        withContext(Dispatchers.IO) {
            project.file(ThemeProject.BOOT_ANIMATION).copyTo(workingFile, overwrite = true)
        }
        reloadBuilt()
        _state.update { it.copy(source = null) }
        "Loaded the theme's boot animation"
    }

    fun saveZip() = work("Saving…") {
        if (!workingFile.exists()) error("Nothing built yet")
        val out = withContext(Dispatchers.IO) {
            Downloads.save(ctx, workingFile, "bootanimation.zip", "application/zip")
        }
        "Saved to ${out.absolutePath}"
    }

    fun exportMagisk() = work("Packing Magisk module…") {
        if (!workingFile.exists()) error("Nothing built yet")
        val out = withContext(Dispatchers.IO) {
            val module = File(workDir, "bootanimation-magisk.zip")
            MagiskModuleExporter.export(workingFile, module)
            Downloads.save(ctx, module, module.name, "application/zip").also { module.delete() }
        }
        "Magisk module saved to ${out.absolutePath}. Flash it in Magisk and reboot."
    }

    /** Rough output size before building: PNG frames of photo content land around 1.5 bytes/pixel. */
    fun estimateMb(s: BootAnimState): Int {
        val frames = when (s.source?.kind) {
            SourceKind.IMAGES -> min(s.source.uris.size, s.maxSeconds * s.fps)
            SourceKind.ZIP -> s.built?.info?.totalFrames ?: (s.maxSeconds * s.fps)
            else -> s.maxSeconds * s.fps
        }
        // Full-colour PNG frames of real footage land near 1.5 bytes/pixel; palette frames around a third of that.
        val bytesPerPixel = if (s.colors > 0) 0.5 else 1.5
        return (frames.toLong() * s.outW * s.outH * bytesPerPixel / (1024 * 1024)).roundToInt()
    }
}
