package com.zynexbd.crmsolution.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.AttendanceListAdapter
import com.zynexbd.crmsolution.adapters.AttendanceSummaryAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminAttendanceBinding
import com.zynexbd.crmsolution.models.AttendanceResponse
import com.zynexbd.crmsolution.models.EmployeeMonthlyAttendanceSummary
import com.zynexbd.crmsolution.models.User
import com.zynexbd.crmsolution.network.RetrofitClient
import com.zynexbd.crmsolution.utils.ReportExporter
import com.zynexbd.crmsolution.viewmodel.AdminAttendanceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminAttendanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminAttendanceBinding
    private lateinit var viewModel: AdminAttendanceViewModel
    private lateinit var logAdapter: AttendanceListAdapter
    private lateinit var summaryAdapter: AttendanceSummaryAdapter

    private var allRecords: List<AttendanceResponse> = emptyList()
    private var allSummaries: List<EmployeeMonthlyAttendanceSummary> = emptyList()
    private val employeeList = mutableListOf<User>()

    private var selectedMonth: Int? = Calendar.getInstance().get(Calendar.MONTH) + 1 // Default to Current Month (1..12)
    private var selectedYear: Int? = Calendar.getInstance().get(Calendar.YEAR)       // Default to Current Year
    private var selectedEmployeeName: String? = null
    private var selectedUserId: Int? = null

    private var selectedExactDate: Calendar? = null
    private var isSummaryTabActive = false

    private val monthNames = arrayOf(
        "All Months (সব মাস)", "January (জানুয়ারি)", "February (ফেব্রুয়ারি)", "March (মার্চ)",
        "April (এপ্রিল)", "May (মে)", "June (জুন)", "July (জুলাই)",
        "August (আগস্ট)", "September (সেপ্টেম্বর)", "October (অক্টোবর)",
        "November (নভেম্বর)", "December (ডিসেম্বর)"
    )

    private val years = arrayOf("All Years (সব বছর)", "2024", "2025", "2026", "2027", "2028")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AdminAttendanceViewModel::class.java]

        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_attendance)
        binding.buttonFilter.setOnClickListener { showFilterDialog() }
        binding.buttonChangeFilter.setOnClickListener { showFilterDialog() }

        // Main Screen Direct Export Buttons
        binding.buttonMainExportExcel.setOnClickListener {
            val filteredRecords = getFilteredRecords()
            val filteredSummaries = getFilteredSummaries()
            ReportExporter.exportToExcel(this, filteredRecords, "Attendance_Report", filteredSummaries)
        }

        binding.buttonMainExportPdf.setOnClickListener {
            val filteredRecords = getFilteredRecords()
            val filteredSummaries = getFilteredSummaries()
            val hasFilter = selectedExactDate != null || selectedMonth != null || selectedYear != null || selectedEmployeeName != null
            val subtitle = if (selectedExactDate != null) {
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(selectedExactDate!!.time)
                val empStr = selectedEmployeeName ?: "All Officers"
                "Date: $dateStr • $empStr"
            } else if (hasFilter) {
                val monthStr = if (selectedMonth != null) monthNames[selectedMonth!!].split(" ")[0] else "All Months"
                val yearStr = selectedYear?.toString() ?: "All Years"
                val empStr = selectedEmployeeName ?: "All Officers"
                "$monthStr $yearStr • $empStr"
            } else "All Records"
            ReportExporter.exportToPdf(this, filteredRecords, "Attendance Report", subtitle, filteredSummaries)
        }

        // Day-Wise Date Navigator Listeners
        updateDateNavigatorDisplay()

        binding.buttonPrevDay.setOnClickListener {
            if (selectedExactDate == null) selectedExactDate = Calendar.getInstance()
            selectedExactDate?.add(Calendar.DAY_OF_YEAR, -1)
            updateDateNavigatorDisplay()
            applyFilter()
        }

        binding.buttonNextDay.setOnClickListener {
            if (selectedExactDate == null) selectedExactDate = Calendar.getInstance()
            selectedExactDate?.add(Calendar.DAY_OF_YEAR, 1)
            updateDateNavigatorDisplay()
            applyFilter()
        }

        binding.buttonPickDate.setOnClickListener {
            val cal = selectedExactDate ?: Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, y, m, d ->
                    val chosen = Calendar.getInstance().apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, d)
                    }
                    selectedExactDate = chosen
                    updateDateNavigatorDisplay()
                    applyFilter()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.buttonAllDates.setOnClickListener {
            selectedExactDate = null
            updateDateNavigatorDisplay()
            applyFilter()
        }

        // Setup Adapters
        summaryAdapter = AttendanceSummaryAdapter()
        binding.recyclerSummary.layoutManager = LinearLayoutManager(this)
        binding.recyclerSummary.adapter = summaryAdapter

        logAdapter = AttendanceListAdapter(showUserName = true)
        binding.recyclerAttendance.layoutManager = LinearLayoutManager(this)
        binding.recyclerAttendance.adapter = logAdapter

        // Tab Switching: Default is Day-Wise Report
        switchTab(showSummary = false)
        binding.buttonTabSummary.setOnClickListener { switchTab(showSummary = true) }
        binding.buttonTabLogs.setOnClickListener { switchTab(showSummary = false) }

        viewModel.records.observe(this) { list ->
            allRecords = list ?: emptyList()
            applyFilter()
        }
        viewModel.error.observe(this) { message ->
            if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        loadEmployees()
    }

    private fun updateDateNavigatorDisplay() {
        val cal = selectedExactDate
        if (cal == null) {
            binding.textSelectedDateDisplay.text = "📅 All Days (সকল দিন) ▾"
            binding.buttonAllDates.visibility = View.GONE
        } else {
            val today = Calendar.getInstance()
            val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(cal.time)
            binding.textSelectedDateDisplay.text = if (isToday) "📅 Today, $dateStr ▾" else "📅 $dateStr ▾"
            binding.buttonAllDates.visibility = View.VISIBLE
        }
    }

    private fun switchTab(showSummary: Boolean) {
        isSummaryTabActive = showSummary
        if (showSummary) {
            binding.buttonTabSummary.setBackgroundResource(R.drawable.bg_gradient_button)
            binding.buttonTabSummary.setTextColor(getColor(R.color.text_on_primary))
            binding.buttonTabLogs.setBackgroundResource(android.R.color.transparent)
            binding.buttonTabLogs.setTextColor(getColor(R.color.text_secondary))

            binding.layoutDateNavigator.visibility = View.GONE
            binding.recyclerSummary.visibility = View.VISIBLE
            binding.recyclerAttendance.visibility = View.GONE
        } else {
            binding.buttonTabLogs.setBackgroundResource(R.drawable.bg_gradient_button)
            binding.buttonTabLogs.setTextColor(getColor(R.color.text_on_primary))
            binding.buttonTabSummary.setBackgroundResource(android.R.color.transparent)
            binding.buttonTabSummary.setTextColor(getColor(R.color.text_secondary))

            binding.layoutDateNavigator.visibility = View.VISIBLE
            binding.recyclerSummary.visibility = View.GONE
            binding.recyclerAttendance.visibility = View.VISIBLE
        }
        applyFilter()
    }

    private fun loadEmployees() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminAttendanceActivity)
                val resp = api.getUsers()
                if (resp.isSuccessful && resp.body() != null) {
                    employeeList.clear()
                    employeeList.addAll(resp.body()!!.filter { it.role != "Admin" })
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        viewModel.load(selectedMonth, selectedYear)
        loadMonthlySummary()
    }

    private fun loadMonthlySummary() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminAttendanceActivity)
                val resp = api.getMonthlyAttendanceSummary(
                    year = selectedYear,
                    month = selectedMonth,
                    userId = selectedUserId
                )
                if (resp.isSuccessful && resp.body() != null) {
                    allSummaries = resp.body()!!
                    applyFilter()
                } else {
                    android.util.Log.e("AdminAttendance", "Failed to load monthly summary: ${resp.code()} ${resp.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminAttendance", "Exception in loadMonthlySummary", e)
            }
        }
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_filter, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val spinnerMonth = dialogView.findViewById<Spinner>(R.id.spinnerMonth)
        val spinnerYear = dialogView.findViewById<Spinner>(R.id.spinnerYear)
        val spinnerEmployee = dialogView.findViewById<Spinner>(R.id.spinnerEmployee)
        val buttonClose = dialogView.findViewById<ImageButton>(R.id.buttonCloseDialog)
        val buttonReset = dialogView.findViewById<AppCompatButton>(R.id.buttonResetFilter)
        val buttonApply = dialogView.findViewById<AppCompatButton>(R.id.buttonApplySearch)
        val buttonExportExcel = dialogView.findViewById<AppCompatButton>(R.id.buttonExportExcel)
        val buttonExportPdf = dialogView.findViewById<AppCompatButton>(R.id.buttonExportPdf)

        // Populate Month Spinner
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthNames)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection(selectedMonth ?: 0)

        // Populate Year Spinner
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        spinnerYear.adapter = yearAdapter
        val yearPos = if (selectedYear != null) years.indexOf(selectedYear.toString()).coerceAtLeast(0) else 0
        spinnerYear.setSelection(yearPos)

        // Populate Employee Spinner
        val employeeOptions = mutableListOf("সকল কর্মকর্তা (All Employees)")
        employeeOptions.addAll(employeeList.map { it.name.ifBlank { it.username } })
        val employeeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, employeeOptions)
        spinnerEmployee.adapter = employeeAdapter

        val empPos = if (selectedEmployeeName != null) {
            val idx = employeeList.indexOfFirst { (it.name.ifBlank { it.username }) == selectedEmployeeName || it.username == selectedEmployeeName }
            if (idx >= 0) idx + 1 else 0
        } else 0
        spinnerEmployee.setSelection(empPos)

        buttonClose.setOnClickListener { dialog.dismiss() }

        buttonReset.setOnClickListener {
            selectedMonth = null
            selectedYear = null
            selectedEmployeeName = null
            selectedUserId = null
            refreshData()
            dialog.dismiss()
        }

        buttonApply.setOnClickListener {
            val monthIndex = spinnerMonth.selectedItemPosition
            selectedMonth = if (monthIndex > 0) monthIndex else null

            val yearIndex = spinnerYear.selectedItemPosition
            selectedYear = if (yearIndex > 0) years[yearIndex].toIntOrNull() else null

            val empIndex = spinnerEmployee.selectedItemPosition
            if (empIndex > 0 && empIndex <= employeeList.size) {
                val emp = employeeList[empIndex - 1]
                selectedEmployeeName = emp.name.ifBlank { emp.username }
                selectedUserId = emp.id
            } else {
                selectedEmployeeName = null
                selectedUserId = null
            }

            refreshData()
            dialog.dismiss()
        }

        buttonExportExcel.setOnClickListener {
            val filteredRecords = getFilteredRecords()
            val filteredSummaries = getFilteredSummaries()
            ReportExporter.exportToExcel(this, filteredRecords, "Attendance_Report", filteredSummaries)
            dialog.dismiss()
        }

        buttonExportPdf.setOnClickListener {
            val filteredRecords = getFilteredRecords()
            val filteredSummaries = getFilteredSummaries()
            val hasFilter = selectedMonth != null || selectedYear != null || selectedEmployeeName != null
            val subtitle = if (hasFilter) {
                val monthStr = if (selectedMonth != null) monthNames[selectedMonth!!].split(" ")[0] else "All Months"
                val yearStr = selectedYear?.toString() ?: "All Years"
                val empStr = selectedEmployeeName ?: "All Officers"
                "$monthStr $yearStr • $empStr"
            } else "All Records"
            ReportExporter.exportToPdf(this, filteredRecords, "Attendance Report", subtitle, filteredSummaries)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getFilteredRecords(): List<AttendanceResponse> {
        return allRecords.filter { record ->
            val dateMatch = if (selectedExactDate != null) {
                matchesExactDate(record.timestamp, selectedExactDate)
            } else {
                matchesMonthAndYear(record.timestamp, selectedMonth, selectedYear)
            }
            val empMatch = if (selectedEmployeeName.isNullOrBlank()) true
            else {
                val rUser = record.userName ?: ""
                rUser.contains(selectedEmployeeName!!, ignoreCase = true) ||
                        selectedEmployeeName!!.contains(rUser, ignoreCase = true)
            }
            dateMatch && empMatch
        }
    }

    private fun matchesExactDate(timestamp: String?, targetCal: Calendar?): Boolean {
        if (targetCal == null) return true
        if (timestamp.isNullOrBlank()) return false
        val (recDateStr, _) = ReportExporter.formatDateTime(timestamp)
        val targetDateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(targetCal.time)
        return recDateStr.equals(targetDateStr, ignoreCase = true)
    }

    private fun getFilteredSummaries(): List<EmployeeMonthlyAttendanceSummary> {
        return allSummaries.filter { s ->
            if (selectedUserId != null && selectedUserId!! > 0) {
                s.userId == selectedUserId
            } else if (!selectedEmployeeName.isNullOrBlank()) {
                val name = s.fullName.ifBlank { s.username }
                name.contains(selectedEmployeeName!!, ignoreCase = true) || s.username.contains(selectedEmployeeName!!, ignoreCase = true)
            } else {
                true
            }
        }
    }

    private fun applyFilter() {
        val filteredRecords = getFilteredRecords()
        val filteredSummaries = getFilteredSummaries()

        logAdapter.submitList(ArrayList(filteredRecords))
        summaryAdapter.submitList(ArrayList(filteredSummaries))

        // Update Subtitle & Empty State
        val hasFilter = selectedExactDate != null || selectedMonth != null || selectedYear != null || selectedEmployeeName != null
        if (selectedExactDate != null) {
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(selectedExactDate!!.time)
            val empStr = selectedEmployeeName ?: "All Officers"
            binding.textFilterSubtitle.text = "📅 $dateStr • $empStr (${filteredRecords.size} logs)"
        } else if (hasFilter) {
            val monthStr = if (selectedMonth != null) monthNames[selectedMonth!!].split(" ")[0] else "All Months"
            val yearStr = selectedYear?.toString() ?: "All Years"
            val empStr = selectedEmployeeName ?: "All Officers"
            binding.textFilterSubtitle.text = "📅 $monthStr $yearStr • $empStr (${filteredSummaries.size} Officers, ${filteredRecords.size} logs)"
        } else {
            binding.textFilterSubtitle.text = "📅 Showing: All Records (${filteredSummaries.size} Officers, ${allRecords.size} logs)"
        }

        val isEmpty = if (isSummaryTabActive) filteredSummaries.isEmpty() else filteredRecords.isEmpty()
        if (isEmpty) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerSummary.visibility = View.GONE
            binding.recyclerAttendance.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            if (isSummaryTabActive) {
                binding.recyclerSummary.visibility = View.VISIBLE
                binding.recyclerAttendance.visibility = View.GONE
            } else {
                binding.recyclerSummary.visibility = View.GONE
                binding.recyclerAttendance.visibility = View.VISIBLE
            }
        }
    }

    private fun matchesMonthAndYear(timestamp: String?, targetMonth: Int?, targetYear: Int?): Boolean {
        if (targetMonth == null && targetYear == null) return true
        if (timestamp.isNullOrBlank()) return false

        val localTz = java.util.TimeZone.getDefault()
        val utcTz = java.util.TimeZone.getTimeZone("UTC")

        val utcPatterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in utcPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utcTz }
                val date = sdf.parse(timestamp)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        timeZone = localTz
                    }
                    val itemMonth = cal.get(Calendar.MONTH) + 1
                    val itemYear = cal.get(Calendar.YEAR)

                    val monthMatches = (targetMonth == null || itemMonth == targetMonth)
                    val yearMatches = (targetYear == null || itemYear == targetYear)
                    if (monthMatches && yearMatches) return true
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        return false
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
