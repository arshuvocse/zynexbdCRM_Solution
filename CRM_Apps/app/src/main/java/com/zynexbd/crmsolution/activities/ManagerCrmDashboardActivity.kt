package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.zynexbd.crmsolution.databinding.ActivityManagerCrmDashboardBinding
import com.zynexbd.crmsolution.models.ChartBarEntry
import com.zynexbd.crmsolution.models.ChartDonutSlice
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import com.zynexbd.crmsolution.views.BarChartView
import com.zynexbd.crmsolution.views.DonutChartView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ManagerCrmDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerCrmDashboardBinding
    private lateinit var viewModel: CrmViewModel

    private var fromDateStr: String? = null
    private var toDateStr: String? = null

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerCrmDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupListeners()
        setupDateFilters()
        observeViewModel()

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

        binding.buttonTeamKpi.setOnClickListener {
            startActivity(Intent(this, AdminCrmKpiActivity::class.java))
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
