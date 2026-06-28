package com.highdel4y.flipcardwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import androidx.core.net.toUri
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.min

class LenticularWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LenticularEngine()

    private inner class LenticularEngine : Engine(), SensorEventListener {
        private val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val mainHandler = Handler(mainLooper)
        private val sensorThread = HandlerThread("FlipCardSensors").apply { start() }
        private val sensorHandler = Handler(sensorThread.looper)
        private val decodeThread = HandlerThread("FlipCardBitmapDecode").apply { start() }
        private val decodeHandler = Handler(decodeThread.looper)

        private val rotationMatrix = FloatArray(9)
        private val displayRotationMatrix = FloatArray(9)
        private val dstRect = Rect()
        private val imageAPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            isAntiAlias = false
            alpha = 255
        }
        private val imageBPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            isAntiAlias = false
            alpha = 0
        }
        private val effectDstRectA = Rect()
        private val effectDstRectB = Rect()
        private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 42f
        }
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                drawFrame(frameTimeNanos)
                if (isVisibleToUser) {
                    Choreographer.getInstance().postFrameCallback(this)
                    framePosted = true
                } else {
                    framePosted = false
                }
            }
        }
        private var activeSensor: Sensor? = null
        private var sensorRegistered = false
        private var isVisibleToUser = false
        private var framePosted = false
        private var displayRotation = Surface.ROTATION_0
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var imageLeases: List<BitmapLease> = emptyList()
        private var imageSrcRects: List<Rect> = emptyList()
        private var transitionEffect = TransitionEffect.Crossfade
        private var transitionSpeed = WallpaperPrefs.DEFAULT_TRANSITION_SPEED
        private var tiltThresholdRadians = WallpaperPrefs.DEFAULT_TILT_THRESHOLD_DEGREES * DEG_TO_RAD
        private var tiltSensitivity = WallpaperPrefs.DEFAULT_TILT_SENSITIVITY
        private var tiltStartSide = TiltStartSide.Right
        private var tiltStepRadians = WallpaperPrefs.DEFAULT_TILT_STEP_DEGREES * DEG_TO_RAD
        private var loopEnabled = false
        private var loopTransitionMode = LoopTransitionMode.Snap
        private var loadGeneration = 0

        @Volatile
        private var currentPosition = 0f

        private var filteredRollRadians = 0f
        private var lastGyroTimestampNs = 0L
        private var wallpaperTarget = WallpaperTarget.System
        private var missingImagesText = WallpaperTarget.System.missingImagesText

        @Volatile
        private var targetPosition = 0f

        @Volatile
        private var loopTiltActive = false

        @Volatile
        private var loopDirection = 0f

        @Volatile
        private var maxTiltPosition = 0f

        private var hasNeutralTurnYaw = false
        private var neutralTurnYawRadians = 0f


        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            wallpaperTarget = WallpaperTarget.fromFlags(readWallpaperFlags())
            missingImagesText = wallpaperTarget.missingImagesText
            activeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            setTouchEventsEnabled(false)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            updateDisplayRotation()
            surfaceWidth = width
            surfaceHeight = height
            dstRect.set(0, 0, width, height)
            requestBitmapLoad()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleToUser = visible
            if (visible) {
                updateDisplayRotation()
                requestBitmapLoad()
                registerSensor()
                startFrameLoop()
            } else {
                unregisterSensor()
                stopFrameLoop()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            unregisterSensor()
            stopFrameLoop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            unregisterSensor()
            stopFrameLoop()
            loadGeneration++
            decodeHandler.removeCallbacksAndMessages(null)
            mainHandler.removeCallbacksAndMessages(null)
            recycleBitmaps()
            sensorThread.quitSafely()
            decodeThread.quitSafely()
            super.onDestroy()
        }

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GAME_ROTATION_VECTOR,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                    updateTargetPositionFromRotationVector(event.values)
                }

                Sensor.TYPE_ORIENTATION -> {
                    updateTargetPositionFromRoll(event.values[2] * DEG_TO_RAD)
                }

                Sensor.TYPE_GYROSCOPE -> {
                    val lastTimestamp = lastGyroTimestampNs
                    if (lastTimestamp != 0L) {
                        val deltaSeconds = (event.timestamp - lastTimestamp) * NS_TO_SECONDS
                        updateTargetPositionFromRoll(filteredRollRadians + event.values[1] * deltaSeconds)
                    }
                    lastGyroTimestampNs = event.timestamp
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun updateDisplayRotation() {
            displayRotation = runCatching {
                getDisplayContext()?.display?.rotation
            }.getOrNull() ?: Surface.ROTATION_0
        }

        private fun registerSensor() {
            if (sensorRegistered) return
            activeSensor?.let { sensor ->
                filteredRollRadians = 0f
                hasNeutralTurnYaw = false
                neutralTurnYawRadians = 0f
                lastGyroTimestampNs = 0L
                loopTiltActive = false
                loopDirection = 0f
                sensorRegistered = sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                    sensorHandler,
                )
            }
        }

        private fun unregisterSensor() {
            if (!sensorRegistered) return
            sensorManager.unregisterListener(this)
            sensorRegistered = false
            lastGyroTimestampNs = 0L
            hasNeutralTurnYaw = false
            neutralTurnYawRadians = 0f
            loopTiltActive = false
            loopDirection = 0f
        }

        private fun updateTargetPositionFromRotationVector(values: FloatArray) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
            val screenMatrix = screenAdjustedRotationMatrix()
            val lateralRoll = lateralRollFromMatrix(screenMatrix)
            val uprightTurn = uprightTurnFromMatrix(screenMatrix) *
                uprightTurnWeight(screenMatrix) *
                UPRIGHT_TURN_GAIN
            updateTargetPositionFromRoll(blendedSideTilt(lateralRoll, uprightTurn))
        }

        private fun screenAdjustedRotationMatrix(): FloatArray =
            when (displayRotation) {
                Surface.ROTATION_90 -> {
                    if (
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_Y,
                            SensorManager.AXIS_MINUS_X,
                            displayRotationMatrix,
                        )
                    ) {
                        displayRotationMatrix
                    } else {
                        rotationMatrix
                    }
                }

                Surface.ROTATION_180 -> {
                    if (
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_MINUS_X,
                            SensorManager.AXIS_MINUS_Y,
                            displayRotationMatrix,
                        )
                    ) {
                        displayRotationMatrix
                    } else {
                        rotationMatrix
                    }
                }

                Surface.ROTATION_270 -> {
                    if (
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_MINUS_Y,
                            SensorManager.AXIS_X,
                            displayRotationMatrix,
                        )
                    ) {
                        displayRotationMatrix
                    } else {
                        rotationMatrix
                    }
                }

                else -> rotationMatrix
            }

        private fun lateralRollFromMatrix(matrix: FloatArray): Float =
            -asin(matrix[6].coerceIn(-1f, 1f))

        private fun uprightTurnFromMatrix(matrix: FloatArray): Float {
            val yawRadians = atan2(matrix[2], matrix[5])
            if (!hasNeutralTurnYaw) {
                neutralTurnYawRadians = yawRadians
                hasNeutralTurnYaw = true
                return 0f
            }
            return -wrapRadians(yawRadians - neutralTurnYawRadians)
        }

        private fun uprightTurnWeight(matrix: FloatArray): Float {
            val screenTopVertical = abs(matrix[7]).coerceInUnit()
            return ((screenTopVertical - UPRIGHT_TURN_START) / UPRIGHT_TURN_RANGE).coerceInUnit()
        }

        private fun blendedSideTilt(lateralRoll: Float, uprightTurn: Float): Float {
            if (uprightTurn == 0f || lateralRoll == 0f) {
                return lateralRoll + uprightTurn
            }

            val sameDirection = (lateralRoll > 0f) == (uprightTurn > 0f)
            if (!sameDirection) {
                return lateralRoll + uprightTurn
            }

            return if (abs(uprightTurn) > abs(lateralRoll)) uprightTurn else lateralRoll
        }

        private fun updateTargetPositionFromRoll(rollRadians: Float) {
            filteredRollRadians += ROLL_SMOOTH_FACTOR * (rollRadians - filteredRollRadians)
            if (loopEnabled) {
                updateLoopTargetFromRoll(filteredRollRadians)
                return
            }

            loopTiltActive = false
            loopDirection = 0f
            val activeTiltRadians = when (tiltStartSide) {
                TiltStartSide.Right -> (-filteredRollRadians).coerceAtLeast(0f)
                TiltStartSide.Left -> filteredRollRadians.coerceAtLeast(0f)
            }
            targetPosition = photoStepPositionForTilt(activeTiltRadians, maxTiltPosition)
        }

        private fun updateLoopTargetFromRoll(rollRadians: Float) {
            val maxPosition = maxTiltPosition
            if (maxPosition <= 0f) {
                targetPosition = 0f
                loopTiltActive = false
                loopDirection = 0f
                return
            }

            val leftTiltRadians = (-rollRadians).coerceAtLeast(0f)
            val rightTiltRadians = rollRadians.coerceAtLeast(0f)
            val thresholdRadians = tiltThresholdRadians
            val direction = when {
                leftTiltRadians > thresholdRadians && leftTiltRadians >= rightTiltRadians -> 1f
                rightTiltRadians > thresholdRadians -> -1f
                else -> 0f
            }
            if (direction == 0f) {
                targetPosition = 0f
                loopDirection = 0f
                loopTiltActive = false
                return
            }

            loopDirection = direction
            val tiltRadians = if (direction > 0f) leftTiltRadians else rightTiltRadians
            val loopPosition = when (loopTransitionMode) {
                LoopTransitionMode.Snap -> photoStepPositionForTilt(tiltRadians, maxPosition)
                LoopTransitionMode.Smooth -> photoSmoothPositionForTilt(tiltRadians, maxPosition)
            }
            targetPosition = direction * loopPosition
            loopTiltActive = loopPosition > 0f
        }

        private fun activeRangeRadians(): Float =
            ROLL_ACTIVE_RANGE_RADIANS / tiltSensitivity

        private fun photoStepPositionForTilt(tiltRadians: Float, maxPosition: Float): Float {
            if (maxPosition <= 0f || tiltRadians <= tiltThresholdRadians) return 0f
            val effectiveStep = (tiltStepRadians / tiltSensitivity).coerceAtLeast(MIN_TILT_STEP_RADIANS)
            val step = floor((tiltRadians - tiltThresholdRadians) / effectiveStep).toFloat()
            return step.coerceIn(0f, maxPosition)
        }

        private fun photoSmoothPositionForTilt(tiltRadians: Float, maxPosition: Float): Float {
            if (maxPosition <= 0f || tiltRadians <= tiltThresholdRadians) return 0f
            val normalized = ((tiltRadians - tiltThresholdRadians) / activeRangeRadians()).coerceInUnit()
            return normalized * maxPosition
        }

        private fun startFrameLoop() {
            if (framePosted) return
            Choreographer.getInstance().postFrameCallback(frameCallback)
            framePosted = true
        }

        private fun stopFrameLoop() {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            framePosted = false
            loopTiltActive = false
            loopDirection = 0f
        }

        private fun requestBitmapLoad() {
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            val nextTransitionEffect = WallpaperPrefs.transitionEffect(this@LenticularWallpaperService)
            val nextLoopEnabled = WallpaperPrefs.loopEnabled(this@LenticularWallpaperService)
            val nextLoopTransitionMode = WallpaperPrefs.loopTransitionMode(this@LenticularWallpaperService)
            val nextTransitionSpeed = WallpaperPrefs.transitionSpeed(this@LenticularWallpaperService)
            val nextTiltThresholdRadians =
                WallpaperPrefs.tiltThresholdDegrees(this@LenticularWallpaperService) * DEG_TO_RAD
            val nextTiltSensitivity = WallpaperPrefs.tiltSensitivity(this@LenticularWallpaperService)
            val nextTiltStartSide = WallpaperPrefs.tiltStartSide(this@LenticularWallpaperService)
            val nextTiltStepRadians = WallpaperPrefs.tiltStepDegrees(this@LenticularWallpaperService) * DEG_TO_RAD
            val width = surfaceWidth
            val height = surfaceHeight
            val generation = ++loadGeneration

            val uriStrings = WallpaperPrefs.imageUriStrings(this@LenticularWallpaperService)
            val transforms = WallpaperPrefs.imageTransforms(this@LenticularWallpaperService, uriStrings)
            decodeHandler.post {
                val nextLeases = ArrayList<BitmapLease>(uriStrings.size)
                val nextTransforms = ArrayList<ImageTransform>(uriStrings.size)
                uriStrings.forEachIndexed { index, uriString ->
                    val lease = SharedWallpaperBitmapStore.acquire(
                        context = this@LenticularWallpaperService,
                        uriString = uriString,
                        targetWidth = width,
                        targetHeight = height,
                    )
                    if (lease != null) {
                        nextLeases.add(lease)
                        nextTransforms.add(transforms.getOrElse(index) { ImageTransform.Centered })
                    }
                }

                mainHandler.post {
                    if (generation == loadGeneration) {
                        val oldLeases = imageLeases
                        imageLeases = nextLeases
                        imageSrcRects = buildCropRects(nextLeases, nextTransforms)
                        transitionEffect = nextTransitionEffect
                        transitionSpeed = nextTransitionSpeed
                        tiltThresholdRadians = nextTiltThresholdRadians
                        tiltSensitivity = nextTiltSensitivity
                        tiltStartSide = nextTiltStartSide
                        tiltStepRadians = nextTiltStepRadians
                        loopEnabled = nextLoopEnabled
                        loopTransitionMode = nextLoopTransitionMode
                        maxTiltPosition = maxPositionFor(nextLeases.size)
                        if (nextLoopEnabled) {
                            targetPosition = targetPosition.coerceIn(-maxTiltPosition, maxTiltPosition)
                            currentPosition = currentPosition.coerceIn(-maxTiltPosition, maxTiltPosition)
                            loopTiltActive = false
                            loopDirection = 0f
                        } else {
                            targetPosition = targetPosition.coerceIn(0f, maxTiltPosition)
                            currentPosition = currentPosition.coerceIn(0f, maxTiltPosition)
                        }
                        oldLeases.forEach(BitmapLease::release)
                    } else {
                        nextLeases.forEach(BitmapLease::release)
                    }
                }
            }
        }

        private fun drawFrame(frameTimeNanos: Long) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                val lockedCanvas = holder.lockHardwareCanvas()
                canvas = lockedCanvas
                drawCanvas(lockedCanvas)
            } catch (_: IllegalArgumentException) {
                drawFallback(holder)
            } catch (_: IllegalStateException) {
                drawFallback(holder)
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun drawFallback(holder: SurfaceHolder) {
            var fallbackCanvas: Canvas? = null
            try {
                fallbackCanvas = holder.lockCanvas()
                drawCanvas(fallbackCanvas)
            } catch (_: IllegalArgumentException) {
                fallbackCanvas = null
            } catch (_: IllegalStateException) {
                fallbackCanvas = null
            } finally {
                if (fallbackCanvas != null) {
                    holder.unlockCanvasAndPost(fallbackCanvas)
                }
            }
        }

        private fun drawCanvas(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)

            val leases = imageLeases
            val srcRects = imageSrcRects
            if (leases.isEmpty() || srcRects.size != leases.size) {
                drawMissingImages(canvas)
                return
            }

            val framePosition = updateFramePosition(leases.size)
            val frame = frameForPosition(framePosition, leases.size, loopEnabled)
            val fromIndex = frame.fromIndex
            val toIndex = frame.toIndex
            val fraction = frame.fraction

            val first = leases[fromIndex].bitmap
            val second = leases[toIndex].bitmap
            if (first.isRecycled || second.isRecycled) {
                drawMissingImages(canvas)
                return
            }

            drawTransition(
                canvas = canvas,
                first = first,
                firstSrc = srcRects[fromIndex],
                second = second,
                secondSrc = srcRects[toIndex],
                fraction = fraction.coerceInUnit(),
                swipeDirection = frame.swipeDirection,
            )
        }

        private fun updateFramePosition(imageCount: Int): Float {
            if (loopEnabled && imageCount > 1) {
                val maxPosition = maxPositionFor(imageCount)
                val target = targetPosition.coerceIn(-maxPosition, maxPosition)
                currentPosition = smoothPositionToward(target)
                return currentPosition.coerceIn(-maxPosition, maxPosition)
            }

            currentPosition = smoothPositionToward(targetPosition)
            return currentPosition.coerceIn(0f, maxPositionFor(imageCount))
        }

        private fun smoothPositionToward(target: Float): Float {
            val next = currentPosition + positionSmoothFactor() * (target - currentPosition)
            return if (abs(target - next) <= POSITION_SETTLE_EPSILON) target else next
        }

        private fun positionSmoothFactor(): Float =
            (POSITION_SMOOTH_FACTOR * transitionSpeed).coerceIn(POSITION_SMOOTH_MIN, POSITION_SMOOTH_MAX)

        private fun drawMissingImages(canvas: Canvas) {
            canvas.drawText(missingImagesText, surfaceWidth * 0.5f, surfaceHeight * 0.5f, statusPaint)
        }

        private fun updateCropRect(bitmap: Bitmap?, transform: ImageTransform, outRect: Rect) {
            if (bitmap == null || surfaceWidth <= 0 || surfaceHeight <= 0) {
                outRect.setEmpty()
                return
            }

            val crop = calculateBitmapCropRect(
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
                targetWidth = surfaceWidth,
                targetHeight = surfaceHeight,
                transform = transform,
            )
            outRect.set(crop.left, crop.top, crop.right, crop.bottom)
        }

        private fun buildCropRects(leases: List<BitmapLease>, transforms: List<ImageTransform>): List<Rect> =
            leases.mapIndexed { index, lease ->
                Rect().also {
                    updateCropRect(
                        bitmap = lease.bitmap,
                        transform = transforms.getOrElse(index) { ImageTransform.Centered },
                        outRect = it,
                    )
                }
            }

        private fun maxPositionFor(imageCount: Int): Float =
            (imageCount - 1).coerceAtLeast(0).toFloat()

        private fun wrapLoopIndex(index: Int, imageCount: Int): Int {
            if (imageCount <= 0) return 0
            val remainder = index % imageCount
            return if (remainder < 0) remainder + imageCount else remainder
        }

        private fun frameForPosition(position: Float, imageCount: Int, loop: Boolean): FramePosition {
            if (imageCount <= 1) {
                return FramePosition(0, 0, 0f, 1f)
            }

            if (loop) {
                val maxPosition = maxPositionFor(imageCount)
                val clampedPosition = position.coerceIn(-maxPosition, maxPosition)
                if (clampedPosition < 0f) {
                    val backwardPosition = -clampedPosition
                    val baseStep = floor(backwardPosition).toInt().coerceIn(0, imageCount - 1)
                    val fromIndex = wrapLoopIndex(-baseStep, imageCount)
                    val toIndex = wrapLoopIndex(-(baseStep + 1), imageCount)
                    val fraction = (backwardPosition - baseStep).coerceInUnit()
                    return FramePosition(
                        fromIndex = fromIndex,
                        toIndex = toIndex,
                        fraction = if (fromIndex == toIndex) 0f else fraction,
                        swipeDirection = -1f,
                    )
                }

                val baseIndex = floor(clampedPosition).toInt().coerceIn(0, imageCount - 1)
                val nextIndex = min(baseIndex + 1, imageCount - 1)
                return FramePosition(
                    fromIndex = baseIndex,
                    toIndex = nextIndex,
                    fraction = if (baseIndex == nextIndex) 0f else (clampedPosition - baseIndex).coerceInUnit(),
                    swipeDirection = 1f,
                )
            }

            val baseIndex = floor(position).toInt().coerceIn(0, imageCount - 1)
            val nextIndex = min(baseIndex + 1, imageCount - 1)
            return FramePosition(
                fromIndex = baseIndex,
                toIndex = nextIndex,
                fraction = if (baseIndex == nextIndex) 0f else (position - baseIndex).coerceInUnit(),
                swipeDirection = 1f,
            )
        }

        private fun drawTransition(
            canvas: Canvas,
            first: Bitmap,
            firstSrc: Rect,
            second: Bitmap,
            secondSrc: Rect,
            fraction: Float,
            swipeDirection: Float,
        ) {
            if (fraction <= 0f || first == second) {
                imageAPaint.alpha = 255
                canvas.drawBitmap(first, firstSrc, dstRect, imageAPaint)
                return
            }

            when (transitionEffect) {
                TransitionEffect.Crossfade -> drawCrossfade(canvas, first, firstSrc, second, secondSrc, fraction)
                TransitionEffect.Slide -> drawSlide(canvas, first, firstSrc, second, secondSrc, fraction, swipeDirection)
                TransitionEffect.Wipe -> drawWipe(canvas, first, firstSrc, second, secondSrc, fraction, swipeDirection)
                TransitionEffect.SwipeFade -> {
                    drawSwipeFade(canvas, first, firstSrc, second, secondSrc, fraction, swipeDirection)
                }
                TransitionEffect.ZoomFade -> drawZoomFade(canvas, first, firstSrc, second, secondSrc, fraction)
                TransitionEffect.Depth -> drawDepth(canvas, first, firstSrc, second, secondSrc, fraction)
            }
        }

        private fun drawCrossfade(canvas: Canvas, first: Bitmap, firstSrc: Rect, second: Bitmap, secondSrc: Rect, fraction: Float) {
            imageAPaint.alpha = 255
            imageBPaint.alpha = (MAX_ALPHA * fraction + 0.5f).toInt().coerceInAlpha()
            canvas.drawBitmap(first, firstSrc, dstRect, imageAPaint)
            canvas.drawBitmap(second, secondSrc, dstRect, imageBPaint)
        }

        @Suppress("UseKtx")
        private fun drawSlide(
            canvas: Canvas,
            first: Bitmap,
            firstSrc: Rect,
            second: Bitmap,
            secondSrc: Rect,
            fraction: Float,
            swipeDirection: Float,
        ) {
            imageAPaint.alpha = 255
            imageBPaint.alpha = 255

            val width = surfaceWidth.toFloat()
            canvas.save()
            canvas.translate(-swipeDirection * width * fraction, 0f)
            canvas.drawBitmap(first, firstSrc, dstRect, imageAPaint)
            canvas.restore()

            canvas.save()
            canvas.translate(swipeDirection * width * (1f - fraction), 0f)
            canvas.drawBitmap(second, secondSrc, dstRect, imageBPaint)
            canvas.restore()
        }

        private fun drawSwipeFade(
            canvas: Canvas,
            first: Bitmap,
            firstSrc: Rect,
            second: Bitmap,
            secondSrc: Rect,
            fraction: Float,
            swipeDirection: Float,
        ) {
            val width = surfaceWidth.toFloat()
            imageAPaint.alpha = (MAX_ALPHA * (1f - 0.55f * fraction) + 0.5f).toInt().coerceInAlpha()
            imageBPaint.alpha = (MAX_ALPHA * fraction + 0.5f).toInt().coerceInAlpha()

            canvas.save()
            canvas.translate(-swipeDirection * width * 0.18f * fraction, 0f)
            canvas.drawBitmap(first, firstSrc, dstRect, imageAPaint)
            canvas.restore()

            canvas.save()
            canvas.translate(swipeDirection * width * 0.28f * (1f - fraction), 0f)
            canvas.drawBitmap(second, secondSrc, dstRect, imageBPaint)
            canvas.restore()
        }

        @Suppress("UseKtx")
        private fun drawWipe(
            canvas: Canvas,
            first: Bitmap,
            firstSrc: Rect,
            second: Bitmap,
            secondSrc: Rect,
            fraction: Float,
            swipeDirection: Float,
        ) {
            imageAPaint.alpha = 255
            imageBPaint.alpha = 255
            canvas.drawBitmap(first, firstSrc, dstRect, imageAPaint)

            val revealWidth = (surfaceWidth * fraction + 0.5f).toInt().coerceIn(0, surfaceWidth)
            canvas.save()
            if (swipeDirection > 0f) {
                canvas.clipRect(surfaceWidth - revealWidth, 0, surfaceWidth, surfaceHeight)
            } else {
                canvas.clipRect(0, 0, revealWidth, surfaceHeight)
            }
            canvas.drawBitmap(second, secondSrc, dstRect, imageBPaint)
            canvas.restore()
        }

        private fun drawZoomFade(canvas: Canvas, first: Bitmap, firstSrc: Rect, second: Bitmap, secondSrc: Rect, fraction: Float) {
            imageAPaint.alpha = (MAX_ALPHA * (1f - 0.24f * fraction) + 0.5f).toInt().coerceInAlpha()
            imageBPaint.alpha = (MAX_ALPHA * fraction + 0.5f).toInt().coerceInAlpha()
            canvas.drawBitmap(first, firstSrc, dstRect, imageAPaint)
            setScaledDst(effectDstRectB, 1.08f - 0.08f * fraction)
            canvas.drawBitmap(second, secondSrc, effectDstRectB, imageBPaint)
        }

        private fun drawDepth(canvas: Canvas, first: Bitmap, firstSrc: Rect, second: Bitmap, secondSrc: Rect, fraction: Float) {
            imageAPaint.alpha = (MAX_ALPHA * (1f - 0.42f * fraction) + 0.5f).toInt().coerceInAlpha()
            imageBPaint.alpha = (MAX_ALPHA * fraction + 0.5f).toInt().coerceInAlpha()
            setScaledDst(effectDstRectA, 1f + 0.06f * fraction)
            setScaledDst(effectDstRectB, 0.94f + 0.06f * fraction)
            canvas.drawBitmap(first, firstSrc, effectDstRectA, imageAPaint)
            canvas.drawBitmap(second, secondSrc, effectDstRectB, imageBPaint)
        }

        private fun setScaledDst(outRect: Rect, scale: Float) {
            val scaledWidth = (surfaceWidth * scale + 0.5f).toInt()
            val scaledHeight = (surfaceHeight * scale + 0.5f).toInt()
            val left = (surfaceWidth - scaledWidth) / 2
            val top = (surfaceHeight - scaledHeight) / 2
            outRect.set(left, top, left + scaledWidth, top + scaledHeight)
        }

        private fun recycleBitmaps() {
            imageLeases.forEach(BitmapLease::release)
            imageLeases = emptyList()
            imageSrcRects = emptyList()
            maxTiltPosition = 0f
            targetPosition = 0f
            currentPosition = 0f
            loopTiltActive = false
            loopDirection = 0f
        }

        private fun readWallpaperFlags(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                getWallpaperFlags()
            } else {
                WallpaperManager.FLAG_SYSTEM
            }

    }

    private companion object {
        private const val MAX_ALPHA = 255f
        private const val POSITION_SMOOTH_FACTOR = 0.10f
        private const val POSITION_SMOOTH_MIN = 0.035f
        private const val POSITION_SMOOTH_MAX = 0.28f
        private const val POSITION_SETTLE_EPSILON = 0.006f
        private const val ROLL_SMOOTH_FACTOR = 0.18f
        private const val DEG_TO_RAD = 0.017453292f
        private const val NS_TO_SECONDS = 0.000000001f
        private const val ROLL_DEAD_ZONE_RADIANS = 2f * DEG_TO_RAD
        private const val ROLL_ACTIVE_RANGE_RADIANS = 33f * DEG_TO_RAD
        private const val MIN_TILT_STEP_RADIANS = 0.5f * DEG_TO_RAD
        private const val UPRIGHT_TURN_START = 0.58f
        private const val UPRIGHT_TURN_FULL = 0.86f
        private const val UPRIGHT_TURN_RANGE = UPRIGHT_TURN_FULL - UPRIGHT_TURN_START
        private const val UPRIGHT_TURN_GAIN = 0.42f
        private const val TWO_PI = 6.2831855f
        private const val PI = 3.1415927f

        private fun wrapRadians(radians: Float): Float {
            var wrapped = radians
            while (wrapped > PI) wrapped -= TWO_PI
            while (wrapped < -PI) wrapped += TWO_PI
            return wrapped
        }

        private fun Float.coerceInUnit(): Float = when {
            this < 0f -> 0f
            this > 1f -> 1f
            else -> this
        }

        private fun Int.coerceInAlpha(): Int = when {
            this < 0 -> 0
            this > 255 -> 255
            else -> this
        }
    }
}

private data class FramePosition(
    val fromIndex: Int,
    val toIndex: Int,
    val fraction: Float,
    val swipeDirection: Float,
)

private enum class WallpaperTarget(val missingImagesText: String) {
    System("Select images for Home"),
    Lock("Select images for Lock"),
    SystemAndLock("Select images for Home and Lock");

    companion object {
        fun fromFlags(flags: Int): WallpaperTarget {
            val drawsSystem = flags and WallpaperManager.FLAG_SYSTEM != 0
            val drawsLock = flags and WallpaperManager.FLAG_LOCK != 0
            return when {
                drawsSystem && drawsLock -> SystemAndLock
                drawsLock -> Lock
                else -> System
            }
        }
    }
}

private class BitmapLease internal constructor(
    private val key: SharedWallpaperBitmapStore.Key,
    val bitmap: Bitmap,
) {
    private var released = false

    fun release() {
        if (released) return
        released = true
        SharedWallpaperBitmapStore.release(key)
    }
}

private object SharedWallpaperBitmapStore {
    data class Key(
        val uriString: String,
        val targetWidth: Int,
        val targetHeight: Int,
    )

    private data class Entry(
        val bitmap: Bitmap,
        var refCount: Int,
    )

    private val lock = Any()
    private val entries = HashMap<Key, Entry>()

    fun acquire(
        context: Context,
        uriString: String,
        targetWidth: Int,
        targetHeight: Int,
    ): BitmapLease? {
        val key = Key(uriString, targetWidth, targetHeight)
        var cachedBitmap: Bitmap? = null

        synchronized(lock) {
            val entry = entries[key]
            if (entry != null) {
                entry.refCount += 1
                cachedBitmap = entry.bitmap
            }
        }
        cachedBitmap?.let {
            return BitmapLease(key, it)
        }

        val decoded = BitmapLoader.decodeForTarget(
            context = context,
            uri = uriString.toUri(),
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            hardware = true,
        ) ?: return null

        var bitmapForLease = decoded
        var duplicateToRecycle: Bitmap? = null
        synchronized(lock) {
            val existingEntry = entries[key]
            if (existingEntry != null) {
                existingEntry.refCount += 1
                bitmapForLease = existingEntry.bitmap
                duplicateToRecycle = decoded
            } else {
                entries[key] = Entry(decoded, refCount = 1)
            }
        }
        duplicateToRecycle?.recycle()

        return BitmapLease(key, bitmapForLease)
    }

    fun release(key: Key) {
        var bitmapToRecycle: Bitmap? = null
        synchronized(lock) {
            val entry = entries[key] ?: return
            entry.refCount -= 1
            if (entry.refCount <= 0) {
                entries.remove(key)
                bitmapToRecycle = entry.bitmap
            }
        }
        bitmapToRecycle?.recycle()
    }
}
