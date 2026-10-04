package app.xeditor.iconpack

import android.content.Context
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

private const val TAG = "IconPacker"

object AppFilterParser {

    fun load(context: Context, packageName: String): IconPack {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val label = pm.getApplicationLabel(appInfo).toString()

        val packCtx = context.createPackageContext(packageName, 0)
        val mappings = parseFromXmlResource(packCtx, packageName)
            .ifEmpty { parseFromAssets(packCtx, packageName) }

        Log.w(TAG, "loaded ${mappings.size} appfilter entries from $packageName")
        return IconPack(packageName, label, mappings)
    }

    private fun parseFromXmlResource(packCtx: Context, packageName: String): Map<ComponentKey, String> {
        val res = packCtx.resources
        val id = res.getIdentifier("appfilter", "xml", packageName)
        Log.w(TAG, "appfilter xml resource id for $packageName = $id")
        if (id == 0) return emptyMap()
        return res.getXml(id).use { drainItems(it) }
    }

    private fun parseFromAssets(packCtx: Context, packageName: String): Map<ComponentKey, String> {
        return runCatching {
            packCtx.assets.open("appfilter.xml").use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, null)
                drainItems(parser).also {
                    Log.w(TAG, "parsed ${it.size} entries from $packageName/assets/appfilter.xml")
                }
            }
        }.getOrElse {
            Log.w(TAG, "no assets/appfilter.xml in $packageName: ${it.message}")
            emptyMap()
        }
    }

    private fun drainItems(parser: XmlPullParser): Map<ComponentKey, String> {
        val out = LinkedHashMap<ComponentKey, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) {
                    ComponentKey.parse(component)?.let { out[it] = drawable }
                }
            }
            event = parser.next()
        }
        return out
    }
}
