package com.pdfbook.reader

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.pdfbook.reader.databinding.ActivityMainBinding

/**
 * PDF book reader with a chapter drawer, page counter, next/previous
 * navigation, go-to-page, and bookmarks.
 *
 * To use your own book: put a file named exactly `book.pdf` in
 * app/src/main/assets/ and list its sections in app/src/main/assets/chapters.txt.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val assetFileName = "book.pdf"
    private val prefsName = "pdf_book_prefs"
    private val lastPageKey = "last_page"
    private val bookmarksKey = "bookmarks"

    private var pageCount = 0
    private var currentPage = 0
    private var immersive = false

    // Chapter title -> 0-based page index, loaded from assets/chapters.txt
    private val chapters = ArrayList<Pair<String, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        binding.toolbar.setNavigationOnClickListener { toggleDrawer() }

        loadChapters()
        buildDrawerMenu()

        binding.btnPrev.setOnClickListener { goToPage(currentPage - 1) }
        binding.btnNext.setOnClickListener { goToPage(currentPage + 1) }

        // Allow zooming in further for the fine calligraphy.
        binding.pdfView.minZoom = 1f
        binding.pdfView.midZoom = 2.5f
        binding.pdfView.maxZoom = 6f

        val startPage = prefs().getInt(lastPageKey, 0)
        loadPdf(startPage)
    }

    /** Single tap toggles a distraction-free full-screen reading mode. */
    private fun toggleFullscreen() {
        immersive = !immersive
        val controller = WindowInsetsControllerCompat(window, binding.root)
        if (immersive) {
            supportActionBar?.hide()
            binding.bottomBar.visibility = View.GONE
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            supportActionBar?.show()
            binding.bottomBar.visibility = View.VISIBLE
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun prefs() = getSharedPreferences(prefsName, MODE_PRIVATE)

    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun loadPdf(startPage: Int) {
        binding.progressBar.visibility = View.VISIBLE
        binding.pdfView.fromAsset(assetFileName)
            .defaultPage(startPage)
            .swipeHorizontal(true)
            .pageSnap(true)
            .autoSpacing(true)
            .pageFling(true)
            .enableDoubletap(true)
            .enableAnnotationRendering(true)
            .pageFitPolicy(FitPolicy.BOTH)   // fit the WHOLE page on screen, not just width
            .fitEachPage(true)
            .onTap { _ ->
                toggleFullscreen()
                true
            }
            .scrollHandle(DefaultScrollHandle(this))
            .onLoad { nbPages ->
                pageCount = nbPages
                currentPage = startPage.coerceIn(0, (nbPages - 1).coerceAtLeast(0))
                binding.progressBar.visibility = View.GONE
                updatePageIndicator()
            }
            .onPageChange { page, total ->
                currentPage = page
                pageCount = total
                updatePageIndicator()
                prefs().edit().putInt(lastPageKey, page).apply()
            }
            .onError {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, R.string.err_open, Toast.LENGTH_LONG).show()
            }
            .load()
    }

    private fun goToPage(page: Int) {
        if (pageCount <= 0) return
        val target = page.coerceIn(0, pageCount - 1)
        binding.pdfView.jumpTo(target, true)
        currentPage = target
        updatePageIndicator()
    }

    private fun updatePageIndicator() {
        val cur = toArabicDigits(currentPage + 1)
        val tot = toArabicDigits(if (pageCount > 0) pageCount else currentPage + 1)
        binding.pageIndicator.text = getString(R.string.page_of, cur, tot)
    }

    /** Converts western digits in a number to Arabic-Indic digits (٠١٢٣…). */
    private fun toArabicDigits(n: Int): String {
        val d = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString { for (c in n.toString()) append(if (c in '0'..'9') d[c - '0'] else c) }
    }

    // ---- Chapters (side drawer) ------------------------------------------

    private fun loadChapters() {
        try {
            assets.open("chapters.txt").bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val sep = line.indexOf('|')
                    if (sep > 0) {
                        val page = line.substring(0, sep).trim().toIntOrNull()
                        val title = line.substring(sep + 1).trim()
                        if (page != null && title.isNotEmpty()) {
                            chapters.add(Pair(title, page - 1))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // No chapters file; drawer will show a friendly note.
        }
    }

    private fun buildDrawerMenu() {
        val menu = binding.navView.menu
        menu.clear()
        if (chapters.isEmpty()) {
            menu.add(Menu.NONE, -1, 0, getString(R.string.no_chapters)).isEnabled = false
        } else {
            chapters.forEachIndexed { i, (title, _) ->
                menu.add(Menu.NONE, i, i, title)
            }
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            val i = item.itemId
            if (i in chapters.indices) goToPage(chapters[i].second)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    // ---- Bookmarks --------------------------------------------------------

    private fun getBookmarks(): MutableList<Int> {
        val s = prefs().getString(bookmarksKey, "") ?: ""
        return if (s.isBlank()) mutableListOf()
        else s.split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
    }

    private fun saveBookmarks(list: List<Int>) {
        val cleaned = list.distinct().sorted().joinToString(",")
        prefs().edit().putString(bookmarksKey, cleaned).apply()
    }

    private fun addBookmark() {
        val list = getBookmarks()
        if (!list.contains(currentPage)) {
            list.add(currentPage)
            saveBookmarks(list)
        }
        Toast.makeText(
            this,
            getString(R.string.bookmark_added, toArabicDigits(currentPage + 1)),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBookmarks() {
        val list = getBookmarks().sorted()
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = list.map { getString(R.string.page_of_label, toArabicDigits(it + 1)) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_bookmarks)
            .setItems(labels) { _, which -> goToPage(list[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Go to page -------------------------------------------------------

    private fun showGoToPageDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.goto_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_goto)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n != null) goToPage(n - 1)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Toolbar menu -----------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_bookmark -> { addBookmark(); true }
            R.id.action_bookmarks -> { showBookmarks(); true }
            R.id.action_goto -> { showGoToPageDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        when {
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            immersive -> toggleFullscreen()
            else -> super.onBackPressed()
        }
    }
}
