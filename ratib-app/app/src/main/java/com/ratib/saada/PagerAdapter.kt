package com.ratib.saada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Renders each pre-computed page of the ratib into a centered text view. */
class PagerAdapter(private var pages: List<CharSequence>) :
    RecyclerView.Adapter<PagerAdapter.PageVH>() {

    class PageVH(root: View) : RecyclerView.ViewHolder(root) {
        val tv: TextView = root.findViewById(R.id.pageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
        return PageVH(v)
    }

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.tv.text = pages[position]
    }

    fun submit(newPages: List<CharSequence>) {
        pages = newPages
        notifyDataSetChanged()
    }
}
