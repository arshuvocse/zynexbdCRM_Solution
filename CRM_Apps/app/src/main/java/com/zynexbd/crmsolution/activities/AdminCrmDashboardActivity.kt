package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.CrmProductivityAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminCrmDashboardBinding
import com.zynexbd.crmsolution.viewmodel.CrmViewModel

class AdminCrmDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminCrmDashboardBinding
    private lateinit var viewModel: CrmViewModel
    private val employeeAdapter = CrmProductivityAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCrmDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupAdminDrawer(binding.drawerLayout, binding.navigationView, binding.buttonMenu, R.id.nav_crm_dashboard)
        setupUI()
        observeViewModel()

        viewModel.loadManagerDashboard()
        viewModel.loadManagerProductivity(periodType = "Daily")
    }

    private fun setupUI() {
        binding.recyclerEmployeePerformance.layoutManager = LinearLayoutManager(this)
        binding.recyclerEmployeePerformance.adapter = employeeAdapter

        binding.buttonRefresh.setOnClickListener {
            viewModel.loadManagerDashboard()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadManagerDashboard()
        }

        // Quick Navigation Buttons
        binding.buttonAllLeads.setOnClickListener {
            startActivity(Intent(this, AdminCrmLeadListActivity::class.java))
        }

        binding.buttonCreateLead.setOnClickListener {
            startActivity(Intent(this, CreateLeadActivity::class.java).apply {
                putExtra("IS_MANAGER", true)
            })
        }

        binding.buttonFollowUps.setOnClickListener {
            startActivity(Intent(this, AdminCrmFollowUpsActivity::class.java))
        }

        binding.cardTotalLeads.setOnClickListener {
            startActivity(Intent(this, AdminCrmLeadListActivity::class.java))
        }

        binding.cardTodayFollowUps.setOnClickListener {
            startActivity(Intent(this, AdminCrmFollowUpsActivity::class.java).apply {
                putExtra("INITIAL_FILTER", "today")
            })
        }

        binding.buttonKpiManagement.setOnClickListener {
            startActivity(Intent(this, AdminCrmKpiActivity::class.java))
        }

        binding.buttonProductivityReport.setOnClickListener {
            startActivity(Intent(this, AdminCrmProductivityActivity::class.java))
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

        viewModel.managerDashboard.observe(this) { data ->
            if (data != null) {
                binding.textTotalLeads.text = data.totalLeads.toString()
                binding.textNewLeads.text = data.newLeads.toString()
                binding.textFollowUpLeads.text = data.followUpLeads.toString()
                binding.textInterestedLeads.text = data.interestedLeads.toString()
                binding.textClosedLeads.text = data.closedLeads.toString()
                binding.textTodayFollowUps.text = data.todayFollowUps.toString()
            }
        }

        viewModel.managerProductivity.observe(this) { data ->
            employeeAdapter.submitList(data?.items ?: emptyList())
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadManagerDashboard()
        viewModel.loadManagerProductivity(periodType = "Daily")
    }
}
