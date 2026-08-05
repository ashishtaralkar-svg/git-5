package com.hdfc.docupload.ui.upload

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.ddn.DeskewedImageResultItem
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.hdfc.docupload.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Document scanner powered by Dynamsoft Capture Vision: CameraX drives the
 * live preview + still capture, and [CaptureVisionRouter] runs automatic
 * document boundary detection + perspective correction ("deskew") on each
 * captured frame — the same [Constants.DYNAMSOFT_LICENSE] key used by the
 * web scanner (web/js/scanner.js).
 *
 * Flow (mirrors the web scanner): live camera -> tap shutter -> auto-detect
 * & correct -> Original/Color/B&W filter -> keep (loops back for the next
 * page) or retake. Tapping close/done returns every kept page.
 */
private const val TEMPLATE_DETECT_AND_NORMALIZE = "DetectAndNormalizeDocument_Default"

private enum class ScanFilter(val label: String) {
    ORIGINAL("Original"), COLOR("Color"), BW("B&W")
}

private sealed interface ScanMode {
    data object Live : ScanMode
    data class Reviewing(val corrected: Bitmap) : ScanMode
}

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
    var isOpen by remember { mutableStateOf(false) }

    if (isOpen) {
        ScannerDialog(
            onDone = { uris -> isOpen = false; onResult(uris) },
            onError = { message -> isOpen = false; onError(message) }
        )
    }

    return { isOpen = true }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ScannerDialog(onDone: (List<Uri>) -> Unit, onError: (String) -> Unit) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Dialog(
        onDismissRequest = { onDone(emptyList()) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = ComposeColor.Black) {
            if (cameraPermission.status.isGranted) {
                ScannerFlow(onDone = onDone, onError = onError)
            } else {
                PermissionGate(
                    shouldShowRationale = cameraPermission.status.shouldShowRationale,
                    onRequest = { cameraPermission.launchPermissionRequest() },
                    onCancel = { onDone(emptyList()) }
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(
    shouldShowRationale: Boolean,
    onRequest: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (shouldShowRationale)
                "Camera access is needed to scan documents. Please allow it to continue."
            else
                "This app needs camera access to scan documents.",
            color = ComposeColor.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row {
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.DarkGray)) {
                Text("Cancel")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onRequest) { Text("Grant camera access") }
        }
    }
}

@Composable
private fun ScannerFlow(onDone: (List<Uri>) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val pages = remember { mutableStateListOf<Uri>() }
    var mode by remember { mutableStateOf<ScanMode>(ScanMode.Live) }
    var filter by remember { mutableStateOf(ScanFilter.COLOR) }
    var status by remember { mutableStateOf("Starting camera…") }
    var busy by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var router by remember { mutableStateOf<CaptureVisionRouter?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // The dialog closing doesn't end the hosting screen's lifecycle (CameraX only
    // auto-unbinds when the LifecycleOwner it was bound to stops), so the camera
    // must be released explicitly or it stays held/lit after "Close"/"Done".
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
        }
    }

    LaunchedEffect(Unit) {
        val initializedRouter = try {
            withContext(Dispatchers.IO) { CaptureVisionRouter(context) }
        } catch (e: Exception) {
            onError("Scanner couldn't start (${e.message ?: e.javaClass.simpleName}).")
            return@LaunchedEffect
        }
        router = initializedRouter

        try {
            val provider = context.awaitCameraProvider()
            cameraProvider = provider
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
            status = "Tap the shutter to capture"
        } catch (e: Exception) {
            onError("Camera couldn't start (${e.message ?: e.javaClass.simpleName}).")
        }
    }

    fun capture() {
        val cvRouter = router ?: return
        if (busy) return
        busy = true
        status = "Processing…"
        val photoFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val bitmap = decodeRotatedBitmap(photoFile)
                            val corrected = normalizeDocument(cvRouter, bitmap)
                            photoFile.delete()
                            withContext(Dispatchers.Main) {
                                filter = ScanFilter.COLOR
                                mode = ScanMode.Reviewing(corrected)
                                status = "Choose a filter, then keep"
                                busy = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                busy = false
                                status = "Couldn't process the page (${e.message ?: e.javaClass.simpleName}). Try again."
                            }
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    busy = false
                    status = "Capture failed (${exc.message ?: exc.javaClass.simpleName}). Try again."
                }
            }
        )
    }

    fun retake() {
        mode = ScanMode.Live
        status = "Tap the shutter to capture"
    }

    fun keep(bitmap: Bitmap) {
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val filtered = applyFilter(bitmap, filter)
                val dir = File(context.filesDir, "documents").apply { mkdirs() }
                val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out -> filtered.compress(Bitmap.CompressFormat.JPEG, Constants.JPEG_QUALITY, out) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) {
                    pages.add(uri)
                    busy = false
                    retake()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    busy = false
                    status = "Couldn't save the page (${e.message ?: e.javaClass.simpleName})."
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val m = mode) {
            is ScanMode.Live ->
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            is ScanMode.Reviewing -> {
                val filtered = remember(m.corrected, filter) { applyFilter(m.corrected, filter) }
                Image(
                    bitmap = filtered.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top bar: close + status + torch (live only)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onDone(pages.toList()) }) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ComposeColor.White)
            }
            Text(
                text = status,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            if (mode is ScanMode.Live) {
                IconButton(onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                }) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "Flash",
                        tint = if (torchOn) ComposeColor.Yellow else ComposeColor.White
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        if (busy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ComposeColor.White)
        }

        when (val m = mode) {
            is ScanMode.Live -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.size(52.dp))
                    ShutterButton(enabled = !busy, onClick = ::capture)
                    IconButton(onClick = { onDone(pages.toList()) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Check, contentDescription = "Done", tint = ComposeColor.White)
                            if (pages.isNotEmpty()) {
                                Text(pages.size.toString(), color = ComposeColor.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            is ScanMode.Reviewing -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ScanFilter.entries.forEach { f ->
                            FilterChip(label = f.label, selected = filter == f, onClick = { filter = f })
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = ::retake, enabled = !busy) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retake", tint = ComposeColor.White)
                        }
                        Button(
                            onClick = { keep(m.corrected) },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1A7F37))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Keep")
                        }
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (enabled) ComposeColor.White.copy(alpha = .25f) else ComposeColor.White.copy(alpha = .1f))
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(ComposeColor.White)
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = ComposeColor.Black.copy(alpha = .55f),
        contentColor = if (selected) ComposeColor(0xFFFFD54A) else ComposeColor.White,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, ComposeColor(0xFFFFD54A)) else null
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { cont.resume(future.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }

private fun decodeRotatedBitmap(file: File): Bitmap {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        ?: error("Couldn't decode the captured image")
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Runs Dynamsoft's document boundary detection + perspective correction on a still frame. */
private fun normalizeDocument(router: CaptureVisionRouter, bitmap: Bitmap): Bitmap {
    val result = router.capture(bitmap, TEMPLATE_DETECT_AND_NORMALIZE)
    val item = result.items?.firstOrNull { it is DeskewedImageResultItem } as? DeskewedImageResultItem
    // No document detected (e.g. low contrast background) -> fall back to the full frame,
    // same graceful degradation the web scanner uses.
    return item?.imageData?.toBitmap() ?: bitmap
}

private fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap = when (filter) {
    ScanFilter.ORIGINAL -> source
    ScanFilter.COLOR -> autoContrast(source)
    ScanFilter.BW -> grayscaleThreshold(source)
}

/** Stretches the luminance histogram to its 2nd/98th percentile range. */
private fun autoContrast(source: Bitmap): Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    val hist = IntArray(256)
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        hist[((r * 299 + g * 587 + b * 114) / 1000)]++
    }
    val total = pixels.size
    var lo = 0
    var hi = 255
    var acc = 0
    for (i in 0..255) {
        acc += hist[i]
        if (acc > total * 0.02) { lo = i; break }
    }
    acc = 0
    for (i in 255 downTo 0) {
        acc += hist[i]
        if (acc > total * 0.02) { hi = i; break }
    }
    val range = (hi - lo).coerceAtLeast(1)

    fun stretch(c: Int): Int = (((c - lo) * 255) / range).coerceIn(0, 255)

    for (i in pixels.indices) {
        val p = pixels[i]
        val r = stretch((p shr 16) and 0xFF)
        val g = stretch((p shr 8) and 0xFF)
        val b = stretch(p and 0xFF)
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

/** Grayscale + Otsu threshold, for a scanned black & white page look. */
private fun grayscaleThreshold(source: Bitmap): Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    val gray = IntArray(pixels.size)
    val hist = IntArray(256)
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val l = (r * 299 + g * 587 + b * 114) / 1000
        gray[i] = l
        hist[l]++
    }

    val n = pixels.size
    var sum = 0L
    for (i in 0..255) sum += i.toLong() * hist[i]
    var sumB = 0L
    var wB = 0L
    var maxVar = 0.0
    var threshold = 127
    for (i in 0..255) {
        wB += hist[i]
        if (wB == 0L) continue
        val wF = n - wB
        if (wF == 0L) break
        sumB += i.toLong() * hist[i]
        val mB = sumB.toDouble() / wB
        val mF = (sum - sumB).toDouble() / wF
        val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
        if (between > maxVar) { maxVar = between; threshold = i }
    }

    for (i in pixels.indices) {
        val v = if (gray[i] > threshold) 255 else 0
        pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
