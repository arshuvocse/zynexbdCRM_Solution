package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.VisitAdapter
import com.zynexbd.crmsolution.databinding.ActivityCustomerDetailsBinding
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.launch

class CustomerDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityCustomerDetailsBinding
    private lateinit var visitAdapter: VisitAdapter
    private var customerId: Int = 0
    private var lat: Double = 0.0
    private var lng: Double = 0.0
    private var mobileStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        customerId = intent.getIntExtra("CUSTOMER_ID", 0)
        val name = intent.getStringExtra("CUSTOMER_NAME") ?: "Customer Profile"
        mobileStr = intent.getStringExtra("CUSTOMER_MOBILE") ?: ""
        val address = intent.getStringExtra("CUSTOMER_ADDRESS") ?: ""
        lat = intent.getDoubleExtra("CUSTOMER_LAT", 0.0)
        lng = intent.getDoubleExtra("CUSTOMER_LNG", 0.0)
        val remarks = intent.getStringExtra("CUSTOMER_REMARKS")

        binding.textName.text = name
        binding.textMobile.text = mobileStr
        binding.textAddress.text = address
        binding.textGpsLocation.text = "জিপিএস অবস্থান: $lat, $lng"
        binding.textRemarks.text = if (!remarks.isNull_or_empty()) "মন্তব্য: $remarks" else "মন্তব্য: প্রযোজ্য নয়"

        setupVisitsList()

        binding.buttonCall.setOnClickListener {
            if (mobileStr.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobileStr"))
                startActivity(intent)
            }
        }

        binding.buttonNavigate.setOnClickListener {
            val uri = Uri.parse("google.navigation:q=$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            try {
                startActivity(mapIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "গুগল ম্যাপস অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonStartVisit.setOnClickListener {
            val intent = Intent(this, RecordVisitActivity::class.java).apply {
                putExtra("PRESELECTED_CUSTOMER_ID", customerId)
            }
            startActivity(intent)
        }

        loadVisitHistory()
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    private fun setupVisitsList() {
        visitAdapter = VisitAdapter()
        binding.recyclerViewVisits.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewVisits.adapter = visitAdapter
    }

    private fun loadVisitHistory() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@CustomerDetailsActivity)
                val response = api.getMyVisits(customerId = customerId)
                if (response.isSuccessful && response.body() != null) {
                    visitAdapter.submitList(response.body()!!)
                }
            } catch (e: Exception) {
                Toast.makeText(this@CustomerDetailsActivity, "ভিজিট ইতিহাস লোড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
