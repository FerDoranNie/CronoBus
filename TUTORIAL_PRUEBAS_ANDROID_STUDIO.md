# 📱 Guía y Tutorial de Pruebas en Android Studio — CronoBus

Este tutorial detalla paso a paso cómo compilar, ejecutar, depurar y validar la aplicación **CronoBus** en **Android Studio**, tanto en emuladores virtuales (AVD) como en dispositivos físicos, incluyendo técnicas de depuración de WebView y simulación de rutas GPS.

---

## 📋 1. Requisitos Previos

Antes de comenzar, asegúrate de contar con lo siguiente en tu equipo:

1. **Android Studio:** Versión reciente (Flamingo, Hedgehog, Iguana, Jellyfish, Koala o superior).
2. **Java Development Kit (JDK):** JDK 17 o JDK 21 (incluido por defecto en Android Studio bajo `jbr`).
3. **Android SDK:**
   - **Android SDK Platform:** API 34 o API 35 (Android 14 / 15).
   - **Android SDK Build-Tools:** 34.0.0 o superior.
   - **Android Emulator & Platform-Tools** (incluye `adb`).

---

## 🚀 2. Apertura y Sincronización del Proyecto

1. Inicia **Android Studio**.
2. En la pantalla de bienvenida, selecciona **Open** (o ve a `File > Open...`).
3. Navega hasta la carpeta del proyecto:
   ```
   c:\Users\fernando.dorantes\local\scripts\proyectos\app_rutas
   ```
4. Haz clic en **OK**.
5. Espera a que termine la sincronización de Gradle (**Gradle Sync**).
   - Si es la primera vez, descargará las dependencias de Compose y AndroidX.
   - Puedes verificar el progreso en la pestaña inferior **Build / Sync**.

> [!TIP]
> Si Gradle muestra una advertencia sobre el SDK, verifica que el archivo `local.properties` contenga la ruta correcta a tu Android SDK, por ejemplo:
> `sdk.dir=C\:\\Users\\<tu_usuario>\\AppData\\Local\\Android\\Sdk`

---

## 📲 3. Configuración del Emulador (AVD - Android Virtual Device)

Para probar la interfaz móvil del chofer y el mapa interactivo:

1. Abre el **Device Manager** en Android Studio (`Tools > Device Manager` o el icono de teléfono en la barra superior derecha).
2. Haz clic en **Create Device (+)**.
3. **Selecciona un dispositivo:** Por ejemplo, **Pixel 7** o **Medium Phone** (buena relación de aspecto para interfaces de conductor).
4. **Selecciona la versión del sistema (System Image):**
   - Elige **UpsideDownCake (API 34)** o **VanillaIceCream (API 35)** con **Google APIs (x86_64)**.
5. Haz clic en **Next** y luego en **Finish**.

---

## ▶️ 4. Compilación y Ejecución de la App

1. En la barra de herramientas superior, asegúrate de tener seleccionada la configuración de ejecución **`app`**.
2. En el selector de dispositivos, elige tu emulador creado (o tu teléfono conectado vía USB).
3. Haz clic en el botón verde **Run (▶)** o presiona `Shift + F10` (Windows).
4. Android Studio compilará el APK e instalará la aplicación en el dispositivo. La app abrirá la interfaz híbrida cargando [`cronobus.html`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/cronobus.html).

---

## 🔍 5. Depuración en Tiempo Real (Chrome DevTools)

Dado que **CronoBus** opera con una arquitectura híbrida de alto rendimiento (Jetpack Compose + WebView de bajo retardo), puedes inspeccionar el DOM, la consola de JavaScript y el almacenamiento local directamente con **Google Chrome en tu PC**:

1. Con la app ejecutándose en el emulador o teléfono, abre **Google Chrome** en tu computadora.
2. En la barra de direcciones de Chrome, escribe:
   ```
   chrome://inspect/#devices
   ```
3. Verás listado tu dispositivo y el WebView de `com.example (CronoBus)`.
4. Haz clic en el enlace **`inspect`**.
5. Se abrirá una ventana completa de **Chrome DevTools** donde podrás:
   - Ver los mensajes y errores de JavaScript en la pestaña **Console**.
   - Modificar estilos CSS en tiempo real en la pestaña **Elements**.
   - Inspeccionar el `localStorage` / `IndexedDB` en la pestaña **Application** (vital para verificar el modo *Store & Forward* offline).
   - Monitorear peticiones de red o simulación en la pestaña **Network**.

---

## 🛰️ 6. Pruebas de Geolocalización y Geocercas en el Emulador

Para probar la detección automática de estaciones y el marcado del "checador digital":

### Método A: Simulación de Ubicación Manual
1. En el panel lateral del Emulador de Android, haz clic en el botón de los 3 puntos **`...`** (Extended Controls).
2. Ve a la sección **Location**.
3. En el mapa, busca una coordenada o escribe latitud y longitud.
4. Haz clic en **Set Location**.
5. Observa cómo el mapa de la app actualiza la posición del vehículo.

### Método B: Simulación de Ruta en Movimiento (Playback de GPX/KML)
1. En la misma ventana de **Extended Controls > Location**, ve a la pestaña **Routes**.
2. Puedes trazar puntos sobre la calle o cargar un archivo `.gpx` de una ruta real.
3. Ajusta la velocidad de desplazamiento (ej. 30 km/h) y presiona **Play Route**.
4. Valida que al cruzar las coordenadas de las paradas configuradas en CronoBus, la app dispare el sonido/alerta de **"Geocerca Cruzada"** y registre el tiempo de paso.

---

## 📶 7. Pruebas de Resiliencia Offline (Store & Forward)

Para comprobar que no se pierden datos cuando el chofer transita por zonas sin señal:

1. En el emulador, abre **Extended Controls (`...`) > Cellular**.
2. Cambia el **Network type** a `No service` (o activa el Modo Avión en la barra de notificaciones del Android).
3. Continúa navegando y marcando eventos o incidencias en la app (reportar bache, cambiar ocupación).
4. Verifica en la consola de Chrome DevTools que los eventos se encolan en la base de datos local.
5. Restablece la red a `LTE / 5G`.
6. Confirma que la app transmita automáticamente el lote de eventos pendientes y actualice el badge de estado a **"Sincronizado"**.

---

## 🧪 8. Ejecución de Pruebas y Tareas desde la Terminal

Puedes ejecutar tareas de validación y compilación sin abrir la interfaz de Android Studio usando **Gradle Wrapper**:

```powershell
# Verificar sintaxis y dependencias
./gradlew build --dry-run

# Ejecutar pruebas unitarias locales
./gradlew test

# Compilar el APK de desarrollo (Debug)
./gradlew assembleDebug

# Instalar el APK directamente en el dispositivo conectado vía ADB
./gradlew installDebug
```

El APK resultante se genera en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🛠️ 9. Solución a Problemas Comunes (Troubleshooting)

| Problema | Causa Probable | Solución |
|---|---|---|
| **Pantalla en blanco al abrir la app** | Ruta de assets incorrecta o JavaScript deshabilitado. | Verifica que `cronobus.html` esté en `app/src/main/assets/` y que `MainActivity.kt` tenga `settings.javaScriptEnabled = true`. |
| **Error `SDK location not found`** | Falta el archivo `local.properties`. | Abre Android Studio y deja que configure el SDK automáticamente o crea el archivo `local.properties` con `sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk`. |
| **Los mapas Leaflet no cargan azulejos** | Falta permiso de Internet en el manifiesto. | Comprueba que `AndroidManifest.xml` incluya `<uses-permission android:name="android.permission.INTERNET" />`. |
| **Gradle Sync Failed por versión de JDK** | Android Studio está usando un JDK inferior a 17. | Ve a `Settings > Build, Execution, Deployment > Build Tools > Gradle` y en **Gradle JDK** selecciona **Embedded JDK (JDK 17/21)**. |

---

## 📚 10. Documentación Relacionada
- [PROMPT_AI_STUDIO_CRONOBUS.md](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/PROMPT_AI_STUDIO_CRONOBUS.md): Prompt para iterar funcionalidades.
- [Proposito_APP_Rutas.md](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Proposito_APP_Rutas.md): Reglas de negocio de geocercas y tiempos.
- [Designs/Design_green.md](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Designs/Design_green.md): Especificación del Design System Classic Signal.
