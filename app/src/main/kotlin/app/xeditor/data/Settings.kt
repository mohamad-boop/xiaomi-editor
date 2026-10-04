package app.xeditor.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("iconpacker", Context.MODE_PRIVATE)

    var selectedPack: String?
        get() = prefs.getString(KEY_PACK, null)
        set(v) = prefs.edit().putString(KEY_PACK, v).apply()

    companion object {
        private const val KEY_PACK = "selected_pack"
    }
}
