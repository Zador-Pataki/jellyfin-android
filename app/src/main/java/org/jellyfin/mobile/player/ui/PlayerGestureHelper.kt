package org.jellyfin.mobile.player.ui

import android.content.res.Configuration
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.dip
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale
import kotlin.math.abs

class PlayerGestureHelper(
    private val fragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val playerLockScreenHelper: PlayerLockScreenHelper,
) : KoinComponent {
    private val appPreferences: AppPreferences by inject()
    private val playerView: PlayerView by playerBinding::playerView
    private val seekOverlayLayout: LinearLayout by playerBinding::seekOverlayLayout
    private val seekOverlayImage: ImageView by playerBinding::seekOverlayImage
    private val seekOverlayText: TextView by playerBinding::seekOverlayText
    private val seekPositionText: TextView by playerBinding::seekPositionText
    private val seekOverlayProgress: ProgressBar by playerBinding::seekOverlayProgress
    private var isOnPressingSpeedUp = false

    /**
     * Tracks whether video content should fill the screen, cutting off unwanted content on the sides.
     * Useful on wide-screen phones to remove black bars from some movies.
     */
    private var isZoomEnabled = false

    private enum class GestureDirection {
        NONE,
        HORIZONTAL,
        VERTICAL,
    }

    /**
     * Tracks the current active swipe gesture.
     */
    private var currentGesture = GestureDirection.NONE

    /**
     * Tracks whether a horizontal swipe seek gesture is in progress.
     */
    private var isHorizontalSeeking = false

    /**
     * Tracks accumulated seek time during horizontal swipe (in milliseconds).
     */
    private var seekTimeAccumulator = 0L

    /**
     * Tracks the initial playback position when seek gesture started.
     */
    private var seekStartPosition = 0L

    /**
     * Tracks total duration of current media.
     */
    private var mediaDuration = 0L

    /**
     * Runnable that hides [playerView] controller
     */
    private val hidePlayerViewControllerAction = Runnable {
        playerView.hideController()
    }

    /**
     * Runnable that hides [seekOverlayLayout]
     */
    private val hideSeekOverlayAction = Runnable {
        seekOverlayLayout.isVisible = false
    }

    /**
     * Handles taps when controls are locked
     */
    private val unlockDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                playerLockScreenHelper.peekUnlockButton()
                return true
            }
        },
    )

    /**
     * Handles taps, horizontal seeking, and long-press speed changes.
     */
    private val gestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val viewWidth = playerView.measuredWidth
                val viewHeight = playerView.measuredHeight
                val viewCenterX = viewWidth / 2
                val viewCenterY = viewHeight / 2
                val isFastForward = e.x.toInt() > viewCenterX

                // Show ripple effect
                playerView.foreground?.apply {
                    val left = if (isFastForward) viewCenterX else 0
                    val right = if (isFastForward) viewWidth else viewCenterX
                    setBounds(left, viewCenterY - viewCenterX / 2, right, viewCenterY + viewCenterX / 2)
                    setHotspot(e.x, e.y)
                    state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
                    playerView.postDelayed(Constants.DOUBLE_TAP_RIPPLE_DURATION_MS) {
                        state = IntArray(0)
                    }
                }

                // Fast-forward/rewind
                with(fragment) { if (isFastForward) onFastForward() else onRewind() }

                // Cancel previous runnable to not hide controller while seeking
                playerView.removeCallbacks(hidePlayerViewControllerAction)

                // Ensure controller gets hidden after seeking
                playerView.postDelayed(hidePlayerViewControllerAction, Constants.DEFAULT_CONTROLS_TIMEOUT_MS.toLong())
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                playerView.apply {
                    if (!isControllerFullyVisible) showController() else hideController()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!appPreferences.exoPlayerAllowPressSpeedUp) {
                    return
                }

                with(fragment) {
                    isOnPressingSpeedUp = true
                    onPressSpeedUp(true)
                }
            }

            override fun onScroll(
                firstEvent: MotionEvent?,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (firstEvent == null) return false

                // Check whether swipe was started in excluded region (vertical)
                val exclusionSizeVertical = playerView.resources.dip(Constants.SWIPE_GESTURE_EXCLUSION_SIZE_VERTICAL)
                if (
                    firstEvent.y < exclusionSizeVertical ||
                    firstEvent.y > playerView.height - exclusionSizeVertical
                ) {
                    return false
                }

                // Determine/lock gesture direction
                if (currentGesture == GestureDirection.NONE) {
                    val deltaX = currentEvent.x - firstEvent.x
                    val deltaY = currentEvent.y - firstEvent.y

                    currentGesture = if (abs(deltaX) > abs(deltaY) * 2) GestureDirection.HORIZONTAL
                    else if (abs(deltaY) > abs(deltaX) * 2) GestureDirection.VERTICAL
                    else return false
                }

                // Handle horizontal swipe for seek
                if (currentGesture == GestureDirection.HORIZONTAL && appPreferences.exoPlayerAllowHorizontalGesture) {
                    // Check horizontal exclusion zones (edges of screen)
                    val exclusionSizeHorizontal = playerView.resources.dip(Constants.SWIPE_GESTURE_EXCLUSION_SIZE_HORIZONTAL)
                    if (
                        firstEvent.x < exclusionSizeHorizontal ||
                        firstEvent.x > playerView.width - exclusionSizeHorizontal
                    ) {
                        return false
                    }

                    // Initialize seek start position on first swipe
                    if (!isHorizontalSeeking) {
                        val player = playerView.player
                        if (player != null) {
                            seekStartPosition = player.currentPosition
                            mediaDuration = player.duration.coerceAtLeast(0)
                        }
                    }

                    isHorizontalSeeking = true

                    // Calculate seek time with non-linear acceleration
                    // The further you swipe, the faster the seek time increases
                    val baseSeekDeltaMs = (-distanceX * 1000 / Constants.HORIZONTAL_SWIPE_DISTANCE_PER_SECOND).toLong()

                    // Apply acceleration based on accumulated distance
                    // Use portrait acceleration (2x) for portrait mode, default for landscape
                    val accelerationFactor = if (fragment.isLandscape()) {
                        Constants.SEEK_ACCELERATION_FACTOR
                    } else {
                        Constants.SEEK_ACCELERATION_FACTOR_PORTRAIT
                    }
                    val currentSeekSeconds = abs(seekTimeAccumulator / 1000f)
                    val accelerationMultiplier = 1f + (currentSeekSeconds / 30f) * (accelerationFactor - 1f)
                    val acceleratedSeekDelta = (baseSeekDeltaMs * accelerationMultiplier).toLong()

                    seekTimeAccumulator += acceleratedSeekDelta

                    // Clamp the accumulated seek time to valid range
                    val minSeek = -seekStartPosition
                    val maxSeek = if (mediaDuration > 0) mediaDuration - seekStartPosition else Long.MAX_VALUE
                    // Allow seeking up to media duration (if known). Do not enforce an artificial MAX_SEEK_TIME_MS limit.
                    seekTimeAccumulator = seekTimeAccumulator.coerceIn(minSeek, maxSeek)

                    // Update the seek overlay with mm:ss format
                    val totalSeconds = abs(seekTimeAccumulator / 1000)
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                    val seekText = if (seekTimeAccumulator >= 0) "+$timeFormatted" else "-$timeFormatted"
                    seekOverlayText.text = seekText

                    // Update position text (current position / duration)
                    val targetPosition = (seekStartPosition + seekTimeAccumulator).coerceIn(0, mediaDuration)
                    seekPositionText.text = "${formatTime(targetPosition)} / ${formatTime(mediaDuration)}"

                    // Update progress bar
                    if (mediaDuration > 0) {
                        seekOverlayProgress.max = 1000
                        seekOverlayProgress.progress = (targetPosition * 1000 / mediaDuration).toInt()
                    }

                    // Set appropriate icon based on direction
                    val iconRes = if (seekTimeAccumulator >= 0) {
                        R.drawable.ic_fast_forward_black_32dp
                    } else {
                        R.drawable.ic_rewind_black_32dp
                    }
                    seekOverlayImage.setImageResource(iconRes)

                    seekOverlayLayout.isVisible = true
                    return true
                } else if (currentGesture == GestureDirection.HORIZONTAL && !appPreferences.exoPlayerAllowHorizontalGesture) {
                    // If horizontal gesture is disabled while a gesture was in progress, reset the state
                    currentGesture = GestureDirection.NONE
                    isHorizontalSeeking = false
                    seekTimeAccumulator = 0L
                    seekStartPosition = 0L
                    mediaDuration = 0L
                    seekOverlayLayout.isVisible = false
                }
                return false
            }
        },
    )

    /**
     * Handles scale/zoom gesture
     */
    private val zoomGestureDetector = ScaleGestureDetector(
        playerView.context,
        object : ScaleGestureDetector.OnScaleGestureListener {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = fragment.isLandscape()

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                if (abs(scaleFactor - Constants.ZOOM_SCALE_BASE) > Constants.ZOOM_SCALE_THRESHOLD) {
                    isZoomEnabled = scaleFactor > 1
                    updateZoomMode(isZoomEnabled)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) = Unit
        },
    ).apply { isQuickScaleEnabled = false }

    init {
        @Suppress("ClickableViewAccessibility")
        playerView.setOnTouchListener { _, event ->
            if (playerView.useController) {
                when (event.pointerCount) {
                    1 -> gestureDetector.onTouchEvent(event)
                    2 -> zoomGestureDetector.onTouchEvent(event)
                }
            } else {
                unlockDetector.onTouchEvent(event)
            }
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (isOnPressingSpeedUp) {
                    isOnPressingSpeedUp = false
                    with(fragment) {
                        onPressSpeedUp(false)
                    }
                }

                // Handle horizontal seek gesture completion
                if (event.action == MotionEvent.ACTION_UP && currentGesture == GestureDirection.HORIZONTAL && isHorizontalSeeking && seekTimeAccumulator != 0L) {
                    fragment.onSeekByOffset(seekTimeAccumulator)
                    seekOverlayLayout.apply {
                        removeCallbacks(hideSeekOverlayAction)
                        postDelayed(
                            hideSeekOverlayAction,
                            Constants.DEFAULT_CENTER_OVERLAY_TIMEOUT_MS.toLong(),
                        )
                    }
                }
                currentGesture = GestureDirection.NONE
                isHorizontalSeeking = false
                seekTimeAccumulator = 0L
                seekStartPosition = 0L
                mediaDuration = 0L
            }
            true
        }
    }

    fun handleConfiguration(newConfig: Configuration) {
        updateZoomMode(fragment.isLandscape(newConfig) && isZoomEnabled)
    }

    private fun updateZoomMode(enabled: Boolean) {
        playerView.resizeMode = if (enabled) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    /**
     * Format time in milliseconds to mm:ss or h:mm:ss format
     */
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
