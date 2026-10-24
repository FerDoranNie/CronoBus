# 👨‍💻 Lo Que Le Toca al Developer (`lo_que_le_toca_al_developer.md`)

Este documento define las responsabilidades exclusivas del desarrollador humano dentro del flujo **Human-in-the-Loop (HITL)** de **CronoBus**, junto con una guía curada de cursos, lecturas técnicas y recursos de **Android, Arquitectura de Software y Ciberseguridad Móvil** directamente aplicados a este proyecto.

---

## 🛠️ Parte 1: Lo que Ningún Agente Puede Hacer por Ti (Tus Tareas en el Proyecto)

Aunque los agentes (**Gemini Argon, DeepSeek, Gemini 3.8 Flash y Claude Opus**) escriban y depuren el código, tú eres el **Director Técnico (Human-in-the-Loop)**. Estas son tus responsabilidades operativas:

### 1. Orquestación Manual de Turnos (Cambio de Modelo en el IDE / Chat)
Como no se utiliza un orquestador automático externo, tú conduces la línea de ensamblaje cambiando el selector de modelo en cada paso:
1. Seleccionas **Gemini 4 Argon** para abrir el bloque y validar el estado.
2. Cambias a **Gemini 3.8 Flash** (tareas rápidas de configuración/UI, 25%) y le das el prompt del paso correspondiente.
3. Cambias a **DeepSeek** (tareas pesadas de motor nativo/Room/GPS, 75%) y le das el prompt de su paso.
4. Cambias a **Claude Opus** para que revise los archivos recién creados, corrija errores de compilación y blinde la seguridad.
5. Vuelves a **Gemini 4 Argon** para que ejecute los tests finales (`./gradlew testDebugUnitTest`, `./gradlew assembleDebug`) y marque el avance en [`to_do.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/to_do.md).

### 2. Gestión de Secretos, Certificados y Cuentas Externas
- **Archivo `.env` y `local.properties`:** Configurar las llaves reales (API Keys de mapas o endpoints de backend) basándote en [`.env.example`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/.env.example) sin subirlas jamás a Git.
- **Keystore de Producción (`my-upload-key.jks`):** Generar tu llave criptográfica con `keytool` para firmar el APK/Bundle de lanzamiento configurado en [`app/build.gradle.kts`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/build.gradle.kts#L26-L34).
- **Proyecto Firebase (`google-services.json`):** Crear el proyecto en Firebase Console si decides activar Firestore, Auth y App Check.

### 3. Pruebas Físicas y de Campo (Validación en el Mundo Real)
- Probar el APK en un **teléfono Android físico** dentro de un vehículo en movimiento para sentir la latencia del GPS real, el consumo de batería del `ForegroundService` y la legibilidad de la interfaz **Classic Signal** bajo la luz del sol.
- Simular rutas GPX en el emulador de Android Studio siguiendo [`TUTORIAL_PRUEBAS_ANDROID_STUDIO.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/TUTORIAL_PRUEBAS_ANDROID_STUDIO.md).

---

## 📚 Parte 2: Ruta de Estudio en Desarrollo Android (Aplicada a CronoBus)

Para auditar con criterio lo que escriben DeepSeek y Claude Opus, te conviene dominar estos 4 pilares de Android moderno:

### 1. Fundamentos de Kotlin, Coroutines y Jetpack Compose
*¿Por qué lo necesitas en CronoBus?* Toda la concurrencia de la base de datos local (`Room`), el flujo de coordenadas GPS (`Flow<Location>`) y el contenedor visual usan Kotlin moderno.
- **Curso Oficial (Gratis):** [Android Basics with Compose (Google Developers)](https://developer.android.com/courses/android-basics-compose/course) — Ideal para dominar el ciclo de vida de `ComponentActivity` ([`MainActivity.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/example/MainActivity.kt)) y estados en Compose.
- **Lectura Esencial:** [Kotlin Coroutines & StateFlow on Android](https://developer.android.com/kotlin/coroutines) — Aprende cómo funcionan `Dispatchers.IO` (para base de datos) y `Dispatchers.Main` (para actualizar el `WebView`).

### 2. Arquitectura Offline-First y Persistencia con Room
*¿Por qué lo necesitas en CronoBus?* El corazón de la resiliencia *Store & Forward* es guardar cruces de geocercas en SQLite cuando la combi se queda sin datos móviles y sincronizarlos después.
- **Guía Oficial de Arquitectura:** [Guide to App Architecture & Offline-first Apps (Android Developers)](https://developer.android.com/topic/architecture/data-layer/offline-first) — Lectura obligatoria sobre cómo diseñar colas de sincronización y resolución de conflictos.
- **Codelab Práctico:** [Room with a View (Kotlin)](https://developer.android.com/codelabs/android-room-with-a-view-kotlin) — Entiende `@Entity`, `@Dao`, `@Database` y migraciones de esquemas.

### 3. Geolocalización en Segundo Plano (`FusedLocationProvider` y `ForegroundService`)
*¿Por qué lo necesitas en CronoBus?* Desde Android 10 hasta Android 16 (SDK 36), el sistema operativo mata agresivamente cualquier app que intente leer el GPS con la pantalla apagada si no declara correctamente un `ForegroundService` de tipo `location`.
- **Documentación Oficial:** [Request location updates in the background](https://developer.android.com/develop/sensors-and-location/location/request-updates) y [Foreground service types (Android 14+)](https://developer.android.com/about/versions/14/changes/fgs-types-required).
- **Lectura Matemática (Filtro de Kalman & Geocercas):**
  - *Kalman Filter in 1D/2D for Beginners:* Busca el repositorio interactivo **"Kalman-and-Bayesian-Filters-in-Python" de Roger Labbe** (Capítulos 1 a 4, gratuitos en GitHub) para entender intuitivamente cómo combinar la incertidumbre del sensor GPS (`accuracy`) con la velocidad del vehículo.

### 4. Aplicaciones Híbridas Avanzadas (`WebView` + `@JavascriptInterface`)
*¿Por qué lo necesitas en CronoBus?* Tu interfaz vive en [`cronobus.html`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/cronobus.html) y se comunica con el motor nativo Kotlin.
- **Guía Oficial:** [Build web apps in WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview) y [In-app content with WebViewAssetLoader](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content).

---

## 🏗️ Parte 3: Lecturas de Ingeniería de Software y Diseño de Sistemas

1. **Clean Architecture & DDD Ligero:**
   - *Libro recomendado:* **"Clean Architecture" de Robert C. Martin** (o resúmenes prácticos de *Guide to Android App Architecture* de Google). Te ayudará a verificar que los agentes no mezclen lógica de negocio (cálculo de multas/exenciones) dentro de la interfaz gráfica.
2. **Sistemas Distribuidos y Resiliencia (*Store & Forward*):**
   - *Libro recomendado:* **"Designing Data-Intensive Applications" (DDIA) de Martin Kleppmann** (Capítulos 1 y 5). Explica de forma magistral cómo manejar relojes desincronizados, idempotencia (evitar cobrar dos veces la misma multa si la red parpadea al sincronizar) y colas locales.
3. **Testing Automatizado en Android (Robolectric y Roborazzi):**
   - **Documentación:** [Roborazzi Screenshot Testing](https://github.com/takahirom/roborazzi) — Ya está integrado en tu proyecto ([`GreetingScreenshotTest.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/test/java/com/example/GreetingScreenshotTest.kt)). Permite tomar capturas de pantalla en milisegundos desde la JVM sin levantar un emulador pesado.

---

## 🛡️ Parte 4: Ciberseguridad Móvil y Anti-Sabotaje (Crítico para CronoBus)

En el transporte concesionado existe un incentivo económico real para que un operador intente engañar a la app (apps de *Fake GPS*, apagar datos antes de la base, modificar la hora del teléfono o manipular el almacenamiento local). Estos son los recursos clave:

### 1. Estándar Mundial de Seguridad Móvil: OWASP MASVS / MASTG
- **Lectura Principal (Gratis):** [OWASP Mobile Application Security (MAS)](https://mas.owasp.org/)
  - Revisa especialmente:
    - **MASVS-STORAGE:** Cómo proteger la base de datos `Room` / `SQLite` (por ejemplo con *SQLCipher* o firmas HMAC por registro) para que un usuario con acceso root no borre sus multas pendientes antes de que sincronicen.
    - **MASVS-PLATFORM:** Seguridad en `WebView`. Por qué en [`MainActivity.kt`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/java/com/example/MainActivity.kt) debemos desactivar `allowFileAccess = true` y nunca exponer `@JavascriptInterface` a páginas web externas (riesgo de ejecución remota de código / XSS-to-RCE).
    - **MASVS-RESILIENCE:** Defensa contra ingeniería inversa y manipulación del entorno.

### 2. Seguridad contra Falsificación de GPS (Anti-Spoofing & Anti-Tampering)
- **Guía Técnica Android:**
  - Uso de `Location.isMock()` (API 31+) y `Location.isFromMockProvider()` (API 18-30).
  - **Cruce de Relojes (Clock Tampering):** Un chofer puede atrasar el reloj de su teléfono 10 minutos para fingir que llegó a tiempo a la geocerca. **Lección de seguridad:** Nunca uses `System.currentTimeMillis()` para validar una geocerca; usa `SystemClock.elapsedRealtimeNanos()` (reloj monotónico desde el arranque) combinado con el timestamp satelital `location.time`.
  - **Play Integrity API (antes SafetyNet):** [Documentación oficial de Google Play Integrity](https://developer.android.com/google/play/integrity). Permite verificar que la app corre en un teléfono Android genuino no rooteado y que el binario APK no fue modificado.

### 3. Cursos Prácticos de Ciberseguridad Android
- **Curso Gratuito:** [Mobile App Security (Cybrary / OWASP Mobile Top 10)](https://owasp.org/www-project-mobile-top-10/) — Repaso de las 10 vulnerabilidades más explotadas en apps móviles (credenciales hardcodeadas en el APK, comunicación sin Certificate Pinning, exportación insegura de `Activities` y `Services` en `AndroidManifest.xml`).
- **Laboratorio de Auditoría de tu propio APK:**
  - Herramienta **MobSF (Mobile Security Framework)** ([GitHub MobSF](https://github.com/MobSF/Mobile-Security-Framework-MobSF)): Cuando termines los Sprints con los agentes, puedes subir tu `app-debug.apk` a una instancia local de MobSF (en Docker) para que te entregue un reporte automático de pentesting estático y dinámico.

### 4. Privacidad y Marco Legal en México (LGPDPPSO y LFT)
- **Principio de Minimización y Privacy by Design:**
  - Verifica siempre que ningún log de depuración (`Log.d`) ni tabla de analítica guarde datos personales innecesarios.
  - Asegúrate de conservar en la base de datos el hash y fecha/hora de aceptación del **Aviso de Privacidad y Consentimiento de Geolocalización Laboral** al iniciar cada turno, protegiendo al concesionario ante juntas de conciliación laboral.
