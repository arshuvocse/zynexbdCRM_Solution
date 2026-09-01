package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.CustomerAdapter
import com.zynexbd.crmsolution.databinding.ActivityCustomerListBinding
import com.zynexbd.crmsolution.models.Customer
import com.zynexbd.crmsolution.network.RetrofitClient
import com.zynexbd.crmsolution.utils.LanguageManager
import kotlinx.coroutines.launch

class CustomerListActivity : BaseActivity() {

    private lateinit var binding: ActivityCustomerListBinding
    private lateinit var adapter: CustomerAdapter
    private var allCustomers = listOf<Customer>()
    private var targetUserId: Int = 0
    private var targetUserName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetUserId = intent.getIntExtra("EXTRA_USER_ID", 0)
        targetUserName = intent.getStringExtra("EXTRA_USER_NAME")

        updateHeaderUi()
        setupRecyclerView()

        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonAddCustomer.setOnClickListener {
            startActivity(Intent(this, AddCustomerActivity::class.java))
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadCustomers()
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCustomers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadCustomers()
    }

    private fun updateHeaderUi() {
        val isEn = LanguageManager.isEnglish(this)
        if (targetUserId > 0 && !targetUserName.isNullOrBlank()) {
            binding.textTitle.text = if (isEn) "$targetUserName's Customers" else "$targetUserName এর গ্রাহক"
            binding.textSubtitle.text = if (isEn) "Assigned Customers" else "নির্ধারিত গ্রাহক তালিকা"
            binding.textSubtitle.visibility = View.VISIBLE
        } else {
            binding.textTitle.text = if (isEn) "Customer Directory" else "গ্রাহক তালিকা"
            binding.textSubtitle.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
        loadCustomers()
    }

    private fun setupRecyclerView() {
        adapter = CustomerAdapter(
            onCustomerClick = { customer ->
                val intent = Intent(this, CustomerDetailsActivity::class.java).apply {
                    putExtra("CUSTOMER_ID", customer.customerId)
                    putExtra("CUSTOMER_NAME", customer.name)
                    putExtra("CUSTOMER_MOBILE", customer.mobile)
                    putExtra("CUSTOMER_ADDRESS", customer.address)
                    putExtra("CUSTOMER_LAT", customer.latitude)
                    putExtra("CUSTOMER_LNG", customer.longitude)
                    putExtra("CUSTOMER_REMARKS", customer.remarks)
                }
                startActivity(intent)
            },
            onCallClick = { customer ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.mobile}"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "কল করা সম্ভব হচ্ছে না", Toast.LENGTH_SHORT).show()
                }
            },
            onNavigateClick = { customer ->
                try {
                    val uri = Uri.parse("google.navigation:q=${customer.latitude},${customer.longitude}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    startActivity(mapIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "গুগল ম্যাপস অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            },
            onVisitClick = { customer ->
                val intent = Intent(this, RecordVisitActivity::class.java).apply {
                    putExtra("PRESELECTED_CUSTOMER_ID", customer.customerId)
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewCustomers.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewCustomers.adapter = adapter
    }

    private fun loadCustomers() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@CustomerListActivity)
                val response = if (targetUserId > 0) {
                    api.getCustomers(targetUserId = targetUserId)
                } else {
                    api.getCustomers()
                }
                binding.swipeRefresh.isRefreshing = false
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    allCustomers = if (targetUserId > 0) {
                        list.filter { it.createdByUserId == null || it.createdByUserId == targetUserId }
                    } else {
                        list
                    }
                    filterCustomers(binding.editSearch.text?.toString().orEmpty())
                } else {
                    val err = response.errorBody()?.string() ?: "Code: ${response.code()}"
                    Toast.makeText(this@CustomerListActivity, "গ্রাহকের তালিকা পেতে ব্যর্থ হয়েছে: $err", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@CustomerListActivity, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun filterCustomers(query: String) {
        if (query.isBlank()) {
            adapter.submitList(allCustomers)
        } else {
            val q = query.lowercase().trim()
            val filtered = allCustomers.filter {
                it.name.lowercase().contains(q) || it.mobile.contains(q) || it.address.lowercase().contains(q)
            }
            adapter.submitList(filtered)
        }
    }
}
