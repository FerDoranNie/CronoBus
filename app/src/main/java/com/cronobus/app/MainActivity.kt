package com.cronobus.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.webkit.WebViewAssetLoader
import com.cronobus.app.bridge.CapturePhotoContract
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
 * - Los assets se sirven con `WebViewAssetLoader` en el origen HTTPS sintético
 *   `https://appassets.androidplatform.net/assets/`; así `localStorage`, el mapa offline y las
 *   rutas relativas funcionan sin conceder acceso al sistema de archivos ni depender de un CDN.
 * - `allowFileAccess = false` y `allowContentAccess = false`: el contenedor no puede leer archivos
 *   privados ni `content://` ajenos.
 * - `MIXED_CONTENT_NEVER_ALLOW`: ninguna petición HTTP plana puede colarse en la sesión segura.
 * - Navegación restringida al origen local (`appassets.../assets/` / `about:`); cualquier enlace
 *   externo se bloquea en lugar de abrir una página arbitraria dentro del contenedor con el
 *   puente expuesto.
 * - El puente `window.CronoBusNative` solo existe mientras este WebView local está montado.
 */
class MainActivity : ComponentActivity() {

    private lateinit var bridge: CronoBusJsBridge
    private lateinit var syncManager: StoreAndForwardSyncManager

    @Suppress("InvalidFragmentVersionForActivityResult")
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // El servicio exige ACCESS_FINE_LOCATION: la ubicación aproximada no basta para
        // un foregroundServiceType="location" con PRIORITY_HIGH_ACCURACY (Android 12+).
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        bridge.onLocationPermissionResult(fineGranted)
    }

    /**
     * Captura de evidencia con la app de cámara del sistema. El contrato propio concede el URI
     * de FileProvider a la cámara; al volver, el puente valida que el archivo no esté vacío.
     */
    @Suppress("InvalidFragmentVersionForActivityResult")
    private val takePictureLauncher = registerForActivityResult(CapturePhotoContract()) { success ->
        bridge.onPhotoCaptureResult(success)
    }

    /** URI pendiente mientras se concede el permiso de cámara. */
    private var pendingCaptureUri: Uri? = null

    @Suppress("InvalidFragmentVersionForActivityResult")
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (granted && uri != null) {
            launchCamera(uri)
        } else {
            bridge.onPhotoCaptureLaunchFailed("Permiso de cámara denegado.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auditoría Paso 3 (S3): el URI pendiente del permiso de cámara se perdía al rotar.
        pendingCaptureUri = savedInstanceState?.let {
            @Suppress("DEPRECATION")
            it.getParcelable<Uri>(STATE_PENDING_CAPTURE_URI)
        }

        syncManager = StoreAndForwardSyncManager.getInstance(this)
        bridge = CronoBusJsBridge(this, syncManager = syncManager).apply {
            onLocationPermissionRequired = {
                if (!isFinishing && !isDestroyed) requestLocationPermission()
            }
            onCapturePhotoRequested = { uri ->
                if (!isFinishing && !isDestroyed) {
                    requestCameraCapture(uri)
                } else {
                    bridge.onPhotoCaptureLaunchFailed("La Activity ya no está disponible.")
                }
            }
            onCsvReadyForShare = { uri ->
                if (!isFinishing && !isDestroyed) shareCsv(uri)
            }
        }

        bridge.restorePendingPhoto(savedInstanceState?.getString(STATE_PENDING_PHOTO_NAME))

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

    /** Pide CAMERA si hace falta y luego abre la app de cámara con el URI de FileProvider. */
    private fun requestCameraCapture(uri: Uri) {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera(uri)
            return
        }
        pendingCaptureUri = uri
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera(uri: Uri) {
        try {
            takePictureLauncher.launch(uri)
        } catch (notFound: ActivityNotFoundException) {
            bridge.onPhotoCaptureLaunchFailed(
                "No hay una app de cámara instalada en el dispositivo.",
            )
        } catch (failure: Throwable) {
            Log.e(TAG, "No se pudo abrir la cámara.", failure)
            bridge.onPhotoCaptureLaunchFailed("No se pudo abrir la cámara.")
        }
    }

    /** Comparte el CSV mediante una URI `content://` de FileProvider, sin exponer rutas. */
    private fun shareCsv(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(
                Intent.createChooser(shareIntent, "Exportar resumen financiero CronoBus"),
            )
        } catch (notFound: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No hay una app disponible para compartir el CSV.",
                Toast.LENGTH_LONG,
            ).show()
        } catch (failure: Throwable) {
            Log.e(TAG, "No se pudo abrir el selector para compartir el CSV.", failure)
            Toast.makeText(
                this,
                "No se pudo compartir el CSV generado.",
                Toast.LENGTH_LONG,
            ).show()
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCaptureUri?.let { outState.putParcelable(STATE_PENDING_CAPTURE_URI, it) }
        bridge.pendingPhotoFileName()?.let { outState.putString(STATE_PENDING_PHOTO_NAME, it) }
    }

    override fun onDestroy() {
        bridge.close()
        super.onDestroy()
    }
}

private const val TAG = "CronoBusMainActivity"
private const val STATE_PENDING_CAPTURE_URI = "cronobus.pendingCaptureUri"
private const val STATE_PENDING_PHOTO_NAME = "cronobus.pendingPhotoName"
private const val ASSET_LOADER_DOMAIN = "appassets.androidplatform.net"
private const val ASSET_PATH_PREFIX = "/assets/"
private const val LOCAL_ENTRY_URL = "https://appassets.androidplatform.net/assets/cronobus.html"

@Composable
fun WebViewScreen(
    bridge: CronoBusJsBridge? = null,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // Sirve los assets locales desde un origen HTTPS sintético, sin CDN ni file://.
            // El handler se crea por WebView para que su ciclo de vida siga al contenedor.
            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain(ASSET_LOADER_DOMAIN)
                .addPathHandler(ASSET_PATH_PREFIX, WebViewAssetLoader.AssetsPathHandler(context))
                .build()

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
                    // WebViewAssetLoader sirve los assets por HTTPS sintético; el resto del
                    // sistema de archivos y los content:// permanecen inaccesibles.
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
                    /**
                     * Auditoría Paso 3 (S4 — MASVS-PLATFORM): `addJavascriptInterface` expone el
                     * puente a **todo** script del documento. Antes `cdn.tailwindcss.com` y Google
                     * Fonts se descargaban y ejecutaban en el mismo frame que `CronoBusNative`
                     * (un CDN comprometido podía abrir turnos o firmar apelaciones). Ahora solo
                     * se sirve el origen local; cualquier otra petición recibe un 403 vacío y la
                     * SPA usa `classic_signal_offline.css` (regla de oro #1, offline-first).
                     */
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        if (isLocalOrigin(request.url)) {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }
                        Log.w(TAG, "Recurso remoto bloqueado en el WebView: ${request.url.host}")
                        return blockedResponse()
                    }

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
    // Auditoría Paso 3 / Fase 5: con el puente expuesto el allowlist debe ser exacto. Ya no se
    // acepta ningún `file://`; los assets llegan exclusivamente por WebViewAssetLoader.
    // Auditoría Paso 3 (S4): se rechazan segmentos `..` (codificados o no) para que una ruta como
    // `/assets/../x` no pase el prefijo; el AssetsPathHandler ya lo bloquea, esto es defensa doble.
    return when (uri.scheme?.lowercase()) {
        "https" -> uri.host == ASSET_LOADER_DOMAIN &&
            uri.port == -1 &&
            uri.path?.startsWith(ASSET_PATH_PREFIX) == true &&
            uri.pathSegments.none { it == ".." }
        "about" -> uri.toString() == "about:blank"
        else -> false
    }
}

/** Respuesta vacía para recursos remotos bloqueados (no deja que la petición salga a la red). */
private fun blockedResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Blocked",
        emptyMap(),
        java.io.ByteArrayInputStream(ByteArray(0)),
    )
