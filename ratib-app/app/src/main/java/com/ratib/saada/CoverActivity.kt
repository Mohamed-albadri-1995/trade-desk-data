package com.ratib.saada

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ratib.saada.databinding.ActivityCoverBinding

/** Opening cover screen: the photo, the title, and a button into the reader. */
class CoverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BackgroundLoader.apply(this, binding.coverImage)

        // The reminders are on from the moment the app is installed: give them a
        // location to work from if none has been captured yet, and arm the next
        // one now. Nobody should have to open the settings and press save for
        // the alarms to start.
        ReminderPrefs.seedLocationIfMissing(this)
        ReminderScheduler.rescheduleNext(this)

        binding.versionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)

        binding.btnEnter.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
