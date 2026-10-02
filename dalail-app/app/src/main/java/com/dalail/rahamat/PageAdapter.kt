package com.dalail.rahamat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Hands the pager one leaf at a time.
 *
 * A leaf is decoded when it comes into view and let go when it leaves, so the
 * book costs the memory of the few leaves on screen rather than of all its
 * hundred and fourteen.
 *
 * It used to decode in one line and keep nothing if that line came back null —
 * so a leaf that would not decode showed as an empty board, and the only thing
 * a reader could say about it was that the app was blank. Now the leaf is
 * asked for in stages, each one able to fail in a way that can be named, and
 * what went wrong is written on the leaf itself.
 */
class PageAdapter(private val onTap: () -> Unit) :
    RecyclerView.Adapter<PageAdapter.LeafVH>() {

    /** A decoded leaf, or the reason there is none. */
    private class Leaf(val bitmap: Bitmap?, val trouble: String?)

    class LeafVH(root: View) : RecyclerView.ViewHolder(root) {
        val image: ZoomImageView = root.findViewById(R.id.leaf)
        val trouble: TextView = root.findViewById(R.id.trouble)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LeafVH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_leaf, parent, false)
    )

    override fun getItemCount() = Book.pageCount()

    override fun onBindViewHolder(holder: LeafVH, position: Int) {
        val context = holder.itemView.context
        val leaf = decode(context, position, context.resources.displayMetrics)

        holder.image.setImageBitmap(leaf.bitmap)
        holder.image.reset()
        holder.trouble.text = leaf.trouble.orEmpty()
        holder.trouble.visibility = if (leaf.trouble == null) View.GONE else View.VISIBLE
        holder.image.setOnClickListener { onTap() }
    }

    override fun onViewRecycled(holder: LeafVH) {
        holder.image.setImageDrawable(null)
        holder.trouble.visibility = View.GONE
    }

    /**
     * The leaf at [position], or why it is not there.
     *
     * Measured before it is decoded, so «the leaf is missing from the package»
     * and «the leaf is there but would not decode» are told apart; and decoded
     * no larger than the screen can use, coming down by halves if the memory
     * will not stretch, so a phone with little to spare still shows the book.
     */
    private fun decode(context: Context, position: Int, screen: DisplayMetrics): Leaf {
        val name = Book.asset(position)

        val measured = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val looked = runCatching {
            context.assets.open(name).use { BitmapFactory.decodeStream(it, null, measured) }
        }
        if (looked.isFailure || measured.outWidth <= 0) {
            val why = looked.exceptionOrNull()?.javaClass?.simpleName ?: "لا أبعاد"
            return Leaf(null, context.getString(R.string.leaf_missing, name, why))
        }

        var sample = 1
        val wanted = maxOf(screen.widthPixels, 1)
        while (measured.outWidth / (sample * 2) >= wanted) sample *= 2

        var why = ""
        repeat(TRIES) {
            val how = BitmapFactory.Options().apply { inSampleSize = sample }
            try {
                val bitmap = context.assets.open(name)
                    .use { BitmapFactory.decodeStream(it, null, how) }
                if (bitmap != null) return Leaf(bitmap, null)
                why = "null"
            } catch (_: OutOfMemoryError) {
                why = "OOM"
            } catch (e: Throwable) {
                why = e.javaClass.simpleName
            }
            sample *= 2
        }
        return Leaf(null, context.getString(R.string.leaf_undrawable, name, why, sample))
    }

    private companion object {
        /** How many times to halve the leaf before giving up on it. */
        const val TRIES = 4
    }
}
