package com.aegispass.hce.data.hce

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aegispass.hce.domain.engine.EmulationMemoryBuffer
import com.aegispass.hce.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * PRD Phase 3 Quick Settings Tile Integration:
 * Launches MainActivity with biometric gate prompt to arm the favorite card profile.
 */
@AndroidEntryPoint
class AegisQuickTileService : TileService() {

    @Inject
    lateinit var memoryBuffer: EmulationMemoryBuffer

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        if (memoryBuffer.isEmulationSessionActive()) {
            memoryBuffer.disarmAndScrub()
            syncTileState()
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.aegispass.hce.ACTION_QUICK_ARM"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    100,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val session = memoryBuffer.sessionState.value
        if (session.isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = session.activeCardName ?: "AegisPass Armed"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Arm AegisPass"
        }
        tile.updateTile()
    }
}
