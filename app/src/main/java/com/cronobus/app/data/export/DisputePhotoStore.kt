package com.cronobus.app.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Foto de evidencia recién capturada, todavía no vinculada a una apelación.
 *
 * @param file Archivo privado persistente en `files/dispute_photos/`.
 * @param uri URI `content://` autorizada temporalmente a la app de cámara.
 */
data class DisputePhoto(
    val file: File,
    val uri: Uri,
)

/**
 * Gestiona las fotos de evidencia de apelaciones dentro del almacenamiento privado **persistente**
 * de la app (`filesDir`).
 *
 * - Usa `FileProvider` (autoridad `${applicationId}.fileprovider`) para que la app de cámara pueda
 *   escribir en el archivo sin conceder acceso a todo el almacenamiento privado.
 * - Nunca expone `File.getAbsolutePath()` al WebView.
 * - [cleanupStale] elimina capturas huérfanas de sesiones anteriores para no crecer sin límite.
 *
 * Producción: la evidencia vive en `filesDir` (no en caché) porque el Store & Forward puede tardar
 * horas o días en subirla y el sistema operativo purga `cacheDir` bajo presión de almacenamiento.
 * La raíz `files-path` está declarada en `res/xml/file_paths.xml`; la antigua raíz `cache-path`
 * se conserva solo para leer capturas heredadas de versiones anteriores.
 */
class DisputePhotoStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val appContext = context.applicationContext

    private val photoDirectory: File
        get() = File(appContext.filesDir, PHOTO_DIRECTORY).apply {
            if (!exists() && !mkdirs()) {
                error("No se pudo crear el directorio de evidencia fotográfica.")
            }
        }

    /** Crea una captura vacía y devuelve su archivo + URI para la app de cámara. */
    fun createPhoto(): DisputePhoto {
        val file = newPhotoFile()
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}$FILE_PROVIDER_SUFFIX",
            file,
        )
        return DisputePhoto(file = file, uri = uri)
    }

    /**
     * Reserva un nombre de archivo único en el directorio persistente.
     *
     * Se separa de [createPhoto] para poder verificar en pruebas JVM que la ruta es `filesDir`
     * sin depender de la resolución de raíces de `FileProvider` de Robolectric.
     */
    internal fun newPhotoFile(): File =
        // No se borran capturas antiguas automáticamente: una apelación puede seguir en la cola
        // offline más de 24 h y su URI debe seguir siendo válida hasta que se sincronice.
        File(photoDirectory, "dispute_${UUID.randomUUID()}.jpg")

    /**
     * Reconstruye una captura a partir del nombre guardado en `onSaveInstanceState`.
     * Solo acepta el patrón generado por [createPhoto] para impedir *path traversal*.
     */
    fun fromFileName(fileName: String): DisputePhoto? {
        if (!SAFE_NAME.matches(fileName)) return null
        val file = resolveExistingPhoto(fileName)
        val uri = runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}$FILE_PROVIDER_SUFFIX",
                file,
            )
        }.getOrNull() ?: return null
        return DisputePhoto(file = file, uri = uri)
    }

    /**
     * Las capturas nuevas viven en `filesDir`; si quedó una pendiente de una versión anterior en
     * `cacheDir`, se resuelve aquí para no perder la evidencia al actualizar la app.
     */
    private fun resolveExistingPhoto(fileName: String): File {
        val persistent = File(photoDirectory, fileName)
        if (persistent.exists()) return persistent
        val legacy = File(appContext.cacheDir, PHOTO_DIRECTORY).let { File(it, fileName) }
        return if (legacy.exists()) legacy else persistent
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
        val SAFE_NAME = Regex("^dispute_[0-9a-fA-F-]{36}\\.jpg$")
    }
}
