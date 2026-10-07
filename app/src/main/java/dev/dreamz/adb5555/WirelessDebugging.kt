package dev.dreamz.adb5555

import android.content.Context
import android.content.Intent
import android.provider.Settings

object WirelessDebugging {
    fun isOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1

    /** Developer options, scrolled to Wireless debugging (the pairing dialog itself can't be opened directly). */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The notification reply: "123456" (port found automatically) or "37123 123456" / "37123:123456"
     * (port typed too). Returns (port or null, code) or null if it doesn't look like either.
     */
    fun parseReply(text: String): Pair<Int?, String>? {
        val parts = Regex("\\d+").findAll(text).map { it.value }.toList()
        return when {
            parts.size == 1 && parts[0].length == 6 -> null to parts[0]
            parts.size == 2 && parts[1].length == 6 && parts[0].length in 4..5 -> parts[0].toInt() to parts[1]
            parts.size == 2 && parts[0].length == 6 && parts[1].length in 4..5 -> parts[1].toInt() to parts[0]
            else -> null
        }
    }
}
