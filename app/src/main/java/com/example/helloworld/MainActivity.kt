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
    private val scope = CoroutineScope(Dispatchers.Main)

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

        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
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
