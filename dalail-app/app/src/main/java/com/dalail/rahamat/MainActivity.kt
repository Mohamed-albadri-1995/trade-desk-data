package com.dalail.rahamat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.viewpager2.widget.ViewPager2
import com.dalail.rahamat.databinding.ActivityMainBinding
import java.util.concurrent.Executors

/**
 * Book-style reader for دلائل الرحمات: the text is laid out and cut into
 * screen-sized leaves you flip through, right to left. An index of the sections
 * and of all ninety صلوات, a page counter, flip arrows, an adjustable size,
 * day and night, and it opens where it was left.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = PagerAdapter()

    private val prefsName = "dalail_prefs"
    private val pageKey = "page_index"
    private val nightKey = "night_mode"
    private val scaleKey = "text_scale"

    private val blocks = ArrayList<Block>()
    private var pagination: Pagination? = null
    private var scale = 1f

    /** Laying the whole book out takes a moment, so it is not done on the UI thread. */
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var job = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            prefs().getInt(nightKey, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener { toggleDrawer() }

        scale = prefs().getFloat(scaleKey, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        readBook()

        binding.pager.adapter = adapter
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showCounter(position)
                prefs().edit().putInt(pageKey, position).apply()
            }
        })

        binding.btnPrev.setOnClickListener {
            binding.pager.currentItem = (binding.pager.currentItem - 1).coerceAtLeast(0)
        }
        binding.btnNext.setOnClickListener {
            val last = (pagination?.pages?.size ?: 1) - 1
            binding.pager.currentItem = (binding.pager.currentItem + 1).coerceAtMost(last)
        }

        binding.pager.post { repaginate(prefs().getInt(pageKey, 0)) }
        handleBackWithDrawer()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun prefs() = getSharedPreferences(prefsName, MODE_PRIVATE)

    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    /**
     * Reads the book off the asset, which carries the reader's own marks:
     *
     *     = …   the book's name        ## n\tsuras   a صلاة and what it is on
     *     # …   a section             ~ …           the closing line
     *
     * Everything else is a passage, one to a line.
     */
    private fun readBook() {
        assets.open(ASSET).bufferedReader().forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("## ") -> {
                    val parts = line.removePrefix("## ").split('\t', limit = 2)
                    blocks.add(Block.Salat(parts[0].trim(), parts.getOrElse(1) { "" }.trim()))
                }
                line.startsWith("# ") -> blocks.add(Block.Section(line.removePrefix("# ").trim()))
                line.startsWith("= ") -> blocks.add(Block.Title(line.removePrefix("= ").trim()))
                line.startsWith("~ ") -> blocks.add(Block.Colophon(line.removePrefix("~ ").trim()))
                else -> blocks.add(Block.Body(line))
            }
        }
    }

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    /**
     * The column the text is set in: the leaf less item_page's own padding —
     * 12dp inside the ruled border, and 10dp more to the column.
     */
    private fun column(width: Int) = width - 2 * dp(12f + 10f)

    /**
     * The room a leaf has for text: the leaf less the same 12dp top and bottom,
     * the running head's line, and the margin above the column.
     */
    private fun room(height: Int) = height - 2 * dp(12f) - dp(26f) - dp(6f)

    /**
     * What the ornamental band costs a leaf over the running head it replaces.
     * The band keeps the artwork's proportions, so its height follows the width
     * it is given — the leaf less the 12dp inside the border.
     */
    private fun bandExtra(width: Int): Int {
        val bandWidth = width - 2 * dp(12f)
        return (bandWidth * 172f / 817f).toInt() - dp(26f)
    }

    /**
     * Lays the book out for the room there is now, and opens it at [anchor] —
     * a block, so that the reader keeps their place across a change of size,
     * where the leaf numbering does not survive — or else at [page].
     */
    private fun repaginate(page: Int = 0, anchor: Int? = null) {
        val w = column(binding.pager.width)
        val h = room(binding.pager.height)
        if (w <= 0 || h <= 0) {
            binding.pager.post { repaginate(page, anchor) }
            return
        }
        val extra = bandExtra(binding.pager.width)
        val mine = ++job
        binding.layingOut.visibility = View.VISIBLE
        worker.execute {
            val result = runCatching {
                Paginator.paginate(this, blocks, w, h, extra, scale)
            }.getOrNull()
            ui.post {
                // A later run has started — or the activity is gone — so this
                // result is stale and must not be shown.
                if (mine != job || result == null || isFinishing) return@post
                pagination = result
                adapter.submit(result)
                buildIndex()
                binding.layingOut.visibility = View.GONE
                val at = anchor?.let { result.navPage[it] } ?: page
                val target = at.coerceIn(0, result.pages.size - 1)
                binding.pager.setCurrentItem(target, false)
                showCounter(target)
            }
        }
    }

    private fun showCounter(position: Int) {
        val total = pagination?.pages?.size ?: 1
        binding.pageIndicator.text =
            getString(R.string.page_of, arabic(position + 1), arabic(total))
    }

    private fun arabic(n: Int): String {
        val d = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString { for (c in n.toString()) append(if (c in '0'..'9') d[c - '0'] else c) }
    }

    /** The sections, and under each of them its صلوات by number and sura. */
    private fun buildIndex() {
        val menu = binding.navView.menu
        menu.clear()
        blocks.forEachIndexed { i, b ->
            when (b) {
                is Block.Section -> menu.add(Menu.NONE, i, i, b.text)
                is Block.Salat -> menu.add(
                    Menu.NONE, i, i,
                    if (b.suras.isEmpty()) b.number
                    else getString(R.string.salat_entry, b.number, b.suras)
                )
                else -> Unit
            }
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            pagination?.navPage?.get(item.itemId)?.let {
                binding.pager.setCurrentItem(it, false)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    /**
     * Re-sets the book at a new size, keeping the reader where they were: the
     * leaf they are on is remembered by the block it begins with, not by its
     * number, since the numbering changes with the size.
     */
    private fun resize(by: Float) {
        val next = (scale + by).coerceIn(MIN_SCALE, MAX_SCALE)
        if (next == scale) return
        val anchor = anchorBlock()
        scale = next
        prefs().edit().putFloat(scaleKey, scale).apply()
        repaginate(anchor = anchor)
    }

    /**
     * The last indexed block to have begun on or before the leaf now open —
     * the section or صلاة the reader is in, which is the thing worth keeping
     * hold of when the leaves are recut at a new size.
     */
    private fun anchorBlock(): Int? {
        val page = binding.pager.currentItem
        return pagination?.navPage?.entries
            ?.filter { it.value <= page }
            ?.maxByOrNull { it.key }
            ?.key
    }

    private fun toggleTheme() {
        val next =
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                AppCompatDelegate.MODE_NIGHT_NO
            else
                AppCompatDelegate.MODE_NIGHT_YES
        prefs().edit().putInt(nightKey, next).apply()
        AppCompatDelegate.setDefaultNightMode(next)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_bigger -> { resize(+STEP); true }
        R.id.action_smaller -> { resize(-STEP); true }
        R.id.action_theme -> { toggleTheme(); true }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Back closes the index before it leaves the book. Registered on the
     * dispatcher rather than by overriding onBackPressed, which an app
     * targeting Android 16 is no longer called on: predictive back is on by
     * default there, and the old override would simply stop running.
     */
    private fun handleBackWithDrawer() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private companion object {
        const val ASSET = "dalail.txt"
        const val MIN_SCALE = 0.7f
        const val MAX_SCALE = 1.6f
        const val STEP = 0.1f
    }
}
