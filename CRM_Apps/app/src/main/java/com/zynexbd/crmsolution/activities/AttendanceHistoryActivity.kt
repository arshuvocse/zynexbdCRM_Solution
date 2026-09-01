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
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.AttendanceListAdapter
import com.zynexbd.crmsolution.databinding.ActivityAttendanceHistoryBinding
import com.zynexbd.crmsolution.models.AttendanceResponse
import com.zynexbd.crmsolution.viewmodel.AttendanceHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AttendanceHistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityAttendanceHistoryBinding
    private lateinit var viewModel: AttendanceHistoryViewModel
    private lateinit var adapter: AttendanceListAdapter

    private var allRecords: List<AttendanceResponse> = emptyList()
    private var selectedMonth: Int? = Calendar.getInstance().get(Calendar.MONTH) + 1 // Default to Current Month (1..12)
    private var selectedYear: Int? = Calendar.getInstance().get(Calendar.YEAR)       // Default to Current Year

    private val monthNames = arrayOf(
        "All Months (সব মাস)", "January (জানুয়ারি)", "February (ফেব্রুয়ারি)", "March (মার্চ)",
        "April (এপ্রিল)", "May (মে)", "June (জুন)", "July (জুলাই)",
        "August (আগস্ট)", "September (সেপ্টেম্বর)", "October (অক্টোবর)",
        "November (নভেম্বর)", "December (ডিসেম্বর)"
    )

    private val years = arrayOf("All Years (সব বছর)", "2024", "2025", "2026", "2027", "2028")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AttendanceHistoryViewModel::class.java]

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonFilter.setOnClickListener { showFilterDialog() }

        // Direct Export Buttons
        binding.buttonMainExportExcel.setOnClickListener {
            val filteredRecords = getFilteredRecords()
            com.zynexbd.crmsolution.utils.ReportExporter.exportToExcel(this, filteredRecords, "My_Attendance_Report")
        }

        binding.buttonMainExportPdf.setOnClickListener {
            val filteredRecords = getFilteredRecords()
            val hasFilter = selectedMonth != null || selectedYear != null
            val subtitle = if (hasFilter) {
                val monthStr = if (selectedMonth != null) monthNames[selectedMonth!!].split(" ")[0] else "All Months"
                val yearStr = selectedYear?.toString() ?: "All Years"
                "$monthStr $yearStr"
            } else "All Records"
            com.zynexbd.crmsolution.utils.ReportExporter.exportToPdf(this, filteredRecords, "My Attendance Report", subtitle)
        }

        adapter = AttendanceListAdapter(showUserName = false)
        binding.recyclerAttendance.layoutManager = LinearLayoutManager(this)
        binding.recyclerAttendance.adapter = adapter

        viewModel.records.observe(this) { list ->
            allRecords = list ?: emptyList()
            applyFilter()
        }
        viewModel.error.observe(this) { message ->
            if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFilteredRecords(): List<AttendanceResponse> {
        return allRecords.filter { record ->
            matchesMonthAndYear(record.timestamp, selectedMonth, selectedYear)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load(selectedMonth, selectedYear)
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_filter, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val spinnerMonth = dialogView.findViewById<Spinner>(R.id.spinnerMonth)
        val spinnerYear = dialogView.findViewById<Spinner>(R.id.spinnerYear)
        val buttonClose = dialogView.findViewById<ImageButton>(R.id.buttonCloseDialog)
        val buttonReset = dialogView.findViewById<AppCompatButton>(R.id.buttonResetFilter)
        val buttonApply = dialogView.findViewById<AppCompatButton>(R.id.buttonApplySearch)
        val buttonExportExcel = dialogView.findViewById<AppCompatButton>(R.id.buttonExportExcel)
        val buttonExportPdf = dialogView.findViewById<AppCompatButton>(R.id.buttonExportPdf)

        // Populate Spinners
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthNames)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection(selectedMonth ?: 0)

        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        spinnerYear.adapter = yearAdapter
        val yearPos = if (selectedYear != null) years.indexOf(selectedYear.toString()).coerceAtLeast(0) else 0
        spinnerYear.setSelection(yearPos)

        buttonClose.setOnClickListener { dialog.dismiss() }

        buttonReset.setOnClickListener {
            selectedMonth = null
            selectedYear = null
            viewModel.load(null, null)
            dialog.dismiss()
        }

        buttonApply.setOnClickListener {
            val monthIndex = spinnerMonth.selectedItemPosition
            selectedMonth = if (monthIndex > 0) monthIndex else null

            val yearIndex = spinnerYear.selectedItemPosition
            selectedYear = if (yearIndex > 0) years[yearIndex].toIntOrNull() else null

            viewModel.load(selectedMonth, selectedYear)
            dialog.dismiss()
        }

        buttonExportExcel.setOnClickListener {
            val filtered = allRecords.filter { record ->
                matchesMonthAndYear(record.timestamp, selectedMonth, selectedYear)
            }
            com.zynexbd.crmsolution.utils.ReportExporter.exportToExcel(this, filtered, "My_Attendance_Report")
            dialog.dismiss()
        }

        buttonExportPdf.setOnClickListener {
            val filtered = allRecords.filter { record ->
                matchesMonthAndYear(record.timestamp, selectedMonth, selectedYear)
            }
            val hasFilter = selectedMonth != null || selectedYear != null
            val subtitle = if (hasFilter) {
                val monthStr = if (selectedMonth != null) monthNames[selectedMonth!!].split(" ")[0] else "All Months"
                val yearStr = selectedYear?.toString() ?: "All Years"
                "$monthStr, $yearStr"
            } else "All Records"
            com.zynexbd.crmsolution.utils.ReportExporter.exportToPdf(this, filtered, "My Attendance Report", subtitle)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyFilter() {
        val filtered = allRecords.filter { record ->
            matchesMonthAndYear(record.timestamp, selectedMonth, selectedYear)
        }
        adapter.submitList(filtered)

        // Update Subtitle & Empty State
        val hasFilter = selectedMonth != null || selectedYear != null
        if (hasFilter) {
            val monthStr = if (selectedMonth != null) monthNames[selectedMonth!!].split(" ")[0] else "All Months"
            val yearStr = selectedYear?.toString() ?: "All Years"
            binding.textFilterSubtitle.text = "Filtered: $monthStr, $yearStr (${filtered.size} records)"
            binding.textFilterSubtitle.setTextColor(getColor(R.color.colorPrimary))
        } else {
            binding.textFilterSubtitle.text = "Showing: All Records (${allRecords.size})"
            binding.textFilterSubtitle.setTextColor(getColor(R.color.text_hint))
        }

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerAttendance.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerAttendance.visibility = View.VISIBLE
        }
    }

    private fun matchesMonthAndYear(timestamp: String?, targetMonth: Int?, targetYear: Int?): Boolean {
        if (targetMonth == null && targetYear == null) return true
        if (timestamp.isNullOrBlank()) return false

        val formats = arrayOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()),
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        )
        for (sdf in formats) {
            try {
                val date = sdf.parse(timestamp)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    val itemMonth = cal.get(Calendar.MONTH) + 1 // 1..12
                    val itemYear = cal.get(Calendar.YEAR)
                    val monthMatch = targetMonth == null || targetMonth == itemMonth
                    val yearMatch = targetYear == null || targetYear == itemYear
                    return monthMatch && yearMatch
                }
            } catch (e: Exception) {}
        }

        if (targetYear != null && !timestamp.contains(targetYear.toString())) return false
        if (targetMonth != null) {
            val shortMonths = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthCode = "%02d".format(targetMonth)
            val monthName = shortMonths[targetMonth - 1]
            return timestamp.contains("-$monthCode-") || timestamp.contains("/$monthCode/") || timestamp.contains(monthName, ignoreCase = true)
        }
        return true
    }
}
