<p align="center">
  <img src="imagenes_e_iconos/logo_cronobus.png" alt="CronoBus Logo" width="280">
</p>

<h1 align="center">CronoBus — Gestión Digital de Transporte Concesionado</h1>

<p align="center">
  <strong>Sistema Integral de Control de Intervalos, Despacho Automatizado y Geolocalización para Combis y Microbuses.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Web-0b6c4b?style=for-the-badge&logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Design%20System-Classic%20Signal-0a0a0a?style=for-the-badge" alt="Design System">
  <img src="https://img.shields.io/badge/Architecture-Store%20%26%20Forward%20Offline-amber?style=for-the-badge" alt="Resilience">
  <img src="https://img.shields.io/badge/Business%20Model-SaaS%20Mixto-177251?style=for-the-badge" alt="Business Model">
</p>

---

## 📌 Descripción General

**CronoBus** es una plataforma tecnológica diseñada para transformar la operación del transporte público concesionado tradicional (rutas de combis y microbuses). Reemplaza el esquema analógico e ineficiente del "checador" humano en esquinas físicas por un sistema digital automatizado basado en **geocercas GPS dinámicas**, **algoritmos de despacho equitativo** y un **modelo operativo centrado en la reducción de fricción sindical y mitigación de carreritas**.

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
- **Checador Digital Automatizado:** Marcado de paso por estaciones y bases sin interacción manual, mediante geocercas poligonales de alta precisión.
- **Monitor de Intervalo (Headway):** Indicador visual en tiempo real que previene las "carreritas" informando la separación exacta respecto a la unidad delantera y trasera.
- **Sistema de Transparencia & Incentivos:** Muestra los bonos acumulados por puntualidad y regularidad de paso, transformando la app en una herramienta de beneficio para el chofer y no solo de control punitivo.
- **Reporte Rápido de Incidencias & Apelación:** Botones táctiles de un solo toque para reportar cierres viales, tráfico o accidentes, junto con un mecanismo de apelación de multas con evidencia fotográfica.
- **Selector de Ocupación:** Registro simple del aforo disponible (`Baja`, `Media`, `Lleno`).

### 2. 🖥️ Panel de Administración y Despacho (Admin / Concesionario)
- **Monitoreo en Tiempo Real:** Mapa interactivo con telemetría de toda la flota, estados de retraso/adelanto y semáforos por unidad.
- **Gestión Automatizada de Sanciones & Exenciones:** Registro de infracciones con reglas de **Exención por Fuerza Mayor** (si múltiples unidades reportan el mismo bloqueo, las multas se condonan automáticamente).
- **Despacho y Rotación:** Algoritmo de turnos de salida que optimiza el tiempo de descanso y equilibra las oportunidades de pasaje entre unidades.
- **Métricas del Modelo Mixto:** Visualización del flujo de ingresos por suscripción SaaS mensual vs. recaudación de multas y reparto de bonos.

### 3. 👥 Vista del Pasajero (Public ETA View)
- Búsqueda por ruta y ramal con visualización en tiempo real de unidades aproximándose.
- Estimación precisa del tiempo de llegada (ETA) y visualización del nivel de ocupación reportado.

---

## 🎨 Sistema de Diseño: "Classic Signal" (Green Spec)

El sistema visual implementa la especificación **Classic Signal**, inspirada en la señalética de transporte internacional y dashboards industriales de alta densidad.

| Token | Hex | Aplicación |
|---|---|---|
| **Primary (Asphalt Black)** | `#0a0a0a` | Estructuras, tipografía principal y navegación |
| **Secondary (Signal Green)** | `#0b6c4b` | Estados activos, a tiempo, geocercas y acciones clave |
| **Secondary Container** | `#a0f4ca` | Fondos de confirmación y badges de estado positivo |
| **Accent (Amber)** | `#ffba38` / `#b57e00` | Advertencias de intervalo, mantenimiento y alertas |
| **Error / Penalty** | `#ba1a1a` | Retrasos críticos, sanciones e incidencias graves |
| **Canvas / Background** | `#f9f9f9` / `#ffffff` | Fondo general y tarjetas con borde sólido de 1px (`#c4c7c7`) |

- **Tipografía:** `Inter` (Google Fonts) con jerarquía rígida y números tabulares.
- **Formas:** Bordes utilitarios suaves de `4px` para inputs y botones, `8px` para tarjetas y `12px` para pastillas de estado. Sin sombras difusas decorativas.

---

## 📊 Modelo Financiero y Operativo

La plataforma está respaldada por un análisis financiero y de mercado documentado en el repositorio:

- **Ingreso SaaS Base:** Cuota mensual fija por unidad activa ($120 - $190 MXN/mes), asegurando estabilidad financiera independiente de la conducta del chofer.
- **Ingreso Variable por Multas:** Cobro regulado ($40 - $65 MXN prom.) con esquemas de captura transparente y fondos de contingencia.
- **Incentivos a la Adopción:** Reparto de bonos de puntualidad para vencer la resistencia sindical y eliminar el incentivo al sabotaje de hardware.

---

## 📁 Estructura del Repositorio

```
app_rutas/
├── README.md                                     # Este documento
├── metadata.json                                 # Metadatos del proyecto y configuración
├── PROMPT_AI_STUDIO_CRONOBUS.md                  # Prompts maestros optimizados para Google AI Studio
├── Proposito_APP_Rutas.md                        # Documento fundacional: Objetivos operativos y legales
├── Informe_Modelo_Financiero_Mixto.md            # Análisis financiero (SaaS + Multas, proyecciones 36 meses)
├── Investigación App Transporte Público...pdf   # Estudio de mercado en ciudades mexicanas
│
├── imagenes_e_iconos/                            # Identidad gráfica y logotipos
│   └── logo_cronobus.png                         # Logotipo oficial de CronoBus
│
├── Designs/                                      # Especificaciones completas de diseño
│   ├── Design_green.md                           # Classic Signal (Paleta Verde / Asfalto - Principal)
│   ├── Design_blue.md                            # Tránsito Pro (Paleta Azul)
│   └── Design_dark.md                            # CronoBus Dark Mode
│
├── stitch_gesti_n_digital_de_transporte/         # Prototipos y pantallas generadas
│   ├── interfaz_del_conductor_classic_signal/    # UI Conductor (código y capturas)
│   ├── panel_de_administraci_n_transito_pro/     # UI Administrador (código y capturas)
│   └── classic_signal/                           # Tokens y guías de estilos
│
└── app/                                          # Aplicación Android Híbrida (Jetpack Compose + WebView)
    ├── build.gradle.kts                          # Configuración de Gradle y dependencias
    └── src/main/
        ├── AndroidManifest.xml                   # Manifiesto de Android con permisos de red
        ├── assets/
        │   └── cronobus.html                     # Frontend interactivo completo autocontenido
        └── java/com/example/
            └── MainActivity.kt                   # WebView optimizado con aceleración y JavaScript
```

---

## 🛠️ Cómo Ejecutar y Probar

### Opción 1: Ejecución en Navegador Web (Prototipo Rápido)
Abre directamente el archivo [`app/src/main/assets/cronobus.html`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/app/src/main/assets/cronobus.html) en cualquier navegador moderno (Chrome, Edge, Firefox) para interactuar con el simulador de rutas, el panel del chofer y el panel de administración.

### Opción 2: Compilación y Ejecución en Android
El repositorio incluye el proyecto Gradle de Android configurado:
```bash
# Compilar el APK de depuración
./gradlew assembleDebug

# O abrir el proyecto en Android Studio y presionar Run en un emulador o dispositivo físico
```

### Opción 3: Iteración con IA en Google AI Studio
Consulta el archivo [`PROMPT_AI_STUDIO_CRONOBUS.md`](file:///c:/Users/fernando.dorantes/local/scripts/proyectos/app_rutas/PROMPT_AI_STUDIO_CRONOBUS.md) para copiar las *System Instructions* y el *Master Prompt* en **Google AI Studio** para generar nuevas pantallas, refinar algoritmos de geofencing o extender funcionalidades.

---

## 🛡️ Resiliencia Técnica y Marco Legal

1. **Modo Store & Forward (Resiliencia Offline):** Los eventos de cruce de geocercas se persisten localmente en `IndexedDB` / almacenamiento local durante zonas de sombra celular y se transmiten automáticamente al restablecer la conexión.
2. **Cumplimiento LGPDPPSO y LFT:**
   - **Disociación de datos:** Tratamiento anonimizado de métricas de aforo de pasajeros.
   - **Consentimiento Digital:** Registro de firma digital y aviso de privacidad para el uso de geolocalización laboral.
