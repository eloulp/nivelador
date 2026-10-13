package com.nivelador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.IBinder

class LevelerService : Service() {

    private var effect: AudioEffect? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        try {
            effect = try {
                createCompressor()
            } catch (e: Exception) {
                createLoudnessEnhancer()   // plan B para Xiaomi/MIUI
            }
            running = true
            lastError = null
        } catch (e: Exception) {
            running = false
            lastError = "Este celular no permite el efecto: ${e.message}"
            stopSelf()
        }
        listener?.invoke()
        return START_STICKY
    }

    // Plan A: compresor + limitador (baja lo fuerte, sube lo bajo)
    private fun createCompressor(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
            2, false, 0, true, 1, false, 0, true
        ).build()
        val dp = DynamicsProcessing(0, 0, config)
        val band = DynamicsProcessing.MbcBand(
            true, 20000f, 20f, 300f, 4f, -35f, 6f, -90f, 1f, 0f,
            12f   // ganancia de salida en dB: sube/baja para mas/menos volumen
        )
        dp.setMbcBandAllChannelsTo(0, band)
        val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f)
        dp.setLimiterAllChannelsTo(limiter)
        dp.enabled = true
        return dp
    }

    // Plan B: sube la ganancia con limitador interno (1000 = +10 dB)
    private fun createLoudnessEnhancer(): LoudnessEnhancer {
        val le = LoudnessEnhancer(0)
        le.setTargetGain(1000)   // prueba 1500 o 2000 si los bajos siguen bajos
        le.enabled = true
        return le
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("nivelador", "Nivelador", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "nivelador")
            .setContentTitle("NIVELADOR ACTIVADO")
            .setContentText("Los volúmenes se están igualando. Toca para abrir o apagar.")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        try { effect?.release() } catch (_: Exception) {}
        effect = null
        running = false
        listener?.invoke()
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
        @Volatile var lastError: String? = null
        var listener: (() -> Unit)? = null
    }
}
