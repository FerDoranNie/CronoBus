package com.cronobus.app.data.local

/**
 * Estado de sincronización en la cola resiliente Store & Forward.
 */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
}

