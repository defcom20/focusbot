package com.example.helloworld

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Servicio que observa y controla Firefox Focus.
 * Se expone como singleton para que BrowserController pueda usarlo.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "FocusBot"

        @Volatile
        var instance: FocusAccessibilityService? = null
            private set

        /** IDs de la toolbar de Mozilla Android Components (verificar con dumpTree si cambian) */
        const val ID_URL_EDIT = "org.mozilla.focus:id/mozac_browser_toolbar_edit_url_view"
        const val ID_URL_DISPLAY = "org.mozilla.focus:id/mozac_browser_toolbar_url_view"
        const val ID_ERASE = "org.mozilla.focus:id/erase"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AccessibilityService conectado")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    /** Aquí llegan los eventos de UI de Focus (detect UI) */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "ventana: ${event.packageName} / ${event.className}")
        }
    }

    // ---------------------------------------------------------------- búsqueda

    private fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    fun findById(viewId: String): AccessibilityNodeInfo? =
        root()?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()

    fun findByText(text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val r = root() ?: return null
        if (exact) {
            return r.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString() == text }
        }
        return r.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    fun findByDesc(desc: String): AccessibilityNodeInfo? =
        walk(root()).firstOrNull { it.contentDescription?.toString()?.contains(desc, true) == true }

    /** Espera hasta [timeoutMs] a que aparezca un nodo. Llamar desde una corrutina. */
    suspend fun waitFor(
        timeoutMs: Long = 8000,
        intervalMs: Long = 200,
        finder: () -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        val limit = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < limit) {
            finder()?.let { return it }
            delay(intervalMs)
        }
        return null
    }

    private fun walk(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        node ?: return emptyList()
        val out = mutableListOf(node)
        for (i in 0 until node.childCount) out += walk(node.getChild(i))
        return out
    }

    // ---------------------------------------------------------------- acciones

    /** click: si el nodo no es clickeable, sube por los padres buscando uno que sí lo sea */
    fun click(node: AccessibilityNodeInfo?): Boolean {
        var n = node
        var hops = 0
        while (n != null && hops < 6) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            hops++
        }
        // último recurso: tap por coordenadas
        return node?.let { tapCenterOf(it) } ?: false
    }

    fun clickById(viewId: String) = click(findById(viewId))
    fun clickByText(text: String) = click(findByText(text))

    /** write: escribe en un campo de texto */
    fun write(node: AccessibilityNodeInfo?, text: String): Boolean {
        node ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Enter / "Ir" del teclado (API 30+); si no, intenta el botón de la UI */
    fun pressEnter(node: AccessibilityNodeInfo?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && node != null) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER.id)) return true
        }
        return clickByText("Ir") || clickByText("Go")
    }

    /** scroll con la acción nativa; si ningún nodo es scrollable, hace swipe por gesto */
    fun scroll(forward: Boolean = true): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val scrollable = walk(root()).firstOrNull { it.isScrollable }
        if (scrollable?.performAction(action) == true) return true
        return swipe(forward)
    }

    fun swipe(up: Boolean = true, durationMs: Long = 300): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y1 = if (up) dm.heightPixels * 0.75f else dm.heightPixels * 0.30f
        val y2 = if (up) dm.heightPixels * 0.30f else dm.heightPixels * 0.75f
        val path = Path().apply { moveTo(x, y1); lineTo(x, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun tapCenterOf(node: AccessibilityNodeInfo): Boolean {
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.width() == 0 || r.height() == 0) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(g, null, null)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)

    // ---------------------------------------------------------------- debug

    /** Imprime el árbol de la pantalla en Logcat. Úsalo para descubrir los viewIds reales. */
    fun dumpTree() {
        fun rec(n: AccessibilityNodeInfo?, depth: Int) {
            n ?: return
            Log.d(TAG, "  ".repeat(depth) +
                    "${n.className} id=${n.viewIdResourceName} text='${n.text}' " +
                    "desc='${n.contentDescription}' click=${n.isClickable}")
            for (i in 0 until n.childCount) rec(n.getChild(i), depth + 1)
        }
        rec(root(), 0)
    }
}
