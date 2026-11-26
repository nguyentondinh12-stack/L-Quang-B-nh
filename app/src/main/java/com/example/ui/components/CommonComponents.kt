package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ChargingState
import com.example.data.model.ConnectionState
import com.example.media.PhotoCompressionHelper
import com.example.ui.theme.StatusCharging
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning

@Composable
fun UserAvatar(
    name: String,
    photoUrl: String?,
    accentColor: Color,
    size: Dp = 44.dp,
    onlineDot: Boolean? = null
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.16f))
                .border(2.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Ảnh đại diện $name",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (onlineDot != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (onlineDot) StatusOnline else StatusOffline)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun BatteryBadge(
    percent: Int,
    chargingState: ChargingState,
    modifier: Modifier = Modifier
) {
    val isCritical = percent <= 10 && chargingState == ChargingState.DISCHARGING
    val isLow = percent <= 20 && chargingState == ChargingState.DISCHARGING
    val color = when {
        chargingState == ChargingState.CHARGING -> StatusCharging
        chargingState == ChargingState.FULL -> StatusOnline
        isCritical -> StatusOffline
        isLow -> StatusWarning
        else -> StatusOnline
    }
    val icon = when {
        chargingState == ChargingState.CHARGING -> Icons.Rounded.BatteryChargingFull
        chargingState == ChargingState.FULL -> Icons.Rounded.BatteryFull
        isLow -> Icons.Rounded.BatteryAlert
        else -> Icons.Rounded.BatteryStd
    }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Pin $percent%",
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ConnectionStatusPill(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, icon, label) = when (state) {
        ConnectionState.ONLINE -> Triple(StatusOnline, Icons.Rounded.CloudDone, "Online")
        ConnectionState.SYNCING -> Triple(StatusWarning, Icons.Rounded.Sync, "Đang đồng bộ")
        ConnectionState.DISCONNECTED -> Triple(StatusOffline, Icons.Rounded.CloudOff, "Mất kết nối")
        ConnectionState.OFFLINE -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.CloudOff, "Offline")
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun Base64PhotoImage(
    base64: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap: Bitmap? = remember(base64) {
        PhotoCompressionHelper.decodeBase64Bitmap(base64)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Không có ảnh", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
