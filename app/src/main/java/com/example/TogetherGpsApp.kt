package com.example

import android.app.Application
import com.example.location.NotificationHelper
import org.osmdroid.config.Configuration

class TogetherGpsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        NotificationHelper.createNotificationChannels(this)
    }
}
