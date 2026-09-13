package com.example.helloworld

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private lateinit var controller: BrowserController
    private lateinit var status: TextView
    private lateinit var loopButton: Button
    private val scope = CoroutineScope(Dispatchers.Main)
    @Volatile private var loopRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = BrowserController(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this)
        root.addView(status)

        val input = EditText(this).apply {
            hint = "url o búsqueda"
            setText("wikipedia.org")
        }
        root.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val rowNameInput = EditText(this).apply {
            hint = "nombre de la fila (ej: Juan Pérez)"
        }
        root.addView(rowNameInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val buttonTextInput = EditText(this).apply {
            hint = "texto del botón a testear"
            setText("Test aprobar")
        }
        root.addView(buttonTextInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val confirmTextInput = EditText(this).apply {
            hint = "texto del botón de confirmación"
            setText("Sí, TESTEAR")
        }
        root.addView(confirmTextInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val successTextInput = EditText(this).apply {
            hint = "texto del alert final de éxito"
            setText("⚠️ TEST APROBADO")
        }
        root.addView(successTextInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        fun button(label: String, action: () -> Unit): Button {
            val b = Button(this).apply {
                text = label
                setOnClickListener { action() }
            }
            root.addView(b, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            return b
        }

        button("1. Activar accesibilidad") { controller.openAccessibilitySettings() }
        button("2. Abrir en Focus (Intent)") {
            if (!controller.openUrl(input.text.toString()))
                toast("Firefox Focus no está instalado")
        }
        button("3. Buscar automatizado") {
            scope.launch {
                val ok = controller.searchInFocus(input.text.toString())
                if (!ok) toast("No se pudo completar (revisa Logcat)")
            }
        }
        button("4. Borrar sesión") { controller.erase() }
        button("5. Dump del árbol UI (Logcat)") { controller.dump() }
        loopButton = button("6. Loop Test Aprobar") {
            if (loopRunning) {
                loopRunning = false
            } else {
                loopRunning = true
                loopButton.text = "Detener loop"
                scope.launch {
                    val rowName = rowNameInput.text.toString()
                    val buttonText = buttonTextInput.text.toString()
                    val confirmText = confirmTextInput.text.toString()
                    val successText = successTextInput.text.toString()
                    val ok = controller.runTestApprovalLoop(
                        input.text.toString(), rowName, buttonText, confirmText, successText
                    ) { loopRunning }
                    loopRunning = false
                    loopButton.text = "6. Loop Test Aprobar"
                    if (!ok) toast("Loop detenido (revisa Logcat)")
                }
            }
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val pkg = controller.installedPackage() ?: "NO instalado"
        val svc = if (controller.isServiceEnabled()) "activo" else "DESACTIVADO"
        status.text = "Navegador: $pkg\nServicio de accesibilidad: $svc\n"
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
