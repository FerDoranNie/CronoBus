# Modelo Financiero Mixto

*App de Gestión de Transporte Público — Ingresos por Suscripción SaaS + Multas*

## 1. Resumen Ejecutivo

Este informe documenta el modelo financiero construido para evaluar la viabilidad de la aplicación de gestión de transporte público descrita en el estudio de mercado previo. A diferencia del planteamiento original — donde "la app se paga sola" mediante el cobro de multas —, este modelo introduce un segundo flujo de ingreso: una cuota de suscripción SaaS mensual cobrada al concesionario o presidente de ruta por unidad activa. El objetivo es reducir la dependencia total del ingreso de un mecanismo (las multas) que, según el propio estudio de mercado, está expuesto a sabotaje de hardware, evasión y resistencia sindical.

El modelo proyecta 36 meses a partir de un piloto de una sola ruta, con tres escenarios (Conservador, Base y Optimista) que varían de forma simultánea el ritmo de expansión de rutas, el precio de suscripción, la tasa de activación y — de forma crítica — la tasa de captura efectiva de multas.

**Hallazgo principal: incluso en el escenario Base, el 80.6% del ingreso total sigue proveniendo de multas, y solo 19.4% de la suscripción fija. La mezcla de ingresos no logra equilibrarse con los supuestos actuales — la app sigue siendo financieramente vulnerable al mismo riesgo operativo (sabotaje, evasión) que motivó diseñar un modelo mixto en primer lugar.**

## 2. Estructura del Modelo

### 2.1 Fuentes de Ingreso

- **Suscripción SaaS:** cuota mensual fija por unidad activa, cobrada al concesionario/ruta. Ingreso predecible, no depende de la conducta del chofer.
- **Multas:** cobro automatizado por retrasos/infracciones de intervalo, calculado como unidades activas × infracciones/día × días operativos × multa promedio × tasa de captura efectiva. Ingreso variable y expuesto a evasión.

### 2.2 Motor de Crecimiento

El número de rutas activas parte de un piloto fijo (1 ruta, meses 1–3) y después crece mensualmente según una tasa de adición neta de churn, hasta un tope máximo definido por escenario. Las unidades activas se derivan de rutas × unidades promedio por ruta × tasa de activación (el porcentaje de unidades que efectivamente adopta la app dentro de una ruta contratada).

### 2.3 Estructura de Costos (OPEX)

- Hosting e infraestructura en la nube, proporcional a unidades activas.
- APIs de mapas/geolocalización, proporcional a unidades activas.
- Mantenimiento recurrente, calculado como % anual del CAPEX inicial.
- SMS/OTP para autenticación, costo fijo mensual.
- Soporte / Customer Success, componente fijo + variable por ruta activa.
- CAC (costo de adquisición), aplicado por cada ruta nueva incorporada al mes.

## 3. Supuestos Clave por Escenario

Todos los valores son estimaciones de partida — no provienen de datos de mercado confirmados de competidores. Deben recalibrarse en cuanto se cuente con pricing real de la competencia (ver sección 6).

| Parámetro | Conservador | Base | Optimista |
|---|---|---|---|
| CAPEX MVP | $588,000 | $675,000 | $762,000 |
| Rutas máximas (tope, mes 36) | 25 | 40 | 60 |
| Unidades promedio por ruta | 12 | 15 | 18 |
| Tasa de activación | 70% | 85% | 95% |
| Precio suscripción/unidad/mes | $120 | $150 | $190 |
| Churn mensual (rutas) | 3.0% | 1.5% | 0.5% |
| Tasa de captura de multas | 25% | 40% | 55% |
| Multa promedio por infracción | $40 | $50 | $65 |

## 4. Resultados por Escenario (36 meses)

| | Conservador | Base | Optimista |
|---|---|---|---|
| Ingreso Total (36 meses) | $511,178 | $3,842,673 | $19,929,907 |
| &nbsp;&nbsp;— % vía Suscripción | 37.5% | 19.4% | 11.0% |
| &nbsp;&nbsp;— % vía Multas | 62.5% | 80.6% | 89.0% |
| OPEX Total (36 meses) | $1,553,059 | $1,613,177 | $1,528,568 |
| EBITDA Total (36 meses) | -$1,041,881 | $2,229,497 | $18,401,340 |
| CAPEX Inicial (MVP) | $588,000 | $675,000 | $762,000 |
| Flujo de Caja Acumulado — Mes 36 | -$1,629,881 | $1,554,497 | $17,639,340 |
| Mes de Breakeven | No alcanzado en 36 meses | Mes 24 | Mes 10 |
| Rutas Activas — Mes 36 | 8.8 | 20.2 | 37.4 |

El escenario Conservador no recupera la inversión dentro del horizonte de 36 meses y cierra con flujo acumulado negativo de aproximadamente -$1.63M MXN. El escenario Base alcanza el punto de equilibrio en el mes 24, con un flujo acumulado positivo de ~$1.55M MXN al cierre del periodo. El escenario Optimista revierte la inversión en solo 10 meses, generando un flujo acumulado de ~$17.6M MXN al mes 36 — pero depende de una tasa de captura de multas de 55% y una expansión a 60 rutas, ambos supuestos agresivos frente al contexto de resistencia sindical documentado en el estudio de mercado.

## 5. Riesgo Central: Concentración en Multas

Un patrón se repite en los tres escenarios: la proporción del ingreso que proviene de multas AUMENTA a medida que el negocio escala (62.5% → 80.6% → 89.0%), en lugar de disminuir. Esto ocurre porque el ingreso por multas crece de forma multiplicativa con las unidades activas, mientras que el ingreso por suscripción crece de forma lineal. En otras palabras: mientras más éxito tiene la expansión, más dependiente se vuelve el negocio del mecanismo de ingreso más frágil.

**Esto contradice el propósito original de introducir un modelo mixto. Si el objetivo es diversificar el riesgo de ingreso, el precio de suscripción y/o su ponderación relativa deben subir de forma sustancial, o debe explorarse un modelo de cuota base más alta con multas como ingreso complementario menor — no al revés.**

## 6. Recomendaciones

- Recalibrar el precio de suscripción y la tasa de captura de multas con datos reales de la competencia en cuanto estén disponibles.
- Modelar un cuarto escenario "suscripción-dominante", con precio de suscripción 2–3x mayor y tasa de captura de multas más conservadora, para evaluar si el negocio es viable sin depender del cobro de sanciones.
- Tratar el escenario Conservador como el caso base para decisiones de inversión — el escenario Optimista no debe usarse para justificar el CAPEX inicial ante inversionistas.
- Incorporar el costo de ventas ruta-por-ruta (ciclo de negociación con presidentes de ruta) de forma más granular; el CAC actual es un supuesto simplificado.

## 7. Archivo Fuente

Todos los cálculos de este informe provienen del libro de Excel "modelo_financiero_mixto.xlsx", con pestañas Supuestos, Modelo Ingresos, OPEX Mensual, P&L y Flujo de Caja, y Dashboard. El selector de escenario (Supuestos!B4) permite recalcular todo el modelo en vivo.
