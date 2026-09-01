package com.zynexbd.crmsolution.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityUserCrmKpiBinding
import com.zynexbd.crmsolution.models.UserKpiPerformance
import com.zynexbd.crmsolution.viewmodel.CrmViewModel

class UserCrmKpiActivity : BaseActivity() {

    private lateinit var binding: ActivityUserCrmKpiBinding
    private lateinit var viewModel: CrmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserCrmKpiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        viewModel.loadUserKpiPerformance()
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadUserKpiPerformance()
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

        viewModel.userKpiPerformance.observe(this) { list ->
            renderKpiCards(list)
        }
    }

    private fun renderKpiCards(list: List<UserKpiPerformance>) {
        binding.layoutKpiContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (kpi in list) {
            val cardView = inflater.inflate(R.layout.item_crm_productivity_row, binding.layoutKpiContainer, false) as MaterialCardView

            val textTitle = cardView.findViewById<TextView>(R.id.textEmpName)
            val textAchievement = cardView.findViewById<TextView>(R.id.textAchievementPercent)
            val progressBar = cardView.findViewById<ProgressBar>(R.id.progressKpi)
            val textFollowUp = cardView.findViewById<TextView>(R.id.textFollowUpDoneTarget)
            val textInterested = cardView.findViewById<TextView>(R.id.textInterestedDoneTarget)
            val textClosed = cardView.findViewById<TextView>(R.id.textClosedDoneTarget)

            textTitle.text = "${kpi.periodType} Target"
            textAchievement.text = "${kpi.overallAchievementPercent}%"
            progressBar.progress = kpi.overallAchievementPercent.toInt().coerceIn(0, 100)

            textFollowUp.text = "Follow-up: ${kpi.followUpDone}/${kpi.followUpTarget} (${kpi.followUpAchievementPercent}%)"
            textInterested.text = "Interested: ${kpi.interestedDone}/${kpi.interestedTarget} (${kpi.interestedAchievementPercent}%)"
            textClosed.text = "Closed: ${kpi.closedDone}/${kpi.closedTarget} (${kpi.closedAchievementPercent}%)"

            binding.layoutKpiContainer.addView(cardView)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUserKpiPerformance()
    }
}
