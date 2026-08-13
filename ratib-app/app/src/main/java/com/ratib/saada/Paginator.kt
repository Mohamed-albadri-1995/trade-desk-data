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
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/** A renderable block: a section heading, a sub-heading, or a stanza. */
sealed class Block {
    data class Heading(val text: String) : Block()
    data class Subheading(val text: String) : Block()
    data class Body(val text: String) : Block()

    val isNav get() = this is Heading || this is Subheading
}

/**
 * @param pages          main text for each page
 * @param footnotes      footnote text pinned to the bottom of each page (null if none)
 * @param pageStartBlock  block index that begins each page
 * @param headingPage     heading block-index -> the page it lands on
 */
data class Pagination(
    val pages: List<CharSequence>,
    val footnotes: List<CharSequence?>,
    val pageStartBlock: List<Int>,
    val headingPage: Map<Int, Int>
)

/**
 * Cuts the ratib into "book" pages. Whole blocks stay together; a block taller
 * than a page is split by lines. A footnote attached to a block is rendered at
 * the BOTTOM of the page that block lands on, and the main text is kept short
 * enough to leave room for it.
 */
object Paginator {

    private val cueRegex = Regex("\\([^)]*\\)|[0-9\\u0660-\\u0669]+")
    const val LINE_SPACING = 1.5f

    fun paginate(
        context: Context,
        blocks: List<Block>,
        footnotes: Map<Int, String>,
        widthPx: Int,
        heightPx: Int,
        scale: Float
    ): Pagination {
        val dm = context.resources.displayMetrics
        val bodyPx = (21f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val headingPx = (24f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val subheadingPx = (20f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val footnotePx = (15f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val bodyColor = ContextCompat.getColor(context, R.color.reading_text)
        val headingColor = ContextCompat.getColor(context, R.color.heading_text)
        val subheadingColor = ContextCompat.getColor(context, R.color.subheading_text)
        val footnoteColor = ContextCompat.getColor(context, R.color.footnote_text)
        val cueColor = ContextCompat.getColor(context, R.color.marker_color)

        val amiri = runCatching { ResourcesCompat.getFont(context, R.font.amiri) }.getOrNull()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = bodyPx.toFloat()
            color = bodyColor
            if (amiri != null) typeface = amiri
        }
        val w = widthPx.coerceAtLeast(1)
        val limit = heightPx.coerceAtLeast(1)
        val oneLine = (bodyPx * LINE_SPACING).toInt().coerceAtLeast(1)

        fun measure(cs: CharSequence): Int {
            @Suppress("DEPRECATION")
            return StaticLayout(cs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, false).height
        }

        fun center(sb: SpannableStringBuilder) {
            sb.setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        fun buildBlock(b: Block): CharSequence {
            val sb = SpannableStringBuilder()
            when (b) {
                is Block.Heading -> {
                    sb.append("۞  ${b.text}  ۞")
                    sb.setSpan(AbsoluteSizeSpan(headingPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(headingColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    center(sb)
                }
                is Block.Subheading -> {
                    sb.append("﴿ ${b.text} ﴾")
                    sb.setSpan(AbsoluteSizeSpan(subheadingPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(subheadingColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    center(sb)
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
                    val isProse = !b.text.contains('\n') && b.text.length > 55
                    val align = if (isProse) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER
                    sb.setSpan(AlignmentSpan.Standard(align), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return sb
        }

        fun buildFootnote(text: String): CharSequence {
            val sb = SpannableStringBuilder("٭ $text")
            sb.setSpan(AbsoluteSizeSpan(footnotePx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.ITALIC), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(footnoteColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            center(sb)
            return sb
        }

        val pages = ArrayList<CharSequence>()
        val pageFns = ArrayList<CharSequence?>()
        val pageStartBlock = ArrayList<Int>()
        val headingPage = HashMap<Int, Int>()

        var current = SpannableStringBuilder()
        var currentStart = -1
        var currentFn: CharSequence? = null
        var currentFnHeight = 0
        var lastWasNav = false
        val gap = "\n\n"

        fun reserve(fn: CharSequence?, fnH: Int) = if (fn != null) fnH + oneLine else 0

        fun flush() {
            if (current.isNotEmpty()) {
                pages.add(SpannableString(current))
                pageFns.add(currentFn)
                pageStartBlock.add(if (currentStart < 0) 0 else currentStart)
                current = SpannableStringBuilder()
                currentStart = -1
                currentFn = null
                currentFnHeight = 0
            }
        }

        blocks.forEachIndexed { i, b ->
            // Start a section heading on a fresh page (but keep consecutive
            // heading + sub-heading together instead of leaving a heading alone).
            if (b.isNav && current.isNotEmpty() && !lastWasNav) flush()

            val blockCs = buildBlock(b)
            val blockFn = footnotes[i]?.let { buildFootnote(it) }
            val blockFnHeight = if (blockFn != null) measure(blockFn) else 0

            val candidate = SpannableStringBuilder(current)
            if (candidate.isNotEmpty()) candidate.append(gap)
            candidate.append(blockCs)

            val pageFn = currentFn ?: blockFn
            val pageFnHeight = if (currentFn != null) currentFnHeight else blockFnHeight

            if (measure(candidate) <= limit - reserve(pageFn, pageFnHeight)) {
                current = candidate
                if (currentStart < 0) currentStart = i
                if (b.isNav) headingPage[i] = pages.size
                if (currentFn == null && blockFn != null) {
                    currentFn = blockFn; currentFnHeight = blockFnHeight
                }
            } else {
                flush()
                if (measure(blockCs) <= limit - reserve(blockFn, blockFnHeight)) {
                    current = SpannableStringBuilder(blockCs)
                    currentStart = i
                    if (b.isNav) headingPage[i] = pages.size
                    if (blockFn != null) { currentFn = blockFn; currentFnHeight = blockFnHeight }
                } else {
                    if (b.isNav) headingPage[i] = pages.size
                    @Suppress("DEPRECATION")
                    val bl = StaticLayout(blockCs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, false)
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
                            pages.add(chunk); pageFns.add(null); pageStartBlock.add(i)
                        } else {
                            current = SpannableStringBuilder(chunk)
                            currentStart = i
                            if (blockFn != null) { currentFn = blockFn; currentFnHeight = blockFnHeight }
                        }
                        startLine = endLine
                    }
                }
            }
            lastWasNav = b.isNav
        }
        flush()

        if (pages.isEmpty()) {
            pages.add(""); pageFns.add(null); pageStartBlock.add(0)
        }

        return Pagination(pages, pageFns, pageStartBlock, headingPage)
    }
}
