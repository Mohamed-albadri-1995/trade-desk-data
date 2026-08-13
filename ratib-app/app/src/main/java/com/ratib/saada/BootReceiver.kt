package com.ratib.saada

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the next reminder after the device reboots or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rescheduleNext(context)
    }
}
