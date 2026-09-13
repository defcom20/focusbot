package com.example.helloworld

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.delay

class BrowserController(private val context: Context) {

    companion object {
        const val FOCUS = "org.mozilla.focus"
        const val KLAR = "org.mozilla.klar"   // variante alemana de Focus
    }

    private val a11y get() = FocusAccessibilityService.instance

    /** Devuelve el paquete de Focus instalado, o null */
    fun installedPackage(): String? =
        listOf(FOCUS, KLAR, "org.mozilla.focus.debug").firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }

    /** ¿Está activado nuestro servicio en Ajustes > Accesibilidad? */
    fun isServiceEnabled(): Boolean {
        val expected = "${context.packageName}/${FocusAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').let { s ->
            s.setString(enabled)
            s.any { it.equals(expected, ignoreCase = true) }
        }
    }

    fun openAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ------------------------------------------------------------------ Intent

    /** Abre una URL directamente en Firefox Focus */
    fun openUrl(url: String): Boolean {
        val pkg = installedPackage() ?: run {
            Log.w(FocusAccessibilityService.TAG, "Firefox Focus no está instalado")
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalize(url))).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun normalize(url: String) =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"

    // ------------------------------------------------- flujo con accesibilidad

    /**
     * Ejemplo de automatización: abre Focus, escribe en la barra y navega.
     * Llamar desde una corrutina (Dispatchers.Main sirve, las acciones son asíncronas).
     */
    suspend fun searchInFocus(query: String): Boolean {
        if (!openUrl("duckduckgo.com")) return false
        val svc = a11y ?: return false
        delay(1500)

        // 1. tocar la barra de direcciones
        val display = svc.waitFor {
            svc.findById(FocusAccessibilityService.ID_URL_DISPLAY)
                ?: svc.findById(FocusAccessibilityService.ID_URL_EDIT)
        } ?: return false
        svc.click(display)
        delay(500)

        // 2. escribir
        val edit = svc.waitFor { svc.findById(FocusAccessibilityService.ID_URL_EDIT) }
            ?: return false
        svc.write(edit, query)
        delay(300)

        // 3. enter
        svc.pressEnter(edit)
        delay(2000)

        // 4. scroll de resultados
        svc.scroll(forward = true)
        return true
    }

    /** Borra la sesión (botón de la papelera de Focus) */
    fun erase(): Boolean {
        val svc = a11y ?: return false
        return svc.clickById(FocusAccessibilityService.ID_ERASE) ||
                svc.click(svc.findByDesc("Borrar")) ||
                svc.click(svc.findByDesc("Erase"))
    }

    fun dump() = a11y?.dumpTree()

    // --------------------------------------------------- ciclo de aprobación de test

    /**
     * Un ciclo configurable: abre [url], espera que cargue, busca la fila que
     * contiene [rowName] y dentro de esa fila toca el botón "Test aprobar"
     * (ya que el texto del botón es idéntico en todas las filas y el
     * data-unique del DOM no es visible para accesibilidad, se ubica el botón
     * correcto subiendo desde el nombre de la fila). Luego toca el botón de
     * confirmación ([confirmText]) y espera el alert final de éxito
     * ([successText]). Llamar desde una corrutina.
     */
    suspend fun runTestApprovalCycle(
        url: String,
        rowName: String,
        buttonText: String,
        confirmText: String,
        successText: String
    ): Boolean {
        if (!openUrl(url)) return false
        val svc = a11y ?: return false
        delay(3000) // esperar carga inicial de la página

        // 1. buscar el botón [buttonText] dentro de la fila de [rowName]
        val target = svc.waitFor(timeoutMs = 15000) {
            svc.findButtonInRow(rowName, buttonText)
        } ?: return false
        svc.click(target)

        // 2. esperar y tocar el botón de confirmación
        val confirmButton = svc.waitFor(timeoutMs = 15000) { svc.findByText(confirmText) }
            ?: return false
        svc.click(confirmButton)

        // 3. esperar el alert final de éxito
        svc.waitFor(timeoutMs = 15000) { svc.findByText(successText) } ?: return false

        // 4. cerrar la página y volver
        svc.back()
        delay(1000)
        return true
    }

    /**
     * Repite [runTestApprovalCycle] mientras [shouldContinue] siga devolviendo
     * true y cada ciclo tenga éxito. Llamar desde una corrutina.
     */
    suspend fun runTestApprovalLoop(
        url: String,
        rowName: String,
        buttonText: String,
        confirmText: String,
        successText: String,
        shouldContinue: () -> Boolean
    ): Boolean {
        while (shouldContinue()) {
            if (!runTestApprovalCycle(url, rowName, buttonText, confirmText, successText)) return false
            delay(1500)
        }
        return true
    }
}
