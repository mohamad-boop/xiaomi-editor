# Xiaomi Editor

An ad-free **theme builder** for MIUI and HyperOS. Build complete themes (`.mtz`) on the phone itself — from scratch or on top of an existing theme — with a boot animation studio and live previews of almost everything.

## What it does

**Theme builder** — start from scratch, or build on an existing `.mtz` or a theme installed on your phone (via Shizuku), and put together:

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

**Apply & Keeper** — on MIUI and HyperOS versions whose theme manager still has a direct apply route, **Apply** applies the theme straight away and **Apply & keep** has the Keeper re-apply it after every reboot (plus **Re-apply now** any time). The app detects this automatically and shows the right buttons for your phone.

## How to use

### Install

1. Download the APK from the [Releases page](https://github.com/mohamad-boop/xiaomi-editor/releases) (or build it — see below) and install it. Android will ask you to allow installing from your browser or file manager.
2. Optional but recommended: install [Shizuku](https://shizuku.rikka.app/) and start it (wireless debugging or root). Open Xiaomi Editor and tap **Allow** on the Shizuku card. Shizuku unlocks reading your installed themes, installing into the Themes app, the fingerprint-animation switch, accurate sensor position, sound protection and one-tap permission setup.

### Build a theme

1. **Builder** tab → **Start from scratch**, or build on an existing theme: **Build on an installed theme** (Shizuku), **Build on a .mtz file**, or **Build on a theme from a folder**.
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
- **Apply / Apply & keep** (MIUI and HyperOS versions with a direct apply route) — hands the theme to the system theme manager. With **Apply & keep**, the **Keeper** tab re-applies it after every reboot, and **Re-apply now** does it on demand. If the Themes app asks you to pick a file, choose `keeper.mtz` (or `apply.mtz`) in Download › XiaomiEditor › temp. Temporary copies are cleaned up automatically.
- **Install via Theme Editor** (HyperOS 3) — see below.

## Applying themes on HyperOS 3

HyperOS 3's Themes app only applies themes that carry a Xiaomi licence and removes others on Apply. This app does **not** create or fake licence files and does **not** block the Themes app's licence checks. On those versions it exports the finished theme and hands it to a separate tool of your choice for installing.

On older MIUI / HyperOS versions it applies themes directly through the system theme manager.

## Keeping your theme applied

HyperOS resets themes that weren't bought from the Themes store, usually after a few hours or a reboot. Xiaomi Editor doesn't include anything that works around that check.

If you want your theme to stay applied, [Zyper](https://play.google.com/store/apps/details?id=com.htetz.zyper) is a separate app on the Play Store built for exactly that. Install it, run through its setup checklist, and leave it running alongside your theme. It's made by another developer and isn't part of this project.

On MIUI / HyperOS versions that still have the theme manager's direct apply route, this app's own **Keeper** tab re-applies your theme after reboots and whenever you tap **Re-apply now**. Turn on Shizuku (or the pop-up permissions in the Keeper's checklist) so it can do that right after boot.

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
