package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.Connection
import com.example.data.model.PairingCode
import com.example.data.model.UserProfile
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
import kotlin.random.Random

class PairingRepository(
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

    fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            append("TG")
            repeat(6) { append(chars[Random.nextInt(chars.length)]) }
        }
    }

    fun observeMyProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val ex = fsError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeActiveConnection(): Flow<Connection?> = flow {
        val uid = requireUserId()
        val path = "connections"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Connection::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .firstOrNull { it.status == "active" && it.memberUids.size == 2 }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val ex = fsError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyPairingCodes(): Flow<List<PairingCode>> = flow {
        val uid = requireUserId()
        val path = "pairing_codes"
        emitAll(
            db.collection(path)
                .whereEqualTo("ownerUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PairingCode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "waiting" }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val ex = fsError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Chưa đăng nhập Google"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val user = auth.currentUser!!
            val safeName = (user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore("@") ?: "Bạn")
                .take(80)
            val safeEmail = (user.email?.takeIf { it.isNotBlank() } ?: "$uid@togethergps.local").take(160)
            val safePhoto = user.photoUrl?.toString()?.take(1000)

            if (!snap.exists()) {
                val inviteCode = generateInviteCode()
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "inviteCode" to inviteCode,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
                val created = docRef.get().await()
                    .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = uid, displayName = safeName, email = safeEmail, photoUrl = safePhoto, inviteCode = inviteCode)
                Result.success(created)
            } else {
                val profile = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)!!
                Result.success(profile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(displayName: String, phoneNumber: String?, activeConnection: Connection?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val path = "users/$uid"
        return try {
            val trimmedName = displayName.trim().take(80)
            require(trimmedName.isNotEmpty()) { "Tên hiển thị không được để trống" }
            val trimmedPhone = phoneNumber?.trim()?.take(32)?.takeIf { it.isNotEmpty() }
            val updates = mutableMapOf<String, Any>(
                "displayName" to trimmedName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (trimmedPhone != null) {
                updates["phoneNumber"] = trimmedPhone
            }
            db.collection("users").document(uid).update(updates).await()

            if (activeConnection != null && activeConnection.id.isNotBlank()) {
                val updatedNames = activeConnection.memberNames.toMutableMap().apply { put(uid, trimmedName) }
                val updatedPhones = activeConnection.memberPhones.toMutableMap().apply {
                    if (trimmedPhone != null) put(uid, trimmedPhone) else remove(uid)
                }
                db.collection("connections").document(activeConnection.id).update(
                    mapOf(
                        "memberNames" to updatedNames,
                        "memberPhones" to updatedPhones,
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

    suspend fun createOrRefreshPairingCode(profile: UserProfile): Result<PairingCode> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val code = generateInviteCode()
        val path = "pairing_codes/$code"
        return try {
            // Cancel any old waiting codes
            val existing = db.collection("pairing_codes")
                .whereEqualTo("ownerUid", uid)
                .get()
                .await()
            for (doc in existing.documents) {
                if (doc.getString("status") == "waiting") {
                    doc.reference.update(
                        mapOf("status" to "cancelled", "updatedAt" to FieldValue.serverTimestamp())
                    ).await()
                }
            }

            val payload = mapOf(
                "code" to code,
                "ownerUid" to uid,
                "ownerName" to profile.displayName.ifBlank { "Bạn" }.take(80),
                "ownerPhotoUrl" to profile.photoUrl?.take(1000),
                "ownerPhone" to profile.phoneNumber?.take(32),
                "status" to "waiting",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            db.collection("pairing_codes").document(code).set(payload).await()
            db.collection("users").document(uid).update(
                mapOf("inviteCode" to code, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            val created = db.collection("pairing_codes").document(code).get().await()
                .toObject(PairingCode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: PairingCode(code = code, ownerUid = uid, ownerName = profile.displayName)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun lookupPairingCode(rawCode: String): Result<PairingCode> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val cleanCode = rawCode.trim().uppercase()
        if (!cleanCode.matches(Regex("^[A-Z0-9]{6,12}$"))) {
            return Result.failure(IllegalArgumentException("Mã kết nối phải gồm 6–12 ký tự chữ/số in hoa"))
        }
        val path = "pairing_codes/$cleanCode"
        return try {
            val snap = db.collection("pairing_codes").document(cleanCode).get().await()
            val pairing = snap.toObject(PairingCode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Không tìm thấy mã kết nối $cleanCode"))
            if (pairing.ownerUid == uid) {
                return Result.failure(IllegalStateException("Không thể tự kết nối với mã của chính bạn"))
            }
            if (pairing.status != "waiting") {
                return Result.failure(IllegalStateException("Mã kết nối này đã được sử dụng hoặc đã hủy"))
            }
            Result.success(pairing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun acceptPairingCode(pairing: PairingCode, myProfile: UserProfile): Result<Connection> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val connectionRef = db.collection("connections").document()
        val connectionId = connectionRef.id
        return try {
            val existingActive = db.collection("connections")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
                .toObjects(Connection::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .any { it.status == "active" }
            if (existingActive) {
                return Result.failure(IllegalStateException("Bạn đang kết nối với một người khác. Hãy ngắt kết nối trước khi ghép đôi mới."))
            }

            val memberNames = mutableMapOf(
                pairing.ownerUid to pairing.ownerName.ifBlank { "Người đồng hành" },
                uid to myProfile.displayName.ifBlank { "Bạn" }
            )
            val memberPhotos = mutableMapOf<String, String>().apply {
                pairing.ownerPhotoUrl?.takeIf { it.isNotBlank() }?.let { put(pairing.ownerUid, it) }
                myProfile.photoUrl?.takeIf { it.isNotBlank() }?.let { put(uid, it) }
            }
            val memberPhones = mutableMapOf<String, String>().apply {
                pairing.ownerPhone?.takeIf { it.isNotBlank() }?.let { put(pairing.ownerUid, it) }
                myProfile.phoneNumber?.takeIf { it.isNotBlank() }?.let { put(uid, it) }
            }

            val connPayload = mapOf(
                "memberUids" to listOf(pairing.ownerUid, uid),
                "memberNames" to memberNames,
                "memberPhotos" to memberPhotos,
                "memberPhones" to memberPhones,
                "inviteCode" to pairing.code,
                "status" to "active",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            connectionRef.set(connPayload).await()

            val codeUpdates = mapOf(
                "status" to "paired",
                "partnerUid" to uid,
                "partnerName" to myProfile.displayName.ifBlank { "Bạn" }.take(80),
                "partnerPhotoUrl" to myProfile.photoUrl?.take(1000),
                "connectionId" to connectionId,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            db.collection("pairing_codes").document(pairing.code).update(codeUpdates).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "connectionId" to connectionId,
                    "partnerId" to pairing.ownerUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val created = connectionRef.get().await()
                .toObject(Connection::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Connection(
                    id = connectionId,
                    memberUids = listOf(pairing.ownerUid, uid),
                    memberNames = memberNames,
                    memberPhotos = memberPhotos,
                    memberPhones = memberPhones,
                    inviteCode = pairing.code,
                    status = "active",
                    createdBy = uid
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "connections/$connectionId")
            Result.failure(e)
        }
    }

    suspend fun syncUserConnectionMetadata(connection: Connection?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val path = "users/$uid"
        return try {
            val updates = if (connection != null && connection.status == "active") {
                mapOf(
                    "connectionId" to connection.id,
                    "partnerId" to (connection.partnerUid(uid) ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "connectionId" to FieldValue.delete(),
                    "partnerId" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun disconnectConnection(connection: Connection): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        val path = "connections/${connection.id}"
        return try {
            db.collection("connections").document(connection.id).update(
                mapOf(
                    "status" to "disconnected",
                    "disconnectedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Revoke authorizedPartnerUid across own live docs immediately
            val revokeMap = mapOf(
                "connectionId" to FieldValue.delete(),
                "authorizedPartnerUid" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            listOf("locations", "device_status", "sharing_permissions").forEach { col ->
                val ref = db.collection(col).document(uid)
                if (ref.get().await().exists()) {
                    ref.update(revokeMap).await()
                }
            }

            db.collection("users").document(uid).update(
                mapOf(
                    "connectionId" to FieldValue.delete(),
                    "partnerId" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccountData(activeConnection: Connection?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        return try {
            if (activeConnection != null && activeConnection.status == "active") {
                disconnectConnection(activeConnection)
            }
            listOf("locations", "device_status", "sharing_permissions", "users").forEach { col ->
                val ref = db.collection(col).document(uid)
                if (ref.get().await().exists()) {
                    ref.delete().await()
                }
            }
            val historyDocs = db.collection("location_history")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            historyDocs.documents.filter { it.getString("userId") == uid }.forEach {
                it.reference.delete().await()
            }
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}
