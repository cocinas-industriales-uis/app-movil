package co.edu.uis.cocinas.monitor

import android.app.Application
import co.edu.uis.cocinas.monitor.service.Notifications

class CocinasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
