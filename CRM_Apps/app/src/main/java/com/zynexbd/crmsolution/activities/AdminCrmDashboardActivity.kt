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
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import com.zynexbd.crmsolution.views.BarChartView
import com.zynexbd.crmsolution.views.DonutChartView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminCrmDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminCrmDashboardBinding
    private lateinit var viewModel: CrmViewModel

    private var fromDateStr: String? = null
    private var toDateStr: String? = null
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCrmDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupAdminDrawer(binding.drawerLayout, binding.navigationView, binding.buttonMenu, R.id.nav_crm_dashboard)
        setupUI()
        setupDateFilters()
        observeViewModel()

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

        binding.buttonProductivityReport.setOnClickListener {
            startActivity(Intent(this, AdminCrmProductivityActivity::class.java))
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
            val followUpBars = data.followUpTrend.map {
                BarChartView.BarEntry(it.label, it.primaryValue.toFloat(), it.secondaryValue.toFloat())
            }
            binding.chartFollowUpTrend.setData(followUpBars)

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
