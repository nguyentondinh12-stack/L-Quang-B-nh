package com.example.ui.pairing

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.media.QrCodeHelper
import com.example.ui.components.UserAvatar
import com.example.ui.viewmodel.TogetherViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: TogetherViewModel,
    initialInviteCode: String?,
    onClearInitialInviteCode: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val pairingCodes by viewModel.activePairingCodes.collectAsStateWithLifecycle()
    val lookupResult by viewModel.pairingLookupResult.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val currentCode = pairingCodes.firstOrNull()?.code ?: myProfile?.inviteCode.orEmpty()
    val qrBitmap: Bitmap? = remember(currentCode) {
        if (currentCode.isNotBlank()) {
            QrCodeHelper.generateQrBitmap(QrCodeHelper.buildPairingUri(currentCode), 420)
        } else null
    }

    var inputCode by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(initialInviteCode) {
        if (!initialInviteCode.isNullOrBlank()) {
            inputCode = initialInviteCode
            viewModel.lookupPairingCode(initialInviteCode)
            onClearInitialInviteCode()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showQrScanner = true
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ghép đôi riêng tư", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Tối đa 2 tài khoản trong một kết nối",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSignOutConfirm = true },
                        modifier = Modifier.testTag("pairing_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Đăng xuất"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_pairing_connect),
                    contentDescription = "Kết nối 2 người",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop
                )

                // My Invite Code & QR Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Mã mời của bạn",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Gửi mã hoặc cho người kia quét mã QR để kết nối.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(14.dp))

                        if (qrBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                tonalElevation = 2.dp,
                                color = androidx.compose.ui.graphics.Color.White
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Mã QR kết nối $currentCode",
                                    modifier = Modifier
                                        .size(180.dp)
                                        .padding(10.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = currentCode.ifBlank { "Đang tạo..." },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (currentCode.isNotBlank()) {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Mã Together GPS", currentCode))
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("copy_code_button"),
                                enabled = currentCode.isNotBlank()
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sao chép")
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (currentCode.isNotBlank()) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Kết nối với mình trên Together GPS bằng mã: $currentCode (hoặc mở liên kết ${QrCodeHelper.buildPairingUri(currentCode)})"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Mời kết nối Together GPS"))
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("share_code_button"),
                                enabled = currentCode.isNotBlank()
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Gửi mời")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = { viewModel.createNewPairingCode() },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("refresh_pairing_code_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pairingCodes.isEmpty()) "Kích hoạt mã mời mới" else "Tạo mã mời khác")
                        }
                    }
                }

                // Enter Partner's Code or Scan QR Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Nhập mã từ người kia",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
                            label = { Text("Mã kết nối (ví dụ: TG8492AB)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("partner_code_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) showQrScanner = true
                                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("scan_qr_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quét QR")
                            }

                            Button(
                                onClick = { viewModel.lookupPairingCode(inputCode) },
                                enabled = inputCode.length >= 6 && !isBusy,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("lookup_code_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Kiểm tra mã")
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Pairing Dialog
    lookupResult?.let { found ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPairingLookup() },
            title = { Text("Xác nhận kết nối riêng tư") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UserAvatar(
                        name = found.ownerName,
                        photoUrl = found.ownerPhotoUrl,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        size = 64.dp
                    )
                    Text(
                        text = "Bạn muốn ghép đôi với ${found.ownerName} (Mã ${found.code})?",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Kết nối này chỉ dành riêng cho 2 tài khoản. Bạn có thể tạm dừng hoặc ngắt kết nối bất kỳ lúc nào.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptPairing(found) },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("accept_pairing_button")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chấp nhận kết nối")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPairingLookup() }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner = false
                inputCode = code
                viewModel.lookupPairingCode(code)
            },
            onDismiss = { showQrScanner = false }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Đăng xuất") },
            text = { Text("Bạn có chắc muốn đăng xuất khỏi tài khoản Google hiện tại?") },
            confirmButton = {
                Button(onClick = {
                    showSignOutConfirm = false
                    onSignOut()
                }) {
                    Text("Đăng xuất")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun QrScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val scanner = BarcodeScanning.getClient()
                            val analyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null && !hasScanned) {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            scanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        val extracted = QrCodeHelper.extractCodeFromQr(barcode.rawValue)
                                                        if (extracted != null && !hasScanned) {
                                                            hasScanned = true
                                                            onCodeScanned(extracted)
                                                            break
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analyzer
                                )
                            } catch (e: Exception) {
                                Log.e("QrScanner", "Failed to bind camera", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Hướng camera vào mã QR của người kia",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(999.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Đóng",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}
