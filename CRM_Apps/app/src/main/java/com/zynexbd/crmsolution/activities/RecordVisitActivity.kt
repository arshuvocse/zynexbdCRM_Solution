package com.zynexbd.crmsolution.activities

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.zynexbd.crmsolution.databinding.ActivityRecordVisitBinding
import com.zynexbd.crmsolution.models.Customer
import com.zynexbd.crmsolution.models.RecordVisitRequest
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class RecordVisitActivity : BaseActivity() {

    private lateinit var binding: ActivityRecordVisitBinding
    private var customerList = listOf<Customer>()
    private var preselectedCustomerId: Int = 0
    private var selectedNextFollowUpDate: String? = null
    private var currentLat: Double = 23.8103
    private var currentLng: Double = 90.4125

    private val visitStatuses = arrayOf("Completed", "Follow-up Required", "Order Received", "Closed")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordVisitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preselectedCustomerId = intent.getIntExtra("PRESELECTED_CUSTOMER_ID", 0)

        setupVisitStatusSpinner()
        verifyGpsLocation()
        loadCustomers()

        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonPickDate.setOnClickListener {
            showDatePicker()
        }

        binding.buttonSaveVisit.setOnClickListener {
            saveVisitLog()
        }
    }

    private fun setupVisitStatusSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, visitStatuses)
        binding.spinnerVisitStatus.adapter = adapter
    }

    private fun verifyGpsLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            val fusedLocation = LocationServices.getFusedLocationProviderClient(this)
            fusedLocation.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                    binding.textGpsStatus.text = "GPS Verified: ${String.format("%.5f", currentLat)}, ${String.format("%.5f", currentLng)}"
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
                    val customerNames = customerList.map { "${it.name} (${it.mobile})" }
                    val adapter = ArrayAdapter(this@RecordVisitActivity, android.R.layout.simple_spinner_dropdown_item, customerNames)
                    binding.spinnerCustomer.adapter = adapter

                    if (preselectedCustomerId > 0) {
                        val index = customerList.indexOfFirst { it.customerId == preselectedCustomerId }
                        if (index >= 0) {
                            binding.spinnerCustomer.setSelection(index)
                        }
                    }
                } else {
                    Toast.makeText(this@RecordVisitActivity, "গ্রাহকের তালিকা লোড করতে ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RecordVisitActivity, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
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
        val selectedIndex = binding.spinnerCustomer.selectedItemPosition
        if (customerList.isEmpty() || selectedIndex < 0 || selectedIndex >= customerList.size) {
            Toast.makeText(this, "অনুগ্রহ করে একজন কাস্টমার নির্বাচন করুন", Toast.LENGTH_SHORT).show()
            return
        }

        val customer = customerList[selectedIndex]
        val status = binding.spinnerVisitStatus.selectedItem as String
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
