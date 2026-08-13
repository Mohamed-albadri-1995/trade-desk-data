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

    /** Must match item_page.xml's lineSpacingMultiplier, or pages mis-measure. */
    const val LINE_SPACING = 1.3f

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
        val oneLine = (bodyPx * LINE_SPACING).toInt().coerceAtLeast(1)
        // Keep one blank line of slack at the bottom so the final rendered line
        // is never clipped (the TextView adds font padding we can't predict
        // exactly), which is what made "the bottom of the page" disappear.
        val limit = (heightPx.coerceAtLeast(1) - oneLine).coerceAtLeast(oneLine)

        fun measure(cs: CharSequence): Int {
            // includePad = true so the measured height matches the padded height
            // the TextView actually draws with.
            @Suppress("DEPRECATION")
            return StaticLayout(cs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true).height
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

        // Pre-build every block once so the look-ahead below is cheap.
        val built = blocks.map { buildBlock(it) }

        /**
         * How much room a heading needs before it is worth starting here: the
         * heading run itself plus two lines of whatever follows. Only two lines
         * — demanding the whole next paragraph fit is what was breaking pages
         * early and leaving them a third empty.
         */
        fun headingGroupHeight(i: Int): Int {
            var j = i
            val group = SpannableStringBuilder()
            while (j < blocks.size && blocks[j].isNav) {
                if (group.isNotEmpty()) group.append(gap)
                group.append(built[j]); j++
            }
            return measure(group) + oneLine + 2 * oneLine
        }

        /**
         * The character offset at which [cs] should be cut so the part that is
         * kept is the most whole lines that fit in [avail]. 0 means not even one
         * line fits.
         */
        fun cutAt(cs: CharSequence, avail: Int): Int {
            if (avail <= 0) return 0
            @Suppress("DEPRECATION")
            val bl = StaticLayout(cs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true)
            var end = 0
            for (line in 0 until bl.lineCount) {
                if (bl.getLineBottom(line) > avail) break
                end = bl.getLineEnd(line)
            }
            return end
        }

        /** Prose may be broken across pages; verse and headings may not. */
        fun isSplittable(b: Block) = b is Block.Body && !b.text.contains('\n') && b.text.length > 55

        var prevWasNav = false

        blocks.forEachIndexed { i, b ->
            // Pages are packed as full as they will go. The only reason to break
            // early is an orphaned heading: if the heading plus a couple of lines
            // of its text cannot fit in what is left, move it to the next page.
            // Skipped right after another heading: the run was already measured
            // as one group, and breaking here would strand that heading.
            if (b.isNav && current.isNotEmpty() && !prevWasNav) {
                val used = measure(current)
                val groupH = headingGroupHeight(i)
                val pageRoom = limit - reserve(currentFn, currentFnHeight)
                if (used + oneLine + groupH > pageRoom && groupH <= pageRoom) flush()
            }
            prevWasNav = b.isNav

            val blockCs = built[i]
            val blockFn = footnotes[i]?.let { buildFootnote(it) }
            val blockFnHeight = if (blockFn != null) measure(blockFn) else 0

            val candidate = SpannableStringBuilder(current)
            if (candidate.isNotEmpty()) candidate.append(gap)
            candidate.append(blockCs)

            val pageFn = currentFn ?: blockFn
            val pageFnHeight = if (currentFn != null) currentFnHeight else blockFnHeight
            val room = limit - reserve(pageFn, pageFnHeight)

            // A long paragraph that does not fit in what is left is not moved
            // whole to the next page (that is what left big gaps at the bottom):
            // it fills this page down to the last line and continues overleaf.
            if (measure(candidate) > room && current.isNotEmpty() && isSplittable(b)) {
                val avail = room - measure(current) - oneLine
                val cut = cutAt(blockCs, avail)
                if (cut > 0) {
                    current.append(gap)
                    current.append(blockCs.subSequence(0, cut))
                    if (currentFn == null && blockFn != null) {
                        currentFn = blockFn; currentFnHeight = blockFnHeight
                    }
                    flush()
                    var rest: CharSequence = blockCs.subSequence(cut, blockCs.length)
                    while (measure(rest) > limit) {
                        val c2 = cutAt(rest, limit)
                        if (c2 <= 0 || c2 >= rest.length) break
                        pages.add(rest.subSequence(0, c2))
                        pageFns.add(null); pageStartBlock.add(i)
                        rest = rest.subSequence(c2, rest.length)
                    }
                    current = SpannableStringBuilder(rest)
                    currentStart = i
                    return@forEachIndexed
                }
            }

            if (measure(candidate) <= room) {
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
                    val bl = StaticLayout(blockCs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true)
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
        }
        flush()

        if (pages.isEmpty()) {
            pages.add(""); pageFns.add(null); pageStartBlock.add(0)
        }

        return Pagination(pages, pageFns, pageStartBlock, headingPage)
    }
}
