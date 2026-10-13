package com.nivelador

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        }
        button = Button(this).apply {
            textSize = 30f
            setTextColor(Color.WHITE)
            setOnClickListener { toggle() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 64 })
            addView(button, LinearLayout.LayoutParams(-1, 400))
        }
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // Al abrir la app se activa sola (no al girar la pantalla)
        if (savedInstanceState == null && !LevelerService.running) {
            startForegroundService(Intent(this, LevelerService::class.java))
        }
    }

    private fun toggle() {
        val i = Intent(this, LevelerService::class.java)
        if (LevelerService.running) stopService(i) else startForegroundService(i)
    }

    private fun render() {
        val on = LevelerService.running
        button.text = if (on) "APAGAR" else "ACTIVAR"
        button.setBackgroundColor(if (on) Color.parseColor("#C62828") else Color.parseColor("#2E7D32"))
        status.text = LevelerService.lastError
            ?: if (on) "Nivelador ACTIVADO\nLos volumenes se igualan" else "Nivelador apagado"
    }

    override fun onResume() {
        super.onResume()
        LevelerService.listener = { runOnUiThread { render() } }
        render()
    }

    override fun onPause() {
        LevelerService.listener = null
        super.onPause()
    }
}
