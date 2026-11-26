package com.example.ui.map

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CheckIn
import com.example.data.model.ImportantPlace
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryEntry
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

enum class MapFocusRequest {
    NONE,
    ME,
    PARTNER,
    BOTH
}

@Composable
fun OsmMapView(
    myLocation: LiveLocation?,
    partnerLocation: LiveLocation?,
    myName: String,
    partnerName: String,
    partnerStaleOrOffline: Boolean = false,
    places: List<ImportantPlace> = emptyList(),
    historyPoints: List<LocationHistoryEntry> = emptyList(),
    checkIns: List<CheckIn> = emptyList(),
    focusRequest: MapFocusRequest = MapFocusRequest.NONE,
    focusTrigger: Int = 0,
    onMapLongClick: ((Double, Double) -> Unit)? = null,
    onCheckInMarkerClick: ((CheckIn) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(10.7769, 106.7009))
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Center once when locations first become available
    val initialCentered = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(myLocation?.latitude, partnerLocation?.latitude) {
        if (!initialCentered.value) {
            val myPt = myLocation?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
                ?.let { GeoPoint(it.latitude, it.longitude) }
            val partnerPt = partnerLocation?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
                ?.let { GeoPoint(it.latitude, it.longitude) }
            when {
                myPt != null && partnerPt != null -> {
                    val box = BoundingBox.fromGeoPoints(listOf(myPt, partnerPt)).increaseByScale(1.35f)
                    mapView.post { mapView.zoomToBoundingBox(box, true, 100) }
                    initialCentered.value = true
                }
                myPt != null -> {
                    mapView.controller.animateTo(myPt, 16.0, 600L)
                    initialCentered.value = true
                }
                partnerPt != null -> {
                    mapView.controller.animateTo(partnerPt, 16.0, 600L)
                    initialCentered.value = true
                }
            }
        }
    }

    LaunchedEffect(focusTrigger) {
        if (focusTrigger == 0) return@LaunchedEffect
        val myPt = myLocation?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
            ?.let { GeoPoint(it.latitude, it.longitude) }
        val partnerPt = partnerLocation?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
            ?.let { GeoPoint(it.latitude, it.longitude) }
        when (focusRequest) {
            MapFocusRequest.ME -> myPt?.let { mapView.controller.animateTo(it, 16.5, 500L) }
            MapFocusRequest.PARTNER -> partnerPt?.let { mapView.controller.animateTo(it, 16.5, 500L) }
            MapFocusRequest.BOTH -> {
                val pts = listOfNotNull(myPt, partnerPt)
                if (pts.size == 2) {
                    val box = BoundingBox.fromGeoPoints(pts).increaseByScale(1.35f)
                    mapView.zoomToBoundingBox(box, true, 120)
                } else if (pts.size == 1) {
                    mapView.controller.animateTo(pts.first(), 16.0, 500L)
                }
            }
            MapFocusRequest.NONE -> {}
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()

            if (onMapLongClick != null) {
                val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(
                    object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) onMapLongClick(p.latitude, p.longitude)
                            return true
                        }
                    }
                )
                map.overlays.add(eventsOverlay)
            }

            // Geofence circles for Important Places
            places.forEach { place ->
                val center = GeoPoint(place.latitude, place.longitude)
                val circle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(center, place.radiusMeters.toDouble())
                    fillPaint.color = Color.argb(35, 14, 107, 110)
                    outlinePaint.color = Color.argb(170, 14, 107, 110)
                    outlinePaint.strokeWidth = 3f
                    title = "${place.name} (${place.radiusMeters}m)"
                }
                map.overlays.add(circle)

                val placeMarker = Marker(map).apply {
                    position = center
                    title = place.name
                    snippet = "${place.parsedCategory.labelVi} • Bán kính ${place.radiusMeters}m"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createCircleDrawable(Color.parseColor("#0E6B6E"), 24)
                }
                map.overlays.add(placeMarker)
            }

            // Movement history route polyline & stop points
            if (historyPoints.isNotEmpty()) {
                val grouped = historyPoints.groupBy { it.userId }
                grouped.forEach { (_, pts) ->
                    val geoPts = pts.map { GeoPoint(it.latitude, it.longitude) }
                    val line = Polyline(map).apply {
                        setPoints(geoPts)
                        outlinePaint.color = Color.parseColor("#E0485A")
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    }
                    map.overlays.add(line)

                    pts.filter { it.stopPoint }.forEach { stop ->
                        val stopMarker = Marker(map).apply {
                            position = GeoPoint(stop.latitude, stop.longitude)
                            title = "Điểm dừng (${stop.stopDurationMinutes} phút)"
                            snippet = stop.address ?: ""
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createCircleDrawable(Color.parseColor("#B76800"), 26)
                        }
                        map.overlays.add(stopMarker)
                    }
                }
            }

            // Memory Check-in Markers
            checkIns.forEach { ck ->
                val ckMarker = Marker(map).apply {
                    position = GeoPoint(ck.latitude, ck.longitude)
                    title = ck.placeName
                    snippet = "${ck.authorName}: ${ck.note ?: "Check-in kỷ niệm"}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createCircleDrawable(Color.parseColor("#D97706"), 34)
                    if (onCheckInMarkerClick != null) {
                        setOnMarkerClickListener { _, _ ->
                            onCheckInMarkerClick(ck)
                            true
                        }
                    }
                }
                map.overlays.add(ckMarker)
            }

            // My Marker
            myLocation?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let { loc ->
                val pt = GeoPoint(loc.latitude, loc.longitude)
                val accuracyCircle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(pt, loc.accuracyMeters.coerceIn(5.0, 200.0))
                    fillPaint.color = Color.argb(28, 14, 107, 110)
                    outlinePaint.color = Color.argb(100, 14, 107, 110)
                    outlinePaint.strokeWidth = 2f
                }
                map.overlays.add(accuracyCircle)

                val myMarker = Marker(map).apply {
                    position = pt
                    title = "$myName (Bạn)"
                    snippet = loc.address ?: "Độ chính xác ±${loc.accuracyMeters.toInt()}m"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createCircleDrawable(Color.parseColor("#0E6B6E"), 42)
                }
                map.overlays.add(myMarker)
            }

            // Partner Marker
            partnerLocation?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let { loc ->
                val pt = GeoPoint(loc.latitude, loc.longitude)
                val color = if (partnerStaleOrOffline || loc.gpsInterrupted) {
                    Color.parseColor("#6B7280")
                } else {
                    Color.parseColor("#E0485A")
                }
                val accuracyCircle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(pt, loc.accuracyMeters.coerceIn(5.0, 250.0))
                    fillPaint.color = Color.argb(30, 224, 72, 90)
                    outlinePaint.color = Color.argb(110, 224, 72, 90)
                    outlinePaint.strokeWidth = 2f
                }
                map.overlays.add(accuracyCircle)

                val partnerMarker = Marker(map).apply {
                    position = pt
                    title = if (partnerStaleOrOffline) "$partnerName (Vị trí cuối)" else partnerName
                    snippet = loc.address ?: "Độ chính xác ±${loc.accuracyMeters.toInt()}m"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createCircleDrawable(color, 46)
                }
                map.overlays.add(partnerMarker)
            }

            map.invalidate()
        }
    )
}

private fun createCircleDrawable(fillColor: Int, sizePx: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
        setStroke(5, Color.WHITE)
        setSize(sizePx, sizePx)
    }
