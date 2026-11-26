package com.example.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonPinCircle
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChargingState
import com.example.data.model.Connection
import com.example.data.model.ConnectionState
import com.example.data.model.MovementState
import com.example.data.model.SharingMode
import com.example.location.DeviceTelemetryHelper
import com.example.location.LocationTrackingService
import com.example.ui.components.BatteryBadge
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.UserAvatar
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.TogetherViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapTabScreen(
    viewModel: TogetherViewModel,
    connection: Connection,
    onOpenCheckIn: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val context = LocalContext.current
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val partnerLocation by viewModel.partnerLocation.collectAsStateWithLifecycle()
    val myStatus by viewModel.myDeviceStatus.collectAsStateWithLifecycle()
    val partnerStatus by viewModel.partnerDeviceStatus.collectAsStateWithLifecycle()
    val mySharing by viewModel.mySharingPermission.collectAsStateWithLifecycle()
    val partnerConnState by viewModel.partnerEffectiveConnection.collectAsStateWithLifecycle()
    val distanceMeters by viewModel.distanceMeters.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    val unreadCount = remember(notifications) { notifications.count { !it.readFlag } }
    val myName = myProfile?.displayName ?: "Bạn"
    val partnerName = connection.partnerName(viewModel.currentUserId)
    val partnerPhoto = connection.partnerPhoto(viewModel.currentUserId)
    val partnerPhone = connection.partnerPhone(viewModel.currentUserId)

    var focusRequest by remember { mutableStateOf(MapFocusRequest.NONE) }
    var focusTrigger by remember { mutableIntStateOf(0) }
    var detailsExpanded by remember { mutableStateOf(true) }
    var showSharingSheet by remember { mutableStateOf(false) }

    var hasForegroundLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasBackgroundLocation by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val fgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasForegroundLocation = granted
        if (granted) {
            LocationTrackingService.start(context)
        }
    }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(hasForegroundLocation, mySharing?.sharingMode) {
        val mode = mySharing?.parsedSharingMode ?: SharingMode.CONTINUOUS
        if (hasForegroundLocation && mode != SharingMode.PAUSED && mode != SharingMode.OFF) {
            LocationTrackingService.start(context)
        }
    }

    val nowMs = System.currentTimeMillis()
    val partnerLocAgeMs = partnerLocation?.let { nowMs - it.clientUpdatedAtMs } ?: Long.MAX_VALUE
    val isPartnerStaleOrOffline = partnerLocation == null ||
        partnerLocation?.gpsInterrupted == true ||
        partnerConnState == ConnectionState.OFFLINE ||
        partnerConnState == ConnectionState.DISCONNECTED ||
        partnerLocAgeMs > 180_000L

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed Live Dual Map
        OsmMapView(
            myLocation = myLocation,
            partnerLocation = partnerLocation,
            myName = myName,
            partnerName = partnerName,
            partnerStaleOrOffline = isPartnerStaleOrOffline,
            places = places,
            focusRequest = focusRequest,
            focusTrigger = focusTrigger,
            modifier = Modifier.fillMaxSize()
        )

        // Top Floating Header & Alerts
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        UserAvatar(
                            name = partnerName,
                            photoUrl = partnerPhoto,
                            accentColor = MaterialTheme.colorScheme.primary,
                            size = 40.dp,
                            onlineDot = partnerConnState == ConnectionState.ONLINE
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = partnerName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                ConnectionStatusPill(state = partnerConnState)
                            }
                            val distText = distanceMeters?.let {
                                "Cách bạn ${DeviceTelemetryHelper.formatDistanceVi(it)}"
                            } ?: "Đang chờ tọa độ hai bên"
                            Text(
                                text = distText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clickable { showSharingSheet = true }
                                .testTag("quick_sharing_pill")
                        ) {
                            Text(
                                text = (mySharing?.parsedSharingMode ?: SharingMode.CONTINUOUS).labelVi,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.size(40.dp).testTag("notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text("$unreadCount") }
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Notifications, contentDescription = "Thông báo")
                            }
                        }
                    }
                }
            }

            // Permission Prompts
            if (!hasForegroundLocation) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Cần cấp quyền vị trí để chia sẻ GPS trực tiếp với $partnerName.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val perms = buildList {
                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                fgLauncher.launch(perms)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("grant_location_button")
                        ) {
                            Text("Cấp quyền")
                        }
                    }
                }
            } else if (!hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cho phép vị trí nền (Luôn cho phép) để cập nhật khi tắt màn hình.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Bật nền")
                        }
                    }
                }
            }

            // Offline / Interrupted GPS Warning (never display stale location as live!)
            if (partnerLocation != null && isPartnerStaleOrOffline) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StatusWarning.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = "Cảnh báo mất kết nối",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val timeText = DeviceTelemetryHelper.formatRelativeTimeVi(partnerLocation!!.clientUpdatedAtMs)
                        Text(
                            text = "Mất kết nối hoặc GPS gián đoạn — Hiển thị vị trí cuối ($timeText), không phải trực tiếp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        }

        // Right-side Map Focus Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    focusRequest = MapFocusRequest.ME
                    focusTrigger++
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("focus_me_button")
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "Đưa bản đồ về vị trí tôi")
            }

            SmallFloatingActionButton(
                onClick = {
                    focusRequest = MapFocusRequest.PARTNER
                    focusTrigger++
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("focus_partner_button")
            ) {
                Icon(Icons.Rounded.PersonPinCircle, contentDescription = "Xem vị trí người kia")
            }

            SmallFloatingActionButton(
                onClick = {
                    focusRequest = MapFocusRequest.BOTH
                    focusTrigger++
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("focus_both_button")
            ) {
                Icon(Icons.Rounded.Group, contentDescription = "Xem toàn bộ bản đồ của 2 người")
            }
        }

        // Bottom Dual Telemetry & Quick Actions Sheet Card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { detailsExpanded = !detailsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = partnerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        partnerStatus?.let { st ->
                            BatteryBadge(
                                percent = st.batteryPercent.toInt(),
                                chargingState = st.parsedCharging
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val moveState = partnerStatus?.parsedMovement ?: MovementState.UNKNOWN
                        Text(
                            text = moveState.labelVi,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (moveState == MovementState.MOVING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (detailsExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = "Thu gọn hoặc mở rộng"
                        )
                    }
                }

                AnimatedVisibility(visible = detailsExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pLoc = partnerLocation
                        if (pLoc != null) {
                            val addressText = pLoc.address ?: String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                pLoc.latitude,
                                pLoc.longitude
                            )
                            Text(
                                text = addressText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            val speedKmh = (pLoc.speedMps * 3.6)
                            val placeStayText = pLoc.currentPlaceName?.let { place ->
                                val stay = pLoc.arrivedAtPlaceTimeMs?.let {
                                    " • Ở đây ${DeviceTelemetryHelper.formatDurationMinutesVi(nowMs - it)}"
                                } ?: ""
                                "$place$stay"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Cập nhật: ${DeviceTelemetryHelper.formatRelativeTimeVi(pLoc.clientUpdatedAtMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "GPS: ±${pLoc.accuracyMeters.toInt()}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (pLoc.moving) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.0f", speedKmh)} km/h",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            if (placeStayText != null) {
                                Text(
                                    text = "Địa điểm: $placeStayText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = "Chưa có dữ liệu vị trí từ $partnerName (hoặc đang tắt chia sẻ).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Compact My Telemetry Strip
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bạn ($myName): ${myLocation?.address ?: "Đang định vị..."}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            myStatus?.let { mine ->
                                BatteryBadge(
                                    percent = mine.batteryPercent.toInt(),
                                    chargingState = mine.parsedCharging
                                )
                            }
                        }

                        // Action Buttons Row
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    partnerLocation?.let {
                                        DeviceTelemetryHelper.openGoogleMapsDirections(context, it.latitude, it.longitude, partnerName)
                                    }
                                },
                                enabled = partnerLocation != null,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("directions_button")
                            ) {
                                Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dẫn đường")
                            }

                            OutlinedButton(
                                onClick = { DeviceTelemetryHelper.openDialer(context, partnerPhone) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("call_partner_button")
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Gọi")
                            }

                            OutlinedButton(
                                onClick = { DeviceTelemetryHelper.openSms(context, partnerPhone) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("sms_partner_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Nhắn tin")
                            }

                            OutlinedButton(
                                onClick = onOpenHistory,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("open_history_button")
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Lịch sử")
                            }

                            OutlinedButton(
                                onClick = onOpenPlaces,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("open_places_button")
                            ) {
                                Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Địa điểm")
                            }

                            Button(
                                onClick = onOpenCheckIn,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("quick_checkin_button")
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Check-in")
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Sharing Mode Sheet
    if (showSharingSheet) {
        ModalBottomSheet(onDismissRequest = { showSharingSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Quyền chia sẻ vị trí của bạn", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Bạn toàn quyền quyết định thời gian và chế độ chia sẻ với $partnerName.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                val activeMode = mySharing?.parsedSharingMode ?: SharingMode.CONTINUOUS
                SharingMode.entries.forEach { mode ->
                    val selected = mode == activeMode
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSharingMode(mode)
                                if (mode == SharingMode.PAUSED || mode == SharingMode.OFF) {
                                    LocationTrackingService.stop(context)
                                } else if (hasForegroundLocation) {
                                    LocationTrackingService.start(context)
                                }
                                showSharingSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.labelVi,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
