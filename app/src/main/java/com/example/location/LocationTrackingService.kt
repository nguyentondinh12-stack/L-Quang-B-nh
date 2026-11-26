package com.example.location

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.firebase.LocationRepository
import com.example.data.firebase.PairingRepository
import com.example.data.firebase.SocialMemoryRepository
import com.example.data.model.ActivityEvent
import com.example.data.model.ActivityType
import com.example.data.model.ChargingState
import com.example.data.model.Connection
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceStatus
import com.example.data.model.GpsPowerMode
import com.example.data.model.ImportantPlace
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryEntry
import com.example.data.model.MovementState
import com.example.data.model.SharingMode
import com.example.data.model.SharingPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRepo: LocationRepository
    private lateinit var pairingRepo: PairingRepository
    private lateinit var socialRepo: SocialMemoryRepository

    private var activeConnection: Connection? = null
    private var myDisplayName: String = "Bạn"
    private var myPermission: SharingPermission = SharingPermission()
    private var places: List<ImportantPlace> = emptyList()

    private var currentIntervalSec: Int = 12
    private var currentPriority: Int = Priority.PRIORITY_BALANCED_POWER_ACCURACY

    private var lastLocation: Location? = null
    private var lastHistoryPoint: Location? = null
    private var lastHistoryTimeMs: Long = 0L
    private var lastAddressLookupLocation: Location? = null
    private var lastAddress: String? = null

    private var lastMovementState: MovementState = MovementState.UNKNOWN
    private var movingSinceMs: Long? = null
    private var stationarySinceMs: Long? = null

    private var insidePlaceIds = mutableSetOf<String>()
    private var currentPlaceName: String? = null
    private var arrivedAtPlaceTimeMs: Long? = null

    private var lastAlertedLowBatteryLevel: Int = 100
    private var lastChargingState: ChargingState? = null
    private var wasNetworkOffline: Boolean = false

    private var heartbeatJob: Job? = null
    private var locationCallback: LocationCallback? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            serviceScope.launch {
                pushTelemetrySnapshot(locationUpdating = myPermission.parsedSharingMode != SharingMode.PAUSED &&
                    myPermission.parsedSharingMode != SharingMode.OFF)
            }
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationRepo = LocationRepository(applicationContext)
        pairingRepo = PairingRepository(applicationContext)
        socialRepo = SocialMemoryRepository(applicationContext)

        NotificationHelper.createNotificationChannels(this)
        val initialNotif = NotificationHelper.buildLocationForegroundNotification(
            this,
            "Đang đồng bộ vị trí và trạng thái thiết bị"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.FOREGROUND_NOTIFICATION_ID, initialNotif)
        }

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registerNetworkObserver()
        observeStreams()
        startHeartbeatLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE_FROM_NOTIFICATION) {
            serviceScope.launch {
                val uid = Firebase.auth.currentUser?.uid ?: return@launch
                val updated = myPermission.copy(
                    userId = uid,
                    sharingMode = SharingMode.PAUSED.name,
                    sharingExpiresAtMs = null
                )
                locationRepo.updateSharingPermission(
                    updated,
                    activeConnection?.id,
                    activeConnection?.partnerUid(uid)
                )
                emitActivityAndNotify(
                    ActivityType.SHARING_PAUSED,
                    "$myDisplayName đã tạm dừng chia sẻ vị trí",
                    "Người dùng tạm dừng từ thanh thông báo",
                    shouldNotifyPartner = updated.notifySharingChange
                )
                stopSelf()
            }
            return START_NOT_STICKY
        }

        requestLocationUpdatesIfPermitted()
        return START_STICKY
    }

    private fun observeStreams() {
        val uid = Firebase.auth.currentUser?.uid ?: return

        serviceScope.launch {
            pairingRepo.observeMyProfile().collectLatest { profile ->
                if (profile != null && profile.displayName.isNotBlank()) {
                    myDisplayName = profile.displayName
                }
            }
        }

        serviceScope.launch {
            pairingRepo.observeActiveConnection().collectLatest { conn ->
                activeConnection = conn
                if (conn != null) {
                    launch {
                        locationRepo.observePlaces(conn.id).collectLatest { list ->
                            places = list
                        }
                    }
                } else {
                    places = emptyList()
                }
            }
        }

        serviceScope.launch {
            locationRepo.observeMySharingPermission().collectLatest { perm ->
                if (perm != null) {
                    myPermission = perm
                    updateLocationRequestParameters()
                    val mode = perm.parsedSharingMode
                    if (mode == SharingMode.OFF || !perm.shareLocation) {
                        stopLocationUpdates()
                        stopSelf()
                    } else {
                        val statusText = "${mode.labelVi} • ${perm.parsedGpsPowerMode.labelVi}"
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        nm.notify(
                            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                            NotificationHelper.buildLocationForegroundNotification(this@LocationTrackingService, statusText)
                        )
                    }
                }
            }
        }
    }

    private fun registerNetworkObserver() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (wasNetworkOffline) {
                    wasNetworkOffline = false
                    serviceScope.launch {
                        pushTelemetrySnapshot(locationUpdating = true, overrideConnState = ConnectionState.ONLINE)
                        emitActivityAndNotify(
                            ActivityType.BACK_ONLINE,
                            "$myDisplayName đã trở lại online",
                            "Kết nối mạng đã ổn định",
                            shouldNotifyPartner = myPermission.notifyOnline
                        )
                    }
                }
            }

            override fun onLost(network: Network) {
                wasNetworkOffline = true
                serviceScope.launch {
                    pushTelemetrySnapshot(locationUpdating = false, overrideConnState = ConnectionState.DISCONNECTED)
                }
            }
        }
        networkCallback = cb
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (true) {
                val nowMs = System.currentTimeMillis()
                val expiresAt = myPermission.sharingExpiresAtMs
                if (expiresAt != null && nowMs >= expiresAt &&
                    myPermission.parsedSharingMode != SharingMode.PAUSED &&
                    myPermission.parsedSharingMode != SharingMode.OFF
                ) {
                    val uid = Firebase.auth.currentUser?.uid
                    if (uid != null) {
                        val paused = myPermission.copy(
                            userId = uid,
                            sharingMode = SharingMode.PAUSED.name,
                            sharingExpiresAtMs = null
                        )
                        locationRepo.updateSharingPermission(
                            paused,
                            activeConnection?.id,
                            activeConnection?.partnerUid(uid)
                        )
                        emitActivityAndNotify(
                            ActivityType.SHARING_PAUSED,
                            "$myDisplayName đã hết thời gian chia sẻ",
                            "Chế độ chia sẻ có thời hạn đã tự động tạm dừng",
                            shouldNotifyPartner = myPermission.notifySharingChange
                        )
                    }
                }

                pushTelemetrySnapshot(
                    locationUpdating = myPermission.parsedSharingMode != SharingMode.PAUSED &&
                        myPermission.parsedSharingMode != SharingMode.OFF &&
                        myPermission.shareLocation
                )
                delay(30_000L)
            }
        }
    }

    private fun updateLocationRequestParameters() {
        val power = myPermission.parsedGpsPowerMode
        val isMovingNow = lastMovementState == MovementState.MOVING
        val targetInterval = if (isMovingNow) power.movingIntervalSec else power.stillIntervalSec
        val targetPriority = when (power) {
            GpsPowerMode.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
            GpsPowerMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            GpsPowerMode.BATTERY_SAVER -> Priority.PRIORITY_LOW_POWER
        }
        if (targetInterval != currentIntervalSec || targetPriority != currentPriority || locationCallback == null) {
            currentIntervalSec = targetInterval
            currentPriority = targetPriority
            requestLocationUpdatesIfPermitted()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestLocationUpdatesIfPermitted() {
        if (!hasLocationPermission()) return
        val mode = myPermission.parsedSharingMode
        if (mode == SharingMode.PAUSED || mode == SharingMode.OFF || !myPermission.shareLocation) {
            stopLocationUpdates()
            return
        }
        stopLocationUpdates()

        val request = LocationRequest.Builder(currentPriority, currentIntervalSec * 1000L)
            .setMinUpdateIntervalMillis((currentIntervalSec * 500L).coerceAtLeast(4000L))
            .setMaxUpdateDelayMillis(currentIntervalSec * 1500L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                serviceScope.launch {
                    handleNewLocation(loc)
                }
            }
        }
        locationCallback = cb
        try {
            fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing location permission: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private suspend fun handleNewLocation(loc: Location) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val conn = activeConnection
        val partnerUid = if (conn != null && myPermission.shareLocation &&
            myPermission.parsedSharingMode != SharingMode.OFF &&
            myPermission.parsedSharingMode != SharingMode.PAUSED
        ) conn.partnerUid(uid) else null

        val nowMs = System.currentTimeMillis()
        val accuracy = loc.accuracy.toDouble().coerceAtLeast(0.0)
        val hasGoodAccuracy = accuracy in 0.1..60.0

        // Reverse geocode only when moved > 80m or first fix
        val prevAddrLoc = lastAddressLookupLocation
        if (prevAddrLoc == null || prevAddrLoc.distanceTo(loc) > 80f) {
            lastAddress = DeviceTelemetryHelper.reverseGeocodeAddress(this, loc.latitude, loc.longitude)
                ?: lastAddress
            lastAddressLookupLocation = loc
        }

        // Determine movement state (strictly require accurate GPS!)
        val speedMps = if (loc.hasSpeed() && hasGoodAccuracy) loc.speed.toDouble().coerceAtLeast(0.0) else 0.0
        val newMovementState = when {
            !hasGoodAccuracy -> MovementState.UNKNOWN
            speedMps >= 1.1 || (lastLocation != null && lastLocation!!.distanceTo(loc) > 15f &&
                (nowMs - lastLocation!!.time) < 60_000L) -> MovementState.MOVING
            else -> MovementState.STILL
        }

        if (newMovementState != lastMovementState) {
            if (newMovementState == MovementState.MOVING) {
                movingSinceMs = nowMs
                stationarySinceMs = null
                if (myPermission.shareMovement && myPermission.shareActivities) {
                    emitActivityAndNotify(
                        ActivityType.MOVING,
                        "$myDisplayName bắt đầu di chuyển",
                        lastAddress ?: "Đang cập nhật tuyến đường",
                        lat = loc.latitude,
                        lng = loc.longitude,
                        shouldNotifyPartner = false
                    )
                }
            } else if (newMovementState == MovementState.STILL) {
                stationarySinceMs = nowMs
                movingSinceMs = null
            }
            lastMovementState = newMovementState
            updateLocationRequestParameters()
        }

        // Evaluate geofences for Important Places
        evaluateGeofences(loc, nowMs)

        val liveLoc = LiveLocation(
            userId = uid,
            connectionId = conn?.id,
            authorizedPartnerUid = partnerUid,
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = accuracy,
            speedMps = speedMps,
            bearing = if (loc.hasBearing()) loc.bearing.toDouble().coerceIn(0.0, 360.0) else 0.0,
            moving = newMovementState == MovementState.MOVING,
            gpsInterrupted = !hasGoodAccuracy && accuracy > 150.0,
            sharingMode = myPermission.sharingMode,
            sharingExpiresAtMs = myPermission.sharingExpiresAtMs,
            address = lastAddress,
            currentPlaceName = currentPlaceName,
            arrivedAtPlaceTimeMs = arrivedAtPlaceTimeMs,
            clientUpdatedAtMs = nowMs
        )
        locationRepo.upsertLiveLocation(liveLoc)

        // Record history point if sharing history & moved >= 25m or >= 2 min
        if (conn != null && myPermission.shareHistory && hasGoodAccuracy) {
            val prevHist = lastHistoryPoint
            val elapsed = nowMs - lastHistoryTimeMs
            val movedEnough = prevHist == null || prevHist.distanceTo(loc) >= 25f
            val timeElapsed = elapsed >= 120_000L
            if (movedEnough || timeElapsed) {
                val isStop = newMovementState == MovementState.STILL &&
                    stationarySinceMs != null && (nowMs - stationarySinceMs!!) >= 180_000L
                val stopMins = if (isStop && stationarySinceMs != null) {
                    ((nowMs - stationarySinceMs!!) / 60_000L).coerceAtLeast(1L)
                } else 0L

                locationRepo.recordLocationHistoryPoint(
                    LocationHistoryEntry(
                        userId = uid,
                        connectionId = conn.id,
                        memberUids = conn.memberUids,
                        dateKey = DeviceTelemetryHelper.formatDateKey(nowMs),
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = accuracy,
                        speedMps = speedMps,
                        address = lastAddress,
                        stopPoint = isStop,
                        stopDurationMinutes = stopMins,
                        recordedAtMs = nowMs
                    )
                )
                lastHistoryPoint = loc
                lastHistoryTimeMs = nowMs
            }
        }

        lastLocation = loc
        pushTelemetrySnapshot(locationUpdating = true, overrideNearestPlace = currentPlaceName)
    }

    private suspend fun evaluateGeofences(loc: Location, nowMs: Long) {
        if (places.isEmpty()) return
        val currentlyInside = mutableSetOf<String>()
        var matchedPlaceName: String? = null

        for (place in places) {
            val dist = DeviceTelemetryHelper.distanceMetersBetween(
                loc.latitude, loc.longitude,
                place.latitude, place.longitude
            )
            if (dist <= place.radiusMeters.toFloat()) {
                currentlyInside.add(place.id)
                if (matchedPlaceName == null) matchedPlaceName = place.name

                if (!insidePlaceIds.contains(place.id)) {
                    // Arrived at place!
                    arrivedAtPlaceTimeMs = nowMs
                    if (myPermission.shareActivities) {
                        emitActivityAndNotify(
                            ActivityType.ARRIVED_PLACE,
                            "$myDisplayName đã đến ${place.name}",
                            "Trong bán kính ${place.radiusMeters}m",
                            placeName = place.name,
                            lat = loc.latitude,
                            lng = loc.longitude,
                            shouldNotifyPartner = place.notificationsEnabled && place.notifyOnArrive && myPermission.notifyArrival
                        )
                    }
                }
            } else if (insidePlaceIds.contains(place.id)) {
                // Left place!
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val inLeaveWindow = !place.leaveWindowAlertEnabled ||
                    (hour in place.leaveWindowStartHour.toInt()..place.leaveWindowEndHour.toInt())
                if (myPermission.shareActivities) {
                    emitActivityAndNotify(
                        ActivityType.LEFT_PLACE,
                        "$myDisplayName đã rời ${place.name}",
                        if (place.leaveWindowAlertEnabled) "Rời khỏi trong khung giờ ${place.leaveWindowStartHour}h–${place.leaveWindowEndHour}h"
                        else "Rời khỏi khu vực ${place.name}",
                        placeName = place.name,
                        lat = loc.latitude,
                        lng = loc.longitude,
                        shouldNotifyPartner = place.notificationsEnabled && place.notifyOnLeave && inLeaveWindow && myPermission.notifyDeparture
                    )
                }
            }
        }

        if (matchedPlaceName == null) {
            arrivedAtPlaceTimeMs = null
        }
        currentPlaceName = matchedPlaceName
        insidePlaceIds = currentlyInside
    }

    private suspend fun pushTelemetrySnapshot(
        locationUpdating: Boolean,
        overrideConnState: ConnectionState? = null,
        overrideNearestPlace: String? = currentPlaceName
    ) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val conn = activeConnection
        val nowMs = System.currentTimeMillis()
        val battery = DeviceTelemetryHelper.readBatterySnapshot(this)
        val hasNet = DeviceTelemetryHelper.isNetworkConnected(this)
        val connState = overrideConnState ?: if (hasNet) ConnectionState.ONLINE else ConnectionState.OFFLINE

        val partnerUid = if (conn != null && myPermission.shareBattery) conn.partnerUid(uid) else null
        val speedKmh = (lastLocation?.speed?.toDouble()?.times(3.6) ?: 0.0).coerceAtLeast(0.0)

        val status = DeviceStatus(
            userId = uid,
            connectionId = conn?.id,
            authorizedPartnerUid = partnerUid,
            batteryPercent = battery.percent.toLong(),
            chargingState = battery.chargingState.name,
            connectionState = connState.name,
            locationUpdating = locationUpdating && hasLocationPermission(),
            locationInterrupted = !hasLocationPermission() || !hasNet,
            movementState = if (myPermission.shareMovement) lastMovementState.name else MovementState.UNKNOWN.name,
            estimatedSpeedKmh = if (myPermission.shareMovement) speedKmh else 0.0,
            stationarySinceMs = stationarySinceMs,
            movingSinceMs = movingSinceMs,
            nearestPlaceName = overrideNearestPlace,
            lastHeartbeatMs = nowMs,
            lastBatteryUpdateMs = nowMs,
            lastAppActiveMs = nowMs
        )
        locationRepo.upsertDeviceStatus(status)

        // Low battery & charging events
        if (myPermission.shareBattery && conn != null) {
            if (battery.chargingState == ChargingState.CHARGING && lastChargingState != ChargingState.CHARGING) {
                emitActivityAndNotify(
                    ActivityType.CHARGING,
                    "$myDisplayName đang sạc điện thoại (${battery.percent}%)",
                    "Pin đang được nạp",
                    shouldNotifyPartner = false
                )
            }
            if (battery.percent <= 10 && lastAlertedLowBatteryLevel > 10 && battery.chargingState == ChargingState.DISCHARGING) {
                lastAlertedLowBatteryLevel = 10
                emitActivityAndNotify(
                    ActivityType.LOW_BATTERY,
                    "$myDisplayName pin cực thấp (${battery.percent}%)",
                    "Pin dưới 10%, có thể sớm mất tín hiệu GPS",
                    shouldNotifyPartner = myPermission.notifyLowBattery
                )
            } else if (battery.percent <= 20 && lastAlertedLowBatteryLevel > 20 && battery.chargingState == ChargingState.DISCHARGING) {
                lastAlertedLowBatteryLevel = 20
                emitActivityAndNotify(
                    ActivityType.LOW_BATTERY,
                    "$myDisplayName pin còn ${battery.percent}%",
                    "Cảnh báo pin dưới 20%",
                    shouldNotifyPartner = myPermission.notifyLowBattery
                )
            } else if (battery.percent > 25) {
                lastAlertedLowBatteryLevel = 100
            }
            lastChargingState = battery.chargingState
        }
    }

    private suspend fun emitActivityAndNotify(
        type: ActivityType,
        title: String,
        description: String,
        placeName: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        shouldNotifyPartner: Boolean
    ) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val conn = activeConnection ?: return
        val nowMs = System.currentTimeMillis()
        if (myPermission.shareActivities) {
            socialRepo.recordActivity(
                ActivityEvent(
                    userId = uid,
                    actorName = myDisplayName,
                    connectionId = conn.id,
                    memberUids = conn.memberUids,
                    type = type.name,
                    title = title,
                    description = description,
                    placeName = placeName,
                    latitude = lat,
                    longitude = lng,
                    dateKey = DeviceTelemetryHelper.formatDateKey(nowMs),
                    eventTimeMs = nowMs
                )
            )
        }
        val partnerUid = conn.partnerUid(uid)
        if (shouldNotifyPartner && partnerUid != null) {
            socialRepo.sendPartnerNotification(
                recipientUid = partnerUid,
                connectionId = conn.id,
                memberUids = conn.memberUids,
                type = type.name,
                title = title,
                body = description,
                placeName = placeName
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatJob?.cancel()
        stopLocationUpdates()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        networkCallback?.let { cb ->
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationTrackingService"
        const val ACTION_PAUSE_FROM_NOTIFICATION = "com.example.action.PAUSE_LOCATION_SHARING"

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
