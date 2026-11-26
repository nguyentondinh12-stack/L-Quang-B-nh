package com.example.ui.memories

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AlbumScope
import com.example.location.DeviceTelemetryHelper
import com.example.media.PhotoCompressionHelper
import com.example.ui.components.Base64PhotoImage
import com.example.ui.viewmodel.TogetherViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CameraCheckInScreen(
    viewModel: TogetherViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val capturedPhotos = remember { mutableStateListOf<String>() }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessingPhoto = true
                for (uri in uris) {
                    if (capturedPhotos.size >= 4) break
                    val encoded = PhotoCompressionHelper.compressUriToBase64(context, uri)
                    if (encoded != null) {
                        capturedPhotos.add(encoded)
                    }
                }
                isProcessingPhoto = false
            }
        }
    }

    var placeName by remember {
        mutableStateOf(
            myLocation?.currentPlaceName
                ?: myLocation?.address
                ?: ""
        )
    }
    LaunchedEffect(myLocation?.currentPlaceName, myLocation?.address) {
        if (placeName.isBlank()) {
            val suggested = myLocation?.currentPlaceName ?: myLocation?.address
            if (!suggested.isNullOrBlank()) {
                placeName = suggested
            }
        }
    }

    var note by remember { mutableStateOf("") }
    var albumScope by remember { mutableStateOf(AlbumScope.SHARED) }
    var selectedTripId by remember { mutableStateOf<String?>(null) }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(4f) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewPhotoIndex by remember { mutableStateOf<Int?>(null) }

    val nowLabel = remember {
        "${DeviceTelemetryHelper.formatClockVi(System.currentTimeMillis())} • ${
            DeviceTelemetryHelper.formatDateDisplayVi(System.currentTimeMillis())
        }"
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Check-in kỷ niệm") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("checkin_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
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
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // CameraX live viewfinder card
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasCameraPermission) {
                            val previewView = remember {
                                PreviewView(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            }

                            DisposableEffect(lensFacing, flashMode) {
                                val providerFuture = ProcessCameraProvider.getInstance(context)
                                val executor = ContextCompat.getMainExecutor(context)
                                val runnable = Runnable {
                                    try {
                                        val cameraProvider = providerFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .setFlashMode(flashMode)
                                            .build()
                                        val selector = CameraSelector.Builder()
                                            .requireLensFacing(lensFacing)
                                            .build()

                                        cameraProvider.unbindAll()
                                        val camera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            selector,
                                            preview,
                                            capture
                                        )
                                        boundCamera = camera
                                        imageCapture = capture
                                        val maxZ = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f
                                        maxZoomRatio = maxZ.coerceIn(1.5f, 8f)
                                        zoomRatio = 1f
                                    } catch (e: Exception) {
                                        Log.e("CameraCheckIn", "CameraX bind error", e)
                                    }
                                }
                                providerFuture.addListener(runnable, executor)
                                onDispose {
                                    try {
                                        providerFuture.get().unbindAll()
                                    } catch (_: Exception) {
                                    }
                                }
                            }

                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top overlay controls: Flash & Switch Camera
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .align(Alignment.TopCenter),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "${capturedPhotos.size}/4 ảnh",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            flashMode = when (flashMode) {
                                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                                else -> ImageCapture.FLASH_MODE_AUTO
                                            }
                                            imageCapture?.flashMode = flashMode
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.55f),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("camera_flash_button")
                                    ) {
                                        Icon(
                                            imageVector = when (flashMode) {
                                                ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                                                ImageCapture.FLASH_MODE_OFF -> Icons.Rounded.FlashOff
                                                else -> Icons.Rounded.FlashAuto
                                            },
                                            contentDescription = "Đèn flash"
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = {
                                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                                CameraSelector.LENS_FACING_FRONT
                                            } else {
                                                CameraSelector.LENS_FACING_BACK
                                            }
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.55f),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("camera_switch_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Cameraswitch,
                                            contentDescription = "Đổi camera trước/sau"
                                        )
                                    }
                                }
                            }

                            // Bottom zoom & shutter bar
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (maxZoomRatio > 1.2f) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1fx", zoomRatio),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Slider(
                                            value = zoomRatio,
                                            onValueChange = { z ->
                                                zoomRatio = z
                                                boundCamera?.cameraControl?.setZoomRatio(z)
                                            },
                                            valueRange = 1f..maxZoomRatio,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = capturedPhotos.size < 4 && !isProcessingPhoto,
                                        modifier = Modifier.testTag("checkin_pick_gallery_button")
                                    ) {
                                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Thư viện", color = Color.White)
                                    }

                                    IconButton(
                                        onClick = {
                                            val cap = imageCapture ?: return@IconButton
                                            if (capturedPhotos.size >= 4 || isProcessingPhoto) return@IconButton
                                            isProcessingPhoto = true
                                            val tempFile = File(context.cacheDir, "checkin_${System.currentTimeMillis()}.jpg")
                                            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                                            cap.takePicture(
                                                outputOptions,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        scope.launch {
                                                            val encoded = PhotoCompressionHelper.compressFileToBase64(tempFile)
                                                            tempFile.delete()
                                                            if (encoded != null) {
                                                                capturedPhotos.add(encoded)
                                                            }
                                                            isProcessingPhoto = false
                                                        }
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        Log.e("CameraCheckIn", "Capture failed", exception)
                                                        tempFile.delete()
                                                        isProcessingPhoto = false
                                                    }
                                                }
                                            )
                                        },
                                        enabled = capturedPhotos.size < 4 && !isProcessingPhoto,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            .testTag("camera_shutter_button")
                                    ) {
                                        if (isProcessingPhoto) {
                                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoCamera,
                                                contentDescription = "Chụp ảnh check-in",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(84.dp))
                                }
                            }
                        } else {
                            // No camera permission view with direct grant + Photo Picker fallback
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Cần quyền Camera để chụp ảnh check-in trực tiếp",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Bạn cũng có thể chọn ảnh có sẵn từ thư viện máy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                        modifier = Modifier.testTag("request_camera_permission_button")
                                    ) {
                                        Text("Cấp quyền Camera")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    ) {
                                        Text("Chọn ảnh")
                                    }
                                }
                            }
                        }
                    }
                }

                // Captured thumbnail strip with retake/remove
                if (capturedPhotos.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Ảnh đã chọn (${capturedPhotos.size}/4) • Nhấn giữ hoặc bấm X để chụp lại",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(capturedPhotos) { index, b64 ->
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { previewPhotoIndex = index }
                                ) {
                                    Base64PhotoImage(
                                        base64 = b64,
                                        contentDescription = "Ảnh check-in ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { capturedPhotos.removeAt(index) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Xóa ảnh",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Metadata card: Time, GPS coords, Place Name, Note, Album scope, Trip association
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = nowLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val coordText = myLocation?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let {
                                    String.format(
                                        java.util.Locale.US,
                                        "GPS: %.5f, %.5f (±%.0fm)",
                                        it.latitude,
                                        it.longitude,
                                        it.accuracyMeters
                                    )
                                } ?: "Đang định vị GPS hiện tại..."
                                Text(
                                    text = coordText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = placeName,
                            onValueChange = { placeName = it.take(140) },
                            label = { Text("Tên địa điểm check-in *") },
                            placeholder = { Text("VD: Quán cà phê quen, Hồ Tây, Sân bay...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkin_place_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(500) },
                            label = { Text("Ghi chú kỷ niệm") },
                            placeholder = { Text("Khoảnh khắc đáng nhớ hôm nay...") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkin_note_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Text(
                            text = "Phạm vi lưu ảnh & check-in",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AlbumScope.entries.forEach { scopeOpt ->
                                FilterChip(
                                    selected = albumScope == scopeOpt,
                                    onClick = { albumScope = scopeOpt },
                                    label = { Text(scopeOpt.labelVi) }
                                )
                            }
                        }

                        if (trips.isNotEmpty()) {
                            Text(
                                text = "Gắn vào chuyến đi (tuỳ chọn)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedTripId == null,
                                    onClick = { selectedTripId = null },
                                    label = { Text("Không gắn chuyến đi") }
                                )
                                trips.forEach { trip ->
                                    FilterChip(
                                        selected = selectedTripId == trip.id,
                                        onClick = { selectedTripId = trip.id },
                                        label = { Text(trip.title) }
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val lat = myLocation?.latitude?.takeIf { it != 0.0 } ?: 10.7769
                        val lng = myLocation?.longitude?.takeIf { it != 0.0 } ?: 106.7009
                        viewModel.submitCheckIn(
                            placeName = placeName,
                            latitude = lat,
                            longitude = lng,
                            note = note,
                            albumScope = albumScope,
                            tripId = selectedTripId,
                            encodedPhotos = capturedPhotos.toList(),
                            onSuccess = onBack
                        )
                    },
                    enabled = placeName.isNotBlank() && !isBusy && !isProcessingPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("checkin_submit_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lưu check-in kỷ niệm")
                    }
                }
            }
        }
    }

    previewPhotoIndex?.let { idx ->
        val photoB64 = capturedPhotos.getOrNull(idx)
        if (photoB64 != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { previewPhotoIndex = null }) {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column {
                        Base64PhotoImage(
                            base64 = photoB64,
                            contentDescription = "Xem trước ảnh check-in",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = {
                                capturedPhotos.removeAt(idx)
                                previewPhotoIndex = null
                            }) {
                                Text("Chụp lại / Xóa")
                            }
                            Button(onClick = { previewPhotoIndex = null }) {
                                Text("Đóng")
                            }
                        }
                    }
                }
            }
        }
    }
}
