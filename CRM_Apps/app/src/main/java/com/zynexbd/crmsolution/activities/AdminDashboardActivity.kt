package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityAdminDashboardBinding
import com.zynexbd.crmsolution.models.LocationResponse
import com.zynexbd.crmsolution.utils.GpsRouteProcessor
import com.zynexbd.crmsolution.utils.SessionManager
import com.zynexbd.crmsolution.viewmodel.AdminMapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin live Google Map dashboard with status-coded markers,
 * SignalR real-time updates, and interactive driver details bottom sheet.
 */
class AdminDashboardActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var viewModel: AdminMapViewModel
    private lateinit var session: SessionManager
    private var googleMap: GoogleMap? = null
    private val markers = mutableMapOf<Int, Marker>()
    private val latestLocationsMap = mutableMapOf<Int, LocationResponse>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        viewModel = ViewModelProvider(this)[AdminMapViewModel::class.java]

        try {
            com.google.android.gms.maps.MapsInitializer.initialize(applicationContext, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST, null)
        } catch (_: Exception) {}

        setupAdminDrawer(binding.drawerLayout, binding.navigationView, binding.buttonMenu, R.id.nav_map)

        binding.root.post {
            var mapFrag = supportFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            if (mapFrag == null) {
                mapFrag = SupportMapFragment.newInstance()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, mapFrag)
                    .commitAllowingStateLoss()
            }
            mapFrag.getMapAsync(this@AdminDashboardActivity)
        }

        binding.cardQuickRoute.setOnClickListener {
            startActivity(Intent(this, AdminRouteHistoryActivity::class.java))
        }
        binding.buttonQuickRouteHistory.setOnClickListener {
            startActivity(Intent(this, AdminRouteHistoryActivity::class.java))
        }
        binding.buttonQuickUserDirectory.setOnClickListener {
            startActivity(Intent(this, UserManagementActivity::class.java))
        }
        binding.buttonQuickAttendance.setOnClickListener {
            startActivity(Intent(this, AdminAttendanceActivity::class.java))
        }
        binding.buttonQuickLeave.setOnClickListener {
            startActivity(Intent(this, AdminLeaveActivity::class.java))
        }

        // Live Users List Triggers (Floating button & summary cards)
        binding.buttonFloatingLiveUsers.setOnClickListener {
            showLiveUsersBottomSheet()
        }
        binding.cardActiveDrivers.setOnClickListener {
            showLiveUsersBottomSheet()
        }
        binding.cardTotalDrivers.setOnClickListener {
            showLiveUsersBottomSheet()
        }

        viewModel.locations.observe(this) { locations -> renderMarkers(locations.values) }
        viewModel.connected.observe(this) { connected ->
            binding.textConnectionStatus.text = if (connected) "Live" else "Reconnecting..."
        }

        com.zynexbd.crmsolution.utils.AppUpdateHelper.checkForUpdate(this, lifecycleScope)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.navigationView.setCheckedItem(R.id.nav_map)
        if (googleMap != null) {
            viewModel.start()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.isTrafficEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isZoomControlsEnabled = true
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(23.8103, 90.4125), 11f))
        map.setOnMarkerClickListener { marker ->
            val userId = marker.tag as? Int
            if (userId != null) {
                latestLocationsMap[userId]?.let { loc -> showDriverBottomSheet(loc) }
            }
            true
        }
        viewModel.start()
    }

    private fun renderMarkers(locations: Collection<LocationResponse>) {
        val activeCount = locations.count { it.isOnline }
        binding.textActiveDriversCount.text = "$activeCount Active"
        binding.textTotalDriversCount.text = "${locations.size} Employees"
        binding.textFloatingLiveUsersCount.text = "👥 Live Employees (${locations.size})"

        val map = googleMap ?: return
        for (loc in locations) {
            latestLocationsMap[loc.userId] = loc
            val lat = loc.latitude ?: continue
            val lng = loc.longitude ?: continue
            val position = LatLng(lat, lng)
            val existing = markers[loc.userId]

            val isMoving = (loc.speed ?: 0.0) > 2.0
            val displayName = loc.name?.ifBlank { loc.username } ?: loc.username ?: "Employee #${loc.userId}"
            val statusText = if (loc.isOnline) (if (isMoving) "Moving" else "Idle") else "Offline"
            val titleText = "$displayName ($statusText)"
            val addrSnippet = if (!loc.locationAddress.isNullOrBlank()) "📍 ${loc.locationAddress} • " else ""
            val snippetText = "${addrSnippet}Speed: ${loc.speed ?: 0.0} km/h"

            val customIcon = createUserMarkerBitmap(displayName, loc.isOnline, isMoving)

            if (existing != null) {
                existing.position = position
                existing.title = titleText
                existing.snippet = snippetText
                existing.setIcon(customIcon)
            } else {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(titleText)
                        .snippet(snippetText)
                        .icon(customIcon)
                )
                if (marker != null) {
                    marker.tag = loc.userId
                    markers[loc.userId] = marker
                }
            }
        }
        markers.values.firstOrNull()?.let {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(it.position, 13f))
        }
    }

    private fun createUserMarkerBitmap(
        name: String,
        isOnline: Boolean,
        isMoving: Boolean
    ): com.google.android.gms.maps.model.BitmapDescriptor {
        val (colorTop, colorBottom, glowColor) = when {
            !isOnline -> Triple(
                android.graphics.Color.parseColor("#F87171"), // Rose 400
                android.graphics.Color.parseColor("#DC2626"), // Red 600
                android.graphics.Color.parseColor("#35EF4444")
            )
            isMoving -> Triple(
                android.graphics.Color.parseColor("#34D399"), // Emerald 400
                android.graphics.Color.parseColor("#059669"), // Emerald 600
                android.graphics.Color.parseColor("#3510B981")
            )
            else -> Triple(
                android.graphics.Color.parseColor("#60A5FA"), // Blue 400
                android.graphics.Color.parseColor("#1D4ED8"), // Royal Blue 700
                android.graphics.Color.parseColor("#352563EB")
            )
        }

        val density = resources.displayMetrics.density
        val pinWidth = (52 * density).toInt()
        val pinHeight = (64 * density).toInt()
        val centerX = pinWidth / 2f
        val headRadius = 20f * density
        val headCenterY = headRadius + (3.5f * density)
        val tipY = pinHeight - (4f * density)

        val bitmap = android.graphics.Bitmap.createBitmap(pinWidth, pinHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // 1. Soft Drop Shadow under pin tip
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#30000000")
        }
        canvas.drawOval(
            centerX - (11f * density),
            pinHeight - (7f * density),
            centerX + (11f * density),
            pinHeight - (1f * density),
            shadowPaint
        )

        // 2. Translucent Ambient Glow Halo
        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = glowColor
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(centerX, headCenterY, headRadius + (3.5f * density), glowPaint)

        // 3. 3D Pin Body Path (Smooth Teardrop Map Pin)
        val pinPath = android.graphics.Path().apply {
            arcTo(
                centerX - headRadius,
                headCenterY - headRadius,
                centerX + headRadius,
                headCenterY + headRadius,
                145f,
                250f,
                false
            )
            quadTo(
                centerX + (headRadius * 0.75f),
                headCenterY + (headRadius * 0.9f),
                centerX + (2f * density),
                tipY - (1.5f * density)
            )
            quadTo(centerX, tipY, centerX - (2f * density), tipY - (1.5f * density))
            quadTo(
                centerX - (headRadius * 0.75f),
                headCenterY + (headRadius * 0.9f),
                centerX - (headRadius * 0.82f),
                headCenterY + (headRadius * 0.57f)
            )
            close()
        }

        // 4. Vibrant Glossy Gradient Fill for Pin Body
        val pinGradient = android.graphics.LinearGradient(
            centerX,
            headCenterY - headRadius,
            centerX,
            tipY,
            colorTop,
            colorBottom,
            android.graphics.Shader.TileMode.CLAMP
        )
        val pinPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = pinGradient
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawPath(pinPath, pinPaint)

        // 5. Glossy Rim Stroke / 3D Specular Border
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#60FFFFFF")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        canvas.drawPath(pinPath, borderPaint)

        // 6. Top Specular Glass Sheen Curve (Highlight)
        val glassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                centerX,
                headCenterY - headRadius,
                centerX,
                headCenterY,
                android.graphics.Color.parseColor("#70FFFFFF"),
                android.graphics.Color.parseColor("#00FFFFFF"),
                android.graphics.Shader.TileMode.CLAMP
            )
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(centerX, headCenterY, headRadius - (1f * density), glassPaint)

        // 7. Crisp Inner White Core Badge
        val innerRadius = headRadius - (3.5f * density)
        val innerCirclePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(centerX, headCenterY, innerRadius, innerCirclePaint)

        // 8. Metallic Inner Ring Border
        val innerRingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawCircle(centerX, headCenterY, innerRadius, innerRingPaint)

        // 9. Person Icon Inside
        val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_person_custom)
        if (drawable != null) {
            val iconSize = (innerRadius * 1.28f).toInt()
            val left = (centerX - iconSize / 2f).toInt()
            val top = (headCenterY - iconSize / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.setTint(colorBottom)
            drawable.draw(canvas)
        }

        // 10. Glowing Status Indicator Beacon on Top-Right
        val statusBadgeRadius = 3.5f * density
        val badgeX = centerX + headRadius - (3.5f * density)
        val badgeY = headCenterY - headRadius + (3.5f * density)

        val statusBadgeGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(badgeX, badgeY, statusBadgeRadius + (1.2f * density), statusBadgeGlow)

        val statusBadgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOnline) (if (isMoving) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.parseColor("#06B6D4")) else android.graphics.Color.parseColor("#94A3B8")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(badgeX, badgeY, statusBadgeRadius, statusBadgePaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun showLiveUsersBottomSheet() {
        val locations = latestLocationsMap.values.toList()
        if (locations.isEmpty()) {
            android.widget.Toast.makeText(this, "No active user pins found on map yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val dialogBinding = com.zynexbd.crmsolution.databinding.DialogLiveUsersListBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(dialogBinding.root)

        val activeCount = locations.count { it.isOnline }
        dialogBinding.textLiveUsersBadge.text = "$activeCount Online / ${locations.size} Total"

        val adapter = com.zynexbd.crmsolution.adapters.LiveUsersAdapter { selectedUser ->
            dialog.dismiss()
            focusOnUser(selectedUser)
        }

        dialogBinding.recyclerLiveUsers.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        dialogBinding.recyclerLiveUsers.adapter = adapter
        adapter.setUsers(locations)

        dialogBinding.inputSearchLiveUsers.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
                dialogBinding.layoutEmptyState.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialog.show()
    }

    private fun focusOnUser(loc: LocationResponse) {
        val lat = loc.latitude ?: return
        val lng = loc.longitude ?: return
        val position = LatLng(lat, lng)

        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(position, 16f),
            800,
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    showDriverBottomSheet(loc)
                }
                override fun onCancel() {
                    showDriverBottomSheet(loc)
                }
            }
        )

        // Highlight and show marker title
        markers[loc.userId]?.showInfoWindow()
    }

    private fun showDriverBottomSheet(loc: LocationResponse) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.item_admin_user_overview, null, false)
        dialog.setContentView(view)

        val avatarInitials = view.findViewById<TextView>(R.id.textAvatarInitials)
        val nameView = view.findViewById<TextView>(R.id.textFullName)
        val usernameView = view.findViewById<TextView>(R.id.textUsername)
        val statusView = view.findViewById<TextView>(R.id.textAttendanceStatus)
        val addressView = view.findViewById<TextView>(R.id.textAddress)
        val coordinatesView = view.findViewById<TextView>(R.id.textCoordinates)
        val dateTimeView = view.findViewById<TextView>(R.id.textDateTime)
        val buttonMaps = view.findViewById<View>(R.id.buttonViewLocation)
        val buttonHistory = view.findViewById<View>(R.id.buttonRouteHistory)

        val displayName = loc.name?.ifBlank { loc.username } ?: loc.username ?: "Employee #${loc.userId}"
        val initial = displayName.trim().take(1).uppercase()
        avatarInitials?.text = if (initial.isNotBlank()) initial else "U"
        nameView?.text = displayName
        usernameView?.text = "@${loc.username}"

        if (loc.isOnline) {
            statusView?.text = "ONLINE"
            statusView?.setTextColor(android.graphics.Color.parseColor("#059669"))
        } else {
            statusView?.text = "OFFLINE"
            statusView?.setTextColor(android.graphics.Color.parseColor("#DC2626"))
        }

        // Display Address with Geocoder Resolution
        if (!loc.locationAddress.isNullOrBlank()) {
            addressView?.text = loc.locationAddress
        } else if (loc.latitude != null && loc.longitude != null) {
            addressView?.text = "Resolving address..."
            lifecycleScope.launch(Dispatchers.IO) {
                val resolved = com.zynexbd.crmsolution.utils.AddressHelper.resolveSpecificAddress(
                    applicationContext,
                    loc.latitude,
                    loc.longitude
                )
                withContext(Dispatchers.Main) {
                    addressView?.text = resolved ?: "Lat: ${"%.4f".format(loc.latitude)}, Lng: ${"%.4f".format(loc.longitude)}"
                }
            }
        } else {
            addressView?.text = "Location not available"
        }

        val speedText = if (loc.speed != null) "Speed: ${loc.speed} km/h" else "Speed: 0 km/h"
        val accuracyText = if (loc.accuracy != null) "Acc: ±${loc.accuracy.toInt()}m" else ""
        val coordsText = "Lat: ${loc.latitude ?: 0.0}, Lng: ${loc.longitude ?: 0.0} • $speedText $accuracyText"
        coordinatesView?.text = coordsText

        // Display formatted Date & Time
        dateTimeView?.text = formatTimestamp(loc.recordedAt)

        buttonMaps?.setOnClickListener {
            dialog.dismiss()
            if (loc.latitude != null && loc.longitude != null) {
                val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(${Uri.encode(displayName)})")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                mapIntent.setPackage("com.google.android.apps.maps")
                try {
                    startActivity(mapIntent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }

        buttonHistory?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, AdminRouteHistoryActivity::class.java).apply {
                putExtra("USER_ID", loc.userId)
                putExtra("USER_NAME", displayName)
            }
            startActivity(intent)
        }

        dialog.show()
    }

    private fun formatTimestamp(recordedAt: String?): String {
        if (recordedAt.isNullOrBlank()) {
            return SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date())
        }
        val millis = GpsRouteProcessor.parseTimestampToMillis(recordedAt)
        return if (millis > 0L) {
            SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(millis))
        } else {
            recordedAt
        }
    }
}
