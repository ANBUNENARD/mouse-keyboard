package com.darusc.mousedroid.mkinput

import android.content.Context
import android.os.Looper
import android.view.*
import androidx.core.view.GestureDetectorCompat
import com.darusc.mousedroid.networking.ConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Class that handles simples gestures (tap, long press, scroll) using
 * a GestureDetectorCompat
 */
class GestureHandler(
    context: Context,
    private val sendInputCallback: (InputEvent) -> (Unit)
) : View.OnTouchListener {

    private val TAG = "Mousedroid"
    private val EV_DELAY_MILLIS: Long = 110
    private val SCROLL_TRESHOLD = 2.0f

    /**
     * Touch slop / sensitivity tuning. The old build sent raw pixel deltas
     * capped at 127, so a fast flick either barely moved the cursor or
     * saturated the HID byte and felt jumpy. Scale by density, apply a gain,
     * and split large deltas into several bounded HID reports so movement
     * stays smooth and proportional to finger speed.
     */
    private val density: Float = context.resources.displayMetrics.density
    private val moveGain = 1.9f
    private val scrollGain = 0.45f
    private var moveRemainderX = 0f
    private var moveRemainderY = 0f
    private var scrollRemainderX = 0f
    private var scrollRemainderY = 0f

    private data class State(
        var scrolling: Boolean,
        var lastScrolled: Long,
        var doublePress: Boolean,
        var lastDoublePress: Long,
        var dragging: Boolean,
        var activeMouseWhileDragging: InputEvent.MouseButton
    )

    private val state = State(false, 0, false, 0, false, InputEvent.MouseButton.NONE)

    /**
     * Detector used for detecting scaling (pinch to zoom)
     */
    private val scaleDetector: ScaleGestureDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            private var accumulatedZoom = 0f
            private val ZOOM_PIXEL_THRESHOLD = 20f

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Calculate the raw distance change in pixels
                val deltaPixels = detector.currentSpan - detector.previousSpan

                accumulatedZoom += deltaPixels
                if (abs(accumulatedZoom) >= ZOOM_PIXEL_THRESHOLD) {
                    // Calculate how many "ticks" this equals
                    val ticks = (accumulatedZoom / ZOOM_PIXEL_THRESHOLD).toInt()
                    if (ticks != 0) {
                        sendInputCallback(InputEvent.Zoom(ticks))
                        // Remove the consumed part, keeping the remainder for smooth scaling
                        accumulatedZoom -= (ticks * ZOOM_PIXEL_THRESHOLD)
                    }
                }

                return true
            }
        })
//    private val scaleDetector: ScaleGestureDetector =
//        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
//            override fun onScale(detector: ScaleGestureDetector): Boolean {
//                val scale = (ln(detector.scaleFactor) * 500).toInt().coerceIn(-128, 127).toByte()
//                if (!state.dragging) {
//                    connectionManager.send(InputEvent.Zoom(scale.toInt()), true)
//                }
//
//                return true
//            }
//        })

    /**
     * Detector used for gestures like: tap, double tap, move, drag and scroll
     */
    private val gestureDetector: GestureDetectorCompat =
        GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Post a runnable that sends a click event after a set delay
                // onDoubleTap will cancel it when called
                singleTapRunnable = Runnable {
                    sendInputCallback(InputEvent.MouseClick(InputEvent.MouseButton.LEFT))
                }
                handler.postDelayed(singleTapRunnable!!, EV_DELAY_MILLIS)
                return super.onSingleTapUp(e)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Cancel the click event from the single tap
                singleTapRunnable?.let { handler.removeCallbacks(it) }

                state.doublePress = true
                state.lastDoublePress = System.currentTimeMillis()

                // Post a runnable that will send 2 click events after a set time
                // If before that a move event is detected by the onTouch, it will be canceled
                doubleTapRunnable = Runnable {
                    CoroutineScope(Dispatchers.IO).launch {
                        sendInputCallback(InputEvent.MouseClick(InputEvent.MouseButton.LEFT))
                        // Add delay so the 2 clicks register as a double click
                        // TODO() might break over TCP/UDP
                        delay(75)
                        sendInputCallback(InputEvent.MouseClick(InputEvent.MouseButton.LEFT))

                        state.doublePress = false
                        state.dragging = false
                    }
                }
                handler.postDelayed(doubleTapRunnable!!, EV_DELAY_MILLIS)
                return false
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val fingers = maxOf(e1?.pointerCount ?: 1, e2.pointerCount)
                if (fingers >= 2 || System.currentTimeMillis() - state.lastScrolled < EV_DELAY_MILLIS) {
                    if (abs(distanceX) < SCROLL_TRESHOLD && abs(distanceY) < SCROLL_TRESHOLD) {
                        return super.onScroll(e1, e2, distanceX, distanceY)
                    }

                    // Two fingers = scroll. Keep scrolling briefly after the
                    // second finger lifts so the gesture does not cut off.
                    state.scrolling = true
                    state.lastScrolled = System.currentTimeMillis()
                    sendSmoothScroll(-distanceX, -distanceY)
                } else {
                    // One finger = pointer move.
                    sendSmoothMove(distanceX, distanceY, state.activeMouseWhileDragging)
                }

                return super.onScroll(e1, e2, distanceX, distanceY)
            }
        })


    private val handler = android.os.Handler(Looper.getMainLooper())
    private var singleTapRunnable: Runnable? = null
    private var doubleTapRunnable: Runnable? = null

    override fun onTouch(p0: View?, p1: MotionEvent?): Boolean {

        if (p1?.actionMasked == MotionEvent.ACTION_POINTER_UP && p1.pointerCount == 2) {
            if (state.scrolling) {
                // Cancel scrolling when pointer is lifted up
                state.scrolling = false
                return false
            } else if (System.currentTimeMillis() - state.lastScrolled > 500) {
                // Right click is detected when there are 2 pointers and 1 starts to lift
                // Can't be detected in onSingleTapUp because the 2 pointers are not lifted at the same time
                // so we don't get that event with 2 pointers but rather 2 different events with 1 pointer
                sendInputCallback(InputEvent.MouseClick(InputEvent.MouseButton.RIGHT))
            }
        }

        if (p1?.action == MotionEvent.ACTION_UP && state.dragging) {
            // Cancel dragging
            state.scrolling = false
            state.doublePress = false
            state.activeMouseWhileDragging = InputEvent.MouseButton.NONE
            sendInputCallback(InputEvent.MouseDragState(InputEvent.MouseButton.LEFT, false))
        }

        if (p1?.let { gestureDetector.onTouchEvent(it) } == true) {
            return true
        }
        p1?.let { scaleDetector.onTouchEvent(it) }

        when (p1?.action) {
            MotionEvent.ACTION_MOVE -> {
                if (state.doublePress && System.currentTimeMillis() - state.lastDoublePress < EV_DELAY_MILLIS) {
                    // If a move event is triggered right after a double press initiate dragging
                    // Can't be detected in onTouch because it seem like the detector classified that event
                    // as a double tap and won't detect a new scroll event right after

                    // Cancel the double tap and continue with dragging
                    doubleTapRunnable?.let { handler.removeCallbacks(it) }
                    state.doublePress = false
                    state.dragging = true
                    state.activeMouseWhileDragging = InputEvent.MouseButton.LEFT
                    // Send a mouse down event to initiate dragging
                    sendInputCallback(
                        InputEvent.MouseDragState(
                            InputEvent.MouseButton.LEFT,
                            true
                        )
                    )

                    val cancelEvent = MotionEvent.obtain(p1)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    gestureDetector.onTouchEvent(cancelEvent)
                }
            }
            MotionEvent.ACTION_DOWN -> {
                // Fresh stroke: drop accumulated sub-pixel remainders so the
                // cursor does not jump from the previous gesture.
                moveRemainderX = 0f
                moveRemainderY = 0f
                scrollRemainderX = 0f
                scrollRemainderY = 0f
            }
        }

        return true
    }

    private fun sendSmoothMove(distanceX: Float, distanceY: Float, button: InputEvent.MouseButton) {
        moveRemainderX += distanceX * moveGain / density
        moveRemainderY += distanceY * moveGain / density
        val dx = moveRemainderX.toInt()
        val dy = moveRemainderY.toInt()
        moveRemainderX -= dx
        moveRemainderY -= dy
        if (dx == 0 && dy == 0) {
            return
        }
        var remainingX = dx
        var remainingY = dy
        while (remainingX != 0 || remainingY != 0) {
            val stepX = remainingX.coerceIn(-100, 100)
            val stepY = remainingY.coerceIn(-100, 100)
            sendInputCallback(InputEvent.MouseMove(stepX, stepY, button))
            remainingX -= stepX
            remainingY -= stepY
        }
    }

    private fun sendSmoothScroll(distanceX: Float, distanceY: Float) {
        scrollRemainderX += distanceX * scrollGain
        scrollRemainderY += distanceY * scrollGain
        val dominantVertical = abs(scrollRemainderY) >= abs(scrollRemainderX)
        if (dominantVertical) {
            val ticks = (scrollRemainderY / 10).toInt()
            if (ticks != 0) {
                scrollRemainderY -= ticks * 10
                scrollRemainderX = 0f
                sendInputCallback(InputEvent.MouseScroll(0, ticks * 10))
            }
        } else {
            val ticks = (scrollRemainderX / 10).toInt()
            if (ticks != 0) {
                scrollRemainderX -= ticks * 10
                scrollRemainderY = 0f
                sendInputCallback(InputEvent.MouseScroll(ticks * 10, 0))
            }
        }
    }
}
