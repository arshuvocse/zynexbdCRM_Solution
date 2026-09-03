package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityManagerCrmDashboardBinding
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
import java.util.Date
import java.util.Locale

class ManagerCrmDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerCrmDashboardBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var session: SessionManager
    private lateinit var liveActivityAdapter: LiveTeamActivityAdapter
    private var signalRClient: SignalRClient? = null

    private var fromDateStr: String? = null
    private var toDateStr: String? = null

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerCrmDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupListeners()
        setupDateFilters()
        observeViewModel()
        setupLiveSignalR()

        loadDashboard()
    }

    private fun setupListeners() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefresh.setOnClickListener { loadDashboard() }
        binding.swipeRefresh.setOnRefreshListener { loadDashboard() }

        binding.buttonTeamLeads.setOnClickListener {
            startActivity(Intent(this, AdminCrmLeadListActivity::class.java))
        }

        binding.buttonCreateLead.setOnClickListener {
            startActivity(Intent(this, AdminCrmLeadListActivity::class.java).apply {
                putExtra("EXTRA_ACTION_CREATE", true)
            })
        }

        binding.buttonTeamFollowUps.setOnClickListener {
            startActivity(Intent(this, AdminCrmFollowUpsActivity::class.java))
        }

        binding.buttonTeamReports.setOnClickListener {
            startActivity(Intent(this, ManagerCrmReportsActivity::class.java))
        }

        binding.buttonProducts.setOnClickListener {
            startActivity(Intent(this, ProductServiceManagementActivity::class.java))
        }

        binding.buttonTeamKpi.setOnClickListener {
            startActivity(Intent(this, AdminCrmKpiActivity::class.java))
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
        binding.textRoleBadge.text = (session.getRole() ?: "MANAGER").uppercase()

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
        viewModel.loadManagerCrmDashboardAnalytics(
            fromDate = fromDateStr,
            toDate = toDateStr
        )
        viewModel.loadLiveTeamActivities()
    }

    private fun observeViewModel() {
        viewModel.managerCrmDashboard.observe(this) { data ->
            binding.swipeRefresh.isRefreshing = false
            if (data == null) return@observe

            // 9 Summary Cards
            binding.textTeamLeads.text = data.teamLeads.toString()
            binding.textNewLeads.text = data.newLeads.toString()
            binding.textTodayFollowUps.text = data.todayFollowUps.toString()
            binding.textPendingFollowUps.text = data.pendingFollowUps.toString()
            binding.textOverdueFollowUps.text = data.overdueFollowUps.toString()
            binding.textInterestedLeads.text = data.interestedLeads.toString()
            binding.textClosedLeads.text = data.closedLeads.toString()
            binding.textConversionRate.text = String.format("%.1f%%", data.conversionRate)
            binding.textKpiAchievement.text = String.format("%.1f%%", data.kpiAchievement)

            // 8 Visual Charts
            // 1. Conversion Funnel
            binding.chartConversionFunnel.setData(data.conversionFunnel)

            // 2. Status Distribution Donut
            val statusSlices = data.statusDistribution.map {
                DonutChartView.Slice(
                    label = it.label,
                    value = it.value.toFloat(),
                    color = safeParseColor(it.colorHex, Color.parseColor("#3B82F6"))
                )
            }
            binding.chartStatusDistribution.setData(statusSlices, "${data.teamLeads}", "Total Leads")
            binding.textStatusLegend.text = data.statusDistribution.joinToString(" • ") { "${it.label}: ${it.value.toInt()}" }

            // 3. Team Lead Trend
            val leadTrendBars = data.teamLeadTrend.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartTeamLeadTrend.setData(leadTrendBars)

            // 4. Employee Productivity
            val empBars = data.employeeProductivity.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartEmployeeProductivity.setData(empBars)

            // 5. KPI Achievement Breakdown
            val kpiBars = data.kpiAchievementBreakdown.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartKpiBreakdown.setData(kpiBars)

            // 6. Follow-up Performance
            val followUpBars = data.followUpPerformance.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartFollowUpPerformance.setData(followUpBars)

            // 7. Product / Service Performance
            val prodBars = data.productPerformance.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartProductPerformance.setData(prodBars)

            // 8. Lead Sources Donut
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
                binding.textEmptyLiveActivities.visibility = View.VISIBLE
                binding.recyclerLiveActivities.visibility = View.GONE
            } else {
                binding.textEmptyLiveActivities.visibility = View.GONE
                binding.recyclerLiveActivities.visibility = View.VISIBLE
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
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ManagerCrmDashboardActivity)
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
                    binding.textEmptyLiveActivities.visibility = View.GONE
                    binding.recyclerLiveActivities.visibility = View.VISIBLE
                    liveActivityAdapter.prependItem(activity)
                    binding.recyclerLiveActivities.scrollToPosition(0)

                    binding.textLiveCountBadge.text = "NEW ACTIVITY"
                    binding.textLiveCountBadge.postDelayed({
                        binding.textLiveCountBadge.text = "REAL-TIME"
                    }, 4000)

                    // Silently refresh metrics in background
                    viewModel.loadManagerCrmDashboardAnalytics(fromDate = fromDateStr, toDate = toDateStr)
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
