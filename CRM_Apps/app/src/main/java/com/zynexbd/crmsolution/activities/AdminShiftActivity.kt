package com.zynexbd.crmsolution.activities

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.AdminShiftAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminShiftsBinding
import com.zynexbd.crmsolution.models.CreateShiftRequest
import com.zynexbd.crmsolution.models.Shift
import com.zynexbd.crmsolution.models.UpdateShiftRequest
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminShiftActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminShiftsBinding
    private lateinit var adapter: AdminShiftAdapter
    private var shiftList = mutableListOf<Shift>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminShiftsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadShifts()
    }

    private fun setupToolbar() {
        val navView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        setupAdminDrawer(binding.drawerLayout, navView, null, R.id.nav_shifts)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
    }

    private fun setupRecyclerView() {
        adapter = AdminShiftAdapter(
            onEditClick = { shift -> showEditShiftDialog(shift) },
            onDeleteClick = { shift -> showDeleteConfirmationDialog(shift) },
            onSetDefaultClick = { shift -> setDefaultShift(shift) }
        )
        binding.recyclerShifts.layoutManager = LinearLayoutManager(this)
        binding.recyclerShifts.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener { loadShifts() }
        binding.fabAddShift.visibility = View.GONE
    }

    private fun loadShifts() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminShiftActivity)
                val response = api.getShifts()
                if (response.isSuccessful && response.body() != null) {
                    shiftList = response.body()!!.toMutableList()
                    adapter.submitList(shiftList)
                    updateEmptyState()
                } else {
                    Toast.makeText(this@AdminShiftActivity, "Failed to load shifts", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminShiftActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateEmptyState() {
        if (shiftList.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerShifts.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerShifts.visibility = View.VISIBLE
        }
    }

    private fun showCreateShiftDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shift, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val textTitle = dialogView.findViewById<TextView>(R.id.dialogShiftTitle)
        val editName = dialogView.findViewById<EditText>(R.id.editShiftName)
        val btnStartTime = dialogView.findViewById<Button>(R.id.btnPickStartTime)
        val btnEndTime = dialogView.findViewById<Button>(R.id.btnPickEndTime)
        val editGrace = dialogView.findViewById<EditText>(R.id.editGracePeriod)
        val checkDefault = dialogView.findViewById<CheckBox>(R.id.checkboxIsDefault)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelShiftDialog)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveShift)

        textTitle.text = "➕ Add New Shift"
        editGrace.setText("15")

        var selectedStartTime = "09:00:00"
        var selectedEndTime = "18:00:00"

        btnStartTime.setOnClickListener {
            showTimePicker(9, 0) { raw, display ->
                selectedStartTime = raw
                btnStartTime.text = display
            }
        }

        btnEndTime.setOnClickListener {
            showTimePicker(18, 0) { raw, display ->
                selectedEndTime = raw
                btnEndTime.text = display
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = editName.text.toString().trim()
            if (name.isBlank()) {
                editName.error = "Shift name is required"
                return@setOnClickListener
            }
            val graceStr = editGrace.text.toString().trim()
            val grace = graceStr.toIntOrNull() ?: 15
            val isDefault = checkDefault.isChecked

            val request = CreateShiftRequest(
                shiftName = name,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                gracePeriodMinutes = grace,
                isDefault = isDefault
            )

            createShift(request)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditShiftDialog(shift: Shift) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shift, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val textTitle = dialogView.findViewById<TextView>(R.id.dialogShiftTitle)
        val editName = dialogView.findViewById<EditText>(R.id.editShiftName)
        val btnStartTime = dialogView.findViewById<Button>(R.id.btnPickStartTime)
        val btnEndTime = dialogView.findViewById<Button>(R.id.btnPickEndTime)
        val editGrace = dialogView.findViewById<EditText>(R.id.editGracePeriod)
        val checkDefault = dialogView.findViewById<CheckBox>(R.id.checkboxIsDefault)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelShiftDialog)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveShift)

        textTitle.text = "✏️ Edit Shift"
        editName.setText(shift.shiftName)
        editGrace.setText(shift.gracePeriodMinutes.toString())
        checkDefault.isChecked = shift.isDefault

        var selectedStartTime = shift.startTime
        var selectedEndTime = shift.endTime

        btnStartTime.text = formatDisplayTime(shift.startTime)
        btnEndTime.text = formatDisplayTime(shift.endTime)

        btnStartTime.setOnClickListener {
            val (h, m) = parseHoursMins(selectedStartTime)
            showTimePicker(h, m) { raw, display ->
                selectedStartTime = raw
                btnStartTime.text = display
            }
        }

        btnEndTime.setOnClickListener {
            val (h, m) = parseHoursMins(selectedEndTime)
            showTimePicker(h, m) { raw, display ->
                selectedEndTime = raw
                btnEndTime.text = display
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = editName.text.toString().trim()
            if (name.isBlank()) {
                editName.error = "Shift name is required"
                return@setOnClickListener
            }
            val graceStr = editGrace.text.toString().trim()
            val grace = graceStr.toIntOrNull() ?: 15
            val isDefault = checkDefault.isChecked

            val request = UpdateShiftRequest(
                shiftName = name,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                gracePeriodMinutes = grace,
                isDefault = isDefault,
                isActive = true
            )

            updateShift(shift.shiftId, request)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTimePicker(initialHour: Int, initialMinute: Int, onTimePicked: (raw: String, display: String) -> Unit) {
        val picker = TimePickerDialog(this, { _, hourOfDay, minute ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
            }
            val raw = String.format(Locale.US, "%02d:%02d:00", hourOfDay, minute)
            val display = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
            onTimePicked(raw, display)
        }, initialHour, initialMinute, false)
        picker.show()
    }

    private fun parseHoursMins(rawTime: String): Pair<Int, Int> {
        return try {
            val parts = rawTime.split(":")
            if (parts.size >= 2) {
                Pair(parts[0].toInt(), parts[1].toInt())
            } else Pair(9, 0)
        } catch (e: Exception) {
            Pair(9, 0)
        }
    }

    private fun formatDisplayTime(rawTime: String): String {
        return try {
            val sdfInput = SimpleDateFormat("HH:mm:ss", Locale.US)
            val date = sdfInput.parse(rawTime)
            if (date != null) {
                SimpleDateFormat("hh:mm a", Locale.US).format(date)
            } else rawTime
        } catch (e: Exception) {
            rawTime
        }
    }

    private fun createShift(request: CreateShiftRequest) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminShiftActivity)
                val response = api.createShift(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@AdminShiftActivity, "Shift created successfully!", Toast.LENGTH_SHORT).show()
                    loadShifts()
                } else {
                    Toast.makeText(this@AdminShiftActivity, "Failed to create shift", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminShiftActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateShift(id: Int, request: UpdateShiftRequest) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminShiftActivity)
                val response = api.updateShift(id, request)
                if (response.isSuccessful) {
                    Toast.makeText(this@AdminShiftActivity, "Shift updated successfully!", Toast.LENGTH_SHORT).show()
                    loadShifts()
                } else {
                    Toast.makeText(this@AdminShiftActivity, "Failed to update shift", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminShiftActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setDefaultShift(shift: Shift) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminShiftActivity)
                val response = api.setDefaultShift(shift.shiftId)
                if (response.isSuccessful) {
                    Toast.makeText(this@AdminShiftActivity, "${shift.shiftName} set as Default Shift!", Toast.LENGTH_SHORT).show()
                    loadShifts()
                } else {
                    Toast.makeText(this@AdminShiftActivity, "Failed to set default shift", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminShiftActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showDeleteConfirmationDialog(shift: Shift) {
        AlertDialog.Builder(this)
            .setTitle("Delete Shift")
            .setMessage("Are you sure you want to delete '${shift.shiftName}'?")
            .setPositiveButton("Delete") { _, _ -> deleteShift(shift.shiftId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteShift(id: Int) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AdminShiftActivity)
                val response = api.deleteShift(id)
                if (response.isSuccessful) {
                    Toast.makeText(this@AdminShiftActivity, "Shift deleted", Toast.LENGTH_SHORT).show()
                    loadShifts()
                } else {
                    Toast.makeText(this@AdminShiftActivity, "Failed to delete shift", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminShiftActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
