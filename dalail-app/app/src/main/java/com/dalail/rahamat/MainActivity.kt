package com.dalail.rahamat

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
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
     * The day's حزب, offered on a card over the leaf until it is taken or
     * dismissed — so opening the book puts today's reading one tap away.
     */
    private fun greet() {
        val hizb = Book.hizbOfToday()
        if (hizb == null) {
            binding.today.visibility = View.GONE
            return
        }
        binding.todayDay.text = getString(R.string.ward_today, Book.todayName(this))
        binding.todayHizb.text = hizb.label
        binding.todayOpen.setOnClickListener {
            binding.pager.setCurrentItem(hizb.page - 1, false)
            binding.today.visibility = View.GONE
        }
        binding.todayDismiss.setOnClickListener { binding.today.visibility = View.GONE }
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
                binding.today.visibility = View.GONE
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
            Book.hizbOfToday()?.let { binding.pager.setCurrentItem(it.page - 1, false) }
            true
        }
        R.id.action_reminder -> { chooseReminder(); true }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Back closes the index, then the day's card, before it leaves the book.
     * Registered on the dispatcher rather than by overriding onBackPressed,
     * which an app targeting Android 16 is no longer called on.
     */
    private fun handleBackWithDrawer() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    binding.today.visibility == View.VISIBLE ->
                        binding.today.visibility = View.GONE
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    companion object {
        const val EXTRA_PAGE = "open_at_page"
    }
}
