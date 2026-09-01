package com.zynexbd.crmsolution.activities

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.zynexbd.crmsolution.adapters.CrmProductivityAdapter
import com.zynexbd.crmsolution.databinding.ActivityCrmProductivityBinding
import com.zynexbd.crmsolution.utils.CrmCsvExporter
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import kotlinx.coroutines.launch

class AdminCrmProductivityActivity : BaseActivity() {

    private lateinit var binding: ActivityCrmProductivityBinding
    private lateinit var viewModel: CrmViewModel
    private val productivityAdapter = CrmProductivityAdapter()

    private var selectedPeriod: String = "Daily"
    private var sortOrder: String = "desc" // Highest first

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrmProductivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        loadProductivity()
    }

    private fun setupUI() {
        binding.recyclerProductivity.layoutManager = LinearLayoutManager(this)
        binding.recyclerProductivity.adapter = productivityAdapter

        binding.buttonBack.setOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            loadProductivity()
        }

        binding.buttonSort.setOnClickListener {
            sortOrder = if (sortOrder == "desc") "asc" else "desc"
            Toast.makeText(this, if (sortOrder == "desc") "Sorting: Highest Achievement First" else "Sorting: Lowest Achievement First", Toast.LENGTH_SHORT).show()
            loadProductivity()
        }

        binding.tabLayoutPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedPeriod = when (tab?.position) {
                    0 -> "Daily"
                    1 -> "Weekly"
                    2 -> "Monthly"
                    else -> "Daily"
                }
                loadProductivity()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.buttonExportCsv.setOnClickListener {
            exportProductivityCsv()
        }
    }

    private fun exportProductivityCsv() {
        lifecycleScope.launch {
            when (val result = CrmCsvExporter.downloadCsv(this@AdminCrmProductivityActivity, "Productivity") {
                viewModel.exportProductivityCsv(periodType = selectedPeriod)
            }) {
                is CrmCsvExporter.ExportResult.Success ->
                    CrmCsvExporter.showSuccessDialog(this@AdminCrmProductivityActivity, result.file)
                is CrmCsvExporter.ExportResult.Error ->
                    CrmCsvExporter.showErrorDialog(this@AdminCrmProductivityActivity, result.message) { exportProductivityCsv() }
            }
        }
    }

    private fun loadProductivity() {
        viewModel.loadManagerProductivity(
            periodType = selectedPeriod,
            sortBy = "achievement",
            sortOrder = sortOrder
        )
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

        viewModel.managerProductivity.observe(this) { prod ->
            if (prod != null) {
                val sortText = if (sortOrder == "desc") "Highest First" else "Lowest First"
                binding.textProductivitySubtitle.text = "Period: ${prod.periodType} (${prod.fromDate.take(10)} to ${prod.toDate.take(10)}) • Sorted: $sortText"
                productivityAdapter.submitList(prod.items)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProductivity()
    }
}
