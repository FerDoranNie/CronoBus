# Prompt Maestro para AI Studio: CronoBus (Gestión Digital de Transporte)

Este archivo contiene la especificación completa y los prompts listos para ser utilizados en **Google AI Studio** (con modelos Gemini 1.5 Pro, 2.0 Flash / Pro o superiores) para generar el prototipo funcional y la arquitectura base del proyecto **CronoBus**.

---

## 🛠️ Guía de Configuración en Google AI Studio

1. **Modelo Recomendado:** `Gemini 1.5 Pro` o `Gemini 2.0 Flash / Pro`
2. **System Instructions:** Copia y pega el bloque **[1. System Instructions]** en la caja lateral de instrucciones del sistema.
3. **User Prompt:** Copia y pega el bloque **[2. User Prompt Inicial]** como el primer mensaje del chat.
4. **Parámetros Recomendados:**
   - **Temperature:** `0.2` - `0.3` (para garantizar código determinista, limpio y sin errores de sintaxis).
   - **Top P:** `0.95`
   - **Max Output Tokens:** `8192` (o el máximo disponible).

---

## 1. System Instructions (Instrucciones del Sistema)

```markdown
Eres un Arquitecto de Software Senior y Diseñador UI/UX especializado en Sistemas de Transporte Inteligente (ITS) y plataformas SaaS B2B de misión crítica. Tu tarea es diseñar e implementar la aplicación web/prototipo interactivo de "CronoBus" (Sistema Integral de Control de Rutas y Despacho Digital para Transporte Concesionado: combis y microbuses).

Debes seguir con máxima rigurosidad las directrices de diseño visual, la lógica operativa de transporte, la mitigación de conflictos sindicales y los requerimientos técnicos descritos a continuación.
```

---

## 2. User Prompt Inicial (Master Prompt)

```markdown
# SOLICITUD DE DESARROLLO: PLATAFORMA CRONOBUS (MVP INTERACTIVO)

Por favor, genera una aplicación web interactiva en un solo archivo (Single File HTML5 + TailwindCSS + JavaScript Vanilla + Leaflet.js) que implemente de forma completa y funcional el sistema **CronoBus**.

---

### 1. CONTEXTO OPERATIVO Y PROPÓSITO
El objetivo es sustituir el esquema tradicional de "checadores" humanos en esquinas por geolocalización automatizada y control de tiempos inteligente, resolviendo:
1. **"Carreritas" y canibalismo de pasaje:** Regulación estricta del intervalo (headway) entre unidades consecutivas.
2. **Fricción sindical y sabotaje:** En lugar de un sistema meramente punitivo, se incorpora un módulo de **Incentivos por Puntualidad**, un **Panel de Transparencia para el Chofer** y un mecanismo de **Disputa de Multas con Evidencia**.
3. **Resiliencia Operativa:** Simulación del modo "Store & Forward" (almacenamiento local ante pérdidas momentáneas de cobertura GPS/celular).
4. **Modelo Financiero Mixto:** Visualización del esquema de suscripción SaaS mensual por unidad activa ($150 MXN) complementado con multas justas ($50 MXN) y exenciones automáticas por causa de fuerza mayor.

---

### 2. SISTEMA DE DISEÑO: "CLASSIC SIGNAL" (GREEN SPECIFICATION)
Implementa de forma estricta la paleta de colores y el estilo visual de alta densidad y alto contraste:

#### Paleta de Tokens (Tailwind Config / CSS Variables):
- **Primary (Asphalt Black):** `#0a0a0a` | Contenedor: `#212121` | Texto: `#ffffff`
- **Secondary (Signal Green):** `#0b6c4b` | Contenedor: `#a0f4ca` | Texto contenedor: `#177251`
- **Accent / Alert (Amber):** `#ffba38` / `#b57e00` | Contenedor: `#2f1e00`
- **Error / Sanción:** `#ba1a1a` | Contenedor: `#ffdad6` | Texto: `#93000a`
- **Superficies & Fondos:**
  - Fondo general: `#f9f9f9`
  - Tarjetas / Contenedores base: `#ffffff`
  - Contenedor medio: `#eeeeee`
  - Bordes / Separadores: `#c4c7c7` (1px sólido)
  - Texto principal: `#1a1c1c`
  - Texto secundario / metadatos: `#444748`

#### Estilo Visual & Componentes:
- **Tipografía:** Fuente `Inter` de Google Fonts.
- **Bordes & Radios:** `4px` (0.25rem) para inputs y botones; `8px` para tarjetas; `12px` para pastillas de estado (pills).
- **Elevación:** Estilo plano ("Flat Utilitarian") con bordes de 1px sólido (`#c4c7c7`). Sin sombras difusas decorativas; solo sombras duras para elementos flotantes (`box-shadow: 4px 4px 0px rgba(10,10,10,0.1)`).

---

### 3. VISTAS Y MÓDULOS OBLIGATORIOS (INTERCAMBIABLES CON NAVBAR SUPERIOR)

Incluye un selector superior para navegar fluidamente entre las siguientes 3 vistas:

#### A. Interfaz del Conductor (Driver Mobile Interface)
- **Header de Turno:** Identificador de Ruta (ej. "Ruta 42 - Ramal Centro"), Unidad (ej. "Unidad #14"), Operador y Badge de señal GPS (Online / Sincronizado / Offline Store & Forward).
- **Cronómetro de Salida / Despacho:** Cuenta regresiva precisa para el siguiente turno de salida.
- **Monitor de Intervalo (Headway):** Semáforo visual que indica la distancia en minutos respecto a la unidad delantera y trasera (ej. "+2:30 min vs Unidad #09 - A tiempo").
- **Checador Digital & Geocercas:** Lista de puntos de control (Base A, Estación Norte, Hospital, Terminal B) con marcado automático cuando el vehículo cruza el radio de la geocerca.
- **Selector Rápido de Ocupación:** 3 botones táctiles (`Baja`, `Media`, `Lleno`) para actualizar el nivel de aforo reportado a los usuarios.
- **Botón de Reporte de Incidencias:** Acceso rápido para reportar: Tráfico Intenso, Accidente, Bloqueo de Calle, Falla Mecánica.
- **Módulo de Incentivos y Transparencia:** 
  - Bono acumulado del día por puntualidad ($).
  - Listado de multas del día con botón interactivo de **"Apelar / Disputar"** adjuntando justificación.

#### B. Panel de Administración y Despacho (Admin / Concesionario Dashboard)
- **Mapa en Tiempo Real (Leaflet.js):**
  - Trazo poligonal de la ruta con marcadores para todas las unidades activas y pines para las geocercas/estaciones.
  - Indicadores visuales sobre los iconos del mapa (Verde = a tiempo, Ámbar = retraso leve, Rojo = detenido/retraso crítico).
- **Control de Carreritas & Alertas en Vivo:** Registro en tiempo real de infracciones por exceso de velocidad, invasión de intervalos o paradas no autorizadas.
- **Gestión de Multas & Exenciones:** 
  - Registro de sanciones con opción de **Exención por Fuerza Mayor** (si múltiples unidades reportan el mismo bloqueo, el sistema permite condonar en lote).
  - Botón para exportar resumen (simulación de descarga CSV/PDF).
- **Métricas del Modelo de Negocio (SaaS + Sanciones):**
  - KPI de Unidades Activas, Cuota SaaS recaudada ($150/unidad), Multas cobradas y Bonos de puntualidad entregados.

#### C. Vista del Pasajero / Usuario General (Public ETA View)
- Selector de Ruta y Parada de Interés.
- Mapa interactivo con las unidades aproximándose.
- Tarjeta de Próxima Llegada con ETA (ej. "Llega en 4 min") y badge de aforo ("Asientos disponibles").

---

### 4. MOTOR DE SIMULACIÓN INTEGRADO (TESTING EN VIVO)
- Añade una barra de control flotante en la parte inferior con:
  - Botón `[▶ Iniciar Simulación]` / `[⏸ Pausar]`
  - Control de velocidad (`1x`, `2x`, `5x`)
  - Botón `[Simular Bloqueo / Tráfico]` para probar las alertas y las exenciones automáticas.
  - Botón `[Simular Corte de Señal]` para demostrar la resiliencia offline (Store & Forward con IndexedDB / LocalStorage).

---

### 5. REQUISITOS DE ENTREGA
Genera el código HTML completo, autocontenido, sin omisiones, listo para copiar y pegar directamente en el navegador o previsualizar en AI Studio.
```

---

## 3. Prompts de Refinamiento y Continuidad (Follow-up Prompts)

Si deseas iterar o profundizar en aspectos específicos durante la sesión en AI Studio, puedes usar estos prompts complementarios:

### Refinamiento A: Algoritmo de Geocercas & Filtro de Kalman
```markdown
Optimiza la función de geolocalización en JavaScript implementando un algoritmo simplificado de Geofencing Circular y un filtro de suavizado de coordenadas (inspirado en Kalman) para evitar saltos y falsos positivos causados por el ruido GPS en cañones urbanos.
```

### Refinamiento B: Sistema de Disputas y Exenciones Automáticas
```markdown
Expande el módulo de multas: si 3 conductores reportan una incidencia de "Bloqueo" o "Tráfico Pesado" en un radio de 500m dentro de una ventana de 15 minutos, haz que el panel administrativo active automáticamente una regla de 'Exención por Fuerza Mayor' que anule las multas de retraso en ese tramo y notifique a los operadores.
```

### Refinamiento C: Reporte Financiero Exportable
```markdown
Agrega un modal de exportación en el Panel de Administración que genere un resumen financiero consolidado (con base en el Modelo Financiero Mixto: CAPEX, OPEX por unidad, cobro SaaS y tasa de captura de multas) y permita descargar un archivo CSV formateado con las métricas del mes.
```
