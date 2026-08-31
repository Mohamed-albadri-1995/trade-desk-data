package com.ratib.saada

import android.content.ContentResolver
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Which sound each kind of reminder is announced with. */
object AlarmSounds {

    /**
     * The adhan recording, if one is bundled with the app.
     *
     * Looked up by name rather than through R.raw so the app builds either way:
     * the recitation is a recording that has to be licensed, and until the right
     * one is in place every prayer alarm falls back to the phone's own alarm
     * sound. Drop the file in as res/raw/adhan.mp3 and it is used from then on.
     */
    fun adhan(context: Context): Uri? {
        val id = context.resources.getIdentifier("adhan", "raw", context.packageName)
        if (id == 0) return null
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$id")
    }

    /** The phone's own alarm sound, for waking rather than announcing. */
    fun systemAlarm(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}
