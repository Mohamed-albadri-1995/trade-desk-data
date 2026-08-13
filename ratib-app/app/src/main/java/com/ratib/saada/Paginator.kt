package com.ratib.saada

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat

/** A renderable block of the ratib: a section heading or a body line. */
sealed class Block {
    data class Heading(val text: String) : Block()
    data class Body(val text: String) : Block()
}

/**
 * Result of laying the whole ratib out and cutting it into screen-sized pages.
 * @param pages    styled text for each page
 * @param starts   character offset where each page begins (used to keep the
 *                 reader's place when the font size changes)
 * @param headingPage  block-index of each heading -> the page it lands on
 */
data class Pagination(
    val pages: List<CharSequence>,
    val starts: List<Int>,
    val headingPage: Map<Int, Int>
)

/**
 * Turns the continuous ratib text into fixed "book" pages that each fill one
 * screen at the current font size — measured with the same StaticLayout the
 * page views use, so every page fits without scrolling.
 */
object Paginator {

    private val cueRegex = Regex("\\([^)]*\\)")

    fun paginate(
        context: Context,
        blocks: List<Block>,
        widthPx: Int,
        heightPx: Int,
        scale: Float
    ): Pagination {
        val dm = context.resources.displayMetrics
        val bodyPx = (21f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val headingPx = (24f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val bodyColor = ContextCompat.getColor(context, R.color.reading_text)
        val headingColor = ContextCompat.getColor(context, R.color.heading_text)
        val cueColor = ContextCompat.getColor(context, R.color.marker_color)

        val sb = SpannableStringBuilder()
        val headingOffsets = ArrayList<Pair<Int, Int>>()

        blocks.forEachIndexed { idx, b ->
            if (sb.isNotEmpty()) sb.append(if (b is Block.Heading) "\n\n\n" else "\n\n")
            val start = sb.length
            when (b) {
                is Block.Heading -> {
                    sb.append("۞  ${b.text}  ۞")
                    sb.setSpan(AbsoluteSizeSpan(headingPx), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(headingColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    headingOffsets.add(idx to start)
                }
                is Block.Body -> {
                    sb.append(b.text)
                    sb.setSpan(AbsoluteSizeSpan(bodyPx), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(bodyColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    for (m in cueRegex.findAll(b.text)) {
                        val cs = start + m.range.first
                        val ce = start + m.range.last + 1
                        sb.setSpan(ForegroundColorSpan(cueColor), cs, ce, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(StyleSpan(Typeface.BOLD), cs, ce, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = bodyPx.toFloat()
            color = bodyColor
        }
        val w = widthPx.coerceAtLeast(1)

        @Suppress("DEPRECATION")
        val layout = StaticLayout(sb, paint, w, Layout.Alignment.ALIGN_CENTER, 1.4f, 0f, false)

        val pages = ArrayList<CharSequence>()
        val starts = ArrayList<Int>()
        val pageBounds = ArrayList<IntArray>()
        val limit = heightPx.coerceAtLeast(1)
        val lineCount = layout.lineCount

        var startLine = 0
        while (startLine < lineCount) {
            val top = layout.getLineTop(startLine)
            var endLine = startLine
            while (endLine < lineCount && layout.getLineBottom(endLine) - top <= limit) endLine++
            if (endLine == startLine) endLine = startLine + 1
            val cs = layout.getLineStart(startLine)
            val ce = layout.getLineEnd(endLine - 1)
            pages.add(sb.subSequence(cs, ce))
            starts.add(cs)
            pageBounds.add(intArrayOf(cs, ce))
            startLine = endLine
        }
        if (pages.isEmpty()) {
            pages.add(""); starts.add(0); pageBounds.add(intArrayOf(0, 0))
        }

        val headingPage = HashMap<Int, Int>()
        for ((blockIdx, off) in headingOffsets) {
            var p = pageBounds.indexOfFirst { off >= it[0] && off < it[1] }
            if (p < 0) p = 0
            headingPage[blockIdx] = p
        }

        return Pagination(pages, starts, headingPage)
    }
}
