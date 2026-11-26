package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.firebase.AuthRepository
import com.example.data.firebase.LocationRepository
import com.example.data.firebase.PairingRepository
import com.example.data.firebase.SocialMemoryRepository
import com.example.data.firebase.UiState
import com.example.data.model.ActivityType
import com.example.data.model.Connection
import com.example.data.model.SharingMode
import com.example.location.LocationTrackingService
import com.example.location.NotificationHelper
import com.example.ui.activity.ActivityTabScreen
import com.example.ui.activity.LocationHistoryScreen
import com.example.ui.activity.NotificationsInboxScreen
import com.example.ui.activity.PlacesManagerScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.map.MapTabScreen
import com.example.ui.memories.CameraCheckInScreen
import com.example.ui.memories.MemoriesTabScreen
import com.example.ui.pairing.PairingScreen
import com.example.ui.profile.ProfileTabScreen
import com.example.ui.viewmodel.TogetherViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
object HomeTabsRoute

@Serializable
object CameraCheckInRoute

@Serializable
object LocationHistoryRoute

@Serializable
object PlacesManagerRoute

@Serializable
object NotificationsRoute

enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    MAP("Bản đồ", Icons.Rounded.Map, "tab_map"),
    ACTIVITY("Hoạt động", Icons.Rounded.DirectionsRun, "tab_activity"),
    MEMORIES("Kỷ niệm", Icons.Rounded.PhotoLibrary, "tab_memories"),
    PROFILE("Cá nhân", Icons.Rounded.Person, "tab_profile")
}

@Composable
fun AppNavigation(
    initialInviteCode: String?,
    onClearInitialInviteCode: () -> Unit
) {
    val currentUser by AuthRepository.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedFlow(
                uid = user.uid,
                initialInviteCode = initialInviteCode,
                onClearInitialInviteCode = onClearInitialInviteCode
            )
        }
    }
}

@Composable
private fun AuthenticatedFlow(
    uid: String,
    initialInviteCode: String?,
    onClearInitialInviteCode: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TogetherViewModel = viewModel(
        key = uid,
        factory = remember(uid, appContext) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = FirebaseFirestore.getInstance(
                        appContext.getString(R.string.firestore_database_id)
                    )
                    return TogetherViewModel(
                        pairingRepo = PairingRepository(db),
                        locationRepo = LocationRepository(db),
                        socialRepo = SocialMemoryRepository(db),
                        currentUserId = uid
                    ) as T
                }
            }
        }
    )

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.uiMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val activeConnectionState by viewModel.activeConnectionState.collectAsStateWithLifecycle()

    val performSignOut: () -> Unit = {
        LocationTrackingService.stop(context)
        AuthRepository.signOut(
            credentialManager = credentialManager,
            scope = scope,
            onBeforeSignOut = {
                LocationRepository(appContext).markOfflineOnSignOut()
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { outerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding)
        ) {
            when (val state = activeConnectionState) {
                is UiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Đang đồng bộ không gian riêng...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = performSignOut) {
                            Text("Đăng xuất và thử lại")
                        }
                    }
                }

                is UiState.Success -> {
                    val conn = state.data
                    if (conn == null) {
                        PairingScreen(
                            viewModel = viewModel,
                            initialInviteCode = initialInviteCode,
                            onClearInitialInviteCode = onClearInitialInviteCode,
                            onSignOut = performSignOut
                        )
                    } else {
                        ConnectedAppContent(
                            viewModel = viewModel,
                            connection = conn,
                            onSignOut = performSignOut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedAppContent(
    viewModel: TogetherViewModel,
    connection: Connection,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.MAP) }

    val mySharing by viewModel.mySharingPermission.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    // Ensure foreground location tracking runs when allowed
    LaunchedEffect(connection.id, mySharing?.sharingMode, mySharing?.shareLocation) {
        val perm = mySharing
        val mode = perm?.parsedSharingMode ?: SharingMode.CONTINUOUS
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if ((fine || coarse) && perm?.shareLocation != false && mode != SharingMode.PAUSED && mode != SharingMode.OFF) {
            LocationTrackingService.start(context)
        }
    }

    // Trigger Android system notifications for new real-time partner events
    val sessionStartMs = remember { System.currentTimeMillis() }
    val shownIds = remember { mutableSetOf<String>() }
    LaunchedEffect(notifications, mySharing) {
        val perm = mySharing
        notifications.forEach { item ->
            if (!item.readFlag &&
                item.senderId != viewModel.currentUserId &&
                item.createdAtMs >= sessionStartMs - 5_000L &&
                shownIds.add(item.id)
            ) {
                val allowed = when (ActivityType.fromRaw(item.type)) {
                    ActivityType.ARRIVED_PLACE -> perm?.notifyArrival != false
                    ActivityType.LEFT_PLACE -> perm?.notifyDeparture != false
                    ActivityType.CHECKIN -> perm?.notifyCheckin != false
                    ActivityType.LOW_BATTERY -> perm?.notifyLowBattery != false
                    ActivityType.LOST_CONNECTION -> perm?.notifyOffline != false
                    ActivityType.BACK_ONLINE -> perm?.notifyOnline != false
                    ActivityType.SHARING_STARTED, ActivityType.SHARING_PAUSED -> perm?.notifySharingChange != false
                    ActivityType.NOTE -> perm?.notifyActivityNote != false
                    else -> true
                }
                if (allowed) {
                    NotificationHelper.showPartnerAlertNotification(
                        context = context,
                        notificationId = item.id.hashCode(),
                        title = item.title,
                        body = item.body
                    )
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeTabsRoute,
        modifier = Modifier.fillMaxSize()
    ) {
        composable<HomeTabsRoute> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                if (useRail) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            MainTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            TabContent(
                                selectedTab = selectedTab,
                                viewModel = viewModel,
                                connection = connection,
                                onOpenCameraCheckIn = { navController.navigate(CameraCheckInRoute) },
                                onOpenHistory = { navController.navigate(LocationHistoryRoute) },
                                onOpenPlaces = { navController.navigate(PlacesManagerRoute) },
                                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                                onSignOut = onSignOut
                            )
                        }
                    }
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                MainTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            TabContent(
                                selectedTab = selectedTab,
                                viewModel = viewModel,
                                connection = connection,
                                onOpenCameraCheckIn = { navController.navigate(CameraCheckInRoute) },
                                onOpenHistory = { navController.navigate(LocationHistoryRoute) },
                                onOpenPlaces = { navController.navigate(PlacesManagerRoute) },
                                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }

        composable<CameraCheckInRoute> {
            CameraCheckInScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable<LocationHistoryRoute> {
            LocationHistoryScreen(
                viewModel = viewModel,
                connection = connection,
                onBack = { navController.popBackStack() }
            )
        }

        composable<PlacesManagerRoute> {
            PlacesManagerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable<NotificationsRoute> {
            NotificationsInboxScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun TabContent(
    selectedTab: MainTab,
    viewModel: TogetherViewModel,
    connection: Connection,
    onOpenCameraCheckIn: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: () -> Unit
) {
    when (selectedTab) {
        MainTab.MAP -> MapTabScreen(
            viewModel = viewModel,
            connection = connection,
            onOpenCheckIn = onOpenCameraCheckIn,
            onOpenHistory = onOpenHistory,
            onOpenPlaces = onOpenPlaces,
            onOpenNotifications = onOpenNotifications
        )

        MainTab.ACTIVITY -> ActivityTabScreen(
            viewModel = viewModel,
            connection = connection,
            onOpenHistory = onOpenHistory,
            onOpenPlaces = onOpenPlaces
        )

        MainTab.MEMORIES -> MemoriesTabScreen(
            viewModel = viewModel,
            connection = connection,
            onOpenCameraCheckIn = onOpenCameraCheckIn
        )

        MainTab.PROFILE -> ProfileTabScreen(
            viewModel = viewModel,
            connection = connection,
            onOpenHistory = onOpenHistory,
            onSignOut = onSignOut
        )
    }
}
