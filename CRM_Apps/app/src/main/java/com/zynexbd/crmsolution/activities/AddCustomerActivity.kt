package com.zynexbd.crmsolution.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.zynexbd.crmsolution.databinding.ActivityAddCustomerBinding
import com.zynexbd.crmsolution.models.CreateCustomerRequest
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.launch

class AddCustomerActivity : BaseActivity() {

    private lateinit var binding: ActivityAddCustomerBinding
    private var currentLat: Double = 23.8103
    private var currentLng: Double = 90.4125

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        captureCurrentLocation()

        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonSaveCustomer.setOnClickListener {
            saveCustomer()
        }
    }

    private fun captureCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            val fusedLocation = LocationServices.getFusedLocationProviderClient(this)
            fusedLocation.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                    binding.textGpsStatus.text = "GPS Location: ${String.format("%.5f", currentLat)}, ${String.format("%.5f", currentLng)}"
                } else {
                    binding.textGpsStatus.text = "GPS Location: Default captured (${currentLat}, ${currentLng})"
                }
            }.addOnFailureListener {
                binding.textGpsStatus.text = "GPS Location: Default captured (${currentLat}, ${currentLng})"
            }
        } else {
            binding.textGpsStatus.text = "GPS Location: Default (${currentLat}, ${currentLng})"
        }
    }

    private fun saveCustomer() {
        val name = binding.editCustomerName.text.toString().trim()
        val mobile = binding.editMobile.text.toString().trim()
        val email = binding.editEmail.text.toString().trim()
        val address = binding.editAddress.text.toString().trim()
        val remarks = binding.editRemarks.text.toString().trim()

        if (name.isEmpty()) {
            binding.editCustomerName.error = "গ্রাহকের নাম পূরণ করা আবশ্যক"
            binding.editCustomerName.requestFocus()
            return
        }

        if (mobile.isEmpty()) {
            binding.editMobile.error = "মোবাইল নম্বর পূরণ করা আবশ্যক"
            binding.editMobile.requestFocus()
            return
        }

        // Mobile validation: Must be exactly 11 digits starting with 01
        if (mobile.length != 11 || !mobile.matches(Regex("""^01\d{9}$"""))) {
            binding.editMobile.error = "সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন (যেমন: 017xxxxxxxx)"
            binding.editMobile.requestFocus()
            return
        }

        // Email validation (optional field, but if provided must be valid email)
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.editEmail.error = "সঠিক ইমেইল ঠিকানা দিন (যেমন: name@example.com)"
            binding.editEmail.requestFocus()
            return
        }

        if (address.isEmpty()) {
            binding.editAddress.error = "ঠিকানা পূরণ করা আবশ্যক"
            binding.editAddress.requestFocus()
            return
        }

        binding.buttonSaveCustomer.isEnabled = false
        binding.buttonSaveCustomer.text = "সংরক্ষণ করা হচ্ছে..."

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@AddCustomerActivity)
                val request = CreateCustomerRequest(
                    name = name,
                    mobile = mobile,
                    email = if (email.isNotEmpty()) email else null,
                    address = address,
                    latitude = currentLat,
                    longitude = currentLng,
                    remarks = if (remarks.isNotEmpty()) remarks else null
                )
                val response = api.createCustomer(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@AddCustomerActivity, "কাস্টমার তথ্য সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    binding.buttonSaveCustomer.isEnabled = true
                    binding.buttonSaveCustomer.text = "কাস্টমার সেভ করুন"
                    val errorMsg = try {
                        val raw = response.errorBody()?.string() ?: ""
                        val json = org.json.JSONObject(raw)
                        json.optString("message", "কাস্টমার তথ্য সেভ করতে ব্যর্থ হয়েছে")
                    } catch (e: Exception) {
                        "কাস্টমার তথ্য সেভ করতে ব্যর্থ হয়েছে"
                    }
                    androidx.appcompat.app.AlertDialog.Builder(this@AddCustomerActivity)
                        .setTitle("⚠️ সতর্কবার্তা")
                        .setMessage(errorMsg)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("ঠিক আছে", null)
                        .show()
                }
            } catch (e: Exception) {
                binding.buttonSaveCustomer.isEnabled = true
                binding.buttonSaveCustomer.text = "কাস্টমার সেভ করুন"
                Toast.makeText(this@AddCustomerActivity, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
