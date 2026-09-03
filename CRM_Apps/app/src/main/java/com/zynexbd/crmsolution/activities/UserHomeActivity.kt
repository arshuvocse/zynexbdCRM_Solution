package com.zynexbd.crmsolution.activities

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityUserHomeBinding
import com.zynexbd.crmsolution.helpers.BatteryOptimizationHelper
import com.zynexbd.crmsolution.network.RetrofitClient
import com.zynexbd.crmsolution.services.TrackingForegroundService
import com.zynexbd.crmsolution.utils.Constants
import com.zynexbd.crmsolution.utils.LanguageManager
import com.zynexbd.crmsolution.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "UserHomeActivity"

class UserHomeActivity : BaseActivity() {

    private lateinit var binding: ActivityUserHomeBinding
    private lateinit var session: SessionManager
    private var blockingDialog: AlertDialog? = null
    private var isFirstLaunch = true

    // 1. Standard Runtime Permissions Launcher (Location, Camera, Notifications)
    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionsResult(permissions)
    }

    // 2. Background Location Launcher (Android 10+)
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        checkGpsAndStartService()
    }

    // 3. Native GPS Enable Resolution Launcher
    private val locationSettingsResolution = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        checkGpsAndStartService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        val name = session.getFullName() ?: session.getUsername() ?: "Employee"
        val isEn = LanguageManager.isEnglish(this)
        binding.textWelcome.text = "Hi, $name"
        binding.buttonUserLanguage.text = if (isEn) "🌐 English" else "🌐 বাংলা"
        binding.buttonGuide.visibility = android.view.View.GONE

        setupClickListeners()
        setupBottomNavigation()

        // Prompt standard Android runtime permissions popup immediately after login
        requestAppPermissions()
    }

    private var signalRClient: com.zynexbd.crmsolution.network.SignalRClient? = null

    override fun onResume() {
        super.onResume()
        try {
            binding.bottomNavigationView.selectedItemId = R.id.nav_home
            if (!isFirstLaunch) {
                if (hasLocationPermission()) {
                    dismissBlockingDialog()
                    checkGpsAndStartService()
                } else {
                    showPermissionDeniedDialog()
                }
            }
            isFirstLaunch = false
            updateDeviceStatusIndicators()
            loadFieldUserDashboardStats()
            com.zynexbd.crmsolution.utils.AppUpdateHelper.checkForUpdate(this, lifecycleScope)
            if (signalRClient == null) {
                signalRClient = com.zynexbd.crmsolution.network.SignalRClient(this).apply {
                    connect(onNotificationReceived = {
                        runOnUiThread { loadFieldUserDashboardStats() }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume refresh failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        signalRClient?.disconnect()
        signalRClient = null
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.nav_home
        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true
                R.id.nav_customers -> {
                    startActivity(Intent(this, CustomerListActivity::class.java))
                    true
                }
                R.id.nav_record_visit -> {
                    startActivity(Intent(this, RecordVisitActivity::class.java))
                    true
                }
                R.id.nav_followups -> {
                    startActivity(Intent(this, FollowUpsActivity::class.java))
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, AttendanceHistoryActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.buttonNavCenterHome.setOnClickListener {
            binding.bottomNavigationView.selectedItemId = R.id.nav_home
        }
    }

    private fun setupClickListeners() {
        binding.textBrandLabel.setOnClickListener {
            showLanguageSelectionDialog()
        }

        binding.buttonUserLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }

        binding.buttonNotifications.setOnClickListener {
            startActivity(Intent(this, AttendanceHistoryActivity::class.java))
        }

        binding.buttonDutyIn.setOnClickListener {
            val intent = Intent(this, PunchAttendanceActivity::class.java).apply {
                putExtra("EXTRA_IS_PUNCH_IN", true)
            }
            startActivity(intent)
        }

        binding.buttonDutyOut.setOnClickListener {
            val intent = Intent(this, PunchAttendanceActivity::class.java).apply {
                putExtra("EXTRA_IS_PUNCH_IN", false)
            }
            startActivity(intent)
        }

        binding.buttonCustomers.setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java))
        }
        binding.cardCustomers.setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java))
        }

        binding.buttonRecordVisit.setOnClickListener {
            startActivity(Intent(this, RecordVisitActivity::class.java))
        }
        binding.cardRecordVisit.setOnClickListener {
            startActivity(Intent(this, RecordVisitActivity::class.java))
        }

        binding.buttonFollowUps.setOnClickListener {
            startActivity(Intent(this, UserCrmFollowUpsActivity::class.java))
        }

        binding.buttonAttendanceHistory.setOnClickListener {
            startActivity(Intent(this, AttendanceHistoryActivity::class.java))
        }
        binding.cardAttendanceHistory.setOnClickListener {
            startActivity(Intent(this, AttendanceHistoryActivity::class.java))
        }

        binding.buttonCrmDashboard.setOnClickListener {
            startActivity(Intent(this, UserCrmDashboardActivity::class.java))
        }
        binding.cardCrmDashboard.setOnClickListener {
            startActivity(Intent(this, UserCrmDashboardActivity::class.java))
        }

        binding.buttonCrmLeads.setOnClickListener {
            startActivity(Intent(this, UserCrmLeadListActivity::class.java))
        }
        binding.cardCrmLeads.setOnClickListener {
            startActivity(Intent(this, UserCrmLeadListActivity::class.java))
        }

        binding.buttonApplyLeave.setOnClickListener {
            startActivity(Intent(this, ApplyLeaveActivity::class.java))
        }

        binding.buttonLeaveHistory.setOnClickListener {
            startActivity(Intent(this, LeaveHistoryActivity::class.java))
        }

        binding.buttonLogout.setOnClickListener {
            showLogoutConfirmationDialog {
                session.clear()
                stopService(Intent(this, TrackingForegroundService::class.java))
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun updateDeviceStatusIndicators() {
        val isEn = LanguageManager.isEnglish(this)

        // GPS Status
        val isGpsOn = isGpsEnabled()
        binding.textGpsPill.text = if (isGpsOn) (if (isEn) "GPS: ON" else "জিপিএস: চালু") else (if (isEn) "GPS: OFF" else "জিপিএস: বন্ধ")
        binding.textGpsPill.setBackgroundResource(if (isGpsOn) R.drawable.bg_status_active_pill else R.drawable.bg_status_inactive_pill)
        binding.textGpsPill.setTextColor(android.graphics.Color.WHITE)

        // Internet Status
        val isConnected = isInternetAvailable()
        binding.textNetworkPill.text = if (isConnected) (if (isEn) "ONLINE" else "অনলাইন") else (if (isEn) "OFFLINE" else "অফলাইন")
        binding.textNetworkPill.setBackgroundResource(if (isConnected) R.drawable.bg_status_active_pill else R.drawable.bg_status_inactive_pill)
        binding.textNetworkPill.setTextColor(android.graphics.Color.WHITE)

        // Battery Status
        val batteryPct = getBatteryPercentage()
        binding.textBatteryPill.text = if (isEn) "BATTERY: ${batteryPct}%" else "ব্যাটারি: ${batteryPct}%"
        binding.textBatteryPill.setBackgroundResource(if (batteryPct > 15) R.drawable.bg_status_active_pill else R.drawable.bg_status_inactive_pill)
        binding.textBatteryPill.setTextColor(android.graphics.Color.WHITE)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getBatteryPercentage(): Int {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    }

    private fun loadFieldUserDashboardStats() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@UserHomeActivity)
                
                // 1. Fetch Today's Attendance Status directly from API
                val todayResponse = api.getTodayAttendanceStatus()
                if (todayResponse.isSuccessful && todayResponse.body() != null) {
                    val todayStatus = todayResponse.body()!!
                    updateDutyButtonsState(todayStatus)
                }

                // 2. Fetch Field User Dashboard Overview Stats
                val response = api.getFieldUserDashboardStats()
                if (response.isSuccessful && response.body() != null) {
                    val stats = response.body()!!
                    when {
                        stats.attendanceStatus.equals("Punched In", ignoreCase = true) || stats.attendanceStatus.equals("Duty In", ignoreCase = true) -> {
                            binding.textPunchInStatus.text = "Duty In"
                            binding.textPunchInTime.text = stats.punchInTime ?: stats.todayWorkingTime
                        }
                        stats.attendanceStatus.equals("Punched Out", ignoreCase = true) || stats.attendanceStatus.equals("Duty Out", ignoreCase = true) -> {
                            binding.textPunchInStatus.text = "Duty Out"
                            binding.textPunchInTime.text = stats.punchOutTime ?: stats.todayWorkingTime
                        }
                        else -> {
                            binding.textPunchInStatus.text = "Duty In"
                            binding.textPunchInTime.text = "--:--"
                        }
                    }
                    binding.textVisitsCount.text = stats.todayVisitsCount.toString()
                    binding.textFollowUpsCount.text = stats.pendingFollowUpsCount.toString()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load field user stats", e)
            }
        }
    }

    private fun updateDutyButtonsState(status: com.zynexbd.crmsolution.models.TodayAttendanceStatusResponse) {
        val isEn = LanguageManager.getLanguage(this) == LanguageManager.LANG_EN

        // ---- DUTY IN BUTTON STATE ----
        if (status.hasPunchedIn) {
            // Already punched in -> Read Only / Done
            binding.buttonDutyIn.setBackgroundResource(R.drawable.bg_clean_rounded_card)
            binding.buttonDutyIn.elevation = 2f
            binding.textDutyInLabel.text = if (isEn) "✓ IN (${status.punchInTime ?: "DONE"})" else "✓ ইন (${status.punchInTime ?: "সম্পন্ন"})"
            binding.textDutyInLabel.setTextColor(ContextCompat.getColor(this, R.color.statusActive))
            binding.imageDutyInIcon.setColorFilter(ContextCompat.getColor(this, R.color.statusActive))
            binding.buttonDutyIn.setOnClickListener {
                Toast.makeText(this, if (isEn) "You have already completed Duty In for today." else "আজকের ডিউটি ইন সম্পন্ন হয়েছে।", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Not punched in yet -> Active button
            binding.buttonDutyIn.setBackgroundResource(R.drawable.bg_btn_duty_in)
            binding.buttonDutyIn.elevation = 6f
            binding.textDutyInLabel.text = if (isEn) "DUTY IN" else "ডিউটি ইন"
            binding.textDutyInLabel.setTextColor(android.graphics.Color.WHITE)
            binding.imageDutyInIcon.clearColorFilter()
            binding.buttonDutyIn.setOnClickListener {
                val intent = Intent(this, PunchAttendanceActivity::class.java).apply {
                    putExtra("EXTRA_IS_PUNCH_IN", true)
                }
                startActivity(intent)
            }
        }

        // ---- DUTY OUT BUTTON STATE ----
        if (status.hasPunchedOut) {
            // Already punched out -> Read Only / Done
            binding.buttonDutyOut.setBackgroundResource(R.drawable.bg_clean_rounded_card)
            binding.buttonDutyOut.elevation = 2f
            binding.textDutyOutLabel.text = if (isEn) "✓ OUT (${status.punchOutTime ?: "DONE"})" else "✓ আউট (${status.punchOutTime ?: "সম্পন্ন"})"
            binding.textDutyOutLabel.setTextColor(ContextCompat.getColor(this, R.color.statusInactive))
            binding.imageDutyOutIcon.setColorFilter(ContextCompat.getColor(this, R.color.statusInactive))
            binding.buttonDutyOut.setOnClickListener {
                Toast.makeText(this, if (isEn) "You have already completed Duty Out for today." else "আজকের ডিউটি আউট সম্পন্ন হয়েছে।", Toast.LENGTH_SHORT).show()
            }
        } else if (!status.hasPunchedIn) {
            // Cannot punch out without punching in first -> Disabled / Prompt
            binding.buttonDutyOut.setBackgroundResource(R.drawable.bg_clean_rounded_card)
            binding.buttonDutyOut.elevation = 2f
            binding.textDutyOutLabel.text = if (isEn) "DUTY OUT" else "ডিউটি আউট"
            binding.textDutyOutLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.imageDutyOutIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            binding.buttonDutyOut.setOnClickListener {
                Toast.makeText(this, if (isEn) "Please complete Duty In first." else "প্রথমে ডিউটি ইন সম্পন্ন করুন।", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Punched in, ready to punch out -> Active button
            binding.buttonDutyOut.setBackgroundResource(R.drawable.bg_btn_duty_out)
            binding.buttonDutyOut.elevation = 6f
            binding.textDutyOutLabel.text = if (isEn) "DUTY OUT" else "ডিউটি আউট"
            binding.textDutyOutLabel.setTextColor(android.graphics.Color.WHITE)
            binding.imageDutyOutIcon.clearColorFilter()
            binding.buttonDutyOut.setOnClickListener {
                val intent = Intent(this, PunchAttendanceActivity::class.java).apply {
                    putExtra("EXTRA_IS_PUNCH_IN", false)
                }
                startActivity(intent)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return true // Bypass location check as location service is temporarily disabled
    }

    private fun isGpsEnabled(): Boolean {
        return true // Bypass GPS provider check as location service is temporarily disabled
    }

    /**
     * Request standard runtime permissions (Camera, Notifications) without forcing location.
     */
    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            runtimePermissionsLauncher.launch(missing.toTypedArray())
        } else {
            handlePermissionsResult(emptyMap())
        }
    }

    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        dismissBlockingDialog()
    }

    private fun checkBackgroundLocation() {
        // Background location service disabled as requested
    }

    private fun checkGpsAndStartService() {
        // Background location service disabled as requested
    }

    private fun startTrackingServiceSafely() {
        // Background location service disabled as requested
    }

    private fun requestBatteryExemption() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean("battery_exemption_prompted", false)) return
        try {
            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
                prefs.edit().putBoolean("battery_exemption_prompted", true).apply()
                BatteryOptimizationHelper.requestExemption(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Battery optimization exemption request failed", e)
        }
    }

    private fun requestAutoStartPermission() {
        if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean("autostart_prompted", false)) return
        try {
            val intent = Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            if (packageManager.resolveActivity(intent, 0) != null) {
                prefs.edit().putBoolean("autostart_prompted", true).apply()
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open MIUI Autostart settings", e)
        }
    }

    private fun requestEnableGps() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
            val settingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)
                .build()

            LocationServices.getSettingsClient(this)
                .checkLocationSettings(settingsRequest)
                .addOnSuccessListener {
                    startTrackingServiceSafely()
                }
                .addOnFailureListener { exception ->
                    if (exception is ResolvableApiException) {
                        try {
                            locationSettingsResolution.launch(
                                IntentSenderRequest.Builder(exception.resolution).build()
                            )
                        } catch (e: IntentSender.SendIntentException) {
                            openLocationSourceSettings()
                        }
                    } else {
                        openLocationSourceSettings()
                    }
                }
        } catch (e: Exception) {
            openLocationSourceSettings()
        }
    }

    private fun openLocationSourceSettings() {
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Location source settings screen not found", e)
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "App settings screen not found", e)
        }
    }

    private fun showPermissionDeniedDialog() {
        if (blockingDialog?.isShowing == true) return
        val isEn = LanguageManager.isEnglish(this)
        blockingDialog = AlertDialog.Builder(this)
            .setTitle(if (isEn) "Location Permission Required" else "লোকেশন পারমিশন প্রয়োজন")
            .setMessage(if (isEn) "Smart Workforce requires location permission to record your attendance and track field operations." else "আপনার উপস্থিতি ও ফিল্ড ট্র্যাকিং সচল রাখতে লোকেশন পারমিশন প্রদান করা বাধ্যতামূলক।")
            .setCancelable(false)
            .setPositiveButton(if (isEn) "Grant Permission" else "পারমিশন দিন") { _, _ -> requestAppPermissions() }
            .setNegativeButton(if (isEn) "App Settings" else "অ্যাপ সেটিংস") { _, _ -> openAppSettings() }
            .show()
    }

    private fun dismissBlockingDialog() {
        blockingDialog?.dismiss()
        blockingDialog = null
    }
}
