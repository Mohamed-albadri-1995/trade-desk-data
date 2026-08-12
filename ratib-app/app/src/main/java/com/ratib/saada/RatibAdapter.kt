package com.ratib.saada

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
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
        when (val b = blocks[position]) {
            is Block.Heading -> (holder as HeadingVH).tv.apply {
                text = b.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, baseHeadingSp * scale)
            }
            is Block.Body -> (holder as BodyVH).tv.apply {
                text = b.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, baseBodySp * scale)
            }
        }
    }
}
