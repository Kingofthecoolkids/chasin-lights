# ClearScreen (working title)

A novelty Android live wallpaper that streams the rear camera as the home-screen
background, calibrated so objects behind the phone line up at roughly their real
size and position, rather than looking like an arbitrary zoomed-out camera feed.

## Status

This is a from-scratch implementation covering everything in the project brief:
the Camera2 + OpenGL rendering pipeline, visibility/battery-tied camera
lifecycle, calibration (auto defaults + manual sliders + guided mode), the
extra settings, and a Compose setup UI. **It has not been built or run on a
device or emulator.** The sandbox this was written in has a JDK and Gradle but
no Android SDK, no emulator, and no physical device, so there was no way to run
`./gradlew assembleDebug`, let alone install and test it. Treat this as a
careful, complete-on-paper implementation that needs its first real build/run
pass before you trust it. The "How to test" section below says exactly what to
check at each stage once you do.

## Project structure

```
app/src/main/java/com/clearscreen/app/
  wallpaper/   WallpaperService + Engine: the glue, and the one place that
               decides when the camera is allowed to be open.
  render/      EGL plumbing, the camera-texture GL renderer, and the
               crop/zoom/offset/rotation matrix math.
  camera/      Camera2 session management, lens listing, and the
               auto-calibration formula.
  settings/    Compose setup UI (permission screen, calibration + extra
               settings, live preview).
  data/        WallpaperSettings model + DataStore-backed repository.
  util/        Display physical size, battery/power-save monitoring.
```

## Why Camera2, not CameraX

CameraX needs a `LifecycleOwner`, and `WallpaperService.Engine` isn't one --
you'd have to fake one, fighting CameraX's internal lifecycle/threading
assumptions, which were designed around Activities and Fragments, not wallpaper
engines. Since the calibration feature already requires a custom OpenGL
pipeline sampling a raw `SurfaceTexture` (to apply the crop/zoom/offset
transform on the GPU), Camera2 gives direct control over exactly when the
sensor opens and closes relative to `Engine.onVisibilityChanged` -- which is
the one thing this app cannot get wrong, for both battery life and the
camera privacy indicator. CameraX's higher-level conveniences (use-case
binding, built-in viewport/crop handling) aren't needed here and would mostly
be redundant with the GL transform this app already has to write.

## How calibration works

- **Zoom** has a real auto-computed default. A camera with horizontal field of
  view captures a real-world width of `2 * d * tan(fov/2)` at distance `d`. For
  the phone to look transparent, the screen should show exactly the real-world
  width it physically occupies at that same distance. Working through the
  algebra (`CameraCalibrationMath.kt`) gives:

  ```
  zoom = (viewingDistanceMm * sensorPhysicalWidthMm) / (focalLengthMm * screenPhysicalWidthMm)
  ```

  using `CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE` /
  `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` and the screen's physical width from
  `DisplayMetrics` (xdpi), at an assumed arm's-length viewing distance (300mm).
  This is a real derivation, but it rests on an idealized assumption (the
  viewer's eye is on the camera's optical axis), and OEM-reported xdpi/ydpi is
  sometimes inaccurate -- hence the slider on top of it.
- **Horizontal/vertical offset** have **no computable auto-default**. Camera2
  doesn't expose where the lens physically sits relative to the screen's visual
  center, and there's no reliable generic source for that. These start at 0 and
  are meant to be dialed in with the slider or guided calibration -- don't
  expect a hardware-perfect number here on first launch.
- **Guided calibration mode** overlays a crosshair + vertical edge guides on
  the live preview and asks you to hold the phone at arm's length against a
  straight real-world edge, then adjust zoom/offset until the edge lines up
  through the gap around the phone.
- The whole illusion also ignores parallax between the viewer's eye and the
  camera's physical position -- true at exactly one distance/angle, which is
  why "arm's length, looking straight at the screen" is the implicit target
  case, same as every other app of this kind.

## Known limitations (read before you ship this)

- **Background camera access (the brief's main flagged risk).** This is a
  plain `WallpaperService`, not a foreground `Service`. Camera access while
  `onVisibilityChanged(true)` is in effect is expected to work without a
  foreground-service promotion, because a visible wallpaper Engine's process
  is kept at a high-enough importance by the OS -- this is the pattern other
  "transparent screen" live wallpapers on the Play Store rely on. **This is
  unverified in this environment.** If real-device testing on Android 11+
  (especially 14+, where Android added a `FOREGROUND_SERVICE_CAMERA`
  requirement for services that use the camera) shows the camera getting
  denied or the process getting killed while visible, the fix is documented
  in a comment on the `<service>` element in `AndroidManifest.xml`: move
  camera ownership into a bound foreground `Service` with
  `foregroundServiceType="camera"`, which forces a persistent notification.
  That tradeoff is deliberately not made by default, since users don't expect
  a notification from a wallpaper.
- **The green camera dot (Android 12+)** will show whenever the camera is
  open. This can't be hidden, and isn't a bug -- the engine's whole job is to
  make sure that dot is *only* lit while the wallpaper is actually visible.
- **Calibration is an approximation**, as described above -- correct at one
  assumed distance/angle, refined manually from there.
- **Blur** is a cheap single-pass 9-tap box blur done directly in the
  fragment shader, not a proper separable Gaussian with an intermediate
  framebuffer. It's fine for the "soften for readability" use case the brief
  describes, not for a polished bokeh look.
- **Preview-screen camera use** (the live preview in Setup) follows the same
  visible-only discipline, tied to the Activity's `onResume`/`onPause` rather
  than wallpaper visibility.
- Untested across real device variety: different `sensorOrientation` values,
  foldables, tablets in landscape home screens, multi-camera logical/physical
  lens setups beyond the simple "shortest focal length = ultrawide" heuristic
  used to rank lenses.

## Build

Requires Android Studio (or command-line Gradle) with the Android SDK
(`compileSdk`/`targetSdk` 35) and a JDK 17+.

```
./gradlew assembleDebug
```

The Gradle wrapper is already checked in (`gradlew`, `gradle/wrapper/`), built
against Gradle 8.9 with Android Gradle Plugin 8.7.2 and Kotlin 2.0.21. If
Android Studio prompts to upgrade AGP/Kotlin/Compose BOM versions, that's
expected and fine -- these were picked to be reasonably current as of writing,
not pinned for any particular reason.

## How to set the wallpaper

1. Install and launch the app (`com.clearscreen.app` / "ClearScreen").
2. Grant the camera permission when prompted (the explanation screen is shown
   first since a wallpaper service can't request permissions itself).
3. Adjust calibration/extra settings if you want, using the live preview.
4. Tap **Set as wallpaper** -- this launches
   `WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER` pointing at
   `ClearScreenWallpaperService`, and the system's own wallpaper picker takes
   it from there.

## How to test each stage on a real device

Since this couldn't be run here, test in this order once you have a device:

1. **Basic feed.** Set the wallpaper, go to the home screen. You should see
   the rear camera feed full-screen behind your icons. Check it isn't
   obviously squished/rotated wrong in both portrait and (if your launcher
   supports it) landscape.
2. **Visibility/battery.**
   - Open any app over the home screen: the green camera-in-use indicator
     (Android 12+) should disappear almost immediately. Go back to the home
     screen: it should reappear and the feed should resume.
   - Turn the screen off and back on: same check.
   - Revoke the camera permission in system settings while the wallpaper is
     set: the home screen should fall back to a neutral background (or the
     last frame if one was ever captured) rather than crashing.
   - Enable Power Saver mode, or set the battery-saver threshold above your
     current battery level in Setup: the feed should freeze on the last
     frame rather than continuing to draw from the camera.
3. **Calibration.** Open Setup, use the live preview. Check the zoom slider's
   starting position looks roughly plausible (not wildly zoomed in/out) on
   first launch -- that's the auto-default. Use guided calibration against a
   door frame or table edge at arm's length and confirm the sliders visibly
   change the crop in the preview and (after a moment) on the actual
   wallpaper. Confirm "Reset to default" actually resets.
4. **Extra settings.** Toggle blur and dim overlay and confirm they visibly
   affect the preview and wallpaper. Change the fps cap and, if you can
   profile it, confirm frame draws are actually throttled. If your device has
   more than one rear lens, confirm the lens chips appear and switching lenses
   restarts the feed on that lens.
5. **Polish.** Check the launcher icon and the wallpaper picker's thumbnail
   (both placeholders -- replace with final branding whenever ready) show up
   as expected, and skim this README against what you actually built.
