package com.hdfc.docupload.ui.upload

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Builds the ML Kit document scanner. `SCANNER_MODE_FULL` enables the complete
 * Adobe Scan / Microsoft Lens style experience:
 *  - automatic edge & document detection
 *  - auto capture, auto crop and perspective correction
 *  - background removal and image enhancement
 *  - Color / Grayscale (B&W) / Original filters
 *  - multi-page scanning
 */
private fun scannerOptions(): GmsDocumentScannerOptions =
    GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setGalleryImportAllowed(false)
        .setPageLimit(20)
        .setResultFormats(
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        )
        .build()

/**
 * Remembers a launcher that starts the scanner and delivers captured page URIs.
 *
 * @param onResult invoked with the JPEG page uris (may be empty if cancelled).
 * @param onError  invoked when the scanner cannot be started or fails.
 * @return a lambda that, when called, opens the camera scanner directly.
 */
@Composable
fun rememberDocumentScanner(
    onResult: (List<Uri>) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = context.findActivity()

    val launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult =
                    GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pages = scanResult?.pages?.mapNotNull { it.imageUri } ?: emptyList()
                onResult(pages)
            }
            // RESULT_CANCELED is a silent no-op (user backed out).
        }

    return scan@{
        if (activity == null) {
            onError("Camera is unavailable on this device.")
            return@scan
        }
        GmsDocumentScanning.getClient(scannerOptions())
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                onError("Document scan failed. Please try again.")
            }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
