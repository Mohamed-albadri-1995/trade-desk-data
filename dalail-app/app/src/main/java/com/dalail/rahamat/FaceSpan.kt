package com.dalail.rahamat

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Sets a run of text in a typeface of its own.
 *
 * `TypefaceSpan(Typeface)` would do this, but only from Android 9 onwards, and
 * the book should read the same on an older phone. Synthesising a slant with
 * `StyleSpan(ITALIC)` is not the same thing either: Amiri has a cut italic of
 * its own, and the counts the book gives the reader are set in it.
 */
class FaceSpan(private val face: Typeface) : MetricAffectingSpan() {

    override fun updateDrawState(tp: TextPaint) = apply(tp)

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    private fun apply(tp: TextPaint) {
        tp.typeface = face
    }
}
