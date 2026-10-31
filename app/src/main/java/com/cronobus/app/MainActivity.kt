package com.cronobus.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cronobus.app.bridge.CronoBusJsBridge
import com.cronobus.app.data.sync.StoreAndForwardSyncManager
import com.cronobus.app.service.LocationTrackingService
import com.cronobus.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Contenedor híbrido de CronoBus.
 *
 * La UI sigue siendo la SPA local de `assets/`, pero el `WebView` queda **endurecido** y conectado
 * al núcleo nativo:
 * - Sin acceso al sistema de archivos ni a `content://` (los assets se sirven por
 *   `file:///android_asset/`, que WebView mantiene disponible aunque `allowFileAccess = false`).
 * - `MIXED_CONTENT_NEVER_ALLOW`: ninguna petición HTTP plana puede colarse en la sesión segura.
 * - Navegación restringida al origen local (`file://` / `about:`); cualquier enlace externo se
 *   bloquea en lugar de abrir una página arbitraria dentro del contenedor con el puente expuesto.
 * - El puente `window.CronoBusNative` solo existe mientras este WebView local está montado.
 */
class MainActivity : ComponentActivity() {

    private lateinit var bridge: CronoBusJsBridge
    private lateinit var syncManager: StoreAndForwardSyncManager

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // El servicio exige ACCESS_FINE_LOCATION: la ubicación aproximada no basta para
        // un foregroundServiceType="location" con PRIORITY_HIGH_ACCURACY (Android 12+).
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        bridge.onLocationPermissionResult(fineGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        syncManager = StoreAndForwardSyncManager.getInstance(this)
        bridge = CronoBusJsBridge(this, syncManager = syncManager).apply {
            onLocationPermissionRequired = {
                if (!isFinishing && !isDestroyed) requestLocationPermission()
            }
        }

        observeNativeStreams()

        setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    WebViewScreen(
                        bridge = bridge,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun requestLocationPermission() {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            bridge.onLocationPermissionResult(granted = true)
            return
        }
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            // Auditoría Paso 3: en Android 13+ sin POST_NOTIFICATIONS la notificación persistente
            // del rastreo queda oculta (el conductor no ve que el GPS laboral está activo, lo
            // que contradice la transparencia exigida por LGPDPPSO). No bloquea el arranque.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        locationPermissionLauncher.launch(permissions.toTypedArray())
    }

    /**
     * Puentea los flujos del servicio y de la cola hacia la SPA. Se detiene cuando la Activity
     * deja de estar visible, pero el `ForegroundService` y el sincronizador siguen vivos.
     */
    private fun observeNativeStreams() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        syncManager.pendingCount,
                        syncManager.isOnline,
                        syncManager.isSyncing,
                    ) { pending, online, syncing -> Triple(pending, online, syncing) }
                        .collect { (pending, online, syncing) ->
                            bridge.pushSyncState(pending, online, syncing)
                        }
                }

                launch {
                    LocationTrackingService.updates.collect { update ->
                        bridge.pushTelemetry(update)
                    }
                }

                launch {
                    LocationTrackingService.geofenceEvents.collect { event ->
                        bridge.pushGeofenceEvent(event)
                    }
                }

                launch {
                    LocationTrackingService.tamperAlerts.collect { alert ->
                        bridge.pushTamperAlert(alert)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        bridge.close()
        super.onDestroy()
    }
}

private const val TAG = "CronoBusMainActivity"
private const val LOCAL_ENTRY_URL = "file:///android_asset/cronobus.html"

@Composable
fun WebViewScreen(
    bridge: CronoBusJsBridge? = null,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.WHITE)

                settings.apply {
                    // --- Funcionalidad estrictamente necesaria para la SPA local ---
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    textZoom = 100
                    loadWithOverviewMode = false
                    useWideViewPort = false
                    mediaPlaybackRequiresUserGesture = true

                    // --- Endurecimiento (OWASP MASVS / WebView) ---
                    // Los assets siguen accesibles vía file:///android_asset pese a desactivar
                    // el acceso al resto del sistema de archivos (documentado por Android).
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setGeolocationEnabled(false) // El GPS llega por el servicio nativo, no por JS.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }

                bridge?.let { addJavascriptInterface(it, CronoBusJsBridge.JS_INTERFACE_NAME) }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        if (isLocalOrigin(request.url)) return false
                        Log.w(
                            TAG,
                            "Navegación externa bloqueada dentro del WebView: ${request.url}",
                        )
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        bridge?.onPageReady()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(
                            "WebViewConsole",
                            "${consoleMessage.message()} -- From line " +
                                "${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}",
                        )
                        return true
                    }
                }

                bridge?.attach(this)
                loadUrl(LOCAL_ENTRY_URL)
            }
        },
        onRelease = { webView ->
            bridge?.detach(webView)
            webView.removeJavascriptInterface(CronoBusJsBridge.JS_INTERFACE_NAME)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        },
    )
}

private fun isLocalOrigin(uri: Uri): Boolean {
    // Auditoría Paso 3: antes se aceptaba cualquier `file://` y cualquier esquema hacia
    // appassets. Con el puente expuesto, el allowlist debe ser exacto.
    val scheme = uri.scheme?.lowercase()
    return when (scheme) {
        "file" -> uri.path?.startsWith(ANDROID_ASSET_PATH) == true
        "about" -> uri.toString() == "about:blank"
        "https" -> uri.host == "appassets.androidplatform.net"
        else -> false
    }
}

private const val ANDROID_ASSET_PATH = "/android_asset/"
