package com.zynexbd.crmsolution.activities

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.CrmLeadAdapter
import com.zynexbd.crmsolution.databinding.ActivityCrmLeadListBinding
import com.zynexbd.crmsolution.databinding.DialogCrmLeadFilterBinding
import com.zynexbd.crmsolution.models.CrmLeadSource
import com.zynexbd.crmsolution.models.CrmProductService
import com.zynexbd.crmsolution.viewmodel.CrmViewModel

class UserCrmLeadListActivity : BaseActivity() {

    private lateinit var binding: ActivityCrmLeadListBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var leadAdapter: CrmLeadAdapter

    private var selectedStatus: String? = null
    private var selectedProductServiceId: Int? = null
    private var selectedLeadSourceId: Int? = null
    private var searchQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrmLeadListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        viewModel.loadMasterData(false)
        loadLeads()
    }

    private fun setupUI() {
        binding.textTitle.text = "My CRM Leads"

        leadAdapter = CrmLeadAdapter { lead ->
            startActivity(Intent(this, LeadDetailsActivity::class.java).apply {
                putExtra("LEAD_ID", lead.leadId)
                putExtra("IS_MANAGER", false)
            })
        }

        binding.recyclerLeads.layoutManager = LinearLayoutManager(this)
        binding.recyclerLeads.adapter = leadAdapter

        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonFilter.setOnClickListener {
            showFilterDialog()
        }

        binding.buttonClearActiveFilter.setOnClickListener {
            clearFilters()
        }

        binding.fabCreateLead.setOnClickListener {
            startActivity(Intent(this, CreateLeadActivity::class.java).apply {
                putExtra("IS_MANAGER", false)
            })
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadLeads()
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = if (s.isNullOrBlank()) null else s.toString().trim()
                loadLeads()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadLeads() {
        viewModel.loadUserLeads(
            status = selectedStatus,
            productServiceId = selectedProductServiceId,
            leadSourceId = selectedLeadSourceId,
            search = searchQuery
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

        viewModel.userLeads.observe(this) { paged ->
            val items = paged?.items ?: emptyList()
            leadAdapter.submitList(items)
            binding.layoutEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

            updateFilterPill()
        }
    }

    private fun updateFilterPill() {
        val filters = mutableListOf<String>()
        if (!selectedStatus.isNullOrBlank()) filters.add("Status: $selectedStatus")
        if (selectedProductServiceId != null) filters.add("Product ID: $selectedProductServiceId")
        if (selectedLeadSourceId != null) filters.add("Source ID: $selectedLeadSourceId")

        if (filters.isNotEmpty()) {
            binding.layoutActiveFilter.visibility = View.VISIBLE
            binding.textFilterDescription.text = "Filtered by: " + filters.joinToString(" • ")
        } else {
            binding.layoutActiveFilter.visibility = View.GONE
        }
    }

    private fun clearFilters() {
        selectedStatus = null
        selectedProductServiceId = null
        selectedLeadSourceId = null
        loadLeads()
    }

    private fun showFilterDialog() {
        val dialog = Dialog(this)
        val dBinding = DialogCrmLeadFilterBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val statuses = listOf("All Statuses", "New Lead", "Follow Up", "Interested", "Not Interested", "Closed")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        dBinding.spinnerFilterStatus.adapter = statusAdapter
        if (selectedStatus != null) {
            val idx = statuses.indexOf(selectedStatus)
            if (idx >= 0) dBinding.spinnerFilterStatus.setSelection(idx)
        }

        // Product Select2 Searchable Picker
        var tempSelectedProduct: CrmProductService? = null
        if (selectedProductServiceId != null) {
            val existing = viewModel.productServices.value?.find { it.productServiceId == selectedProductServiceId }
            if (existing != null) {
                tempSelectedProduct = existing
                dBinding.textFilterProduct.text = existing.name
                dBinding.buttonClearFilterProduct.visibility = View.VISIBLE
            }
        }

        dBinding.layoutFilterProduct.setOnClickListener {
            val sheet = com.zynexbd.crmsolution.dialogs.Select2ProductBottomSheet.newInstance(tempSelectedProduct?.productServiceId) { product ->
                tempSelectedProduct = product
                if (product != null) {
                    dBinding.textFilterProduct.text = product.name
                    dBinding.buttonClearFilterProduct.visibility = View.VISIBLE
                } else {
                    dBinding.textFilterProduct.text = "All Products/Services"
                    dBinding.buttonClearFilterProduct.visibility = View.GONE
                }
            }
            sheet.show(supportFragmentManager, com.zynexbd.crmsolution.dialogs.Select2ProductBottomSheet.TAG)
        }

        dBinding.buttonClearFilterProduct.setOnClickListener {
            tempSelectedProduct = null
            dBinding.textFilterProduct.text = "All Products/Services"
            dBinding.buttonClearFilterProduct.visibility = View.GONE
        }

        val sources = mutableListOf(CrmLeadSource(0, 0, "All Lead Sources"))
        sources.addAll(viewModel.leadSources.value ?: emptyList())
        val sourceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sources)
        dBinding.spinnerFilterSource.adapter = sourceAdapter
        if (selectedLeadSourceId != null) {
            val idx = sources.indexOfFirst { it.leadSourceId == selectedLeadSourceId }
            if (idx >= 0) dBinding.spinnerFilterSource.setSelection(idx)
        }

        dBinding.labelFilterEmployee.visibility = View.GONE
        dBinding.spinnerFilterEmployee.visibility = View.GONE

        dBinding.buttonClearFilter.setOnClickListener {
            clearFilters()
            dialog.dismiss()
        }

        dBinding.buttonApplyFilter.setOnClickListener {
            val statusSel = dBinding.spinnerFilterStatus.selectedItem.toString()
            selectedStatus = if (statusSel == "All Statuses") null else statusSel

            selectedProductServiceId = tempSelectedProduct?.productServiceId?.takeIf { it > 0 }

            val sourceSel = dBinding.spinnerFilterSource.selectedItem as? CrmLeadSource
            selectedLeadSourceId = if (sourceSel != null && sourceSel.leadSourceId > 0) sourceSel.leadSourceId else null

            loadLeads()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        loadLeads()
    }
}
