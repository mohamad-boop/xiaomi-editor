# Xiaomi Editor

An ad-free Android app for making and editing MIUI / HyperOS themes (`.mtz`) on the phone itself — with a boot animation studio and on-device previews.

## What it does

**Theme editor** — open an existing `.mtz`, an installed theme (via Shizuku), or start from scratch, then edit:

- Wallpapers (home & lock screen), with "set as phone wallpaper now"
- App icons: import a whole icon pack (auto-matched by `appfilter.xml`), pick icons per app from a searchable grid, recolour, icon mask & folder shapes, dynamic calendar/clock icons, icon size & label colour
- Launcher: folder / search bar / recents backgrounds, colours, grid & dock
- Theme palette, system colours, status bar (signal & Wi-Fi styles), notifications panel & Control Center toggle icons, volume dialog, navigation bar & gesture handle
- Fingerprint icon and unlock animation, sized as a % of your real sensor
- Hide elements, recents background, Settings / Messaging / Contacts colours and backgrounds
- Fonts (custom `.ttf` or the phone's built-in fonts)
- Sounds: ringtone, notification and alarm — or copy in your current ones so applying a theme keeps them
- Mix & match: copy any part (lock screen style, AOD, widgets, super wallpaper…) from another theme
- Advanced: any colour / dimen / integer / bool / string value or picture in any component, by resource name
- Description, preview images, presets, per-section reset
- Previews of almost everything, including the fingerprint drawn at your sensor's actual position and size

**Boot animation studio** — build a `bootanimation.zip` from a video, GIF, image frames or an existing zip: full-screen frames, size / position controls, loop or play-once, optional boot sound, palette compression (256/128/64 colours), live preview. Add it to the theme or export a Magisk module for rooted phones.

**Keeper** — re-applies a chosen theme after reboot on MIUI / HyperOS versions that still expose the theme manager's apply entry point.

## How to use

### Install

1. Download `XiaomiEditor.apk` from the Releases page (or build it — see below) and install it.
2. Optional but recommended: install [Shizuku](https://shizuku.rikka.app/) and start it (wireless debugging or root). Open Xiaomi Editor and tap **Allow** on the Shizuku card. Shizuku unlocks reading your installed themes, installing into the Themes app, the fingerprint-animation switch, accurate sensor position, sound protection and one-tap permission setup.

### Make a theme

1. **Editor** tab → **Open .mtz**, **Pick an installed theme** (Shizuku), **Pick a theme from a folder**, or **Start from scratch**.
2. Tap any tile to edit that part. Tiles with a dot have changes. Most pages show a preview at the top; **Preview theme** shows everything at once.
3. **Description** sets the name the Themes app shows.
4. **Presets & reset** saves your choices to reuse on another theme, or undoes one section.

### Icons

1. **App icons** → **Import icon pack**, choose a pack. Matching icons are assigned automatically.
2. Tap **Pick** on any app to choose from a searchable grid of the pack's icons (best matches first), or from your gallery.
3. Tap **Use in theme**. Back on the App icons page you can recolour icons, set the mask / folder shape, dynamic calendar & clock, icon size and label colour.

### Fingerprint

1. **Fingerprint** → choose an icon picture and/or an unlock animation (GIF or picture + effect).
2. Set each size as a % of your sensor (up to 400 % for the icon, 600 % for the animation) and check the preview, which draws them at the sensor's real position.
3. Compression (256 colours by default) keeps the theme small.
4. On HyperOS, turn on **Use the theme's unlock animation** (Shizuku) so the system plays the theme's animation instead of the one picked in Settings.

### Boot animation

1. **Boot animation** tab → choose a video, GIF, image frames or an existing `bootanimation.zip`.
2. Adjust picture size, position, frame rate, length, playback and compression; the layout preview updates live. Tap **Build**.
3. Check the preview, optionally add a boot sound, then **Add to my theme** (or **Save .zip** / **Magisk module** for rooted phones).
4. **Resize this animation** re-renders an existing one, e.g. to fill the screen or shrink it.

### Sounds

**Sounds** → choose a ringtone, notification and alarm sound, or tap **Use my current sounds** so applying the theme keeps the ones you have. **Protect sound settings** restores ringtones and sound-effect settings (e.g. Dolby) that applying a theme resets.

### Everything else

- **Mix & match** — open another `.mtz` and copy chosen parts (lock screen style, AOD, widgets…) into yours.
- **Advanced** — set any resource value or picture by name in any component.

### Apply

- **Export** — save the `.mtz` (choose name and folder) or **Install to Themes app** via Shizuku.
- **Apply / Apply & keep** (MIUI and HyperOS versions with a direct apply route) — hands the theme to the system theme manager; **Keeper** re-applies it after reboot. Temporary copies are cleaned up automatically.
- **Install via Theme Editor** (HyperOS 3) — see below.

## Applying themes on HyperOS 3

HyperOS 3's Themes app only applies themes that carry a Xiaomi licence and removes others on Apply. This app does **not** create or fake licence files and does **not** block the Themes app's licence checks. On those versions it exports the finished theme and hands it to a separate tool of your choice for installing.

On older MIUI / HyperOS versions it applies themes directly through the system theme manager.

## Building

JDK 17 and the Android SDK (platform 34):

```bash
./gradlew assembleDebug
```

or on Windows, `build.bat` (expects a JDK 17 and Gradle in `../AndroidBuildTools`). The APK lands in `app/build/outputs/apk/debug/`.

## Credits

Started from HyperIcons by Stephen Benjamin (MIT), whose icon-pack matching and `.mtz` icon builder live on in the Icons section. Uses [Shizuku](https://github.com/RikkaApps/Shizuku) for the parts that need shell access.

Not affiliated with or endorsed by Xiaomi. MIUI, HyperOS and POCO are trademarks of Xiaomi.

## License

MIT — see [LICENSE](LICENSE).
