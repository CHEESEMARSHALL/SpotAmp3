package com.example.ui

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.data.CompanionPairingPayload
import com.example.data.parseCompanionPairingPayload
import com.google.zxing.BinaryBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.PlanarYUVLuminanceSource
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val pairingQrHints = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true
)

private const val PAIRING_PERMISSION_PREFERENCES = "companion_pairing_permissions"
private const val CAMERA_DENIED_BEFORE_KEY = "camera_denied_before"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun decodePairingQr(source: LuminanceSource): String? {
    val candidates = buildList {
        add(source)
        if (source.isRotateSupported) {
            add(source.rotateCounterClockwise())
            add(source.rotateCounterClockwise().rotateCounterClockwise())
            add(source.rotateCounterClockwise().rotateCounterClockwise().rotateCounterClockwise())
        }
    }
    for (candidate in candidates) {
        val decoded = runCatching {
            MultiFormatReader().apply { setHints(pairingQrHints) }
                .decodeWithState(BinaryBitmap(HybridBinarizer(candidate)))
        }.getOrNull() ?: continue
        return decoded.text
    }
    return null
}

private class PairingAnalyzer(
    private val onPayload: (CompanionPairingPayload) -> Unit,
    private val onError: (String) -> Unit,
    private val invalidQrMessage: String,
    private val readErrorMessage: String
) : ImageAnalysis.Analyzer {
    private val delivered = AtomicBoolean(false)
    private val errorShown = AtomicBoolean(false)
    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val width = image.width
            val height = image.height
            val sourceBytes = ByteArray(width * height)
            val buffer = plane.buffer
            for (row in 0 until height) {
                buffer.position(row * plane.rowStride)
                buffer.get(sourceBytes, row * width, width)
            }
            val source = PlanarYUVLuminanceSource(sourceBytes, width, height, 0, 0, width, height, false)
            val decodedText = decodePairingQr(source)
            if (decodedText == null) {
                errorShown.set(false)
                return
            }
            val payload = runCatching { parseCompanionPairingPayload(decodedText) }.getOrElse {
                if (errorShown.compareAndSet(false, true)) {
                    onError(invalidQrMessage)
                }
                return
            }
            if (delivered.compareAndSet(false, true)) onPayload(payload)
        } catch (_: NotFoundException) {
            // The analyzer normally reaches this path while the QR is out of frame.
            errorShown.set(false)
        } catch (_: Exception) {
            if (errorShown.compareAndSet(false, true)) {
                onError(readErrorMessage)
            }
        } finally {
            image.close()
        }
    }
}

@Composable
private fun PairingCamera(
    onPayload: (CompanionPairingPayload) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner) {
        val listener = Runnable {
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            executor,
                            PairingAnalyzer(
                                onPayload = onPayload,
                                onError = onError,
                                invalidQrMessage = context.getString(R.string.pairing_invalid_qr),
                                readErrorMessage = context.getString(R.string.pairing_read_qr_error)
                            )
                        )
                    }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { onError(context.getString(R.string.pairing_camera_start_error)) }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdownNow()
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(280.dp)
    )
}

@Composable
internal fun PairingDialogBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PairingConfirmationContent(
    payload: CompanionPairingPayload,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    claiming: Boolean,
    onScanAgain: () -> Unit,
    onCancel: () -> Unit,
    onClaim: () -> Unit
) {
    Text(stringResource(R.string.pairing_endpoint, payload.baseUrl))
    OutlinedTextField(
        value = deviceName,
        onValueChange = onDeviceNameChange,
        label = { Text(stringResource(R.string.pairing_device_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text(stringResource(R.string.pairing_confirm_endpoint))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            enabled = !claiming,
            onClick = onScanAgain,
            modifier = Modifier.testTag("pairing_scan_again")
        ) { Text(stringResource(R.string.pairing_scan_again)) }
        OutlinedButton(
            enabled = !claiming,
            onClick = onCancel,
            modifier = Modifier.testTag("pairing_cancel_confirm")
        ) { Text(stringResource(R.string.pairing_cancel)) }
        Button(
            enabled = !claiming && deviceName.isNotBlank(),
            modifier = Modifier.testTag("pairing_claim"),
            onClick = onClaim
        ) {
            if (claiming) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.pairing_claiming_device))
            } else {
                Text(stringResource(R.string.pairing_claim_device))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CompanionPairingDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onPairingComplete: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionPreferences = remember(context) {
        context.getSharedPreferences(PAIRING_PERMISSION_PREFERENCES, Context.MODE_PRIVATE)
    }
    val hasPermission = remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var localError by remember { mutableStateOf<String?>(null) }
    var cameraPermissionDenied by remember {
        mutableStateOf(
            !hasPermission.value && permissionPreferences.getBoolean(CAMERA_DENIED_BEFORE_KEY, false)
        )
    }
    var permissionStatusRevision by remember { mutableStateOf(0) }
    val cameraPermissionBlocked = rememberCameraPermissionBlocked(
        permissionStatusRevision = permissionStatusRevision,
        hasPermission = hasPermission.value,
        cameraDeniedBefore = cameraPermissionDenied,
        shouldShowRationale = context.findActivity()
            ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
    )
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission.value = granted
        cameraPermissionDenied = !granted
        permissionStatusRevision++
        permissionPreferences.edit().putBoolean(CAMERA_DENIED_BEFORE_KEY, !granted).apply()
        if (!granted) localError = context.getString(R.string.pairing_camera_denied)
    }
    var payload by remember { mutableStateOf<CompanionPairingPayload?>(null) }
    var deviceName by remember { mutableStateOf("SpotAmp ${Build.MODEL.orEmpty()}".trim()) }
    var claiming by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatusRevision++
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                hasPermission.value = granted
                if (granted) {
                    cameraPermissionDenied = false
                    permissionPreferences.edit().putBoolean(CAMERA_DENIED_BEFORE_KEY, false).apply()
                    if (localError == context.getString(R.string.pairing_camera_denied)) {
                        localError = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Dialog(onDismissRequest = { if (!claiming) onDismiss() }) {
        Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            PairingDialogBody {
                Text(
                    stringResource(if (payload == null) R.string.pairing_scan_title else R.string.pairing_confirm_title),
                    modifier = Modifier.testTag("pairing_dialog_title")
                )
                if (payload == null) {
                    if (hasPermission.value) {
                        PairingCamera(
                            onPayload = { payload = it; localError = null },
                            onError = { localError = it }
                        )
                        Text(stringResource(R.string.pairing_camera_frame_help))
                    } else {
                        CameraPermissionPrompt(
                            cameraPermissionDenied = cameraPermissionDenied,
                            cameraPermissionBlocked = cameraPermissionBlocked,
                            onRequestPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
                            onOpenAppSettings = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        )
                    }
                } else {
                    PairingConfirmationContent(
                        payload = payload!!,
                        deviceName = deviceName,
                        onDeviceNameChange = { deviceName = it.take(80) },
                        claiming = claiming,
                        onScanAgain = { payload = null; localError = null },
                        onCancel = onDismiss,
                        onClaim = {
                            localError = null
                            claiming = true
                            viewModel.claimCompanionPairing(
                                payload = payload!!,
                                deviceName = deviceName,
                                onSuccess = onPairingComplete,
                                onFailure = { claiming = false; localError = it }
                            )
                        }
                    )
                }
                when {
                    cameraPermissionBlocked -> PairingInlineError(
                        stringResource(R.string.pairing_camera_blocked_error)
                    )
                    cameraPermissionDenied -> PairingInlineError(
                        localError ?: stringResource(R.string.pairing_camera_denied)
                    )
                    else -> localError?.let { PairingInlineError(it) }
                }
                if (payload == null) {
                    Spacer(Modifier.height(2.dp))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pairing_cancel)) }
                }
            }
        }
    }
}

@Composable
internal fun PairingInlineError(message: String) {
    Text(
        message,
        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
        modifier = Modifier
            .testTag("pairing_error")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                error(message)
            }
    )
}

@Composable
internal fun CameraPermissionPrompt(
    cameraPermissionDenied: Boolean,
    cameraPermissionBlocked: Boolean = false,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Text(
        stringResource(
            if (cameraPermissionBlocked) R.string.pairing_camera_blocked_help
            else R.string.pairing_camera_help
        )
    )
    if (cameraPermissionBlocked) {
        Button(onClick = onOpenAppSettings) {
            Text(stringResource(R.string.pairing_open_app_settings))
        }
    } else {
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.pairing_continue_camera))
        }
    }
    if (cameraPermissionDenied && !cameraPermissionBlocked) {
        OutlinedButton(onClick = onOpenAppSettings) {
            Text(stringResource(R.string.pairing_open_app_settings))
        }
    }
}

internal fun cameraPermissionIsBlocked(
    hasPermission: Boolean,
    cameraDeniedBefore: Boolean,
    shouldShowRationale: Boolean
): Boolean = !hasPermission && cameraDeniedBefore && !shouldShowRationale

@Composable
internal fun rememberCameraPermissionBlocked(
    permissionStatusRevision: Int,
    hasPermission: Boolean,
    cameraDeniedBefore: Boolean,
    shouldShowRationale: Boolean
): Boolean = remember(permissionStatusRevision, hasPermission, cameraDeniedBefore, shouldShowRationale) {
    cameraPermissionIsBlocked(hasPermission, cameraDeniedBefore, shouldShowRationale)
}
