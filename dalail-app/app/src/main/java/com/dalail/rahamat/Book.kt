package com.dalail.rahamat

import android.content.Context
import java.util.Calendar
import java.util.Locale

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
        // Not «if the index is read» but «if the book has leaves»: a load that
        // found none must be allowed to try again rather than latch a book
        // with nothing in it.
        if (entries.isNotEmpty() && leaves > 0) return
        val read = ArrayList<Entry>()
        runCatching {
            context.assets.open(INDEX).bufferedReader().forEachLine { raw ->
                val parts = raw.trim().split('|', limit = 3)
                if (parts.size < 3) return@forEachLine
                val page = parts[0].toIntOrNull() ?: return@forEachLine
                read.add(Entry(page, parts[1].toIntOrNull() ?: 0, parts[2]))
            }
        }
        if (read.isNotEmpty()) entries = read
        leaves = count(context)
    }

    /**
     * How many leaves the book has, asked three ways.
     *
     * It used to be asked one way — list the assets folder and count what
     * comes back — and everything the reader sees hung on it. On a phone where
     * that listing came back empty, and it can, the pager was handed a book of
     * no leaves and showed a blank screen, and nothing ever tried again.
     *
     * So: what the build wrote down, which cannot be wrong; then the listing;
     * then opening the leaves one after another until one is not there. Any of
     * the three is enough, and they fail in different ways.
     */
    private fun count(context: Context): Int {
        runCatching {
            context.assets.open(COUNT).bufferedReader().use { it.readLine() }
                ?.trim()?.toIntOrNull()
        }.getOrNull()?.let { if (it > 0) return it }

        runCatching { context.assets.list(PAGES)?.size ?: 0 }
            .getOrDefault(0).let { if (it > 0) return it }

        var n = 0
        while (n < MOST_LEAVES &&
            runCatching { context.assets.open(asset(n)).close() }.isSuccess
        ) n++
        return n
    }

    fun pageCount() = leaves

    fun index(): List<Entry> = entries

    /**
     * The name of a leaf's file in the package.
     *
     * Locale.ROOT, and not the phone's own: String.format without one uses
     * the default locale, and on a phone set to Arabic %03d is written in
     * Arabic-Indic digits — p٠٠١.webp — which is not the name of anything in
     * the package. That is why the book opened on a blank screen for some
     * readers and not others, and why no emulator ever showed it: an
     * emulator speaks English.
     */
    fun asset(leaf: Int): String =
        String.format(Locale.ROOT, "%s/p%03d.webp", PAGES, leaf + 1)

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

    /**
     * When the day of the ward turns over.
     *
     * The Islamic day begins at sunset, not at midnight: Sunday evening is
     * ليلة الاثنين, and what is read then is Monday's حزب, not Sunday's. So
     * from this hour onward the ward belongs to tomorrow by the civil
     * calendar. Sunset moves through the year and this does not — it is a
     * plain hour, chosen to sit after maghrib the year round rather than to
     * track it.
     */
    const val EVENING_TURN = 18

    /** Whether the ward being read now is the coming night's. */
    fun isNightWard(now: Calendar = Calendar.getInstance()) =
        now.get(Calendar.HOUR_OF_DAY) >= EVENING_TURN

    /** The weekday the ward belongs to, which after sunset is tomorrow's. */
    fun wardDay(now: Calendar = Calendar.getInstance()): Int {
        val c = now.clone() as Calendar
        if (isNightWard(c)) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.get(Calendar.DAY_OF_WEEK)
    }

    /** The حزب to be read now. */
    fun hizbOfWard(): Entry? = hizbOfDay(wardDay())

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

    /** The name of the day the ward belongs to. */
    fun wardDayName(context: Context): String = dayName(context, wardDay())

    /**
     * How the ward is named now: «ورد ليلة الاثنين» after sunset on Sunday,
     * «ورد يوم الاثنين» during Monday itself.
     */
    fun wardTitle(context: Context): String = context.getString(
        if (isNightWard()) R.string.ward_night else R.string.ward_day,
        wardDayName(context)
    )

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

    /** What the build wrote: how many leaves this book has. */
    private const val COUNT = "pages.txt"

    /** A stop for the probe, so a broken book cannot loop for ever. */
    private const val MOST_LEAVES = 2000
}
