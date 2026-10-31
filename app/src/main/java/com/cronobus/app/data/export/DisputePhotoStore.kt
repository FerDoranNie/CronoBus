package com.cronobus.app.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Foto de evidencia recién capturada, todavía no vinculada a una apelación.
 *
 * @param file Archivo privado en `cache/dispute_photos/`.
 * @param uri URI `content://` autorizada temporalmente a la app de cámara.
 */
data class DisputePhoto(
    val file: File,
    val uri: Uri,
)

/**
 * Gestiona las fotos de evidencia de apelaciones dentro de la caché privada de la app.
 *
 * - Usa `FileProvider` (autoridad `${applicationId}.fileprovider`) para que la app de cámara pueda
 *   escribir en el archivo sin conceder acceso a toda la caché.
 * - Nunca expone `File.getAbsolutePath()` al WebView.
 * - [cleanupStale] elimina capturas huérfanas de sesiones anteriores para no crecer sin límite.
 *
 * Nota de producción: la evidencia se guarda en caché porque el Store & Forward la sube en cuanto
 * hay red. Si se requiere conservarla indefinidamente, mover el `cache-path` de
 * `res/xml/file_paths.xml` a un `files-path` persistente.
 */
class DisputePhotoStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val appContext = context.applicationContext

    private val photoDirectory: File
        get() = File(appContext.cacheDir, PHOTO_DIRECTORY).apply {
            if (!exists() && !mkdirs()) {
                error("No se pudo crear el directorio de evidencia fotográfica.")
            }
        }

    /** Crea una captura vacía y devuelve su archivo + URI para la app de cámara. */
    fun createPhoto(): DisputePhoto {
        // No se borran capturas antiguas automáticamente: una apelación puede seguir en la cola
        // offline más de 24 h y su URI debe seguir siendo válida hasta que se sincronice.
        val file = File(photoDirectory, "dispute_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}$FILE_PROVIDER_SUFFIX",
            file,
        )
        return DisputePhoto(file = file, uri = uri)
    }

    /** La cámara pudo cancelar o escribir un archivo de 0 bytes. */
    fun isUsable(photo: DisputePhoto): Boolean =
        photo.file.exists() && photo.file.length() > 0L

    fun delete(photo: DisputePhoto) {
        runCatching { photo.file.delete() }
    }

    /** Borra capturas no vinculadas con más de [maxAgeMs] de antigüedad. Uso manual. */
    fun cleanupStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        val cutoff = clock() - maxAgeMs
        photoDirectory.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.forEach { runCatching { it.delete() } }
    }

    private companion object {
        const val PHOTO_DIRECTORY = "dispute_photos"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        const val DEFAULT_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
    }
}
