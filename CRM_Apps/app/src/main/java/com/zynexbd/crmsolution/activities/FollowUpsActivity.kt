package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.FollowUpAdapter
import com.zynexbd.crmsolution.databinding.ActivityFollowUpsBinding
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.launch

class FollowUpsActivity : BaseActivity() {

    private lateinit var binding: ActivityFollowUpsBinding
    private lateinit var adapter: FollowUpAdapter
    private var currentCategory = "Today"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFollowUpsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonCategoryToday.setOnClickListener { selectCategory("Today") }
        binding.buttonCategoryTomorrow.setOnClickListener { selectCategory("Tomorrow") }
        binding.buttonCategoryUpcoming.setOnClickListener { selectCategory("Upcoming") }
        binding.buttonCategoryOverdue.setOnClickListener { selectCategory("Overdue") }

        binding.swipeRefresh.setOnRefreshListener {
            loadFollowUps()
        }

        selectCategory("Today")
    }

    private fun setupRecyclerView() {
        adapter = FollowUpAdapter(
            onCallClick = { item ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.mobile}"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "কল করা সম্ভব হচ্ছে না", Toast.LENGTH_SHORT).show()
                }
            },
            onVisitClick = { item ->
                val intent = Intent(this, RecordVisitActivity::class.java).apply {
                    putExtra("PRESELECTED_CUSTOMER_ID", item.customerId)
                }
                startActivity(intent)
            },
            onCompleteClick = { item ->
                completeFollowUp(item.visitId)
            }
        )
        binding.recyclerViewFollowUps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewFollowUps.adapter = adapter
    }

    private fun selectCategory(cat: String) {
        currentCategory = cat
        updateCategoryButtonsUI()
        loadFollowUps()
    }

    private fun updateCategoryButtonsUI() {
        val activeBg = R.drawable.bg_gradient_button
        val inactiveBg = R.drawable.bg_gradient_button_secondary
        val activeTextColor = 0xFFFFFFFF.toInt()
        val inactiveTextColor = ContextCompatHelper.getColor(this, R.color.colorPrimary)

        binding.buttonCategoryToday.setBackgroundResource(if (currentCategory == "Today") activeBg else inactiveBg)
        binding.buttonCategoryToday.setTextColor(if (currentCategory == "Today") activeTextColor else inactiveTextColor)

        binding.buttonCategoryTomorrow.setBackgroundResource(if (currentCategory == "Tomorrow") activeBg else inactiveBg)
        binding.buttonCategoryTomorrow.setTextColor(if (currentCategory == "Tomorrow") activeTextColor else inactiveTextColor)

        binding.buttonCategoryUpcoming.setBackgroundResource(if (currentCategory == "Upcoming") activeBg else inactiveBg)
        binding.buttonCategoryUpcoming.setTextColor(if (currentCategory == "Upcoming") activeTextColor else inactiveTextColor)

        binding.buttonCategoryOverdue.setBackgroundResource(if (currentCategory == "Overdue") activeBg else inactiveBg)
        binding.buttonCategoryOverdue.setTextColor(if (currentCategory == "Overdue") activeTextColor else inactiveTextColor)
    }

    private fun loadFollowUps() {
        val targetUserId = intent.getIntExtra("EXTRA_USER_ID", 0)
        val userName = intent.getStringExtra("EXTRA_USER_NAME")
        if (!userName.isNullOrBlank()) {
            binding.textTitle.text = "Follow-ups ($userName)"
        }

        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@FollowUpsActivity)
                val response = api.getFollowUps(
                    category = currentCategory,
                    targetUserId = if (targetUserId > 0) targetUserId else null
                )
                binding.swipeRefresh.isRefreshing = false
                if (response.isSuccessful && response.body() != null) {
                    adapter.submitList(response.body()!!)
                } else {
                    Toast.makeText(this@FollowUpsActivity, "ফলো-আপ তালিকা লোড করতে ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@FollowUpsActivity, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun completeFollowUp(visitId: Long) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@FollowUpsActivity)
                val response = api.completeFollowUp(visitId)
                if (response.isSuccessful) {
                    Toast.makeText(this@FollowUpsActivity, "ফলো-আপ সম্পন্ন হয়েছে বলে চিহ্নিত করা হলো", Toast.LENGTH_SHORT).show()
                    loadFollowUps()
                } else {
                    Toast.makeText(this@FollowUpsActivity, "ফলো-আপ স্ট্যাটাস আপডেট করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FollowUpsActivity, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

object ContextCompatHelper {
    fun getColor(context: android.content.Context, id: Int): Int {
        return androidx.core.content.ContextCompat.getColor(context, id)
    }
}
