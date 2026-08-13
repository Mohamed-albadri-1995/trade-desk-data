package com.ratib.saada

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** A single renderable block of the ratib: a section heading or a body line. */
sealed class Block {
    data class Heading(val text: String) : Block()
    data class Body(val text: String) : Block()
}

/**
 * Renders the ratib as a list of headings and body lines, with a live font
 * scale the user can grow or shrink.
 */
class RatibAdapter(private val blocks: List<Block>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var scale = 1f

    private val typeHeading = 0
    private val typeBody = 1

    private val baseHeadingSp = 24f
    private val baseBodySp = 21f

    class HeadingVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    class BodyVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun getItemCount() = blocks.size

    override fun getItemViewType(position: Int) =
        if (blocks[position] is Block.Heading) typeHeading else typeBody

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == typeHeading) {
            HeadingVH(inflater.inflate(R.layout.item_heading, parent, false) as TextView)
        } else {
            BodyVH(inflater.inflate(R.layout.item_body, parent, false) as TextView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        when (val b = blocks[position]) {
            is Block.Heading -> (holder as HeadingVH).tv.apply {
                text = "۞  ${b.text}  ۞"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, baseHeadingSp * scale)
            }
            is Block.Body -> (holder as BodyVH).tv.apply {
                text = highlightCues(context, b.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, baseBodySp * scale)
            }
        }
    }

    // Matches parenthetical recitation cues, e.g. (٣) ( ثلاثًا بالمد ) (سورة الفاتحة)
    private val cueRegex = Regex("\\([^)]*\\)")
    private var cueColor: Int? = null

    /** Colours the repetition/instruction cues so they stand apart from the recited text. */
    private fun highlightCues(context: android.content.Context, raw: String): CharSequence {
        val color = cueColor ?: ContextCompat.getColor(context, R.color.marker_color).also { cueColor = it }
        val sb = SpannableStringBuilder(raw)
        for (m in cueRegex.findAll(raw)) {
            val start = m.range.first
            val end = m.range.last + 1
            sb.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }
}
