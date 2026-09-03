package com.zynexbd.crmsolution.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityCrmCreateLeadBinding
import com.zynexbd.crmsolution.models.CreateCrmLeadRequest
import com.zynexbd.crmsolution.models.CrmLeadSource
import com.zynexbd.crmsolution.models.CrmProductService
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import java.util.Calendar

class CreateLeadActivity : BaseActivity() {

    private lateinit var binding: ActivityCrmCreateLeadBinding
    private lateinit var viewModel: CrmViewModel
    private var isManager: Boolean = false
    private var selectedFollowUpDate: String? = null

    private var selectedProduct: CrmProductService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrmCreateLeadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val session = com.zynexbd.crmsolution.utils.SessionManager(this)
        isManager = intent.getBooleanExtra("IS_MANAGER", session.isManagerOrAdmin())
        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()

        viewModel.loadMasterData(isManager)
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.textHeaderTitle.text = if (isManager) "Create Company Lead" else "Create Self Lead"

        // Manager option to assign lead
        binding.layoutAssignEmployee.visibility = View.GONE // Initially simple dropdown

        // Initial Status Spinner
        val statuses = listOf("New Lead", "Follow Up", "Interested", "Not Interested", "Closed")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        binding.spinnerLeadStatus.adapter = statusAdapter

        // Select2 Product/Service Picker
        binding.layoutSelectProductService.setOnClickListener {
            openSelect2ProductDialog()
        }
        binding.buttonClearSelectedProduct.setOnClickListener {
            setSelectedProduct(null)
        }

        // Date Picker for Next Follow Up
        binding.buttonPickDate.setOnClickListener { showDatePicker() }
        binding.textNextFollowUpDate.setOnClickListener { showDatePicker() }

        // Submit Button
        binding.buttonSubmitLead.setOnClickListener {
            submitLead()
        }
    }

    private fun openSelect2ProductDialog() {
        val sheet = com.zynexbd.crmsolution.dialogs.Select2ProductBottomSheet.newInstance(selectedProduct?.productServiceId) { product ->
            setSelectedProduct(product)
        }
        sheet.show(supportFragmentManager, com.zynexbd.crmsolution.dialogs.Select2ProductBottomSheet.TAG)
    }

    private fun setSelectedProduct(product: CrmProductService?) {
        selectedProduct = product
        if (product != null) {
            val priceStr = if (product.price != null && product.price > 0) " (৳ %,.2f)".format(product.price) else ""
            val codeStr = if (!product.code.isNullOrBlank()) "[${product.code}] " else ""
            binding.textSelectedProductService.text = "$codeStr${product.name}$priceStr"
            binding.textSelectedProductService.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary))
            binding.buttonClearSelectedProduct.visibility = View.VISIBLE
        } else {
            binding.textSelectedProductService.text = "Search & Select Product or Service..."
            binding.textSelectedProductService.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary))
            binding.buttonClearSelectedProduct.visibility = View.GONE
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formatted = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                selectedFollowUpDate = formatted
                binding.textNextFollowUpDate.text = formatted
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun submitLead() {
        val leadName = binding.editLeadName.text.toString().trim()
        if (leadName.isEmpty()) {
            binding.editLeadName.error = "Lead name is required"
            binding.editLeadName.requestFocus()
            return
        }

        val contactPerson = binding.editContactPerson.text.toString().trim().ifBlank { null }
        val phone = binding.editPhone.text.toString().trim().ifBlank { null }
        val email = binding.editEmail.text.toString().trim().ifBlank { null }
        val address = binding.editAddress.text.toString().trim().ifBlank { null }
        val remarks = binding.editRemarks.text.toString().trim().ifBlank { null }
        val leadStatus = binding.spinnerLeadStatus.selectedItem.toString()

        val productServiceId = selectedProduct?.productServiceId?.takeIf { it > 0 }

        val sourceSel = binding.spinnerLeadSource.selectedItem as? CrmLeadSource
        val leadSourceId = if (sourceSel != null && sourceSel.leadSourceId > 0) sourceSel.leadSourceId else null

        val request = CreateCrmLeadRequest(
            leadName = leadName,
            contactPerson = contactPerson,
            phone = phone,
            email = email,
            address = address,
            productServiceId = productServiceId,
            leadSourceId = leadSourceId,
            leadStatus = leadStatus,
            nextFollowUpDate = selectedFollowUpDate,
            remarks = remarks
        )

        viewModel.createLead(request, isManager) { success ->
            if (success) {
                finish()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.buttonSubmitLead.isEnabled = !loading
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

        viewModel.leadSources.observe(this) { list ->
            val items = mutableListOf(CrmLeadSource(0, 0, "-- Select Lead Source --"))
            items.addAll(list)
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
            binding.spinnerLeadSource.adapter = adapter
        }
    }
}
