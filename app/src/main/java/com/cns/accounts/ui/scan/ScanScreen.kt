package com.cns.accounts.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.cns.accounts.R
import com.cns.accounts.ui.settings.SettingsHeader
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.RequestMetaText
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Scans the login QR code a website shows (or any QR code; only CNS login codes are taken, see
 * [onScanned]). The camera preview fills a square in the middle. Without the camera permission
 * the square shows a camera icon; tapping it asks for the permission, or opens the app's system
 * settings once Android no longer asks.
 *
 * [onScanned] gets every QR code's text and says whether it was a login code; the first one
 * that is ends the scan.
 */
@Composable
fun ScanScreen(onBack: () -> Unit, onScanned: (String) -> Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    // Back from the system settings: the permission may have been given there.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) granted = hasCameraPermission(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        askedOnce = true
    }
    var notLoginCode by remember { mutableStateOf(false) }
    LaunchedEffect(notLoginCode) {
        if (notLoginCode) {
            delay(NotLoginHintMillis)
            notLoginCode = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SettingsHeader(
            title = stringResource(R.string.qr_scan_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(LocalRadii.current.card))
                    .background(colors.elevatedSurface)
                    .then(
                        if (granted) {
                            Modifier
                        } else {
                            Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.qr_scan_camera_off)) {
                                val activity = context as? android.app.Activity
                                val canAsk = !askedOnce || activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
                                if (canAsk) {
                                    permission.launch(Manifest.permission.CAMERA)
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    CameraPreview(onQrCode = { text -> if (!onScanned(text)) notLoginCode = true })
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.qr_scan_camera_icon),
                            tint = colors.onSurface,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            stringResource(if (askedOnce) R.string.qr_scan_camera_denied else R.string.qr_scan_camera_off),
                            color = colors.onSurface,
                            style = RequestMetaText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Text(
                stringResource(if (notLoginCode) R.string.qr_scan_not_login else R.string.qr_scan_hint),
                color = if (notLoginCode) colors.accent else colors.dialogSubtitle,
                style = RequestMetaText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The back camera's preview, decoding QR codes from its frames while the screen is shown. */
@Composable
private fun CameraPreview(onQrCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnQrCode by rememberUpdatedState(onQrCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        var lastText: String? = null
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(executor) { image ->
                    val text = image.use { decodeQr(reader, it) }
                    // The same code stays in view for many frames: report it once.
                    if (text != null && text != lastText) {
                        lastText = text
                        ContextCompat.getMainExecutor(context).execute { latestOnQrCode(text) }
                    }
                }
            }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val cameras = providerFuture.get()
            provider = cameras
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            runCatching {
                cameras.unbindAll()
                cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** The text of a QR code in the frame's brightness plane, if there is one. */
private fun decodeQr(reader: MultiFormatReader, image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate().apply { rewind() }
    val rowStride = plane.rowStride
    val bytes = ByteArray(buffer.remaining()).also(buffer::get)
    val source = PlanarYUVLuminanceSource(bytes, rowStride, image.height, 0, 0, image.width, image.height, false)
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    } finally {
        reader.reset()
    }
}

private fun hasCameraPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private const val NotLoginHintMillis = 2500L
