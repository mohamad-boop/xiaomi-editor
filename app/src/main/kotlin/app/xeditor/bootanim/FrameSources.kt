package app.xeditor.bootanim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.Closeable
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Something that can hand out numbered frames for a boot animation. Frames come
 * back roughly pre-scaled for the target box so we never decode a 4K video frame
 * just to shrink it again.
 */
interface FrameSource : Closeable {
    val frameCount: Int
    fun frame(index: Int, targetW: Int, targetH: Int, fit: FitMode): Bitmap?
    override fun close() {}
}

/** Size a (srcW x srcH) image must be scaled to so it fits in / covers (boxW x boxH). */
internal fun scaledSize(srcW: Int, srcH: Int, boxW: Int, boxH: Int, fit: FitMode): Pair<Int, Int> {
    if (srcW <= 0 || srcH <= 0) return boxW to boxH
    val sx = boxW.toFloat() / srcW
    val sy = boxH.toFloat() / srcH
    val s = if (fit == FitMode.FIT) min(sx, sy) else max(sx, sy)
    return max(1, ceil(srcW * s).toInt()) to max(1, ceil(srcH * s).toInt())
}

class VideoFrameSource(
    context: Context,
    uri: Uri,
    private val fps: Int,
    maxFrames: Int,
) : FrameSource {
    private val mmr = MediaMetadataRetriever().apply { setDataSource(context, uri) }
    private val durationMs: Long =
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    private val videoSize: Pair<Int, Int> = run {
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) h to w else w to h
    }

    val durationSeconds: Float get() = durationMs / 1000f

    override val frameCount: Int =
        ((durationMs * fps) / 1000).toInt().coerceIn(1, maxFrames)

    override fun frame(index: Int, targetW: Int, targetH: Int, fit: FitMode): Bitmap? {
        val timeUs = index * 1_000_000L / fps
        val (w, h) = scaledSize(videoSize.first, videoSize.second, targetW, targetH, fit)
        return mmr.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, w, h)
            ?: mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
    }

    override fun close() {
        runCatching { mmr.release() }
    }
}

@Suppress("DEPRECATION")
class GifFrameSource(
    context: Context,
    uri: Uri,
    private val fps: Int,
    maxFrames: Int,
) : FrameSource {
    private val movie: Movie = run {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Can't read the GIF")
        Movie.decodeStream(ByteArrayInputStream(bytes)) ?: error("Not a readable GIF")
    }
    private val durationMs = movie.duration().coerceAtLeast(0)

    override val frameCount: Int =
        if (durationMs == 0) 1 else ((durationMs.toLong() * fps) / 1000).toInt().coerceIn(1, maxFrames)

    override fun frame(index: Int, targetW: Int, targetH: Int, fit: FitMode): Bitmap? {
        val w = movie.width().coerceAtLeast(1)
        val h = movie.height().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val t = if (durationMs == 0) 0 else ((index * 1000L / fps) % durationMs).toInt()
        movie.setTime(t)
        movie.draw(Canvas(bmp), 0f, 0f)
        return bmp
    }
}

class ImageSequenceSource(
    private val context: Context,
    private val uris: List<Uri>,
) : FrameSource {
    override val frameCount: Int = uris.size

    override fun frame(index: Int, targetW: Int, targetH: Int, fit: FitMode): Bitmap? = runCatching {
        val src = ImageDecoder.createSource(context.contentResolver, uris[index])
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = scaledSize(info.size.width, info.size.height, targetW, targetH, fit)
            // Only ever shrink here; upscaling happens once, in the renderer.
            if (w < info.size.width) decoder.setTargetSize(w, h)
        }
    }.getOrNull()
}

/** Frames of an existing bootanimation.zip, in play order, for re-rendering it. */
class ZipFrameSource(file: java.io.File, maxFrames: Int) : FrameSource {
    private val reader = BootAnimReader(file)
    private val names = reader.info.frameNames.take(maxFrames)
    override val frameCount: Int = names.size

    override fun frame(index: Int, targetW: Int, targetH: Int, fit: FitMode): Bitmap? = reader.decode(names[index], 1)

    override fun close() = reader.close()
}
