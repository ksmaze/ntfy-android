package io.heckel.ntfy.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.heckel.ntfy.ui.ShareActivity
import io.heckel.ntfy.util.Log

class ClipboardShareShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareIntent = Intent(this, ShareActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(ShareActivity.EXTRA_LOAD_CLIPBOARD, true)
        }
        Log.d(TAG, "Launching ShareActivity from shortcut requesting clipboard content")
        startActivity(shareIntent)
        finish()
    }

    companion object {
        private const val TAG = "ClipboardShareShortcut"
    }
}
