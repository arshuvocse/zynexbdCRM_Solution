package com.zynexbd.crmsolution.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.LiveTeamActivityAdapter
import com.zynexbd.crmsolution.databinding.ActivityTeamActivityHistoryBinding
import com.zynexbd.crmsolution.models.LiveTeamActivity
import com.zynexbd.crmsolution.utils.SessionManager
import com.zynexbd.crmsolution.utils.TeamActivityReportExporter
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TeamActivityHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamActivityHistoryBinding
    private lateinit var crmViewModel: CrmViewModel
    private lateinit var session: SessionManager

    private lateinit var activityAdapter: LiveTeamActivityAdapter
    private val allLoadedActivities = mutableListOf<LiveTeamActivity>()
    private val displayedActivities = mutableListOf<LiveTeamActivity>()

    private var currentFromDateIso: String? = null
    private var currentToDateIso: String? = null
    private var currentPeriodLabel: String = "This Month"
    private var selectedActionFilter: String = "All"
    private var currentSearchQuery: String = ""

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        crmViewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupPeriodFilters()
        setupActionFilters()
        setupSearch()
        setupExportButtons()
        observeViewModel()

        // Default to This Month
        selectPeriod("Month")
    }

    private fun setupToolbar() {
        binding.buttonBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        activityAdapter = LiveTeamActivityAdapter(
            onItemClick = { item ->
                handleActivityClick(item)
            }
        )
        binding.recyclerActivities.apply {
            layoutManager = LinearLayoutManager(this@TeamActivityHistoryActivity)
            adapter = activityAdapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadActivities()
        }
    }

    private fun handleActivityClick(item: LiveTeamActivity) {
        if (item.entityType.equals("Lead", ignoreCase = true) && item.targetEntityId != null && item.targetEntityId > 0) {
            val intent = Intent(this, LeadDetailsActivity::class.java).apply {
                putExtra("lead_id", item.targetEntityId)
            }
            startActivity(intent)
        } else if (item.entityType.equals("Kpi", ignoreCase = true) || item.actionType.startsWith("Kpi", ignoreCase = true)) {
            val userRole = session.getRole()
            if (userRole.equals("Admin", ignoreCase = true) || userRole.equals("Manager", ignoreCase = true)) {
                startActivity(Intent(this, AdminCrmKpiActivity::class.java))
            } else {
                Toast.makeText(this, "${item.title}\n${item.subtitle}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "${item.title}\n${item.subtitle}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPeriodFilters() {
        binding.chipGroupPeriod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipToday -> selectPeriod("Day")
                R.id.chipWeek -> selectPeriod("Week")
                R.id.chipMonth -> selectPeriod("Month")
                R.id.chipYear -> selectPeriod("Year")
                R.id.chipCustom -> openCustomDatePicker()
            }
        }
    }

    private fun selectPeriod(period: String) {
        val cal = Calendar.getInstance()
        val toDate = cal.time // Current moment

        when (period) {
            "Day" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val fromDate = cal.time

                currentFromDateIso = isoFormat.format(fromDate)
                currentToDateIso = isoFormat.format(toDate)
                currentPeriodLabel = "Today • " + SimpleDateFormat("dd MMM yyyy", Locale.US).format(fromDate)
            }
            "Week" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val fromDate = cal.time

                currentFromDateIso = isoFormat.format(fromDate)
                currentToDateIso = isoFormat.format(toDate)
                currentPeriodLabel = "This Week • " + SimpleDateFormat("dd MMM", Locale.US).format(fromDate) + " - " + SimpleDateFormat("dd MMM yyyy", Locale.US).format(toDate)
            }
            "Month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val fromDate = cal.time

                currentFromDateIso = isoFormat.format(fromDate)
                currentToDateIso = isoFormat.format(toDate)
                currentPeriodLabel = "This Month • " + SimpleDateFormat("MMMM yyyy", Locale.US).format(fromDate)
            }
            "Year" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val fromDate = cal.time

                currentFromDateIso = isoFormat.format(fromDate)
                currentToDateIso = isoFormat.format(toDate)
                currentPeriodLabel = "Year " + SimpleDateFormat("yyyy", Locale.US).format(fromDate)
            }
        }

        binding.textPeriodSummary.text = currentPeriodLabel
        loadActivities()
    }

    private fun openCustomDatePicker() {
        val cal = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val startCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }
                val endCal = Calendar.getInstance() // Up to now

                currentFromDateIso = isoFormat.format(startCal.time)
                currentToDateIso = isoFormat.format(endCal.time)
                currentPeriodLabel = "Since " + SimpleDateFormat("dd MMM yyyy", Locale.US).format(startCal.time)
                binding.textPeriodSummary.text = currentPeriodLabel

                loadActivities()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.setTitle("Select Start Date")
        datePicker.show()
    }

    private fun setupActionFilters() {
        binding.chipGroupActionType.setOnCheckedChangeListener { _, checkedId ->
            selectedActionFilter = when (checkedId) {
                R.id.chipActionLeads -> "LeadCreated"
                R.id.chipActionFollowUps -> "FollowUpAdded"
                R.id.chipActionStatus -> "StatusChanged"
                R.id.chipActionAssigned -> "LeadAssigned"
                R.id.chipActionVisits -> "CustomerVisit"
                R.id.chipActionKpi -> "Kpi"
                else -> "All"
            }
            applyClientSideFilters()
        }
    }

    private fun setupSearch() {
        binding.editSearchActivity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                binding.buttonClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyClientSideFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.buttonClearSearch.setOnClickListener {
            binding.editSearchActivity.text?.clear()
        }
    }

    private fun setupExportButtons() {
        binding.buttonExportExcel.setOnClickListener {
            TeamActivityReportExporter.exportToExcel(
                context = this,
                activities = displayedActivities,
                periodSubtitle = currentPeriodLabel
            )
        }

        binding.buttonExportPdf.setOnClickListener {
            val companyName = session.getCompanyName() ?: "CRM Enterprise"
            TeamActivityReportExporter.exportToPdf(
                activity = this,
                companyName = companyName,
                periodSubtitle = currentPeriodLabel,
                activities = displayedActivities
            )
        }
    }

    private fun loadActivities() {
        binding.swipeRefresh.isRefreshing = true
        crmViewModel.loadLiveTeamActivities(
            fromDate = currentFromDateIso,
            toDate = currentToDateIso,
            limit = 300
        )
    }

    private fun observeViewModel() {
        crmViewModel.liveTeamActivities.observe(this) { activities ->
            binding.swipeRefresh.isRefreshing = false
            allLoadedActivities.clear()
            if (activities != null) {
                allLoadedActivities.addAll(activities)
            }
            applyClientSideFilters()
        }
    }

    private fun applyClientSideFilters() {
        displayedActivities.clear()

        val filtered = allLoadedActivities.filter { item ->
            // Action Type Filter
            val matchesAction = when (selectedActionFilter) {
                "All" -> true
                "Kpi" -> item.entityType.equals("Kpi", ignoreCase = true) || item.actionType.startsWith("Kpi", ignoreCase = true)
                "CustomerVisit" -> item.actionType.equals("CustomerVisit", ignoreCase = true)
                "LeadAssigned" -> item.actionType.contains("Assigned", ignoreCase = true)
                else -> item.actionType.equals(selectedActionFilter, ignoreCase = true)
            }

            // Text Search Filter
            val matchesSearch = if (currentSearchQuery.isEmpty()) {
                true
            } else {
                item.userName.contains(currentSearchQuery, ignoreCase = true) ||
                item.title.contains(currentSearchQuery, ignoreCase = true) ||
                item.subtitle.contains(currentSearchQuery, ignoreCase = true) ||
                item.userRole.contains(currentSearchQuery, ignoreCase = true)
            }

            matchesAction && matchesSearch
        }

        displayedActivities.addAll(filtered)
        activityAdapter.submitList(displayedActivities.toList())

        // Update counts & empty state
        binding.textCountIndicator.text = "${displayedActivities.size} activities found"

        if (displayedActivities.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerActivities.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerActivities.visibility = View.VISIBLE
        }
    }
}
