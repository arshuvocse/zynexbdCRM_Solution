package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityAdminCrmDashboardBinding
import com.zynexbd.crmsolution.models.ChartBarEntry
import com.zynexbd.crmsolution.models.ChartDonutSlice
import com.zynexbd.crmsolution.utils.SessionManager
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import com.zynexbd.crmsolution.views.BarChartView
import com.zynexbd.crmsolution.views.DonutChartView
import com.zynexbd.crmsolution.adapters.LiveTeamActivityAdapter
import com.zynexbd.crmsolution.network.SignalRClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminCrmDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminCrmDashboardBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var session: SessionManager
    private lateinit var liveActivityAdapter: LiveTeamActivityAdapter
    private var signalRClient: SignalRClient? = null

    private var fromDateStr: String? = null
    private var toDateStr: String? = null
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCrmDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupAdminDrawer(binding.drawerLayout, binding.navigationView, binding.buttonMenu, R.id.nav_crm_dashboard)
        setupUI()
        setupDateFilters()
        observeViewModel()
        setupLiveSignalR()

        loadDashboard()
    }

    private fun setupUI() {
        binding.buttonRefresh.setOnClickListener { loadDashboard() }
        binding.swipeRefresh.setOnRefreshListener { loadDashboard() }

        binding.buttonAllLeads.setOnClickListener {
            startActivity(Intent(this, AdminCrmLeadListActivity::class.java))
        }

        binding.cardTotalLeads.setOnClickListener {
            startActivity(Intent(this, AdminCrmLeadListActivity::class.java))
        }

        binding.buttonCreateLead.setOnClickListener {
            startActivity(Intent(this, AdminCrmLeadListActivity::class.java).apply {
                putExtra("EXTRA_ACTION_CREATE", true)
            })
        }

        binding.buttonFollowUps.setOnClickListener {
            startActivity(Intent(this, AdminCrmFollowUpsActivity::class.java))
        }

        binding.cardTodayFollowUps.setOnClickListener {
            startActivity(Intent(this, AdminCrmFollowUpsActivity::class.java).apply {
                putExtra("INITIAL_FILTER", "today")
            })
        }

        binding.buttonReports.setOnClickListener {
            startActivity(Intent(this, AdminCrmReportsActivity::class.java))
        }

        binding.buttonKpiManagement.setOnClickListener {
            startActivity(Intent(this, AdminCrmKpiActivity::class.java))
        }

        binding.buttonProducts.setOnClickListener {
            startActivity(Intent(this, ProductServiceManagementActivity::class.java))
        }

        binding.buttonProductivityReport.setOnClickListener {
            startActivity(Intent(this, AdminCrmProductivityActivity::class.java))
        }

        setupLiveActivities()
        setupCompanyBrandingCard()
    }

    private fun setupCompanyBrandingCard() {
        val userName = session.getFullName() ?: session.getUsername() ?: "User"
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        binding.textUserGreeting.text = "$timeGreeting, $userName"
        binding.textCompanyName.text = session.getCompanyName() ?: "CRM SOLUTION"
        binding.textRoleBadge.text = (session.getRole() ?: "ADMIN").uppercase()

        val logoUrl = session.getCompanyLogoUrl()
        loadBrandingLogo(logoUrl)

        viewModel.loadCompanyBranding { branding ->
            if (branding != null) {
                session.saveCompanyBranding(branding.companyName, branding.logoUrl)
                binding.textCompanyName.text = branding.companyName
                loadBrandingLogo(branding.logoUrl)
            }
        }
    }

    private fun loadBrandingLogo(logoUrl: String?) {
        if (!logoUrl.isNullOrBlank()) {
            val fullUrl = if (logoUrl.startsWith("http")) logoUrl else session.getServerBaseUrl().trimEnd('/') + "/" + logoUrl.trimStart('/')
            com.bumptech.glide.Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.ic_person_custom)
                .error(R.drawable.ic_person_custom)
                .circleCrop()
                .into(binding.imageCompanyLogo)
        } else {
            binding.imageCompanyLogo.setImageResource(R.drawable.ic_person_custom)
        }
    }

    private fun setupDateFilters() {
        binding.tabLayoutDateFilters.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val cal = Calendar.getInstance()
                val now = cal.time
                when (tab?.position) {
                    0 -> { // Today
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        fromDateStr = isoFormat.format(cal.time)
                        toDateStr = isoFormat.format(now)
                    }
                    1 -> { // This Week
                        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        fromDateStr = isoFormat.format(cal.time)
                        toDateStr = isoFormat.format(now)
                    }
                    2 -> { // This Month
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        fromDateStr = isoFormat.format(cal.time)
                        toDateStr = isoFormat.format(now)
                    }
                    3 -> { // All Time
                        fromDateStr = null
                        toDateStr = null
                    }
                }
                loadDashboard()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadDashboard() {
        binding.swipeRefresh.isRefreshing = true
        viewModel.loadAdminCrmDashboard(
            fromDate = fromDateStr,
            toDate = toDateStr
        )
        viewModel.loadLiveTeamActivities()
    }

    private fun observeViewModel() {
        viewModel.adminCrmDashboard.observe(this) { data ->
            binding.swipeRefresh.isRefreshing = false
            if (data == null) return@observe

            // 11 Summary Cards
            binding.textTotalLeads.text = data.totalLeads.toString()
            binding.textNewLeads.text = data.newLeads.toString()
            binding.textTodayFollowUps.text = data.followUpsToday.toString()
            binding.textPendingFollowUps.text = data.pendingFollowUps.toString()
            binding.textOverdueFollowUps.text = data.overdueFollowUps.toString()
            binding.textInterestedLeads.text = data.interestedLeads.toString()
            binding.textNotInterestedLeads.text = data.notInterestedLeads.toString()
            binding.textClosedLeads.text = data.closedLeads.toString()
            binding.textConversionRate.text = String.format("%.1f%%", data.conversionRate)
            binding.textTotalManagers.text = data.totalManagers.toString()
            binding.textTotalUsers.text = data.totalUsers.toString()

            // 8 Visual Dynamic Charts
            // 1. Funnel
            binding.chartConversionFunnel.setData(data.conversionFunnel)

            // 2. Status Distribution Donut
            val statusSlices = data.statusDistribution.map {
                DonutChartView.Slice(
                    label = it.label,
                    value = it.value.toFloat(),
                    color = safeParseColor(it.colorHex, Color.parseColor("#3B82F6"))
                )
            }
            binding.chartStatusDistribution.setData(statusSlices, "${data.totalLeads}", "Total Leads")
            binding.textStatusLegend.text = data.statusDistribution.joinToString(" • ") { "${it.label}: ${it.value.toInt()}" }

            // 3. Monthly Lead Trend
            val trendBars = data.monthlyLeadTrend.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartMonthlyLeadTrend.setData(trendBars)

            // 4. Follow-up Trend
            val fuBars = data.followUpTrend.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartFollowUpTrend.setData(fuBars)

            // 5. Manager Performance
            val managerBars = data.managerPerformance.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartManagerPerformance.setData(managerBars)

            // 6. User Productivity
            val userBars = data.userProductivity.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartUserProductivity.setData(userBars)

            // 7. Product Performance
            val prodBars = data.productPerformance.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartProductPerformance.setData(prodBars)

            // 8. Source Distribution Donut
            val sourceSlices = data.sourceDistribution.map {
                DonutChartView.Slice(
                    label = it.label,
                    value = it.value.toFloat(),
                    color = safeParseColor(it.colorHex, Color.parseColor("#8B5CF6"))
                )
            }
            binding.chartSourceDistribution.setData(sourceSlices, "${data.sourceDistribution.sumOf { it.value.toInt() }}", "Sources")
            binding.textSourceLegend.text = data.sourceDistribution.joinToString(" • ") { "${it.label}: ${it.value.toInt()}" }
        }

        viewModel.liveTeamActivities.observe(this) { list ->
            if (list.isNullOrEmpty()) {
                binding.textEmptyLiveActivities.visibility = android.view.View.VISIBLE
                binding.recyclerLiveActivities.visibility = android.view.View.GONE
            } else {
                binding.textEmptyLiveActivities.visibility = android.view.View.GONE
                binding.recyclerLiveActivities.visibility = android.view.View.VISIBLE
                liveActivityAdapter.submitList(list)
            }
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupLiveActivities() {
        liveActivityAdapter = LiveTeamActivityAdapter { item ->
            if (item.entityType == "Lead" && item.targetEntityId != null && item.targetEntityId > 0) {
                startActivity(Intent(this, LeadDetailsActivity::class.java).apply {
                    putExtra("LEAD_ID", item.targetEntityId)
                })
            } else if (item.entityType == "Kpi" || item.actionType.startsWith("Kpi")) {
                startActivity(Intent(this, AdminCrmKpiActivity::class.java))
            } else {
                Toast.makeText(this, "${item.title}\n${item.subtitle}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.recyclerLiveActivities.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@AdminCrmDashboardActivity)
            adapter = liveActivityAdapter
            isNestedScrollingEnabled = false
        }

        binding.buttonOpenLiveActivityHistory.setOnClickListener {
            startActivity(Intent(this, TeamActivityHistoryActivity::class.java))
        }
    }

    private fun setupLiveSignalR() {
        signalRClient = SignalRClient(this)
        signalRClient?.connect(
            onTeamActivityReceived = { activity ->
                runOnUiThread {
                    binding.textEmptyLiveActivities.visibility = android.view.View.GONE
                    binding.recyclerLiveActivities.visibility = android.view.View.VISIBLE
                    liveActivityAdapter.prependItem(activity)
                    binding.recyclerLiveActivities.scrollToPosition(0)

                    binding.textLiveCountBadge.text = "NEW ACTIVITY"
                    binding.textLiveCountBadge.postDelayed({
                        binding.textLiveCountBadge.text = "REAL-TIME"
                    }, 4000)

                    // Silently refresh metrics in background
                    viewModel.loadAdminCrmDashboard(fromDate = fromDateStr, toDate = toDateStr)
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        signalRClient?.disconnect()
    }

    private fun safeParseColor(hex: String?, defaultColor: Int): Int {
        if (hex.isNullOrBlank()) return defaultColor
        return try {
            Color.parseColor(hex)
        } catch (e: Exception) {
            defaultColor
        }
    }
}
