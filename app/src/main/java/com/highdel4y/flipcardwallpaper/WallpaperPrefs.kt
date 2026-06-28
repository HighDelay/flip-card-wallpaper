package com.highdel4y.flipcardwallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

object WallpaperPrefs {
    private const val PREFS_NAME = "flip_card_wallpaper"
    private const val KEY_IMAGE_URIS = "image_uris"
    private const val KEY_IMAGE_A_URI = "image_a_uri"
    private const val KEY_IMAGE_B_URI = "image_b_uri"
    private const val KEY_IMAGE_TRANSFORMS = "image_transforms"
    private const val KEY_TRANSITION_EFFECT = "transition_effect"
    private const val KEY_LOOP_ENABLED = "loop_enabled"
    private const val KEY_LOOP_TRANSITION_MODE = "loop_transition_mode"
    private const val KEY_TRANSITION_SPEED = "transition_speed"
    private const val KEY_TILT_THRESHOLD_DEGREES = "tilt_threshold_degrees"
    private const val KEY_TILT_SENSITIVITY = "tilt_sensitivity"
    private const val KEY_TILT_START_SIDE = "tilt_start_side"
    private const val KEY_TILT_STEP_DEGREES = "tilt_step_degrees"
    private const val KEY_FLAT_SURFACE_GUARD_ENABLED = "flat_surface_guard_enabled"
    private const val KEY_DARK_THEME_ENABLED = "dark_theme_enabled"

    const val MIN_TRANSITION_SPEED = 0.5f
    const val MAX_TRANSITION_SPEED = 2f
    const val DEFAULT_TRANSITION_SPEED = 1f
    const val MIN_TILT_THRESHOLD_DEGREES = 1f
    const val MAX_TILT_THRESHOLD_DEGREES = 15f
    const val DEFAULT_TILT_THRESHOLD_DEGREES = 4f
    const val MIN_TILT_SENSITIVITY = 0.5f
    const val MAX_TILT_SENSITIVITY = 2f
    const val DEFAULT_TILT_SENSITIVITY = 1f
    const val MIN_TILT_STEP_DEGREES = 2f
    const val MAX_TILT_STEP_DEGREES = 15f
    const val DEFAULT_TILT_STEP_DEGREES = 8f
    const val DEFAULT_FLAT_SURFACE_GUARD_ENABLED = false

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun imageAUri(context: Context): Uri? =
        prefs(context).getString(KEY_IMAGE_A_URI, null)?.toUri()

    fun imageBUri(context: Context): Uri? =
        prefs(context).getString(KEY_IMAGE_B_URI, null)?.toUri()

    fun imageUris(context: Context): List<Uri> =
        imageUriStrings(context).map { it.toUri() }

    fun imageUriStrings(context: Context): List<String> {
        val stored = prefs(context).getString(KEY_IMAGE_URIS, null)
        if (!stored.isNullOrBlank()) {
            return runCatching {
                val array = JSONArray(stored)
                buildList {
                    for (index in 0 until array.length()) {
                        val value = array.optString(index)
                        if (value.isNotBlank()) add(value)
                    }
                }
            }.getOrDefault(emptyList())
        }

        return listOfNotNull(
            imageAUriString(context),
            imageBUriString(context),
        ).distinct()
    }

    fun imageAUriString(context: Context): String? =
        prefs(context).getString(KEY_IMAGE_A_URI, null)

    fun imageBUriString(context: Context): String? =
        prefs(context).getString(KEY_IMAGE_B_URI, null)

    fun saveImageA(context: Context, uri: Uri) {
        prefs(context).edit {
            putString(KEY_IMAGE_A_URI, uri.toString())
        }
    }

    fun saveImageB(context: Context, uri: Uri) {
        prefs(context).edit {
            putString(KEY_IMAGE_B_URI, uri.toString())
        }
    }

    fun saveImageUris(context: Context, uris: List<Uri>) {
        val uriStrings = uris.map { it.toString() }.distinct()
        val array = JSONArray()
        uriStrings.forEach(array::put)
        prefs(context).edit {
            putString(KEY_IMAGE_URIS, array.toString())
            putString(KEY_IMAGE_A_URI, uriStrings.getOrNull(0))
            putString(KEY_IMAGE_B_URI, uriStrings.getOrNull(1))
        }
    }

    fun imageTransforms(context: Context, uriStrings: List<String> = imageUriStrings(context)): List<ImageTransform> {
        val stored = prefs(context).getString(KEY_IMAGE_TRANSFORMS, null)
        val transforms = stored?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }
        return uriStrings.map { uriString ->
            transforms?.optJSONObject(uriString)?.toImageTransform() ?: ImageTransform.Centered
        }
    }

    fun saveImageTransforms(context: Context, uriStrings: List<String>, transforms: List<ImageTransform>) {
        val json = JSONObject()
        uriStrings.forEachIndexed { index, uriString ->
            val transform = transforms.getOrNull(index)?.sanitized() ?: ImageTransform.Centered
            json.put(uriString, transform.toJson())
        }
        prefs(context).edit {
            putString(KEY_IMAGE_TRANSFORMS, json.toString())
        }
    }

    fun transitionEffect(context: Context): TransitionEffect =
        TransitionEffect.fromStoredName(prefs(context).getString(KEY_TRANSITION_EFFECT, null))

    fun saveTransitionEffect(context: Context, effect: TransitionEffect) {
        prefs(context).edit {
            putString(KEY_TRANSITION_EFFECT, effect.name)
        }
    }

    fun loopEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOOP_ENABLED, false)

    fun saveLoopEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_LOOP_ENABLED, enabled)
        }
    }

    fun loopTransitionMode(context: Context): LoopTransitionMode =
        LoopTransitionMode.fromStoredName(prefs(context).getString(KEY_LOOP_TRANSITION_MODE, null))

    fun saveLoopTransitionMode(context: Context, mode: LoopTransitionMode) {
        prefs(context).edit {
            putString(KEY_LOOP_TRANSITION_MODE, mode.name)
        }
    }

    fun transitionSpeed(context: Context): Float =
        prefs(context)
            .getFloat(KEY_TRANSITION_SPEED, DEFAULT_TRANSITION_SPEED)
            .coerceIn(MIN_TRANSITION_SPEED, MAX_TRANSITION_SPEED)

    fun saveTransitionSpeed(context: Context, speed: Float) {
        prefs(context).edit {
            putFloat(KEY_TRANSITION_SPEED, speed.coerceIn(MIN_TRANSITION_SPEED, MAX_TRANSITION_SPEED))
        }
    }

    fun tiltThresholdDegrees(context: Context): Float =
        prefs(context)
            .getFloat(KEY_TILT_THRESHOLD_DEGREES, DEFAULT_TILT_THRESHOLD_DEGREES)
            .coerceIn(MIN_TILT_THRESHOLD_DEGREES, MAX_TILT_THRESHOLD_DEGREES)

    fun saveTiltThresholdDegrees(context: Context, degrees: Float) {
        prefs(context).edit {
            putFloat(
                KEY_TILT_THRESHOLD_DEGREES,
                degrees.coerceIn(MIN_TILT_THRESHOLD_DEGREES, MAX_TILT_THRESHOLD_DEGREES),
            )
        }
    }

    fun tiltSensitivity(context: Context): Float =
        prefs(context)
            .getFloat(KEY_TILT_SENSITIVITY, DEFAULT_TILT_SENSITIVITY)
            .coerceIn(MIN_TILT_SENSITIVITY, MAX_TILT_SENSITIVITY)

    fun saveTiltSensitivity(context: Context, sensitivity: Float) {
        prefs(context).edit {
            putFloat(KEY_TILT_SENSITIVITY, sensitivity.coerceIn(MIN_TILT_SENSITIVITY, MAX_TILT_SENSITIVITY))
        }
    }

    fun tiltStartSide(context: Context): TiltStartSide =
        TiltStartSide.fromStoredName(prefs(context).getString(KEY_TILT_START_SIDE, null))

    fun saveTiltStartSide(context: Context, side: TiltStartSide) {
        prefs(context).edit {
            putString(KEY_TILT_START_SIDE, side.name)
        }
    }

    fun tiltStepDegrees(context: Context): Float =
        prefs(context)
            .getFloat(KEY_TILT_STEP_DEGREES, DEFAULT_TILT_STEP_DEGREES)
            .coerceIn(MIN_TILT_STEP_DEGREES, MAX_TILT_STEP_DEGREES)

    fun saveTiltStepDegrees(context: Context, degrees: Float) {
        prefs(context).edit {
            putFloat(KEY_TILT_STEP_DEGREES, degrees.coerceIn(MIN_TILT_STEP_DEGREES, MAX_TILT_STEP_DEGREES))
        }
    }

    fun flatSurfaceGuardEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLAT_SURFACE_GUARD_ENABLED, DEFAULT_FLAT_SURFACE_GUARD_ENABLED)

    fun saveFlatSurfaceGuardEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_FLAT_SURFACE_GUARD_ENABLED, enabled)
        }
    }

    fun darkThemeOverride(context: Context): Boolean? {
        val preferences = prefs(context)
        return if (preferences.contains(KEY_DARK_THEME_ENABLED)) {
            preferences.getBoolean(KEY_DARK_THEME_ENABLED, false)
        } else {
            null
        }
    }

    fun saveDarkTheme(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_DARK_THEME_ENABLED, enabled)
        }
    }

    private fun JSONObject.toImageTransform(): ImageTransform =
        ImageTransform(
            scale = optDouble("scale", ImageTransform.MIN_SCALE.toDouble()).toFloat(),
            offsetX = optDouble("offsetX", 0.0).toFloat(),
            offsetY = optDouble("offsetY", 0.0).toFloat(),
        ).sanitized()

    private fun ImageTransform.toJson(): JSONObject =
        JSONObject()
            .put("scale", scale.coerceIn(ImageTransform.MIN_SCALE, ImageTransform.MAX_SCALE).toDouble())
            .put("offsetX", offsetX.coerceIn(-1f, 1f).toDouble())
            .put("offsetY", offsetY.coerceIn(-1f, 1f).toDouble())
}
