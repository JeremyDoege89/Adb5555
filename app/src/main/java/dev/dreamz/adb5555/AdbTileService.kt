package dev.dreamz.adb5555

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tap to turn on port 5555. Tapping while it's on does nothing (turning it off by accident would
 * cut a remote session); turn it off in the app. Long-press opens the app.
 */
class AdbTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val tile = qsTile ?: return
        if (tile.state == Tile.STATE_ACTIVE) {
            Toast.makeText(this, "Port 5555 is already on", Toast.LENGTH_SHORT).show(); return
        }
        tile.subtitle = "Turning on…"; tile.updateTile()
        (application as App).scope.launch {
            val outcome = AdbClient.get(this@AdbTileService).enable()
            Toast.makeText(this@AdbTileService, describe(outcome), Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    private fun refresh() {
        (application as App).scope.launch {
            val on = withContext(Dispatchers.IO) { AdbClient.isPortOpen() }
            val tile = qsTile ?: return@launch
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (on) "On" else "Off"
            tile.updateTile()
        }
    }
}
