package com.athaan.app

import android.app.Application
import com.athaan.app.schedule.AdhanScheduler

class AthaanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        AdhanScheduler.reschedule(this)
    }
}
