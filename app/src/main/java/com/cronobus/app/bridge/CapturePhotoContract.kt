package com.cronobus.app.bridge

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/**
 * Contrato de captura de evidencia con la app de cámara del sistema.
 *
 * Se usa un contrato propio en lugar de `ActivityResultContracts.TakePicture` porque es necesario
 * conceder explícitamente `FLAG_GRANT_WRITE_URI_PERMISSION` y adjuntar el URI en `ClipData`; sin
 * ambos, varias apps de cámara (Samsung, Xiaomi) devuelven `RESULT_CANCELED` o escriben en una ruta
 * no autorizada al recibir un URI de `FileProvider`.
 *
 * No requiere `CAMERA` en tiempo de ejecución: la captura la realiza la app de cámara del sistema,
 * que ya tiene su propio permiso. La app solo recibe la foto mediante el URI concedido.
 */
class CapturePhotoContract : ActivityResultContract<Uri, Boolean>() {

    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            clipData = ClipData.newRawUri("dispute_photo", input)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}
