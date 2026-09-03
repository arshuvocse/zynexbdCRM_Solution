package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityUserCrmDashboardBinding
import com.zynexbd.crmsolution.models.ChartBarEntry
import com.zynexbd.crmsolution.models.ChartDonutSlice
import com.zynexbd.crmsolution.utils.SessionManager
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import com.zynexbd.crmsolution.views.BarChartView
import com.zynexbd.crmsolution.views.DonutChartView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UserCrmDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityUserCrmDashboardBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var session: SessionManager

    private var fromDateStr: String? = null
    private var toDateStr: String? = null
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserCrmDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        setupDateFilters()
        observeViewModel()

        loadDashboard()
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefresh.setOnClickListener { loadDashboard() }
        binding.swipeRefresh.setOnRefreshListener { loadDashboard() }

        binding.buttonMyLeads.setOnClickListener {
            startActivity(Intent(this, UserCrmLeadListActivity::class.java))
        }

        binding.cardTotalLeads.setOnClickListener {
            startActivity(Intent(this, UserCrmLeadListActivity::class.java))
        }

        binding.buttonCreateLead.setOnClickListener {
            startActivity(Intent(this, CreateLeadActivity::class.java).apply {
                putExtra("IS_MANAGER", false)
            })
        }

        binding.buttonMyFollowUps.setOnClickListener {
            startActivity(Intent(this, UserCrmFollowUpsActivity::class.java))
        }

        binding.cardTodayFollowUps.setOnClickListener {
            startActivity(Intent(this, UserCrmFollowUpsActivity::class.java).apply {
                putExtra("INITIAL_FILTER", "today")
            })
        }

        binding.buttonMyReports.setOnClickListener {
            startActivity(Intent(this, UserCrmReportsActivity::class.java))
        }

        binding.buttonViewFullKpi.setOnClickListener {
            startActivity(Intent(this, UserCrmKpiActivity::class.java))
        }

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
        binding.textRoleBadge.text = (session.getRole() ?: "USER").uppercase()

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
        viewModel.loadUserCrmDashboardAnalytics(
            fromDate = fromDateStr,
            toDate = toDateStr
        )
    }

    private fun observeViewModel() {
        viewModel.userCrmDashboardAnalytics.observe(this) { data ->
            binding.swipeRefresh.isRefreshing = false
            if (data == null) return@observe

            // 10 Summary Cards
            binding.textMyTotalLeads.text = data.myTotalLeads.toString()
            binding.textNewLeads.text = data.myNewLeads.toString()
            binding.textTodayFollowUps.text = data.todayFollowUps.toString()
            binding.textPendingFollowUps.text = data.pendingFollowUps.toString()
            binding.textOverdueFollowUps.text = data.overdueFollowUps.toString()
            binding.textInterestedLeads.text = data.interestedLeads.toString()
            binding.textClosedLeads.text = data.closedLeads.toString()
            binding.textDailyTarget.text = data.dailyFollowUpTarget.toString()
            binding.textDailyAchieved.text = data.dailyFollowUpAchieved.toString()

            // 5 Visual Charts
            // 1. Funnel
            binding.chartConversionFunnel.setData(data.myConversionFunnel)

            // 2. Status Distribution Donut
            val statusSlices = data.myLeadStatus.map {
                DonutChartView.Slice(
                    label = it.label,
                    value = it.value.toFloat(),
                    color = safeParseColor(it.colorHex, Color.parseColor("#3B82F6"))
                )
            }
            binding.chartStatusDistribution.setData(statusSlices, "${data.myTotalLeads}", "My Leads")
            binding.textStatusLegend.text = data.myLeadStatus.joinToString(" • ") { "${it.label}: ${it.value.toInt()}" }

            // 3. Monthly Trend
            val trendBars = data.myLeadTrend.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartMyLeadTrend.setData(trendBars)

            // 4. Follow-up Trend
            val followUpBars = data.myFollowUpTrend.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartMyFollowUpTrend.setData(followUpBars)

            // 5. KPI Achievement
            val kpiBars = data.myKpiAchievement.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartMyKpiAchievement.setData(kpiBars)
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
            }
        }
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
