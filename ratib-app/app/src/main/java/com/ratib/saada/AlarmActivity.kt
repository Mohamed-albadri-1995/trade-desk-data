package com.ratib.saada

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
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
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 700, 700)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
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
