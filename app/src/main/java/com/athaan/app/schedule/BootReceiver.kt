package com.athaan.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the alarms after a reboot, an app update, or a clock/time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AdhanScheduler.reschedule(context)
    }
}
