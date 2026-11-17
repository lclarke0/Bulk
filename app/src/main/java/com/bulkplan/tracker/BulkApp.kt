package com.bulkplan.tracker

import android.app.Application
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.work.Reminders

class BulkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)
        Reminders.ensureGarminSync(this)
    }
}
