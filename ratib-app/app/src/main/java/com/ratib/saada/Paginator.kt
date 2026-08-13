package com.ratib.saada

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat

/** A renderable block: a section heading, a sub-heading, or a stanza. */
sealed class Block {
    data class Heading(val text: String) : Block()
    data class Subheading(val text: String) : Block()
    data class Body(val text: String) : Block()

    val isNav get() = this is Heading || this is Subheading
}

/**
 * @param pages          styled text for each page
 * @param pageStartBlock  block index that begins each page (used to keep place on font change)
 * @param headingPage     heading block-index -> the page it lands on
 */
data class Pagination(
    val pages: List<CharSequence>,
    val pageStartBlock: List<Int>,
    val headingPage: Map<Int, Int>
)

/**
 * Cuts the ratib into "book" pages. Whole blocks (a couplet, a salawat, a
 * heading) are kept together on a page and never split across a page turn;
 * only a block taller than a whole page (long prose) is split by lines.
 */
object Paginator {

    // Parenthetical cues like (٣) ( ثلاثًا ) (سورة الفاتحة), or a bare number (١٠٠، ١٢٩…).
    private val cueRegex = Regex("\\([^)]*\\)|[0-9\\u0660-\\u0669]+")

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
        val subheadingPx = (20f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val bodyColor = ContextCompat.getColor(context, R.color.reading_text)
        val headingColor = ContextCompat.getColor(context, R.color.heading_text)
        val subheadingColor = ContextCompat.getColor(context, R.color.subheading_text)
        val cueColor = ContextCompat.getColor(context, R.color.marker_color)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = bodyPx.toFloat()
            color = bodyColor
        }
        val w = widthPx.coerceAtLeast(1)
        val limit = heightPx.coerceAtLeast(1)

        fun measure(cs: CharSequence): Int {
            @Suppress("DEPRECATION")
            return StaticLayout(cs, paint, w, Layout.Alignment.ALIGN_CENTER, 1.4f, 0f, false).height
        }

        fun buildBlock(b: Block): CharSequence {
            val sb = SpannableStringBuilder()
            when (b) {
                is Block.Heading -> {
                    sb.append("۞  ${b.text}  ۞")
                    sb.setSpan(AbsoluteSizeSpan(headingPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(headingColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is Block.Subheading -> {
                    sb.append("﴿ ${b.text} ﴾")
                    sb.setSpan(AbsoluteSizeSpan(subheadingPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(subheadingColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is Block.Body -> {
                    sb.append(b.text)
                    sb.setSpan(AbsoluteSizeSpan(bodyPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(bodyColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    for (m in cueRegex.findAll(b.text)) {
                        val cs = m.range.first
                        val ce = m.range.last + 1
                        sb.setSpan(ForegroundColorSpan(cueColor), cs, ce, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(StyleSpan(Typeface.BOLD), cs, ce, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            return sb
        }

        val pages = ArrayList<CharSequence>()
        val pageStartBlock = ArrayList<Int>()
        val headingPage = HashMap<Int, Int>()

        var current = SpannableStringBuilder()
        var currentStartBlock = -1
        val gap = "\n\n"

        fun flush() {
            if (current.isNotEmpty()) {
                pages.add(SpannableString(current))
                pageStartBlock.add(if (currentStartBlock < 0) 0 else currentStartBlock)
                current = SpannableStringBuilder()
                currentStartBlock = -1
            }
        }

        blocks.forEachIndexed { i, b ->
            val blockCs = buildBlock(b)

            val candidate = SpannableStringBuilder(current)
            if (candidate.isNotEmpty()) candidate.append(gap)
            candidate.append(blockCs)

            if (measure(candidate) <= limit) {
                current = candidate
                if (currentStartBlock < 0) currentStartBlock = i
                if (b.isNav) headingPage[i] = pages.size
            } else {
                flush()
                if (measure(blockCs) <= limit) {
                    current = SpannableStringBuilder(blockCs)
                    currentStartBlock = i
                    if (b.isNav) headingPage[i] = pages.size
                } else {
                    // Block taller than a full page (long prose): split by lines.
                    if (b.isNav) headingPage[i] = pages.size
                    @Suppress("DEPRECATION")
                    val bl = StaticLayout(blockCs, paint, w, Layout.Alignment.ALIGN_CENTER, 1.4f, 0f, false)
                    val lc = bl.lineCount
                    var startLine = 0
                    while (startLine < lc) {
                        val top = bl.getLineTop(startLine)
                        var endLine = startLine
                        while (endLine < lc && bl.getLineBottom(endLine) - top <= limit) endLine++
                        if (endLine == startLine) endLine = startLine + 1
                        val cs = bl.getLineStart(startLine)
                        val ce = bl.getLineEnd(endLine - 1)
                        val chunk = blockCs.subSequence(cs, ce)
                        if (endLine < lc) {
                            pages.add(chunk)
                            pageStartBlock.add(i)
                        } else {
                            current = SpannableStringBuilder(chunk)
                            currentStartBlock = i
                        }
                        startLine = endLine
                    }
                }
            }
        }
        flush()

        if (pages.isEmpty()) {
            pages.add(""); pageStartBlock.add(0)
        }

        return Pagination(pages, pageStartBlock, headingPage)
    }
}
