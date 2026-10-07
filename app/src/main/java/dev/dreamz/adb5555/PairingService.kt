package dev.dreamz.adb5555

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pairing without juggling numbers: while Settings shows "Pair device with pairing code", this
 * service finds that dialog's port over mDNS, and you type only the 6-digit code into the
 * notification (pull down the shade; the dialog stays open, so the code stays valid).
 */
class PairingService : Service() {

    private var mdns: AdbMdns? = null
    @Volatile private var port: Int? = null
    private var pairing: Job? = null
    private var timeout: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finish(null); return START_NOT_STICKY }
            ACTION_REPLY -> {
                startForeground(ID, waiting(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                startDiscovery()
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString().orEmpty()
                onReply(text)
            }
            else -> {
                startForeground(ID, waiting(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                startDiscovery()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDiscovery() {
        if (mdns?.isRunning == true) return
        mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, p ->
            if (p > 0 && pairing?.isActive != true) {
                port = p
                notify(ready(p))
            }
        }.also { it.start() }
        timeout?.cancel()
        timeout = app().scope.launch { delay(10 * 60_000L); finish("Pairing timed out. Tap Pair in the app to try again.") }
    }

    private fun onReply(text: String) {
        val parsed = WirelessDebugging.parseReply(text)
        if (parsed == null) {
            notify(prompt("Type the 6-digit code (or the port and the code).", error = true)); return
        }
        val target = parsed.first ?: port
        if (target == null) {
            notify(prompt("Pairing dialog not found yet. Type the port and the code, like 37123 123456.", error = true)); return
        }
        notify(working())
        pairing = app().scope.launch {
            when (val r = AdbClient.get(this@PairingService).pairWithCode(target, parsed.second)) {
                AdbClient.Outcome.Done -> finish(null, paired = true)
                is AdbClient.Outcome.Failed -> notify(prompt("${r.message} Get a new code if the dialog closed.", error = true))
                else -> notify(prompt("Pairing failed. Try again.", error = true))
            }
        }
    }

    private fun finish(message: String?, paired: Boolean = false) {
        mdns?.stop(); mdns = null
        timeout?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(NotificationManager::class.java)
        when {
            paired -> nm.notify(ID_RESULT, result("Paired", "You can close the pairing dialog. Tap Enable 5555 in the app or the tile.", withEnable = true))
            message != null -> nm.notify(ID_RESULT, result("Not paired", message, withEnable = false))
        }
        stopSelf()
    }

    // --- notifications -----------------------------------------------------------------------

    private fun waiting() = prompt(
        "Open Wireless debugging › Pair device with pairing code, then pull this down and type the code.",
    )

    private fun ready(p: Int) = prompt("Pairing dialog found (port $p). Type the 6-digit code.")

    private fun working() = base()
        .setContentTitle("Pairing…")
        .setProgress(0, 0, true)
        .build()

    private fun prompt(text: String, error: Boolean = false): Notification {
        val input = RemoteInput.Builder(KEY_TEXT).setLabel("Pairing code").build()
        // Foreground-service intent: the reply must work even if the system stopped us meanwhile.
        val reply = PendingIntent.getForegroundService(
            this, 1, Intent(this, PairingService::class.java).setAction(ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val enter = Notification.Action.Builder(null, "Enter code", reply).addRemoteInput(input).build()
        return base()
            .setContentTitle(if (error) "Pairing: try again" else "Pair with Wireless debugging")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .addAction(enter)
            .addAction(Notification.Action.Builder(null, "Open settings", settings()).build())
            .addAction(Notification.Action.Builder(null, "Cancel", stop()).build())
            .build()
    }

    private fun result(title: String, text: String, withEnable: Boolean): Notification {
        val b = Notification.Builder(this, App.CHANNEL_PAIRING)
            .setSmallIcon(R.drawable.ic_adb)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
        if (withEnable) b.addAction(Notification.Action.Builder(null, "Open app", openApp()).build())
        return b.build()
    }

    private fun base() = Notification.Builder(this, App.CHANNEL_PAIRING)
        .setSmallIcon(R.drawable.ic_adb)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())

    private fun notify(n: Notification) = getSystemService(NotificationManager::class.java).notify(ID, n)

    private fun settings() = PendingIntent.getActivity(this, 2, WirelessDebugging.settingsIntent(), PendingIntent.FLAG_IMMUTABLE)
    private fun stop() = PendingIntent.getService(
        this, 3, Intent(this, PairingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
    )
    private fun openApp() = PendingIntent.getActivity(
        this, 4, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun app() = application as App

    override fun onDestroy() {
        mdns?.stop()
        timeout?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ID = 1
        private const val ID_RESULT = 2
        private const val KEY_TEXT = "text"
        private const val ACTION_REPLY = "dev.dreamz.adb5555.REPLY"
        private const val ACTION_STOP = "dev.dreamz.adb5555.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PairingService::class.java))
        }
    }
}
