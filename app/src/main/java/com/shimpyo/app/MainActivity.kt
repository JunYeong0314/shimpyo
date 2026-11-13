package com.shimpyo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.engine.EngineIntent
import com.shimpyo.app.engine.ShimpyoEngine
import com.shimpyo.app.ui.ShimpyoRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var engine: ShimpyoEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShimpyoTheme { ShimpyoRoot() } }
        handleAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAction(intent)
    }

    /** Widget and notification shortcuts. */
    private fun handleAction(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_ACTION) == ACTION_REST_NOW) {
            engine.start()
            engine.onIntent(EngineIntent.RestNow)
        }
    }

    companion object {
        const val EXTRA_ACTION = "com.shimpyo.app.extra.ACTION"
        const val ACTION_REST_NOW = "rest_now"
    }
}
