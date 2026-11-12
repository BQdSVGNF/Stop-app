package com.tempo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tempo.app.data.AppSettings
import com.tempo.app.data.TaskStore
import com.tempo.app.notif.Notifier
import com.tempo.app.notif.Scheduler
import com.tempo.app.ui.TempoApp
import com.tempo.app.ui.TempoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        TaskStore.init(this)
        Notifier.createChannel(this)
        enableEdgeToEdge()
        setContent {
            TempoTheme {
                TempoApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Rafraîchit les rappels (phrase du jour, exactitude des alarmes…)
        Scheduler.rescheduleAll(this)
    }
}
