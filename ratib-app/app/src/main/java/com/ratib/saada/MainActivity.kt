package com.ratib.saada

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.viewpager2.widget.ViewPager2
import com.ratib.saada.databinding.ActivityMainBinding

/**
 * Book-style reader for راتب السعادة: the text is laid out and cut into
 * screen-sized pages you flip through (right-to-left). Includes a section
 * drawer, page counter, flip arrows, adjustable font, light/dark mode, a
 * photo background, and it remembers the page you were on.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: PagerAdapter

    private val prefsName = "ratib_prefs"
    private val scaleKey = "font_scale"
    private val pageKey = "page_index"
    private val nightKey = "night_mode"

    private val blocks = ArrayList<Block>()
    private val footnotes = HashMap<Int, String>()
    private var pagination: Pagination? = null
    private var scale = 1f
    private var needsAutoFit = false
    private val targetPages = 15

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

        BackgroundLoader.apply(this, binding.bgImage)
        needsAutoFit = !prefs().contains(scaleKey)
        scale = prefs().getFloat(scaleKey, 1f)
        parseContent()

        pagerAdapter = PagerAdapter(emptyList(), emptyList())
        binding.pager.adapter = pagerAdapter
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                prefs().edit().putInt(pageKey, position).apply()
            }
        })

        binding.btnPrev.setOnClickListener {
            binding.pager.currentItem = (binding.pager.currentItem - 1).coerceAtLeast(0)
        }
        binding.btnNext.setOnClickListener {
            val max = (pagination?.pages?.size ?: 1) - 1
            binding.pager.currentItem = (binding.pager.currentItem + 1).coerceAtMost(max)
        }

        // Paginate once the pager has real dimensions.
        binding.pager.post { repaginate(restorePage = prefs().getInt(pageKey, 0)) }

        // Ask for notification permission (Android 13+) so reminders can alert.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the next reminder from the latest prayer times.
        ReminderScheduler.rescheduleNext(this)
    }

    private fun prefs() = getSharedPreferences(prefsName, MODE_PRIVATE)

    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun parseContent() {
        // A blank line ends a stanza. Consecutive non-blank lines form one block
        // and keep their line breaks (so a couplet's two halves stay together).
        val buffer = ArrayList<String>()
        fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                blocks.add(Block.Body(buffer.joinToString("\n")))
                buffer.clear()
            }
        }
        assets.open("ratib.txt").bufferedReader().forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> flushBuffer()
                line.startsWith("## ") -> {
                    flushBuffer()
                    blocks.add(Block.Subheading(line.removePrefix("## ").trim()))
                }
                line.startsWith("# ") -> {
                    flushBuffer()
                    blocks.add(Block.Heading(line.removePrefix("# ").trim()))
                }
                line.startsWith("> ") -> {
                    flushBuffer()
                    if (blocks.isNotEmpty()) footnotes[blocks.lastIndex] = line.removePrefix("> ").trim()
                }
                else -> buffer.add(line)
            }
        }
        flushBuffer()
    }

    private val density get() = resources.displayMetrics.density
    private fun paddingH() = (20f * density * 2).toInt()
    // 14dp padding top+bottom in item_page, plus a safety line so nothing clips.
    private fun paddingV() = (14f * density * 2 + 20f * density).toInt()

    /** Largest font scale that keeps the whole ratib within ~targetPages pages. */
    private fun autoFitScale(w: Int, h: Int): Float {
        var best = 0.7f
        var s = 0.7f
        while (s <= 1.51f) {
            val pages = Paginator.paginate(this, blocks, footnotes, w, h, s).pages.size
            if (pages <= targetPages) best = s
            s += 0.05f
        }
        return best
    }

    private fun repaginate(restorePage: Int) {
        val w = binding.pager.width - paddingH()
        val h = binding.pager.height - paddingV()
        if (w <= 0 || h <= 0) {
            binding.pager.post { repaginate(restorePage) }
            return
        }
        if (needsAutoFit) {
            needsAutoFit = false
            scale = autoFitScale(w, h)
            prefs().edit().putFloat(scaleKey, scale).apply()
        }
        val result = Paginator.paginate(this, blocks, footnotes, w, h, scale)
        pagination = result
        pagerAdapter.submit(result.pages, result.footnotes)
        buildDrawerMenu(result)
        val target = restorePage.coerceIn(0, result.pages.size - 1)
        binding.pager.setCurrentItem(target, false)
        updateIndicator(target)
    }

    private fun updateIndicator(position: Int) {
        val total = pagination?.pages?.size ?: 1
        binding.pageIndicator.text =
            getString(R.string.page_of, toArabicDigits(position + 1), toArabicDigits(total))
    }

    private fun toArabicDigits(n: Int): String {
        val d = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString { for (c in n.toString()) append(if (c in '0'..'9') d[c - '0'] else c) }
    }

    private fun buildDrawerMenu(result: Pagination) {
        val menu = binding.navView.menu
        menu.clear()
        blocks.forEachIndexed { i, b ->
            when (b) {
                is Block.Heading -> menu.add(Menu.NONE, i, i, b.text)
                is Block.Subheading -> menu.add(Menu.NONE, i, i, "—  ${b.text}")
                else -> {}
            }
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            val page = pagination?.headingPage?.get(item.itemId)
            if (page != null) binding.pager.setCurrentItem(page, false)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun changeFont(delta: Float) {
        val s = (scale + delta).coerceIn(0.8f, 2.4f)
        if (s == scale) return
        // Keep our place: remember which block was at the top of the current page.
        val anchorBlock = pagination?.pageStartBlock?.getOrNull(binding.pager.currentItem) ?: 0
        scale = s
        prefs().edit().putFloat(scaleKey, s).apply()
        val w = binding.pager.width - paddingH()
        val h = binding.pager.height - paddingV()
        val result = Paginator.paginate(this, blocks, footnotes, w, h, scale)
        pagination = result
        pagerAdapter.submit(result.pages, result.footnotes)
        buildDrawerMenu(result)
        // Find the page that starts at or before that block.
        var page = result.pageStartBlock.indexOfLast { it <= anchorBlock }
        if (page < 0) page = 0
        binding.pager.setCurrentItem(page, false)
        updateIndicator(page)
    }

    private fun isNightActive(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun toggleTheme() {
        val next = if (isNightActive())
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
        R.id.action_font_larger -> { changeFont(0.1f); true }
        R.id.action_font_smaller -> { changeFont(-0.1f); true }
        R.id.action_theme -> { toggleTheme(); true }
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }
}
