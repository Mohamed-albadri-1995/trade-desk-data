package com.ratib.saada

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

/** Full-screen alarm screen: rings (looping) over the lock screen until إيقاف. */
class AlarmActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)
        showLabel(intent)

        // The screen takes over the ringing; cancel the notification's own sound.
        NotificationManagerCompat.from(this).cancel(AlarmReceiver.NOTIF_ID)
        startRinging()

        findViewById<android.view.View>(R.id.btnStop).setOnClickListener { stopAndFinish() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showLabel(intent)
    }

    private fun showLabel(intent: Intent?) {
        val label = intent?.getStringExtra(ReminderScheduler.EXTRA_LABEL)
            ?: getString(R.string.app_name)
        findViewById<TextView>(R.id.alarmLabel).text = label
    }

    private fun startRinging() {
        // A prayer time calls the adhan, once through, as an adhan is called;
        // anything meant to wake you rings on until it is stopped. Where no
        // recitation is bundled the adhan falls back to the ringing alarm, so
        // the prayer is never announced by silence.
        val kind = runCatching {
            ReminderScheduler.Kind.valueOf(intent?.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: "")
        }.getOrDefault(ReminderScheduler.Kind.ADHAN)
        val adhan = if (kind == ReminderScheduler.Kind.ADHAN) AlarmSounds.adhan(this) else null
        val uri = adhan ?: AlarmSounds.systemAlarm(this)

        try {
            player = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, uri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(
                            if (adhan != null) AudioAttributes.CONTENT_TYPE_SPEECH
                            else AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )
                isLooping = adhan == null
                // The screen stays up after the adhan ends, so it is still there
                // to be dismissed rather than vanishing on its own.
                setOnCompletionListener { vibrator?.cancel() }
                prepare()
                start()
            }
        } catch (_: Exception) {
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        // A short shake to announce the adhan; a persistent one to wake a sleeper.
        val pattern = if (adhan != null) longArrayOf(0, 500, 400, 500) else longArrayOf(0, 700, 700)
        val repeat = if (adhan != null) -1 else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, repeat)
        }
    }

    private fun stopRinging() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopAndFinish() {
        stopRinging()
        NotificationManagerCompat.from(this).cancel(AlarmReceiver.NOTIF_ID)
        finish()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}
