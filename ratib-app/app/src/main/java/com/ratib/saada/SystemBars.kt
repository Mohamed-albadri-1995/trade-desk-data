package com.ratib.saada

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keeps a screen's own furniture clear of the system's bars.
 *
 * From Android 15 the system draws every window behind the status and
 * navigation bars and ignores android:statusBarColor and
 * android:navigationBarColor in the theme. An app that targets Android 16 —
 * which Google Play now requires — therefore has its toolbar under the clock
 * and whatever sits at the foot of the screen under the gesture bar. In this
 * app that was the page-turning bar, and, worse, the button that stops the
 * adhan.
 *
 * The screen keeps the whole display, which is what the reading wants, and
 * the bars' own room is added as padding to whichever views were asked for.
 * It is applied on every Android version, not only 15 and later, so an old
 * phone shows what a new one shows.
 */
object SystemBars {

    /**
     * @param top the view to hold clear of the status bar, if any
     * @param bottom the view to hold clear of the navigation bar, if any
     *
     * Either may be the same view — a scrolling list, say, that wants room at
     * both ends. Each view's own padding is kept and the bar's added to it.
     */
    fun fit(activity: Activity, top: View? = null, bottom: View? = null) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val ownTop = top?.paddingTop ?: 0
        val ownBottom = bottom?.paddingBottom ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            top?.updatePadding(top = ownTop + bars.top, left = bars.left, right = bars.right)
            bottom?.updatePadding(
                bottom = ownBottom + bars.bottom, left = bars.left, right = bars.right
            )
            insets
        }
    }
}
