# 📋 CronoBus — Backlog Maestro y Lista de Tareas (`to_do.md`)

Este documento rastrea el estado real de implementación de **CronoBus**, separando lo que ya existe a nivel prototipo/documentación de las tareas de ingeniería necesarias para convertir la aplicación Android en un **MVP de Producción**.

---

## ✅ Fase 0: Cimientos, Diseño y Prototipo Base (Completado)
- [x] Definición de alcance operativo y requerimientos legales LGPDPPSO / LFT ([`Proposito_APP_Rutas.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Proposito_APP_Rutas.md)).
- [x] Modelado financiero a 36 meses bajo esquema SaaS Mixto ([`Informe_Modelo_Financiero_Mixto.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Informe_Modelo_Financiero_Mixto.md)).
- [x] Especificación de Sistemas de Diseño: Classic Signal ([`Design_green.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Designs/Design_green.md)), Tránsito Pro ([`Design_blue.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Designs/Design_blue.md)) y Dark Mode ([`Design_dark.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Designs/Design_dark.md)).
- [x] Maquetas HTML de referencia generadas en Stitch (`stitch_gesti_n_digital_de_transporte/`).
- [x] Prototipo funcional SPA con las 3 vistas y simulador básico en [`cronobus.html`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/cronobus.html).
- [x] Configuración inicial de proyecto Android con Gradle Version Catalog ([`libs.versions.toml`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/gradle/libs.versions.toml)) y contenedor `WebView` en [`MainActivity.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/example/MainActivity.kt).

---

## 🔧 Fase 1: Saneamiento de Configuración Android & Empaquetado Offline
- [ ] **1.1 Identidad del Paquete:** Migrar `namespace` y `applicationId` de `com.example` a `com.cronobus.app` en [`app/build.gradle.kts`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/build.gradle.kts) y estructura de carpetas.
- [ ] **1.2 Permisos en Manifiesto:** Declarar en [`AndroidManifest.xml`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/AndroidManifest.xml):
  - `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`
  - `ACCESS_BACKGROUND_LOCATION`
  - `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_LOCATION`
  - `ACCESS_NETWORK_STATE`
  - `CAMERA` (para evidencia de apelación de multas)
  - `POST_NOTIFICATIONS` (para alertas de despacho y headway)
- [ ] **1.3 Eliminación de Dependencias CDN Bloqueantes en WebView:**
  - Empaquetar Tailwind compilado (CSS estático), fuente `Inter` y librería `Leaflet.js` localmente en `app/src/main/assets/` para que la app no falle al abrirse sin internet.
- [ ] **1.4 Endurecimiento de Seguridad en WebView:**
  - Deshabilitar `allowFileAccess = true` innecesario, implementar `WebViewAssetLoader` (`https://appassets.androidplatform.net/`) y restringir `MIXED_CONTENT_ALWAYS_ALLOW`.
- [ ] **1.5 Tema Compose Classic Signal:**
  - Reemplazar los colores por defecto (`Purple80`, `Pink40`) en [`Color.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/example/ui/theme/Color.kt) y [`Theme.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/example/ui/theme/Theme.kt) por los tokens oficiales de **Classic Signal** y **CronoBus Dark Mode**.

---

## 🛰️ Fase 2: Motor GPS Nativo, Filtro de Kalman y Geocercas
- [ ] **2.1 Descomentar y Configurar Play Services Location:** Activar `libs.play.services.location` y `libs.accompanist.permissions` en `app/build.gradle.kts`.
- [ ] **2.2 Servicio de Ubicación en Primer Plano (`LocationTrackingService`):**
  - Implementar `ForegroundService` con notificación persistente del turno activo para evitar que Android mate el rastreo cuando el chofer apague la pantalla o cambie de app.
- [ ] **2.3 Filtro de Kalman 2D (`KalmanLocationFilter.kt`):**
  - Implementar suavizado de coordenadas $(lat, lng, velocidad, accuracy)$ para mitigar saltos por "cañón urbano".
- [ ] **2.4 Motor de Geocercas Poligonales y Circulares (`GeofenceEngine.kt`):**
  - Detección matemática de entrada/salida (Haversine + Ray-Casting para polígonos) con histéresis de tiempo (permanencia mínima de 5s) para evitar lecturas dobles.
- [ ] **2.5 Detector Anti-Sabotaje (`TamperDetectionManager.kt`):**
  - Detectar `Location.isMock()` / `isFromMockProvider()`.
  - Detectar apagado manual del proveedor GPS o modo avión recurrente a menos de 300m de un punto de control.
  - Registrar nivel de batería al momento de desconexión para distinguir descarga real vs. apagado inducido.

---

## 💾 Fase 3: Persistencia Local (Store & Forward) y Sincronización
- [ ] **3.1 Base de Datos Room (`CronoBusDatabase.kt`):**
  - Entidad `CheckpointEventEntity` (cruce de geocerca, timestamp UTC, lat/lng filtrada, desvío vs. horario, estado de sincronización).
  - Entidad `IncidenceReportEntity` (tipo: Tráfico/Bloqueo/Accidente/Falla, lat/lng, foto URI opcional, timestamp, estado sync).
  - Entidad `FineDisputeEntity` (id multa, motivo, evidencia URI, telemetría adjunta, estado).
  - Entidad `ShiftLogEntity` (inicio/fin de turno, checklist de mantenimiento preventivo, firma digital de consentimiento LFT/LGPDPPSO).
- [ ] **3.2 Cola de Sincronización Resiliente (`SyncWorker` / `StoreAndForwardManager`):**
  - Monitorear conectividad real con `ConnectivityManager.NetworkCallback`.
  - Al recuperar red, enviar lotes pendientes en orden cronológico con reintentos exponenciales (Exponential Backoff).
- [ ] **3.3 Puente Bidireccional Nativo ↔ WebView (`CronoBusJsBridge.kt`) / Estado UI:**
  - Exponer `@JavascriptInterface` seguro (o migrar vistas a Compose) para que la UI reciba telemetría GPS real, estado de cola offline (`Store & Forward`) y dispare guardado real en `Room`.

---

## 📱 Fase 4: Lógica de Negocio en las 3 Vistas (Conductor, Admin, Pasajero)
- [ ] **4.1 Módulo Conductor (Operación Real):**
  - Flujo de inicio de turno: firma de consentimiento legal LGPDPPSO + checklist rápido de mantenimiento preventivo (frenos, luces, aceite).
  - Cálculo real de **Headway** dinámico y actualización visual del Checador Digital al cruzar geocercas.
  - Captura de evidencia fotográfica (vía CameraX o Intent seguro) al pulsar **Apelar Multa**.
  - Modo nocturno automático (`CronoBus Dark Mode`) para conducción segura de noche.
- [ ] **4.2 Módulo Administración y Despacho:**
  - Algoritmo de **Exención Automática por Fuerza Mayor**: anular multas en un tramo si $\ge 3$ unidades reportan bloqueo/tráfico en un radio de $500\text{ m}$ dentro de una ventana de $15\text{ min}$.
  - Algoritmo de rotación de salidas y despacho equitativo en bases.
  - Generación y descarga real de archivo `.csv` con métricas del Modelo Financiero Mixto (SaaS + Multas + Bonos + Exenciones).
- [ ] **4.3 Módulo Pasajero (Público):**
  - Selección funcional de ruta y parada con recálculo dinámico de ETA según velocidad media del tramo y retrasos reportados.
  - Caché en dispositivo de la última posición conocida y ETA estimado para resiliencia en pérdidas momentáneas de red.

---

## 🧪 Fase 5: Pruebas Automatizadas, Auditoría y QA
- [ ] **5.1 Pruebas Unitarias (JUnit 4 + Coroutines Test):**
  - Tests para `KalmanLocationFilter`, `GeofenceEngine`, regla de exención por fuerza mayor (3 unidades / 500m / 15m) y cálculo de headway/multas.
- [ ] **5.2 Pruebas de Base de Datos y Store & Forward (Robolectric + Room):**
  - Validar encolado sin red, persistencia tras reinicio y vaciado ordenado al reconectar.
- [ ] **5.3 Pruebas de Regresión Visual (Roborazzi):**
  - Actualizar [`GreetingScreenshotTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/example/GreetingScreenshotTest.kt) hacia tests de captura de pantalla reales (`CronoBusScreenshotTest.kt`) para las vistas principales.
- [ ] **5.4 Verificación de Build & Lint:**
  - `./gradlew lintDebug`, `./gradlew testDebugUnitTest` y `./gradlew assembleDebug` sin warnings críticos ni errores.
