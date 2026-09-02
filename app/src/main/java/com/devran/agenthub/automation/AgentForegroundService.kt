package com.devran.agenthub.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.devran.agenthub.R
import androidx.core.app.NotificationCompat

class AgentForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.devran.agenthub.START_AGENT_RUNTIME"
        const val ACTION_STOP = "com.devran.agenthub.STOP_AGENT_RUNTIME"
        var active: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("agenthub_agent", "AgentHub agent runtime", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            active = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = NotificationCompat.Builder(this, "agenthub_agent")
            .setSmallIcon(R.drawable.ic_agenthub)
            .setContentTitle("AgentHub agent runtime")
            .setContentText("Background agent runtime is enabled. Accessibility/screen permissions still control device access.")
            .setOngoing(true)
            .build()
        startForeground(1002, notification)
        active = true
        return START_STICKY
    }

    override fun onDestroy() {
        active = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
