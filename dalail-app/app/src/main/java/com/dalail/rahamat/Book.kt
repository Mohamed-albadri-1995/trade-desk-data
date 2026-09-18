package com.dalail.rahamat

import android.content.Context
import java.util.Calendar

/**
 * What the app knows about the book: how many leaves it has, what is on them,
 * and which حزب belongs to today.
 */
object Book {

    /** An entry in the index: the leaf it opens at, and how it reads. */
    data class Entry(val page: Int, val depth: Int, val label: String)

    /** The seven أحزاب, in the book's order, each with the day it is read on. */
    private val DAY_OF_HIZB = intArrayOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )

    private var entries: List<Entry> = emptyList()
    private var leaves = 0

    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        val read = ArrayList<Entry>()
        runCatching {
            context.assets.open(INDEX).bufferedReader().forEachLine { raw ->
                val parts = raw.trim().split('|', limit = 3)
                if (parts.size < 3) return@forEachLine
                val page = parts[0].toIntOrNull() ?: return@forEachLine
                read.add(Entry(page, parts[1].toIntOrNull() ?: 0, parts[2]))
            }
        }
        entries = read
        leaves = runCatching { context.assets.list(PAGES)?.size ?: 0 }.getOrDefault(0)
    }

    fun pageCount() = leaves

    fun index(): List<Entry> = entries

    fun asset(leaf: Int) = "$PAGES/p%03d.webp".format(leaf + 1)

    /** The sections, in order — the أحزاب among them. */
    private fun sections() = entries.filter { it.depth == 0 }

    /**
     * The حزب read on [day], as an index entry, or null.
     *
     * The أحزاب are the sections whose name says which day they are for, and
     * they come in the week's order starting at Monday, so the day is found by
     * position rather than by reading the Arabic.
     */
    fun hizbOfDay(day: Int): Entry? {
        val hizbs = sections().filter { it.label.contains("الحزب") }
        val at = DAY_OF_HIZB.indexOf(day)
        return if (at in hizbs.indices) hizbs[at] else null
    }

    fun hizbOfToday(): Entry? =
        hizbOfDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))

    /** Today's name, for the greeting and the reminder. */
    fun dayName(context: Context, day: Int): String {
        val names = context.resources.getStringArray(R.array.week_days)
        val at = when (day) {
            Calendar.SUNDAY -> 0; Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3; Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5
            else -> 6
        }
        return names.getOrElse(at) { "" }
    }

    fun todayName(context: Context): String =
        dayName(context, Calendar.getInstance().get(Calendar.DAY_OF_WEEK))

    /** The section a leaf belongs to, for the running title. */
    fun sectionOf(leaf: Int): String {
        val page = leaf + 1
        return sections().lastOrNull { it.page <= page }?.label.orEmpty()
    }

    fun arabic(n: Int): String {
        val d = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString { for (c in n.toString()) append(if (c in '0'..'9') d[c - '0'] else c) }
    }

    private const val INDEX = "index.txt"
    private const val PAGES = "pages"
}
