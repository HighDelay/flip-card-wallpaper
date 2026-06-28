package com.highdel4y.flipcardwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.highdel4y.flipcardwallpaper.ui.theme.FlipCardTheme
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        window.makeSystemBarsTransparent()
        setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val systemDarkTheme = isSystemInDarkTheme()
            var darkThemeOverride by remember(appContext) {
                mutableStateOf(WallpaperPrefs.darkThemeOverride(appContext))
            }
            val requestedDarkTheme = darkThemeOverride ?: systemDarkTheme
            var darkTheme by remember { mutableStateOf(requestedDarkTheme) }
            var themeRevealSpec by remember { mutableStateOf<ThemeRevealSpec?>(null) }
            var themeRevealSequence by remember { mutableStateOf(0) }

            LaunchedEffect(requestedDarkTheme, themeRevealSpec) {
                if (themeRevealSpec == null) {
                    darkTheme = requestedDarkTheme
                }
            }

            SideEffect {
                window.makeSystemBarsTransparent(darkTheme = darkTheme)
            }

            FlipCardTheme(darkTheme = darkTheme) {
                val stateHolder = rememberFlipCardStateHolder()
                FlipCardScreen(
                    state = stateHolder.state,
                    darkTheme = darkTheme,
                    onImagesAdded = stateHolder::onImagesAdded,
                    onImageRemoved = stateHolder::onImageRemoved,
                    onImageTransformChanged = stateHolder::onImageTransformChanged,
                    onTransitionSelected = stateHolder::onTransitionSelected,
                    onLoopChanged = stateHolder::onLoopChanged,
                    onLoopTransitionModeChanged = stateHolder::onLoopTransitionModeChanged,
                    onTransitionSpeedChanged = stateHolder::onTransitionSpeedChanged,
                    onTiltThresholdChanged = stateHolder::onTiltThresholdChanged,
                    onTiltSensitivityChanged = stateHolder::onTiltSensitivityChanged,
                    onTiltStartSideChanged = stateHolder::onTiltStartSideChanged,
                    onTiltStepDegreesChanged = stateHolder::onTiltStepDegreesChanged,
                    onMotionDefaults = stateHolder::onMotionDefaults,
                    onPreviewProgressChange = stateHolder::onPreviewProgressChange,
                    themeRevealSpec = themeRevealSpec,
                    onThemeRevealCovered = { darkTheme = it },
                    onThemeRevealFinished = { themeRevealSpec = null },
                    onDarkThemeChanged = { enabled, origin ->
                        themeRevealSequence += 1
                        themeRevealSpec = ThemeRevealSpec(
                            id = themeRevealSequence,
                            targetDarkTheme = enabled,
                            origin = origin,
                        )
                        WallpaperPrefs.saveDarkTheme(appContext, enabled)
                        darkThemeOverride = enabled
                    },
                    onApplyWallpaper = {
                        if (stateHolder.state.canApplyWallpaper) {
                            launchWallpaperPicker()
                        }
                    },
                )
            }
        }
    }

    private fun launchWallpaperPicker() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, LenticularWallpaperService::class.java),
            )
        }
        startActivity(intent)
    }
}

private fun Window.makeSystemBarsTransparent(darkTheme: Boolean? = null) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    statusBarColor = AndroidColor.TRANSPARENT
    navigationBarColor = AndroidColor.TRANSPARENT
    darkTheme?.let { enabled ->
        val controller = WindowCompat.getInsetsController(this, decorView)
        controller.isAppearanceLightStatusBars = !enabled
        controller.isAppearanceLightNavigationBars = !enabled
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isStatusBarContrastEnforced = false
        isNavigationBarContrastEnforced = false
    }
}

private data class ThemeRevealSpec(
    val id: Int,
    val targetDarkTheme: Boolean,
    val origin: Offset,
)

@Immutable
data class FlipCardUiState(
    val imageUris: List<Uri>,
    val imageTransforms: List<ImageTransform>,
    val transitionEffect: TransitionEffect,
    val loopEnabled: Boolean,
    val loopTransitionMode: LoopTransitionMode,
    val transitionSpeed: Float,
    val tiltThresholdDegrees: Float,
    val tiltSensitivity: Float,
    val tiltStartSide: TiltStartSide,
    val tiltStepDegrees: Float,
    val previewProgress: Float,
) {
    val canApplyWallpaper: Boolean
        get() = imageUris.size >= 2
}

@Stable
class FlipCardStateHolder(context: Context) {
    private val appContext = context.applicationContext
    private val initialUriStrings = WallpaperPrefs.imageUriStrings(appContext)

    var state by mutableStateOf(
        FlipCardUiState(
            imageUris = initialUriStrings.map(Uri::parse),
            imageTransforms = WallpaperPrefs.imageTransforms(appContext, initialUriStrings),
            transitionEffect = WallpaperPrefs.transitionEffect(appContext),
            loopEnabled = WallpaperPrefs.loopEnabled(appContext),
            loopTransitionMode = WallpaperPrefs.loopTransitionMode(appContext),
            transitionSpeed = WallpaperPrefs.transitionSpeed(appContext),
            tiltThresholdDegrees = WallpaperPrefs.tiltThresholdDegrees(appContext),
            tiltSensitivity = WallpaperPrefs.tiltSensitivity(appContext),
            tiltStartSide = WallpaperPrefs.tiltStartSide(appContext),
            tiltStepDegrees = WallpaperPrefs.tiltStepDegrees(appContext),
            previewProgress = 0f,
        ),
    )
        private set

    fun onImagesAdded(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach(appContext::persistPhotoPickerReadGrant)
        val nextUris = (state.imageUris + uris).distinctBy { it.toString() }
        val existingTransforms = state.imageUris.mapIndexed { index, uri ->
            uri.toString() to (state.imageTransforms.getOrNull(index) ?: ImageTransform.Centered)
        }.toMap()
        val nextTransforms = nextUris.map { uri ->
            existingTransforms[uri.toString()] ?: ImageTransform.Centered
        }
        WallpaperPrefs.saveImageUris(appContext, nextUris)
        WallpaperPrefs.saveImageTransforms(appContext, nextUris.map { it.toString() }, nextTransforms)
        state = state.copy(imageUris = nextUris, imageTransforms = nextTransforms)
    }

    fun onImageRemoved(index: Int) {
        if (index !in state.imageUris.indices) return
        val nextUris = state.imageUris.filterIndexed { currentIndex, _ -> currentIndex != index }
        val nextTransforms = state.imageTransforms.filterIndexed { currentIndex, _ -> currentIndex != index }
        WallpaperPrefs.saveImageUris(appContext, nextUris)
        WallpaperPrefs.saveImageTransforms(appContext, nextUris.map { it.toString() }, nextTransforms)
        state = state.copy(
            imageUris = nextUris,
            imageTransforms = nextTransforms,
            previewProgress = state.previewProgress.coerceIn(0f, 1f),
        )
    }

    fun onImageTransformChanged(index: Int, transform: ImageTransform) {
        if (index !in state.imageUris.indices) return
        val nextTransforms = state.imageUris.mapIndexed { currentIndex, _ ->
            if (currentIndex == index) {
                transform.sanitized()
            } else {
                state.imageTransforms.getOrNull(currentIndex) ?: ImageTransform.Centered
            }
        }
        WallpaperPrefs.saveImageTransforms(appContext, state.imageUris.map { it.toString() }, nextTransforms)
        state = state.copy(imageTransforms = nextTransforms)
    }

    fun onTransitionSelected(effect: TransitionEffect) {
        WallpaperPrefs.saveTransitionEffect(appContext, effect)
        state = state.copy(transitionEffect = effect)
    }

    fun onLoopChanged(enabled: Boolean) {
        WallpaperPrefs.saveLoopEnabled(appContext, enabled)
        state = state.copy(loopEnabled = enabled)
    }

    fun onLoopTransitionModeChanged(mode: LoopTransitionMode) {
        WallpaperPrefs.saveLoopTransitionMode(appContext, mode)
        state = state.copy(loopTransitionMode = mode)
    }

    fun onTransitionSpeedChanged(speed: Float) {
        val sanitized = speed.coerceIn(WallpaperPrefs.MIN_TRANSITION_SPEED, WallpaperPrefs.MAX_TRANSITION_SPEED)
        WallpaperPrefs.saveTransitionSpeed(appContext, sanitized)
        state = state.copy(transitionSpeed = sanitized)
    }

    fun onTiltThresholdChanged(degrees: Float) {
        val sanitized = degrees.coerceIn(
            WallpaperPrefs.MIN_TILT_THRESHOLD_DEGREES,
            WallpaperPrefs.MAX_TILT_THRESHOLD_DEGREES,
        )
        WallpaperPrefs.saveTiltThresholdDegrees(appContext, sanitized)
        state = state.copy(tiltThresholdDegrees = sanitized)
    }

    fun onTiltSensitivityChanged(sensitivity: Float) {
        val sanitized = sensitivity.coerceIn(WallpaperPrefs.MIN_TILT_SENSITIVITY, WallpaperPrefs.MAX_TILT_SENSITIVITY)
        WallpaperPrefs.saveTiltSensitivity(appContext, sanitized)
        state = state.copy(tiltSensitivity = sanitized)
    }

    fun onTiltStartSideChanged(side: TiltStartSide) {
        WallpaperPrefs.saveTiltStartSide(appContext, side)
        state = state.copy(tiltStartSide = side)
    }

    fun onTiltStepDegreesChanged(degrees: Float) {
        val sanitized = degrees.coerceIn(WallpaperPrefs.MIN_TILT_STEP_DEGREES, WallpaperPrefs.MAX_TILT_STEP_DEGREES)
        WallpaperPrefs.saveTiltStepDegrees(appContext, sanitized)
        state = state.copy(tiltStepDegrees = sanitized)
    }

    fun onMotionDefaults() {
        WallpaperPrefs.saveTransitionSpeed(appContext, WallpaperPrefs.DEFAULT_TRANSITION_SPEED)
        WallpaperPrefs.saveTiltThresholdDegrees(appContext, WallpaperPrefs.DEFAULT_TILT_THRESHOLD_DEGREES)
        WallpaperPrefs.saveTiltSensitivity(appContext, WallpaperPrefs.DEFAULT_TILT_SENSITIVITY)
        WallpaperPrefs.saveTiltStartSide(appContext, TiltStartSide.Right)
        WallpaperPrefs.saveTiltStepDegrees(appContext, WallpaperPrefs.DEFAULT_TILT_STEP_DEGREES)
        WallpaperPrefs.saveLoopTransitionMode(appContext, LoopTransitionMode.Snap)
        state = state.copy(
            transitionSpeed = WallpaperPrefs.DEFAULT_TRANSITION_SPEED,
            tiltThresholdDegrees = WallpaperPrefs.DEFAULT_TILT_THRESHOLD_DEGREES,
            tiltSensitivity = WallpaperPrefs.DEFAULT_TILT_SENSITIVITY,
            tiltStartSide = TiltStartSide.Right,
            tiltStepDegrees = WallpaperPrefs.DEFAULT_TILT_STEP_DEGREES,
            loopTransitionMode = LoopTransitionMode.Snap,
        )
    }

    fun onPreviewProgressChange(value: Float) {
        state = state.copy(previewProgress = value.coerceIn(0f, 1f))
    }
}

@Composable
private fun rememberFlipCardStateHolder(): FlipCardStateHolder {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        FlipCardStateHolder(context.applicationContext)
    }
}

@Composable
private fun FlipCardScreen(
    state: FlipCardUiState,
    darkTheme: Boolean,
    onImagesAdded: (List<Uri>) -> Unit,
    onImageRemoved: (Int) -> Unit,
    onImageTransformChanged: (Int, ImageTransform) -> Unit,
    onTransitionSelected: (TransitionEffect) -> Unit,
    onLoopChanged: (Boolean) -> Unit,
    onLoopTransitionModeChanged: (LoopTransitionMode) -> Unit,
    onTransitionSpeedChanged: (Float) -> Unit,
    onTiltThresholdChanged: (Float) -> Unit,
    onTiltSensitivityChanged: (Float) -> Unit,
    onTiltStartSideChanged: (TiltStartSide) -> Unit,
    onTiltStepDegreesChanged: (Float) -> Unit,
    onMotionDefaults: () -> Unit,
    onPreviewProgressChange: (Float) -> Unit,
    themeRevealSpec: ThemeRevealSpec?,
    onThemeRevealCovered: (Boolean) -> Unit,
    onThemeRevealFinished: () -> Unit,
    onDarkThemeChanged: (Boolean, Offset) -> Unit,
    onApplyWallpaper: () -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) {
        onImagesAdded(it)
    }
    val launchImagePicker = {
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FlipCardScreenContent(
            state = state,
            darkTheme = darkTheme,
            themeChangeEnabled = themeRevealSpec == null,
            renderImages = true,
            onAddPhotos = launchImagePicker,
            onImageRemoved = onImageRemoved,
            onImageTransformChanged = onImageTransformChanged,
            onTransitionSelected = onTransitionSelected,
            onLoopChanged = onLoopChanged,
            onLoopTransitionModeChanged = onLoopTransitionModeChanged,
            onTransitionSpeedChanged = onTransitionSpeedChanged,
            onTiltThresholdChanged = onTiltThresholdChanged,
            onTiltSensitivityChanged = onTiltSensitivityChanged,
            onTiltStartSideChanged = onTiltStartSideChanged,
            onTiltStepDegreesChanged = onTiltStepDegreesChanged,
            onMotionDefaults = onMotionDefaults,
            onPreviewProgressChange = onPreviewProgressChange,
            onDarkThemeChanged = onDarkThemeChanged,
            onApplyWallpaper = onApplyWallpaper,
        )
        ThemeRevealOverlay(
            spec = themeRevealSpec,
            onCovered = onThemeRevealCovered,
            onFinished = onThemeRevealFinished,
        ) { revealSpec ->
            FlipCardTheme(darkTheme = revealSpec.targetDarkTheme) {
                FlipCardScreenContent(
                    state = state,
                    darkTheme = revealSpec.targetDarkTheme,
                    themeChangeEnabled = false,
                    renderImages = false,
                    onAddPhotos = {},
                    onImageRemoved = {},
                    onImageTransformChanged = { _, _ -> },
                    onTransitionSelected = {},
                    onLoopChanged = {},
                    onLoopTransitionModeChanged = {},
                    onTransitionSpeedChanged = {},
                    onTiltThresholdChanged = {},
                    onTiltSensitivityChanged = {},
                    onTiltStartSideChanged = {},
                    onTiltStepDegreesChanged = {},
                    onMotionDefaults = {},
                    onPreviewProgressChange = {},
                    onDarkThemeChanged = { _, _ -> },
                    onApplyWallpaper = {},
                )
            }
        }
    }
}

@Composable
private fun FlipCardScreenContent(
    state: FlipCardUiState,
    darkTheme: Boolean,
    themeChangeEnabled: Boolean,
    renderImages: Boolean,
    onAddPhotos: () -> Unit,
    onImageRemoved: (Int) -> Unit,
    onImageTransformChanged: (Int, ImageTransform) -> Unit,
    onTransitionSelected: (TransitionEffect) -> Unit,
    onLoopChanged: (Boolean) -> Unit,
    onLoopTransitionModeChanged: (LoopTransitionMode) -> Unit,
    onTransitionSpeedChanged: (Float) -> Unit,
    onTiltThresholdChanged: (Float) -> Unit,
    onTiltSensitivityChanged: (Float) -> Unit,
    onTiltStartSideChanged: (TiltStartSide) -> Unit,
    onTiltStepDegreesChanged: (Float) -> Unit,
    onMotionDefaults: () -> Unit,
    onPreviewProgressChange: (Float) -> Unit,
    onDarkThemeChanged: (Boolean, Offset) -> Unit,
    onApplyWallpaper: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onApplyWallpaper,
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(if (state.canApplyWallpaper) "Set live wallpaper" else "Add at least 2 photos")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(
                countLabel = "${state.imageUris.size} photos",
                darkTheme = darkTheme,
                themeChangeEnabled = themeChangeEnabled,
                onDarkThemeChanged = onDarkThemeChanged,
            )
            LenticularPreviewCard(
                imageUris = state.imageUris,
                imageTransforms = state.imageTransforms,
                transitionEffect = state.transitionEffect,
                loopEnabled = state.loopEnabled,
                previewProgress = state.previewProgress,
                renderImages = renderImages,
                onPreviewProgressChange = onPreviewProgressChange,
            )
            TransitionSelector(
                selected = state.transitionEffect,
                loopEnabled = state.loopEnabled,
                loopTransitionMode = state.loopTransitionMode,
                transitionSpeed = state.transitionSpeed,
                tiltThresholdDegrees = state.tiltThresholdDegrees,
                tiltSensitivity = state.tiltSensitivity,
                tiltStartSide = state.tiltStartSide,
                tiltStepDegrees = state.tiltStepDegrees,
                onSelected = onTransitionSelected,
                onLoopChanged = onLoopChanged,
                onLoopTransitionModeChanged = onLoopTransitionModeChanged,
                onTransitionSpeedChanged = onTransitionSpeedChanged,
                onTiltThresholdChanged = onTiltThresholdChanged,
                onTiltSensitivityChanged = onTiltSensitivityChanged,
                onTiltStartSideChanged = onTiltStartSideChanged,
                onTiltStepDegreesChanged = onTiltStepDegreesChanged,
                onMotionDefaults = onMotionDefaults,
            )
            WallpaperSequenceEditor(
                imageUris = state.imageUris,
                imageTransforms = state.imageTransforms,
                renderImages = renderImages,
                onAddPhotos = onAddPhotos,
                onImageRemoved = onImageRemoved,
                onImageTransformChanged = onImageTransformChanged,
            )
        }
    }
}

@Composable
private fun Header(
    countLabel: String,
    darkTheme: Boolean,
    themeChangeEnabled: Boolean,
    onDarkThemeChanged: (Boolean, Offset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Flip Cards",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThemeModeButton(
            darkTheme = darkTheme,
            enabled = themeChangeEnabled,
            onDarkThemeChanged = onDarkThemeChanged,
        )
    }
}

@Composable
private fun ThemeModeButton(
    darkTheme: Boolean,
    enabled: Boolean,
    onDarkThemeChanged: (Boolean, Offset) -> Unit,
) {
    var centerInRoot by remember { mutableStateOf(Offset.Zero) }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface
    val label = if (darkTheme) "Switch to light mode" else "Switch to dark mode"

    Surface(
        modifier = Modifier
            .size(52.dp)
            .onGloballyPositioned { coordinates ->
                val rootPosition = coordinates.positionInRoot()
                centerInRoot = Offset(
                    x = rootPosition.x + coordinates.size.width / 2f,
                    y = rootPosition.y + coordinates.size.height / 2f,
                )
            }
            .semantics { contentDescription = label }
            .clickable(enabled = enabled) {
                onDarkThemeChanged(!darkTheme, centerInRoot)
            },
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ThemeModeGlyph(
                showSun = darkTheme,
                color = contentColor.copy(alpha = if (enabled) 1f else 0.46f),
                cutoutColor = containerColor,
            )
        }
    }
}

@Composable
private fun ThemeModeGlyph(
    showSun: Boolean,
    color: Color,
    cutoutColor: Color,
) {
    Canvas(modifier = Modifier.size(24.dp)) {
        if (showSun) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rayInner = size.minDimension * 0.36f
            val rayOuter = size.minDimension * 0.48f
            drawCircle(color = color, radius = size.minDimension * 0.19f, center = center)
            repeat(8) { index ->
                val angle = (Math.PI * 2.0 * index / 8.0).toFloat()
                drawLine(
                    color = color,
                    start = Offset(
                        x = center.x + kotlin.math.cos(angle) * rayInner,
                        y = center.y + kotlin.math.sin(angle) * rayInner,
                    ),
                    end = Offset(
                        x = center.x + kotlin.math.cos(angle) * rayOuter,
                        y = center.y + kotlin.math.sin(angle) * rayOuter,
                    ),
                    strokeWidth = size.minDimension * 0.08f,
                    cap = StrokeCap.Round,
                )
            }
        } else {
            val radius = size.minDimension * 0.34f
            val center = Offset(size.width * 0.48f, size.height * 0.50f)
            drawCircle(color = color, radius = radius, center = center)
            drawCircle(
                color = cutoutColor,
                radius = radius * 0.88f,
                center = Offset(size.width * 0.62f, size.height * 0.38f),
            )
        }
    }
}

@Composable
private fun ThemeRevealOverlay(
    spec: ThemeRevealSpec?,
    onCovered: (Boolean) -> Unit,
    onFinished: () -> Unit,
    targetContent: @Composable (ThemeRevealSpec) -> Unit,
) {
    if (spec == null) return

    val radiusProgress = remember(spec.id) { Animatable(0f) }

    LaunchedEffect(spec.id) {
        radiusProgress.snapTo(0f)
        radiusProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        )
        onCovered(spec.targetDarkTheme)
        withFrameNanos { }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(spec.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .drawWithContent {
                val origin = if (spec.origin == Offset.Zero) {
                    Offset(size.width - 48.dp.toPx(), 48.dp.toPx())
                } else {
                    spec.origin
                }
                val farX = maxOf(origin.x, size.width - origin.x)
                val farY = maxOf(origin.y, size.height - origin.y)
                val maxRadius = hypot(farX, farY)
                val radius = maxRadius * radiusProgress.value
                if (radius > 0f) {
                    val path = Path().apply {
                        addOval(
                            Rect(
                                left = origin.x - radius,
                                top = origin.y - radius,
                                right = origin.x + radius,
                                bottom = origin.y + radius,
                            ),
                        )
                    }
                    clipPath(path) {
                        this@drawWithContent.drawContent()
                    }
                }
            },
    ) {
        targetContent(spec)
    }
}

@Composable
private fun LenticularPreviewCard(
    imageUris: List<Uri>,
    imageTransforms: List<ImageTransform>,
    transitionEffect: TransitionEffect,
    loopEnabled: Boolean,
    previewProgress: Float,
    renderImages: Boolean,
    onPreviewProgressChange: (Float) -> Unit,
) {
    val position = previewProgress.coerceIn(0f, 1f) * maxPreviewPosition(imageUris.size)
    val frame = previewFrameForPosition(position, imageUris.size, loopEnabled)
    val fromIndex = frame.fromIndex
    val toIndex = frame.toIndex
    val localFraction = frame.fraction
    val imageA = if (renderImages) rememberSampledBitmap(imageUris.getOrNull(fromIndex), 1080, 1440) else null
    val imageB = if (renderImages) rememberSampledBitmap(imageUris.getOrNull(toIndex), 1080, 1440) else null
    val transformA = imageTransforms.getOrNull(fromIndex) ?: ImageTransform.Centered
    val transformB = imageTransforms.getOrNull(toIndex) ?: ImageTransform.Centered
    val animatedFraction by animateFloatAsState(
        targetValue = localFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "previewTransition",
    )
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val previewShape = RoundedCornerShape(topStart = 32.dp, topEnd = 24.dp, bottomEnd = 32.dp, bottomStart = 20.dp)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = previewShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp)
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(28.dp))
                    .then(if (renderImages) Modifier.background(placeholderBrush()) else Modifier)
                    .onSizeChanged { previewSize = it }
                    .pointerInput(previewSize.width, imageUris.size) {
                        fun updateFromX(x: Float) {
                            val width = previewSize.width
                            if (width > 0) {
                                onPreviewProgressChange((x / width.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        detectDragGestures(
                            onDragStart = { updateFromX(it.x) },
                            onDrag = { change, _ ->
                                updateFromX(change.position.x)
                                change.consume()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (renderImages) {
                    TransitionPreviewImages(
                        imageA = imageA,
                        imageB = imageB,
                        transformA = transformA,
                        transformB = transformB,
                        effect = transitionEffect,
                        fraction = animatedFraction,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = "${fromIndex + 1} / ${imageUris.size.coerceAtLeast(1)}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Slider(
                value = previewProgress,
                onValueChange = onPreviewProgressChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TransitionPreviewImages(
    imageA: Bitmap?,
    imageB: Bitmap?,
    transformA: ImageTransform,
    transformB: ImageTransform,
    effect: TransitionEffect,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size = it },
        contentAlignment = Alignment.Center,
    ) {
        when (effect) {
            TransitionEffect.Crossfade -> {
                PreviewImageLayer(imageA, transform = transformA, containerSize = size, alpha = 1f)
                PreviewImageLayer(imageB, transform = transformB, containerSize = size, alpha = fraction)
            }

            TransitionEffect.Slide -> {
                val width = size.width.toFloat()
                PreviewImageLayer(imageA, transform = transformA, containerSize = size, translationX = -width * fraction)
                PreviewImageLayer(imageB, transform = transformB, containerSize = size, translationX = width * (1f - fraction))
            }

            TransitionEffect.SwipeFade -> {
                val width = size.width.toFloat()
                PreviewImageLayer(
                    bitmap = imageA,
                    transform = transformA,
                    containerSize = size,
                    alpha = 1f - 0.55f * fraction,
                    translationX = -width * 0.18f * fraction,
                )
                PreviewImageLayer(
                    bitmap = imageB,
                    transform = transformB,
                    containerSize = size,
                    alpha = fraction,
                    translationX = width * 0.28f * (1f - fraction),
                )
            }

            TransitionEffect.Wipe -> {
                PreviewImageLayer(imageA, transform = transformA, containerSize = size)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                        .clipToBounds(),
                ) {
                    PreviewImageLayer(imageB, transform = transformB, containerSize = size)
                }
            }

            TransitionEffect.ZoomFade -> {
                PreviewImageLayer(
                    bitmap = imageA,
                    transform = transformA,
                    containerSize = size,
                    alpha = 1f - 0.24f * fraction,
                )
                PreviewImageLayer(
                    bitmap = imageB,
                    transform = transformB,
                    containerSize = size,
                    alpha = fraction,
                    scale = 1.08f - 0.08f * fraction,
                )
            }

            TransitionEffect.Depth -> {
                PreviewImageLayer(
                    bitmap = imageA,
                    transform = transformA,
                    containerSize = size,
                    alpha = 1f - 0.42f * fraction,
                    scale = 1f + 0.06f * fraction,
                )
                PreviewImageLayer(
                    bitmap = imageB,
                    transform = transformB,
                    containerSize = size,
                    alpha = fraction,
                    scale = 0.94f + 0.06f * fraction,
                )
            }
        }
    }
}

@Composable
private fun PreviewImageLayer(
    bitmap: Bitmap?,
    transform: ImageTransform = ImageTransform.Centered,
    containerSize: IntSize = IntSize.Zero,
    alpha: Float = 1f,
    translationX: Float = 0f,
    scale: Float = 1f,
) {
    val layerModifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            this.alpha = alpha.coerceIn(0f, 1f)
            this.translationX = translationX
            scaleX = scale
            scaleY = scale
        }

    if (bitmap != null && !bitmap.isRecycled) {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val safeTransform = transform.sanitized()
        val crop = remember(bitmap, safeTransform, containerSize) {
            calculateBitmapCropRect(
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
                targetWidth = containerSize.width,
                targetHeight = containerSize.height,
                transform = safeTransform,
            )
        }
        Canvas(modifier = layerModifier) {
            if (crop.width > 0 && crop.height > 0) {
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(crop.left, crop.top),
                    srcSize = IntSize(crop.width, crop.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(
                        width = size.width.roundToInt().coerceAtLeast(1),
                        height = size.height.roundToInt().coerceAtLeast(1),
                    ),
                    alpha = 1f,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    } else {
        PlaceholderPanel(
            label = "",
            modifier = layerModifier,
        )
    }
}

@Composable
private fun TransitionSelector(
    selected: TransitionEffect,
    loopEnabled: Boolean,
    loopTransitionMode: LoopTransitionMode,
    transitionSpeed: Float,
    tiltThresholdDegrees: Float,
    tiltSensitivity: Float,
    tiltStartSide: TiltStartSide,
    tiltStepDegrees: Float,
    onSelected: (TransitionEffect) -> Unit,
    onLoopChanged: (Boolean) -> Unit,
    onLoopTransitionModeChanged: (LoopTransitionMode) -> Unit,
    onTransitionSpeedChanged: (Float) -> Unit,
    onTiltThresholdChanged: (Float) -> Unit,
    onTiltSensitivityChanged: (Float) -> Unit,
    onTiltStartSideChanged: (TiltStartSide) -> Unit,
    onTiltStepDegreesChanged: (Float) -> Unit,
    onMotionDefaults: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Controls",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Effect",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                TransitionEffect.entries.chunked(2).forEach { rowEffects ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowEffects.forEach { effect ->
                            val selectedEffect = effect == selected
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clickable { onSelected(effect) },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selectedEffect) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (selectedEffect) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = effect.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        repeat(2 - rowEffects.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    text = selected.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Motion",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    FilledTonalButton(onClick = onMotionDefaults, shape = RoundedCornerShape(16.dp)) {
                        Text("Reset")
                    }
                }
                TuningSlider(
                    label = "Speed",
                    valueLabel = "${(transitionSpeed * 100f).roundToInt()}%",
                    value = transitionSpeed,
                    onValueChange = onTransitionSpeedChanged,
                    valueRange = WallpaperPrefs.MIN_TRANSITION_SPEED..WallpaperPrefs.MAX_TRANSITION_SPEED,
                    steps = 5,
                )
                TuningSlider(
                    label = "Trigger",
                    valueLabel = "${tiltThresholdDegrees.roundToInt()} deg",
                    value = tiltThresholdDegrees,
                    onValueChange = onTiltThresholdChanged,
                    valueRange = WallpaperPrefs.MIN_TILT_THRESHOLD_DEGREES..WallpaperPrefs.MAX_TILT_THRESHOLD_DEGREES,
                    steps = 13,
                )
                TuningSlider(
                    label = "Sensitivity",
                    valueLabel = "${(tiltSensitivity * 100f).roundToInt()}%",
                    value = tiltSensitivity,
                    onValueChange = onTiltSensitivityChanged,
                    valueRange = WallpaperPrefs.MIN_TILT_SENSITIVITY..WallpaperPrefs.MAX_TILT_SENSITIVITY,
                    steps = 5,
                )
                if (!loopEnabled || loopTransitionMode == LoopTransitionMode.Snap) {
                    TuningSlider(
                        label = "Step",
                        valueLabel = "${tiltStepDegrees.roundToInt()} deg/photo",
                        value = tiltStepDegrees,
                        onValueChange = onTiltStepDegreesChanged,
                        valueRange = WallpaperPrefs.MIN_TILT_STEP_DEGREES..WallpaperPrefs.MAX_TILT_STEP_DEGREES,
                        steps = 12,
                    )
                }
                if (!loopEnabled) {
                    TiltStartSideSelector(
                        selected = tiltStartSide,
                        onSelected = onTiltStartSideChanged,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Loop",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = if (loopEnabled) {
                                "${loopTransitionMode.label}: ${loopTransitionMode.description}"
                            } else {
                                "${tiltStartSide.label} tilt stops on the last photo."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = loopEnabled,
                        onCheckedChange = onLoopChanged,
                    )
                }
                if (loopEnabled) {
                    LoopTransitionModeSelector(
                        selected = loopTransitionMode,
                        onSelected = onLoopTransitionModeChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoopTransitionModeSelector(
    selected: LoopTransitionMode,
    onSelected: (LoopTransitionMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Loop mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoopTransitionMode.entries.forEach { mode ->
                val selectedMode = mode == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable { onSelected(mode) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selectedMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (selectedMode) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Text(
            text = selected.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TiltStartSideSelector(
    selected: TiltStartSide,
    onSelected: (TiltStartSide) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Start side",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TiltStartSide.entries.forEach { side ->
                val selectedSide = side == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable { onSelected(side) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selectedSide) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (selectedSide) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = side.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Text(
            text = selected.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TuningSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun WallpaperSequenceEditor(
    imageUris: List<Uri>,
    imageTransforms: List<ImageTransform>,
    renderImages: Boolean,
    onAddPhotos: () -> Unit,
    onImageRemoved: (Int) -> Unit,
    onImageTransformChanged: (Int, ImageTransform) -> Unit,
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val activeEditIndex = editingIndex?.takeIf { it in imageUris.indices }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Wallpapers",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            FilledTonalButton(onClick = onAddPhotos, shape = RoundedCornerShape(20.dp)) {
                Text("Add photos")
            }
        }

        if (imageUris.isEmpty()) {
            EmptyWallpaperCard(onAddPhotos = onAddPhotos)
        } else {
            imageUris.forEachIndexed { index, uri ->
                WallpaperSequenceCard(
                    index = index,
                    uri = uri,
                    transform = imageTransforms.getOrNull(index) ?: ImageTransform.Centered,
                    renderImage = renderImages,
                    onRemove = { onImageRemoved(index) },
                    onEdit = { editingIndex = index },
                )
            }
        }
    }

    activeEditIndex?.let { index ->
        ImageFitDialog(
            index = index,
            uri = imageUris[index],
            transform = imageTransforms.getOrNull(index) ?: ImageTransform.Centered,
            onDone = {
                onImageTransformChanged(index, it)
                editingIndex = null
            },
        )
    }
}

@Composable
private fun EmptyWallpaperCard(onAddPhotos: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddPhotos),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .background(placeholderBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Add photos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun WallpaperSequenceCard(
    index: Int,
    uri: Uri,
    transform: ImageTransform,
    renderImage: Boolean,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    val thumbnail = if (renderImage) rememberSampledBitmap(uri, 360, 720) else null
    val wallpaperAspectRatio = wallpaperAspectRatio()
    var thumbnailSize by remember { mutableStateOf(IntSize.Zero) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(138.dp)
                    .aspectRatio(wallpaperAspectRatio)
                    .clip(RoundedCornerShape(22.dp))
                    .clipToBounds()
                    .then(if (renderImage) Modifier.background(placeholderBrush()) else Modifier)
                    .onSizeChanged { thumbnailSize = it }
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                if (renderImage && thumbnail != null && !thumbnail.isRecycled) {
                    PreviewImageLayer(
                        bitmap = thumbnail,
                        transform = transform,
                        containerSize = thumbnailSize,
                    )
                } else if (renderImage) {
                    PlaceholderPanel(label = "${index + 1}")
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Photo ${index + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (index == 0) "Start" else "Tilt step $index",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${(transform.scale * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onEdit, shape = RoundedCornerShape(20.dp)) {
                        Text("Fit")
                    }
                    FilledTonalButton(onClick = onRemove, shape = RoundedCornerShape(20.dp)) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageFitDialog(
    index: Int,
    uri: Uri,
    transform: ImageTransform,
    onDone: (ImageTransform) -> Unit,
) {
    val bitmap = rememberSampledBitmap(uri, 1080, 2400)
    val wallpaperAspectRatio = wallpaperAspectRatio()
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var workingTransform by remember(uri) { mutableStateOf(transform.sanitized()) }

    LaunchedEffect(uri, transform) {
        workingTransform = transform.sanitized()
    }

    fun commit(next: ImageTransform) {
        workingTransform = next.sanitizedForScale()
    }

    fun finishEditing() {
        onDone(workingTransform.sanitizedForScale())
    }

    Dialog(
        onDismissRequest = ::finishEditing,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Photo ${index + 1}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = if (index == 0) "Start" else "Tilt step $index",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = ::finishEditing, shape = RoundedCornerShape(20.dp)) {
                        Text("Done")
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val availableAspectRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                    val frameModifier = if (availableAspectRatio > wallpaperAspectRatio) {
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(wallpaperAspectRatio)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(wallpaperAspectRatio)
                    }

                    Box(
                        modifier = frameModifier
                            .clip(RoundedCornerShape(32.dp))
                            .clipToBounds()
                            .background(placeholderBrush())
                            .onSizeChanged { editorSize = it }
                            .pointerInput(editorSize, bitmap?.width, bitmap?.height) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    commit(
                                        workingTransform.updatedFromGesture(
                                            panX = pan.x,
                                            panY = pan.y,
                                            zoom = zoom,
                                            containerSize = editorSize,
                                            bitmapWidth = bitmap?.width ?: 0,
                                            bitmapHeight = bitmap?.height ?: 0,
                                        ),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bitmap != null && !bitmap.isRecycled) {
                            PreviewImageLayer(
                                bitmap = bitmap,
                                transform = workingTransform,
                                containerSize = editorSize,
                            )
                        } else {
                            PlaceholderPanel(label = "${index + 1}")
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = "${(workingTransform.scale * 100f).toInt()}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = { commit(workingTransform.withScaleDelta(-0.1f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("-")
                    }
                    FilledTonalButton(
                        onClick = { commit(workingTransform.copy(offsetX = 0f, offsetY = 0f)) },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Center")
                    }
                    FilledTonalButton(
                        onClick = { commit(ImageTransform.Centered) },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Reset")
                    }
                    FilledTonalButton(
                        onClick = { commit(workingTransform.withScaleDelta(0.1f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("+")
                    }
                }
            }
        }
    }
}

@Composable
private fun wallpaperAspectRatio(): Float {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
    val height = configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
    return (width / height).coerceIn(0.42f, 0.75f)
}

private fun ImageTransform.updatedFromGesture(
    panX: Float,
    panY: Float,
    zoom: Float,
    containerSize: IntSize,
    bitmapWidth: Int,
    bitmapHeight: Int,
): ImageTransform {
    val nextScale = (scale * zoom).coerceIn(ImageTransform.MIN_SCALE, ImageTransform.MAX_SCALE)
    val panRangeX = bitmapCropPanRangeX(
        bitmapWidth = bitmapWidth,
        bitmapHeight = bitmapHeight,
        targetWidth = containerSize.width,
        targetHeight = containerSize.height,
        scale = nextScale,
    )
    val panRangeY = bitmapCropPanRangeY(
        bitmapWidth = bitmapWidth,
        bitmapHeight = bitmapHeight,
        targetWidth = containerSize.width,
        targetHeight = containerSize.height,
        scale = nextScale,
    )
    return copy(
        scale = nextScale,
        offsetX = if (panRangeX > 0f) offsetX + panX / panRangeX else 0f,
        offsetY = if (panRangeY > 0f) offsetY + panY / panRangeY else 0f,
    ).sanitized()
}

private fun ImageTransform.withScaleDelta(delta: Float): ImageTransform =
    copy(scale = scale + delta).sanitizedForScale()

private fun ImageTransform.sanitizedForScale(): ImageTransform {
    val boundedScale = scale.coerceIn(ImageTransform.MIN_SCALE, ImageTransform.MAX_SCALE)
    return copy(scale = boundedScale).sanitized()
}

@Composable
private fun PlaceholderPanel(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(placeholderBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun placeholderBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.84f),
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
    ),
)

@Composable
private fun rememberSampledBitmap(uri: Uri?, targetWidth: Int, targetHeight: Int): Bitmap? {
    val context = LocalContext.current.applicationContext
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val latestBitmap by rememberUpdatedState(bitmap)

    DisposableEffect(Unit) {
        onDispose {
            latestBitmap?.recycle()
        }
    }

    LaunchedEffect(uri, targetWidth, targetHeight) {
        val previous = bitmap
        bitmap = null
        previous?.recycle()

        var decoded: Bitmap? = null
        try {
            decoded = uri?.let {
                withContext(Dispatchers.IO) {
                    BitmapLoader.decodeForTarget(
                        context = context,
                        uri = it,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        hardware = false,
                    )
                }
            }
            bitmap = decoded
            decoded = null
        } finally {
            decoded?.recycle()
        }
    }

    return bitmap
}

private data class PreviewFrame(
    val fromIndex: Int,
    val toIndex: Int,
    val fraction: Float,
)

private fun maxPreviewPosition(imageCount: Int): Float =
    (imageCount - 1).coerceAtLeast(0).toFloat()

private fun previewFrameForPosition(position: Float, imageCount: Int, loop: Boolean): PreviewFrame {
    if (imageCount <= 1) {
        return PreviewFrame(0, 0, 0f)
    }

    if (loop) {
        val clampedPosition = position.coerceIn(0f, (imageCount - 1).toFloat())
        val baseIndex = floor(clampedPosition).toInt().coerceIn(0, imageCount - 1)
        val nextIndex = min(baseIndex + 1, imageCount - 1)
        return PreviewFrame(
            fromIndex = baseIndex,
            toIndex = nextIndex,
            fraction = if (baseIndex == nextIndex) 0f else (clampedPosition - baseIndex).coerceIn(0f, 1f),
        )
    }

    val baseIndex = floor(position).toInt().coerceIn(0, imageCount - 1)
    val nextIndex = min(baseIndex + 1, imageCount - 1)
    return PreviewFrame(
        fromIndex = baseIndex,
        toIndex = nextIndex,
        fraction = if (baseIndex == nextIndex) 0f else (position - baseIndex).coerceIn(0f, 1f),
    )
}

private fun Context.persistPhotoPickerReadGrant(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
