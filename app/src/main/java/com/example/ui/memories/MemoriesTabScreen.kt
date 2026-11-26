package com.example.ui.memories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardTravel
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AlbumScope
import com.example.data.model.CheckIn
import com.example.data.model.Connection
import com.example.data.model.MemoryPhoto
import com.example.data.model.TripJournal
import com.example.location.DeviceTelemetryHelper
import com.example.media.PhotoCompressionHelper
import com.example.ui.components.Base64PhotoImage
import com.example.ui.map.MapFocusRequest
import com.example.ui.map.OsmMapView
import com.example.ui.viewmodel.TogetherViewModel
import kotlinx.coroutines.launch

private enum class MemoryFilter(val labelVi: String) {
    ALL("Tất cả"),
    SHARED("Album chung"),
    PERSONAL("Cá nhân"),
    BY_PLACE("Theo địa điểm"),
    BY_TRIP("Theo chuyến đi")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoriesTabScreen(
    viewModel: TogetherViewModel,
    connection: Connection,
    onOpenCameraCheckIn: () -> Unit
) {
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val partnerLocation by viewModel.partnerLocation.collectAsStateWithLifecycle()
    val checkIns by viewModel.checkIns.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()

    val myName = myProfile?.displayName ?: "Bạn"
    val partnerName = connection.partnerName(viewModel.currentUserId)

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf(MemoryFilter.ALL) }
    var selectedPlaceFilter by remember { mutableStateOf<String?>(null) }
    var selectedTripFilterId by remember { mutableStateOf<String?>(null) }

    var detailCheckIn by remember { mutableStateOf<CheckIn?>(null) }
    var editingTrip by remember { mutableStateOf<TripJournal?>(null) }
    var showTripDialog by remember { mutableStateOf(false) }
    var detailTrip by remember { mutableStateOf<TripJournal?>(null) }

    val uniquePlaces = remember(checkIns) {
        checkIns.map { it.placeName }.filter { it.isNotBlank() }.distinct()
    }

    val filteredCheckIns = remember(checkIns, selectedFilter, selectedPlaceFilter, selectedTripFilterId) {
        when (selectedFilter) {
            MemoryFilter.ALL -> checkIns
            MemoryFilter.SHARED -> checkIns.filter { it.parsedScope == AlbumScope.SHARED }
            MemoryFilter.PERSONAL -> checkIns.filter { it.parsedScope == AlbumScope.PERSONAL }
            MemoryFilter.BY_PLACE -> {
                if (selectedPlaceFilter == null) checkIns
                else checkIns.filter { it.placeName == selectedPlaceFilter }
            }
            MemoryFilter.BY_TRIP -> {
                if (selectedTripFilterId == null) checkIns.filter { !it.tripId.isNullOrBlank() }
                else checkIns.filter { it.tripId == selectedTripFilterId }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            // Sub-tab bar
            PrimaryTabRow(selectedTabIndex = selectedSubTab) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Check-in & Ảnh") },
                    icon = { Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Bản đồ kỷ niệm") },
                    icon = { Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("Chuyến đi") },
                    icon = { Icon(Icons.Rounded.CardTravel, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedSubTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            MemoryHeroBanner(
                                totalCheckIns = checkIns.size,
                                totalPhotos = memories.size,
                                totalTrips = trips.size,
                                onNewCheckIn = onOpenCameraCheckIn
                            )
                        }

                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MemoryFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = {
                                            selectedFilter = filter
                                            if (filter != MemoryFilter.BY_PLACE) selectedPlaceFilter = null
                                            if (filter != MemoryFilter.BY_TRIP) selectedTripFilterId = null
                                        },
                                        label = { Text(filter.labelVi) }
                                    )
                                }
                            }
                        }

                        if (selectedFilter == MemoryFilter.BY_PLACE && uniquePlaces.isNotEmpty()) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(uniquePlaces) { place ->
                                        FilterChip(
                                            selected = selectedPlaceFilter == place,
                                            onClick = {
                                                selectedPlaceFilter = if (selectedPlaceFilter == place) null else place
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            label = { Text(place) }
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedFilter == MemoryFilter.BY_TRIP && trips.isNotEmpty()) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(trips, key = { it.id }) { trip ->
                                        FilterChip(
                                            selected = selectedTripFilterId == trip.id,
                                            onClick = {
                                                selectedTripFilterId = if (selectedTripFilterId == trip.id) null else trip.id
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.CardTravel, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            label = { Text(trip.title) }
                                        )
                                    }
                                }
                            }
                        }

                        if (filteredCheckIns.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoCamera,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = "Chưa có check-in kỷ niệm nào",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Chụp ảnh trực tiếp hoặc chọn ảnh kỷ niệm tại nơi hai bạn đi qua để lưu dấu ấn.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = onOpenCameraCheckIn,
                                            modifier = Modifier.testTag("empty_memories_checkin_button")
                                        ) {
                                            Text("Chụp check-in đầu tiên")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(filteredCheckIns, key = { it.id }) { ck ->
                                CheckInCard(
                                    checkIn = ck,
                                    tripTitle = trips.firstOrNull { it.id == ck.tripId }?.title,
                                    onClick = { detailCheckIn = ck }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OsmMapView(
                            myLocation = myLocation,
                            partnerLocation = partnerLocation,
                            myName = myName,
                            partnerName = partnerName,
                            checkIns = filteredCheckIns,
                            focusRequest = MapFocusRequest.BOTH,
                            focusTrigger = 1,
                            onCheckInMarkerClick = { ck -> detailCheckIn = ck },
                            modifier = Modifier.fillMaxSize()
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "${filteredCheckIns.size} địa điểm check-in kỷ niệm • Chạm ghim vàng để xem",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Nhật ký chuyến đi chung",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Lưu lại hành trình, ghi chú và check-in theo từng chuyến đi",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        editingTrip = null
                                        showTripDialog = true
                                    },
                                    modifier = Modifier.testTag("create_trip_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Tạo mới")
                                }
                            }
                        }

                        if (trips.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CardTravel,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text("Chưa có nhật ký chuyến đi", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Tạo chuyến đi đầu tiên để nhóm các ảnh check-in và ghi chép hành trình của hai người.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(trips, key = { it.id }) { trip ->
                                val tripCheckInsCount = checkIns.count { it.tripId == trip.id }
                                TripCard(
                                    trip = trip,
                                    checkInsCount = tripCheckInsCount,
                                    isOwner = trip.userId == viewModel.currentUserId,
                                    onClick = { detailTrip = trip },
                                    onEdit = {
                                        editingTrip = trip
                                        showTripDialog = true
                                    },
                                    onDelete = { viewModel.deleteTrip(trip.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (selectedSubTab == 2) {
                    editingTrip = null
                    showTripDialog = true
                } else {
                    onOpenCameraCheckIn()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("memories_fab"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = if (selectedSubTab == 2) Icons.Rounded.Add else Icons.Rounded.PhotoCamera,
                contentDescription = if (selectedSubTab == 2) "Tạo chuyến đi mới" else "Check-in chụp ảnh"
            )
        }
    }

    detailCheckIn?.let { ck ->
        val relatedPhotos = remember(memories, ck.id) {
            memories.filter { it.checkinId == ck.id }
        }
        CheckInDetailDialog(
            checkIn = ck,
            photos = relatedPhotos,
            tripTitle = trips.firstOrNull { it.id == ck.tripId }?.title,
            canDelete = ck.userId == viewModel.currentUserId,
            onDelete = {
                viewModel.deleteCheckIn(ck.id)
                detailCheckIn = null
            },
            onDismiss = { detailCheckIn = null }
        )
    }

    if (showTripDialog) {
        TripEditorDialog(
            initialTrip = editingTrip,
            onSave = { trip ->
                viewModel.saveTrip(trip)
                showTripDialog = false
            },
            onDismiss = { showTripDialog = false }
        )
    }

    detailTrip?.let { trip ->
        val tripCheckIns = remember(checkIns, trip.id) { checkIns.filter { it.tripId == trip.id } }
        TripDetailDialog(
            trip = trip,
            checkIns = tripCheckIns,
            onOpenCheckIn = { ck ->
                detailTrip = null
                detailCheckIn = ck
            },
            onDismiss = { detailTrip = null }
        )
    }
}

@Composable
private fun MemoryHeroBanner(
    totalCheckIns: Int,
    totalPhotos: Int,
    totalTrips: Int,
    onNewCheckIn: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.height(148.dp).fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_memory_banner),
                contentDescription = "Ảnh bìa kỷ niệm hành trình",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xD9102C38), Color(0x66102C38))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Kỷ niệm & Check-in của hai ta",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalCheckIns check-in • $totalPhotos ảnh • $totalTrips chuyến đi",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
                Button(
                    onClick = onNewCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("banner_checkin_button")
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check-in ngay")
                }
            }
        }
    }
}

@Composable
private fun CheckInCard(
    checkIn: CheckIn,
    tripTitle: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            if (!checkIn.primaryPhotoBase64.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                    Base64PhotoImage(
                        base64 = checkIn.primaryPhotoBase64,
                        contentDescription = "Ảnh check-in tại ${checkIn.placeName}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (checkIn.photoCount > 1L) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "+${checkIn.photoCount} ảnh",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = checkIn.placeName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = if (checkIn.parsedScope == AlbumScope.PERSONAL) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (checkIn.parsedScope == AlbumScope.PERSONAL) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                            Text(
                                text = checkIn.parsedScope.labelVi,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (!checkIn.note.isNullOrBlank()) {
                    Text(
                        text = checkIn.note,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${checkIn.authorName} • ${DeviceTelemetryHelper.formatRelativeTimeVi(checkIn.capturedAtMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!tripTitle.isNullOrBlank()) {
                        Text(
                            text = tripTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckInDetailDialog(
    checkIn: CheckIn,
    photos: List<MemoryPhoto>,
    tripTitle: String?,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = checkIn.placeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${checkIn.authorName} • ${DeviceTelemetryHelper.formatDateDisplayVi(checkIn.capturedAtMs)} lúc ${
                        DeviceTelemetryHelper.formatClockVi(checkIn.capturedAtMs)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!tripTitle.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Chuyến đi: $tripTitle",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                val displayList = if (photos.isNotEmpty()) {
                    photos.map { it.imageBase64 }
                } else {
                    listOfNotNull(checkIn.primaryPhotoBase64)
                }

                displayList.forEachIndexed { i, b64 ->
                    Base64PhotoImage(
                        base64 = b64,
                        contentDescription = "Ảnh kỷ niệm ${i + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                if (!checkIn.note.isNullOrBlank()) {
                    Text(text = checkIn.note, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    text = String.format(java.util.Locale.US, "Tọa độ: %.5f, %.5f", checkIn.latitude, checkIn.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (canDelete) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Xóa check-in")
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = onDismiss) {
                        Text("Đóng")
                    }
                }
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: TripJournal,
    checkInsCount: Int,
    isOwner: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            if (!trip.coverPhotoBase64.isNullOrBlank()) {
                Base64PhotoImage(
                    base64 = trip.coverPhotoBase64,
                    contentDescription = "Ảnh bìa ${trip.title}",
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${trip.destination} • ${DeviceTelemetryHelper.formatDateDisplayVi(trip.startDateMs)} – ${
                                DeviceTelemetryHelper.formatDateDisplayVi(trip.endDateMs)
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOwner) {
                        Row {
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Sửa chuyến đi")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Xóa chuyến đi", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (!trip.notes.isNullOrBlank()) {
                    Text(
                        text = trip.notes,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$checkInsCount điểm check-in",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (trip.participantNames.isNotEmpty()) {
                        Text(
                            text = trip.participantNames.joinToString(" & "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripEditorDialog(
    initialTrip: TripJournal?,
    onSave: (TripJournal) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialTrip?.title ?: "") }
    var destination by remember { mutableStateOf(initialTrip?.destination ?: "") }
    var notes by remember { mutableStateOf(initialTrip?.notes ?: "") }
    var startDateMs by remember { mutableLongStateOf(initialTrip?.startDateMs ?: System.currentTimeMillis()) }
    var endDateMs by remember { mutableLongStateOf(initialTrip?.endDateMs ?: System.currentTimeMillis()) }
    var coverBase64 by remember { mutableStateOf(initialTrip?.coverPhotoBase64) }

    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                coverBase64 = PhotoCompressionHelper.compressUriToBase64(context, uri)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTrip == null) "Tạo nhật ký chuyến đi" else "Chỉnh sửa chuyến đi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Tên chuyến đi *") },
                    placeholder = { Text("VD: Đà Lạt cuối tuần") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it.take(140) },
                    label = { Text("Điểm đến *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickingStartDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(DeviceTelemetryHelper.formatDateDisplayVi(startDateMs))
                    }
                    OutlinedButton(
                        onClick = { pickingEndDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(DeviceTelemetryHelper.formatDateDisplayVi(endDateMs))
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(2000) },
                    label = { Text("Ghi chép hành trình") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!coverBase64.isNullOrBlank()) {
                    Base64PhotoImage(
                        base64 = coverBase64,
                        contentDescription = "Ảnh bìa chuyến đi",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                OutlinedButton(
                    onClick = {
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (coverBase64 == null) "Chọn ảnh bìa chuyến đi" else "Đổi ảnh bìa")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initialTrip ?: TripJournal()
                    onSave(
                        base.copy(
                            title = title,
                            destination = destination,
                            startDateMs = startDateMs,
                            endDateMs = maxOf(startDateMs, endDateMs),
                            notes = notes.takeIf { it.isNotBlank() },
                            coverPhotoBase64 = coverBase64
                        )
                    )
                },
                enabled = title.isNotBlank() && destination.isNotBlank()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )

    if (pickingStartDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDateMs)
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        startDateMs = it
                        if (endDateMs < it) endDateMs = it
                    }
                    pickingStartDate = false
                }) { Text("Chọn") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) { Text("Hủy") }
            }
        ) { DatePicker(state = state) }
    }

    if (pickingEndDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDateMs)
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDateMs = maxOf(startDateMs, it) }
                    pickingEndDate = false
                }) { Text("Chọn") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) { Text("Hủy") }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun TripDetailDialog(
    trip: TripJournal,
    checkIns: List<CheckIn>,
    onOpenCheckIn: (CheckIn) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!trip.coverPhotoBase64.isNullOrBlank()) {
                    Base64PhotoImage(
                        base64 = trip.coverPhotoBase64,
                        contentDescription = "Ảnh bìa ${trip.title}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(trip.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${trip.destination} • ${DeviceTelemetryHelper.formatDateDisplayVi(trip.startDateMs)} – ${
                        DeviceTelemetryHelper.formatDateDisplayVi(trip.endDateMs)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!trip.notes.isNullOrBlank()) {
                    Text(trip.notes, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    text = "Các check-in trong chuyến đi (${checkIns.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (checkIns.isEmpty()) {
                    Text(
                        text = "Chưa có check-in nào gắn với chuyến đi này.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    checkIns.forEach { ck ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCheckIn(ck) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!ck.primaryPhotoBase64.isNullOrBlank()) {
                                    Base64PhotoImage(
                                        base64 = ck.primaryPhotoBase64,
                                        contentDescription = ck.placeName,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ck.placeName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${ck.authorName} • ${DeviceTelemetryHelper.formatDateDisplayVi(ck.capturedAtMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("Đóng") }
                }
            }
        }
    }
}
