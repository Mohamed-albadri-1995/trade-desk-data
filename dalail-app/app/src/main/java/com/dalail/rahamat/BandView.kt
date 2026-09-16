package com.dalail.rahamat

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * The ornamental band at the head of a section's first leaf, with the
 * section's name written into its cartouche.
 *
 * The band is the book's own — cut from a printed page — so the cartouche is
 * where the printer put it, and the name is drawn into that rectangle rather
 * than laid over the band by guesswork. Drawing it here, instead of stacking a
 * TextView on an ImageView, is what lets the type be measured against the
 * cartouche and shrunk until it sits inside it.
 */
class BandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** The cartouche, as a fraction of the band, measured on the artwork. */
    private companion object {
        const val PANEL_LEFT = 302f / 817f
        const val PANEL_TOP = 70f / 172f
        const val PANEL_RIGHT = 518f / 817f
        const val PANEL_BOTTOM = 148f / 172f
        /** The band's own proportions, so its height follows its width. */
        const val ASPECT = 172f / 817f
        /** Room left inside the cartouche so the name never touches its edge. */
        const val INSET = 0.09f
    }

    private val band = BitmapFactory.decodeResource(resources, R.drawable.header_band)
    private val src = Rect(0, 0, band.width, band.height)
    private val dst = RectF()
    private val panel = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.amiri) ?: Typeface.SERIF
        color = ContextCompat.getColor(context, R.color.reading_text)
        textAlign = Paint.Align.CENTER
    }

    var title: String = ""
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * ASPECT).toInt().coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(band, src, dst, null)
        if (title.isEmpty()) return

        panel.set(
            width * PANEL_LEFT, height * PANEL_TOP,
            width * PANEL_RIGHT, height * PANEL_BOTTOM
        )
        // The cartouche is repainted before the name goes in: the artwork's own
        // panel carries the section name of the page it was cut from.
        fill.color = ContextCompat.getColor(context, R.color.reading_bg)
        canvas.drawRect(panel, fill)

        val room = panel.width() * (1f - 2 * INSET)
        var size = panel.height() * 0.62f
        ink.textSize = size
        while (size > 4f && ink.measureText(title) > room) {
            size -= 1f
            ink.textSize = size
        }
        // Centred on the cartouche by its own metrics, not by its box, so the
        // descenders of a name like الحِزْبُ do not push it off centre.
        val fm = ink.fontMetrics
        val baseline = panel.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(title, panel.centerX(), baseline, ink)
    }
}
