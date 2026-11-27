// Important instructions:
// - Provide a native Android Quick Settings Tile in the notification shade to toggle the simulator ON or OFF anytime.

package shree.remote.sim

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RemoteQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileVisuals()
    }

    override fun onClick() {
        super.onClick()
        val prefs = RemotePrefs(this)
        prefs.isSimulatorEnabled = !prefs.isSimulatorEnabled
        syncTileVisuals()
    }

    private fun syncTileVisuals() {
        val tile = qsTile ?: return
        val enabled = RemotePrefs(this).isSimulatorEnabled
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (enabled) "TV Remote: ON" else "TV Remote: OFF"
        tile.updateTile()
    }
}
