package com.zynexbd.crmsolution.activities

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.adapters.CrmKpiAdapter
import com.zynexbd.crmsolution.databinding.ActivityCrmKpiManagementBinding
import com.zynexbd.crmsolution.databinding.DialogCrmEditKpiBinding
import com.zynexbd.crmsolution.models.CreateOrUpdateKpiRequest
import com.zynexbd.crmsolution.models.CrmKpi
import com.zynexbd.crmsolution.utils.CrmCsvExporter
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import kotlinx.coroutines.launch

class AdminCrmKpiActivity : BaseActivity() {

    private lateinit var binding: ActivityCrmKpiManagementBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var kpiAdapter: CrmKpiAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrmKpiManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        viewModel.loadCompanyKpis()
        viewModel.loadManagerDashboard() // for employee list
    }

    private fun setupUI() {
        kpiAdapter = CrmKpiAdapter { kpi ->
            showEditKpiDialog(kpi)
        }

        binding.recyclerKpis.layoutManager = LinearLayoutManager(this)
        binding.recyclerKpis.adapter = kpiAdapter

        binding.buttonBack.setOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadCompanyKpis()
        }

        binding.fabAddKpi.setOnClickListener {
            showEditKpiDialog(null)
        }

        binding.buttonExportCsv.setOnClickListener {
            exportKpiCsv()
        }
    }

    private fun exportKpiCsv() {
        lifecycleScope.launch {
            when (val result = CrmCsvExporter.downloadCsv(this@AdminCrmKpiActivity, "KPI") {
                viewModel.exportKpiCsv()
            }) {
                is CrmCsvExporter.ExportResult.Success ->
                    CrmCsvExporter.showSuccessDialog(this@AdminCrmKpiActivity, result.file)
                is CrmCsvExporter.ExportResult.Error ->
                    CrmCsvExporter.showErrorDialog(this@AdminCrmKpiActivity, result.message) { exportKpiCsv() }
            }
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

        viewModel.successMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.companyKpis.observe(this) { list ->
            kpiAdapter.submitList(list)
        }
    }

    private fun showEditKpiDialog(existing: CrmKpi?) {
        val dialog = Dialog(this)
        val dBinding = DialogCrmEditKpiBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dBinding.textKpiDialogTitle.text = if (existing == null) "Add KPI Target" else "Edit KPI Target"

        // Employee / Target Scope dropdown
        val perf = viewModel.managerDashboard.value?.employeePerformance ?: emptyList()
        val scopes = mutableListOf("Company Default (All Employees)")
        scopes.addAll(perf.map { "${it.employeeName} (ID: ${it.userId})" })
        val scopeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, scopes)
        dBinding.spinnerKpiUser.adapter = scopeAdapter

        if (existing?.userId != null) {
            val idx = perf.indexOfFirst { it.userId == existing.userId }
            if (idx >= 0) dBinding.spinnerKpiUser.setSelection(idx + 1)
        } else {
            dBinding.spinnerKpiUser.setSelection(0)
        }

        // Period dropdown
        val periods = listOf("Daily", "Weekly", "Monthly")
        val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, periods)
        dBinding.spinnerKpiPeriod.adapter = periodAdapter

        if (existing != null) {
            val pIdx = periods.indexOf(existing.periodType)
            if (pIdx >= 0) dBinding.spinnerKpiPeriod.setSelection(pIdx)
            dBinding.editFollowUpTarget.setText(existing.followUpTarget.toString())
            dBinding.editInterestedTarget.setText(existing.interestedTarget.toString())
            dBinding.editClosedTarget.setText(existing.closedTarget.toString())
        }

        dBinding.buttonCancel.setOnClickListener { dialog.dismiss() }

        dBinding.buttonSubmit.setOnClickListener {
            val followUp = dBinding.editFollowUpTarget.text.toString().toIntOrNull() ?: 0
            val interested = dBinding.editInterestedTarget.text.toString().toIntOrNull() ?: 0
            val closed = dBinding.editClosedTarget.text.toString().toIntOrNull() ?: 0

            val userPos = dBinding.spinnerKpiUser.selectedItemPosition
            val userId = if (userPos > 0 && userPos - 1 < perf.size) perf[userPos - 1].userId else null
            val period = dBinding.spinnerKpiPeriod.selectedItem.toString()

            val req = CreateOrUpdateKpiRequest(
                userId = userId,
                periodType = period,
                followUpTarget = followUp,
                interestedTarget = interested,
                closedTarget = closed
            )

            viewModel.saveKpi(req) { success ->
                if (success) dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCompanyKpis()
    }
}
