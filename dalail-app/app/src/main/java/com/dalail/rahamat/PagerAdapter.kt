package com.dalail.rahamat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Draws each leaf: the ornamental band where a section opens, otherwise the
 * running head, then the column of text, and the book's closing line at the
 * foot of the last leaf.
 */
class PagerAdapter(
    private var pages: List<CharSequence> = emptyList(),
    private var heads: List<String> = emptyList(),
    private var opens: List<Boolean> = emptyList(),
    private var colophons: List<CharSequence?> = emptyList()
) : RecyclerView.Adapter<PagerAdapter.LeafVH>() {

    class LeafVH(root: View) : RecyclerView.ViewHolder(root) {
        val band: BandView = root.findViewById(R.id.band)
        val head: TextView = root.findViewById(R.id.runningHead)
        val text: TextView = root.findViewById(R.id.pageText)
        val colophon: TextView = root.findViewById(R.id.colophonText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LeafVH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
    )

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(holder: LeafVH, position: Int) {
        holder.text.text = pages[position]

        val head = heads.getOrNull(position).orEmpty()
        val wearsBand = opens.getOrNull(position) == true
        holder.band.title = head
        holder.band.visibility = if (wearsBand) View.VISIBLE else View.GONE
        // The running head keeps its room even where there is nothing to say,
        // so that a leaf without one is not a line taller than the rest — which
        // is what the paginator measured against.
        holder.head.text = head
        holder.head.visibility = when {
            wearsBand -> View.GONE
            head.isEmpty() -> View.INVISIBLE
            else -> View.VISIBLE
        }

        val end = colophons.getOrNull(position)
        holder.colophon.text = end ?: ""
        holder.colophon.visibility = if (end != null) View.VISIBLE else View.GONE
    }

    fun submit(p: Pagination) {
        pages = p.pages
        heads = p.heads
        opens = p.opensSection
        colophons = p.colophons
        notifyDataSetChanged()
    }
}
