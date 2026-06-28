# Project Context: Lenticular Live Wallpaper (Android 16)

## Overview
A high-performance Android Live Wallpaper app targeting Android 16 (API 36) designed for Google Pixel devices. It replicates Vivo's lenticular "Flip Cards" feature, smoothly cross-fading between two images based on the physical tilt of the phone via real-time sensor fusion.

## Tech Stack & Targets
- **Language:** Kotlin
- **Minimum SDK:** 31 (Android 12)
- **Target SDK:** 36 (Android 16)
- **UI (Config App):** Jetpack Compose
- **Rendering Pipeline:** Hardware-accelerated Canvas / RenderNode or OpenGL ES 2.0+

---

## Technical Constraints & Guardrails

### 1. Memory & Bitmap Management (Pixel Optimization)
- High-res wallpapers can easily cause `OutOfMemoryError` spikes when blending.
- **Rule:** All user-selected images must be downsampled safely relative to the actual hardware display metrics (`WindowMetrics`) before being cached as Bitmaps.
- **Rule:** Recycle old bitmaps instantly when new ones are loaded in the configuration UI.

### 2. Sensor Framework & Frame Pacing
- Do not use `SENSOR_DELAY_NORMAL` (causes major stuttering on 90Hz/120Hz panels).
- **Rule:** Register the rotation sensor using `SENSOR_DELAY_GAME`.
- **Rule:** Implement a Linear Interpolation (LERP) or Low-Pass filter on raw sensor events to smooth out micro-tremors from the user's hand before passing values to the alpha blend calculation.
- **Formula Target:**
  `CurrentAlpha = CurrentAlpha + SmoothFactor * (TargetAlpha - CurrentAlpha)`

### 3. Strict Android 16 Background Rules
- Android 16 aggressively monitors and terminates background services running long-standing sensor listeners.
- **Rule:** The `SensorEventListener` **must** be unregistered the exact millisecond `onVisibilityChanged(false)` is invoked.
- **Rule:** Do not allocate any memory (objects, paints, vectors) inside the sensor callback or the Canvas draw loop (`onDraw`). Pre-allocate everything during engine initialization or size changes (`onSurfaceChanged`).

---

## Architecture Layout

### Core Engine (`LenticularWallpaperService.kt`)
- Extends `WallpaperService`.
- Instantiates a custom `Engine` implementation.
- Manages `SensorManager` lifecycle inside visibility lifecycle overrides.

### Configuration (`ConfigActivity.kt`)
- Jetpack Compose interface allowing the user to select Image A (Default state) and Image B (Tilted state).
- Persists image URIs safely using `Intent.FLAG_GRANT_READ_URI_PERMISSION` and stores access via `SharedPreferences` or encrypted DataStore.

---

## Verification Checklist for Agents
- [ ] Does the wallpaper completely stop polling sensors when the user locks the screen or opens an application?
- [ ] Is rendering offloaded to a hardware-accelerated pipeline to guarantee a smooth 120Hz transition?
- [ ] Are all permissions and feature flags (`android.software.live_wallpaper`) accurately declared in the Manifest?
- [ ] Does the alpha blending logic range flawlessly from 0 (fully Image A) to 255 (fully Image B) without clipping bugs?