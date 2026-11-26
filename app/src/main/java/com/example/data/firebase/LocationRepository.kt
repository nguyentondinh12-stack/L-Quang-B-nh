package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.DeviceStatus
import com.example.data.model.ImportantPlace
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryEntry
import com.example.data.model.PlaceCategory
import com.example.data.model.SharingMode
import com.example.data.model.SharingPermission
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Người dùng chưa đăng nhập Google.")

    fun observeMyLocation(): Flow<LiveLocation?> = flow {
        val uid = requireUserId()
        val path = "locations/$uid"
        emitAll(
            db.collection("locations").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(LiveLocation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.GET, path, uid)
                }
        )
    }

    fun observePartnerLocation(connectionId: String): Flow<LiveLocation?> = flow {
        val uid = requireUserId()
        val path = "locations"
        emitAll(
            db.collection(path)
                .whereEqualTo("authorizedPartnerUid", uid)
                .whereEqualTo("connectionId", connectionId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(LiveLocation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .firstOrNull { it.userId != uid }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeMyDeviceStatus(): Flow<DeviceStatus?> = flow {
        val uid = requireUserId()
        val path = "device_status/$uid"
        emitAll(
            db.collection("device_status").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(DeviceStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.GET, path, uid)
                }
        )
    }

    fun observePartnerDeviceStatus(connectionId: String): Flow<DeviceStatus?> = flow {
        val uid = requireUserId()
        val path = "device_status"
        emitAll(
            db.collection(path)
                .whereEqualTo("authorizedPartnerUid", uid)
                .whereEqualTo("connectionId", connectionId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DeviceStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .firstOrNull { it.userId != uid }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeMySharingPermission(): Flow<SharingPermission?> = flow {
        val uid = requireUserId()
        val path = "sharing_permissions/$uid"
        emitAll(
            db.collection("sharing_permissions").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(SharingPermission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.GET, path, uid)
                }
        )
    }

    fun observePartnerSharingPermission(connectionId: String): Flow<SharingPermission?> = flow {
        val uid = requireUserId()
        val path = "sharing_permissions"
        emitAll(
            db.collection(path)
                .whereEqualTo("authorizedPartnerUid", uid)
                .whereEqualTo("connectionId", connectionId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SharingPermission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .firstOrNull { it.userId != uid }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observePlaces(connectionId: String): Flow<List<ImportantPlace>> = flow {
        val uid = requireUserId()
        val path = "places"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ImportantPlace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.connectionId == connectionId }
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeLocationHistory(connectionId: String, dateKey: String): Flow<List<LocationHistoryEntry>> = flow {
        val uid = requireUserId()
        val path = "location_history"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(LocationHistoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.connectionId == connectionId && it.dateKey == dateKey }
                        .sortedBy { it.recordedAtMs }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    suspend fun ensureDefaultSharingPermission(connectionId: String?, partnerUid: String?): Result<SharingPermission> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val docRef = db.collection("sharing_permissions").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "connectionId" to connectionId,
                    "authorizedPartnerUid" to partnerUid,
                    "sharingMode" to SharingMode.CONTINUOUS.name,
                    "gpsPowerMode" to "BALANCED",
                    "updateIntervalSec" to 12L,
                    "shareLocation" to true,
                    "shareBattery" to true,
                    "shareMovement" to true,
                    "shareActivities" to true,
                    "shareHistory" to true,
                    "historyRetentionDays" to 30L,
                    "notifyArrival" to true,
                    "notifyDeparture" to true,
                    "notifyCheckin" to true,
                    "notifyLowBattery" to true,
                    "notifyOffline" to true,
                    "notifyOnline" to true,
                    "notifySharingChange" to true,
                    "notifyActivityNote" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
                Result.success(
                    docRef.get().await().toObject(SharingPermission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: SharingPermission(userId = uid, connectionId = connectionId, authorizedPartnerUid = partnerUid)
                )
            } else {
                val existing = snap.toObject(SharingPermission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)!!
                val desiredPartner = if (existing.shareLocation && existing.parsedSharingMode != SharingMode.OFF) partnerUid else null
                if (existing.connectionId != connectionId || existing.authorizedPartnerUid != desiredPartner) {
                    docRef.update(
                        mapOf(
                            "connectionId" to (connectionId ?: FieldValue.delete()),
                            "authorizedPartnerUid" to (desiredPartner ?: FieldValue.delete()),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                Result.success(existing)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "sharing_permissions/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateSharingPermission(
        permission: SharingPermission,
        connectionId: String?,
        partnerUid: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val path = "sharing_permissions/$uid"
        return try {
            val allowPartnerRead = connectionId != null && partnerUid != null &&
                permission.shareLocation && permission.parsedSharingMode != SharingMode.OFF
            val resolvedPartner = if (allowPartnerRead) partnerUid else null
            val docRef = db.collection("sharing_permissions").document(uid)
            val snap = docRef.get().await()
            val payload = mapOf(
                "connectionId" to (connectionId ?: FieldValue.delete()),
                "authorizedPartnerUid" to (resolvedPartner ?: FieldValue.delete()),
                "sharingMode" to permission.sharingMode,
                "sharingExpiresAtMs" to (permission.sharingExpiresAtMs ?: FieldValue.delete()),
                "gpsPowerMode" to permission.gpsPowerMode,
                "updateIntervalSec" to permission.updateIntervalSec.coerceIn(3L, 600L),
                "shareLocation" to permission.shareLocation,
                "shareBattery" to permission.shareBattery,
                "shareMovement" to permission.shareMovement,
                "shareActivities" to permission.shareActivities,
                "shareHistory" to permission.shareHistory,
                "historyRetentionDays" to permission.historyRetentionDays.coerceIn(1L, 365L),
                "notifyArrival" to permission.notifyArrival,
                "notifyDeparture" to permission.notifyDeparture,
                "notifyCheckin" to permission.notifyCheckin,
                "notifyLowBattery" to permission.notifyLowBattery,
                "notifyOffline" to permission.notifyOffline,
                "notifyOnline" to permission.notifyOnline,
                "notifySharingChange" to permission.notifySharingChange,
                "notifyActivityNote" to permission.notifyActivityNote,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (snap.exists()) {
                docRef.update(payload).await()
            } else {
                val createMap = payload.filterValues { it != FieldValue.delete() } + mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createMap).await()
            }

            // Sync partner authorization on locations & device_status immediately
            val locRef = db.collection("locations").document(uid)
            if (locRef.get().await().exists()) {
                val locUpdates = mutableMapOf<String, Any>(
                    "connectionId" to (connectionId ?: FieldValue.delete()),
                    "authorizedPartnerUid" to (resolvedPartner ?: FieldValue.delete()),
                    "sharingMode" to permission.sharingMode,
                    "sharingExpiresAtMs" to (permission.sharingExpiresAtMs ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                locRef.update(locUpdates).await()
            }
            val devRef = db.collection("device_status").document(uid)
            if (devRef.get().await().exists()) {
                val devPartner = if (connectionId != null && partnerUid != null && permission.shareBattery) partnerUid else null
                devRef.update(
                    mapOf(
                        "connectionId" to (connectionId ?: FieldValue.delete()),
                        "authorizedPartnerUid" to (devPartner ?: FieldValue.delete()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertLiveLocation(location: LiveLocation): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val docRef = db.collection("locations").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "connectionId" to location.connectionId,
                    "authorizedPartnerUid" to location.authorizedPartnerUid,
                    "latitude" to location.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to location.longitude.coerceIn(-180.0, 180.0),
                    "accuracyMeters" to location.accuracyMeters.coerceIn(0.0, 50000.0),
                    "speedMps" to location.speedMps.coerceIn(0.0, 500.0),
                    "bearing" to location.bearing.coerceIn(0.0, 360.0),
                    "moving" to location.moving,
                    "gpsInterrupted" to location.gpsInterrupted,
                    "sharingMode" to location.sharingMode,
                    "sharingExpiresAtMs" to location.sharingExpiresAtMs,
                    "address" to location.address?.take(300),
                    "currentPlaceName" to location.currentPlaceName?.take(120),
                    "arrivedAtPlaceTimeMs" to location.arrivedAtPlaceTimeMs,
                    "clientUpdatedAtMs" to location.clientUpdatedAtMs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "connectionId" to (location.connectionId ?: FieldValue.delete()),
                    "authorizedPartnerUid" to (location.authorizedPartnerUid ?: FieldValue.delete()),
                    "latitude" to location.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to location.longitude.coerceIn(-180.0, 180.0),
                    "accuracyMeters" to location.accuracyMeters.coerceIn(0.0, 50000.0),
                    "speedMps" to location.speedMps.coerceIn(0.0, 500.0),
                    "bearing" to location.bearing.coerceIn(0.0, 360.0),
                    "moving" to location.moving,
                    "gpsInterrupted" to location.gpsInterrupted,
                    "sharingMode" to location.sharingMode,
                    "sharingExpiresAtMs" to (location.sharingExpiresAtMs ?: FieldValue.delete()),
                    "address" to (location.address?.take(300) ?: FieldValue.delete()),
                    "currentPlaceName" to (location.currentPlaceName?.take(120) ?: FieldValue.delete()),
                    "arrivedAtPlaceTimeMs" to (location.arrivedAtPlaceTimeMs ?: FieldValue.delete()),
                    "clientUpdatedAtMs" to location.clientUpdatedAtMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertDeviceStatus(status: DeviceStatus): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val docRef = db.collection("device_status").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "connectionId" to status.connectionId,
                    "authorizedPartnerUid" to status.authorizedPartnerUid,
                    "batteryPercent" to status.batteryPercent.coerceIn(0L, 100L),
                    "chargingState" to status.chargingState,
                    "connectionState" to status.connectionState,
                    "locationUpdating" to status.locationUpdating,
                    "locationInterrupted" to status.locationInterrupted,
                    "movementState" to status.movementState,
                    "estimatedSpeedKmh" to status.estimatedSpeedKmh.coerceIn(0.0, 1200.0),
                    "stationarySinceMs" to status.stationarySinceMs,
                    "movingSinceMs" to status.movingSinceMs,
                    "nearestPlaceName" to status.nearestPlaceName?.take(120),
                    "lastHeartbeatMs" to status.lastHeartbeatMs,
                    "lastBatteryUpdateMs" to status.lastBatteryUpdateMs,
                    "lastAppActiveMs" to status.lastAppActiveMs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "connectionId" to (status.connectionId ?: FieldValue.delete()),
                    "authorizedPartnerUid" to (status.authorizedPartnerUid ?: FieldValue.delete()),
                    "batteryPercent" to status.batteryPercent.coerceIn(0L, 100L),
                    "chargingState" to status.chargingState,
                    "connectionState" to status.connectionState,
                    "locationUpdating" to status.locationUpdating,
                    "locationInterrupted" to status.locationInterrupted,
                    "movementState" to status.movementState,
                    "estimatedSpeedKmh" to status.estimatedSpeedKmh.coerceIn(0.0, 1200.0),
                    "stationarySinceMs" to (status.stationarySinceMs ?: FieldValue.delete()),
                    "movingSinceMs" to (status.movingSinceMs ?: FieldValue.delete()),
                    "nearestPlaceName" to (status.nearestPlaceName?.take(120) ?: FieldValue.delete()),
                    "lastHeartbeatMs" to status.lastHeartbeatMs,
                    "lastBatteryUpdateMs" to status.lastBatteryUpdateMs,
                    "lastAppActiveMs" to status.lastAppActiveMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordLocationHistoryPoint(entry: LocationHistoryEntry): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val docRef = db.collection("location_history").document()
        return try {
            val payload = mapOf(
                "userId" to uid,
                "connectionId" to entry.connectionId,
                "memberUids" to entry.memberUids,
                "dateKey" to entry.dateKey,
                "latitude" to entry.latitude.coerceIn(-90.0, 90.0),
                "longitude" to entry.longitude.coerceIn(-180.0, 180.0),
                "accuracyMeters" to entry.accuracyMeters.coerceAtLeast(0.0),
                "speedMps" to entry.speedMps.coerceAtLeast(0.0),
                "address" to entry.address?.take(300),
                "stopPoint" to entry.stopPoint,
                "stopDurationMinutes" to entry.stopDurationMinutes.coerceIn(0L, 10080L),
                "recordedAtMs" to entry.recordedAtMs,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMyLocationHistory(dateKey: String? = null): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        return try {
            val docs = db.collection("location_history")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
                .documents
                .filter {
                    it.getString("userId") == uid &&
                        (dateKey == null || it.getString("dateKey") == dateKey)
                }
            for (doc in docs) {
                doc.reference.delete().await()
            }
            Result.success(docs.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "location_history")
            Result.failure(e)
        }
    }

    suspend fun savePlace(place: ImportantPlace): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val isCreate = place.id.isBlank()
        val docRef = if (isCreate) db.collection("places").document() else db.collection("places").document(place.id)
        return try {
            val cleanName = place.name.trim().take(100)
            require(cleanName.isNotEmpty()) { "Tên địa điểm không được để trống" }
            if (isCreate) {
                val payload = mapOf(
                    "userId" to uid,
                    "connectionId" to place.connectionId,
                    "memberUids" to place.memberUids,
                    "name" to cleanName,
                    "category" to place.category,
                    "address" to place.address?.take(300),
                    "latitude" to place.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to place.longitude.coerceIn(-180.0, 180.0),
                    "radiusMeters" to place.radiusMeters.coerceIn(50L, 5000L),
                    "notifyOnArrive" to place.notifyOnArrive,
                    "notifyOnLeave" to place.notifyOnLeave,
                    "notifyWhileInside" to place.notifyWhileInside,
                    "leaveWindowAlertEnabled" to place.leaveWindowAlertEnabled,
                    "leaveWindowStartHour" to place.leaveWindowStartHour.coerceIn(0L, 23L),
                    "leaveWindowEndHour" to place.leaveWindowEndHour.coerceIn(0L, 23L),
                    "notificationsEnabled" to place.notificationsEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "name" to cleanName,
                    "category" to place.category,
                    "address" to (place.address?.take(300) ?: FieldValue.delete()),
                    "latitude" to place.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to place.longitude.coerceIn(-180.0, 180.0),
                    "radiusMeters" to place.radiusMeters.coerceIn(50L, 5000L),
                    "notifyOnArrive" to place.notifyOnArrive,
                    "notifyOnLeave" to place.notifyOnLeave,
                    "notifyWhileInside" to place.notifyWhileInside,
                    "leaveWindowAlertEnabled" to place.leaveWindowAlertEnabled,
                    "leaveWindowStartHour" to place.leaveWindowStartHour.coerceIn(0L, 23L),
                    "leaveWindowEndHour" to place.leaveWindowEndHour.coerceIn(0L, 23L),
                    "notificationsEnabled" to place.notificationsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePlace(placeId: String): Result<Unit> {
        val path = "places/$placeId"
        return try {
            requireUserId()
            require(placeId.isNotBlank()) { "ID địa điểm không hợp lệ" }
            db.collection("places").document(placeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun markOfflineOnSignOut(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            val docRef = db.collection("device_status").document(uid)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "connectionState" to com.example.data.model.ConnectionState.OFFLINE.name,
                        "locationUpdating" to false,
                        "lastAppActiveMs" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unwrapAndHandleStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        val ex = fsError ?: (error as? Exception)
        if (ex != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(ex, operationType, path)
        }
        throw error
    }
}
