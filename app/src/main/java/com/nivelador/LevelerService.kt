package com.nivelador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder

class LevelerService : Service() {

    private var effect: DynamicsProcessing? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        try {
            effect = createCompressor()
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

    /**
     * Compresor sobre la mezcla global de audio (sesion 0):
     * - Baja los sonidos fuertes (ratio 4:1 sobre -35 dB)
     * - Sube todo con ganancia de salida (+12 dB), asi los bajos se escuchan mas
     * - Limitador final para que nunca explote
     */
    private fun createCompressor(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
            2,          // canales (estereo)
            false, 0,   // pre-EQ
            true, 1,    // MBC (compresor) con 1 banda
            false, 0,   // post-EQ
            true        // limitador
        ).build()

        val dp = DynamicsProcessing(0, 0, config)

        val band = DynamicsProcessing.MbcBand(
            true,       // activo
            20000f,     // frecuencia de corte (toda la banda)
            20f,        // ataque (ms)
            300f,       // liberacion (ms)
            4f,         // ratio
            -35f,       // umbral (dB)
            6f,         // knee (dB)
            -90f,       // noise gate (dB)
            1f,         // expander ratio
            0f,         // pre gain
            12f         // post gain (dB)  <- subir/bajar para mas/menos volumen
        )
        dp.setMbcBandAllChannelsTo(0, band)

        val limiter = DynamicsProcessing.Limiter(
            true, true, 0,
            1f,         // ataque (ms)
            60f,        // liberacion (ms)
            10f,        // ratio
            -2f,        // umbral (dB)
            0f          // post gain
        )
        dp.setLimiterAllChannelsTo(limiter)

        dp.enabled = true
        return dp
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("nivelador", "Nivelador", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "nivelador")
            .setContentTitle("Nivelador activo")
            .setContentText("Nivelando el volumen")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
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
