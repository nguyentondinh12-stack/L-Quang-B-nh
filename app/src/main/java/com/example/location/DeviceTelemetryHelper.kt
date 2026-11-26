package com.example.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import com.example.data.model.ChargingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class BatterySnapshot(
    val percent: Int,
    val chargingState: ChargingState
)

object DeviceTelemetryHelper {

    fun readBatterySnapshot(context: Context): BatterySnapshot {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val percent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_FULL -> ChargingState.FULL
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargingState.CHARGING
            else -> ChargingState.DISCHARGING
        }
        return BatterySnapshot(percent = percent, chargingState = charging)
    }

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun distanceMetersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun formatDistanceVi(meters: Float): String = when {
        meters < 1000f -> "${meters.roundToInt()} m"
        meters < 10_000f -> String.format(Locale.US, "%.1f km", meters / 1000f)
        else -> "${(meters / 1000f).roundToInt()} km"
    }

    fun formatDurationMinutesVi(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours > 0 -> "${hours}g ${mins}p"
            mins > 0 -> "$mins phút"
            else -> "Vừa mới"
        }
    }

    fun formatRelativeTimeVi(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return "Chưa có dữ liệu"
        val diff = (nowMs - timestampMs).coerceAtLeast(0L)
        return when {
            diff < 20_000L -> "Vừa xong"
            diff < 60_000L -> "${diff / 1000L} giây trước"
            diff < 3_600_000L -> "${diff / 60_000L} phút trước"
            diff < 86_400_000L -> "${diff / 3_600_000L} giờ trước"
            else -> SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")).format(Date(timestampMs))
        }
    }

    fun formatClockVi(timestampMs: Long): String =
        SimpleDateFormat("HH:mm", Locale("vi", "VN")).format(Date(timestampMs))

    fun formatDateKey(timestampMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))

    fun formatDateDisplayVi(timestampMs: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date(timestampMs))

    @Suppress("DEPRECATION")
    suspend fun reverseGeocodeAddress(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null
                val geocoder = Geocoder(context, Locale("vi", "VN"))
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1)
                } else {
                    geocoder.getFromLocation(lat, lng, 1)
                }
                val addr = addresses?.firstOrNull() ?: return@withContext null
                listOfNotNull(
                    addr.thoroughfare ?: addr.featureName,
                    addr.subLocality,
                    addr.subAdminArea ?: addr.locality,
                    addr.adminArea
                ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

    fun openGoogleMapsDirections(context: Context, lat: Double, lng: Double, label: String) {
        val navUri = Uri.parse("google.navigation:q=$lat,$lng")
        val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
        val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (navIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(navIntent)
            } else {
                context.startActivity(fallbackIntent)
            }
        } catch (_: Exception) {
            context.startActivity(fallbackIntent)
        }
    }

    fun openDialer(context: Context, phoneNumber: String?) {
        val uri = if (!phoneNumber.isNullOrBlank()) Uri.parse("tel:$phoneNumber") else Uri.parse("tel:")
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSms(context: Context, phoneNumber: String?) {
        val uri = if (!phoneNumber.isNullOrBlank()) Uri.parse("smsto:$phoneNumber") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
