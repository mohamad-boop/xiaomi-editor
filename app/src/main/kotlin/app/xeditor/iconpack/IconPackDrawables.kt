package app.xeditor.iconpack

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Every icon an icon pack offers, by drawable name. CandyBar-style packs list them
 * in `drawable.xml` (res/xml or assets); anything referenced from appfilter.xml is
 * added too, so packs without a drawable.xml still show their icons.
 */
object IconPackDrawables {

    fun list(context: Context, pack: IconPack): List<String> {
        val packCtx = runCatching { context.createPackageContext(pack.packageName, 0) }.getOrNull()
        val fromDrawableXml = packCtx?.let { ctx ->
            val res = ctx.resources
            val id = res.getIdentifier("drawable", "xml", pack.packageName)
            if (id != 0) runCatching { res.getXml(id).use { drain(it) } }.getOrNull()
            else runCatching {
                ctx.assets.open("drawable.xml").use { s ->
                    XmlPullParserFactory.newInstance().newPullParser().apply { setInput(s, null) }.let(::drain)
                }
            }.getOrNull()
        }.orEmpty()
        return (fromDrawableXml + pack.mappings.values).distinct().sorted()
    }

    private fun drain(p: XmlPullParser): List<String> {
        val out = mutableListOf<String>()
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "item") p.getAttributeValue(null, "drawable")?.let(out::add)
            e = p.next()
        }
        return out
    }

    /** Drawable names that look like they belong to [label] / [pkg], best first. */
    fun suggestions(all: List<String>, label: String, pkg: String): List<String> {
        val words = (label.lowercase().split(Regex("[^a-z0-9]+")) + pkg.lowercase().split('.'))
            .filter { it.length >= 3 && it !in setOf("com", "android", "app", "google", "apps", "miui", "xiaomi", "org") }
            .distinct()
        if (words.isEmpty()) return emptyList()
        return all.map { name -> name to words.count { name.contains(it) } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .map { it.first }
            .take(24)
    }
}
