package com.dalail.rahamat

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.dalail.rahamat.databinding.ActivityMainBinding

/**
 * The reader.
 *
 * The leaves are turned right to left, as the book is turned, which is what
 * the pager does of its own accord once it is told the layout is Arabic. On
 * opening, the day's own حزب is offered — the book is read a حزب a day — and
 * the reader may take it or carry on where they left off.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PageAdapter

    private val pageKey = "page_index"
    private var chromeShown = true

    private val notifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Book.load(this)
        fitSystemBars()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener { toggleDrawer() }

        adapter = PageAdapter { toggleChrome() }
        binding.pager.adapter = adapter
        // Leaves either side stay decoded so a turn is instant.
        binding.pager.offscreenPageLimit = 1
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showWhere(position)
                Reminder.prefs(this@MainActivity).edit().putInt(pageKey, position).apply()
            }
        })

        buildIndex()
        greet()
        askForNotifications()
        Reminder.schedule(this)

        val asked = intent.getIntExtra(EXTRA_PAGE, -1)
        val open = if (asked >= 0) asked else Reminder.prefs(this).getInt(pageKey, 0)
        binding.pager.setCurrentItem(open.coerceIn(0, maxOf(0, Book.pageCount() - 1)), false)
        showWhere(binding.pager.currentItem)

        handleBackWithDrawer()
    }

    /**
     * Keeps the bars clear of the system's own.
     *
     * From Android 15 every window is drawn behind the status and navigation
     * bars and the theme's colours for them are ignored, so a toolbar at the
     * top would sit under the clock and the folio at the foot under the
     * gesture bar. The leaf is left to have the whole screen — that is what
     * the book wants — and the insets are added as padding to the two bars
     * over it, and as a margin to the day's card. Padding rather than margin
     * for the bars, so each one's veil still reaches under the system bar
     * instead of leaving a bare strip above it.
     *
     * setDecorFitsSystemWindows is called for every version, not only 15 and
     * later, so the book looks the same on an old phone as on a new one.
     */
    private fun fitSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Both bars stand on a dark veil, so the system draws its icons light.
        WindowCompat.getInsetsController(window, binding.root)
            .isAppearanceLightStatusBars = false

        val barTop = binding.toolbar.paddingTop
        val folioBottom = binding.folioBar.paddingBottom
        val cardEdge = (binding.today.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            binding.toolbar.updatePadding(
                top = barTop + bars.top, left = bars.left, right = bars.right
            )
            binding.folioBar.updatePadding(
                bottom = folioBottom + bars.bottom, left = bars.left, right = bars.right
            )
            (binding.today.layoutParams as ViewGroup.MarginLayoutParams).let {
                it.bottomMargin = cardEdge + bars.bottom
                binding.today.layoutParams = it
            }
            insets
        }
    }

    /**
     * The day's حزب, offered on a card over the leaf until it is taken or
     * dismissed — so opening the book puts today's reading one tap away.
     */
    private fun greet() {
        val hizb = Book.hizbOfWard()
        if (hizb == null) {
            hideToday()
            return
        }
        binding.todayDay.text = Book.wardTitle(this)
        binding.todayHizb.text = hizb.label
        binding.todayOpen.setOnClickListener {
            binding.pager.setCurrentItem(hizb.page - 1, false)
            hideToday()
        }
        binding.todayDismiss.setOnClickListener { hideToday() }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!Reminder.isOn(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showWhere(position: Int) {
        supportActionBar?.subtitle = Book.sectionOf(position)
        binding.folio.text = getString(
            R.string.page_of, Book.arabic(position + 1), Book.arabic(Book.pageCount())
        )
    }

    /** The bars step aside on a tap, so the leaf can have the whole screen. */
    private fun toggleChrome() {
        chromeShown = !chromeShown
        val to = if (chromeShown) View.VISIBLE else View.GONE
        binding.toolbar.visibility = to
        binding.folioBar.visibility = to
    }

    private fun buildIndex() {
        val menu = binding.navView.menu
        menu.clear()
        Book.index().forEachIndexed { i, e ->
            menu.add(Menu.NONE, i, i, if (e.depth > 0) " ${e.label}" else e.label)
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            Book.index().getOrNull(item.itemId)?.let {
                binding.pager.setCurrentItem(it.page - 1, false)
                hideToday()
            }
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

    /** Sets when the daily call comes, or turns it off. */
    private fun chooseReminder() {
        TimePickerDialog(
            this,
            { _, hour, minute -> Reminder.set(this, true, hour, minute) },
            Reminder.hour(this), Reminder.minute(this), true
        ).apply {
            setButton(TimePickerDialog.BUTTON_NEUTRAL, getString(R.string.reminder_off)) { _, _ ->
                Reminder.set(this@MainActivity, false, Reminder.hour(this@MainActivity),
                    Reminder.minute(this@MainActivity))
            }
            show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_today -> {
            Book.hizbOfWard()?.let { binding.pager.setCurrentItem(it.page - 1, false) }
            hideToday()
            true
        }
        R.id.action_reminder -> { chooseReminder(); true }
        else -> super.onOptionsItemSelected(item)
    }

    /** Puts the day's card away, and lets back know it no longer holds it. */
    private fun hideToday() {
        binding.today.visibility = View.GONE
        refreshBack()
    }

    /**
     * Back closes the index, then the day's card, before it leaves the book.
     *
     * The callback is armed only while there is one of those to close, so that
     * when there is not, the press reaches the system untouched and Android 16
     * can draw its predictive animation of leaving the book. A callback that
     * stayed armed and passed the press on by hand would take that away.
     */
    private fun handleBackWithDrawer() {
        onBackPressedDispatcher.addCallback(this, backHandler)
        binding.drawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) = refreshBack()
                override fun onDrawerClosed(drawerView: View) = refreshBack()
            }
        )
        refreshBack()
    }

    private val backHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            else
                hideToday()
        }
    }

    private fun refreshBack() {
        backHandler.isEnabled =
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) ||
                binding.today.visibility == View.VISIBLE
    }

    companion object {
        const val EXTRA_PAGE = "open_at_page"
    }
}
