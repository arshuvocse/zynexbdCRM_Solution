package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityUserCrmDashboardBinding
import com.zynexbd.crmsolution.viewmodel.CrmViewModel

class UserCrmDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityUserCrmDashboardBinding
    private lateinit var viewModel: CrmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserCrmDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        viewModel.loadUserDashboard()
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonRefresh.setOnClickListener {
            viewModel.loadUserDashboard()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadUserDashboard()
        }

        // Quick Navigation Buttons
        binding.buttonMyLeads.setOnClickListener {
            startActivity(Intent(this, UserCrmLeadListActivity::class.java))
        }

        binding.buttonCreateLead.setOnClickListener {
            startActivity(Intent(this, CreateLeadActivity::class.java).apply {
                putExtra("IS_MANAGER", false)
            })
        }

        binding.buttonMyFollowUps.setOnClickListener {
            startActivity(Intent(this, UserCrmFollowUpsActivity::class.java))
        }

        binding.cardTotalLeads.setOnClickListener {
            startActivity(Intent(this, UserCrmLeadListActivity::class.java))
        }

        binding.cardTodayFollowUps.setOnClickListener {
            startActivity(Intent(this, UserCrmFollowUpsActivity::class.java).apply {
                putExtra("INITIAL_FILTER", "today")
            })
        }

        binding.buttonViewFullKpi.setOnClickListener {
            startActivity(Intent(this, UserCrmKpiActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.userDashboard.observe(this) { data ->
            if (data != null) {
                binding.textMyTotalLeads.text = data.myTotalLeads.toString()
                binding.textTodayFollowUps.text = data.todayFollowUps.toString()
                binding.textNewLeads.text = data.newLeads.toString()
                binding.textFollowUpLeads.text = data.followUpLeads.toString()
                binding.textInterestedLeads.text = data.interestedLeads.toString()
                binding.textClosedLeads.text = data.closedLeads.toString()

                // Daily Progress
                val dailyPct = data.dailyAchievementPercent
                binding.textDailyPercent.text = "${dailyPct}%"
                binding.progressDaily.progress = dailyPct.toInt().coerceIn(0, 100)
                binding.textDailyProgressText.text = "Follow-up Done: ${data.dailyFollowUpAchieved} / ${data.dailyFollowUpTarget}"
                applyBadgeStyle(binding.textDailyPercent, dailyPct)

                // Weekly Progress
                val weeklyPct = data.weeklyAchievementPercent
                binding.textWeeklyPercent.text = "${weeklyPct}%"
                binding.progressWeekly.progress = weeklyPct.toInt().coerceIn(0, 100)
                binding.textWeeklyProgressText.text = "Follow-up Done: ${data.weeklyFollowUpAchieved} / ${data.weeklyFollowUpTarget}"
                applyBadgeStyle(binding.textWeeklyPercent, weeklyPct)
            }
        }
    }

    private fun applyBadgeStyle(textView: android.widget.TextView, percent: Double) {
        when {
            percent >= 100.0 -> {
                textView.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_badge_success)
                textView.setTextColor(android.graphics.Color.WHITE)
            }
            percent >= 50.0 -> {
                textView.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_badge_primary)
                textView.setTextColor(android.graphics.Color.WHITE)
            }
            percent > 0.0 -> {
                textView.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_badge_warning)
                textView.setTextColor(android.graphics.Color.WHITE)
            }
            else -> {
                textView.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_badge_slate)
                textView.setTextColor(android.graphics.Color.parseColor("#475569"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUserDashboard()
    }
}
