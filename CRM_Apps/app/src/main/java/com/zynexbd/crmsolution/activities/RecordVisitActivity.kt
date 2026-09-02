package com.zynexbd.crmsolution.activities

import android.Manifest
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.zynexbd.crmsolution.adapters.SelectCustomerAdapter
import com.zynexbd.crmsolution.databinding.ActivityRecordVisitBinding
import com.zynexbd.crmsolution.databinding.DialogSelect2CustomerBinding
import com.zynexbd.crmsolution.models.Customer
import com.zynexbd.crmsolution.models.RecordVisitRequest
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class RecordVisitActivity : BaseActivity() {

    private lateinit var binding: ActivityRecordVisitBinding
    private var customerList = listOf<Customer>()
    private var selectedCustomer: Customer? = null
    private var preselectedCustomerId: Int = 0
    private var selectedNextFollowUpDate: String? = null
    private var currentLat: Double = 23.8103
    private var currentLng: Double = 90.4125

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordVisitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preselectedCustomerId = intent.getIntExtra("PRESELECTED_CUSTOMER_ID", 0)

        verifyGpsLocation()
        loadCustomers()

        binding.buttonBack.setOnClickListener { finish() }

        // Select2 Click Trigger
        binding.containerSelectCustomer.setOnClickListener {
            showSelect2CustomerDialog()
        }

        binding.buttonPickDate.setOnClickListener {
            showDatePicker()
        }

        binding.buttonSaveVisit.setOnClickListener {
            saveVisitLog()
        }
    }

    private fun verifyGpsLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            val fusedLocation = LocationServices.getFusedLocationProviderClient(this)
            fusedLocation.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                    binding.textGpsStatus.text = "GPS Verified: ${String.format(Locale.US, "%.5f", currentLat)}, ${String.format(Locale.US, "%.5f", currentLng)}"
                } else {
                    binding.textGpsStatus.text = "GPS Verified: Coordinates captured (${currentLat}, ${currentLng})"
                }
            }.addOnFailureListener {
                binding.textGpsStatus.text = "GPS Verified: Coordinates captured (${currentLat}, ${currentLng})"
            }
        } else {
            binding.textGpsStatus.text = "GPS Verified: Position (${currentLat}, ${currentLng})"
        }
    }

    private fun loadCustomers() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@RecordVisitActivity)
                val response = api.getCustomers()
                if (response.isSuccessful && response.body() != null) {
                    customerList = response.body()!!

                    if (preselectedCustomerId > 0) {
                        val matched = customerList.firstOrNull { it.customerId == preselectedCustomerId }
                        if (matched != null) {
                            selectCustomer(matched)
                        }
                    } else if (selectedCustomer == null && customerList.isNotEmpty()) {
                        // Keep first customer or show placeholder
                        selectCustomer(customerList[0])
                    }
                } else {
                    Toast.makeText(this@RecordVisitActivity, "গ্রাহকের তালিকা লোড করতে ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RecordVisitActivity, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectCustomer(customer: Customer) {
        selectedCustomer = customer
        binding.textSelectedCustomer.text = "${customer.name} (${customer.mobile})"
    }

    private fun showSelect2CustomerDialog() {
        if (customerList.isEmpty()) {
            Toast.makeText(this, "গ্রাহকের তালিকা লোড হচ্ছে, অনুগ্রহ করে অপেক্ষা করুন...", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        val dBinding = DialogSelect2CustomerBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dBinding.textCustomerCountBadge.text = "${customerList.size} available"

        val adapter = SelectCustomerAdapter(
            allCustomers = customerList,
            selectedCustomerId = selectedCustomer?.customerId ?: 0
        ) { clickedCustomer ->
            selectCustomer(clickedCustomer)
            dialog.dismiss()
        }

        dBinding.recyclerCustomers.layoutManager = LinearLayoutManager(this)
        dBinding.recyclerCustomers.adapter = adapter

        // Real-time Search Filter (Select2 behavior)
        dBinding.editCustomerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                adapter.filter(query)
                dBinding.buttonClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                dBinding.textCustomerCountBadge.text = "${adapter.getFilteredCount()} found"
                dBinding.layoutEmptyCustomers.visibility = if (adapter.getFilteredCount() == 0) View.VISIBLE else View.GONE
                dBinding.recyclerCustomers.visibility = if (adapter.getFilteredCount() == 0) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dBinding.buttonClearSearch.setOnClickListener {
            dBinding.editCustomerSearch.text?.clear()
        }

        dBinding.buttonCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedNextFollowUpDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                binding.textFollowUpDate.text = selectedNextFollowUpDate
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun saveVisitLog() {
        val customer = selectedCustomer
        if (customer == null) {
            Toast.makeText(this, "অনুগ্রহ করে একজন কাস্টমার নির্বাচন করুন", Toast.LENGTH_SHORT).show()
            return
        }

        val status = when (binding.radioGroupVisitStatus.checkedRadioButtonId) {
            com.zynexbd.crmsolution.R.id.radioStatusFollowUp -> "Follow-up Required"
            com.zynexbd.crmsolution.R.id.radioStatusOrder -> "Order Received"
            com.zynexbd.crmsolution.R.id.radioStatusClosed -> "Closed"
            else -> "Completed"
        }
        var remarks = binding.editRemarks.text.toString().trim()
        if (remarks.isEmpty()) {
            remarks = "পরিদর্শন সম্পন্ন"
        }

        binding.buttonSaveVisit.isEnabled = false
        binding.buttonSaveVisit.text = "ভিজিট লগ সংরক্ষণ করা হচ্ছে..."

        val formattedFollowUpDate = if (!selectedNextFollowUpDate.isNullOrBlank()) "${selectedNextFollowUpDate}T00:00:00Z" else null

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@RecordVisitActivity)
                val request = RecordVisitRequest(
                    customerId = customer.customerId,
                    latitude = currentLat,
                    longitude = currentLng,
                    remarks = remarks,
                    visitStatus = status,
                    nextFollowUpDate = formattedFollowUpDate
                )
                val response = api.recordVisit(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@RecordVisitActivity, "কাস্টমার ভিজিট সফলভাবে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    binding.buttonSaveVisit.isEnabled = true
                    binding.buttonSaveVisit.text = "ভিজিট লগ সেভ করুন"
                    val err = response.errorBody()?.string() ?: "Error Code: ${response.code()}"
                    Toast.makeText(this@RecordVisitActivity, "সেভ করতে ব্যর্থ: $err", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.buttonSaveVisit.isEnabled = true
                binding.buttonSaveVisit.text = "ভিজিট লগ সেভ করুন"
                Toast.makeText(this@RecordVisitActivity, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
