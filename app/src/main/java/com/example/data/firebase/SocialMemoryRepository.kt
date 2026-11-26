package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ActivityEvent
import com.example.data.model.ActivityType
import com.example.data.model.AlbumScope
import com.example.data.model.AlbumType
import com.example.data.model.CheckIn
import com.example.data.model.MemoryPhoto
import com.example.data.model.NotificationItem
import com.example.data.model.TripJournal
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

class SocialMemoryRepository(
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

    fun observeActivities(connectionId: String, dateKey: String): Flow<List<ActivityEvent>> = flow {
        val uid = requireUserId()
        val path = "activities"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ActivityEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.connectionId == connectionId && it.dateKey == dateKey }
                        .sortedByDescending { it.eventTimeMs }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeCheckIns(connectionId: String): Flow<List<CheckIn>> = flow {
        val uid = requireUserId()
        val path = "checkins"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CheckIn::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.connectionId == connectionId }
                        .sortedByDescending { it.capturedAtMs }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeMemories(connectionId: String): Flow<List<MemoryPhoto>> = flow {
        val uid = requireUserId()
        val path = "memories"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MemoryPhoto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.connectionId == connectionId }
                        .sortedByDescending { it.capturedAtMs }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeTrips(connectionId: String): Flow<List<TripJournal>> = flow {
        val uid = requireUserId()
        val path = "trips"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TripJournal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.connectionId == connectionId }
                        .sortedByDescending { it.startDateMs }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeNotifications(connectionId: String): Flow<List<NotificationItem>> = flow {
        val uid = requireUserId()
        val path = "notifications"
        emitAll(
            db.collection(path)
                .whereEqualTo("recipientUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.connectionId == connectionId }
                        .sortedByDescending { it.createdAtMs }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    suspend fun recordActivity(event: ActivityEvent): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val docRef = db.collection("activities").document()
        return try {
            val payload = mapOf(
                "userId" to uid,
                "actorName" to event.actorName.ifBlank { "Bạn" }.take(80),
                "connectionId" to event.connectionId,
                "memberUids" to event.memberUids,
                "type" to event.type,
                "title" to event.title.take(140),
                "description" to event.description.take(500),
                "placeName" to event.placeName?.take(120),
                "latitude" to event.latitude,
                "longitude" to event.longitude,
                "dateKey" to event.dateKey,
                "eventTimeMs" to event.eventTimeMs,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun createCheckInWithPhotos(
        authorName: String,
        connectionId: String,
        allMemberUids: List<String>,
        placeName: String,
        latitude: Double,
        longitude: Double,
        note: String?,
        albumScope: AlbumScope,
        tripId: String?,
        encodedPhotos: List<String>,
        dateKey: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val checkinRef = db.collection("checkins").document()
        return try {
            val cleanPlace = placeName.trim().take(140)
            require(cleanPlace.isNotEmpty()) { "Tên địa điểm không được để trống" }
            val scopedMembers = if (albumScope == AlbumScope.PERSONAL) listOf(uid) else allMemberUids
            val nowMs = System.currentTimeMillis()
            val primaryPhoto = encodedPhotos.firstOrNull()?.take(680_000)

            val checkinPayload = mapOf(
                "userId" to uid,
                "authorName" to authorName.ifBlank { "Bạn" }.take(80),
                "connectionId" to connectionId,
                "memberUids" to scopedMembers,
                "placeName" to cleanPlace,
                "latitude" to latitude.coerceIn(-90.0, 90.0),
                "longitude" to longitude.coerceIn(-180.0, 180.0),
                "note" to note?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
                "albumScope" to albumScope.name,
                "tripId" to tripId?.takeIf { it.isNotBlank() },
                "photoCount" to encodedPhotos.size.toLong().coerceIn(0L, 10L),
                "primaryPhotoBase64" to primaryPhoto,
                "capturedAtMs" to nowMs,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            checkinRef.set(checkinPayload).await()

            val albumType = when {
                !tripId.isNullOrBlank() -> AlbumType.TRIP.name
                albumScope == AlbumScope.PERSONAL -> AlbumType.PERSONAL.name
                else -> AlbumType.SHARED.name
            }

            encodedPhotos.take(4).forEach { base64 ->
                val memRef = db.collection("memories").document()
                val memPayload = mapOf(
                    "userId" to uid,
                    "connectionId" to connectionId,
                    "memberUids" to scopedMembers,
                    "checkinId" to checkinRef.id,
                    "tripId" to tripId?.takeIf { it.isNotBlank() },
                    "albumType" to albumType,
                    "placeName" to cleanPlace,
                    "latitude" to latitude.coerceIn(-90.0, 90.0),
                    "longitude" to longitude.coerceIn(-180.0, 180.0),
                    "caption" to note?.trim()?.take(300)?.takeIf { it.isNotEmpty() },
                    "imageBase64" to base64.take(680_000),
                    "capturedAtMs" to nowMs,
                    "createdAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                memRef.set(memPayload).await()
            }

            if (albumScope == AlbumScope.SHARED) {
                recordActivity(
                    ActivityEvent(
                        userId = uid,
                        actorName = authorName,
                        connectionId = connectionId,
                        memberUids = allMemberUids,
                        type = ActivityType.CHECKIN.name,
                        title = "$authorName đã check-in tại $cleanPlace",
                        description = note?.trim()?.take(500)?.ifBlank { "Đã lưu ${encodedPhotos.size} ảnh kỷ niệm" }
                            ?: "Đã lưu ${encodedPhotos.size} ảnh kỷ niệm",
                        placeName = cleanPlace,
                        latitude = latitude,
                        longitude = longitude,
                        dateKey = dateKey,
                        eventTimeMs = nowMs
                    )
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, checkinRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCheckIn(checkinId: String): Result<Unit> {
        return try {
            requireUserId()
            require(checkinId.isNotBlank())
            val relatedMemories = db.collection("memories")
                .whereArrayContains("memberUids", requireUserId())
                .get()
                .await()
                .documents
                .filter { it.getString("checkinId") == checkinId && it.getString("userId") == requireUserId() }
            relatedMemories.forEach { it.reference.delete().await() }
            db.collection("checkins").document(checkinId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "checkins/$checkinId")
            Result.failure(e)
        }
    }

    suspend fun saveTrip(trip: TripJournal): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val isCreate = trip.id.isBlank()
        val docRef = if (isCreate) db.collection("trips").document() else db.collection("trips").document(trip.id)
        return try {
            val cleanTitle = trip.title.trim().take(120)
            val cleanDest = trip.destination.trim().take(140)
            require(cleanTitle.isNotEmpty()) { "Tên chuyến đi không được để trống" }
            require(cleanDest.isNotEmpty()) { "Địa điểm chuyến đi không được để trống" }
            val endMs = maxOf(trip.startDateMs, trip.endDateMs)
            if (isCreate) {
                val payload = mapOf(
                    "userId" to uid,
                    "connectionId" to trip.connectionId,
                    "memberUids" to trip.memberUids,
                    "title" to cleanTitle,
                    "destination" to cleanDest,
                    "startDateMs" to trip.startDateMs,
                    "endDateMs" to endMs,
                    "notes" to trip.notes?.trim()?.take(2000)?.takeIf { it.isNotEmpty() },
                    "participantNames" to trip.participantNames.take(2),
                    "coverPhotoBase64" to trip.coverPhotoBase64?.take(680_000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "title" to cleanTitle,
                    "destination" to cleanDest,
                    "startDateMs" to trip.startDateMs,
                    "endDateMs" to endMs,
                    "notes" to (trip.notes?.trim()?.take(2000)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                    "participantNames" to trip.participantNames.take(2),
                    "coverPhotoBase64" to (trip.coverPhotoBase64?.take(680_000) ?: FieldValue.delete()),
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

    suspend fun deleteTrip(tripId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("trips").document(tripId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "trips/$tripId")
            Result.failure(e)
        }
    }

    suspend fun sendPartnerNotification(
        recipientUid: String,
        connectionId: String,
        memberUids: List<String>,
        type: String,
        title: String,
        body: String,
        placeName: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val docRef = db.collection("notifications").document()
        return try {
            val payload = mapOf(
                "senderId" to uid,
                "recipientUid" to recipientUid,
                "connectionId" to connectionId,
                "memberUids" to memberUids,
                "type" to type.take(50),
                "title" to title.take(140),
                "body" to body.take(400),
                "placeName" to placeName?.take(120),
                "readFlag" to false,
                "createdAtMs" to System.currentTimeMillis(),
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

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("notifications").document(notificationId)
                .update(mapOf("readFlag" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications/$notificationId")
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
