package com.dalail.rahamat

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Hands the pager one leaf at a time.
 *
 * A leaf is decoded when it comes into view and let go when it leaves, so the
 * book costs the memory of the few leaves on screen rather than of all
 * hundred and thirteen.
 */
class PageAdapter(private val onTap: () -> Unit) :
    RecyclerView.Adapter<PageAdapter.LeafVH>() {

    class LeafVH(root: View) : RecyclerView.ViewHolder(root) {
        val image: ZoomImageView = root.findViewById(R.id.leaf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LeafVH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_leaf, parent, false)
    )

    override fun getItemCount() = Book.pageCount()

    override fun onBindViewHolder(holder: LeafVH, position: Int) {
        val assets = holder.itemView.context.assets
        val bitmap = runCatching {
            assets.open(Book.asset(position)).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        holder.image.setImageBitmap(bitmap)
        holder.image.reset()
        holder.image.setOnClickListener { onTap() }
    }

    override fun onViewRecycled(holder: LeafVH) {
        holder.image.setImageDrawable(null)
    }
}
