package com.pdfbook.reader

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.pdfbook.reader.databinding.ActivityMainBinding

/**
 * Displays the bundled PDF book.
 *
 * To use your own book: put a file named exactly `book.pdf` in
 * app/src/main/assets/  (replace the placeholder there), then Run the app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // The PDF file inside app/src/main/assets/. Rename here if you use a different name.
    private val assetFileName = "book.pdf"

    // Remembers the last page the reader was on, so the book reopens where they left off.
    private val prefsName = "pdf_book_prefs"
    private val lastPageKey = "last_page"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val startPage = prefs.getInt(lastPageKey, 0)

        loadPdf(startPage)
    }

    private fun loadPdf(startPage: Int) {
        binding.progressBar.visibility = View.VISIBLE

        binding.pdfView.fromAsset(assetFileName)
            .defaultPage(startPage)
            .enableSwipe(true)          // swipe between pages
            .swipeHorizontal(false)     // vertical scrolling (set true for page-flip style)
            .enableDoubletap(true)      // double-tap to zoom
            .enableAnnotationRendering(true)
            .spacing(8)                 // gap between pages in dp
            .scrollHandle(DefaultScrollHandle(this)) // draggable scroll bar with page number
            .onLoad {
                binding.progressBar.visibility = View.GONE
            }
            .onPageChange { page, pageCount ->
                title = "${getString(R.string.app_name)}  (${page + 1}/$pageCount)"
                // Save progress so we can restore it next launch.
                getSharedPreferences(prefsName, MODE_PRIVATE)
                    .edit()
                    .putInt(lastPageKey, page)
                    .apply()
            }
            .onError { throwable ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Couldn't open the book. Make sure app/src/main/assets/$assetFileName exists.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .load()
    }
}
