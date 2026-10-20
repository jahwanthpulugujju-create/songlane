package app.songlane.playback

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class PlaybackTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Songlane"
            state = Tile.STATE_INACTIVE
            contentDescription = "Toggle Songlane playback"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            runPlaybackAction(this, "toggle") {
                qsTile?.apply {
                    state = Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }
}
