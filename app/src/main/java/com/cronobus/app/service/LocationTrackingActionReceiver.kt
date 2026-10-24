package com.cronobus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receptor explícito para la acción "Detener rastreo" de la notificación persistente.
 *
 * Se usa un `BroadcastReceiver` en lugar de un `PendingIntent.getService` para evitar que
 * Android 12+ intente arrancar un servicio en segundo plano cuando el turno ya terminó.
 * El receptor solo detiene el servicio ya existente y nunca lo inicia.
 */
class LocationTrackingActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_TRACKING) {
            LocationTrackingService.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP_TRACKING = "com.cronobus.app.action.STOP_LOCATION_TRACKING_FROM_NOTIFICATION"
    }
}
