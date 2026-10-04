package app.xeditor.project

/**
 * Everything the editor can change, and which theme resources each change lands in.
 *
 * Resource names were checked against HyperOS 3 system apps (MiuiSystemUI,
 * MIUISystemUIPlugin, the POCO/MIUI launcher, framework-res). Some items also list
 * names used by older MIUI builds; a name the phone doesn't have is simply ignored
 * by the theme engine, so listing extras only widens compatibility.
 */
object Comp {
    const val SYSUI = "com.android.systemui"
    const val PLUGIN = "miui.systemui.plugin"
    const val HOME = "com.miui.home"
    const val POCO_HOME = "com.mi.android.globallauncher"
    const val FRAMEWORK = "framework-res"
    const val MMS = "com.android.mms"
    const val SETTINGS = "com.android.settings"
    const val CONTACTS = "com.android.contacts"
    const val ICONS = "icons"
}

enum class ValueType(val tag: String) { COLOR("color"), DIMEN("dimen"), INTEGER("integer"), BOOL("bool"), STRING("string") }

data class ResTarget(val component: String, val name: String)

sealed class Item {
    abstract val id: String
    abstract val label: String
}

data class ColorItem(override val id: String, override val label: String, val targets: List<ResTarget>) : Item()

data class NumberItem(
    override val id: String,
    override val label: String,
    val type: ValueType,
    val targets: List<ResTarget>,
    val min: Int,
    val max: Int,
    val default: Int,
    val unit: String,
) : Item()

data class ImageItem(
    override val id: String,
    override val label: String,
    val targets: List<ResTarget>,
    /** Longest side the picked picture is scaled down to. */
    val maxPx: Int = 1440,
) : Item()

data class Group(val title: String?, val items: List<Item>)

data class Surface(val id: String, val title: String, val hint: String?, val groups: List<Group>) {
    val items: List<Item> get() = groups.flatMap { it.items }
}

private fun t(component: String, vararg names: String) = names.map { ResTarget(component, it) }
private fun launcher(vararg names: String) = t(Comp.HOME, *names) + t(Comp.POCO_HOME, *names)

object Catalog {
    const val FINGER_ICON = "finger.icon"

    val palette = Surface(
        "palette", "Theme palette",
        "Five colours repaint the whole theme at once. Anything you set on the other colour screens wins over these.",
        listOf(
            Group(
                "Palette",
                listOf(
                    ColorItem(
                        "palette.accent", "Accent",
                        t(Comp.PLUGIN, "qs_icon_enabled_color", "miui_volume_color_accent", "qs_auto_brightness_icon_enabled_color") +
                            t(Comp.SYSUI, "brightness_window_slider_progress_color"),
                    ),
                    ColorItem(
                        "palette.surface", "Panels and cards",
                        t(Comp.SYSUI, "qs_panel_background_color", "notification_heads_up_bg_color") +
                            t(Comp.PLUGIN, "miui_volume_bg_color", "miui_volume_expand_background") +
                            launcher("recent_menu_bg_color"),
                    ),
                    ColorItem(
                        "palette.surfaceVariant", "Buttons and chips",
                        t(Comp.PLUGIN, "miui_volume_bg_color_collapse", "miui_volume_ringer_btn_bg_color") +
                            t(Comp.SYSUI, "brightness_window_slider_progress_bg_color"),
                    ),
                    ColorItem(
                        "palette.text", "Text and icons",
                        t(Comp.SYSUI, "qs_tile_label_text_color", "notification_primary_text_color_light") +
                            t(Comp.PLUGIN, "qs_card_primary_text_enabled_color", "miui_volume_tint_light") +
                            t(Comp.FRAMEWORK, "statusbar_content_title"),
                    ),
                    ColorItem(
                        "palette.secondaryText", "Secondary text",
                        t(Comp.SYSUI, "notification_time_color", "qs_footer_data_usage_text_color", "notification_secondary_text_color_light") +
                            t(Comp.PLUGIN, "qs_card_primary_text_disabled_color", "qs_icon_disabled_color") +
                            t(Comp.FRAMEWORK, "statusbar_content"),
                    ),
                ),
            ),
        ),
    )

    val systemColours = Surface(
        "syscolors", "System colours",
        "Only the colours you pick are changed. Anything you leave alone keeps the colour the theme already had.",
        listOf(
            Group(
                "Status bar & notifications",
                listOf(
                    ColorItem("sys.sbText", "Status bar text & clock", t(Comp.SYSUI, "status_bar_textColor", "status_bar_clock_color")),
                    ColorItem("sys.sbTextDark", "Status bar text over light wallpapers", t(Comp.SYSUI, "status_bar_textColor_darkmode")),
                    ColorItem("sys.notifTitle", "Notification title", t(Comp.FRAMEWORK, "statusbar_content_title") + t(Comp.SYSUI, "notification_primary_text_color_light")),
                    ColorItem("sys.notifText", "Notification text", t(Comp.FRAMEWORK, "statusbar_content") + t(Comp.SYSUI, "notification_secondary_text_color_light")),
                    ColorItem("sys.notifTime", "Notification time", t(Comp.SYSUI, "notification_time_color")),
                    ColorItem("sys.notifAction", "Notification buttons", t(Comp.SYSUI, "notification_action_text_color", "notification_action_button_text_color")),
                    ColorItem("sys.headsUp", "Pop-up notification background", t(Comp.SYSUI, "notification_heads_up_bg_color")),
                ),
            ),
            Group(
                "Quick settings & Control Center",
                listOf(
                    ColorItem("sys.qsPanel", "Panel background", t(Comp.SYSUI, "qs_panel_background_color")),
                    ColorItem("sys.qsLabel", "Tile label", t(Comp.SYSUI, "qs_tile_label_text_color")),
                    ColorItem("sys.qsDots", "Page dots", t(Comp.SYSUI, "qs_page_indicator_dot_color")),
                    ColorItem("sys.qsData", "Data usage text", t(Comp.SYSUI, "qs_footer_data_usage_text_color")),
                    ColorItem("sys.ccIconOn", "Control Center icon, on", t(Comp.PLUGIN, "qs_icon_enabled_color")),
                    ColorItem("sys.ccIconOff", "Control Center icon, off", t(Comp.PLUGIN, "qs_icon_disabled_color")),
                    ColorItem("sys.ccTextOn", "Control Center card text, on", t(Comp.PLUGIN, "qs_card_primary_text_enabled_color")),
                    ColorItem("sys.ccTextOff", "Control Center card text, off", t(Comp.PLUGIN, "qs_card_primary_text_disabled_color")),
                ),
            ),
            Group(
                "Brightness slider",
                listOf(
                    ColorItem("sys.brTrack", "Track", t(Comp.SYSUI, "brightness_window_slider_progress_bg_color") + t(Comp.PLUGIN, "brightness_window_slider_progress_bg_color")),
                    ColorItem("sys.brFill", "Fill", t(Comp.SYSUI, "brightness_window_slider_progress_color") + t(Comp.PLUGIN, "brightness_window_slider_progress_color")),
                    ColorItem("sys.brIcon", "Icon", t(Comp.SYSUI, "toggle_slider_brightness_icon_color", "brightness_window_icon_color") + t(Comp.PLUGIN, "toggle_slider_brightness_icon_color")),
                ),
            ),
        ),
    )

    val volume = Surface(
        "volume", "Volume dialog",
        "Only the colours you pick are changed.",
        listOf(
            Group(
                "Panel",
                listOf(
                    ColorItem("vol.bg", "Dialog background", t(Comp.PLUGIN, "miui_volume_bg_color")),
                    ColorItem("vol.bgCollapsed", "Collapsed background", t(Comp.PLUGIN, "miui_volume_bg_color_collapse")),
                    ColorItem("vol.expanded", "Expanded panel", t(Comp.PLUGIN, "miui_volume_expand_background")),
                ),
            ),
            Group(
                "Slider",
                listOf(
                    ColorItem("vol.fill", "Slider fill", t(Comp.PLUGIN, "miui_volume_color_accent")),
                    ColorItem("vol.disabled", "Disabled slider", t(Comp.PLUGIN, "miui_volume_disabled_color")),
                ),
            ),
            Group(
                "Buttons & icons",
                listOf(
                    ColorItem("vol.selected", "Selected button", t(Comp.PLUGIN, "miui_volume_ringer_btn_bg_color", "vp_o3_volume_color_btn_selected", "miui_volume_color_btn_seleted")),
                    ColorItem("vol.expandBtn", "Expand button", t(Comp.PLUGIN, "miui_volume_expand_button_color")),
                    ColorItem("vol.tintLight", "Icon tint (light)", t(Comp.PLUGIN, "miui_volume_tint_light")),
                    ColorItem("vol.tintDark", "Icon tint (dark)", t(Comp.PLUGIN, "miui_volume_tint_dark")),
                ),
            ),
        ),
    )

    private val handleTargets = listOf(Comp.SYSUI, Comp.PLUGIN, Comp.HOME, Comp.POCO_HOME)

    val navBar = Surface(
        "navbar", "Navigation bar",
        "Button pictures apply to 3-button navigation; the handle colours to gestures.",
        listOf(
            Group(
                "Buttons",
                listOf(
                    ImageItem("nav.back", "Back (light backgrounds)", t(Comp.SYSUI, "ic_sysbar_back_darkmode"), 192),
                    ImageItem("nav.backDark", "Back (dark backgrounds)", t(Comp.SYSUI, "ic_sysbar_back"), 192),
                    ImageItem("nav.home", "Home (light backgrounds)", t(Comp.SYSUI, "ic_sysbar_home_darkmode"), 192),
                    ImageItem("nav.homeDark", "Home (dark backgrounds)", t(Comp.SYSUI, "ic_sysbar_home"), 192),
                    ImageItem("nav.recent", "Recents (light backgrounds)", t(Comp.SYSUI, "ic_sysbar_recent_darkmode"), 192),
                    ImageItem("nav.recentDark", "Recents (dark backgrounds)", t(Comp.SYSUI, "ic_sysbar_recent"), 192),
                ),
            ),
            Group(
                "Gesture handle",
                listOf(
                    ColorItem("nav.handleOnLight", "Handle over light content", handleTargets.map { ResTarget(it, "navigation_bar_home_handle_dark_color") }),
                    ColorItem("nav.handleOnDark", "Handle over dark content", handleTargets.map { ResTarget(it, "navigation_bar_home_handle_light_color") }),
                ),
            ),
        ),
    )

    val launcher = Surface(
        "launcher", "Launcher", null,
        listOf(
            Group(
                "Backgrounds",
                listOf(
                    ImageItem("home.folderBg", "Folder background", launcher("folder_background")),
                    ImageItem("home.searchBg", "Search bar background", launcher("bg_search_bar_light"), 1080),
                    ImageItem("home.recentsMenuBg", "Recents menu background", launcher("recent_menu_bg"), 1080),
                ),
            ),
            Group(
                "Launcher colours",
                listOf(
                    ColorItem("home.searchBgLight", "Search bar background (light)", launcher("search_bar_bg_color_light")),
                    ColorItem("home.searchBgDark", "Search bar background (dark)", launcher("search_bar_bg_color_dark")),
                    ColorItem("home.searchStrokeLight", "Search bar outline (light)", launcher("search_bar_stroke_color_light")),
                    ColorItem("home.searchStrokeDark", "Search bar outline (dark)", launcher("search_bar_stroke_color_dark")),
                    ColorItem("home.recentsMenu", "Recents menu background", launcher("recent_menu_bg_color")),
                    ColorItem("home.memory", "Memory text", launcher("txt_memory_info_color")),
                ),
            ),
            Group(
                "Grid & layout",
                listOf(
                    NumberItem("grid.columns", "Home screen columns", ValueType.INTEGER, launcher("config_cell_count_x"), 3, 6, 4, ""),
                    NumberItem("grid.rows", "Home screen rows", ValueType.INTEGER, launcher("config_cell_count_y"), 4, 8, 6, ""),
                    NumberItem("grid.dock", "Dock height", ValueType.DIMEN, launcher("hotseats_height"), 60, 140, 96, "dp"),
                ),
            ),
        ),
    )

    val notifications = Surface(
        "notifications", "Notifications panel",
        "Toggle pictures work best as white-on-transparent PNGs; the system tints them.",
        listOf(
            Group(
                "Background",
                listOf(ImageItem("notif.panelBg", "Notifications panel background", t(Comp.SYSUI, "notification_panel_window_bg"))),
            ),
            Group(
                "Shortcuts panel icons",
                TOGGLES.flatMap { (key, label) ->
                    listOf(
                        ImageItem("toggle.$key.on", "$label (on)", t(Comp.PLUGIN, "ic_cc_qs_${key}_on") + t(Comp.SYSUI, "ic_cc_qs_${key}_on"), 192),
                        ImageItem("toggle.$key.off", "$label (off)", t(Comp.PLUGIN, "ic_cc_qs_${key}_off") + t(Comp.SYSUI, "ic_cc_qs_${key}_off"), 192),
                    )
                },
            ),
        ),
    )

    val recents = Surface(
        "recents", "Recents background", null,
        listOf(Group(null, listOf(ImageItem("recents.bg", "Recent tasks background", launcher("recents_background", "recent_task_bg"))))),
    )

    val apps = Surface(
        "apps", "Apps",
        "Contacts, Dialer and Messaging items only affect Xiaomi's own versions of those apps.",
        listOf(
            Group(
                "Settings",
                listOf(
                    ImageItem("app.settingsBg", "Settings background", t(Comp.SETTINGS, "miuix_appcompat_settings_window_bg_light", "settings_window_bg_light")),
                    ImageItem("app.settingsBgDark", "Settings background (dark mode)", t(Comp.SETTINGS, "miuix_appcompat_settings_window_bg_dark")),
                ),
            ),
            Group(
                "Messaging",
                listOf(
                    ColorItem("app.mmsBg", "Conversation background", t(Comp.MMS, "conversation_bg")),
                    ColorItem("app.mmsBar", "Conversation top bar", t(Comp.MMS, "conversation_actionbar_bg")),
                ),
            ),
            Group(
                "Contacts & Dialer",
                listOf(
                    ImageItem("app.callBtn", "Call button background", t(Comp.CONTACTS, "dialer_btn_call_bg"), 480),
                    ImageItem("app.incallBg", "In-call background", t(Comp.CONTACTS, "incall_bg", "ic_incall_bg")),
                    ImageItem("app.dialpadBg", "Dialpad background", t(Comp.CONTACTS, "dialpad_bg", "fragment_dialpad_bg")),
                    ColorItem("app.missedCall", "Missed call text", t(Comp.CONTACTS, "missed_call_text_color", "call_log_missed_call_text_color")),
                ),
            ),
        ),
    )

    val fingerprint = Surface(
        "fingerprint", "Fingerprint",
        "Shown only on phones with an in-screen fingerprint sensor.",
        listOf(
            Group(
                null,
                listOf(ImageItem(FINGER_ICON, "Fingerprint icon", t(Comp.SYSUI, "finger_circle_image_normal", "finger_circle_image_light", "finger_circle_image_aod"), 480)),
            ),
        ),
    )

    /** Screens driven entirely by this catalog. */
    val genericSurfaces = listOf(palette, systemColours, volume, navBar, launcher, notifications, recents, apps, fingerprint)

    fun surface(id: String) = genericSurfaces.first { it.id == id }

    val hideItems = listOf(
        HideItem("hide.notifBg", "Notification panel background", images = t(Comp.SYSUI, "notification_panel_window_bg")),
        HideItem("hide.brightnessIcons", "Brightness slider end icons", images = t(Comp.SYSUI, "ic_brightness_slider_animate_icon") + t(Comp.PLUGIN, "ic_brightness_slider_animate_icon")),
        HideItem("hide.searchBar", "Home screen search bar", images = launcher("bg_search_bar_light")),
        HideItem("hide.fingerIcon", "Fingerprint icon", images = t(Comp.SYSUI, "finger_circle_image_normal", "finger_circle_image_light", "finger_circle_image_aod")),
        HideItem("hide.handle", "Gesture handle", colors = handleTargets.flatMap {
            listOf(ResTarget(it, "navigation_bar_home_handle_dark_color"), ResTarget(it, "navigation_bar_home_handle_light_color"))
        }),
    )

    const val FINGER_FRAMES = 24
    val fingerStyles = listOf("a", "b", "c", "d", "e", "f")
}

data class HideItem(
    val id: String,
    val label: String,
    val images: List<ResTarget> = emptyList(),
    val colors: List<ResTarget> = emptyList(),
)

val TOGGLES = listOf(
    "wifi" to "Wi-Fi", "bluetooth" to "Bluetooth", "cellular" to "Mobile data", "airplane" to "Airplane mode",
    "flashlight" to "Flashlight", "rotation_lock" to "Rotation lock", "location" to "Location", "gps" to "GPS",
    "hotspot" to "Hotspot", "nfc" to "NFC", "mute" to "Mute", "vibrate" to "Vibrate", "quiet" to "Do not disturb",
    "night_mode" to "Dark mode", "paper_mode" to "Reading mode", "power_save" to "Battery saver",
    "auto_brightness" to "Auto brightness", "drive_mode" to "Driving mode", "sync" to "Sync",
)
