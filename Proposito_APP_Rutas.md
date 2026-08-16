# Propósito APP Rutas

## 1. Objetivo Principal

Desarrollar una aplicación móvil para el control integral de tiempos y rutas de transporte público (combis/microbuses), reemplazando el registro manual por un sistema digital automatizado.

## 2. Funcionalidades Operativas

- **Gestión de Tiempos:** Reemplazo del "checador" humano por geolocalización.
- **Monitoreo en Tiempo Real:** Visualización en mapa de la ubicación de todas las unidades.
- **Rotación de Salidas:** Algoritmo de turnos y notificaciones al operador sobre su próxima salida para reducir tiempos de inactividad.
- **Control de Intervalos:** Indicador de distancia/tiempo entre unidades para evitar la "pesca" excesiva de pasaje.
- **Sistema de Multas:** Gestión automatizada de cobros por retrasos con acumulación diaria y reporte detallado.
- **Planificación de Rutas:** Configuración de N estaciones mediante pines digitales en mapa.
- **Reporte de Incidencias:** Sistema de notificación inmediata sobre bloqueos, tráfico o accidentes.

## 3. Público en General

- **Disponibilidad:** Aplicación descargable para usuarios finales.
- **Información de Servicio:** Filtro de rutas controladas y visualización del tiempo estimado de llegada (ETA) para optimizar tiempos de espera.

## 4. Funcionalidades de Mejora

- **Sistema de Incentivos:** Tablero de desempeño para premiar puntualidad y cumplimiento de intervalos.
- **Botón de Alerta Rápida:** Interfaz intuitiva con iconos (Ej. Accidente, Tráfico, Bloqueo) para notificación inmediata.
- **Módulo de Mantenimiento Preventivo:** Registro simplificado de componentes clave (frenos, luces, nivel de aceite) al inicio del turno.
- **Visibilidad de Ocupación:** Indicador de nivel de carga (ej. "Espacio disponible", "Lleno").
  - **Opción A (Manual):** El operador selecciona en su panel un nivel de ocupación predefinido.
  - **Opción B (Sensores Infrarrojos):** Conteo automático mediante flujo en puertas (sin captura de datos biométricos).

## 5. Especificaciones Técnicas

- **Geofencing Dinámico:** Uso de polígonos alrededor de estaciones para marcaje automático.
- **Modo Resiliencia (Store & Forward):** Base de datos local para almacenar datos durante cortes de señal, con sincronización automática.
- **Filtrado de Ruido GPS:** Algoritmo (filtro de Kalman) para suavizar la posición y evitar errores de lectura urbana.
- **Integración con APIs de Tráfico:** Ajuste dinámico de tiempos esperados ante condiciones de tráfico real.

## 6. Referencias y Cumplimiento Legal (LGPDPPSO)

- **Disociación (Art. 3, Frac. XIII):** Datos de ocupación agregados y anónimos.
- **Proporcionalidad (Art. 19):** Tratamiento limitado estrictamente a la optimización del servicio.
- **Transparencia (Art. 20 y 21):** Aviso de privacidad integrado informando sobre el uso estadístico.

## 7. Estrategia de Implementación: Propuesta A (MVP)

- **Alcance:** Control de tiempo (GPS) y App del operador.
- **Ocupación:** Se implementa inicialmente la Opción A (Manual) para optimizar costos de hardware.
- **Multas:** Gestión mediante reporte semanal (Excel/PDF) como transición a la automatización total.

**Ventaja:** Despliegue rápido (2-3 meses) para validar adopción operativa antes de escalar a sensores físicos.

---

## 8. Ideas Adicionales

Estas ideas complementan el documento original con base en los riesgos identificados en el estudio de mercado (resistencia sindical, sabotaje de hardware, dependencia de multas) y en el modelo financiero desarrollado.

### 8.1 Anti-sabotaje y confiabilidad de datos

- **Detección de manipulación de GPS:** alertas automáticas cuando un dispositivo se apaga de forma sistemática antes de cruzar una geocerca, o cuando la señal cae con un patrón repetitivo (posible desconexión deliberada).
- **Dispositivo dedicado no removible** instalado en el tablero, en lugar de depender del teléfono personal del chofer — reduce tanto el riesgo de sabotaje como el riesgo legal de exigir apps de vigilancia en dispositivos personales (ya señalado en el estudio bajo la LFT).
- **Modo de disputa con evidencia:** que el chofer pueda subir una foto o reporte con un solo toque cuando cuestiona una multa, en vez de que el sistema actúe como juez sin apelación.

### 8.2 Adopción y reducción de fricción sindical

- **Panel de transparencia para el chofer**, no solo para el dueño de la ruta: que el operador vea sus propias métricas, comparación de turnos e ingresos potenciales por incentivos — convierte la app de una herramienta puramente punitiva en una de valor percibido.
- **Mover el Sistema de Incentivos del MVP+ al MVP mismo:** es la mejor herramienta para reducir resistencia inicial, más que cualquier ajuste técnico.
- **Piloto con "ruta amiga":** elegir una sola ruta cuyo presidente/concesionario ya esté frustrado con las carreritas o haya enfrentado un accidente/revocación reciente, para generar un caso de éxito documentado antes de vender a otras rutas.

### 8.3 Modelo de negocio e ingresos

- **No depender 100% de las multas** para el flujo de caja (ver modelo financiero mixto): una cuota de suscripción SaaS por unidad activa da ingresos predecibles independientes de si el chofer paga o evade sanciones.
- **Vender el ROI al dueño de la ruta**, no solo la funcionalidad: menos accidentes por "carreritas" implica menos responsabilidad legal y menos fuga de efectivo por manipulación del checador humano.

### 8.4 Mejoras técnicas complementarias

- **Modo offline más robusto para el pasajero:** cachear el ETA y la última ubicación conocida de la unidad cuando el usuario pierde conexión momentánea (común en zonas de "cañón urbano").
- **Exenciones automáticas por fuerza mayor**, ya contempladas vía integración con APIs de tráfico externo — considerar extenderlo a reportes de incidencias de la propia flota (si tres unidades reportan el mismo bloqueo, aplicar exención a toda la ruta automáticamente).
- **Firma digital del consentimiento** del operador dentro de la propia app, con logs de auditoría, para blindar legalmente el uso de geolocalización ante disputas laborales (relevante bajo LGPDPPSO y LFT).

### 8.5 Estrategia de mercado

- **Evitar competir de frente en ciudades donde ya exista tracción de un competidor B2B** (ej. ChecaBus u otros) sin antes conocer su pricing y cobertura real — validar esto antes de elegir la ciudad piloto.
- **Priorizar mercados en transición** (Puebla, Toluca, Ciudad Juárez, Torreón) sobre mercados hiper-conflictivos (CDMX) o monopolizados por el Estado (Mérida, Querétaro), como ya identificó el estudio de mercado.
