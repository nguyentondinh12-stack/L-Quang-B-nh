package com.example.ui.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Connection
import com.example.location.DeviceTelemetryHelper
import com.example.ui.map.MapFocusRequest
import com.example.ui.map.OsmMapView
import com.example.ui.viewmodel.TogetherViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationHistoryScreen(
    viewModel: TogetherViewModel,
    connection: Connection?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val history by viewModel.locationHistory.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val checkIns by viewModel.checkIns.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val partnerLocation by viewModel.partnerLocation.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()

    val todayKey = remember { DeviceTelemetryHelper.formatDateKey(System.currentTimeMillis()) }
    val yesterdayKey = remember {
        DeviceTelemetryHelper.formatDateKey(System.currentTimeMillis() - 86_400_000L)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dayCheckIns = remember(checkIns, selectedDateKey) {
        checkIns.filter { DeviceTelemetryHelper.formatDateKey(it.capturedAtMs) == selectedDateKey }
    }
    val stops = remember(history) { history.filter { it.stopPoint } }
    val visitedPlaces = remember(activities) {
        activities.mapNotNull { it.placeName }.distinct()
    }
    val totalDistanceMeters = remember(history) {
        var total = 0f
        history.groupBy { it.userId }.values.forEach { pts ->
            for (i in 1 until pts.size) {
                total += DeviceTelemetryHelper.distanceMetersBetween(
                    pts[i - 1].latitude, pts[i - 1].longitude,
                    pts[i].latitude, pts[i].longitude
                )
            }
        }
        total
    }
    val movingDurationMs = remember(history) {
        var total = 0L
        history.groupBy { it.userId }.values.forEach { pts ->
            for (i in 1 until pts.size) {
                val dt = pts[i].recordedAtMs - pts[i - 1].recordedAtMs
                if (dt in 1..600_000L && pts[i].speedMps >= 1.0) {
                    total += dt
                }
            }
        }
        total
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Lịch sử di chuyển") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_history_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Xóa lịch sử của tôi")
                    }
                }
            )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedDateKey == todayKey,
                            onClick = { viewModel.selectDateKey(todayKey) },
                            label = { Text("Hôm nay") }
                        )
                        FilterChip(
                            selected = selectedDateKey == yesterdayKey,
                            onClick = { viewModel.selectDateKey(yesterdayKey) },
                            label = { Text("Hôm qua") }
                        )
                        FilterChip(
                            selected = selectedDateKey != todayKey && selectedDateKey != yesterdayKey,
                            onClick = { showDatePicker = true },
                            leadingIcon = {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(
                                    if (selectedDateKey != todayKey && selectedDateKey != yesterdayKey) {
                                        selectedDateKey
                                    } else "Chọn ngày"
                                )
                            }
                        )
                    }
                }

                // Route Map
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(22.dp))
                    ) {
                        OsmMapView(
                            myLocation = myLocation,
                            partnerLocation = partnerLocation,
                            myName = myProfile?.displayName ?: "Bạn",
                            partnerName = connection?.partnerName(viewModel.currentUserId) ?: "Người kia",
                            places = places,
                            historyPoints = history,
                            checkIns = dayCheckIns,
                            focusRequest = MapFocusRequest.BOTH,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Metrics Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HistoryStatCard(
                            label = "Quãng đường",
                            value = DeviceTelemetryHelper.formatDistanceVi(totalDistanceMeters),
                            modifier = Modifier.weight(1f)
                        )
                        HistoryStatCard(
                            label = "Di chuyển",
                            value = DeviceTelemetryHelper.formatDurationMinutesVi(movingDurationMs),
                            modifier = Modifier.weight(1f)
                        )
                        HistoryStatCard(
                            label = "Điểm dừng",
                            value = "${stops.size}",
                            modifier = Modifier.weight(1f)
                        )
                        HistoryStatCard(
                            label = "Check-in",
                            value = "${dayCheckIns.size}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (visitedPlaces.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Địa điểm đã đến (${visitedPlaces.size})", style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    text = visitedPlaces.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Các điểm dừng & mốc tuyến đường (${history.size})", style = MaterialTheme.typography.titleMedium)
                }

                if (history.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(32.dp))
                                Text("Không có dữ liệu tuyến đường ngày $selectedDateKey", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                } else {
                    items(history.reversed(), key = { it.id }) { pt ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (pt.stopPoint) {
                                            "Điểm dừng (${pt.stopDurationMinutes} phút)"
                                        } else {
                                            "Di chuyển • ${String.format(Locale.US, "%.0f", pt.speedMps * 3.6)} km/h"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (pt.stopPoint) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = pt.address ?: String.format(Locale.US, "%.5f, %.5f", pt.latitude, pt.longitude),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = DeviceTelemetryHelper.formatClockVi(pt.recordedAtMs),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                if (dayCheckIns.isNotEmpty()) {
                    item {
                        Text("Check-in trong ngày (${dayCheckIns.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(dayCheckIns, key = { it.id }) { ck ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ck.authorName} tại ${ck.placeName}", style = MaterialTheme.typography.titleSmall)
                                    if (!ck.note.isNullOrBlank()) {
                                        Text(ck.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(DeviceTelemetryHelper.formatClockVi(ck.capturedAtMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.selectDateKey(DeviceTelemetryHelper.formatDateKey(it))
                    }
                    showDatePicker = false
                }) { Text("Chọn") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Hủy") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xóa lịch sử di chuyển của bạn") },
            text = { Text("Bạn muốn xóa lịch sử di chuyển ngày $selectedDateKey hay toàn bộ lịch sử của mình?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMyLocationHistory(selectedDateKey)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa ngày này")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteMyLocationHistory(null)
                        showDeleteConfirm = false
                    }) {
                        Text("Xóa tất cả", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Hủy")
                    }
                }
            }
        )
    }
}

@Composable
private fun HistoryStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
