package com.zynexbd.crmsolution.activities

import android.app.DatePickerDialog
import android.os.Bundle
import com.zynexbd.crmsolution.R
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.AdminHolidayAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminHolidaysBinding
import com.zynexbd.crmsolution.databinding.DialogEditHolidayBinding
import com.zynexbd.crmsolution.models.CreateOrUpdateHolidayRequest
import com.zynexbd.crmsolution.models.Holiday
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminHolidaysActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminHolidaysBinding
    private lateinit var adapter: AdminHolidayAdapter

    private var allHolidays: List<Holiday> = emptyList()
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth: Int = 0 // 0 = All Months, 1..12 = Jan..Dec

    private val monthNames = arrayOf(
        "All Months", "January", "February", "March", "April",
        "May", "June", "July", "August", "September",
        "October", "November", "December"
    )

    private val years = (2024..2030).toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHolidaysBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFilters()
        setupListeners()
        loadHolidays()
    }

    private fun setupRecyclerView() {
        adapter = AdminHolidayAdapter(
            onEditClick = { holiday -> showAddOrEditHolidayDialog(holiday) },
            onToggleStatusClick = { holiday -> toggleHolidayStatus(holiday) },
            onDeleteClick = { holiday -> confirmDeleteHoliday(holiday) }
        )
        binding.recyclerViewHolidays.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewHolidays.adapter = adapter
    }

    private fun setupFilters() {
        // Month Spinner
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthNames)
        binding.spinnerMonth.adapter = monthAdapter
        binding.spinnerMonth.setSelection(0)
        binding.spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMonth = position
                applyLocalFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Year Spinner
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years.map { it.toString() })
        binding.spinnerYear.adapter = yearAdapter
        val currentYearIndex = years.indexOf(selectedYear).coerceAtLeast(0)
        binding.spinnerYear.setSelection(currentYearIndex)
        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedYear = years[position]
                loadHolidays()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, binding.buttonBack, R.id.nav_holidays)

        binding.btnAddHoliday.visibility = View.GONE
        binding.swipeRefresh.setOnRefreshListener { loadHolidays() }
    }

    private fun loadHolidays() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@AdminHolidaysActivity).getHolidays(year = selectedYear)
                binding.swipeRefresh.isRefreshing = false
                if (response.isSuccessful && response.body() != null) {
                    allHolidays = response.body()!!
                    applyLocalFilter()
                } else {
                    Toast.makeText(this@AdminHolidaysActivity, "Failed to load holidays.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@AdminHolidaysActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyLocalFilter() {
        val filtered = if (selectedMonth == 0) {
            allHolidays
        } else {
            allHolidays.filter { holiday ->
                val month = extractMonthFromDate(holiday.date)
                month == selectedMonth
            }
        }

        adapter.submitList(filtered)

        // Update stats
        val total = filtered.size
        val active = filtered.count { it.isActive }
        val inactive = filtered.count { !it.isActive }

        binding.chipTotalHolidays.text = "Total: $total"
        binding.chipActiveHolidays.text = "Active: $active"
        binding.chipInactiveHolidays.text = "Inactive: $inactive"

        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun extractMonthFromDate(dateStr: String): Int {
        val formats = arrayOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date = sdf.parse(dateStr)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    return cal.get(Calendar.MONTH) + 1
                }
            } catch (_: Exception) {}
        }
        return 0
    }

    private fun showAddOrEditHolidayDialog(holiday: Holiday?) {
        val dialogBinding = DialogEditHolidayBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var selectedDateString = ""

        if (holiday != null) {
            dialogBinding.dialogTitle.text = "Edit Holiday"
            dialogBinding.editHolidayName.setText(holiday.name)
            dialogBinding.editHolidayDesc.setText(holiday.description ?: "")
            dialogBinding.switchRecurring.isChecked = holiday.isRecurring
            dialogBinding.switchActive.isChecked = holiday.isActive

            val parsedDate = parseFormattedDate(holiday.date)
            selectedDateString = parsedDate
            dialogBinding.editHolidayDate.setText(parsedDate)
        } else {
            dialogBinding.dialogTitle.text = "Add New Holiday"
            dialogBinding.switchActive.isChecked = true
            dialogBinding.switchRecurring.isChecked = false
        }

        dialogBinding.editHolidayDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedDateString = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    dialogBinding.editHolidayDate.setText(selectedDateString)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.editHolidayName.text.toString().trim()
            val desc = dialogBinding.editHolidayDesc.text.toString().trim()
            val isRecurring = dialogBinding.switchRecurring.isChecked
            val isActive = dialogBinding.switchActive.isChecked

            if (name.isBlank()) {
                dialogBinding.editHolidayName.error = "Holiday name is required"
                return@setOnClickListener
            }

            if (selectedDateString.isBlank()) {
                dialogBinding.editHolidayDate.error = "Please select a date"
                return@setOnClickListener
            }

            val request = CreateOrUpdateHolidayRequest(
                name = name,
                date = selectedDateString,
                isRecurring = isRecurring,
                isActive = isActive,
                description = desc.ifBlank { null }
            )

            dialogBinding.btnSave.isEnabled = false
            dialogBinding.btnSave.text = "Saving..."

            lifecycleScope.launch {
                try {
                    val response = if (holiday == null) {
                        RetrofitClient.getApiService(this@AdminHolidaysActivity).createHoliday(request)
                    } else {
                        RetrofitClient.getApiService(this@AdminHolidaysActivity).updateHoliday(holiday.holidayId, request)
                    }

                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@AdminHolidaysActivity,
                            if (holiday == null) "Holiday created successfully!" else "Holiday updated successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        loadHolidays()
                    } else {
                        Toast.makeText(this@AdminHolidaysActivity, "Failed to save holiday.", Toast.LENGTH_SHORT).show()
                        dialogBinding.btnSave.isEnabled = true
                        dialogBinding.btnSave.text = "Save Holiday"
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@AdminHolidaysActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    dialogBinding.btnSave.isEnabled = true
                    dialogBinding.btnSave.text = "Save Holiday"
                }
            }
        }

        dialog.show()
    }

    private fun toggleHolidayStatus(holiday: Holiday) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@AdminHolidaysActivity).toggleHolidayStatus(holiday.holidayId)
                if (response.isSuccessful && response.body() != null) {
                    val updated = response.body()!!
                    allHolidays = allHolidays.map {
                        if (it.holidayId == updated.holidayId) updated else it
                    }
                    applyLocalFilter()
                    Toast.makeText(
                        this@AdminHolidaysActivity,
                        "${updated.name} is now ${if (updated.isActive) "Active" else "Inactive"}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@AdminHolidaysActivity, "Failed to toggle status.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminHolidaysActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteHoliday(holiday: Holiday) {
        AlertDialog.Builder(this)
            .setTitle("Delete Holiday")
            .setMessage("Are you sure you want to delete \"${holiday.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteHoliday(holiday)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteHoliday(holiday: Holiday) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@AdminHolidaysActivity).deleteHoliday(holiday.holidayId)
                if (response.isSuccessful) {
                    allHolidays = allHolidays.filter { it.holidayId != holiday.holidayId }
                    applyLocalFilter()
                    Toast.makeText(this@AdminHolidaysActivity, "Holiday deleted.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AdminHolidaysActivity, "Failed to delete holiday.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminHolidaysActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseFormattedDate(dateStr: String): String {
        val formats = arrayOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date = sdf.parse(dateStr)
                if (date != null) {
                    val outFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    return outFormat.format(date)
                }
            } catch (_: Exception) {}
        }
        return dateStr
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
