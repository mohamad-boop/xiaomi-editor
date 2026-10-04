package app.xeditor.project

import org.json.JSONArray
import org.json.JSONObject

/**
 * Overrides the editor has no dedicated screen for: any value or picture, in any
 * component, by resource name. Stored in the edits map as JSON lists so presets
 * and reset handle them like everything else.
 */
object AdvancedEdits {
    const val VALUES_KEY = "adv.values"
    const val PICTURES_KEY = "adv.pictures"

    data class Value(val component: String, val type: String, val name: String, val value: String)
    data class Picture(val component: String, val name: String, val imageKey: String)

    fun values(edits: Map<String, String>): List<Value> = parse(edits[VALUES_KEY]) {
        Value(it.getString("c"), it.getString("t"), it.getString("n"), it.getString("v"))
    }

    fun pictures(edits: Map<String, String>): List<Picture> = parse(edits[PICTURES_KEY]) {
        Picture(it.getString("c"), it.getString("n"), it.getString("k"))
    }

    fun encodeValues(list: List<Value>): String? = list.takeIf { it.isNotEmpty() }?.let {
        JSONArray(it.map { v -> JSONObject().put("c", v.component).put("t", v.type).put("n", v.name).put("v", v.value) }).toString()
    }

    fun encodePictures(list: List<Picture>): String? = list.takeIf { it.isNotEmpty() }?.let {
        JSONArray(it.map { p -> JSONObject().put("c", p.component).put("n", p.name).put("k", p.imageKey) }).toString()
    }

    private fun <T> parse(json: String?, f: (JSONObject) -> T): List<T> =
        runCatching { JSONArray(json ?: return emptyList()).let { a -> (0 until a.length()).map { f(a.getJSONObject(it)) } } }
            .getOrDefault(emptyList())

    /** Components worth suggesting in the Advanced editor, with what they theme. */
    val COMPONENTS = listOf(
        Comp.SYSUI to "Status bar, notifications, quick settings",
        Comp.PLUGIN to "Control Center, volume dialog",
        Comp.HOME to "MIUI launcher",
        Comp.POCO_HOME to "POCO launcher",
        Comp.FRAMEWORK to "Android system (framework-res)",
        "framework-miui-res" to "MIUI system resources",
        Comp.SETTINGS to "Settings app",
        Comp.CONTACTS to "Contacts & dialer",
        Comp.MMS to "Messaging",
        "com.miui.securitycenter" to "Security app",
        "com.android.thememanager" to "Themes app",
        Comp.ICONS to "Icons",
    )
}
