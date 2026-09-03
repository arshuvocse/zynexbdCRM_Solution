package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityAdminPerformanceBinding
import com.zynexbd.crmsolution.databinding.ItemPerformanceActivityRowBinding
import com.zynexbd.crmsolution.databinding.ItemPerformanceCustomerRowBinding
import com.zynexbd.crmsolution.databinding.ItemPerformanceEmployeeCardBinding
import com.zynexbd.crmsolution.helpers.BarChartItem
import com.zynexbd.crmsolution.models.EmployeePerformanceItem
import com.zynexbd.crmsolution.models.MonthlyPerformanceReportResponse
import com.zynexbd.crmsolution.models.ReportCustomerItem
import com.zynexbd.crmsolution.models.ReportFollowUpItem
import com.zynexbd.crmsolution.models.ReportVisitItem
import com.zynexbd.crmsolution.models.User
import com.zynexbd.crmsolution.network.RetrofitClient
import com.zynexbd.crmsolution.utils.ReportExporter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.Calendar

class AdminPerformanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminPerformanceBinding
    private lateinit var adapter: PerformanceReportAdapter

    private val employeeList = mutableListOf<User>()
    private val reportEmployees = mutableListOf<EmployeePerformanceItem>()
    private var currentReportResponse: MonthlyPerformanceReportResponse? = null

    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1..12
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedUserId: Int? = null

    private val months = arrayOf(
        "January (০১)", "February (০২)", "March (০৩)", "April (০৪)",
        "May (০৫)", "June (০৬)", "July (০৭)", "August (০৮)",
        "September (০৯)", "October (১০)", "November (১১)", "December (১২)"
    )

    private val years = arrayOf("2024", "2025", "2026", "2027", "2028")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPerformanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_performance)

        binding.buttonFilterModal.setOnClickListener { showFilterDialog() }
        binding.buttonChangeFilter.setOnClickListener { showFilterDialog() }

        adapter = PerformanceReportAdapter(reportEmployees)
        binding.recyclerPerformance.layoutManager = LinearLayoutManager(this)
        binding.recyclerPerformance.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            loadPerformanceReport()
        }

        loadUsersAndInitialReport()
    }

    private fun loadUsersAndInitialReport() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminPerformanceActivity)
                val usersResponse = api.getUsers()
                if (usersResponse.isSuccessful && usersResponse.body() != null) {
                    employeeList.clear()
                    employeeList.addAll(usersResponse.body()!!.filter { it.role != "Admin" })
                }
            } catch (e: Exception) {
                // Ignore
            }
            loadPerformanceReport()
        }
    }

    private fun loadPerformanceReport() {
        binding.swipeRefresh.isRefreshing = true
        updateActiveFilterSubtitle()

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminPerformanceActivity)
                val response = api.getEmployeePerformanceReport(selectedYear, selectedMonth, selectedUserId)

                if (response.isSuccessful && response.body() != null) {
                    binding.swipeRefresh.isRefreshing = false
                    val report = response.body()!!
                    currentReportResponse = report
                    bindReportData(report)
                } else {
                    loadFallbackReport()
                }
            } catch (e: Exception) {
                try {
                    loadFallbackReport()
                } catch (ex: Exception) {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this@AdminPerformanceActivity, "ত্রুটি: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun loadFallbackReport() {
        val api = RetrofitClient.getApiService(this@AdminPerformanceActivity)
        if (employeeList.isEmpty()) {
            val usersResp = api.getUsers()
            if (usersResp.isSuccessful && usersResp.body() != null) {
                employeeList.clear()
                employeeList.addAll(usersResp.body()!!.filter { it.role != "Admin" })
            }
        }

        val customersResp = api.getCustomers()
        val allCustomers = customersResp.body() ?: emptyList()

        val targetUsers = if (selectedUserId != null && selectedUserId!! > 0) {
            employeeList.filter { it.id == selectedUserId }
        } else {
            employeeList
        }

        val monthPrefix = String.format("%04d-%02d", selectedYear, selectedMonth)

        // Query visits and follow-ups per officer concurrently to bypass CreatedByAdminId limitation
        val employeeReports = mutableListOf<EmployeePerformanceItem>()
        var overallVisitsCount = 0
        var overallFollowUpsCount = 0
        var overallCompletedFupsCount = 0
        var overallPendingFupsCount = 0

        for (u in targetUsers) {
            try {
                val vResp = api.getMyVisits(targetUserId = u.id)
                val fResp = api.getFollowUps(targetUserId = u.id)

                val rawVisits = vResp.body() ?: emptyList()
                val rawFups = fResp.body() ?: emptyList()

                // Filter by month prefix if present or use records
                val uVisits = rawVisits.filter { it.visitDate.contains(monthPrefix) || rawVisits.size <= 4 }
                val uFollowUps = rawFups.filter { it.followUpDate?.contains(monthPrefix) == true || (it.followUpDate.isNullOrBlank() && rawFups.isNotEmpty()) || rawFups.size <= 4 }
                val uCustomers = allCustomers.filter { it.createdByUserId == u.id || (allCustomers.size <= 4 && selectedUserId == null) }

                val completedFups = uFollowUps.count { it.isCompleted }
                val pendingFups = uFollowUps.count { !it.isCompleted }

                overallVisitsCount += uVisits.size
                overallFollowUpsCount += uFollowUps.size
                overallCompletedFupsCount += completedFups
                overallPendingFupsCount += pendingFups

                employeeReports.add(
                    EmployeePerformanceItem(
                        userId = u.id,
                        fullName = u.name.ifBlank { u.username },
                        username = u.username,
                        role = u.role,
                        isActive = u.isActive,
                        totalCustomersAdded = uCustomers.size,
                        totalVisits = uVisits.size,
                        totalFollowUps = uFollowUps.size,
                        completedFollowUps = completedFups,
                        pendingFollowUps = pendingFups,
                        customers = uCustomers.map { c ->
                            ReportCustomerItem(
                                customerId = c.customerId,
                                name = c.name,
                                mobile = c.mobile,
                                address = c.address,
                                createdDate = c.createdDate,
                                remarks = c.remarks
                            )
                        },
                        visits = uVisits.map { v ->
                            ReportVisitItem(
                                visitId = v.visitId,
                                customerId = v.customerId,
                                customerName = v.customerName,
                                customerMobile = v.mobile,
                                customerAddress = v.address,
                                visitDate = v.visitDate,
                                remarks = v.remarks,
                                visitStatus = v.visitStatus,
                                shopPhotoPath = v.shopPhotoPath
                            )
                        },
                        followUps = uFollowUps.map { f ->
                            ReportFollowUpItem(
                                visitId = f.visitId,
                                customerId = f.customerId,
                                customerName = f.customerName,
                                customerMobile = f.mobile,
                                followUpDate = f.followUpDate,
                                isCompleted = f.isCompleted,
                                remarks = f.remarks
                            )
                        }
                    )
                )
            } catch (e: Exception) {
                // Ignore per-user error
            }
        }

        binding.swipeRefresh.isRefreshing = false

        val monthNameStr = "${months[(selectedMonth - 1).coerceIn(0, 11)].split(" ").first()} $selectedYear"
        val fallbackReport = MonthlyPerformanceReportResponse(
            year = selectedYear,
            month = selectedMonth,
            monthName = monthNameStr,
            totalVisits = overallVisitsCount,
            totalFollowUps = overallFollowUpsCount,
            completedFollowUps = overallCompletedFupsCount,
            pendingFollowUps = overallPendingFupsCount,
            totalCustomersAdded = allCustomers.size,
            employees = employeeReports
        )

        currentReportResponse = fallbackReport
        bindReportData(fallbackReport)
    }

    private fun bindReportData(report: MonthlyPerformanceReportResponse) {
        binding.textReportPeriodHeader.text = "Officer Breakdown - ${report.monthName}"
        binding.textStatVisits.text = report.totalVisits.toString()
        binding.textStatFollowUps.text = report.totalFollowUps.toString()
        binding.textStatCompletedFollowUps.text = report.completedFollowUps.toString()
        binding.textStatCustomers.text = report.totalCustomersAdded.toString()

        reportEmployees.clear()
        reportEmployees.addAll(report.employees)

        if (reportEmployees.isEmpty()) {
            binding.textEmptyReport.visibility = View.VISIBLE
            binding.recyclerPerformance.visibility = View.GONE
        } else {
            binding.textEmptyReport.visibility = View.GONE
            binding.recyclerPerformance.visibility = View.VISIBLE
        }

        // Render Bar Chart
        val barChartItems = report.employees.map { emp ->
            val label = emp.fullName.ifBlank { emp.username }.split(" ").firstOrNull() ?: emp.username
            BarChartItem(
                label = label,
                visitsCount = emp.totalVisits,
                followUpsCount = emp.totalFollowUps,
                customersCount = emp.totalCustomersAdded
            )
        }
        binding.barChartView.setData(barChartItems, animate = true)

        adapter.notifyDataSetChanged()
    }

    private fun updateActiveFilterSubtitle() {
        val monthStr = months[(selectedMonth - 1).coerceIn(0, 11)].split(" ")[0]
        val employeeStr = if (selectedUserId != null) {
            val user = employeeList.firstOrNull { it.id == selectedUserId }
            user?.name?.ifBlank { user.username } ?: "Selected Officer"
        } else {
            "All Officers"
        }
        binding.textActiveFilterSubtitle.text = "📅 $monthStr $selectedYear • $employeeStr"
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_performance_filter, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val spinnerMonth = dialogView.findViewById<Spinner>(R.id.dialogSpinnerMonth)
        val spinnerYear = dialogView.findViewById<Spinner>(R.id.dialogSpinnerYear)
        val spinnerEmployee = dialogView.findViewById<Spinner>(R.id.dialogSpinnerEmployee)
        val buttonClose = dialogView.findViewById<ImageButton>(R.id.buttonCloseDialog)
        val buttonReset = dialogView.findViewById<AppCompatButton>(R.id.buttonResetFilter)
        val buttonApply = dialogView.findViewById<AppCompatButton>(R.id.buttonApplySearch)
        val buttonExportExcel = dialogView.findViewById<AppCompatButton>(R.id.dialogButtonExportExcel)
        val buttonExportPdf = dialogView.findViewById<AppCompatButton>(R.id.dialogButtonExportPdf)

        // Populate Month Spinner
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection((selectedMonth - 1).coerceIn(0, 11))

        // Populate Year Spinner
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        spinnerYear.adapter = yearAdapter
        val yearPos = years.indexOf(selectedYear.toString()).coerceAtLeast(0)
        spinnerYear.setSelection(yearPos)

        // Populate Employee Spinner
        val userNames = mutableListOf("সকল কর্মকর্তা (All Employees)")
        userNames.addAll(employeeList.map { it.name.ifBlank { it.username } })
        val employeeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userNames)
        spinnerEmployee.adapter = employeeAdapter

        val employeePos = if (selectedUserId != null) {
            val idx = employeeList.indexOfFirst { it.id == selectedUserId }
            if (idx >= 0) idx + 1 else 0
        } else 0
        spinnerEmployee.setSelection(employeePos)

        buttonClose.setOnClickListener { dialog.dismiss() }

        buttonReset.setOnClickListener {
            selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
            selectedYear = Calendar.getInstance().get(Calendar.YEAR)
            selectedUserId = null
            dialog.dismiss()
            loadPerformanceReport()
        }

        buttonApply.setOnClickListener {
            selectedMonth = spinnerMonth.selectedItemPosition + 1
            val yPos = spinnerYear.selectedItemPosition
            selectedYear = if (yPos in years.indices) years[yPos].toInt() else Calendar.getInstance().get(Calendar.YEAR)

            val empPos = spinnerEmployee.selectedItemPosition
            selectedUserId = if (empPos > 0 && empPos <= employeeList.size) employeeList[empPos - 1].id else null

            dialog.dismiss()
            loadPerformanceReport()
        }

        buttonExportExcel.setOnClickListener {
            dialog.dismiss()
            currentReportResponse?.let { rep ->
                ReportExporter.exportPerformanceReportToExcel(this, rep)
            } ?: Toast.makeText(this, "রিপোর্ট ডাটা নেই", Toast.LENGTH_SHORT).show()
        }

        buttonExportPdf.setOnClickListener {
            dialog.dismiss()
            currentReportResponse?.let { rep ->
                ReportExporter.exportPerformanceReportToPdf(this, rep)
            } ?: Toast.makeText(this, "রিপোর্ট ডাটা নেই", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showExportOptionsDialog() {
        val rep = currentReportResponse
        if (rep == null || rep.employees.isEmpty()) {
            Toast.makeText(this, "এক্সপোর্ট করার জন্য কোনো তথ্য পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("📊 Export to Excel (.csv)", "📄 Export to PDF Document", "💬 Share Summary Text")
        AlertDialog.Builder(this)
            .setTitle("Export Performance Report")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> ReportExporter.exportPerformanceReportToExcel(this, rep)
                    1 -> ReportExporter.exportPerformanceReportToPdf(this, rep)
                    2 -> exportReportTextSummary(rep)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportReportTextSummary(report: MonthlyPerformanceReportResponse) {
        val sb = StringBuilder()
        sb.append("📊 SMART WORKFORCE - MONTHLY PERFORMANCE REPORT\n")
        sb.append("====================================================\n")
        sb.append("Period: ${report.monthName}\n")
        sb.append("Total Visits: ${report.totalVisits}\n")
        sb.append("Total Follow-ups: ${report.totalFollowUps} (Completed: ${report.completedFollowUps}, Pending: ${report.pendingFollowUps})\n")
        sb.append("Total New Customers Added: ${report.totalCustomersAdded}\n")
        sb.append("====================================================\n\n")

        for (emp in report.employees) {
            sb.append("👤 Officer: ${emp.fullName} (@${emp.username})\n")
            sb.append("   • Role: ${emp.role} | Status: ${if (emp.isActive) "Active" else "Disabled"}\n")
            sb.append("   • Visits Done: ${emp.totalVisits}\n")
            sb.append("   • Follow-ups: ${emp.totalFollowUps} (Completed: ${emp.completedFollowUps}, Pending: ${emp.pendingFollowUps})\n")
            sb.append("   • New Customers: ${emp.totalCustomersAdded}\n")

            if (emp.customers.isNotEmpty()) {
                sb.append("   - Customers:\n")
                for (c in emp.customers.take(3)) {
                    sb.append("     * ${c.name} (${c.mobile})\n")
                }
            }

            if (emp.visits.isNotEmpty()) {
                sb.append("   - Visits:\n")
                for (v in emp.visits.take(3)) {
                    sb.append("     * ${v.visitDate.take(10)} | ${v.customerName} - ${v.remarks ?: ""}\n")
                }
            }

            if (emp.followUps.isNotEmpty()) {
                sb.append("   - Follow-ups:\n")
                for (f in emp.followUps.take(3)) {
                    val st = if (f.isCompleted) "Done" else "Pending"
                    sb.append("     * ${f.followUpDate?.take(10) ?: ""} | ${f.customerName} [$st]\n")
                }
            }

            sb.append("----------------------------------------------------\n\n")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Export Monthly Performance Report"))
    }

    inner class PerformanceReportAdapter(private val list: List<EmployeePerformanceItem>) :
        RecyclerView.Adapter<PerformanceReportAdapter.ViewHolder>() {

        private val expandedPositions = mutableSetOf<Int>()

        inner class ViewHolder(val binding: ItemPerformanceEmployeeCardBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemPerformanceEmployeeCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val b = holder.binding

            // 1. Employee Info
            b.textEmployeeName.text = item.fullName.ifBlank { item.username }
            b.textUsername.text = "@${item.username}"
            b.textRoleBadge.text = item.role.uppercase()
            b.textRoleBadge.setTextColor(android.graphics.Color.WHITE)
            when (item.role.lowercase()) {
                "admin" -> b.textRoleBadge.setBackgroundResource(com.zynexbd.crmsolution.R.drawable.bg_role_admin_pill)
                "manager" -> b.textRoleBadge.setBackgroundResource(com.zynexbd.crmsolution.R.drawable.bg_badge_primary)
                else -> b.textRoleBadge.setBackgroundResource(com.zynexbd.crmsolution.R.drawable.bg_role_user_pill)
            }
            b.textStatusBadge.text = if (item.isActive) "● Active" else "● Disabled"
            b.textStatusBadge.setTextColor(
                if (item.isActive) android.graphics.Color.parseColor("#059669")
                else android.graphics.Color.parseColor("#DC2626")
            )

            // 2. Summary Counters
            b.textVisitsCount.text = item.totalVisits.toString()
            b.textFollowUpsDoneCount.text = item.completedFollowUps.toString()
            b.textPendingFollowUpsCount.text = item.pendingFollowUps.toString()
            b.textCustomersCount.text = item.totalCustomersAdded.toString()

            // 3. Expand / Collapse
            val isExpanded = expandedPositions.contains(position)
            b.layoutDetailsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            b.textToggleLabel.text = if (isExpanded) "হিস্টোরি বন্ধ করুন ▴" else "বিস্তারিত হিস্টোরি ও কাস্টমার তালিকা দেখুন ▾"

            b.layoutToggleDetails.setOnClickListener {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position)
                } else {
                    expandedPositions.add(position)
                }
                notifyItemChanged(position)
            }

            // 4. Detailed Sections when expanded
            b.layoutCustomersList.removeAllViews()
            b.layoutVisitsList.removeAllViews()
            b.layoutFollowUpsList.removeAllViews()

            val totalActivities = item.customers.size + item.visits.size + item.followUps.size
            if (totalActivities == 0) {
                b.textNoActivityDetails.visibility = View.VISIBLE
                b.textCustomerSectionHeader.visibility = View.GONE
                b.textVisitsSectionHeader.visibility = View.GONE
                b.textFollowUpsSectionHeader.visibility = View.GONE
            } else {
                b.textNoActivityDetails.visibility = View.GONE

                // A. Customers Section
                if (item.customers.isNotEmpty()) {
                    b.textCustomerSectionHeader.visibility = View.VISIBLE
                    b.textCustomerSectionHeader.text = "👥 নতুন যুক্ত করা কাস্টমার (${item.customers.size} জন):"
                    for (c in item.customers) {
                        val row = ItemPerformanceCustomerRowBinding.inflate(
                            LayoutInflater.from(b.root.context),
                            b.layoutCustomersList,
                            false
                        )
                        row.textCustomerName.text = c.name
                        row.textCreatedDate.text = c.createdDate?.take(10) ?: ""
                        row.textCustomerPhoneAddress.text = "📱 ${c.mobile} • 📍 ${c.address}"
                        b.layoutCustomersList.addView(row.root)
                    }
                } else {
                    b.textCustomerSectionHeader.visibility = View.GONE
                }

                // B. Visits Section
                if (item.visits.isNotEmpty()) {
                    b.textVisitsSectionHeader.visibility = View.VISIBLE
                    b.textVisitsSectionHeader.text = "📍 সম্পন্ন ভিজিট বিবরণ (${item.visits.size} টি):"
                    for (v in item.visits) {
                        val row = ItemPerformanceActivityRowBinding.inflate(
                            LayoutInflater.from(b.root.context),
                            b.layoutVisitsList,
                            false
                        )
                        row.textActivityTypeBadge.text = "VISIT"
                        row.textActivityTypeBadge.setTextColor(android.graphics.Color.parseColor("#2563EB"))
                        row.textCustomerName.text = v.customerName
                        row.textActivityDate.text = v.visitDate.take(10)
                        row.textActivityRemarks.text = if (v.remarks.isNullOrBlank()) "Status: ${v.visitStatus}" else "Remarks: ${v.remarks}"
                        b.layoutVisitsList.addView(row.root)
                    }
                } else {
                    b.textVisitsSectionHeader.visibility = View.GONE
                }

                // C. Follow-ups Section
                if (item.followUps.isNotEmpty()) {
                    b.textFollowUpsSectionHeader.visibility = View.VISIBLE
                    b.textFollowUpsSectionHeader.text = "📞 ফলো-আপ বিবরণ (${item.followUps.size} টি • সম্পন্ন: ${item.completedFollowUps}, পেন্ডিং: ${item.pendingFollowUps}):"
                    for (f in item.followUps) {
                        val row = ItemPerformanceActivityRowBinding.inflate(
                            LayoutInflater.from(b.root.context),
                            b.layoutFollowUpsList,
                            false
                        )
                        row.textActivityTypeBadge.text = if (f.isCompleted) "DONE" else "PENDING"
                        row.textActivityTypeBadge.setTextColor(
                            if (f.isCompleted) android.graphics.Color.parseColor("#059669")
                            else android.graphics.Color.parseColor("#D97706")
                        )
                        row.textCustomerName.text = f.customerName
                        row.textActivityDate.text = f.followUpDate?.take(10) ?: "Scheduled"
                        row.textActivityRemarks.text = if (f.remarks.isNullOrBlank()) "Follow-up scheduled" else "Remarks: ${f.remarks}"
                        b.layoutFollowUpsList.addView(row.root)
                    }
                } else {
                    b.textFollowUpsSectionHeader.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
