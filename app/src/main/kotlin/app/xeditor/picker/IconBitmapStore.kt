package app.xeditor.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.xeditor.iconpack.ComponentKey
import java.io.File
import java.io.FileOutputStream

/**
 * Persists manually-picked icon bitmaps to disk so reruns can reuse them.
 */
class IconBitmapStore(context: Context) {
    private val dir: File = File(context.filesDir, "manual_icons").apply { mkdirs() }

    fun save(key: ComponentKey, bitmap: Bitmap): String {
        val file = fileFor(key)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    fun load(key: ComponentKey): Bitmap? {
        val f = fileFor(key)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }

    fun delete(key: ComponentKey) {
        fileFor(key).delete()
    }

    private fun fileFor(key: ComponentKey): File {
        val safe = (key.pkg + "_" + key.activity).replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.png")
    }
}
