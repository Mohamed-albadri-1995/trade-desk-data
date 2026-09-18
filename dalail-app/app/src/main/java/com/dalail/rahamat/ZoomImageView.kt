package com.dalail.rahamat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

/**
 * A leaf the reader can bring closer.
 *
 * Pinch to magnify, drag to move about, double-tap to go in and out again. The
 * one subtlety is living inside a pager: while the leaf is magnified the drag
 * belongs to the leaf, and only when its edge is reached does the gesture pass
 * back to the pager so the page turns. Without that, either the page turns
 * while you are trying to read a corner, or it never turns at all.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {

    private companion object {
        const val MAX = 4f
        const val DOUBLE_TAP = 2.5f
        /** Slack at the edge, so a hair of overscroll does not lock the pager. */
        const val EDGE = 1f
    }

    private val m = Matrix()
    private val bounds = RectF()
    private var scale = 1f

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoomBy(d.scaleFactor, d.focusX, d.focusY)
                return true
            }
        }
    )

    private val tapper = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scale > 1.05f) reset() else zoomBy(DOUBLE_TAP, e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                if (scale <= 1.05f) return false
                m.postTranslate(-dx, -dy)
                settle()
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        // A leaf is taller than the screen; fitting it is the job of fit(), which
        // needs the view's size, so it waits for the layout pass.
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reset() }
    }

    /** Back to the whole leaf, fitted to the view. */
    fun reset() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val k = min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        m.setScale(k, k)
        m.postTranslate(
            (width - d.intrinsicWidth * k) / 2f,
            (height - d.intrinsicHeight * k) / 2f
        )
        scale = 1f
        imageMatrix = m
    }

    private fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val next = (scale * factor).coerceIn(1f, MAX)
        val applied = next / scale
        scale = next
        m.postScale(applied, applied, fx, fy)
        settle()
    }

    /** Keeps the leaf covering the view, and centred while it is smaller. */
    private fun settle() {
        val d = drawable ?: return
        bounds.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(bounds)
        var dx = 0f
        var dy = 0f
        if (bounds.width() <= width) dx = width / 2f - bounds.centerX()
        else if (bounds.left > 0) dx = -bounds.left
        else if (bounds.right < width) dx = width - bounds.right
        if (bounds.height() <= height) dy = height / 2f - bounds.centerY()
        else if (bounds.top > 0) dy = -bounds.top
        else if (bounds.bottom < height) dy = height - bounds.bottom
        m.postTranslate(dx, dy)
        imageMatrix = m
    }

    /**
     * Whether the leaf itself can still be dragged [direction]-ward. The pager
     * asks this before deciding the gesture is a page turn.
     */
    override fun canScrollHorizontally(direction: Int): Boolean {
        if (scale <= 1.05f) return false
        val d = drawable ?: return false
        bounds.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(bounds)
        return if (direction < 0) bounds.left < -EDGE else bounds.right > width + EDGE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        tapper.onTouchEvent(event)
        // While magnified the leaf keeps the gesture; the pager is told so it
        // does not turn the page mid-drag.
        parent?.requestDisallowInterceptTouchEvent(
            scale > 1.05f && event.pointerCount == 1
        )
        if (event.pointerCount > 1) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
