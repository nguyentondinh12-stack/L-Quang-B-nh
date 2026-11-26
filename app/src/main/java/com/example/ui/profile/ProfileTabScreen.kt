package com.example.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Connection
import com.example.data.model.GpsPowerMode
import com.example.data.model.SharingMode
import com.example.data.model.SharingPermission
import com.example.location.DeviceTelemetryHelper
import com.example.location.LocationTrackingService
import com.example.ui.components.SectionHeader
import com.example.ui.components.UserAvatar
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.TogetherViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileTabScreen(
    viewModel: TogetherViewModel,
    connection: Connection,
    onOpenHistory: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val mySharing by viewModel.mySharingPermission.collectAsStateWithLifecycle()

    val perm = mySharing ?: SharingPermission(userId = viewModel.currentUserId)
    val myName = myProfile?.displayName ?: "Bạn"
    val partnerName = connection.partnerName(viewModel.currentUserId)

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    var hasBgLocation by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBgLocation = granted
    }

    val fgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            LocationTrackingService.start(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        UserAvatar(
                            name = myName,
                            photoUrl = myProfile?.photoUrl,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            size = 58.dp,
                            onlineDot = true
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(myName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = myProfile?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = myProfile?.phoneNumber?.takeIf { it.isNotBlank() }
                                    ?: "Chưa thêm số điện thoại",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Đang kết nối riêng với $partnerName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Sửa hồ sơ")
                        }
                    }
                }
            }

            // Sharing Mode & GPS Power Mode
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
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
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Quyền & Chế độ chia sẻ vị trí", style = MaterialTheme.typography.titleMedium)
                        }

                        perm.sharingExpiresAtMs?.let { exp ->
                            if (perm.parsedSharingMode != SharingMode.PAUSED && perm.parsedSharingMode != SharingMode.OFF) {
                                Text(
                                    text = "Tự động dừng lúc: ${DeviceTelemetryHelper.formatClockVi(exp)} (${
                                        DeviceTelemetryHelper.formatDateDisplayVi(exp)
                                    })",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SharingMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = perm.parsedSharingMode == mode,
                                    onClick = {
                                        viewModel.setSharingMode(mode)
                                        if (mode == SharingMode.PAUSED || mode == SharingMode.OFF) {
                                            LocationTrackingService.stop(context)
                                        } else {
                                            val fine = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            val coarse = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (fine || coarse) {
                                                LocationTrackingService.start(context)
                                            } else {
                                                fgLocationLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    label = { Text(mode.labelVi) }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("Tần suất cập nhật & Pin GPS", style = MaterialTheme.typography.titleSmall)
                        }

                        GpsPowerMode.entries.forEach { gpsMode ->
                            Surface(
                                onClick = { viewModel.updateGpsPowerMode(gpsMode) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (perm.parsedGpsPowerMode == gpsMode) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(gpsMode.labelVi, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = gpsMode.descVi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${gpsMode.movingIntervalSec}s / ${gpsMode.stillIntervalSec}s",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        if (!hasBgLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            OutlinedButton(
                                onClick = {
                                    bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cấp quyền vị trí nền khi tắt màn hình")
                            }
                        } else {
                            Text(
                                text = "Quyền cập nhật vị trí nền (Foreground Service): Đã sẵn sàng",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusOnline
                            )
                        }
                    }
                }
            }

            // Granular Privacy Controls + History Retention
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionHeader(
                            title = "Kiểm soát dữ liệu chia sẻ",
                            subtitle = "Bạn quyết định thông tin nào được gửi sang người kia"
                        )

                        PrivacySwitchRow(
                            label = "Chia sẻ vị trí trực tiếp",
                            checked = perm.shareLocation,
                            onCheckedChange = {
                                viewModel.updateSharingSettings(perm.copy(shareLocation = it))
                                if (!it) LocationTrackingService.stop(context) else LocationTrackingService.start(context)
                            }
                        )
                        PrivacySwitchRow(
                            label = "Chia sẻ phần trăm pin & sạc",
                            checked = perm.shareBattery,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(shareBattery = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Chia sẻ trạng thái di chuyển",
                            checked = perm.shareMovement,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(shareMovement = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Chia sẻ dòng thời gian hoạt động",
                            checked = perm.shareActivities,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(shareActivities = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Lưu & chia sẻ lịch sử lộ trình",
                            checked = perm.shareHistory,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(shareHistory = it)) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text("Thời gian lưu lịch sử vị trí", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7L, 30L, 90L, 365L).forEach { days ->
                                FilterChip(
                                    selected = perm.historyRetentionDays == days,
                                    onClick = { viewModel.updateSharingSettings(perm.copy(historyRetentionDays = days)) },
                                    label = { Text("$days ngày") }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.fillMaxWidth().testTag("profile_open_history_button")
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Xem hoặc xóa lịch sử di chuyển")
                        }
                    }
                }
            }

            // Notification Preferences
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text("Cài đặt thông báo", style = MaterialTheme.typography.titleMedium)
                        }
                        PrivacySwitchRow(
                            label = "Khi người kia đến địa điểm",
                            checked = perm.notifyArrival,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifyArrival = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Khi người kia rời địa điểm",
                            checked = perm.notifyDeparture,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifyDeparture = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Khi có check-in kỷ niệm mới",
                            checked = perm.notifyCheckin,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifyCheckin = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Cảnh báo pin yếu (<20%, <10%)",
                            checked = perm.notifyLowBattery,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifyLowBattery = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Khi người kia offline / mất kết nối",
                            checked = perm.notifyOffline,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifyOffline = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Khi người kia trở lại online",
                            checked = perm.notifyOnline,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifyOnline = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Khi bật/tạm dừng chia sẻ vị trí",
                            checked = perm.notifySharingChange,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifySharingChange = it)) }
                        )
                        PrivacySwitchRow(
                            label = "Khi có ghi chú hoạt động mới",
                            checked = perm.notifyActivityNote,
                            onCheckedChange = { viewModel.updateSharingSettings(perm.copy(notifyActivityNote = it)) }
                        )
                    }
                }
            }

            // Privacy & Security Charter (Section XII)
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("Cam kết riêng tư & minh bạch", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "• Không theo dõi ẩn: Luôn hiển thị thông báo hệ thống khi đang chia sẻ GPS.\n" +
                                "• Không đọc tin nhắn, nhật ký cuộc gọi, lịch sử trình duyệt hay ứng dụng khác.\n" +
                                "• Tối đa 2 tài khoản trong kết nối riêng. Ngắt kết nối sẽ thu hồi quyền xem ngay lập tức.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Disconnect, Sign Out, Delete Account
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDisconnectDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("disconnect_partner_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarning),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ngắt kết nối với $partnerName")
                        }

                        OutlinedButton(
                            onClick = {
                                LocationTrackingService.stop(context)
                                onSignOut()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Đăng xuất tài khoản")
                        }

                        TextButton(
                            onClick = { showDeleteAccountDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("delete_account_button"),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Xóa tài khoản & toàn bộ dữ liệu cá nhân")
                        }
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(myProfile?.displayName ?: "") }
        var phoneInput by remember { mutableStateOf(myProfile?.phoneNumber ?: "") }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Cập nhật hồ sơ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(80) },
                        label = { Text("Tên hiển thị *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it.take(24) },
                        label = { Text("Số điện thoại (để gọi/nhắn nhanh)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(nameInput, phoneInput)
                        showEditProfileDialog = false
                    },
                    enabled = nameInput.isNotBlank()
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Ngắt kết nối với $partnerName?") },
            text = {
                Text("Quyền xem vị trí, trạng thái pin và các cập nhật thời gian thực giữa hai bạn sẽ bị thu hồi ngay lập tức.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        LocationTrackingService.stop(context)
                        viewModel.disconnectPartner()
                        showDisconnectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ngắt kết nối")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Xóa tài khoản vĩnh viễn?") },
            text = {
                Text("Hồ sơ, vị trí, trạng thái thiết bị của bạn sẽ bị xóa và kết nối hiện tại sẽ được ngắt. Hành động này không thể hoàn tác.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        LocationTrackingService.stop(context)
                        viewModel.deleteAccount {
                            showDeleteAccountDialog = false
                            onSignOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa tài khoản")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun PrivacySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
