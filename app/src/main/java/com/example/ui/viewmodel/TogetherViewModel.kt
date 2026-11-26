package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.LocationRepository
import com.example.data.firebase.PairingRepository
import com.example.data.firebase.SocialMemoryRepository
import com.example.data.firebase.UiState
import com.example.data.model.ActivityEvent
import com.example.data.model.ActivityType
import com.example.data.model.AlbumScope
import com.example.data.model.CheckIn
import com.example.data.model.Connection
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceStatus
import com.example.data.model.GpsPowerMode
import com.example.data.model.ImportantPlace
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryEntry
import com.example.data.model.MemoryPhoto
import com.example.data.model.NotificationItem
import com.example.data.model.PairingCode
import com.example.data.model.SharingMode
import com.example.data.model.SharingPermission
import com.example.data.model.TripJournal
import com.example.data.model.UserProfile
import com.example.location.DeviceTelemetryHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TogetherViewModel(
    private val pairingRepo: PairingRepository,
    private val locationRepo: LocationRepository,
    private val socialRepo: SocialMemoryRepository,
    val currentUserId: String
) : ViewModel() {

    private val _selectedDateKey = MutableStateFlow(DeviceTelemetryHelper.formatDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _pairingLookupResult = MutableStateFlow<PairingCode?>(null)
    val pairingLookupResult: StateFlow<PairingCode?> = _pairingLookupResult.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _tickerMs = MutableStateFlow(System.currentTimeMillis())

    val myProfile: StateFlow<UserProfile?> = pairingRepo.observeMyProfile()
        .catch { handleStreamError("Profile", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeConnectionState: StateFlow<UiState<Connection?>> = pairingRepo.observeActiveConnection()
        .map<Connection?, UiState<Connection?>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Active connection error", root)
            emit(UiState.Error(root.localizedMessage ?: "Không thể tải kết nối"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val activeConnectionFlow = activeConnectionState
        .map { (it as? UiState.Success)?.data }

    val activePairingCodes: StateFlow<List<PairingCode>> = pairingRepo.observeMyPairingCodes()
        .catch { handleStreamError("PairingCodes", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myLocation: StateFlow<LiveLocation?> = locationRepo.observeMyLocation()
        .catch { handleStreamError("MyLocation", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val myDeviceStatus: StateFlow<DeviceStatus?> = locationRepo.observeMyDeviceStatus()
        .catch { handleStreamError("MyDeviceStatus", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val mySharingPermission: StateFlow<SharingPermission?> = locationRepo.observeMySharingPermission()
        .catch { handleStreamError("MySharing", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val partnerLocation: StateFlow<LiveLocation?> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(null) else locationRepo.observePartnerLocation(conn.id)
        }
        .catch { handleStreamError("PartnerLocation", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val partnerDeviceStatus: StateFlow<DeviceStatus?> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(null) else locationRepo.observePartnerDeviceStatus(conn.id)
        }
        .catch { handleStreamError("PartnerDeviceStatus", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val partnerSharingPermission: StateFlow<SharingPermission?> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(null) else locationRepo.observePartnerSharingPermission(conn.id)
        }
        .catch { handleStreamError("PartnerSharing", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val places: StateFlow<List<ImportantPlace>> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(emptyList()) else locationRepo.observePlaces(conn.id)
        }
        .catch { handleStreamError("Places", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activities: StateFlow<List<ActivityEvent>> = combine(activeConnectionFlow, _selectedDateKey) { conn, dateKey ->
        conn to dateKey
    }.flatMapLatest { (conn, dateKey) ->
        if (conn == null) flowOf(emptyList()) else socialRepo.observeActivities(conn.id, dateKey)
    }
        .catch { handleStreamError("Activities", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val locationHistory: StateFlow<List<LocationHistoryEntry>> = combine(activeConnectionFlow, _selectedDateKey) { conn, dateKey ->
        conn to dateKey
    }.flatMapLatest { (conn, dateKey) ->
        if (conn == null) flowOf(emptyList()) else locationRepo.observeLocationHistory(conn.id, dateKey)
    }
        .catch { handleStreamError("History", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val checkIns: StateFlow<List<CheckIn>> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(emptyList()) else socialRepo.observeCheckIns(conn.id)
        }
        .catch { handleStreamError("CheckIns", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val memories: StateFlow<List<MemoryPhoto>> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(emptyList()) else socialRepo.observeMemories(conn.id)
        }
        .catch { handleStreamError("Memories", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val trips: StateFlow<List<TripJournal>> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(emptyList()) else socialRepo.observeTrips(conn.id)
        }
        .catch { handleStreamError("Trips", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = activeConnectionFlow
        .flatMapLatest { conn ->
            if (conn == null) flowOf(emptyList()) else socialRepo.observeNotifications(conn.id)
        }
        .catch { handleStreamError("Notifications", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val distanceMeters: StateFlow<Float?> = combine(myLocation, partnerLocation) { mine, partner ->
        if (mine == null || partner == null) null
        else if (mine.latitude == 0.0 && mine.longitude == 0.0) null
        else if (partner.latitude == 0.0 && partner.longitude == 0.0) null
        else DeviceTelemetryHelper.distanceMetersBetween(
            mine.latitude, mine.longitude,
            partner.latitude, partner.longitude
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val partnerEffectiveConnection: StateFlow<ConnectionState> = combine(partnerDeviceStatus, _tickerMs) { status, now ->
        if (status == null) return@combine ConnectionState.OFFLINE
        val age = now - status.lastHeartbeatMs
        when {
            status.parsedConnection == ConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
            age > 120_000L -> ConnectionState.OFFLINE
            age > 65_000L -> ConnectionState.SYNCING
            else -> status.parsedConnection
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ConnectionState.OFFLINE)

    init {
        viewModelScope.launch {
            pairingRepo.ensureUserProfile().onFailure {
                _uiMessage.tryEmit("Không thể khởi tạo hồ sơ: ${it.localizedMessage}")
            }
        }
        viewModelScope.launch {
            activeConnectionFlow.collect { conn ->
                pairingRepo.syncUserConnectionMetadata(conn)
                locationRepo.ensureDefaultSharingPermission(conn?.id, conn?.partnerUid(currentUserId))
            }
        }
        viewModelScope.launch {
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(15_000L)
                }
            }.collect { _tickerMs.value = it }
        }
    }

    fun selectDateKey(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun createNewPairingCode() {
        val profile = myProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            pairingRepo.createOrRefreshPairingCode(profile)
                .onSuccess { _uiMessage.tryEmit("Đã tạo mã kết nối mới: ${it.code}") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể tạo mã kết nối") }
            _isBusy.value = false
        }
    }

    fun lookupPairingCode(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            pairingRepo.lookupPairingCode(code)
                .onSuccess { _pairingLookupResult.value = it }
                .onFailure {
                    _pairingLookupResult.value = null
                    _uiMessage.tryEmit(it.localizedMessage ?: "Mã kết nối không hợp lệ")
                }
            _isBusy.value = false
        }
    }

    fun clearPairingLookup() {
        _pairingLookupResult.value = null
    }

    fun acceptPairing(pairing: PairingCode) {
        val profile = myProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            pairingRepo.acceptPairingCode(pairing, profile)
                .onSuccess { conn ->
                    _pairingLookupResult.value = null
                    locationRepo.ensureDefaultSharingPermission(conn.id, conn.partnerUid(currentUserId))
                    _uiMessage.tryEmit("Đã ghép đôi thành công với ${pairing.ownerName}!")
                }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể kết nối") }
            _isBusy.value = false
        }
    }

    fun disconnectPartner() {
        val conn = (activeConnectionState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            pairingRepo.disconnectConnection(conn)
                .onSuccess { _uiMessage.tryEmit("Đã ngắt kết nối và thu hồi quyền chia sẻ dữ liệu.") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Lỗi khi ngắt kết nối") }
            _isBusy.value = false
        }
    }

    fun updateProfile(displayName: String, phoneNumber: String?) {
        val conn = (activeConnectionState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            pairingRepo.updateProfile(displayName, phoneNumber, conn)
                .onSuccess { _uiMessage.tryEmit("Đã cập nhật hồ sơ") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Lỗi cập nhật hồ sơ") }
            _isBusy.value = false
        }
    }

    fun setSharingMode(mode: SharingMode) {
        val current = mySharingPermission.value ?: SharingPermission(userId = currentUserId)
        val conn = (activeConnectionState.value as? UiState.Success)?.data
        val expiresAt = mode.durationMs?.let { System.currentTimeMillis() + it }
        val updated = current.copy(
            userId = currentUserId,
            sharingMode = mode.name,
            sharingExpiresAtMs = expiresAt,
            shareLocation = mode != SharingMode.OFF
        )
        viewModelScope.launch {
            locationRepo.updateSharingPermission(updated, conn?.id, conn?.partnerUid(currentUserId))
                .onSuccess {
                    _uiMessage.tryEmit("Chế độ chia sẻ: ${mode.labelVi}")
                    if (conn != null) {
                        val actor = myProfile.value?.displayName ?: "Bạn"
                        val actType = if (mode == SharingMode.PAUSED || mode == SharingMode.OFF) {
                            ActivityType.SHARING_PAUSED
                        } else {
                            ActivityType.SHARING_STARTED
                        }
                        socialRepo.recordActivity(
                            ActivityEvent(
                                userId = currentUserId,
                                actorName = actor,
                                connectionId = conn.id,
                                memberUids = conn.memberUids,
                                type = actType.name,
                                title = "$actor: ${mode.labelVi}",
                                description = "Cập nhật quyền chia sẻ vị trí",
                                dateKey = DeviceTelemetryHelper.formatDateKey(),
                                eventTimeMs = System.currentTimeMillis()
                            )
                        )
                        val partnerUid = conn.partnerUid(currentUserId)
                        if (partnerUid != null && updated.notifySharingChange) {
                            socialRepo.sendPartnerNotification(
                                recipientUid = partnerUid,
                                connectionId = conn.id,
                                memberUids = conn.memberUids,
                                type = actType.name,
                                title = "$actor: ${mode.labelVi}",
                                body = "Quyền chia sẻ vị trí vừa thay đổi"
                            )
                        }
                    }
                }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể cập nhật quyền chia sẻ") }
        }
    }

    fun updateSharingSettings(updated: SharingPermission) {
        val conn = (activeConnectionState.value as? UiState.Success)?.data
        viewModelScope.launch {
            locationRepo.updateSharingPermission(updated, conn?.id, conn?.partnerUid(currentUserId))
                .onSuccess { _uiMessage.tryEmit("Đã lưu cài đặt chia sẻ & thông báo") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể lưu cài đặt") }
        }
    }

    fun updateGpsPowerMode(mode: GpsPowerMode) {
        val current = mySharingPermission.value ?: SharingPermission(userId = currentUserId)
        val updated = current.copy(
            userId = currentUserId,
            gpsPowerMode = mode.name,
            updateIntervalSec = mode.movingIntervalSec.toLong()
        )
        updateSharingSettings(updated)
    }

    fun sendActivityNote(noteText: String) {
        val conn = (activeConnectionState.value as? UiState.Success)?.data ?: return
        val clean = noteText.trim()
        if (clean.isEmpty()) return
        val actor = myProfile.value?.displayName ?: "Bạn"
        val loc = myLocation.value
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch {
            socialRepo.recordActivity(
                ActivityEvent(
                    userId = currentUserId,
                    actorName = actor,
                    connectionId = conn.id,
                    memberUids = conn.memberUids,
                    type = ActivityType.NOTE.name,
                    title = "$actor gửi cập nhật",
                    description = clean,
                    placeName = loc?.currentPlaceName ?: loc?.address,
                    latitude = loc?.latitude,
                    longitude = loc?.longitude,
                    dateKey = DeviceTelemetryHelper.formatDateKey(nowMs),
                    eventTimeMs = nowMs
                )
            ).onSuccess {
                _uiMessage.tryEmit("Đã đăng cập nhật lên dòng thời gian")
                val partnerUid = conn.partnerUid(currentUserId)
                if (partnerUid != null && (mySharingPermission.value?.notifyActivityNote != false)) {
                    socialRepo.sendPartnerNotification(
                        recipientUid = partnerUid,
                        connectionId = conn.id,
                        memberUids = conn.memberUids,
                        type = ActivityType.NOTE.name,
                        title = "$actor gửi cập nhật",
                        body = clean,
                        placeName = loc?.currentPlaceName
                    )
                }
            }.onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể gửi cập nhật") }
        }
    }

    fun saveImportantPlace(place: ImportantPlace) {
        val conn = (activeConnectionState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            locationRepo.savePlace(
                place.copy(
                    userId = currentUserId,
                    connectionId = conn.id,
                    memberUids = conn.memberUids
                )
            )
                .onSuccess { _uiMessage.tryEmit("Đã lưu địa điểm \"${place.name}\"") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể lưu địa điểm") }
            _isBusy.value = false
        }
    }

    fun deleteImportantPlace(placeId: String) {
        viewModelScope.launch {
            locationRepo.deletePlace(placeId)
                .onSuccess { _uiMessage.tryEmit("Đã xóa địa điểm") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể xóa địa điểm") }
        }
    }

    fun deleteMyLocationHistory(dateKey: String? = null) {
        viewModelScope.launch {
            _isBusy.value = true
            locationRepo.deleteMyLocationHistory(dateKey)
                .onSuccess { count -> _uiMessage.tryEmit("Đã xóa $count điểm lịch sử di chuyển") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể xóa lịch sử") }
            _isBusy.value = false
        }
    }

    fun submitCheckIn(
        placeName: String,
        latitude: Double,
        longitude: Double,
        note: String?,
        albumScope: AlbumScope,
        tripId: String?,
        encodedPhotos: List<String>,
        onSuccess: () -> Unit
    ) {
        val conn = (activeConnectionState.value as? UiState.Success)?.data ?: return
        val author = myProfile.value?.displayName ?: "Bạn"
        viewModelScope.launch {
            _isBusy.value = true
            socialRepo.createCheckInWithPhotos(
                authorName = author,
                connectionId = conn.id,
                allMemberUids = conn.memberUids,
                placeName = placeName,
                latitude = latitude,
                longitude = longitude,
                note = note,
                albumScope = albumScope,
                tripId = tripId,
                encodedPhotos = encodedPhotos,
                dateKey = DeviceTelemetryHelper.formatDateKey()
            ).onSuccess {
                if (albumScope == AlbumScope.SHARED) {
                    val partnerUid = conn.partnerUid(currentUserId)
                    if (partnerUid != null && (mySharingPermission.value?.notifyCheckin != false)) {
                        socialRepo.sendPartnerNotification(
                            recipientUid = partnerUid,
                            connectionId = conn.id,
                            memberUids = conn.memberUids,
                            type = ActivityType.CHECKIN.name,
                            title = "$author đã check-in tại $placeName",
                            body = note?.takeIf { it.isNotBlank() } ?: "Vừa lưu ${encodedPhotos.size} ảnh kỷ niệm",
                            placeName = placeName
                        )
                    }
                }
                _uiMessage.tryEmit("Đã lưu check-in kỷ niệm!")
                onSuccess()
            }.onFailure {
                _uiMessage.tryEmit(it.localizedMessage ?: "Không thể lưu check-in")
            }
            _isBusy.value = false
        }
    }

    fun deleteCheckIn(checkinId: String) {
        viewModelScope.launch {
            socialRepo.deleteCheckIn(checkinId)
                .onSuccess { _uiMessage.tryEmit("Đã xóa check-in") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể xóa check-in") }
        }
    }

    fun saveTrip(trip: TripJournal) {
        val conn = (activeConnectionState.value as? UiState.Success)?.data ?: return
        val myName = myProfile.value?.displayName ?: "Bạn"
        val partnerName = conn.partnerName(currentUserId)
        viewModelScope.launch {
            _isBusy.value = true
            socialRepo.saveTrip(
                trip.copy(
                    userId = currentUserId,
                    connectionId = conn.id,
                    memberUids = conn.memberUids,
                    participantNames = listOf(myName, partnerName)
                )
            )
                .onSuccess { _uiMessage.tryEmit("Đã lưu nhật ký chuyến đi") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể lưu chuyến đi") }
            _isBusy.value = false
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            socialRepo.deleteTrip(tripId)
                .onSuccess { _uiMessage.tryEmit("Đã xóa chuyến đi") }
                .onFailure { _uiMessage.tryEmit(it.localizedMessage ?: "Không thể xóa chuyến đi") }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            socialRepo.markNotificationRead(notificationId)
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        val conn = (activeConnectionState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            pairingRepo.deleteAccountData(conn)
                .onSuccess {
                    _uiMessage.tryEmit("Đã xóa tài khoản và dữ liệu cá nhân")
                    onComplete()
                }
                .onFailure {
                    _uiMessage.tryEmit(it.localizedMessage ?: "Cần đăng nhập lại gần đây để xóa tài khoản")
                }
            _isBusy.value = false
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private fun handleStreamError(label: String, error: Throwable) {
        val fsError = unwrapFirestoreError(error)
        if (fsError == null && error is CancellationException) throw error
        Log.w(TAG, "Stream error [$label]", fsError ?: error)
    }

    private companion object {
        const val TAG = "TogetherVM"
    }
}
