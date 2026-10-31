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
- [x] **1.1 Identidad del Paquete:** Migrar `namespace` y `applicationId` de `com.example` a `com.cronobus.app` en [`app/build.gradle.kts`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/build.gradle.kts) y estructura de carpetas.
- [x] **1.2 Permisos en Manifiesto:** Declarar en [`AndroidManifest.xml`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/AndroidManifest.xml):
  - `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`
  - `ACCESS_BACKGROUND_LOCATION`
  - `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_LOCATION`
  - `ACCESS_NETWORK_STATE`
  - `CAMERA` (para evidencia de apelación de multas)
  - `POST_NOTIFICATIONS` (para alertas de despacho y headway)
- [ ] **1.3 Eliminación de Dependencias CDN Bloqueantes en WebView:**
  - [x] Empaquetar utilidades CSS offline de diseño (`classic_signal_offline.css`) en `app/src/main/assets/` para diseño 100% fiel sin conexión.
  - [ ] Empaquetar librería `Leaflet.js` localmente en `app/src/main/assets/`.
- [x] **1.4 Endurecimiento de Seguridad en WebView (Completado en [`MainActivity.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/MainActivity.kt); pendiente empaquetado local de Leaflet en 1.3):**
  - [x] Deshabilitar `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`, restringir `MIXED_CONTENT_NEVER_ALLOW` y bloquear navegación fuera de `file:///android_asset/` / `https://appassets.androidplatform.net/`.
  - [ ] Migrar carga de entrada a `WebViewAssetLoader` (`https://appassets.androidplatform.net/`) junto con `Leaflet.js` local en Fase 5.
- [x] **1.5 Tema Compose Classic Signal:**
  - Reemplazar los colores por defecto (`Purple80`, `Pink40`) en [`Color.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/ui/theme/Color.kt) y [`Theme.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/ui/theme/Theme.kt) por los tokens oficiales de **Classic Signal** y **CronoBus Dark Mode**.

---

## 🛰️ Fase 2: Motor GPS Nativo, Filtro de Kalman y Geocercas (Completado en Semana 1)
- [x] **2.1 Descomentar y Configurar Play Services Location:** Activar `libs.play.services.location`, `libs.accompanist.permissions`, DataStore y CameraX en `app/build.gradle.kts`.
- [x] **2.2 Servicio de Ubicación en Primer Plano (`LocationTrackingService`):**
  - Implementar `ForegroundService` (`foregroundServiceType="location"`) con notificación persistente del turno activo, acción de parada (`LocationTrackingActionReceiver`), compuerta `@Volatile acceptingFixes` y contrato seguro `start(context): Boolean`.
- [x] **2.3 Filtro de Kalman 2D (`KalmanLocationFilter.kt`):**
  - Implementar suavizado de coordenadas $(lat, lng, v_{\text{Norte}}, v_{\text{Este}})$ con manejo seguro de `Double.NaN` en velocidad/rumbo, rechazo de *teleports* y re-anclaje automático.
- [x] **2.4 Motor de Geocercas Poligonales y Circulares (`GeofenceEngine.kt` + `GeoMath.kt`):**
  - Detección matemática de entrada/salida (Haversine + Ray-Casting para polígonos) con histéresis temporal de permanencia mínima (`dwellMillis = 5000L`) y banda muerta de salida (`exitHysteresisMeters = 10.0`).
- [x] **2.5 Detector Anti-Sabotaje (`TamperDetector.kt`):**
  - Detectar `Location.isMock()` / `isFromMockProvider()`.
  - Detectar manipulación del reloj del dispositivo comparando `wallClockNowMs` vs. `elapsedRealtimeNanos`.
  - Detectar apagado sospechoso del proveedor GPS a $\le 300\text{ m}$ de un punto de control con batería $> 15\%$.

---

## 💾 Fase 3: Persistencia Local (Store & Forward), Seguridad WebView y Puente JS (Completado en Semana 2)
- [x] **3.1 Base de Datos Room ([`CronoBusDatabase.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/CronoBusDatabase.kt)):**
  - Enums y TypeConverters ([`CronoBusTypeConverters.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/CronoBusTypeConverters.kt): `SyncStatus`, `OccupancyLevel`, `IncidenceType`).
  - Entidad [`CheckpointEventEntity`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/entity/CheckpointEventEntity.kt) + [`CheckpointEventDao`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/dao/CheckpointEventDao.kt).
  - Entidad [`IncidenceReportEntity`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/entity/IncidenceReportEntity.kt) + [`IncidenceReportDao`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/dao/IncidenceReportDao.kt) (incluye consulta bounding-box para Fuerza Mayor).
  - Entidad [`FineDisputeEntity`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/entity/FineDisputeEntity.kt) + [`FineDisputeDao`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/dao/FineDisputeDao.kt).
  - Entidad [`ShiftLogEntity`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/entity/ShiftLogEntity.kt) + [`ShiftLogDao`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/dao/ShiftLogDao.kt) y proyección global [`SyncQueueDao`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/dao/SyncQueueDao.kt) (`UNION ALL` cronológico).
- [x] **3.2 Cola de Sincronización Resiliente ([`StoreAndForwardSyncManager.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/sync/StoreAndForwardSyncManager.kt)):**
  - Monitorear conectividad real validada (`NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED`) con `ConnectivityManager.NetworkCallback`.
  - Vaciado en orden cronológico global (`timestampUtcMs ASC, elapsedRealtimeNanos ASC`), recuperación de filas `SYNCING`, guardia `WHERE syncStatus = 'SYNCING'` contra condiciones de carrera y *Exponential Backoff* con interrupción inmediata al reconectar.
- [x] **3.3 Puente Bidireccional Nativo ↔ WebView ([`CronoBusJsBridge.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/bridge/CronoBusJsBridge.kt)) / Estado UI:**
  - Exponer `@JavascriptInterface` seguro (`window.CronoBusNative`) con `processScope` sobreviviente a rotación, serialización inmune a inyección (`JSONObject.quote`), modal LFT/LGPDPPSO y badge `SYNC: N PEND` en [`cronobus.html`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/cronobus.html).

---

## 📱 Fase 4: Lógica de Negocio en las 3 Vistas (Conductor, Admin, Pasajero) (🔄 En Curso — Semana 3)
- [ ] **4.1 Módulo Conductor (Operación Real):**
  - [x] Flujo de inicio de turno: firma de consentimiento legal LGPDPPSO + checklist rápido de mantenimiento preventivo (frenos, luces, aceite).
  - [ ] Cálculo real de **Headway** dinámico (`HeadwayCalculatorUseCase`), persistencia automática de `CheckpointEventEntity` al cruzar geocercas y actualización visual del Checador Digital.
  - [ ] Estrategia híbrida de `ACCESS_BACKGROUND_LOCATION` en Android 14+ (guardia en `START_STICKY` con `intent == null` + reanudación automática de turno abierto al volver al frente).
  - [ ] Captura de evidencia fotográfica (URI local segura) al pulsar **Apelar Multa**.
  - [ ] Modo nocturno automático / conmutable (`CronoBus Dark Mode`) para conducción segura de noche.
- [ ] **4.2 Módulo Administración y Despacho:**
  - [ ] Algoritmo de **Exención Automática por Fuerza Mayor** (`EvaluateForceMajeureUseCase`): anular multas en un tramo si $\ge 3$ unidades distintas reportan bloqueo/tráfico en un radio de $500\text{ m}$ dentro de una ventana de $15\text{ min}$.
  - [ ] Algoritmo de rotación de salidas y despacho equitativo en bases (`FairDispatchRotationUseCase`).
  - [ ] Generación y exportación real de `.csv` con métricas del Modelo Financiero Mixto (SaaS + Multas + Bonos + Exenciones).
- [ ] **4.3 Módulo Pasajero (Público):**
  - [ ] Selección funcional de ruta y parada con recálculo dinámico de ETA (`PassengerEtaCalculatorUseCase`) según velocidad media filtrada del tramo y retrasos/bloqueos reportados.
  - [ ] Caché local (`DataStore` / `localStorage` sincronizado) de la última posición conocida y ETA estimado para resiliencia en pérdidas momentáneas de red.

---

## 🧪 Fase 5: Pruebas Automatizadas, Auditoría y QA
- [ ] **5.1 Pruebas Unitarias (JUnit 4 + Coroutines Test):**
  - [x] Tests para `KalmanLocationFilter` (`KalmanLocationFilterTest`, `KalmanRobustnessTest`), `GeofenceEngine` (`GeofenceEngineTest`) y `TamperDetector` (`TamperDetectorTest`, `TamperClockSourceTest`) — **31 pruebas en verde**.
  - [ ] Tests para regla de exención por fuerza mayor (3 unidades / 500m / 15m), cálculo de headway/multas, rotación de despacho y ETA de pasajero.
- [ ] **5.2 Pruebas de Base de Datos y Store & Forward (Robolectric + Room):**
  - [x] `StoreAndForwardSyncManagerTest` (4 tests en verde: vaciado cronológico global `UNION ALL`, retención ante falla transitoria, re-encolado de turno cerrado en vuelo y guardia `SYNCING`).
  - [ ] Ampliar pruebas de persistencia de cruces de geocerca y consultas espaciales de Fuerza Mayor en Room.
- [ ] **5.3 Pruebas de Regresión Visual (Roborazzi):**
  - [x] Migrar [`GreetingScreenshotTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/GreetingScreenshotTest.kt) a `com.cronobus.app` con tema *Classic Signal*.
  - [ ] Expandir capturas de pantalla para las vistas principales.
- [ ] **5.4 Verificación de Build & Lint:**
  - [x] Semana 1: `./gradlew testDebugUnitTest assembleDebug` en verde (49 tareas ejecutadas, 31 tests OK).
  - [x] Semana 2: `./gradlew testDebugUnitTest assembleDebug` en verde (49 tareas ejecutadas, 35 tests OK, KSP Room + Robolectric OK).
  - [ ] Cierre final: `./gradlew lintDebug`, `./gradlew testDebugUnitTest` y `./gradlew assembleDebug` sin warnings críticos ni errores.
