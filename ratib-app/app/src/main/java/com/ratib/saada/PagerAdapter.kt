package com.ratib.saada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Renders each page: main text on top, then anything pinned to the foot of the
 * page — the book's closing line, and a footnote — in that order.
 */
class PagerAdapter(
    private var pages: List<CharSequence>,
    private var footnotes: List<CharSequence?>,
    private var colophons: List<CharSequence?> = emptyList()
) : RecyclerView.Adapter<PagerAdapter.PageVH>() {

    class PageVH(root: View) : RecyclerView.ViewHolder(root) {
        val tv: TextView = root.findViewById(R.id.pageText)
        val colophon: TextView = root.findViewById(R.id.colophonText)
        val rule: View = root.findViewById(R.id.footnoteRule)
        val fn: TextView = root.findViewById(R.id.footnoteText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
        return PageVH(v)
    }

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.tv.text = pages[position]
        val end = colophons.getOrNull(position)
        holder.colophon.text = end ?: ""
        holder.colophon.visibility = if (end != null) View.VISIBLE else View.GONE
        val note = footnotes.getOrNull(position)
        if (note != null) {
            holder.fn.text = note
            holder.fn.visibility = View.VISIBLE
            holder.rule.visibility = View.VISIBLE
        } else {
            holder.fn.visibility = View.GONE
            holder.rule.visibility = View.GONE
        }
    }

    fun submit(
        newPages: List<CharSequence>,
        newFootnotes: List<CharSequence?>,
        newColophons: List<CharSequence?>
    ) {
        pages = newPages
        footnotes = newFootnotes
        colophons = newColophons
        notifyDataSetChanged()
    }
}
