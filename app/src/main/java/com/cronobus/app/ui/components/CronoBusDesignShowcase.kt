package com.cronobus.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronobus.app.ui.theme.ClassicSignalAccent
import com.cronobus.app.ui.theme.ClassicSignalBorder
import com.cronobus.app.ui.theme.SignalLightBackground
import com.cronobus.app.ui.theme.SignalLightError
import com.cronobus.app.ui.theme.SignalLightOnSecondaryContainer
import com.cronobus.app.ui.theme.SignalLightPrimary
import com.cronobus.app.ui.theme.SignalLightSecondary
import com.cronobus.app.ui.theme.SignalLightSecondaryContainer
import com.cronobus.app.ui.theme.SignalLightSurface

/**
 * Componentes de interfaz deterministas que representan las 3 vistas de CronoBus
 * siguiendo rigurosamente el sistema de diseño Classic Signal:
 * - Bordes sólidos de 1px (#c4c7c7).
 * - Esquinas rectas / rectangulares (cero radio difuso).
 * - Cero sombras difusas (elevación 0.dp).
 * - Paleta estricta: Primary (#0a0a0a), Secondary (#0b6c4b), Accent (#ffba38), Error (#ba1a1a).
 */

@Composable
fun DriverShiftStatusCard(
    unitId: String,
    routeName: String,
    headwayText: String,
    statusLabel: String,
    pendingSyncCount: Int,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val borderColor = if (isDark) Color(0xFF333333) else ClassicSignalBorder
    val primaryText = if (isDark) Color(0xFFF5F5F5) else SignalLightPrimary
    val mutedText = if (isDark) Color(0xFFC4C7C7) else Color(0xFF444748)
    val surfaceBox = if (isDark) Color(0xFF141414) else SignalLightSurface
    val greenValue = if (isDark) Color(0xFFA0F4CA) else SignalLightSecondary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header: Unidad y Estado de Conexión
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(SignalLightSecondary)
                        .border(1.dp, borderColor, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "UNIDAD $unitId",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isOnline) greenValue else SignalLightError,
                                shape = CircleShape,
                            )
                            .border(1.dp, borderColor, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnline) "EN LÍNEA" else "MODO OFFLINE",
                        color = primaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ruta
            Text(
                text = routeName.uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = primaryText,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = borderColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Checador Digital: Headway e Intervalo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "INTERVALO / HEADWAY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                    Text(
                        text = headwayText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = greenValue,
                        letterSpacing = (-0.5).sp,
                    )
                }

                // Badge de Estatus Operativo
                Box(
                    modifier = Modifier
                        .background(if (isDark) Color(0xFF0B3D2B) else SignalLightSecondaryContainer)
                        .border(1.dp, if (isDark) SignalLightSecondary else SignalLightOnSecondaryContainer, RectangleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = statusLabel.uppercase(),
                        color = if (isDark) SignalLightSecondaryContainer else SignalLightOnSecondaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer: Cola de Sincronización Resiliente Store & Forward
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceBox)
                    .border(1.dp, borderColor, RectangleShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "STORE & FORWARD QUEUE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                    Text(
                        text = if (pendingSyncCount > 0) "SYNC: $pendingSyncCount PEND" else "SYNC: AL DÍA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (pendingSyncCount > 0) ClassicSignalAccent else greenValue,
                    )
                }
            }
        }
    }
}

@Composable
fun AdminFinancialDispatchCard(
    activeUnitsText: String,
    saasTotalText: String,
    netFinesText: String,
    bonusTotalText: String,
    forceMajeureActive: Boolean,
    nextDispatchUnit: String,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val borderColor = if (isDark) Color(0xFF333333) else ClassicSignalBorder
    val primaryText = if (isDark) Color(0xFFF5F5F5) else SignalLightPrimary
    val mutedText = if (isDark) Color(0xFFC4C7C7) else Color(0xFF444748)
    val greenValue = if (isDark) Color(0xFFA0F4CA) else SignalLightSecondary
    val errorValue = if (isDark) Color(0xFFFFB4AB) else SignalLightError

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header Despacho ITS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DESPACHO & CONTROL ITS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = primaryText,
                )
                Box(
                    modifier = Modifier
                        .background(SignalLightPrimary)
                        .border(1.dp, borderColor, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "SALIDA: $nextDispatchUnit",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Alerta de Fuerza Mayor
            if (forceMajeureActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClassicSignalAccent)
                        .border(1.dp, SignalLightPrimary, RectangleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "⚠ EXENCIÓN POR FUERZA MAYOR ACTIVA EN CORREDOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = SignalLightPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = borderColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Rejilla Financiera 2x2
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "UNIDADES EN RUTA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                    Text(
                        text = activeUnitsText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryText,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RECAUDACIÓN SAAS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                    Text(
                        text = saasTotalText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = greenValue,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MULTAS NETAS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                    Text(
                        text = netFinesText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = errorValue,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BONO PUNTUALIDAD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                    Text(
                        text = bonusTotalText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryText,
                    )
                }
            }
        }
    }
}

@Composable
fun PassengerEtaCard(
    routeName: String,
    stopName: String,
    etaMinutes: Int,
    occupancyLabel: String,
    isStaleCache: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val borderColor = if (isDark) Color(0xFF333333) else ClassicSignalBorder
    val primaryText = if (isDark) Color(0xFFF5F5F5) else SignalLightPrimary
    val mutedText = if (isDark) Color(0xFFC4C7C7) else Color(0xFF444748)
    val surfaceBox = if (isDark) Color(0xFF141414) else SignalLightSurface
    val greenValue = if (isDark) Color(0xFFA0F4CA) else SignalLightSecondary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Ruta y Parada
            Text(
                text = routeName.uppercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = primaryText,
            )
            Text(
                text = "PARADA: $stopName",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = mutedText,
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = borderColor)
            Spacer(modifier = Modifier.height(10.dp))

            // Estimación ETA y Nivel de Ocupación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "PRÓXIMO ARRIBO ESTIMADO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                    Text(
                        text = if (etaMinutes <= 0) "< 1 min" else "$etaMinutes min",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = greenValue,
                        letterSpacing = (-1).sp,
                    )
                }

                // Nivel de ocupación anónimo agregado (LGPDPPSO)
                val (occupancyBg, occupancyFg) = when (occupancyLabel.uppercase()) {
                    "BAJA" -> Pair(SignalLightSecondaryContainer, SignalLightOnSecondaryContainer)
                    "MEDIA" -> Pair(ClassicSignalAccent, SignalLightPrimary)
                    else -> Pair(Color(0xFFFFDAD6), SignalLightError)
                }

                Box(
                    modifier = Modifier
                        .background(occupancyBg)
                        .border(1.dp, occupancyFg, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "OCUPACIÓN: $occupancyLabel",
                        color = occupancyFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isStaleCache) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceBox)
                        .border(1.dp, borderColor, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "📡 ESTIMACIÓN CON CACHÉ LOCAL RESILIENTE (SIN RED)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedText,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CronoBusDesignShowcasePreview() {
    Surface(
        modifier = Modifier.padding(16.dp),
        color = SignalLightBackground,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DriverShiftStatusCard(
                unitId = "U-104",
                routeName = "Ruta 4 - Directo Base Norte",
                headwayText = "+01:30 min",
                statusLabel = "A TIEMPO",
                pendingSyncCount = 2,
                isOnline = true,
            )

            AdminFinancialDispatchCard(
                activeUnitsText = "18 / 20",
                saasTotalText = "$6,300 MXN",
                netFinesText = "$450 MXN",
                bonusTotalText = "$350 MXN",
                forceMajeureActive = true,
                nextDispatchUnit = "U-108",
            )

            PassengerEtaCard(
                routeName = "Ruta 4 - Directo Base Norte",
                stopName = "Av. Central & Calle 5",
                etaMinutes = 4,
                occupancyLabel = "MEDIA",
                isStaleCache = true,
            )
        }
    }
}
