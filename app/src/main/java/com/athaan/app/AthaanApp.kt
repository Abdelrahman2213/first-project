package com.athaan.app

import android.app.Application
import com.athaan.app.data.CustomMuezzins
import com.athaan.app.schedule.AdhanScheduler

class AthaanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CustomMuezzins.load(this)
        Notifications.createChannels(this)
        AdhanScheduler.reschedule(this)
    }
}
