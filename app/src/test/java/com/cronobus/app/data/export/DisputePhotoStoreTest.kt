package com.cronobus.app.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Garantiza que la evidencia de apelaciones se escriba en `filesDir` (persistente) y no en la
 * caché que Android puede purgar bajo presión de almacenamiento.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisputePhotoStoreTest {

    @Test
    fun `evidence lives under files dir and never under cache dir`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DisputePhotoStore(context)

        // Se evita FileProvider (Robolectric resuelve raíces con otro package context), pero se
        // valida exactamente la misma ruta que usa createPhoto().
        val file = store.newPhotoFile()
        val photo = DisputePhoto(file = file, uri = android.net.Uri.EMPTY)

        try {
            val expectedDirectory = File(context.filesDir, "dispute_photos").canonicalFile
            assertEquals(expectedDirectory, file.parentFile?.canonicalFile)
            assertTrue(file.absolutePath.startsWith(context.filesDir.absolutePath))
            assertFalse(file.absolutePath.startsWith(context.cacheDir.absolutePath))

            // La cámara aún no escribió: un archivo de 0 bytes no es evidencia válida.
            assertFalse(store.isUsable(photo))
            file.writeBytes(byteArrayOf(0x11, 0x22, 0x33, 0x44))
            assertTrue(store.isUsable(photo))
        } finally {
            store.delete(photo)
            File(context.filesDir, "dispute_photos").delete()
        }
    }

    @Test
    fun `fromFileName rejects path traversal and unknown patterns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DisputePhotoStore(context)

        assertNull(store.fromFileName("../../databases/cronobus.db"))
        assertNull(store.fromFileName("dispute_123.jpg"))
        assertNull(store.fromFileName("otra_cosa.jpg"))
        assertNull(store.fromFileName(""))
    }
}
