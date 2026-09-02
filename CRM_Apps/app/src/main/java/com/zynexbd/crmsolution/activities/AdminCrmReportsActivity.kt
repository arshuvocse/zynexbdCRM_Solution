package com.zynexbd.crmsolution.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.CrmReportAdapter
import com.zynexbd.crmsolution.databinding.ActivityAdminCrmReportsBinding
import com.zynexbd.crmsolution.repository.CrmRepository
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AdminCrmReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminCrmReportsBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var adapter: CrmReportAdapter
    private lateinit var repository: CrmRepository

    private var currentReportType = 1
    private var currentPage = 1
    private var totalPages = 1
    private var currentSearch: String? = null

    private val reportTitles = listOf(
        "1. Company Lead Summary",
        "2. Office Location-wise Leads",
        "3. Manager-wise Lead Report",
        "4. User-wise Lead Report",
        "5. Product/Service-wise Leads",
        "6. Lead Source-wise Report",
        "7. Lead Status Report",
        "8. Follow-up Summary",
        "9. Overdue Follow-up Report",
        "10. KPI Summary Report",
        "11. Employee Productivity",
        "12. Conversion Report",
        "13. Daily Lead Trend",
        "14. Weekly Lead Trend",
        "15. Monthly Lead Trend"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCrmReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]
        repository = CrmRepository(this)

        setupRecyclerView()
        setupSpinner()
        setupSearch()
        setupListeners()
        observeViewModel()

        loadCurrentReport()
    }

    private fun setupRecyclerView() {
        adapter = CrmReportAdapter { row ->
            // If lead entity, can open details
            if (row.entityId > 0 && (currentReportType in listOf(1, 2, 3, 4, 7, 8, 9))) {
                val intent = Intent(this, AdminCrmLeadListActivity::class.java).apply {
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

        binding.buttonExportCsv.setOnClickListener {
            exportCsv()
        }
    }

    private fun loadCurrentReport() {
        binding.progressBar.visibility = View.VISIBLE
        viewModel.loadAdminReport(
            reportType = currentReportType,
            search = currentSearch,
            pageNumber = currentPage,
            pageSize = 20
        )
    }

    private fun observeViewModel() {
        viewModel.adminReport.observe(this) { report ->
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            if (report == null) {
                binding.textEmpty.visibility = View.VISIBLE
                adapter.submitList(emptyList())
                return@observe
            }

            totalPages = report.totalPages
            currentPage = report.pageNumber

            // Update Summary Card
            binding.textSummaryLabel1.text = report.summary.summary1Label.ifBlank { "Metric 1" }
            binding.textSummaryValue1.text = report.summary.summary1Value.ifBlank { "0" }

            binding.textSummaryLabel2.text = report.summary.summary2Label.ifBlank { "Metric 2" }
            binding.textSummaryValue2.text = report.summary.summary2Value.ifBlank { "0" }

            binding.textSummaryLabel3.text = report.summary.summary3Label.ifBlank { "Metric 3" }
            binding.textSummaryValue3.text = report.summary.summary3Value.ifBlank { "0" }

            // Update List
            adapter.submitList(report.rows)
            binding.textEmpty.visibility = if (report.rows.isEmpty()) View.VISIBLE else View.GONE

            // Update Pagination
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

    private fun exportCsv() {
        Toast.makeText(this, "Exporting CSV...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    repository.exportAdminReport(reportType = currentReportType, search = currentSearch)
                }
                if (res.isSuccessful && res.body() != null) {
                    val file = File(getExternalFilesDir(null), "admin_report_${currentReportType}_${System.currentTimeMillis()}.csv")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(file).use { out ->
                            res.body()!!.byteStream().copyTo(out)
                        }
                    }
                    val uri = FileProvider.getUriForFile(this@AdminCrmReportsActivity, "${packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Report CSV"))
                } else {
                    Toast.makeText(this@AdminCrmReportsActivity, "Export failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminCrmReportsActivity, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
