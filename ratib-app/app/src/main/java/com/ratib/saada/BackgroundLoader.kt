package com.ratib.saada

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.ImageView

/**
 * Loads the user's background photo from assets (background.jpg or .png),
 * downsampled to roughly the screen size to avoid memory issues, and places it
 * into the given ImageView. If no such asset exists, the ImageView is left
 * empty so the elegant fallback gradient behind it shows through.
 */
object BackgroundLoader {

    private val candidates = listOf("background.jpg", "background.png", "background.jpeg", "background.webp")

    fun apply(context: Context, target: ImageView) {
        val name = candidates.firstOrNull { asset ->
            runCatching { context.assets.open(asset).close() }.isSuccess
        } ?: return

        val metrics = context.resources.displayMetrics
        val reqW = metrics.widthPixels.coerceAtLeast(1)
        val reqH = metrics.heightPixels.coerceAtLeast(1)

        // First pass: read bounds only.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(name).use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        var halfW = bounds.outWidth / 2
        var halfH = bounds.outHeight / 2
        while (halfW >= reqW && halfH >= reqH) {
            sample *= 2
            halfW /= 2
            halfH /= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) }
        if (bitmap != null) target.setImageBitmap(bitmap)
    }
}
