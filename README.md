<p align="center">
  <img src="imagenes_e_iconos/logo_cronobus.png" alt="CronoBus Logo" width="280">
</p>

<h1 align="center">CronoBus — Gestión Digital de Transporte Concesionado</h1>

<p align="center">
  <strong>Sistema Integral de Control de Intervalos (Headway), Despacho Automatizado y Geolocalización para Combis y Microbuses.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20(Compose%20%2B%20WebView)-0b6c4b?style=for-the-badge&logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Status-MVP%20Prototipo%20H%C3%ADbrido-ffba38?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/Design%20System-Classic%20Signal-0a0a0a?style=for-the-badge" alt="Design System">
  <img src="https://img.shields.io/badge/Architecture-Store%20%26%20Forward%20Offline-amber?style=for-the-badge" alt="Resilience">
  <img src="https://img.shields.io/badge/Business%20Model-SaaS%20Mixto-177251?style=for-the-badge" alt="Business Model">
</p>

---

## 📌 Descripción General

**CronoBus** es una plataforma tecnológica diseñada para profesionalizar la operación del transporte público concesionado tradicional (rutas de combis y microbuses en México y LATAM). Reemplaza el esquema analógico del "checador" humano en esquinas físicas por un sistema digital automatizado basado en **geocercas GPS dinámicas**, **algoritmos de despacho equitativo** y un **modelo operativo centrado en la reducción de fricción sindical y mitigación de "carreritas"**.

### Estado Actual del Proyecto (v0.1 - Prototipo Híbrido)
Actualmente el proyecto cuenta con:
- **Contenedor Android (`app/`):** Proyecto configurado con **AGP 9.1.1 / Kotlin 2.2 / SDK 36**, Jetpack Compose y catálogo de dependencias listo para producción (`Room`, `Retrofit`, `Moshi`, `Play Services Location`, `CameraX`, `Firebase`, `Roborazzi`).
- **Frontend Interactivo (`cronobus.html`):** Aplicación SPA autocontenida renderizada en `WebView` que simula en tiempo real las 3 vistas (Conductor, Despacho/Admin y Pasajero) con mapas `Leaflet.js`.
- **Próximo Paso (Transición a MVP Productivo):** Migración de los mocks en memoria hacia un puente nativo Android (`JavascriptInterface`) o UI nativa en Jetpack Compose respaldada por `FusedLocationProviderClient` (GPS real en segundo plano), filtro de Kalman, persistencia offline real en `Room` (*Store & Forward*) y sincronización en la nube.

---

## 🚀 Características y Módulos Principales

```
                                  ┌────────────────────────────────┐
                                  │           CRONOBUS             │
                                  │      Plataforma Central        │
                                  └───────────────┬────────────────┘
                                                  │
                ┌─────────────────────────────────┼─────────────────────────────────┐
                ▼                                 ▼                                 ▼
   ┌──────────────────────────┐     ┌──────────────────────────┐      ┌──────────────────────────┐
   │    Panel del Conductor   │     │    Panel Administrativo  │      │    Vista del Pasajero    │
   │      (Mobile First)      │     │      (Control & BI)      │      │      (Público / ETA)     │
   ├──────────────────────────┤     ├──────────────────────────┤      ├──────────────────────────┤
   │ • Geochecador automático │     │ • Mapa flota en vivo     │      │ • Localización de combis │
   │ • Semáforo de intervalos │     │ • Detección carreritas   │      │ • ETA en tiempo real     │
   │ • Reporte de incidencias │     │ • Exenciones fuerza mayor│      │ • Nivel de ocupación     │
   │ • Bonos de puntualidad   │     │ • Métricas SaaS y multas │      │ • Trazado de ramales     │
   │ • Apelación de multas    │     │ • Despacho inteligente   │      │ • Alertas de servicio    │
   └──────────────────────────┘     └──────────────────────────┘      └──────────────────────────┘
```

### 1. 📱 Interfaz del Conductor (Driver Mobile Interface)
- **Checador Digital Automatizado:** Marcado de paso por estaciones y bases sin interacción manual mediante geocercas poligonales/circulares.
- **Monitor de Intervalo (Headway):** Indicador visual en tiempo real que previene las "carreritas" informando la separación en minutos respecto a la unidad delantera y trasera.
- **Sistema de Transparencia & Incentivos:** Muestra los bonos acumulados por puntualidad y regularidad de paso, convirtiendo la app en una herramienta de beneficio para el operador.
- **Reporte Rápido de Incidencias & Apelación:** Botones táctiles de un solo toque para reportar cierres viales, tráfico o accidentes, más mecanismo de disputa de multas con evidencia fotográfica y GPS.
- **Selector de Ocupación:** Registro rápido del aforo disponible (`Baja`, `Media`, `Lleno`).

### 2. 🖥️ Panel de Administración y Despacho (Admin / Concesionario)
- **Monitoreo en Tiempo Real:** Mapa interactivo con telemetría de toda la flota, estados de retraso/adelanto y semáforos por unidad.
- **Gestión Automatizada de Sanciones & Exenciones:** Registro de infracciones con reglas de **Exención por Fuerza Mayor** (si $\ge 3$ unidades reportan el mismo bloqueo en un radio de 500m y ventana de 15 min, las multas se condonan en lote).
- **Despacho y Rotación:** Algoritmo de turnos de salida que optimiza el tiempo de descanso y equilibra las oportunidades de pasaje.
- **Métricas del Modelo Mixto:** Visualización del flujo de ingresos por suscripción SaaS mensual vs. recaudación de multas y reparto de bonos (exportable a CSV/PDF).

### 3. 👥 Vista del Pasajero (Public ETA View)
- Búsqueda por ruta, ramal y parada con visualización en tiempo real de unidades aproximándose.
- Estimación del tiempo de llegada (ETA) con caché offline para zonas de sombra celular y badge de nivel de ocupación.

---

## 🤖 Flujo de Desarrollo Multi-Agente

El desarrollo hacia producción está estructurado para ejecutarse mediante una célula coordinada de agentes de IA (consulta [`AGENTS.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/AGENTS.md) para reglas de gobernanza):

| Rol | Modelo Asignado | Responsabilidad Principal | Cuota de Código |
|---|---|---|---|
| **Orquestador & QA Final** | **Gemini 4 Argon** | Arquitectura, asignación de tareas, integración de ramas y validación E2E / regresión final | Coordinación + QA |
| **Escritor Principal (Core & Nativo)** | **DeepSeek** | Implementación pesada: servicios GPS nativos, filtro Kalman, Room DB, repositorios, lógica de negocio y UI compleja | **75%** |
| **Escritor Secundario (UI & Boilerplate)** | **Gemini 3.8 Flash** | Componentes de interfaz rápidos, tokens de tema, DTOs, recursos XML, assets offline y pruebas unitarias base | **25%** |
| **Primer Debugger & Auditor** | **Claude Opus** | Revisión estática profunda, depuración de concurrencia/coroutines, fugas de memoria, permisos Android y fixes de compilación | Debugging & Fixes |

---

## 🎨 Sistema de Diseño: "Classic Signal" (Green Spec)

El sistema visual implementa la especificación **Classic Signal** ([`Designs/Design_green.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/Designs/Design_green.md)), inspirada en la señalética de transporte internacional y dashboards industriales de alta densidad.

| Token | Hex | Aplicación |
|---|---|---|
| **Primary (Asphalt Black)** | `#0a0a0a` | Estructuras, tipografía principal y navegación |
| **Secondary (Signal Green)** | `#0b6c4b` | Estados activos, a tiempo, geocercas y acciones clave |
| **Secondary Container** | `#a0f4ca` | Fondos de confirmación y badges de estado positivo |
| **Accent (Amber)** | `#ffba38` / `#b57e00` | Advertencias de intervalo, mantenimiento y alertas |
| **Error / Penalty** | `#ba1a1a` | Retrasos críticos, sanciones e incidencias graves |
| **Canvas / Background** | `#f9f9f9` / `#ffffff` | Fondo general y tarjetas con borde sólido de 1px (`#c4c7c7`) |

---

## 📁 Estructura del Repositorio y Documentación

```
app_rutas/
├── README.md                                     # Descripción general y guía rápida
├── PLAN_AGENTES.md                               # Plan de 4 semanas (16 pasos HITL con prompts listos)
├── to_do.md                                      # Backlog maestro y checklist de desarrollo
├── ARCHITECTURE.md                               # Arquitectura técnica (Híbrida -> Nativa Android)
├── AGENTS.md                                     # Reglas de coordinación secuencial (Human-in-the-Loop)
├── lo_que_le_toca_al_developer.md                # Guía del developer: cursos Android, arquitectura y ciberseguridad
├── Proposito_APP_Rutas.md                        # Documento fundacional: Objetivos operativos y legales
├── Informe_Modelo_Financiero_Mixto.md            # Análisis financiero (SaaS + Multas, proyecciones 36m)
├── PROMPT_AI_STUDIO_CRONOBUS.md                  # Prompts maestros optimizados para Google AI Studio
├── TUTORIAL_PRUEBAS_ANDROID_STUDIO.md            # Guía de compilación, emulador GPS y Chrome DevTools
│
├── Designs/                                      # Especificaciones completas de diseño
│   ├── Design_green.md                           # Classic Signal (Paleta Verde / Asfalto - Principal)
│   ├── Design_blue.md                            # Tránsito Pro (Paleta Azul - Alternativa Admin)
│   └── Design_dark.md                            # CronoBus Dark Mode (Operación nocturna)
│
├── stitch_gesti_n_digital_de_transporte/         # Prototipos de referencia UI generados en Stitch
│
└── app/                                          # Aplicación Android (Jetpack Compose + WebView)
    ├── build.gradle.kts                          # Configuración de Gradle (SDK 36, Room, Retrofit, Compose)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml               # Manifiesto de Android y permisos
        │   ├── assets/cronobus.html              # SPA interactiva de las 3 vistas
        │   └── java/com/example/MainActivity.kt  # Punto de entrada y contenedor WebView
        └── test/                                 # Tests unitarios con Robolectric y Roborazzi
```

---

## 🛠️ Cómo Ejecutar y Probar

### Opción 1: Ejecución en Navegador Web (Prototipo Rápido)
Abre [`app/src/main/assets/cronobus.html`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/cronobus.html) en cualquier navegador moderno para interactuar con el simulador de rutas, el panel del chofer y el panel de administración.

### Opción 2: Compilación y Ejecución en Android Studio
Consulta la guía detallada en **[TUTORIAL_PRUEBAS_ANDROID_STUDIO.md](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/TUTORIAL_PRUEBAS_ANDROID_STUDIO.md)**.

```powershell
# Compilar el APK de depuración
./gradlew assembleDebug

# Ejecutar pruebas unitarias y de regresión visual (Robolectric / Roborazzi)
./gradlew testDebugUnitTest

# Instalar en el emulador o dispositivo conectado
./gradlew installDebug
```

---

## 🛡️ Resiliencia Técnica y Marco Legal

1. **Modo Store & Forward (Resiliencia Offline):** Los eventos de cruce de geocercas e incidencias se encolan localmente en base de datos (`Room` / `IndexedDB`) durante zonas sin cobertura celular y se sincronizan con sello criptográfico de tiempo al recuperar red.
2. **Detección Anti-Sabotaje:** Monitoreo de apagados sistemáticos de GPS previo a geocercas, detección de ubicaciones simuladas (*Mock Locations*) y telemetría de batería/red.
3. **Cumplimiento LGPDPPSO y LFT:**
   - **Disociación de datos (Art. 3, Frac. XIII):** Métricas de aforo de pasajeros completamente anónimas.
   - **Consentimiento Digital (Art. 19-21):** Firma digital del operador en inicio de turno y aviso de privacidad laboral con bitácora de auditoría.
