package com.zynexbd.crmsolution.activities

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.TimelineAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminRouteHistoryBinding
import com.zynexbd.crmsolution.models.Customer
import com.zynexbd.crmsolution.models.LocationResponse
import com.zynexbd.crmsolution.models.OfficeLocation
import com.zynexbd.crmsolution.models.User
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.utils.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminRouteHistoryActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityAdminRouteHistoryBinding
    private var googleMap: GoogleMap? = null
    private var users: List<User> = emptyList()
    private var customers: List<Customer> = emptyList()
    private var offices: List<OfficeLocation> = emptyList()

    private var selectedDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private var targetUserId: Int = -1

    private var processResult: FullDayProcessResult? = null
    private var multiRouteResult: MultiSegmentRouteResult? = null
    private val placedMarkers = mutableListOf<Marker>()
    private var lastPointMarker: Marker? = null

    // Timeline
    private lateinit var timelineAdapter: TimelineAdapter

    // Route Replay
    private var replayMarker: Marker? = null
    private var isReplayPlaying: Boolean = false
    private var replaySpeedMultiplier: Float = 1.0f
    private var replayJob: Job? = null
    private var replayPoints: List<ProcessedPoint> = emptyList()
    private var currentReplayIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminRouteHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetUserId = intent.getIntExtra("USER_ID", -1)

        try {
            com.google.android.gms.maps.MapsInitializer.initialize(applicationContext, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST, null)
        } catch (_: Exception) {}

        setupUI()

        binding.root.post {
            var mapFrag = supportFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            if (mapFrag == null) {
                mapFrag = SupportMapFragment.newInstance()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, mapFrag)
                    .commitAllowingStateLoss()
            }
            mapFrag.getMapAsync(this@AdminRouteHistoryActivity)
            loadInitialData()
        }
    }

    private fun setupUI() {
        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_route_history)

        binding.editDate.setText(selectedDate)
        binding.editDate.setOnClickListener { showDatePicker() }
        binding.buttonSearch.setOnClickListener { searchRoute() }

        // Search Controls Toggle
        binding.buttonToggleSearch.setOnClickListener {
            val isVisible = binding.cardSearchControls.visibility == View.VISIBLE
            binding.cardSearchControls.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        // Timeline Setup
        timelineAdapter = TimelineAdapter { event ->
            onTimelineEventClicked(event)
        }
        binding.recyclerTimeline.layoutManager = LinearLayoutManager(this)
        binding.recyclerTimeline.adapter = timelineAdapter

        binding.buttonViewTimeline.setOnClickListener { showTimelineView() }
        binding.buttonQuickTimeline.setOnClickListener { showTimelineView() }
        binding.buttonCloseTimeline.setOnClickListener {
            binding.cardTimeline.visibility = View.GONE
            binding.cardSummary.visibility = View.VISIBLE
        }

        // Replay Setup
        binding.buttonStartReplay.setOnClickListener { startReplayView() }
        binding.buttonQuickReplay.setOnClickListener { startReplayView() }
        binding.buttonCloseReplay.setOnClickListener { stopReplayAndShowSummary() }

        binding.buttonPlayPause.setOnClickListener { toggleReplayPlayPause() }

        // Speed Multipliers
        binding.btnSpeed1x.setOnClickListener { setReplaySpeed(1.0f, binding.btnSpeed1x) }
        binding.btnSpeed2x.setOnClickListener { setReplaySpeed(2.0f, binding.btnSpeed2x) }
        binding.btnSpeed5x.setOnClickListener { setReplaySpeed(5.0f, binding.btnSpeed5x) }
        binding.btnSpeed10x.setOnClickListener { setReplaySpeed(10.0f, binding.btnSpeed10x) }

        // Time Slider
        binding.seekBarReplay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && replayPoints.isNotEmpty()) {
                    currentReplayIndex = progress.coerceIn(0, replayPoints.size - 1)
                    updateReplayMarkerPosition(currentReplayIndex, animateCamera = false)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                pauseReplay()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Resume or remain paused
            }
        })
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.isTrafficEnabled = true
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@AdminRouteHistoryActivity)

                val usersDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                    try { api.getUsers() } catch (_: Exception) { null }
                }
                val custDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                    try { api.getCustomers() } catch (_: Exception) { null }
                }
                val offDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                    try { api.getOfficeLocations() } catch (_: Exception) { null }
                }

                val usersResp = usersDeferred.await()
                if (usersResp?.isSuccessful == true) {
                    users = usersResp.body().orEmpty()
                    val names = users.map { it.name.ifBlank { it.username } }
                    binding.spinnerUser.adapter = ArrayAdapter(
                        this@AdminRouteHistoryActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        names
                    )

                    if (targetUserId != -1) {
                        val index = users.indexOfFirst { it.id == targetUserId }
                        if (index >= 0) {
                            binding.spinnerUser.setSelection(index)
                            searchRoute()
                        }
                    }
                }

                val custResp = custDeferred.await()
                if (custResp?.isSuccessful == true) {
                    customers = custResp.body().orEmpty()
                }

                val offResp = offDeferred.await()
                if (offResp?.isSuccessful == true) {
                    offices = offResp.body().orEmpty()
                }

            } catch (e: Exception) {
                Toast.makeText(this@AdminRouteHistoryActivity, "Failed to initialize master data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun convertBengaliToAsciiDigits(input: String): String {
        val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        var result = input
        for (i in 0..9) {
            result = result.replace(bengaliDigits[i], ('0' + i))
        }
        return result
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                binding.editDate.setText(selectedDate)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun searchRoute() {
        stopReplay()

        val selectedIndex = binding.spinnerUser.selectedItemPosition
        val selectedUser = users.getOrNull(selectedIndex)
        if (selectedUser == null) {
            Toast.makeText(this, "Please select a driver.", Toast.LENGTH_SHORT).show()
            return
        }

        val rawDateText = binding.editDate.text.toString().trim()
        val queryDate = if (rawDateText.isNotBlank()) convertBengaliToAsciiDigits(rawDateText) else selectedDate

        binding.buttonSearch.isEnabled = false
        binding.cardSummary.visibility = View.VISIBLE
        binding.cardTimeline.visibility = View.GONE
        binding.cardReplay.visibility = View.GONE
        binding.layoutMapFloatingActions.visibility = View.GONE

        binding.textRouteTitle.text = "Route: ${selectedUser.name.ifBlank { selectedUser.username }}"
        binding.textRouteDetails.text = "Loading full-day travel history & computing movement segments..."

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@AdminRouteHistoryActivity)
                val resp = api.getRouteHistory(selectedUser.id, queryDate)
                binding.buttonSearch.isEnabled = true

                if (resp.isSuccessful) {
                    val rawLocs = resp.body().orEmpty().filter { it.latitude != null && it.longitude != null }

                    if (rawLocs.isEmpty()) {
                        Toast.makeText(
                            this@AdminRouteHistoryActivity,
                            "No route recorded for ${selectedUser.username} on $selectedDate.",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.cardSummary.visibility = View.GONE
                        binding.layoutMapFloatingActions.visibility = View.GONE
                        googleMap?.clear()
                        placedMarkers.clear()
                    } else {
                        processAndRenderFullDayHistory(selectedUser, queryDate, rawLocs)
                    }
                } else {
                    Toast.makeText(this@AdminRouteHistoryActivity, "Failed to fetch route data.", Toast.LENGTH_SHORT).show()
                    binding.cardSummary.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.buttonSearch.isEnabled = true
                binding.cardSummary.visibility = View.GONE
                Toast.makeText(this@AdminRouteHistoryActivity, e.message ?: "Network error.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processAndRenderFullDayHistory(
        user: User,
        dateStr: String,
        rawLocations: List<LocationResponse>
    ) {
        val map = googleMap ?: return
        map.clear()
        placedMarkers.clear()

        val displayName = user.name.ifBlank { user.username }

        lifecycleScope.launch {
            // 1. Process GPS Data (Filtering, Gaps, Stops, Place Matching & Timeline)
            val result = GpsRouteProcessor.process(
                userName = displayName,
                dateStr = dateStr,
                rawLocations = rawLocations,
                customers = customers,
                offices = offices
            )
            processResult = result
            replayPoints = result.validPoints

            // 2. Segment-wise Road Snapping via OSRM (Independent movement routing)
            val multiSnapResult = RoadRoutingHelper.routeMovementSegments(result.movementSegments)
            multiRouteResult = multiSnapResult

            // Update Summary distance metrics
            val summary = result.summary
            summary.totalDistanceKm = multiSnapResult.totalDistanceKm
            summary.roadDistanceKm = multiSnapResult.totalRoadDistanceKm
            summary.rawGpsDistanceKm = multiSnapResult.totalRawDistanceKm
            summary.routeModeTag = when {
                multiSnapResult.isFullySnapped -> "🛣️ 100% Road Snapped"
                multiSnapResult.snappedCount > 0 -> "🛣️ Road + 📍 GPS Fallback"
                else -> "📍 GPS Path"
            }

            // 3. Render Segment Polylines on Google Map
            renderSegmentPolylines(map, multiSnapResult, result.validPoints)

            // 4. Place START, STOP, VISIT & LAST Markers
            renderMapEventMarkers(map, user, result)

            // 5. Populate Full-Day Summary Card
            updateSummaryCardUI(summary)

            // 6. Update Timeline Adapter
            timelineAdapter.setEvents(result.timelineEvents)

            // Show floating quick-access buttons
            binding.layoutMapFloatingActions.visibility = View.VISIBLE

            // 7. Auto-fit Map Camera Bounds
            fitMapBounds(map, result.validPoints)
        }
    }

    private fun renderSegmentPolylines(
        map: GoogleMap,
        multiSnap: MultiSegmentRouteResult,
        fallbackPoints: List<ProcessedPoint>
    ) {
        val allSegments = if (multiSnap.segmentRoutes.isNotEmpty()) {
            multiSnap.segmentRoutes.map { it.points }
        } else if (fallbackPoints.size >= 2) {
            listOf(fallbackPoints.map { it.latLng })
        } else {
            emptyList()
        }

        for (pts in allSegments) {
            if (pts.size < 2) continue

            // 1. Soft Ambient Drop Shadow Layer
            map.addPolyline(
                PolylineOptions()
                    .addAll(pts)
                    .width(22f)
                    .color(Color.parseColor("#300F172A"))
                    .jointType(JointType.ROUND)
                    .startCap(RoundCap())
                    .endCap(RoundCap())
                    .geodesic(true)
                    .zIndex(4f)
            )

            // 2. High-contrast crisp white casing outline
            map.addPolyline(
                PolylineOptions()
                    .addAll(pts)
                    .width(16f)
                    .color(Color.parseColor("#FFFFFF"))
                    .jointType(JointType.ROUND)
                    .startCap(RoundCap())
                    .endCap(RoundCap())
                    .geodesic(true)
                    .zIndex(5f)
            )

            // 3. Bold Vibrant Electric Blue core route line
            map.addPolyline(
                PolylineOptions()
                    .addAll(pts)
                    .width(10.5f)
                    .color(Color.parseColor("#2563EB"))
                    .jointType(JointType.ROUND)
                    .startCap(RoundCap())
                    .endCap(RoundCap())
                    .geodesic(true)
                    .zIndex(6f)
            )

            // 4. Direction Arrow Chevrons along route
            val step = (pts.size / 6).coerceIn(8, 22)
            val chevronIcon = createDirectionChevronBitmap()
            for (i in step until pts.size - 1 step step) {
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val bearing = calculateBearing(p1, p2)
                val chevronMarker = map.addMarker(
                    MarkerOptions()
                        .position(p1)
                        .icon(chevronIcon)
                        .anchor(0.5f, 0.5f)
                        .rotation(bearing)
                        .flat(true)
                        .zIndex(7f)
                )
                if (chevronMarker != null) placedMarkers.add(chevronMarker)
            }
        }
    }

    private fun renderMapEventMarkers(
        map: GoogleMap,
        user: User,
        result: FullDayProcessResult
    ) {
        val displayName = user.name.ifBlank { user.username }
        val points = result.validPoints

        if (points.isEmpty()) return

        if (points.size == 1) {
            val single = points.first()
            val batteryText = if (single.battery != null) " | 🔋 ${single.battery}%" else ""
            val speedText = if (single.speed != null && single.speed > 0) " | 🚗 %.1f km/h".format(single.speed) else ""
            val timeStr = GpsRouteProcessor.formatLocalTime(single.timeMillis)
            val addrText = if (!single.originalResponse.locationAddress.isNullOrBlank()) "📍 ${single.originalResponse.locationAddress} | " else ""

            val marker = map.addMarker(
                MarkerOptions()
                    .position(single.latLng)
                    .title("🔴 Current Point: $displayName")
                    .snippet("${addrText}Time: $timeStr$batteryText$speedText")
                    .icon(createPillMarker("🔴", "LAST POINT", "$displayName • $timeStr", Color.parseColor("#DC2626")))
                    .zIndex(10f)
            )
            marker?.showInfoWindow()
            lastPointMarker = marker
            if (marker != null) placedMarkers.add(marker)
            return
        }

        // 🟢 START Marker (Green Glossy Badge)
        val startPt = points.first()
        val startTimeStr = GpsRouteProcessor.formatLocalTime(startPt.timeMillis)
        val startBattery = if (startPt.battery != null) " | 🔋 ${startPt.battery}%" else ""
        val startMatched = GpsRouteProcessor.matchLocationToPlace(startPt.latLng, customers, offices)
        val startLocationName = startPt.originalResponse.locationAddress?.takeIf { it.isNotBlank() } ?: startMatched?.first ?: "Starting Location"

        val startMarker = map.addMarker(
            MarkerOptions()
                .position(startPt.latLng)
                .title("🟢 START: $displayName ($startTimeStr)")
                .snippet("📍 $startLocationName$startBattery")
                .icon(createPillMarker("🏁", "START", startTimeStr, Color.parseColor("#059669")))
                .zIndex(12f)
        )
        if (startMarker != null) placedMarkers.add(startMarker)

        // 📍 Customer / Office / Long Stop Markers (Numbered custom badges)
        var stopIndex = 1
        for (stop in result.stops) {
            val durStr = GpsRouteProcessor.formatDuration(stop.durationMinutes)
            val arrStr = GpsRouteProcessor.formatLocalTime(stop.arrivalTimeMillis)
            val depStr = GpsRouteProcessor.formatLocalTime(stop.departureTimeMillis)
            val stopAddr = stop.points.firstOrNull { !it.originalResponse.locationAddress.isNullOrBlank() }?.originalResponse?.locationAddress
            val addrPrefix = if (!stopAddr.isNullOrBlank()) "📍 $stopAddr | " else ""

            val (icon, label, sub, badgeColor, title, snippet) = when (stop.matchedPlaceType) {
                PlaceType.CUSTOMER -> {
                    val custName = stop.matchedPlaceName?.take(18) ?: "Customer"
                    Tuple6(
                        "🏢",
                        "VISIT $stopIndex: $custName",
                        "Stay: $durStr ($arrStr)",
                        Color.parseColor("#2563EB"),
                        "📍 Customer Visit: ${stop.matchedPlaceName}",
                        "${addrPrefix}${stop.matchedPlaceDetails ?: "Customer"} | Stayed: $durStr ($arrStr - $depStr)"
                    )
                }
                PlaceType.OFFICE -> {
                    val offName = stop.matchedPlaceName?.take(18) ?: "Office"
                    Tuple6(
                        "🏛️",
                        "OFFICE: $offName",
                        "Stay: $durStr ($arrStr)",
                        Color.parseColor("#D97706"),
                        "🟠 Office: ${stop.matchedPlaceName}",
                        "${addrPrefix}Stayed: $durStr ($arrStr - $depStr)"
                    )
                }
                else -> {
                    if (stop.durationMinutes >= GpsProcessingConfig.LONG_STOP_THRESHOLD_MINUTES) {
                        Tuple6(
                            "⏸️",
                            "STOP #$stopIndex",
                            "Stay: $durStr ($arrStr)",
                            Color.parseColor("#E11D48"),
                            "🟡 Long Stop (${durStr})",
                            "${addrPrefix}Stayed: $durStr ($arrStr - $depStr)"
                        )
                    } else {
                        Tuple6(
                            "🅿️",
                            "STOP #$stopIndex",
                            "Stay: $durStr",
                            Color.parseColor("#475569"),
                            "⚪ Short Stop (${durStr})",
                            "${addrPrefix}Stayed: $durStr ($arrStr - $depStr)"
                        )
                    }
                }
            }

            val stopMarker = map.addMarker(
                MarkerOptions()
                    .position(stop.centroid)
                    .title(title)
                    .snippet(snippet)
                    .icon(createPillMarker(icon, label, sub, badgeColor))
                    .zIndex(9f)
            )
            if (stopMarker != null) placedMarkers.add(stopMarker)
            stopIndex++
        }

        // 🔴 LAST POINT Marker (Red Glossy Badge)
        val endPt = points.last()
        val endTimeStr = GpsRouteProcessor.formatLocalTime(endPt.timeMillis)
        val endBattery = if (endPt.battery != null) " | 🔋 ${endPt.battery}%" else ""
        val endSpeed = if (endPt.speed != null && endPt.speed > 0) " | 🚗 %.1f km/h".format(endPt.speed) else ""
        val endMatched = GpsRouteProcessor.matchLocationToPlace(endPt.latLng, customers, offices)
        val endLocationName = endPt.originalResponse.locationAddress?.takeIf { it.isNotBlank() } ?: endMatched?.first ?: "Last Recorded Location"

        val endMarker = map.addMarker(
            MarkerOptions()
                .position(endPt.latLng)
                .title("🔴 FINISH: $displayName ($endTimeStr)")
                .snippet("📍 $endLocationName$endBattery$endSpeed")
                .icon(createPillMarker("🏁", "FINISH", endTimeStr, Color.parseColor("#DC2626")))
                .zIndex(12f)
        )
        endMarker?.showInfoWindow()
        lastPointMarker = endMarker
        if (endMarker != null) placedMarkers.add(endMarker)
    }

    private data class Tuple6<A, B, C, D, E, F>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
    )

    private fun createPillMarker(
        iconText: String,
        labelText: String,
        subText: String? = null,
        bgColor: Int
    ): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11.5f * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            textSize = 9.5f * density
        }

        val mainTextWidth = textPaint.measureText("$iconText $labelText")
        val subTextWidth = if (subText != null) subTextPaint.measureText(subText) else 0f
        val contentWidth = maxOf(mainTextWidth, subTextWidth)

        val paddingH = 9f * density
        val pillWidth = (contentWidth + paddingH * 2).toInt().coerceAtLeast((48 * density).toInt())
        val pillHeight = (if (subText != null) 34f * density else 24f * density).toInt()
        val totalHeight = pillHeight + (7f * density).toInt()

        val bitmap = android.graphics.Bitmap.createBitmap(pillWidth, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Drop shadow
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#35000000")
        }
        val rectShadow = android.graphics.RectF(2f * density, 2f * density, pillWidth - 2f * density, pillHeight.toFloat() + 2f * density)
        canvas.drawRoundRect(rectShadow, 10f * density, 10f * density, shadowPaint)

        // Pill background
        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = android.graphics.Paint.Style.FILL
        }
        val rect = android.graphics.RectF(0f, 0f, pillWidth.toFloat(), pillHeight.toFloat())
        canvas.drawRoundRect(rect, 10f * density, 10f * density, bgPaint)

        // Specular highlight on top half
        val glassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, pillHeight / 2f,
                Color.parseColor("#45FFFFFF"), Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP
            )
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 10f * density, 10f * density, glassPaint)

        // White border
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.3f * density
        }
        canvas.drawRoundRect(rect, 10f * density, 10f * density, borderPaint)

        // Pointer triangle at bottom center
        val pointerPath = android.graphics.Path().apply {
            val cx = pillWidth / 2f
            moveTo(cx - (5f * density), pillHeight.toFloat())
            lineTo(cx + (5f * density), pillHeight.toFloat())
            lineTo(cx, totalHeight - (1f * density))
            close()
        }
        canvas.drawPath(pointerPath, bgPaint)
        canvas.drawPath(pointerPath, borderPaint)

        // Main Text
        val textY = if (subText != null) 14f * density else 16.5f * density
        canvas.drawText("$iconText $labelText", paddingH, textY, textPaint)

        // Sub Text (if any)
        if (subText != null) {
            canvas.drawText(subText, paddingH, textY + (13f * density), subTextPaint)
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun createDirectionChevronBitmap(): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (17 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - (1f * density), circlePaint)

        val arrowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            style = android.graphics.Paint.Style.FILL
        }
        val path = android.graphics.Path().apply {
            val cx = size / 2f
            val cy = size / 2f
            moveTo(cx - (3.5f * density), cy - (4.5f * density))
            lineTo(cx + (4.5f * density), cy)
            lineTo(cx - (3.5f * density), cy + (4.5f * density))
            close()
        }
        canvas.drawPath(path, arrowPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lng1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lng2 = Math.toRadians(end.longitude)

        val dLng = lng2 - lng1
        val y = Math.sin(dLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
        val brng = Math.toDegrees(Math.atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }

    private fun updateSummaryCardUI(summary: FullDayRouteSummary) {
        binding.cardSummary.visibility = View.VISIBLE
        binding.textRouteTitle.text = "Route: ${summary.userName}"
        binding.textRouteModeTag.text = summary.routeModeTag

        binding.textRouteDetails.text = "Waypoints: ${summary.validPointsCount} | Distance: %.2f km | 🚦 Traffic: Live".format(summary.totalDistanceKm)

        // Start and End location address display
        binding.textStartAddress.text = "🟢 Start: ${summary.startAddress ?: "Unknown"} (${summary.startTimeStr})"
        binding.textEndAddress.text = "🔴 End: ${summary.endAddress ?: "Unknown"} (${summary.endTimeStr})"

        binding.textStartEndTime.text = "🟢 ${summary.startTimeStr}  🔴 ${summary.endTimeStr}"
        binding.textVisitsAndGaps.text = "📍 Visits: ${summary.visitCount} | ⚠ Gaps: ${summary.gpsGapCount}"

        val moveStr = GpsRouteProcessor.formatDuration(summary.totalMovingMinutes)
        val stopStr = GpsRouteProcessor.formatDuration(summary.totalStoppedMinutes)
        binding.textMovingStoppedTime.text = "🚗 Move: $moveStr | ⏸ Stop: $stopStr"

        val batText = if (summary.lastBattery != null) "${summary.lastBattery}%" else "--"
        binding.textBatteryAndPoints.text = "🔋 Battery: $batText | Pings: ${summary.validPointsCount}"
    }

    private fun fitMapBounds(map: GoogleMap, points: List<ProcessedPoint>) {
        if (points.isEmpty()) return
        try {
            val boundsBuilder = LatLngBounds.Builder()
            for (pt in points) {
                boundsBuilder.include(pt.latLng)
            }
            val bounds = boundsBuilder.build()
            val padding = 130
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        } catch (_: Exception) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first().latLng, 14f))
        }
    }

    private fun onTimelineEventClicked(event: TimelineEvent) {
        val map = googleMap ?: return
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(event.latLng, 16f))

        // Find matching marker and show info window
        val matchedMarker = placedMarkers.minByOrNull { m ->
            GpsRouteProcessor.distanceBetweenMeters(m.position, event.latLng)
        }
        if (matchedMarker != null && GpsRouteProcessor.distanceBetweenMeters(matchedMarker.position, event.latLng) < 100) {
            matchedMarker.showInfoWindow()
        }
    }

    private fun showTimelineView() {
        binding.cardSummary.visibility = View.GONE
        binding.cardReplay.visibility = View.GONE
        binding.cardTimeline.visibility = View.VISIBLE
    }

    // ==========================================
    // ROUTE REPLAY & TIME SLIDER
    // ==========================================

    private fun startReplayView() {
        val pts = replayPoints
        if (pts.isEmpty()) {
            Toast.makeText(this, "No route points available to replay.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.cardSummary.visibility = View.GONE
        binding.cardTimeline.visibility = View.GONE
        binding.cardReplay.visibility = View.VISIBLE

        binding.textReplayStartTime.text = GpsRouteProcessor.formatLocalTime(pts.first().timeMillis)
        binding.textReplayEndTime.text = GpsRouteProcessor.formatLocalTime(pts.last().timeMillis)

        binding.seekBarReplay.max = (pts.size - 1).coerceAtLeast(1)
        binding.seekBarReplay.progress = 0
        currentReplayIndex = 0

        // Create or reset Replay Marker
        createReplayMarker(pts.first().latLng)

        // Start playback
        isReplayPlaying = true
        binding.buttonPlayPause.text = "⏸ PAUSE"
        startReplayLoop()
    }

    private fun createReplayMarker(position: LatLng) {
        val map = googleMap ?: return
        replayMarker?.remove()
        replayMarker = map.addMarker(
            MarkerOptions()
                .position(position)
                .title("🚗 Replaying Route")
                .icon(createReplayVehicleBitmap())
                .anchor(0.5f, 0.5f)
                .flat(true)
                .zIndex(20f)
        )
    }

    private fun createReplayVehicleBitmap(): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (38 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Outer glow
        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#407C3AED")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, glowPaint)

        // Inner circle
        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7C3AED")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 13f * density, bgPaint)

        // White border
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawCircle(size / 2f, size / 2f, 13f * density, borderPaint)

        // Navigation Chevron Pointer
        val arrowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val path = android.graphics.Path().apply {
            val cx = size / 2f
            val cy = size / 2f
            moveTo(cx, cy - (7f * density))
            lineTo(cx + (5.5f * density), cy + (5.5f * density))
            lineTo(cx, cy + (2.5f * density))
            lineTo(cx - (5.5f * density), cy + (5.5f * density))
            close()
        }
        canvas.drawPath(path, arrowPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun toggleReplayPlayPause() {
        if (isReplayPlaying) {
            pauseReplay()
        } else {
            resumeReplay()
        }
    }

    private fun pauseReplay() {
        isReplayPlaying = false
        binding.buttonPlayPause.text = "▶ PLAY"
        replayJob?.cancel()
    }

    private fun resumeReplay() {
        if (replayPoints.isEmpty()) return
        if (currentReplayIndex >= replayPoints.size - 1) {
            currentReplayIndex = 0
        }
        isReplayPlaying = true
        binding.buttonPlayPause.text = "⏸ PAUSE"
        startReplayLoop()
    }

    private fun startReplayLoop() {
        replayJob?.cancel()
        replayJob = lifecycleScope.launch {
            while (isActive && isReplayPlaying && currentReplayIndex < replayPoints.size) {
                updateReplayMarkerPosition(currentReplayIndex, animateCamera = true)
                binding.seekBarReplay.progress = currentReplayIndex

                val baseDelay = 600L
                val calculatedDelay = (baseDelay / replaySpeedMultiplier).toLong().coerceAtLeast(60L)
                delay(calculatedDelay)

                currentReplayIndex++
            }

            if (currentReplayIndex >= replayPoints.size) {
                isReplayPlaying = false
                binding.buttonPlayPause.text = "▶ RESTART"
            }
        }
    }

    private fun updateReplayMarkerPosition(index: Int, animateCamera: Boolean) {
        val pts = replayPoints
        if (index !in pts.indices) return
        val pt = pts[index]

        replayMarker?.position = pt.latLng

        // Smooth heading rotation
        if (index < pts.size - 1) {
            val nextPt = pts[index + 1]
            val bearing = calculateBearing(pt.latLng, nextPt.latLng)
            replayMarker?.rotation = bearing
        }

        // Status Text
        val timeStr = GpsRouteProcessor.formatLocalTime(pt.timeMillis)
        val speedStr = if (pt.speed != null && pt.speed > 0) "🚗 %.1f km/h".format(pt.speed) else "📍 Stationary"
        val batteryStr = if (pt.battery != null) " | 🔋 ${pt.battery}%" else ""
        binding.textReplayStatus.text = "$timeStr | $speedStr$batteryStr"

        if (animateCamera) {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(pt.latLng))
        }
    }

    private fun setReplaySpeed(multiplier: Float, selectedButton: View) {
        replaySpeedMultiplier = multiplier

        val buttons = listOf(binding.btnSpeed1x, binding.btnSpeed2x, binding.btnSpeed5x, binding.btnSpeed10x)
        for (btn in buttons) {
            if (btn == selectedButton) {
                btn.setBackgroundResource(R.drawable.bg_gradient_button)
                btn.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
            } else {
                btn.setBackgroundResource(0)
                btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun stopReplay() {
        isReplayPlaying = false
        replayJob?.cancel()
        replayMarker?.remove()
        replayMarker = null
    }

    private fun stopReplayAndShowSummary() {
        stopReplay()
        binding.cardReplay.visibility = View.GONE
        binding.cardSummary.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        stopReplay()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
