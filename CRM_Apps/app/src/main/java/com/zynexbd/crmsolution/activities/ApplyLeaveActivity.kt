package com.zynexbd.crmsolution.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.zynexbd.crmsolution.databinding.ActivityApplyLeaveBinding
import com.zynexbd.crmsolution.models.ApplyLeaveRequest
import com.zynexbd.crmsolution.models.LeaveBalance
import com.zynexbd.crmsolution.models.LeaveType
import com.zynexbd.crmsolution.viewmodel.ApplyLeaveViewModel
import java.util.Calendar
import java.util.Locale

class ApplyLeaveActivity : BaseActivity() {

    private lateinit var binding: ActivityApplyLeaveBinding
    private lateinit var viewModel: ApplyLeaveViewModel

    private var leaveTypes: List<LeaveType> = emptyList()
    private var balances: List<LeaveBalance> = emptyList()
    private var startDate: String? = null
    private var endDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplyLeaveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ApplyLeaveViewModel::class.java]
        binding.buttonBack.setOnClickListener { finish() }

        binding.editStartDate.setOnClickListener { pickDate { date -> startDate = date; binding.editStartDate.setText(date) } }
        binding.editEndDate.setOnClickListener { pickDate { date -> endDate = date; binding.editEndDate.setText(date) } }

        binding.spinnerLeaveType.setOnItemSelectedListenerCompat { position ->
            updateBalanceLabel(position)
        }

        binding.buttonSubmit.setOnClickListener { submit() }

        viewModel.leaveTypes.observe(this) { types ->
            leaveTypes = types
            binding.spinnerLeaveType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types.map { it.name })
            updateBalanceLabel(binding.spinnerLeaveType.selectedItemPosition)
        }
        viewModel.balances.observe(this) { balances = it; updateBalanceLabel(binding.spinnerLeaveType.selectedItemPosition) }
        viewModel.error.observe(this) { message ->
            if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.loadTypesAndBalances()
    }

    private fun updateBalanceLabel(position: Int) {
        val type = leaveTypes.getOrNull(position) ?: return
        val balance = balances.firstOrNull { it.leaveTypeId == type.id }
        binding.textBalance.text = if (balance != null) {
            "অবশিষ্ট ছুটি: ${balance.totalDays} দিনের মধ্যে ${balance.remainingDays} দিন"
        } else {
            "অবশিষ্ট ছুটি: ${type.defaultDaysPerYear} দিন (এখনো ছুটি নেওয়া হয়নি)"
        }
    }

    private fun pickDate(onPicked: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                onPicked(date)
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun submit() {
        val type = leaveTypes.getOrNull(binding.spinnerLeaveType.selectedItemPosition)
        val reason = binding.editReason.text.toString().trim()

        if (type == null) {
            Toast.makeText(this, "অনুগ্রহ করে ছুটির ধরন নির্বাচন করুন।", Toast.LENGTH_SHORT).show()
            return
        }
        if (startDate == null || endDate == null) {
            Toast.makeText(this, "অনুগ্রহ করে ছুটির শুরুর এবং শেষ তারিখ নির্বাচন করুন।", Toast.LENGTH_SHORT).show()
            return
        }
        if (reason.isBlank()) {
            Toast.makeText(this, "অনুগ্রহ করে ছুটির কারণ উল্লেখ করুন।", Toast.LENGTH_SHORT).show()
            return
        }

        binding.buttonSubmit.isEnabled = false
        binding.buttonSubmit.text = "আবেদন জমা নেওয়া হচ্ছে..."
        val request = ApplyLeaveRequest(type.id, startDate!!, endDate!!, reason)
        viewModel.apply(request) { success, error ->
            binding.buttonSubmit.isEnabled = true
            binding.buttonSubmit.text = "SUBMIT LEAVE APPLICATION"
            if (success) {
                Toast.makeText(this, "ছুটির আবেদন সফলভাবে জমা দেওয়া হয়েছে।", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, error ?: "ছুটির আবেদন জমা দিতে ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun android.widget.Spinner.setOnItemSelectedListenerCompat(onSelected: (Int) -> Unit) {
    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
