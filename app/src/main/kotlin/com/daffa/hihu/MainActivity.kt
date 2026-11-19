package com.daffa.hihu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daffa.hihu.views.shells.HihuRoot

/**
 * The single activity. It only binds playback while visible, hands the state to the views, and
 * passes on playlist links shared to Hihu.
 */
class MainActivity : ComponentActivity() {
    private val app get() = (application as HihuApplication).state

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HihuRoot(app) }
        // Only a fresh start; after a rotation the shared link was already taken.
        if (savedInstanceState == null) receive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /** A playlist link shared from another app, such as YouTube, opens the import dialog. */
    private fun receive(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(app.playlistImport::offer)
    }

    override fun onStart() {
        super.onStart()
        app.playback.connect()
    }

    override fun onStop() {
        app.playback.disconnect()
        super.onStop()
    }
}
