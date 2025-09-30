package io.heckel.ntfy.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.heckel.ntfy.ui.ShareActivity
import io.heckel.ntfy.util.Log

/**
 * Quick Settings Tile that opens ShareActivity with clipboard content
 */
class ClipboardShareTileService : TileService() {
    
    override fun onClick() {
        super.onClick()
        
        val shareIntent = Intent(this, ShareActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(ShareActivity.EXTRA_LOAD_CLIPBOARD, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        Log.d(TAG, "Launching ShareActivity from Quick Settings tile with clipboard content")
        val pending = PendingIntent.getActivity(
            this,
            0,
            shareIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pending)
    }
    
    companion object {
        private const val TAG = "ClipboardShareTile"
    }
}
