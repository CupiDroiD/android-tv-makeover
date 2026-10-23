# Free My TV vs. this fork — comparison note

Reference reviewed: [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv)
(MIT, ~3.1k stars, 180 commits at the time of writing). Reviewed from its README plus
`app/src/io/freemytv/HomeView.java` and `HomeMode.java`.

It solves the same problem this fork solves — an ad-free TV home screen, no root, no
uninstalls, one button back to stock — but it is a different engineering bet. This note
records what applies to `MiTV-AXSO2` (Android 11 / API 30) and what is worth porting.

## Side by side

| | Free My TV | This fork (`launcher/`) |
|---|---|---|
| Targets | Google TV / Android TV 8+, plus LG (webOS 3+) and Samsung (Tizen 3+) web builds | One TV: MiTV-AXSO2, Android 11 |
| Build | No Gradle, no libraries: `aapt2 → javac → d8 → zipalign → apksigner`, ~2 MB APK | Gradle 8.9 / AGP 8.5.2, `launcher/gradlew.bat assembleDebug` |
| Rendering | One hand-drawn `Canvas` for the whole home screen | View tree + a `GlassHighlight` overlay |
| Frosted glass | Android 13+ shaders (AGSL). On Android 8–12 it draws **plain fills**; on 8–11 no GPU layers and lighter shadows | Built for API 30: cached 1/8-scale `GlassBackdrop` + `GlassHighlight` re-projection (no `RenderEffect` on this TV) |
| Becomes HOME | In-app ADB to `127.0.0.1:5555`: `cmd package set-home-activity`, `settings put secure screensaver_components` | Same class of commands, driven over external `adb` from the PC |
| Undo | In-app *Home Screen → Original*; list of disabled packages kept in TV settings, so reinstalling restores them | `pm enable <package>` per package; debloat profile in `profiles/` |
| Debloat | `DEBLOAT` list in `HomeMode.java` (Google extras + per-brand: TCL, Vestel, Xiaomi, KTC, Philco, Multilaser) with a `PROTECTED` never-touch list | `pm disable-user --user 0` from the PC, 23 packages, same protected-set rule |
| Safety net | Watchdog in a **separate process**: reverts to stock home after 3 crashes in 5 min; boot check re-applies the choice after TV updates | None — a crash loop leaves the TV on a broken home |
| Content | TMDB trending picks (own key), Open-Meteo weather/sunrise, optional Binance prices, day/night by real sunset, screensaver | App dock + grid, clock/greeting, wallpaper rotation, optional follower pills |
| External keys | `tmdb_key` required for picks, optional `watchmode_key` | None |
| i18n | 24 languages (4 hand-translated, rest machine) | English |
| Tested hardware | 98" TCL on Android 14 (its perf numbers: 60 fps, 96 MB vs 242 MB stock) | MiTV-AXSO2, Android 11, ~2 GB RAM |

## What does not transfer to our TV

- **The glass.** Their frosted glass needs Android 13 shaders. On our API 30 panel the
  app degrades to flat, opaque fills — the look in their screenshots is simply not what
  runs on a MiTV-AXSO2. Our `GlassBackdrop`/`GlassHighlight` path is the only way to get
  that material on this firmware, which is why this fork still exists.
- **The Xiaomi debloat list is partial.** It covers telemetry and promo packages
  (`com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.mitv.tvhome.mitvplus`,
  `com.xm.webcontent`) and a MediaTek-era Xiaomi service list (`mitv.service`,
  `com.mitv.tvlock`, `com.xiaomi.mitv.updateservice`, …), but `MiTV-AXSO2` is not a
  tested model, and its remote-button remap only claims Xiaomi *projectors*. Our
  hand-verified `profiles/xiaomi-mitv-axso2.txt` stays the source of truth.
- **The feature set.** TMDB keys, Watchmode, crypto prices, LG/Samsung packaging are
  scope we do not need.

## Worth borrowing (in order of value)

1. **Watchdog + boot check.** A second process that flips back to the stock home after
   three crashes in five minutes, and a boot receiver that re-asserts the choice after
   an OTA. This is the one genuinely missing safety property in our build.
2. **Capability-gated rendering.** They branch the visual language by API level instead
   of forcing one design everywhere: shader path where available, a deliberately plain
   path below. Applied here it would also settle the parked wallpaper question — commit
   to the API-30 look (downsample glass, no realtime blur) instead of chasing Android 13
   aesthetics on hardware that cannot render them.
3. **Self-ADB instead of PC-side adb.** Doing `set-home-activity`, screensaver and
   `pm disable-user` from inside the app removes the "keep a PC on the same Wi-Fi"
   step from every install and every undo. Our field notes already record that the TV
   opens `:5555` once USB debugging is on, so the path is available.
4. **Single-Canvas rendering.** One `Canvas` avoids the per-view measure/layout/draw
   cost and made their 60 fps budget on weaker silicon possible. Large refactor, real
   payoff only if we hit frame drops.

## Not worth borrowing

- Machine-translated catalogues and multi-platform packaging — scope creep for a
  one-model project.
- Their content pipeline (TMDB region lists, crypto) — new external dependencies and
  keys, no gain for a dock-and-grid home screen.

## License position

MIT on both sides, so code can be copied with attribution. If the watchdog or the
capability-gating pattern is ported, keep a credit line in the file header and in
`README.md`.
