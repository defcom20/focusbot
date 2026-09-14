package com.example.helloworld

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio que dibuja un botón flotante sobre otras apps (tipo "burbuja").
 * Un toque simple reinicia la sesión de Firefox Focus con la URL configurada;
 * una pulsación larga detiene todo y quita el botón de la pantalla.
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var button: Button
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var controller: BrowserController
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = BrowserController(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // lejos del borde izquierdo: en x=0 el sistema suele reservar esa
            // franja para el gesto de "volver atrás" y se come el toque
            x = 100
            y = 300
        }

        button = Button(this).apply { text = "↻" }

        // variables para distinguir arrastre / toque simple / pulsación larga
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTime = 0L

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(button, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val movedX = Math.abs(event.rawX - initialTouchX)
                    val movedY = Math.abs(event.rawY - initialTouchY)
                    if (movedX < 20 && movedY < 20) {
                        val pressDuration = System.currentTimeMillis() - downTime
                        if (pressDuration > 600) {
                            stopSelf() // pulsación larga: parar todo
                        } else {
                            restartFocus() // toque simple: reiniciar
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(button, params)
    }

    /**
     * Borra la sesión actual de Focus, lo manda a Inicio (para forzar que
     * quede en segundo plano de verdad) y lo vuelve a abrir con la URL
     * configurada. Mandarlo a Inicio es necesario: si Focus ya está en
     * primer plano con esa misma URL, reabrirlo con el mismo Intent no
     * fuerza una recarga, solo trae la pantalla actual al frente.
     */
    private fun restartFocus() {
        scope.launch {
            val url = getSharedPreferences("focusbot_config", MODE_PRIVATE)
                .getString("url", "wikipedia.org") ?: "wikipedia.org"
            controller.erase()
            delay(500)
            controller.goHome()
            delay(500)
            controller.openUrl(url)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::button.isInitialized) {
            runCatching { windowManager.removeView(button) }
        }
    }
}
