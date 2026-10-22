# Hands-on: liquid-glass launcher on a Xiaomi Mi TV 4A Pro (India)

Field notes from running the custom launcher from this repo (`launcher/`) on a real
Xiaomi Mi TV 4A Pro (India) — model `MiTV-AXSO2` (codename `dangalUHD`) — updated to
Android 11 (API 30). Everything below was verified working on the actual TV, not just
an emulator.

## What was built on top of the base repo
- **Liquid-glass focus system (API 30-safe).** Android 9/11 has no `RenderEffect`, so
  the glass is done without it:
  - `GlassBackdrop` — a cached 1/8-scale downsampled blur layer built from the two
    wallpaper `ImageView`s. It re-samples whichever layer currently has the max alpha
    (so it always matches what's on screen during the cross-fade) and re-projects the
    blurred wallpaper under the focused card through `GlassHighlight`.
  - `GlassHighlight` — clips the re-projected blur to the rounded card path and layers a
    vertical body tint, a top sheen, an inner specular edge and a thin rim on top.
  - The backdrop is rebuilt on every wallpaper cross-fade (once when the fade starts,
    once after it settles), and the highlight is invalidated right after each rebuild so
    the frosted content always matches the current wallpaper.
- **Customization** (all in `app/src/main/res/values/config.xml` / `build.gradle`):
  blanked the social-follower pills, renamed the applicationId and app label, set the
  greeting name, shipped 4 own 1280x720 wallpaper JPEGs in `res/drawable-nodpi/`.

## The wallpaper veil + picker (HUD v3, verified on Android 11)
The base repo dims wallpapers uniformly with four scrim rectangles. This fork adds a
Prime-Video-style veil instead: the wallpaper stays sharp across the middle of the
screen and progressively blurs toward the top (the pill row) and the bottom (the dock
band). All of it is XML-comment-safe (no `--` inside comments!) and configured in
`app/src/main/res/values/config.xml`:

- `use_wallpaper` (bool) / `wallpaper_seconds` (rotation interval) / `use_bundled_wallpapers`.
- `wallpaper_dirs` string-array + `externalPhotos()` scans those absolute paths plus all
  mounted USB roots found under `/storage/*` at mount time (jpg/jpeg/png/webp,
  alphabetical, capped). `READ_EXTERNAL_STORAGE` is requested at runtime; photos only
  show after the grant.
- `wallpaper_clear_top` / `wallpaper_clear_bottom` / `wallpaper_blur_fade` (per-mille of
  screen height: defaults 300/640/140 — the clear zone is scaled 0.30–0.64 of the screen
  height with a 0.14 fade band on each edge).
- `wallpaper_scrim` (colors, kept as a soft second layer over the fade bands).
- `show_wallpaper_pill`: adds a fourth `Wallpaper` pill next to Settings with a new
  `ic_wallpaper` icon. OK opens `WallpaperPicker`, a D-pad chooser (6-column grid, white
  focus ring, live thumbnails of store + external photos). OK on a tile shows that
  wallpaper immediately (without waiting for the next rotation tick) and closes the
  picker; BACK/OK-outside closes without changing anything.
- `WallpaperVeil` draws the coarse blur (the cached 1/8-scale bitmap `GlassBackdrop`
  already builds) masked by keyed `LinearGradient`s through `DST_IN`, so the blur fades
  smoothly instead of hard-cutting.
- Refresh triggers verified: wallpaper cross-fade (veil follows the new image), app
  window focus (covers permission-grant relaunch), and MEDIA_MOUNTED broadcasts (USB
  stick with a `Wallpapers` folder shows up without a relaunch; insertion from shell
  can't be simulated for security reasons, so the receiver path is validated by code
  review + manual focus events).

## Build notes (Windows)
- JDK 17 (Temurin) + Gradle 8.9 / AGP 8.5.2; Android Studio's bundled JBR (25) is too new
  for Gradle 8.9. `compileSdk 34` auto-installs the platform on first build.
- Make sure `local.properties` is plain ASCII (`sdk.dir=...` with forward slashes works);
  a PowerShell-written UTF-16 file breaks the build with a confusing error.

## Verified working on this TV (Android 11 / dangalUHD)
- Launcher installs and runs as the default HOME (`cmd package set-home-activity ...`;
  stock launcher must be disabled/made non-default if it keeps winning).
- `sys.boot_completed` after reboot → the custom launcher comes up first, not PatchWall.
- D-pad navigation, app grid, dock, wallpaper cross-fade every 30 s with the glass
  backdrop re-projecting correctly — no crashes, no visual glitches, on ~2 GB RAM
  Mali-class hardware.
- 23 stock packages disabled with `pm disable-user --user 0` (ads/telemetry/PatchWall),
  launcher unaffected. Freed ~1.7 GB with `pm trim-caches`. All disabled via
  `disable-user` only — fully reversible with `pm enable <package>`.

## Gotchas discovered on this model/firmware
1. **Play Protect silently blocks ADB installs.** `adb install` / `pm install` hang
   forever with no output while a Play Protect scan dialog (or its background verifier)
   waits. Fix before installing:
   ```
   adb shell settings put global verifier_verify_adb_installs 0
   adb shell settings put global package_verifier_enable 0
   adb shell am force-stop com.android.vending
   ```
2. **Emulator D-pad**: Android TV AVDs have `hw.keyboard = no` by default — arrow keys,
   Back and Home from the host (and the extended-controls D-pad) do nothing until you
   set `hw.keyboard = yes` in the AVD's `config.ini` and cold-boot.
3. **Only TV system image for API 30** is `x86` (32-bit); there is no x86_64 TV image for
   API 30/31.
4. `svc power reboot` is unreliable on dangal firmware — use `adb reboot` instead.
5. Disabling PatchWall means the remote's PatchWall button does nothing (expected).
6. USB debugging opens network ADB on port 5555 — the TV accepts `adb connect <ip>:5555`
   with no pairing prompt beyond the on-device debug authorization. Keep that in mind
   on your home network.

## Debloat profile used
See `profiles/xiaomi-mitv-axso2.txt`. Rules that were followed:
- `pm disable-user --user 0` only — never uninstall.
- Never touch Play Store/Services, TV input services, IME, Bluetooth/remote services.
- Kept on purpose: Assistant, Chromecast/mediashell, Mi Cast (milinkservice),
  smartshare/Miracast, and all real streaming apps.
