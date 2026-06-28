# Flip Card Wallpaper

High-performance Android live wallpaper written in Kotlin. The app creates a lenticular "flip cards" effect by reacting to the phone's horizontal tilt and blending through a user-selected wallpaper sequence.

The project targets Android 16 / API 36 and is tuned for Pixel high-refresh-rate displays.

## Features

- Live wallpaper service for home screen and lock screen.
- Multiple user-selected wallpapers, not limited to two images.
- Tilt-reactive transitions driven by device rotation sensors.
- Neutral phone position always returns to the first image.
- Optional loop mode:
  - Off: the selected start side advances through the list and stops on the last image.
  - Snap: tilt left/right steps through the image list in either direction.
  - Smooth transition: tilt left/right blends continuously through the image list in either direction.
- Transition effects:
  - Crossfade
  - Slide
  - Wipe
  - Swipe fade
  - Zoom fade
  - Depth
- Motion tuning:
  - Transition speed
  - Tilt trigger threshold
  - Tilt sensitivity
  - Step angle per photo
  - Non-loop start side
  - Loop transition mode
- Full-screen image fit editor for crop, resize, and center positioning.
- Jetpack Compose configuration UI with Material 3 dynamic color.
- Light/dark mode toggle with a circular theme reveal transition.
- Transparent edge-to-edge status and navigation bars.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Live Wallpaper API
- `Sensor.TYPE_ROTATION_VECTOR` with fallbacks
- Hardware-accelerated Android `Canvas`
- SharedPreferences for wallpaper configuration

## Requirements

- Android Studio with Android Gradle Plugin support for SDK 36.
- Android SDK 36 installed.
- JDK 11 or newer.
- Device or emulator running Android 12+.

Project SDK configuration:

- `minSdk`: 31
- `targetSdk`: 36
- `compileSdk`: 36.1

## Build

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

Run unit tests and lint:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Full local verification:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Release APK

The project is configured to use `flipcard-release.jks` from the repository root as the release keystore. The keystore file is intentionally ignored by Git.

### Unsigned Release APK

If release signing passwords are not configured, Gradle can still build an unsigned release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

The unsigned APK is generated under:

```text
app/build/outputs/apk/release/
```

Unsigned release APKs are useful for local inspection, but Android devices will not install them as production apps until they are signed.

### Signed Release APK

The repository expects the release keystore at:

```text
flipcard-release.jks
```

If you need to recreate that keystore, run:

```powershell
keytool -genkeypair -v -keystore flipcard-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias flipcard
```

Add the keystore passwords to `local.properties`:

```properties
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=flipcard
RELEASE_KEY_PASSWORD=your_key_password
```

`RELEASE_KEY_ALIAS` defaults to `flipcard`, and `RELEASE_KEY_PASSWORD` defaults to `RELEASE_STORE_PASSWORD` if omitted.

You can also provide the same values as environment variables instead of editing `local.properties`.

Build the signed release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

The signed APK is generated under:

```text
app/build/outputs/apk/release/
```

Before distributing a release build, run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease
```

## Run

1. Install the debug APK from Android Studio or Gradle.
2. Open **Flip Card Wallpaper**.
3. Add at least two photos.
4. Tap **Set live wallpaper**.
5. Apply it from the Android live wallpaper picker.

The wallpaper metadata is declared in `app/src/main/res/xml/lenticular_wallpaper.xml`, and the service is registered in `AndroidManifest.xml` with `android.permission.BIND_WALLPAPER`.

## Architecture

Main components:

- `MainActivity.kt`
  - Compose configuration UI.
  - Photo picker integration.
  - Image ordering and crop/fit controls.
  - Transition and motion tuning controls.
  - Dynamic light/dark theme handling.
- `LenticularWallpaperService.kt`
  - `WallpaperService` implementation.
  - Per-engine sensor lifecycle.
  - Photo transition rendering.
  - Home/lock screen engine target detection.
- `WallpaperPrefs.kt`
  - Persistent image URIs.
  - Transition, loop, speed, threshold, sensitivity, and theme settings.
- `TransitionEffect.kt`
  - User-facing transition effect definitions.
- `LoopTransitionMode.kt`
  - Snap versus smooth loop behavior definitions.

## Sensor and Power Behavior

The live wallpaper registers sensors only while an engine is visible and unregisters immediately when hidden or destroyed. This is important for Android 16 background service and battery behavior.

The engine uses:

- `SensorManager.SENSOR_DELAY_GAME`
- Rotation vector sensor when available
- Game rotation vector / orientation / gyroscope fallbacks
- Low-pass smoothing for hand tremor reduction
- A `Choreographer` frame callback while visible

## Image Handling

Selected images are persisted with photo picker URI permissions. Bitmaps are decoded off the main thread and downsampled for the target surface or preview size to reduce memory pressure.

Each image can store:

- Scale
- Horizontal offset
- Vertical offset

These transforms are shared by the configuration preview and the live wallpaper renderer.

## Notes

- Home screen and lock screen may create separate `WallpaperService.Engine` instances. The service treats each engine lifecycle independently while sharing immutable preference state.
- The renderer avoids per-frame allocation in sensor and draw paths where practical.
- The configuration UI intentionally avoids loading duplicate bitmaps during the theme reveal animation.
