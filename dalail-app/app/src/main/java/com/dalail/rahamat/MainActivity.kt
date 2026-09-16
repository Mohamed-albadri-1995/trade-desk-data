package com.dalail.rahamat

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.dalail.rahamat.databinding.ActivityMainBinding
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle

/**
 * Reader for دلائل الرحمات.
 *
 * The book is carried as the printed PDF itself — the same file the press
 * would take — so what is on the screen is exactly the page, ornamental band,
 * gold border and all. On top of it the app adds what a PDF on its own cannot:
 * an index of the twelve sections and all ninety صلوات, each pointing at the
 * page it begins on; a page counter; and it opens where it was left.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefsName = "dalail_prefs"
    private val pageKey = "page_index"
    private val nightKey = "night_mode"

    /** An entry in the index: the page it points at, and how it reads. */
    private data class Entry(val page: Int, val depth: Int, val label: String)

    private val entries = ArrayList<Entry>()
    private var pages = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener { toggleDrawer() }

        readIndex()
        buildIndexMenu()
        openBook(prefs().getInt(pageKey, 0))

        binding.btnPrev.setOnClickListener { goTo(binding.pdf.currentPage - 1) }
        binding.btnNext.setOnClickListener { goTo(binding.pdf.currentPage + 1) }

        handleBackWithDrawer()
    }

    private fun prefs() = getSharedPreferences(prefsName, MODE_PRIVATE)

    private fun isNight() = prefs().getBoolean(nightKey, false)

    /**
     * Loads the book, opening at [page].
     *
     * Called again when the reader turns the lamp on or off, since the night
     * rendering is chosen as the file is opened, not after.
     */
    private fun openBook(page: Int) {
        binding.opening.visibility = View.VISIBLE
        binding.pdf.fromAsset(ASSET)
            .defaultPage(page)
            .swipeHorizontal(false)
            .pageSnap(true)
            .pageFling(true)
            .autoSpacing(true)
            .fitEachPage(true)
            .spacing(8)
            .nightMode(isNight())
            .scrollHandle(DefaultScrollHandle(this))
            .onLoad { count ->
                pages = count
                binding.opening.visibility = View.GONE
                showCounter(binding.pdf.currentPage)
            }
            .onPageChange { current, _ ->
                showCounter(current)
                prefs().edit().putInt(pageKey, current).apply()
            }
            .load()
    }

    private fun goTo(page: Int) {
        if (pages == 0) return
        binding.pdf.jumpTo(page.coerceIn(0, pages - 1), true)
    }

    private fun showCounter(current: Int) {
        val total = if (pages > 0) pages else 1
        binding.pageIndicator.text =
            getString(R.string.page_of, arabic(current + 1), arabic(total))
    }

    private fun arabic(n: Int): String {
        val d = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString { for (c in n.toString()) append(if (c in '0'..'9') d[c - '0'] else c) }
    }

    /**
     * Reads the index built beside the book: one line per entry, as
     * `page|depth|label`, the page counting the cover as one.
     */
    private fun readIndex() {
        runCatching {
            assets.open(INDEX).bufferedReader().forEachLine { raw ->
                val parts = raw.trim().split('|', limit = 3)
                if (parts.size < 3) return@forEachLine
                val page = parts[0].toIntOrNull() ?: return@forEachLine
                val depth = parts[1].toIntOrNull() ?: 0
                entries.add(Entry(page, depth, parts[2]))
            }
        }
    }

    private fun buildIndexMenu() {
        val menu = binding.navView.menu
        menu.clear()
        entries.forEachIndexed { i, e ->
            // A صلاة is set in from its حزب, so the index reads as one.
            menu.add(Menu.NONE, i, i, if (e.depth > 0) " ${e.label}" else e.label)
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            entries.getOrNull(item.itemId)?.let { goTo(it.page - 1) }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    /** The lamp: the page is rendered dark for reading at night. */
    private fun toggleNight() {
        val next = !isNight()
        prefs().edit().putBoolean(nightKey, next).apply()
        openBook(binding.pdf.currentPage)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_theme -> { toggleNight(); true }
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
        const val ASSET = "book.pdf"
        const val INDEX = "index.txt"
    }
}
