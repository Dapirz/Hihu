package com.daffa.hihu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daffa.hihu.views.shells.HihuRoot

/** The single activity. It only binds playback while visible and hands the state to the views. */
class MainActivity : ComponentActivity() {
    private val app get() = (application as HihuApplication).state

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HihuRoot(app) }
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
