package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SharingMode(val labelVi: String, val durationMs: Long?) {
    CONTINUOUS("Chia sẻ liên tục", null),
    ONE_HOUR("Chia sẻ trong 1 giờ", 60L * 60L * 1000L),
    EIGHT_HOURS("Chia sẻ trong 8 giờ", 8L * 60L * 60L * 1000L),
    TWENTY_FOUR_HOURS("Chia sẻ trong 24 giờ", 24L * 60L * 60L * 1000L),
    PAUSED("Đang tạm dừng", null),
    OFF("Tắt hoàn toàn", null);

    companion object {
        fun fromRaw(raw: String?): SharingMode =
            entries.firstOrNull { it.name == raw } ?: CONTINUOUS
    }
}

enum class GpsPowerMode(val labelVi: String, val descVi: String, val movingIntervalSec: Int, val stillIntervalSec: Int) {
    HIGH_ACCURACY("Cập nhật nhanh", "5–10 giây khi di chuyển, độ chính xác cao", 6, 30),
    BALANCED("Tối ưu pin", "10–15 giây khi di chuyển, cân bằng pin", 12, 45),
    BATTERY_SAVER("Tiết kiệm pin", "25–30 giây khi di chuyển, ít hao pin", 25, 90);

    companion object {
        fun fromRaw(raw: String?): GpsPowerMode =
            entries.firstOrNull { it.name == raw } ?: BALANCED
    }
}

enum class ChargingState(val labelVi: String) {
    CHARGING("Đang sạc"),
    DISCHARGING("Không sạc"),
    FULL("Pin đầy");

    companion object {
        fun fromRaw(raw: String?): ChargingState =
            entries.firstOrNull { it.name == raw } ?: DISCHARGING
    }
}

enum class ConnectionState(val labelVi: String) {
    ONLINE("Đang online"),
    OFFLINE("Offline"),
    DISCONNECTED("Mất kết nối"),
    SYNCING("Đang đồng bộ");

    companion object {
        fun fromRaw(raw: String?): ConnectionState =
            entries.firstOrNull { it.name == raw } ?: OFFLINE
    }
}

enum class MovementState(val labelVi: String) {
    STILL("Đang đứng yên"),
    MOVING("Đang di chuyển"),
    UNKNOWN("Chưa đủ độ chính xác GPS");

    companion object {
        fun fromRaw(raw: String?): MovementState =
            entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

enum class ActivityType(val labelVi: String) {
    MOVING("Di chuyển"),
    ARRIVED_PLACE("Đến địa điểm"),
    LEFT_PLACE("Rời địa điểm"),
    CHECKIN("Check-in kỷ niệm"),
    SHARING_STARTED("Bắt đầu chia sẻ vị trí"),
    SHARING_PAUSED("Tạm dừng chia sẻ"),
    LOW_BATTERY("Pin thấp"),
    CHARGING("Đang sạc pin"),
    LOST_CONNECTION("Mất kết nối"),
    BACK_ONLINE("Trở lại online"),
    NOTE("Cập nhật cá nhân");

    companion object {
        fun fromRaw(raw: String?): ActivityType =
            entries.firstOrNull { it.name == raw } ?: NOTE
    }
}

enum class PlaceCategory(val labelVi: String) {
    HOME("Nhà"),
    WORK("Công ty"),
    SCHOOL("Trường học"),
    MEETING("Điểm hẹn"),
    CUSTOM("Địa điểm tùy chỉnh");

    companion object {
        fun fromRaw(raw: String?): PlaceCategory =
            entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

enum class AlbumScope(val labelVi: String) {
    SHARED("Album chung"),
    PERSONAL("Album cá nhân");

    companion object {
        fun fromRaw(raw: String?): AlbumScope =
            entries.firstOrNull { it.name == raw } ?: SHARED
    }
}

enum class AlbumType(val labelVi: String) {
    SHARED("Album chung"),
    PERSONAL("Album cá nhân"),
    PLACE("Theo địa điểm"),
    TRIP("Theo chuyến đi");

    companion object {
        fun fromRaw(raw: String?): AlbumType =
            entries.firstOrNull { it.name == raw } ?: SHARED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val phoneNumber: String? = null,
    val inviteCode: String = "",
    val connectionId: String? = null,
    val partnerId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PairingCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerPhotoUrl: String? = null,
    val ownerPhone: String? = null,
    val connectionId: String? = null,
    val partnerUid: String? = null,
    val partnerName: String? = null,
    val partnerPhotoUrl: String? = null,
    val status: String = "waiting",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Connection(
    @DocumentId val id: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val memberPhotos: Map<String, String> = emptyMap(),
    val memberPhones: Map<String, String> = emptyMap(),
    val inviteCode: String = "",
    val status: String = "active",
    val createdBy: String = "",
    val disconnectedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun partnerUid(myUid: String): String? = memberUids.firstOrNull { it != myUid }
    fun partnerName(myUid: String): String =
        partnerUid(myUid)?.let { memberNames[it] }?.takeIf { it.isNotBlank() } ?: "Người đồng hành"
    fun partnerPhoto(myUid: String): String? =
        partnerUid(myUid)?.let { memberPhotos[it] }?.takeIf { it.isNotBlank() }
    fun partnerPhone(myUid: String): String? =
        partnerUid(myUid)?.let { memberPhones[it] }?.takeIf { it.isNotBlank() }
}

@Keep
@IgnoreExtraProperties
data class LiveLocation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val connectionId: String? = null,
    val authorizedPartnerUid: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    val bearing: Double = 0.0,
    val moving: Boolean = false,
    val gpsInterrupted: Boolean = false,
    val sharingMode: String = SharingMode.CONTINUOUS.name,
    val sharingExpiresAtMs: Long? = null,
    val address: String? = null,
    val currentPlaceName: String? = null,
    val arrivedAtPlaceTimeMs: Long? = null,
    val clientUpdatedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedSharingMode: SharingMode get() = SharingMode.fromRaw(sharingMode)
}

@Keep
@IgnoreExtraProperties
data class DeviceStatus(
    @DocumentId val id: String = "",
    val userId: String = "",
    val connectionId: String? = null,
    val authorizedPartnerUid: String? = null,
    val batteryPercent: Long = 100L,
    val chargingState: String = ChargingState.DISCHARGING.name,
    val connectionState: String = ConnectionState.ONLINE.name,
    val locationUpdating: Boolean = false,
    val locationInterrupted: Boolean = false,
    val movementState: String = MovementState.UNKNOWN.name,
    val estimatedSpeedKmh: Double = 0.0,
    val stationarySinceMs: Long? = null,
    val movingSinceMs: Long? = null,
    val nearestPlaceName: String? = null,
    val lastHeartbeatMs: Long = 0L,
    val lastBatteryUpdateMs: Long = 0L,
    val lastAppActiveMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedCharging: ChargingState get() = ChargingState.fromRaw(chargingState)
    val parsedConnection: ConnectionState get() = ConnectionState.fromRaw(connectionState)
    val parsedMovement: MovementState get() = MovementState.fromRaw(movementState)
}

@Keep
@IgnoreExtraProperties
data class SharingPermission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val connectionId: String? = null,
    val authorizedPartnerUid: String? = null,
    val sharingMode: String = SharingMode.CONTINUOUS.name,
    val sharingExpiresAtMs: Long? = null,
    val gpsPowerMode: String = GpsPowerMode.BALANCED.name,
    val updateIntervalSec: Long = 12L,
    val shareLocation: Boolean = true,
    val shareBattery: Boolean = true,
    val shareMovement: Boolean = true,
    val shareActivities: Boolean = true,
    val shareHistory: Boolean = true,
    val historyRetentionDays: Long = 30L,
    val notifyArrival: Boolean = true,
    val notifyDeparture: Boolean = true,
    val notifyCheckin: Boolean = true,
    val notifyLowBattery: Boolean = true,
    val notifyOffline: Boolean = true,
    val notifyOnline: Boolean = true,
    val notifySharingChange: Boolean = true,
    val notifyActivityNote: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedSharingMode: SharingMode get() = SharingMode.fromRaw(sharingMode)
    val parsedGpsPowerMode: GpsPowerMode get() = GpsPowerMode.fromRaw(gpsPowerMode)
}

@Keep
@IgnoreExtraProperties
data class ActivityEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorName: String = "",
    val connectionId: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = ActivityType.NOTE.name,
    val title: String = "",
    val description: String = "",
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dateKey: String = "",
    val eventTimeMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: ActivityType get() = ActivityType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class LocationHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val connectionId: String = "",
    val memberUids: List<String> = emptyList(),
    val dateKey: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    val address: String? = null,
    val stopPoint: Boolean = false,
    val stopDurationMinutes: Long = 0L,
    val recordedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ImportantPlace(
    @DocumentId val id: String = "",
    val userId: String = "",
    val connectionId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val category: String = PlaceCategory.CUSTOM.name,
    val address: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Long = 200L,
    val notifyOnArrive: Boolean = true,
    val notifyOnLeave: Boolean = true,
    val notifyWhileInside: Boolean = false,
    val leaveWindowAlertEnabled: Boolean = false,
    val leaveWindowStartHour: Long = 7L,
    val leaveWindowEndHour: Long = 9L,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedCategory: PlaceCategory get() = PlaceCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class CheckIn(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val connectionId: String = "",
    val memberUids: List<String> = emptyList(),
    val placeName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val note: String? = null,
    val albumScope: String = AlbumScope.SHARED.name,
    val tripId: String? = null,
    val photoCount: Long = 0L,
    val primaryPhotoBase64: String? = null,
    val capturedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedScope: AlbumScope get() = AlbumScope.fromRaw(albumScope)
}

@Keep
@IgnoreExtraProperties
data class MemoryPhoto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val connectionId: String = "",
    val memberUids: List<String> = emptyList(),
    val checkinId: String = "",
    val tripId: String? = null,
    val albumType: String = AlbumType.SHARED.name,
    val placeName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val caption: String? = null,
    val imageBase64: String = "",
    val capturedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedAlbumType: AlbumType get() = AlbumType.fromRaw(albumType)
}

@Keep
@IgnoreExtraProperties
data class TripJournal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val connectionId: String = "",
    val memberUids: List<String> = emptyList(),
    val title: String = "",
    val destination: String = "",
    val startDateMs: Long = 0L,
    val endDateMs: Long = 0L,
    val notes: String? = null,
    val participantNames: List<String> = emptyList(),
    val coverPhotoBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val recipientUid: String = "",
    val connectionId: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val placeName: String? = null,
    val readFlag: Boolean = false,
    val createdAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
