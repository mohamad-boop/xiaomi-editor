package app.xeditor.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object Downloads {

    /** Hand-off copies the theme manager reads while applying; cleaned up afterwards. */
    const val TEMP_DIR = "XiaomiEditor/temp"

    /**
     * Copies [file] into the public Downloads directory as [displayName] and returns
     * the real filesystem path MediaStore wrote to — which may differ from the
     * requested name if MediaStore disambiguated a collision ("name (1).zip").
     * [subfolder] is relative to Download. With [replace], an earlier copy this app
     * saved under the same name is removed first, so repeats don't pile up as
     * "name (1).mtz", "name (2).mtz"…
     */
    fun save(
        context: Context,
        file: File,
        displayName: String = file.name,
        mimeType: String = "application/octet-stream",
        subfolder: String? = null,
        replace: Boolean = false,
    ): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relPath = Environment.DIRECTORY_DOWNLOADS + (subfolder?.let { "/$it" } ?: "") + "/"
            if (replace) deleteOwned(context, relPath, displayName)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val target: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (target != null) {
                resolver.openOutputStream(target)?.use { os ->
                    file.inputStream().use { it.copyTo(os) }
                }
                resolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null, null,
                )
                @Suppress("DEPRECATION")
                val realPath = resolver.query(
                    target,
                    arrayOf(MediaStore.Downloads.DATA),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
                if (realPath != null) return File(realPath)
                @Suppress("DEPRECATION")
                return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relPath.removePrefix(Environment.DIRECTORY_DOWNLOADS) + displayName)
            }
        }
        val exports = File(context.getExternalFilesDir(null), "exports" + (subfolder?.let { "/$it" } ?: "")).apply { mkdirs() }
        val dest = File(exports, displayName)
        file.copyTo(dest, overwrite = true)
        return dest
    }

    /** Deletes this app's files in Download/[TEMP_DIR] older than [olderThanMs], except [keep]. */
    fun cleanTemp(context: Context, olderThanMs: Long, keep: Set<String> = emptySet()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(null), "exports/$TEMP_DIR").listFiles().orEmpty()
                .filter { it.name !in keep && System.currentTimeMillis() - it.lastModified() > olderThanMs }
                .forEach { it.delete() }
            return
        }
        val relPath = Environment.DIRECTORY_DOWNLOADS + "/" + TEMP_DIR + "/"
        val cutoff = (System.currentTimeMillis() - olderThanMs) / 1000
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_MODIFIED),
            "${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf(relPath), null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) in keep || c.getLong(2) > cutoff) continue
                runCatching {
                    resolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)), null, null)
                }
            }
        }
    }

    /** MediaStore only lets an app delete rows it created, so this never touches the user's own files. */
    private fun deleteOwned(context: Context, relPath: String, displayName: String) {
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(relPath, displayName), null,
        )?.use { c ->
            while (c.moveToNext()) {
                runCatching {
                    resolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)), null, null)
                }
            }
        }
    }
}

/** Best-effort display name for a content:// URI. */
fun Context.displayNameOf(uri: Uri): String {
    val fromProvider = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
