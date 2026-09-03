package com.zynexbd.crmsolution.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.utils.LanguageManager
import com.zynexbd.crmsolution.utils.PaymentExpiredDialog
import com.zynexbd.crmsolution.utils.SessionManager
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityReceiver: BroadcastReceiver? = null
    private var networkBannerView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCurrentlyOnline = true

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLocale(newBase))
    }

    override fun onStart() {
        super.onStart()
        val typeface = LanguageManager.getTypeface(this)
        findViewById<View>(android.R.id.content)?.let { rootView ->
            LanguageManager.applyFontRecursively(rootView, typeface)
        }
        registerNetworkMonitors()
        com.zynexbd.crmsolution.utils.NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val session = SessionManager(this)
            if (session.isLoggedIn()) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    val prefs = getSharedPreferences("com.zynexbd.crmsolution_prefs", Context.MODE_PRIVATE)
                    val promptedCount = prefs.getInt("post_notif_prompt_count", 0)
                    if (promptedCount < 2) {
                        prefs.edit().putInt("post_notif_prompt_count", promptedCount + 1).apply()
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1011)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkConnectivityAndUpdate()
        checkSubscriptionStatusIfLoggedIn()
    }

    protected fun checkSubscriptionStatusIfLoggedIn() {
        val session = SessionManager(this)
        if (!session.isLoggedIn()) return

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@BaseActivity)
                val resp = api.getSubscriptionStatus()
                if (resp.isSuccessful && resp.body() != null) {
                    val status = resp.body()!!
                    if (status.isExpired) {
                        PaymentExpiredDialog.show(this@BaseActivity, status)
                    }
                }
            } catch (e: Exception) {
                // Silently ignore connection errors during background check
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkMonitors()
    }

    private fun registerNetworkMonitors() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        // 1. ConnectivityManager NetworkCallback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { checkConnectivityAndUpdate() }
            }

            override fun onLost(network: Network) {
                mainHandler.post { checkConnectivityAndUpdate() }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                mainHandler.post { checkConnectivityAndUpdate() }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                val builder = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                connectivityManager?.registerNetworkCallback(builder.build(), networkCallback!!)
            }
        } catch (e: Exception) {
            // Fallback continues
        }

        // 2. BroadcastReceiver Fallback for instantaneous trigger across all devices/OEMs
        try {
            connectivityReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    mainHandler.post { checkConnectivityAndUpdate() }
                }
            }
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            ContextCompat.registerReceiver(this, connectivityReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            // Fallback for devices where RECEIVER_EXPORTED or system broadcast registration varies
            try {
                if (connectivityReceiver != null) {
                    val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                    registerReceiver(connectivityReceiver, filter)
                }
            } catch (_: Exception) {}
        }
    }

    private fun unregisterNetworkMonitors() {
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore
            }
        }
        networkCallback = null

        connectivityReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
        connectivityReceiver = null
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = connectivityManager ?: getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkConnectivityAndUpdate() {
        if (isFinishing || isDestroyed) return
        val isOnline = isNetworkAvailable()
        if (isOnline != isCurrentlyOnline || (!isOnline && networkBannerView?.visibility != View.VISIBLE)) {
            isCurrentlyOnline = isOnline
            onNetworkStatusChanged(isOnline)
            if (!isOnline) {
                showOfflineBanner()
            } else {
                showOnlineBannerAndAutoHide()
            }
        }
    }

    open fun onNetworkStatusChanged(isConnected: Boolean) {
        // Subclasses can react
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        val fallback = (32 * resources.displayMetrics.density).toInt()
        return if (result > 0) result else fallback
    }

    private fun getOrCreateNetworkBanner(): View {
        if (networkBannerView != null) {
            networkBannerView?.bringToFront()
            return networkBannerView!!
        }
        val content = findViewById<ViewGroup>(android.R.id.content)
        val banner = LayoutInflater.from(this).inflate(R.layout.layout_offline_banner, content, false)
        
        val statusBarH = getStatusBarHeight()
        val marginHorizontal = (16 * resources.displayMetrics.density).toInt()
        val marginTop = statusBarH + (8 * resources.displayMetrics.density).toInt()

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            setMargins(marginHorizontal, marginTop, marginHorizontal, 0)
        }
        content.addView(banner, params)
        banner.elevation = 100f
        banner.translationZ = 100f
        banner.bringToFront()
        networkBannerView = banner
        return banner
    }

    private fun showOfflineBanner() {
        val banner = getOrCreateNetworkBanner()
        val isEn = LanguageManager.getLanguage(this) == LanguageManager.LANG_EN
        val textMessage = banner.findViewById<TextView>(R.id.textNetworkStatusMessage)
        val progress = banner.findViewById<ProgressBar>(R.id.progressNetworkReconnecting)
        val icon = banner.findViewById<ImageView>(R.id.imageNetworkStatusIcon)

        mainHandler.removeCallbacksAndMessages(null)
        banner.setBackgroundResource(R.drawable.bg_offline_pill)
        icon.setImageResource(R.drawable.ic_wifi_off)
        icon.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        textMessage.text = if (isEn) "No Internet Connection • Offline Mode" else "ইন্টারনেট সংযোগ নেই • অফলাইন মোড"
        
        banner.bringToFront()
        banner.visibility = View.VISIBLE
        banner.translationY = -250f
        banner.alpha = 0f
        banner.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(280)
            .setListener(null)
            .start()
    }

    private fun showOnlineBannerAndAutoHide() {
        if (networkBannerView == null || networkBannerView?.visibility != View.VISIBLE) return
        val banner = networkBannerView!!
        val isEn = LanguageManager.getLanguage(this) == LanguageManager.LANG_EN
        val textMessage = banner.findViewById<TextView>(R.id.textNetworkStatusMessage)
        val progress = banner.findViewById<ProgressBar>(R.id.progressNetworkReconnecting)
        val icon = banner.findViewById<ImageView>(R.id.imageNetworkStatusIcon)

        mainHandler.removeCallbacksAndMessages(null)
        banner.setBackgroundResource(R.drawable.bg_online_pill)
        icon.setImageResource(R.drawable.ic_wifi_check)
        icon.visibility = View.VISIBLE
        progress.visibility = View.GONE
        textMessage.text = if (isEn) "Connected • Back Online" else "ইন্টারনেট সংযোগ চালু হয়েছে • ব্যাক অনলাইন"

        mainHandler.postDelayed({
            if (isCurrentlyOnline && banner.visibility == View.VISIBLE) {
                banner.animate()
                    .translationY(-250f)
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            banner.visibility = View.GONE
                        }
                    })
                    .start()
            }
        }, 1500)
    }

    fun showLanguageSelectionDialog(onLanguageChanged: (() -> Unit)? = null) {
        val currentLang = LanguageManager.getLanguage(this)
        val items = arrayOf("English 🇬🇧 (Roboto Font)", "বাংলা 🇧🇩 (সোলাইমান লিপি)")
        val selectedIndex = if (currentLang == LanguageManager.LANG_EN) 0 else 1

        AlertDialog.Builder(this)
            .setTitle(if (currentLang == LanguageManager.LANG_EN) "Select Language" else "ভাষা নির্বাচন করুন")
            .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                val newLang = if (which == 0) LanguageManager.LANG_EN else LanguageManager.LANG_BN
                if (newLang != currentLang) {
                    LanguageManager.setLanguage(this, newLang)
                    dialog.dismiss()
                    onLanguageChanged?.invoke()
                    recreateActivity()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(if (currentLang == LanguageManager.LANG_EN) "Cancel" else "বাতিল", null)
            .show()
    }

    fun showLogoutConfirmationDialog(onConfirmLogout: () -> Unit) {
        val isEn = LanguageManager.getLanguage(this) == LanguageManager.LANG_EN
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_logout, null)

        val textTitle = dialogView.findViewById<TextView>(R.id.textLogoutTitle)
        val textMessage = dialogView.findViewById<TextView>(R.id.textLogoutMessage)
        val buttonCancel = dialogView.findViewById<TextView>(R.id.buttonCancelLogout)
        val buttonConfirm = dialogView.findViewById<TextView>(R.id.buttonConfirmLogout)

        textTitle.text = if (isEn) "Confirm Logout" else "লগআউট নিশ্চিত করুন"
        textMessage.text = if (isEn)
            "Are you sure you want to log out from your account on this device?"
        else
            "আপনি কি নিশ্চিতভাবে আপনার অ্যাকাউন্ট থেকে লগআউট করতে চান?"
        buttonCancel.text = if (isEn) "Cancel" else "বাতিল"
        buttonConfirm.text = if (isEn) "Yes, Log Out" else "হ্যাঁ, লগআউট"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        buttonConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirmLogout()
        }

        dialog.show()
    }

    fun setupAdminDrawer(
        drawerLayout: androidx.drawerlayout.widget.DrawerLayout,
        navigationView: com.google.android.material.navigation.NavigationView,
        buttonMenu: View?,
        selectedItemId: Int? = null
    ) {
        val session = SessionManager(this)
        val headerView = if (navigationView.headerCount > 0) navigationView.getHeaderView(0) else null
        val textNavTitle = headerView?.findViewById<TextView>(R.id.textNavTitle)
        val textNavSubtitle = headerView?.findViewById<TextView>(R.id.textNavSubtitle)
        val buttonNavLanguage = headerView?.findViewById<TextView>(R.id.buttonNavLanguage)
        val imageNavLogo = headerView?.findViewById<ImageView>(R.id.imageNavLogo)
        val username = session.getUsername() ?: "admin"
        val fullName = session.getFullName() ?: username
        val companyName = session.getCompanyName() ?: "CRM SOLUTION"
        val role = session.getRole() ?: "Admin"
        val isEn = LanguageManager.getLanguage(this) == LanguageManager.LANG_EN

        textNavTitle?.text = companyName
        textNavSubtitle?.text = if (isEn) "$fullName\nRole: $role" else "$fullName\nরোল: $role"
        buttonNavLanguage?.text = if (isEn) "🌐 EN 🇬🇧" else "🌐 বাংলা 🇧🇩"
        buttonNavLanguage?.setOnClickListener { showLanguageSelectionDialog() }

        // Dynamic Company Logo Binding with Glide
        val logoUrl = session.getCompanyLogoUrl()
        if (imageNavLogo != null) {
            if (!logoUrl.isNullOrBlank()) {
                val fullUrl = if (logoUrl.startsWith("http")) logoUrl else session.getServerBaseUrl().trimEnd('/') + "/" + logoUrl.trimStart('/')
                com.bumptech.glide.Glide.with(this)
                    .load(fullUrl)
                    .placeholder(R.drawable.ic_person_custom)
                    .error(R.drawable.ic_person_custom)
                    .circleCrop()
                    .into(imageNavLogo)
            } else {
                imageNavLogo.setImageResource(R.drawable.ic_person_custom)
            }
        }

        // Asynchronously refresh dynamic branding from server if logged in
        if (session.isLoggedIn()) {
            lifecycleScope.launch {
                try {
                    val resp = ApiClient.getApiService(this@BaseActivity).getCompanyBranding()
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        val brand = resp.body()?.data
                        if (brand != null) {
                            session.saveCompanyBranding(brand.companyName, brand.logoUrl)
                            textNavTitle?.text = brand.companyName
                            if (!brand.logoUrl.isNullOrBlank() && imageNavLogo != null) {
                                val fullUrl = if (brand.logoUrl.startsWith("http")) brand.logoUrl else session.getServerBaseUrl().trimEnd('/') + "/" + brand.logoUrl.trimStart('/')
                                com.bumptech.glide.Glide.with(this@BaseActivity)
                                    .load(fullUrl)
                                    .placeholder(R.drawable.ic_person_custom)
                                    .error(R.drawable.ic_person_custom)
                                    .circleCrop()
                                    .into(imageNavLogo)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        buttonMenu?.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        if (session.isManager()) {
            // Managers get the CRM manager surface only, not system-admin areas (user/employee
            // management, billing, legacy attendance/visit performance reports).
            navigationView.menu.findItem(R.id.nav_users)?.isVisible = false
            navigationView.menu.findItem(R.id.nav_payment)?.isVisible = false
            navigationView.menu.findItem(R.id.nav_performance)?.isVisible = false
        }

        selectedItemId?.let { navigationView.setCheckedItem(it) }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            val itemId = menuItem.itemId
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            when (itemId) {
                R.id.nav_overview -> {
                    if (this !is AdminOverviewDashboardActivity) {
                        startActivity(Intent(this, AdminOverviewDashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_crm_dashboard -> {
                    if (this !is AdminCrmDashboardActivity) {
                        startActivity(Intent(this, AdminCrmDashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_crm_leads -> {
                    if (this !is AdminCrmLeadListActivity) {
                        startActivity(Intent(this, AdminCrmLeadListActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_crm_productivity -> {
                    if (this !is AdminCrmProductivityActivity) {
                        startActivity(Intent(this, AdminCrmProductivityActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_crm_kpi -> {
                    if (this !is AdminCrmKpiActivity) {
                        startActivity(Intent(this, AdminCrmKpiActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_crm_products -> {
                    if (this !is ProductServiceManagementActivity) {
                        startActivity(Intent(this, ProductServiceManagementActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_map -> {
                    if (this !is AdminDashboardActivity) {
                        startActivity(Intent(this, AdminDashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_route_history -> {
                    if (this !is AdminRouteHistoryActivity) {
                        startActivity(Intent(this, AdminRouteHistoryActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_users -> {
                    if (this !is UserManagementActivity) {
                        startActivity(Intent(this, UserManagementActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_attendance -> {
                    if (this !is AdminAttendanceActivity) {
                        startActivity(Intent(this, AdminAttendanceActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_leave -> {
                    if (this !is AdminLeaveActivity) {
                        startActivity(Intent(this, AdminLeaveActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_performance -> {
                    if (this !is AdminPerformanceActivity) {
                        startActivity(Intent(this, AdminPerformanceActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_shifts -> {
                    if (this !is AdminShiftActivity) {
                        startActivity(Intent(this, AdminShiftActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_holidays -> {
                    if (this !is AdminHolidaysActivity) {
                        startActivity(Intent(this, AdminHolidaysActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_offices -> {
                    if (this !is AdminOfficeLocationsActivity) {
                        startActivity(Intent(this, AdminOfficeLocationsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_payment -> {
                    if (this !is PaymentManagementActivity) {
                        startActivity(Intent(this, PaymentManagementActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_support -> {
                    if (this !is AdminSupportActivity) {
                        startActivity(Intent(this, AdminSupportActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                R.id.nav_logout -> {
                    showLogoutConfirmationDialog {
                        session.clear()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finishAffinity()
                    }
                }
            }
            true
        }
    }

    private fun recreateActivity() {
        val intent = intent
        finish()
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
