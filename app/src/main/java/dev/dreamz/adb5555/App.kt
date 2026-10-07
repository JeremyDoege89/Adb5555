package dev.dreamz.adb5555

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class App : Application() {
    /** Outlives screens and the tile, so an enable started from either always finishes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_PAIRING, "Pairing", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Where you type the Wireless debugging pairing code"
                setSound(null, null)
            },
        )
    }

    companion object {
        const val CHANNEL_PAIRING = "pairing"
    }
}
