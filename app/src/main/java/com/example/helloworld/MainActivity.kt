package com.example.helloworld

import android.app.Activity
import android.content.SharedPreferences
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
    private lateinit var prefs: SharedPreferences
    private lateinit var input: EditText
    private lateinit var rowNameInput: EditText
    private lateinit var buttonTextInput: EditText
    private lateinit var confirmTextInput: EditText
    private lateinit var successTextInput: EditText
    private val scope = CoroutineScope(Dispatchers.Main)
    @Volatile private var loopRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = BrowserController(this)
        prefs = getSharedPreferences("focusbot_config", MODE_PRIVATE)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this)
        root.addView(status)

        input = EditText(this).apply {
            hint = "url o búsqueda"
            setText(prefs.getString("url", "wikipedia.org"))
        }
        root.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        rowNameInput = EditText(this).apply {
            hint = "nombre de la fila (ej: Juan Pérez)"
            setText(prefs.getString("rowName", ""))
        }
        root.addView(rowNameInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        buttonTextInput = EditText(this).apply {
            hint = "texto del botón a testear"
            setText(prefs.getString("buttonText", "Test aprobar"))
        }
        root.addView(buttonTextInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        confirmTextInput = EditText(this).apply {
            hint = "texto del botón de confirmación"
            setText(prefs.getString("confirmText", "Sí, TESTEAR"))
        }
        root.addView(confirmTextInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        successTextInput = EditText(this).apply {
            hint = "texto del alert final de éxito"
            setText(prefs.getString("successText", "⚠️ TEST APROBADO"))
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

    /** Guarda la configuración de los 5 campos para que persista al salir de la app */
    override fun onPause() {
        super.onPause()
        prefs.edit()
            .putString("url", input.text.toString())
            .putString("rowName", rowNameInput.text.toString())
            .putString("buttonText", buttonTextInput.text.toString())
            .putString("confirmText", confirmTextInput.text.toString())
            .putString("successText", successTextInput.text.toString())
            .apply()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
