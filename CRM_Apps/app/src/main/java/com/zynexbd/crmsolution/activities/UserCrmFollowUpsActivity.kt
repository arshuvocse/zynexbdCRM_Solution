package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.CrmFollowUpItemAdapter
import com.zynexbd.crmsolution.databinding.ActivityCrmFollowUpsBinding
import com.zynexbd.crmsolution.viewmodel.CrmViewModel

class UserCrmFollowUpsActivity : BaseActivity() {

    private lateinit var binding: ActivityCrmFollowUpsBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var followUpAdapter: CrmFollowUpItemAdapter

    private var activeFilterType: String = "today"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrmFollowUpsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        activeFilterType = intent.getStringExtra("INITIAL_FILTER") ?: "today"
        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        loadFollowUps()
    }

    private fun setupUI() {
        binding.textHeaderTitle.text = "My Follow-ups"

        followUpAdapter = CrmFollowUpItemAdapter { item ->
            startActivity(Intent(this, LeadDetailsActivity::class.java).apply {
                putExtra("LEAD_ID", item.leadId)
                putExtra("IS_MANAGER", false)
            })
        }

        binding.recyclerFollowUps.layoutManager = LinearLayoutManager(this)
        binding.recyclerFollowUps.adapter = followUpAdapter

        binding.buttonBack.setOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            loadFollowUps()
        }

        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                activeFilterType = when (chip.id) {
                    R.id.chipToday -> "today"
                    R.id.chipTomorrow -> "tomorrow"
                    R.id.chipNext7Days -> "7days"
                    R.id.chipNext15Days -> "15days"
                    R.id.chipNext30Days -> "30days"
                    R.id.chipOverdue -> "overdue"
                    else -> "today"
                }
                loadFollowUps()
            }
        }

        when (activeFilterType) {
            "today" -> binding.chipToday.isChecked = true
            "tomorrow" -> binding.chipTomorrow.isChecked = true
            "7days" -> binding.chipNext7Days.isChecked = true
            "15days" -> binding.chipNext15Days.isChecked = true
            "30days" -> binding.chipNext30Days.isChecked = true
            "overdue" -> binding.chipOverdue.isChecked = true
        }

        binding.layoutEmployeeFilter.visibility = View.GONE
    }

    private fun loadFollowUps() {
        viewModel.loadUserFollowUps(filterType = activeFilterType)
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

        viewModel.userFollowUps.observe(this) { list ->
            followUpAdapter.submitList(list)
            binding.layoutEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadFollowUps()
    }
}
