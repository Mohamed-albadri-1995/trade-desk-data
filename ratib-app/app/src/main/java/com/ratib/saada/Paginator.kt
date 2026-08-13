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

    /**
     * Fewest lines that may be left on either side when a block is broken over
     * a page. Two means a couplet (two lines) is never halved, while a longer
     * paragraph flows on and fills the page.
     */
    private const val MIN_SPLIT_LINES = 2

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

        // The blank line between paragraphs used to be a full-height line, which
        // ate roughly a quarter of every page. It is kept as a visible breath
        // between stanzas but at under half that height.
        val gapPx = (bodyPx * 0.45f).toInt().coerceAtLeast(1)
        val gap: CharSequence = SpannableString("\n\n").apply {
            setSpan(AbsoluteSizeSpan(gapPx), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val gapH = (gapPx * LINE_SPACING).toInt().coerceAtLeast(1)

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
         * Where [cs] would have to be cut to fill [avail]: the character offset
         * after the last line that fits, how many lines that is, and how many
         * lines the whole thing has.
         */
        fun splitPoint(cs: CharSequence, avail: Int): Triple<Int, Int, Int> {
            @Suppress("DEPRECATION")
            val bl = StaticLayout(cs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true)
            val total = bl.lineCount
            if (avail <= 0) return Triple(0, 0, total)
            var end = 0
            var fit = 0
            for (line in 0 until total) {
                if (bl.getLineBottom(line) > avail) break
                end = bl.getLineEnd(line)
                fit = line + 1
            }
            return Triple(end, fit, total)
        }

        /**
         * Room a heading needs to be worth starting on this page: the run of
         * headings itself plus two lines of the text underneath. Two lines only
         * — asking for the whole following paragraph is what used to break
         * pages early and leave them a third empty.
         */
        fun navRunNeed(i: Int): Int {
            var j = i
            var h = 0
            var first = true
            while (j < blocks.size && blocks[j].isNav) {
                if (!first) h += gapH
                h += measure(built[j])
                first = false
                j++
            }
            return h + gapH + MIN_SPLIT_LINES * oneLine
        }

        var prevWasNav = false

        blocks.forEachIndexed { i, b ->
            // A heading left alone at the foot of a page, with its text starting
            // overleaf, reads as a mistake. If it and a couple of its lines will
            // not fit in what is left, start it on the next page instead. Not
            // applied straight after another heading: that run travels together.
            if (b.isNav && current.isNotEmpty() && !prevWasNav) {
                val used = measure(current)
                val pageRoom = limit - reserve(currentFn, currentFnHeight)
                if (used + gapH + navRunNeed(i) > pageRoom) flush()
            }
            prevWasNav = b.isNav

            val blockFn = footnotes[i]?.let { buildFootnote(it) }
            val blockFnHeight = if (blockFn != null) measure(blockFn) else 0

            // Whatever is still left of this block to place. A page is only ever
            // ended because it ran out of room, never because of what comes next,
            // so every page is filled right down to its last line.
            var piece: CharSequence = built[i]

            while (true) {
                val pageFn = currentFn ?: blockFn
                val pageFnHeight = if (currentFn != null) currentFnHeight else blockFnHeight
                val room = limit - reserve(pageFn, pageFnHeight)
                val used = if (current.isEmpty()) 0 else measure(current)
                val gapBefore = if (current.isEmpty()) 0 else gapH

                fun take(cs: CharSequence) {
                    if (current.isEmpty()) currentStart = i else current.append(gap)
                    current.append(cs)
                    if (b.isNav) headingPage[i] = pages.size
                    if (currentFn == null && blockFn != null) {
                        currentFn = blockFn; currentFnHeight = blockFnHeight
                    }
                }

                if (used + gapBefore + measure(piece) <= room) {
                    take(piece)
                    break
                }

                // Too tall for what is left. Put as many of its lines here as
                // fit and carry the rest over, as long as a sensible amount
                // lands on each side — that keeps a couplet from being halved.
                val (cut, fit, total) = splitPoint(piece, room - used - gapBefore)
                if (cut > 0 && fit >= MIN_SPLIT_LINES && total - fit >= MIN_SPLIT_LINES) {
                    take(piece.subSequence(0, cut))
                    flush()
                    piece = piece.subSequence(cut, piece.length)
                    continue
                }

                if (current.isNotEmpty()) {
                    // Try again with a whole empty page underneath it.
                    flush()
                    continue
                }

                // Taller than an entire empty page and not splittable on the
                // rules above: cut it hard so we always make progress.
                val (hard, _, _) = splitPoint(piece, room)
                if (hard <= 0 || hard >= piece.length) {
                    take(piece)
                    break
                }
                take(piece.subSequence(0, hard))
                flush()
                piece = piece.subSequence(hard, piece.length)
            }
        }
        flush()

        if (pages.isEmpty()) {
            pages.add(""); pageFns.add(null); pageStartBlock.add(0)
        }

        return Pagination(pages, pageFns, pageStartBlock, headingPage)
    }
}
