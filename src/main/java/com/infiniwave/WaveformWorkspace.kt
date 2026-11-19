package com.infiniwave

import android.content.Context
import android.view.MotionEvent
import android.widget.ScrollView
import com.infiniwave.editor.TimelinePinch
import kotlin.math.max

/** Own the complete pinch before Android can split its fingers across separate tracks. */
class WaveformWorkspace(context: Context) : ScrollView(context) {
    var canZoom: () -> Boolean = { true }
    var onPinch: ((Double, Double, Double) -> Unit)? = null
    var waveformInset = 0
    private val pinch = TimelinePinch(max(1.0, 8.0 * resources.displayMetrics.density))
    private var captured = false

    init { isMotionEventSplittingEnabled = false }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) finishPinch()
        if (!captured && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && canZoom()) {
            // Cancel any pending selection, header click or scroll before taking ownership.
            val cancel = MotionEvent.obtain(event)
            try {
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
            } finally { cancel.recycle() }
            captured = true
            parent?.requestDisallowInterceptTouchEvent(true)
            rebase(event)
            return true
        }
        if (!captured) return super.dispatchTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (canZoom() && event.pointerCount >= 2) {
                pinch.move(event.getX(0).toDouble(), event.getY(0).toDouble(), event.getX(1).toDouble(), event.getY(1).toDouble())?.let { delta ->
                    val left = paddingLeft + waveformInset
                    val usableWidth = max(1, width - left - paddingRight - waveformInset)
                    onPinch?.invoke(delta.factor(), (delta.previousFocusX() - left) / usableWidth, (delta.focusX() - left) / usableWidth)
                }
            } else pinch.reset()
            MotionEvent.ACTION_POINTER_DOWN -> rebase(event)
            MotionEvent.ACTION_POINTER_UP -> rebase(event, event.actionIndex)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishPinch()
        }
        // Keep ownership after one finger lifts; it must not start a selection or scroll.
        return true
    }

    private fun rebase(event: MotionEvent, excluded: Int = -1) {
        val indices = (0 until event.pointerCount).filter { it != excluded }
        if (indices.size < 2) { pinch.reset(); return }
        val a = indices[0]; val b = indices[1]
        pinch.begin(event.getX(a).toDouble(), event.getY(a).toDouble(), event.getX(b).toDouble(), event.getY(b).toDouble())
    }

    private fun finishPinch() {
        if (captured) parent?.requestDisallowInterceptTouchEvent(false)
        captured = false
        pinch.reset()
    }
}
