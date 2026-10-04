package app.xeditor.iconpack

data class ComponentKey(val pkg: String, val activity: String) {
    override fun toString() = "$pkg/$activity"
    companion object {
        private val COMPONENT_REGEX = Regex("""ComponentInfo\{([^/]+)/([^}]+)\}""")
        fun parse(componentInfo: String): ComponentKey? {
            val m = COMPONENT_REGEX.matchEntire(componentInfo.trim()) ?: return null
            return ComponentKey(m.groupValues[1], m.groupValues[2])
        }
    }
}

data class IconPack(
    val packageName: String,
    val label: String,
    val mappings: Map<ComponentKey, String>,
)
