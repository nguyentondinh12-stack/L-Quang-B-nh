package com.example.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActivityEvent
import com.example.data.model.ActivityType
import com.example.data.model.Connection
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceStatus
import com.example.data.model.MovementState
import com.example.location.DeviceTelemetryHelper
import com.example.ui.components.BatteryBadge
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.SectionHeader
import com.example.ui.theme.StatusCharging
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.TogetherViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTabScreen(
    viewModel: TogetherViewModel,
    connection: Connection,
    onOpenHistory: () -> Unit,
    onOpenPlaces: () -> Unit
) {
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val myStatus by viewModel.myDeviceStatus.collectAsStateWithLifecycle()
    val partnerStatus by viewModel.partnerDeviceStatus.collectAsStateWithLifecycle()
    val partnerConnState by viewModel.partnerEffectiveConnection.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()

    val myName = myProfile?.displayName ?: "Bạn"
    val partnerName = connection.partnerName(viewModel.currentUserId)

    val todayKey = remember { DeviceTelemetryHelper.formatDateKey(System.currentTimeMillis()) }
    val yesterdayKey = remember {
        DeviceTelemetryHelper.formatDateKey(System.currentTimeMillis() - 86_400_000L)
    }

    var noteInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeader(
                    title = "Trạng thái thiết bị của 2 người",
                    subtitle = "Pin, kết nối heartbeat và hoạt động di chuyển"
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeviceTelemetryCard(
                        name = "$myName (Bạn)",
                        status = myStatus,
                        effectiveConnection = myStatus?.parsedConnection ?: ConnectionState.ONLINE,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    DeviceTelemetryCard(
                        name = partnerName,
                        status = partnerStatus,
                        effectiveConnection = partnerConnState,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenPlaces,
                        modifier = Modifier.weight(1f).testTag("activity_places_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Địa điểm")
                    }
                    OutlinedButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f).testTag("activity_history_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lịch sử đi")
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it.take(200) },
                            placeholder = { Text("Gửi ghi chú cập nhật cho $partnerName...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("activity_note_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (noteInput.isNotBlank()) {
                                    viewModel.sendActivityNote(noteInput)
                                    noteInput = ""
                                }
                            },
                            enabled = noteInput.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_activity_note_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Gửi cập nhật",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Dòng thời gian hoạt động",
                    subtitle = "Chỉ ghi nhận sự kiện hệ thống hỗ trợ và được chia sẻ"
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                } else {
                                    "Chọn ngày"
                                }
                            )
                        }
                    )
                }
            }

            if (activities.isEmpty()) {
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
                            Icon(
                                imageVector = Icons.Rounded.DirectionsRun,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Chưa có hoạt động nào trong ngày $selectedDateKey",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Các sự kiện di chuyển, đến/rời địa điểm, check-in, pin thấp hoặc ghi chú sẽ tự động xuất hiện tại đây.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(activities, key = { it.id }) { event ->
                    ActivityTimelineItem(event = event)
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
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.selectDateKey(DeviceTelemetryHelper.formatDateKey(millis))
                    }
                    showDatePicker = false
                }) {
                    Text("Chọn")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Hủy")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DeviceTelemetryCard(
    name: String,
    status: DeviceStatus?,
    effectiveConnection: ConnectionState,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val nowMs = System.currentTimeMillis()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )

            ConnectionStatusPill(state = effectiveConnection)

            if (status != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BatteryBadge(
                        percent = status.batteryPercent.toInt(),
                        chargingState = status.parsedCharging
                    )
                    Text(
                        text = status.parsedCharging.labelVi,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status.batteryPercent <= 10 && status.parsedCharging.name == "DISCHARGING") {
                    Text(
                        text = "Cảnh báo: Pin dưới 10%!",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusOffline
                    )
                } else if (status.batteryPercent <= 20 && status.parsedCharging.name == "DISCHARGING") {
                    Text(
                        text = "Pin yếu dưới 20%",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning
                    )
                }

                val gpsText = when {
                    status.locationInterrupted -> "Vị trí bị gián đoạn"
                    status.locationUpdating -> "Vị trí đang cập nhật"
                    else -> "Tạm dừng cập nhật GPS"
                }
                Text(
                    text = gpsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.locationInterrupted) StatusWarning else MaterialTheme.colorScheme.onSurface
                )

                val moveLabel = when (status.parsedMovement) {
                    MovementState.MOVING ->
                        "Đang di chuyển (${String.format(Locale.US, "%.0f", status.estimatedSpeedKmh)} km/h)"
                    MovementState.STILL -> {
                        val dur = status.stationarySinceMs?.let {
                            " • ${DeviceTelemetryHelper.formatDurationMinutesVi(nowMs - it)}"
                        } ?: ""
                        "Đang đứng yên$dur"
                    }
                    MovementState.UNKNOWN -> "GPS chưa đủ chính xác"
                }
                Text(
                    text = moveLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                status.nearestPlaceName?.let { place ->
                    Text(
                        text = "Gần: $place",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = "Pin cập nhật: ${DeviceTelemetryHelper.formatRelativeTimeVi(status.lastBatteryUpdateMs, nowMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Đang chờ đồng bộ thiết bị...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActivityTimelineItem(event: ActivityEvent) {
    val (icon, tint) = activityStyle(event.parsedType)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = DeviceTelemetryHelper.formatClockVi(event.eventTimeMs),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!event.placeName.isNullOrBlank()) {
                    Text(
                        text = "Địa điểm: ${event.placeName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun activityStyle(type: ActivityType): Pair<ImageVector, androidx.compose.ui.graphics.Color> =
    when (type) {
        ActivityType.MOVING -> Icons.Rounded.DirectionsRun to MaterialTheme.colorScheme.primary
        ActivityType.ARRIVED_PLACE -> Icons.Rounded.LocationOn to StatusOnline
        ActivityType.LEFT_PLACE -> Icons.Rounded.LocationOff to StatusWarning
        ActivityType.CHECKIN -> Icons.Rounded.PhotoCamera to MaterialTheme.colorScheme.tertiary
        ActivityType.SHARING_STARTED -> Icons.Rounded.PlayCircle to StatusOnline
        ActivityType.SHARING_PAUSED -> Icons.Rounded.PauseCircle to StatusWarning
        ActivityType.LOW_BATTERY -> Icons.Rounded.BatteryAlert to StatusOffline
        ActivityType.CHARGING -> Icons.Rounded.BatteryChargingFull to StatusCharging
        ActivityType.LOST_CONNECTION -> Icons.Rounded.CloudOff to StatusOffline
        ActivityType.BACK_ONLINE -> Icons.Rounded.CloudDone to StatusOnline
        ActivityType.NOTE -> Icons.Rounded.EditNote to MaterialTheme.colorScheme.secondary
    }
