package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.CrmReportAdapter
import com.zynexbd.crmsolution.databinding.ActivityUserCrmReportsBinding
import com.zynexbd.crmsolution.viewmodel.CrmViewModel

class UserCrmReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserCrmReportsBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var adapter: CrmReportAdapter

    private var currentReportType = 1
    private var currentPage = 1
    private var totalPages = 1
    private var currentSearch: String? = null

    private val reportTitles = listOf(
        "1. My Lead Summary",
        "2. My Lead Status",
        "3. My Follow-up Report",
        "4. My Overdue Follow-ups",
        "5. My KPI Report",
        "6. My KPI Achievement",
        "7. My Productivity",
        "8. My Product/Service Leads",
        "9. My Lead Source Leads",
        "10. My Conversion Performance",
        "11. Daily Performance",
        "12. Weekly Performance",
        "13. Monthly Performance"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserCrmReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupRecyclerView()
        setupSpinner()
        setupSearch()
        setupListeners()
        observeViewModel()

        loadCurrentReport()
    }

    private fun setupRecyclerView() {
        adapter = CrmReportAdapter { row ->
            if (row.entityId > 0 && (currentReportType in listOf(1, 2, 3, 4, 8, 9))) {
                val intent = Intent(this, UserCrmLeadListActivity::class.java).apply {
                    putExtra("EXTRA_LEAD_ID", row.entityId)
                }
                startActivity(intent)
            }
        }
        binding.recyclerViewReports.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewReports.adapter = adapter
    }

    private fun setupSpinner() {
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reportTitles)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerReportType.adapter = spinnerAdapter

        binding.spinnerReportType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newType = position + 1
                if (newType != currentReportType) {
                    currentReportType = newType
                    currentPage = 1
                    loadCurrentReport()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearch() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearch = s?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                currentPage = 1
                loadCurrentReport()
            }
        })
    }

    private fun setupListeners() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            loadCurrentReport()
        }

        binding.buttonPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                loadCurrentReport()
            }
        }

        binding.buttonNextPage.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                loadCurrentReport()
            }
        }
    }

    private fun loadCurrentReport() {
        binding.progressBar.visibility = View.VISIBLE
        viewModel.loadUserReport(
            reportType = currentReportType,
            search = currentSearch,
            pageNumber = currentPage,
            pageSize = 20
        )
    }

    private fun observeViewModel() {
        viewModel.userReport.observe(this) { report ->
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            if (report == null) {
                binding.textEmpty.visibility = View.VISIBLE
                adapter.submitList(emptyList())
                return@observe
            }

            totalPages = report.totalPages
            currentPage = report.pageNumber

            binding.textSummaryLabel1.text = report.summary.summary1Label.ifBlank { "Metric 1" }
            binding.textSummaryValue1.text = report.summary.summary1Value.ifBlank { "0" }

            binding.textSummaryLabel2.text = report.summary.summary2Label.ifBlank { "Metric 2" }
            binding.textSummaryValue2.text = report.summary.summary2Value.ifBlank { "0" }

            binding.textSummaryLabel3.text = report.summary.summary3Label.ifBlank { "Metric 3" }
            binding.textSummaryValue3.text = report.summary.summary3Value.ifBlank { "0" }

            adapter.submitList(report.rows)
            binding.textEmpty.visibility = if (report.rows.isEmpty()) View.VISIBLE else View.GONE

            binding.textPageIndicator.text = "Page $currentPage of $totalPages"
            binding.buttonPrevPage.isEnabled = currentPage > 1
            binding.buttonNextPage.isEnabled = currentPage < totalPages
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
