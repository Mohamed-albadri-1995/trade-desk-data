package com.dalail.rahamat

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/** A piece of the book, as the reader sets it. */
sealed class Block {
    /** The book's name, alone on the leaf the reader opens at. */
    data class Title(val text: String) : Block()

    /**
     * A section — المقدمة, a حزب, دعوة الصلوات. A section always begins a leaf
     * of its own, and that leaf wears the ornamental band with this name in it.
     */
    data class Section(val text: String) : Block()

    /**
     * A صلاة, marked as the book marks it: by its number alone. [suras] names
     * the suras it is built on, which the index shows but the page does not.
     */
    data class Salat(val number: String, val suras: String) : Block()

    data class Body(val text: String) : Block()

    /** The book's closing line, at the foot of its last leaf. */
    data class Colophon(val text: String) : Block()

    /** Whether this block is worth an entry in the index. */
    val isNav get() = this is Title || this is Section || this is Salat
}

/**
 * @param pages     the text of each leaf
 * @param heads     the section each leaf belongs to, for its running head
 * @param opensSection  whether a leaf is the first of its section, and so
 *                      wears the ornamental band instead of the running head
 * @param colophons the closing line pinned to a leaf's foot, or null
 * @param navPage   block index -> the leaf it landed on
 */
data class Pagination(
    val pages: List<CharSequence>,
    val heads: List<String>,
    val opensSection: List<Boolean>,
    val colophons: List<CharSequence?>,
    val navPage: Map<Int, Int>
)

/**
 * Cuts دلائل الرحمات into leaves that fit the screen.
 *
 * A section always opens a leaf of its own; inside it, whole blocks are kept
 * together where they fit and a block taller than a leaf is broken by lines,
 * at a rosette where there is one within reach. Nothing is ever broken because
 * of what comes next, so every leaf is filled down to its last line.
 */
object Paginator {

    /** Must match item_page.xml's lineSpacingMultiplier, or leaves mis-measure. */
    const val LINE_SPACING = 1.32f

    /**
     * Fewest lines that may be left on either side when a block is broken over
     * a leaf, so a couple of words are never stranded alone.
     */
    private const val MIN_SPLIT_LINES = 2

    /**
     * A passage this short is a line the book sets centred and alone —
     * البسملة, a rosette divider, the last words of a دعاء. Anything longer is
     * running prose, justified to the column.
     */
    private const val CENTRED_UP_TO = 55

    /** How large the rosette is drawn beside the type it stands in. */
    private const val ROSETTE_RATIO = 0.78f

    /** An instruction to the reader: how often a passage is said. */
    private const val ASIDE_OPEN = '⟨'
    private const val ASIDE_CLOSE = '⟩'

    /** Qur'an, or a sura's name. */
    private const val QURAN_OPEN = '﴿'
    private const val QURAN_CLOSE = '﴾'

    /** The book's own separator between one invocation and the next. */
    private val ROSETTES = charArrayOf('♡', '۞', '♥', '❤')

    /**
     * @param widthPx   the column the text is set in
     * @param heightPx  the room a leaf has for text, with the running head's
     *                  own line already taken off
     * @param bandExtra how much more than the running head the ornamental band
     *                  takes, charged to the leaf that opens a section
     */
    fun paginate(
        context: Context,
        blocks: List<Block>,
        widthPx: Int,
        heightPx: Int,
        bandExtra: Int,
        scale: Float
    ): Pagination {
        val dm = context.resources.displayMetrics
        fun sp(v: Float) = (v * scale * dm.scaledDensity).toInt().coerceAtLeast(1)
        val bodyPx = sp(19f)
        val titlePx = sp(32f)
        val salatPx = sp(21f)

        val ink = ContextCompat.getColor(context, R.color.reading_text)
        val headingInk = ContextCompat.getColor(context, R.color.heading_text)
        val quranInk = ContextCompat.getColor(context, R.color.quran_text)
        val asideInk = ContextCompat.getColor(context, R.color.aside_text)

        val amiri = runCatching { ResourcesCompat.getFont(context, R.font.amiri) }.getOrNull()
        val italic = runCatching { ResourcesCompat.getFont(context, R.font.amiri_italic) }.getOrNull()

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            // A base size only: every character carries an explicit size span,
            // so this applies to nothing but the newlines between blocks. It
            // matches item_page.xml's textSize deliberately.
            textSize = 10f * dm.scaledDensity
            color = ink
            if (amiri != null) typeface = amiri
        }
        val w = widthPx.coerceAtLeast(1)
        val limit = heightPx.coerceAtLeast(1)

        /** The slanted face, or a synthesised slant where it is missing. */
        fun slant(sb: SpannableStringBuilder, from: Int, to: Int) {
            val span = if (italic != null) FaceSpan(italic) else StyleSpan(Typeface.ITALIC)
            sb.setSpan(span, from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // The rosette, sized to the type it stands in. One drawable serves every
        // span: they are all in body text, so they are all the same size.
        val rosettePx = (bodyPx * ROSETTE_RATIO).toInt().coerceAtLeast(1)
        val rosette: Drawable? =
            ContextCompat.getDrawable(context, R.drawable.rosette)?.apply {
                setBounds(0, 0, rosettePx, rosettePx)
            }

        fun measure(cs: CharSequence): Int {
            // includePad = true so the measured height matches the padded height
            // the TextView actually draws with.
            @Suppress("DEPRECATION")
            return StaticLayout(
                cs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true
            ).height
        }

        // Measured, not calculated. textSize × lineSpacing badly understates a
        // real line in Amiri — Arabic ascenders and descenders push the drawn
        // line about a third taller — and every "will two more lines fit" test
        // would work from that too-small figure.
        val oneLine = measure(
            SpannableStringBuilder("ا").apply {
                setSpan(AbsoluteSizeSpan(bodyPx), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        ).coerceAtLeast(1)

        fun centre(sb: SpannableStringBuilder) {
            sb.setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        /**
         * Appends a passage, reading the book's own marks as it goes: a run
         * between ⟨ and ⟩ is an instruction to the reader and is set slanted in
         * gold, a run between ﴿ and ﴾ is Qur'an or a sura's name and is set in
         * turquoise, and a ♡ is drawn as the book's own rosette. None of the
         * marks themselves is shown.
         */
        fun passage(sb: SpannableStringBuilder, text: String, px: Int) {
            val from = sb.length
            var open: Char? = null
            var runFrom = -1

            fun closeRun(colour: Int, slanted: Boolean) {
                if (runFrom in 0 until sb.length) {
                    sb.setSpan(
                        ForegroundColorSpan(colour), runFrom, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    if (slanted) slant(sb, runFrom, sb.length)
                }
                runFrom = -1
                open = null
            }

            for (c in text) {
                when {
                    c == ASIDE_OPEN || c == QURAN_OPEN -> {
                        open = c
                        runFrom = sb.length
                    }
                    c == ASIDE_CLOSE && open == ASIDE_OPEN -> closeRun(asideInk, true)
                    c == QURAN_CLOSE && open == QURAN_OPEN -> closeRun(quranInk, false)
                    c in ROSETTES && rosette != null -> {
                        val at = sb.length
                        sb.append(c)
                        sb.setSpan(
                            ImageSpan(rosette, ImageSpan.ALIGN_BASELINE),
                            at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    else -> sb.append(c)
                }
            }
            // A mark left open by a slip in the text must not swallow the rest
            // of the passage, so any run still open is closed at its end.
            when (open) {
                ASIDE_OPEN -> closeRun(asideInk, true)
                QURAN_OPEN -> closeRun(quranInk, false)
            }

            sb.setSpan(AbsoluteSizeSpan(px), from, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val align =
                if (text.length <= CENTRED_UP_TO) Layout.Alignment.ALIGN_CENTER
                else Layout.Alignment.ALIGN_NORMAL
            sb.setSpan(
                AlignmentSpan.Standard(align), from, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        fun buildBlock(b: Block): CharSequence {
            val sb = SpannableStringBuilder()
            when (b) {
                is Block.Title -> {
                    sb.append(b.text)
                    sb.setSpan(AbsoluteSizeSpan(titlePx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(headingInk), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    centre(sb)
                }
                // A section's name is in the band at the head of its leaf, so
                // nothing of it goes into the column.
                is Block.Section -> Unit
                is Block.Salat -> {
                    // The number alone, as the book prints it, in the slanted
                    // face the instructions to the reader wear.
                    sb.append(b.number)
                    sb.setSpan(AbsoluteSizeSpan(salatPx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(asideInk), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    slant(sb, 0, sb.length)
                    centre(sb)
                }
                is Block.Body -> passage(sb, b.text, bodyPx)
                // Never reached: the closing line is held out of the flow and
                // pinned to the foot of the last leaf. Set here as it is set
                // there, so it cannot go astray.
                is Block.Colophon -> {
                    sb.append(b.text)
                    sb.setSpan(AbsoluteSizeSpan(titlePx), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(quranInk), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    centre(sb)
                }
            }
            return sb
        }

        // The closing line stands at the foot of the last leaf, with room kept
        // for it there rather than being set into the column.
        val colophon: CharSequence? =
            (blocks.firstOrNull { it is Block.Colophon } as? Block.Colophon)?.let {
                SpannableStringBuilder(it.text).apply {
                    setSpan(AbsoluteSizeSpan(titlePx), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(quranInk), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    centre(this)
                }
            }
        // Its own height plus the margin item_page leaves above it.
        val colophonReserve =
            if (colophon != null) measure(colophon) + (10f * dm.density).toInt() else 0
        // Which blocks are in the book's last section, and so must leave the
        // closing line its room. Found once, not asked per block.
        val tailFrom = blocks.indexOfLast { it is Block.Section }

        val pages = ArrayList<CharSequence>()
        val heads = ArrayList<String>()
        val opens = ArrayList<Boolean>()
        val navPage = HashMap<Int, Int>()

        // No blank line between blocks: one simply starts on the line after the
        // last, so a break costs nothing beyond the line itself.
        val gap: CharSequence = "\n"

        var current = SpannableStringBuilder()
        var head = ""
        // The leaf the reader opens at carries the book's name and no furniture.
        var wearsBand = false
        var bandRoom = 0

        fun flush() {
            if (current.isEmpty()) return
            pages.add(SpannableString(current))
            heads.add(head)
            opens.add(wearsBand)
            current = SpannableStringBuilder()
            wearsBand = false
            bandRoom = 0
        }

        /**
         * Where [cs] would have to be cut to fill [avail]: the offset after the
         * last line that fits, how many lines that is, and how many it has.
         */
        fun splitPoint(cs: CharSequence, avail: Int): Triple<Int, Int, Int> {
            @Suppress("DEPRECATION")
            val bl = StaticLayout(
                cs, paint, w, Layout.Alignment.ALIGN_CENTER, LINE_SPACING, 0f, true
            )
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
         * Nudges a break back to where the book itself pauses — at a rosette,
         * or failing that a comma — so a leaf does not stop in the middle of an
         * invocation. Gives up if the nearest pause is more than a line back,
         * rather than open a hole at the foot of the leaf.
         */
        fun pauseCut(cs: CharSequence, cut: Int, avail: Int): Int {
            if (cut <= 0 || cut >= cs.length) return cut
            val pauses = "،؛,.!؟?"
            var p = cut - 1
            while (p > 0) {
                if (cs[p] in ROSETTES || cs[p] in pauses) {
                    var e = p + 1
                    while (e < cs.length && cs[e] == ' ') e++
                    if (e >= cs.length) return cut
                    val kept = measure(cs.subSequence(0, e))
                    return if (kept >= avail - oneLine && kept >= MIN_SPLIT_LINES * oneLine)
                        e else cut
                }
                p--
            }
            return cut
        }

        val built = blocks.map { buildBlock(it) }

        blocks.forEachIndexed { i, b ->
            if (b is Block.Colophon) return@forEachIndexed

            if (b is Block.Section) {
                // A section opens a leaf of its own, and that leaf wears the
                // band, which costs it the room the running head would not have.
                flush()
                head = b.text
                wearsBand = true
                bandRoom = bandExtra
                navPage[i] = pages.size
                return@forEachIndexed
            }

            var piece: CharSequence = built[i]
            if (piece.isEmpty()) return@forEachIndexed

            val roomOnLeaf =
                (if (tailFrom >= 0 && i > tailFrom) limit - colophonReserve else limit)

            // A صلاة's number left alone at the foot of a leaf, with the صلاة
            // itself starting overleaf, reads as a mistake. If it and a couple
            // of the lines under it will not fit, start it on the next leaf.
            if (b is Block.Salat && current.isNotEmpty()) {
                val need = measure(piece) + MIN_SPLIT_LINES * oneLine
                if (measure(current) + need > roomOnLeaf - bandRoom) flush()
            }

            while (true) {
                val room = roomOnLeaf - bandRoom
                val used = if (current.isEmpty()) 0 else measure(current)

                fun take(cs: CharSequence) {
                    if (current.isNotEmpty()) current.append(gap)
                    current.append(cs)
                    if (b.isNav && !navPage.containsKey(i)) navPage[i] = pages.size
                }

                // Measured as one piece of text, not as two heights added
                // together: each separate measurement carries its own font
                // padding, and summing them would charge the leaf for it twice.
                val candidate = SpannableStringBuilder(current)
                if (candidate.isNotEmpty()) candidate.append(gap)
                candidate.append(piece)
                if (measure(candidate) <= room) {
                    take(piece)
                    break
                }

                // Too tall for what is left. Put as many of its lines here as
                // fit and carry the rest over, as long as a sensible amount
                // lands on each side.
                val avail = room - used
                val (lineCut, fit, total) = splitPoint(piece, avail)
                if (lineCut > 0 && fit >= MIN_SPLIT_LINES && total - fit >= MIN_SPLIT_LINES) {
                    val cut = pauseCut(piece, lineCut, avail)
                    take(piece.subSequence(0, cut))
                    flush()
                    piece = piece.subSequence(cut, piece.length)
                    continue
                }

                if (current.isNotEmpty()) {
                    // Try again with a whole empty leaf underneath it.
                    flush()
                    continue
                }

                // Taller than an entire empty leaf and not splittable on the
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
            pages.add(""); heads.add(""); opens.add(false)
        }
        // The closing line belongs to the last leaf, whichever that turned out.
        val pageColophons = arrayOfNulls<CharSequence>(pages.size)
        if (colophon != null) pageColophons[pages.size - 1] = colophon

        // A section whose text all fell to a later leaf, or a صلاة at the very
        // end, can point past the book; bring any such entry back to the last
        // leaf. Collected first — the map cannot be written while it is read.
        val past = navPage.filterValues { it >= pages.size }.keys
        for (block in past) navPage[block] = pages.size - 1

        return Pagination(pages, heads, opens, pageColophons.toList(), navPage)
    }
}
