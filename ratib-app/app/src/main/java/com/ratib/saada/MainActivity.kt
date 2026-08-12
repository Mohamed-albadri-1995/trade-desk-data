package com.ratib.saada

import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ratib.saada.databinding.ActivityMainBinding

/**
 * Reader for راتب السعادة. The text lives in assets/ratib.txt (lines starting
 * with "# " are section headings). Features: section drawer, adjustable font
 * size, light/dark mode, and it remembers the reading position.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RatibAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private val prefsName = "ratib_prefs"
    private val scaleKey = "font_scale"
    private val posKey = "scroll_pos"
    private val nightKey = "night_mode"

    private val blocks = ArrayList<Block>()
    private val headings = ArrayList<Pair<String, Int>>() // title, block index

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

        parseContent()

        adapter = RatibAdapter(blocks).apply { scale = prefs().getFloat(scaleKey, 1f) }
        layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        buildDrawerMenu()

        val pos = prefs().getInt(posKey, 0)
        if (pos in blocks.indices) layoutManager.scrollToPosition(pos)
    }

    private fun prefs() = getSharedPreferences(prefsName, MODE_PRIVATE)

    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun parseContent() {
        assets.open("ratib.txt").bufferedReader().forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            if (line.startsWith("# ")) {
                val title = line.removePrefix("# ").trim()
                headings.add(Pair(title, blocks.size))
                blocks.add(Block.Heading(title))
            } else {
                blocks.add(Block.Body(line))
            }
        }
    }

    private fun buildDrawerMenu() {
        val menu = binding.navView.menu
        menu.clear()
        headings.forEachIndexed { i, (title, _) -> menu.add(Menu.NONE, i, i, title) }
        binding.navView.setNavigationItemSelectedListener { item ->
            val idx = item.itemId
            if (idx in headings.indices) {
                layoutManager.scrollToPositionWithOffset(headings[idx].second, 0)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun changeFont(delta: Float) {
        val s = (adapter.scale + delta).coerceIn(0.8f, 2.4f)
        if (s == adapter.scale) return
        adapter.scale = s
        prefs().edit().putFloat(scaleKey, s).apply()
        adapter.notifyDataSetChanged()
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
        AppCompatDelegate.setDefaultNightMode(next) // recreates the activity
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_font_larger -> { changeFont(0.1f); true }
        R.id.action_font_smaller -> { changeFont(-0.1f); true }
        R.id.action_theme -> { toggleTheme(); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        prefs().edit().putInt(posKey, first).apply()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }
}
