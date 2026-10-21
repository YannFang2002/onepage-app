package com.onepage.companion.ui.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner tuned for the two codes the reader draws:
 * the Wi-Fi payload (`WIFI:S:CrossPoint-Reader;;`) and the URL payload
 * (`http://192.168.4.1/`). ZXing runs locally on the analysis thread, so no
 * Play Services and no network are involved.
 */
@Composable
fun QrScannerScreen(
    onDetected: (String) -> Unit,
    onClose: () -> Unit,
    hint: String = "对准阅读器屏幕上的二维码",
    subtitle: String = "Wi-Fi 码用于加入设备热点，网址码可直接连接",
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Android 13+ gates hotspot joining behind NEARBY_WIFI_DEVICES; older
    // releases require location. Ask for both up front, on the scan screen.
    val wifiPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    val requiredPermissions = remember {
        arrayOf(Manifest.permission.CAMERA, wifiPermission)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants[Manifest.permission.CAMERA] == true
        permissionDenied = !hasPermission
    }

    LaunchedEffect(Unit) {
        val wifiGranted = ContextCompat.checkSelfPermission(context, wifiPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission || !wifiGranted) permissionLauncher.launch(requiredPermissions)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                onCameraReady = { camera = it },
                onDecoded = { raw ->
                    // Freeze immediately; the caller navigates away.
                    camera?.cameraControl?.enableTorch(false)
                    onDetected(raw)
                },
            )
            ScannerOverlay(
                hint = hint,
                subtitle = subtitle,
                torchOn = torchOn,
                onTorch = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                onClose = onClose,
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (permissionDenied) "需要相机权限才能扫码" else "正在打开相机…",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (permissionDenied) "请到系统设置里允许 OnePage 使用相机，或改用手动输入 IP。" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (permissionDenied) {
                        PillButton("重试") { permissionLauncher.launch(requiredPermissions) }
                    }
                    PillButton("返回") { onClose() }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onCameraReady: (Camera) -> Unit,
    onDecoded: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                if (handled.get()) {
                    image.close()
                    return@setAnalyzer
                }
                val text = decode(image)
                image.close()
                if (text != null && handled.compareAndSet(false, true)) {
                    previewView.post { onDecoded(text) }
                }
            }
            runCatching { provider.unbindAll() }
            val camera = runCatching {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.getOrNull()
            if (camera != null) onCameraReady(camera)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdownNow()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun decode(image: ImageProxy): String? {
    return runCatching {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val source = PlanarYUVLuminanceSource(bytes, image.width, image.height, 0, 0, image.width, image.height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                )
            )
        }
        reader.decodeWithState(bitmap)?.text
    }.getOrNull()
}

@Composable
private fun ScannerOverlay(
    hint: String,
    subtitle: String,
    torchOn: Boolean,
    onTorch: () -> Unit,
    onClose: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Box(Modifier.fillMaxSize()) {
        val frame = 0.72f
        Canvas(Modifier.fillMaxSize()) {
            val side = size.minDimension * frame
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            val scrim = Color.Black.copy(alpha = 0.62f)
            drawRect(scrim, Offset.Zero, Size(size.width, top))
            drawRect(scrim, Offset(0f, top + side), Size(size.width, size.height - top - side))
            drawRect(scrim, Offset(0f, top), Size(left, side))
            drawRect(scrim, Offset(left + side, top), Size(size.width - left - side, side))

            // corner brackets
            val corner = side * 0.14f
            val stroke = 3.dp.toPx()
            val accent = Color.White
            fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(accent, Offset(x, y), Offset(x + dx, y), stroke)
                drawLine(accent, Offset(x, y), Offset(x, y + dy), stroke)
            }
            bracket(left, top, corner, corner)
            bracket(left + side, top, -corner, corner)
            bracket(left, top + side, corner, -corner)
            bracket(left + side, top + side, -corner, -corner)

            // sweep line
            val y = top + side * sweep
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(left + 8.dp.toPx(), y),
                end = Offset(left + side - 8.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RoundIconButton(icon = Icons.Rounded.Close, description = "关闭", onClick = onClose)
            Spacer(Modifier.height(14.dp))
            Text(hint, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                RoundIconButton(
                    icon = if (torchOn) Icons.Rounded.FlashOff else Icons.Rounded.FlashOn,
                    description = "手电筒",
                    onClick = onTorch,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "读取是本机离线完成的，不会上传画面",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}
