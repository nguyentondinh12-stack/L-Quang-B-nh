package com.example.ui.activity

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ImportantPlace
import com.example.data.model.PlaceCategory
import com.example.ui.map.OsmMapView
import com.example.ui.viewmodel.TogetherViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlacesManagerScreen(
    viewModel: TogetherViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val places by viewModel.places.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    var editingPlace by remember { mutableStateOf<ImportantPlace?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Địa điểm quan trọng") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPlace = ImportantPlace(
                        name = "",
                        category = PlaceCategory.HOME.name,
                        latitude = myLocation?.latitude?.takeIf { it != 0.0 } ?: 10.7769,
                        longitude = myLocation?.longitude?.takeIf { it != 0.0 } ?: 106.7009,
                        address = myLocation?.address,
                        radiusMeters = 200L
                    )
                },
                modifier = Modifier.testTag("add_place_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Thêm địa điểm")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Thiết lập Nhà, Công ty, Trường học, Điểm hẹn với bán kính 100m, 200m, 500m hoặc tùy chỉnh để nhận thông báo khi đến/rời.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (places.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(36.dp))
                                Text("Chưa có địa điểm quan trọng nào", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Nhấn nút + để thêm Nhà, Công ty, Trường học hoặc Điểm hẹn chung của 2 người.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(places, key = { it.id }) { place ->
                        PlaceItemCard(
                            place = place,
                            onToggleNotifications = { enabled ->
                                viewModel.saveImportantPlace(place.copy(notificationsEnabled = enabled))
                            },
                            onEdit = { editingPlace = place },
                            onDelete = { viewModel.deleteImportantPlace(place.id) }
                        )
                    }
                }
            }
        }
    }

    editingPlace?.let { draft ->
        EditPlaceDialog(
            initial = draft,
            currentLat = myLocation?.latitude,
            currentLng = myLocation?.longitude,
            currentAddr = myLocation?.address,
            onSave = { saved ->
                viewModel.saveImportantPlace(saved)
                editingPlace = null
            },
            onDismiss = { editingPlace = null }
        )
    }
}

@Composable
private fun PlaceItemCard(
    place: ImportantPlace,
    onToggleNotifications: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (place.parsedCategory) {
        PlaceCategory.HOME -> Icons.Rounded.Home
        PlaceCategory.WORK -> Icons.Rounded.Work
        PlaceCategory.SCHOOL -> Icons.Rounded.School
        else -> Icons.Rounded.Place
    }
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${place.parsedCategory.labelVi} • Bán kính ${place.radiusMeters}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = place.notificationsEnabled,
                    onCheckedChange = onToggleNotifications
                )
            }
            val rulesSummary = buildList {
                if (place.notifyOnArrive) add("Khi đến")
                if (place.notifyOnLeave) add("Khi rời")
                if (place.notifyWhileInside) add("Khi ở trong")
                if (place.leaveWindowAlertEnabled) add("Rời trong ${place.leaveWindowStartHour}h–${place.leaveWindowEndHour}h")
            }.joinToString(" • ").ifBlank { "Không có cảnh báo" }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rulesSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Xóa địa điểm", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPlaceDialog(
    initial: ImportantPlace,
    currentLat: Double?,
    currentLng: Double?,
    currentAddr: String?,
    onSave: (ImportantPlace) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var category by remember { mutableStateOf(initial.parsedCategory) }
    var lat by remember { mutableStateOf(initial.latitude) }
    var lng by remember { mutableStateOf(initial.longitude) }
    var address by remember { mutableStateOf(initial.address ?: "") }
    var radius by remember { mutableFloatStateOf(initial.radiusMeters.toFloat()) }
    var notifyArrive by remember { mutableStateOf(initial.notifyOnArrive) }
    var notifyLeave by remember { mutableStateOf(initial.notifyOnLeave) }
    var notifyInside by remember { mutableStateOf(initial.notifyWhileInside) }
    var leaveWindow by remember { mutableStateOf(initial.leaveWindowAlertEnabled) }
    var startHour by remember { mutableIntStateOf(initial.leaveWindowStartHour.toInt()) }
    var endHour by remember { mutableIntStateOf(initial.leaveWindowEndHour.toInt()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Thêm địa điểm" else "Chỉnh sửa địa điểm") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text("Tên địa điểm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("place_name_input")
                )

                Text("Phân loại", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (name.isBlank() && cat != PlaceCategory.CUSTOM) {
                                    name = cat.labelVi
                                }
                            },
                            label = { Text(cat.labelVi) }
                        )
                    }
                }

                // Mini interactive map (long press to pick point)
                Text(
                    "Nhấn giữ trên bản đồ để chọn tọa độ (${String.format(Locale.US, "%.4f, %.4f", lat, lng)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    OsmMapView(
                        myLocation = null,
                        partnerLocation = null,
                        myName = "",
                        partnerName = "",
                        places = listOf(
                            initial.copy(
                                name = name.ifBlank { "Vị trí chọn" },
                                latitude = lat,
                                longitude = lng,
                                radiusMeters = radius.roundToInt().toLong()
                            )
                        ),
                        onMapLongClick = { newLat, newLng ->
                            lat = newLat
                            lng = newLng
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (currentLat != null && currentLng != null && (currentLat != 0.0 || currentLng != 0.0)) {
                    OutlinedButton(
                        onClick = {
                            lat = currentLat
                            lng = currentLng
                            if (!currentAddr.isNullOrBlank()) address = currentAddr
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dùng vị trí hiện tại của tôi")
                    }
                }

                Text("Bán kính: ${radius.roundToInt()} m", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 200, 500).forEach { preset ->
                        FilterChip(
                            selected = radius.roundToInt() == preset,
                            onClick = { radius = preset.toFloat() },
                            label = { Text("${preset}m") }
                        )
                    }
                }
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 50f..2000f
                )

                ToggleRow("Thông báo khi đến", notifyArrive) { notifyArrive = it }
                ToggleRow("Thông báo khi rời", notifyLeave) { notifyLeave = it }
                ToggleRow("Thông báo khi ở trong địa điểm", notifyInside) { notifyInside = it }
                ToggleRow("Cảnh báo rời trong khung giờ", leaveWindow) { leaveWindow = it }

                if (leaveWindow) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startHour.toString(),
                            onValueChange = { startHour = it.toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                            label = { Text("Từ giờ (0-23)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endHour.toString(),
                            onValueChange = { endHour = it.toIntOrNull()?.coerceIn(0, 23) ?: 23 },
                            label = { Text("Đến giờ (0-23)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            category = category.name,
                            address = address.trim().takeIf { it.isNotEmpty() },
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = radius.roundToInt().toLong(),
                            notifyOnArrive = notifyArrive,
                            notifyOnLeave = notifyLeave,
                            notifyWhileInside = notifyInside,
                            leaveWindowAlertEnabled = leaveWindow,
                            leaveWindowStartHour = startHour.toLong(),
                            leaveWindowEndHour = endHour.toLong(),
                            notificationsEnabled = true
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_place_button")
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
