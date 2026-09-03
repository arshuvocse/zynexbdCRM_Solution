package com.zynexbd.crmsolution.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.adapters.ProductServiceAdapter
import com.zynexbd.crmsolution.databinding.ActivityCrmProductServiceManagementBinding
import com.zynexbd.crmsolution.databinding.DialogAddEditProductServiceBinding
import com.zynexbd.crmsolution.models.CreateCrmProductServiceRequest
import com.zynexbd.crmsolution.models.CrmProductService
import com.zynexbd.crmsolution.models.UpdateCrmProductServiceRequest
import com.zynexbd.crmsolution.utils.SessionManager
import com.zynexbd.crmsolution.viewmodel.CrmViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProductServiceManagementActivity : BaseActivity() {

    private lateinit var binding: ActivityCrmProductServiceManagementBinding
    private lateinit var viewModel: CrmViewModel
    private lateinit var adapter: ProductServiceAdapter
    private lateinit var session: SessionManager

    private var currentSearch: String? = null
    private var currentActiveOnly: Boolean? = null // null = All, true = Active, false = Inactive
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrmProductServiceManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        if (!session.isAdmin() && !session.isManager()) {
            Toast.makeText(this, "Unauthorized: Access restricted to Admin & Manager.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[CrmViewModel::class.java]

        setupUI()
        observeViewModel()
        loadData()
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonAddProduct.setOnClickListener {
            showAddEditDialog(null)
        }

        // Setup Recycler
        adapter = ProductServiceAdapter(
            onEditClicked = { product -> showAddEditDialog(product) },
            onToggleStatusClicked = { product -> handleToggleStatus(product) }
        )
        binding.recyclerProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerProducts.adapter = adapter

        // Swipe Refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }

        // Search text watcher
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()
                binding.buttonClearSearch.visibility = if (query.isNullOrEmpty()) View.GONE else View.VISIBLE

                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    currentSearch = if (query.isNullOrEmpty()) null else query
                    loadData()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.buttonClearSearch.setOnClickListener {
            binding.editSearch.text?.clear()
        }

        // Filter Chips
        binding.chipGroupStatus.setOnCheckedChangeListener { _, checkedId ->
            currentActiveOnly = when (checkedId) {
                R.id.chipActive -> true
                R.id.chipInactive -> false
                else -> null // All
            }
            loadData()
        }
    }

    private fun loadData() {
        viewModel.loadProductServicesManagement(
            search = currentSearch,
            activeOnly = currentActiveOnly
        )
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading && !binding.swipeRefresh.isRefreshing) View.VISIBLE else View.GONE
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }

        viewModel.productServicesManagement.observe(this) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.recyclerProducts.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.recyclerProducts.visibility = View.VISIBLE
            }
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.successMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleToggleStatus(product: CrmProductService) {
        val newStatus = !product.isActive
        if (!newStatus) {
            // Confirmation before deactivation
            AlertDialog.Builder(this)
                .setTitle("⚠️ Deactivate Product/Service")
                .setMessage("Are you sure you want to deactivate \"${product.name}\"?\n\nExisting leads will keep their historical record intact, but this item will no longer appear for new lead creation.")
                .setPositiveButton("Yes, Deactivate") { _, _ ->
                    viewModel.toggleProductServiceStatus(product.productServiceId, false) { _, _ -> }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Simple confirmation for activation
            viewModel.toggleProductServiceStatus(product.productServiceId, true) { _, _ -> }
        }
    }

    private fun showAddEditDialog(existing: CrmProductService?) {
        val dialogBinding = DialogAddEditProductServiceBinding.inflate(LayoutInflater.from(this))
        val isEdit = (existing != null)

        dialogBinding.textDialogTitle.text = if (isEdit) "Edit Product / Service" else "Add Product / Service"

        if (isEdit) {
            dialogBinding.editProductName.setText(existing!!.name)
            dialogBinding.editProductCode.setText(existing.code ?: "")
            dialogBinding.editProductPrice.setText(existing.price?.let { "%.2f".format(it) } ?: "")
            dialogBinding.editProductDescription.setText(existing.description ?: "")
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.buttonCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.buttonSave.setOnClickListener {
            val name = dialogBinding.editProductName.text.toString().trim()
            if (name.isEmpty()) {
                dialogBinding.editProductName.error = "Name is required"
                dialogBinding.editProductName.requestFocus()
                return@setOnClickListener
            }

            val code = dialogBinding.editProductCode.text.toString().trim().ifBlank { null }
            val priceStr = dialogBinding.editProductPrice.text.toString().trim()
            val price = priceStr.toDoubleOrNull()
            val desc = dialogBinding.editProductDescription.text.toString().trim().ifBlank { null }

            dialogBinding.buttonSave.isEnabled = false

            if (!isEdit) {
                val request = CreateCrmProductServiceRequest(
                    name = name,
                    code = code,
                    description = desc,
                    price = price
                )
                viewModel.createProductService(request) { success, _ ->
                    dialogBinding.buttonSave.isEnabled = true
                    if (success) {
                        dialog.dismiss()
                    }
                }
            } else {
                val request = UpdateCrmProductServiceRequest(
                    name = name,
                    code = code,
                    description = desc,
                    price = price,
                    isActive = existing!!.isActive
                )
                viewModel.updateProductService(existing.productServiceId, request) { success, _ ->
                    dialogBinding.buttonSave.isEnabled = true
                    if (success) {
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }
}
