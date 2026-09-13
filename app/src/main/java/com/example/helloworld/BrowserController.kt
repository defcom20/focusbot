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
}
