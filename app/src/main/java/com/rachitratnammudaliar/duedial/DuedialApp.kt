package com.rachitratnammudaliar.duedial

import android.app.Application
import com.rachitratnammudaliar.duedial.reminders.Reminders

class DuedialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.schedule(this)
    }
}
