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
- [x] **1.3 Eliminación de Dependencias CDN Bloqueantes en WebView:**
  - [x] Empaquetar utilidades CSS offline de diseño (`classic_signal_offline.css`) en `app/src/main/assets/` para diseño 100% fiel sin conexión.
  - [x] Empaquetar librería `Leaflet.js` localmente en `app/src/main/assets/` (`leaflet_offline.js` + `leaflet_offline.css`).
- [x] **1.4 Endurecimiento de Seguridad en WebView (Completado en [`MainActivity.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/MainActivity.kt)):**
  - [x] Deshabilitar `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`, restringir `MIXED_CONTENT_NEVER_ALLOW` y bloquear navegación fuera de `https://appassets.androidplatform.net/assets/`.
  - [x] Migrar carga de entrada a `WebViewAssetLoader` (`https://appassets.androidplatform.net/assets/cronobus.html`) junto con `Leaflet.js` local en Fase 5.
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

## 📱 Fase 4: Lógica de Negocio en las 3 Vistas (Conductor, Admin, Pasajero) (Completado en Semana 3)
- [x] **4.1 Módulo Conductor (Operación Real):**
  - [x] Flujo de inicio de turno: firma de consentimiento legal LGPDPPSO + checklist rápido de mantenimiento preventivo (frenos, luces, aceite).
  - [x] Cálculo real de **Headway** dinámico ([`HeadwayCalculatorUseCase.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/domain/usecase/HeadwayCalculatorUseCase.kt)), persistencia automática serializada (`checkpointMutex`) de [`CheckpointEventEntity`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/entity/CheckpointEventEntity.kt) al cruzar geocercas (`RouteCatalog`) y actualización visual del Checador Digital.
  - [x] Estrategia híbrida de `ACCESS_BACKGROUND_LOCATION` en Android 14+ (guardia `hasBackgroundLocationPermission` en `START_STICKY` con `intent == null` + reanudación automática de turno abierto en `onPageReady` al volver al frente).
  - [x] Captura de evidencia fotográfica ([`DisputePhotoStore.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/export/DisputePhotoStore.kt) + [`CapturePhotoContract.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/bridge/CapturePhotoContract.kt) con `FileProvider` y restauración `onSaveInstanceState`) al pulsar **Apelar Multa**.
  - [x] Modo nocturno conmutable (`CronoBus Dark Mode`) en [`classic_signal_offline.css`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/classic_signal_offline.css) y [`cronobus.html`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/cronobus.html) persistido en `localStorage`.
- [x] **4.2 Módulo Administración y Despacho:**
  - [x] Algoritmo de **Exención Automática por Fuerza Mayor** ([`EvaluateForceMajeureUseCase.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/domain/usecase/EvaluateForceMajeureUseCase.kt)): anular multas en un tramo si $\ge 3$ unidades distintas reportan bloqueo/tráfico en un radio Haversine de $500\text{ m}$ dentro de una ventana de $15\text{ min}$.
  - [x] Algoritmo de rotación de salidas y despacho equitativo en bases ([`FairDispatchRotationUseCase.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/domain/usecase/FairDispatchRotationUseCase.kt)).
  - [x] Generación y exportación real de `.csv` ([`FinancialCsvExporter.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/export/FinancialCsvExporter.kt)) con métricas del Modelo Financiero Mixto (SaaS + Multas Brutas/Condonadas/Netas + Bonos) y neutralización anti-inyección de fórmulas OWASP.
- [x] **4.3 Módulo Pasajero (Público):**
  - [x] Selección funcional de ruta y parada con recálculo dinámico de ETA ([`PassengerEtaCalculatorUseCase.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/domain/usecase/PassengerEtaCalculatorUseCase.kt)) según velocidad media filtrada del tramo y retrasos/bloqueos reportados.
  - [x] Caché local (`localStorage` sincronizado: `cronobus_last_eta_quote` y `cronobus_last_telemetry`) de la última posición conocida y ETA estimado (`isStaleCache = true`) para resiliencia en pérdidas momentáneas de red/GPS.

---

## 🧪 Fase 5: Pruebas Automatizadas, Auditoría y QA (✅ Completado en Semana 4)
- [x] **5.1 Pruebas Unitarias (JUnit 4 + Coroutines Test):**
  - [x] Tests para `KalmanLocationFilter` (`KalmanLocationFilterTest`, `KalmanRobustnessTest`), `GeofenceEngine` (`GeofenceEngineTest`) y `TamperDetector` (`TamperDetectorTest`, `TamperClockSourceTest`) — **31 pruebas en verde**.
  - [x] Tests para regla de exención por fuerza mayor ([`EvaluateForceMajeureUseCaseTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/domain/usecase/EvaluateForceMajeureUseCaseTest.kt)), cálculo de headway/multas ([`HeadwayCalculatorUseCaseTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/domain/usecase/HeadwayCalculatorUseCaseTest.kt)), rotación de despacho ([`FairDispatchRotationUseCaseTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/domain/usecase/FairDispatchRotationUseCaseTest.kt)), ETA de pasajero ([`PassengerEtaCalculatorUseCaseTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/domain/usecase/PassengerEtaCalculatorUseCaseTest.kt)) y exportación segura CSV ([`FinancialCsvExporterTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/data/export/FinancialCsvExporterTest.kt)).
- [x] **5.2 Pruebas de Base de Datos y Store & Forward (Robolectric + Room):**
  - [x] `StoreAndForwardSyncManagerTest` (4 tests en verde: vaciado cronológico global `UNION ALL`, retención ante falla transitoria, re-encolado de turno cerrado en vuelo y guardia `SYNCING`).
  - [x] Pruebas de persistencia de cruces de geocerca, consultas espaciales de Fuerza Mayor en Room y validación de migración no destructiva `1 → 2` y `2 → 3` ([`CronoBusDatabaseMigrationTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/data/local/CronoBusDatabaseMigrationTest.kt)) + [`FinancialLedgerDaoTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/data/local/dao/FinancialLedgerDaoTest.kt).
- [x] **5.3 Pruebas de Regresión Visual (Roborazzi):**
  - [x] Migrar [`GreetingScreenshotTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/GreetingScreenshotTest.kt) a `com.cronobus.app` con tema *Classic Signal*.
  - [x] Expandir componentes Compose de referencia visual ([`CronoBusDesignShowcase.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/ui/components/CronoBusDesignShowcase.kt)) y capturas de pantalla Roborazzi para las 3 vistas principales (Conductor, Admin, Pasajero + Dark Mode) verificadas con `recordRoborazziDebug` y `verifyRoborazziDebug`.
- [x] **5.4 Pendientes Registrados en Auditoría del Paso 3 (Semana 3 → Semana 4):** *(implementado en Paso 2, auditado en Paso 3 y validado en Paso 4 — Semana 4)*
  - [x] Persistir el *ledger* financiero de sesión ([`FinancialLedgerEntity.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/entity/FinancialLedgerEntity.kt) + [`FinancialLedgerDao.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/local/dao/FinancialLedgerDao.kt) + migración Room `2 → 3`) para que sanciones y condonaciones por unidad sobrevivan a muerte del proceso antes de exportar el `.csv`.
    - Auditoría S4: `accumulateAssessment` devuelve la fila persistida y la caché del puente refleja Room (sin suma paralela en memoria); hidratación con `ledgerMutex` **de proceso** (antes por instancia → carrera tras rotación). Orden de bloqueo `dispatchMutex → ledgerMutex`.
    - Migración `2 → 3` validada **abriendo una base Room real** (`onValidateSchema`), reapertura en frío y rechazo sin migración (`IllegalStateException`, evidencia intacta).
  - [x] Migrar el directorio de `dispute_photos` de `cache-path` a `files-path` en [`file_paths.xml`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/res/xml/file_paths.xml) y [`DisputePhotoStore.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/cronobus/app/data/export/DisputePhotoStore.kt) para evitar que el sistema operativo purgue evidencias fotográficas pendientes de sincronizar bajo presión de almacenamiento ([`DisputePhotoStoreTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/cronobus/app/data/export/DisputePhotoStoreTest.kt)).
  - [x] Completar empaquetado local de `Leaflet.js` ([`leaflet_offline.js`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/leaflet_offline.js) + [`leaflet_offline.css`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/leaflet_offline.css) en `assets/`, motor de mapa vectorial/SVG offline 100% compatible con la API `L.map` / `L.tileLayer` / `L.polyline` / `L.circle` / `L.marker` / `L.divIcon`) y migración de entrada a `WebViewAssetLoader` (`https://appassets.androidplatform.net/assets/cronobus.html`) con `androidx.webkit:webkit`.
    - Auditoría S4 (MASVS-PLATFORM): se bloquea todo recurso fuera del origen local en `shouldInterceptRequest` (403 vacío); retirados Tailwind CDN y Google Fonts que se ejecutaban junto a `window.CronoBusNative`. Allowlist endurecido (puerto, segmentos `..`).
  - [x] **Validación Paso 4 (Argon):** verificado que [`classic_signal_offline.css`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/classic_signal_offline.css) cubre el 100% de las utilidades de las 3 vistas y modales sin Tailwind CDN (`gap-1.5`, `gap-2.5`, `p-2.5`, `p-5`, `px-1.5`, `px-2.5`, `pt-2`, `pb-1`, `pb-3`, `mt-0.5`, `mt-1`, `max-w-lg`, `max-h-[90vh]`, `w-2.5`, `h-2.5`, `w-4`, `h-4`, `min-w-0`, `z-[3000]`, `bg-[#0a0a0a]/60`, `italic`, `truncate`, `tracking-wide`, `leading-relaxed`, `border-[#0a0a0a]`, `border-[#444748]`, `accent-[#0b6c4b]`, `cursor-pointer`) y que las 5 capturas Roborazzi (Modo Claro y Modo Nocturno) pasan al 100%.
- [x] **5.5 Verificación de Build & Lint:**
  - [x] Semana 1: `./gradlew testDebugUnitTest assembleDebug` en verde (49 tareas ejecutadas, 31 tests OK).
  - [x] Semana 2: `./gradlew testDebugUnitTest assembleDebug` en verde (49 tareas ejecutadas, 35 tests OK, KSP Room + Robolectric OK).
  - [x] Semana 3: `./gradlew testDebugUnitTest assembleDebug` en verde (66 tests unitarios/Robolectric OK, migración Room 1→2 validada y APK Debug ensamblado).
  - [x] Cierre final (Semana 4): `./gradlew lintDebug`, `./gradlew verifyRoborazziDebug`, `./gradlew testDebugUnitTest` y `./gradlew assembleDebug` en verde (**78 pruebas ejecutadas, 0 fallos, 0 errores de Lint**).
