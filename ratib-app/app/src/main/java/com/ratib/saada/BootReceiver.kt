package com.ratib.saada

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the next reminder after the device reboots or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Seed first: after a reboot this may run before the app is ever opened,
        // and without coordinates there is nothing to schedule.
        ReminderPrefs.seedLocationIfMissing(context)
        ReminderScheduler.rescheduleNext(context)
    }
}
