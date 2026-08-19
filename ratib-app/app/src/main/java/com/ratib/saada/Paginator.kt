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

    /** Footnotes are set tighter than the body; matches item_page.xml. */
    private const val FOOTNOTE_LINE_SPACING = 1.0f

    /**
     * Fewest lines that may be left on either side when a block is broken over
     * a page. Two means a couplet (two lines) is never halved, while a longer
     * paragraph flows on and fills the page.
     */
    private const val MIN_SPLIT_LINES = 2

    /**
     * How far the الأوراد المربوطة section may be shrunk to keep it on a single
     * page. Below this it stops being readable, and running on to a second page
     * is the better trade.
     */
    private const val TAIL_MIN_SCALE = 0.75f

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
        val footnotePx = (11f * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val bodyColor = ContextCompat.getColor(context, R.color.reading_text)
        val headingColor = ContextCompat.getColor(context, R.color.heading_text)
        val subheadingColor = ContextCompat.getColor(context, R.color.subheading_text)
        val footnoteColor = ContextCompat.getColor(context, R.color.footnote_text)
        val cueColor = ContextCompat.getColor(context, R.color.marker_color)

        val amiri = runCatching { ResourcesCompat.getFont(context, R.font.amiri) }.getOrNull()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            // Base size only. Every character emitted carries an explicit size
            // span, so this applies to nothing but the newlines between blocks;
            // keeping it small stops them from inflating a line on the shrunken
            // الأوراد page. It matches item_page.xml's textSize deliberately.
            textSize = 10f * dm.scaledDensity
            color = bodyColor
            if (amiri != null) typeface = amiri
        }
        val w = widthPx.coerceAtLeast(1)

        fun measure(cs: CharSequence): Int {
            // includePad = true so the measured height matches the padded height
            // the TextView actually draws with.
            @Suppress("DEPRECATION")
            return StaticLayout(cs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true).height
        }

        // Measured, not calculated. textSize × lineSpacing badly understates a
        // real line in Amiri — Arabic ascenders and descenders push the drawn
        // line about a third taller — and every "will two more lines fit" test
        // was working from that too-small figure.
        val oneLine = measure(
            SpannableStringBuilder("ا").apply {
                setSpan(AbsoluteSizeSpan(bodyPx), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        ).coerceAtLeast(1)

        // No guard line here: measurement includes the font padding the view
        // draws with, and the caller already withholds 20dp of its own.
        val limit = heightPx.coerceAtLeast(1)

        /** Whether [text] fits on a single line at [px], set bold as headings are. */
        fun fitsOneLine(text: CharSequence, px: Int): Boolean {
            val tp = TextPaint(paint).apply {
                textSize = px.toFloat()
                typeface = Typeface.create(paint.typeface, Typeface.BOLD)
            }
            @Suppress("DEPRECATION")
            return StaticLayout(
                text, tp, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true
            ).lineCount <= 1
        }

        fun center(sb: SpannableStringBuilder) {
            sb.setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        /**
         * Builds one block. [m] scales every size, which is only ever anything
         * but 1 for the الأوراد المربوطة page, which is squeezed to fit whole.
         *
         * Every heading is set at the same size — no shrinking one to fit. A
         * heading whose ۞ … ۞ frame will not go on a single line is drawn
         * without the ornaments rather than with a broken frame.
         */
        fun buildBlock(b: Block, m: Float = 1f): CharSequence {
            val bPx = (bodyPx * m).toInt().coerceAtLeast(1)
            val hPx = (headingPx * m).toInt().coerceAtLeast(1)
            val sPx = (subheadingPx * m).toInt().coerceAtLeast(1)
            val sb = SpannableStringBuilder()
            when (b) {
                is Block.Heading -> {
                    val framed = "۞  ${b.text}  ۞"
                    sb.append(if (fitsOneLine(framed, hPx)) framed else b.text)
                    sb.setSpan(AbsoluteSizeSpan(hPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(headingColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    center(sb)
                }
                is Block.Subheading -> {
                    val framed = "﴿ ${b.text} ﴾"
                    sb.append(if (fitsOneLine(framed, sPx)) framed else b.text)
                    sb.setSpan(AbsoluteSizeSpan(sPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(subheadingColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    center(sb)
                }
                is Block.Body -> {
                    sb.append(b.text)
                    sb.setSpan(AbsoluteSizeSpan(bPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(bodyColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    for (hit in cueRegex.findAll(b.text)) {
                        val cs = hit.range.first
                        val ce = hit.range.last + 1
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

        /** The footnote is set tighter than the body, so it measures that way. */
        fun measureFootnote(cs: CharSequence): Int {
            @Suppress("DEPRECATION")
            return StaticLayout(
                cs, paint, w, Layout.Alignment.ALIGN_CENTER, FOOTNOTE_LINE_SPACING, 0f, true
            ).height
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

        // No blank line between blocks at all: one block simply starts on the
        // line after the last. Shrinking the blank line was not enough — it is
        // removed outright, so a break costs nothing beyond the line itself.
        val gap: CharSequence = "\n"
        val gapH = 0

        // Room held back for a footnote pinned to the foot of the page: the
        // note itself plus the rule above it and its margins, which is exactly
        // what item_page.xml takes away from the text when a note is shown —
        // no more, since this is charged to the page before the footnoted verse
        // is placed, and any padding here costs that verse its place.
        val footnoteChrome = (13f * dm.density).toInt()
        fun reserve(fn: CharSequence?, fnH: Int) = if (fn != null) fnH + footnoteChrome else 0

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

        // الأوراد المربوطة and everything after it is held back and set as a
        // single page of its own below, shrunk if that is what it takes.
        val tailStart = blocks.indexOfFirst {
            it is Block.Heading && it.text.contains("الأوراد المربوطة")
        }
        val mainEnd = if (tailStart >= 0) tailStart else blocks.size

        // Pre-build every block once so the look-ahead below is cheap.
        val built = (0 until mainEnd).map { buildBlock(blocks[it]) }

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
         * Nudges a page break back to the end of the nearest clause, so a page
         * stops at a completed phrase rather than in the middle of one —
         * ...وجد كل نخارٍ في دينه، not ...وجد كل نخارٍ في. Gives up and keeps
         * the original break if the nearest pause is more than a line back,
         * rather than open a hole at the foot of the page.
         */
        fun clauseCut(cs: CharSequence, cut: Int, avail: Int): Int {
            if (cut <= 0 || cut >= cs.length) return cut
            val pauses = "،؛,.!؟?"
            var p = cut - 1
            while (p > 0) {
                if (cs[p] in pauses) {
                    var e = p + 1
                    while (e < cs.length && cs[e] == ' ') e++
                    if (e >= cs.length) return cut
                    val kept = measure(cs.subSequence(0, e))
                    return if (kept >= avail - oneLine && kept >= MIN_SPLIT_LINES * oneLine) e else cut
                }
                p--
            }
            return cut
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
            while (j < mainEnd && blocks[j].isNav) {
                if (!first) h += gapH
                h += measure(built[j])
                first = false
                j++
            }
            return h + gapH + MIN_SPLIT_LINES * oneLine
        }

        var prevWasNav = false

        (0 until mainEnd).forEach { i ->
            val b = blocks[i]
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
            val blockFnHeight = if (blockFn != null) measureFootnote(blockFn) else 0

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

                // Measured as one piece of text, not as two heights added
                // together: each separate measurement carries its own font
                // padding, and summing them charged the page for padding twice
                // — half a line of imagined height on every test.
                val candidate = SpannableStringBuilder(current)
                if (candidate.isNotEmpty()) candidate.append(gap)
                candidate.append(piece)
                if (measure(candidate) <= room) {
                    take(piece)
                    break
                }

                // Too tall for what is left. Put as many of its lines here as
                // fit and carry the rest over, as long as a sensible amount
                // lands on each side — that keeps a couplet from being halved.
                val avail = room - used - gapBefore
                val (lineCut, fit, total) = splitPoint(piece, avail)
                if (lineCut > 0 && fit >= MIN_SPLIT_LINES && total - fit >= MIN_SPLIT_LINES) {
                    val cut = clauseCut(piece, lineCut, avail)
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

        // الأوراد المربوطة is wanted whole on one page, so it is set on its own
        // and the type shrunk — by as much as half — until all of it fits. This
        // is the one place the text is not at the book's size.
        if (tailStart >= 0) {
            // Build the section at a given size, remembering where each block
            // starts so a heading can be pointed at the page it lands on.
            fun assemble(m: Float): Pair<SpannableStringBuilder, List<Int>> {
                val sb = SpannableStringBuilder()
                val starts = ArrayList<Int>()
                for (i in tailStart until blocks.size) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    starts.add(sb.length)
                    sb.append(buildBlock(blocks[i], m))
                }
                return sb to starts
            }

            // Shrink to get the whole section onto one page, but only as far as
            // it stays comfortably readable. Beyond that it is better to run on
            // to a second page than to set it in type nobody can read — and far
            // better than overflowing the page, which would drop the text off
            // the bottom without a word.
            var m = 1f
            var built = assemble(m)
            while (measure(built.first) > limit && m > TAIL_MIN_SCALE) {
                m -= 0.05f
                built = assemble(m)
            }

            val (tail, starts) = built
            val ranges = ArrayList<Pair<Int, Int>>()   // char range shown per page
            if (measure(tail) <= limit) {
                pages.add(tail); pageFns.add(null); pageStartBlock.add(tailStart)
                ranges.add(0 to tail.length)
            } else {
                var from = 0
                while (from < tail.length) {
                    val rest = tail.subSequence(from, tail.length)
                    val (cut, _, _) = splitPoint(rest, limit)
                    val end = if (cut <= 0) tail.length else from + cut
                    pages.add(tail.subSequence(from, end))
                    pageFns.add(null)
                    pageStartBlock.add(tailStart)
                    ranges.add(from to end)
                    from = end
                }
            }

            // Point each heading at whichever of those pages holds it.
            val firstTailPage = pages.size - ranges.size
            for ((n, i) in (tailStart until blocks.size).withIndex()) {
                if (!blocks[i].isNav) continue
                val at = starts[n]
                val page = ranges.indexOfFirst { at >= it.first && at < it.second }
                headingPage[i] = firstTailPage + (if (page < 0) 0 else page)
            }
        }

        if (pages.isEmpty()) {
            pages.add(""); pageFns.add(null); pageStartBlock.add(0)
        }
        return Pagination(pages, pageFns, pageStartBlock, headingPage)
    }
}
