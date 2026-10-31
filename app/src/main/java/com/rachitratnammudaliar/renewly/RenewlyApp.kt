package com.rachitratnammudaliar.renewly

import android.app.Application
import com.rachitratnammudaliar.renewly.reminders.Reminders

class RenewlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.schedule(this)
    }
}
