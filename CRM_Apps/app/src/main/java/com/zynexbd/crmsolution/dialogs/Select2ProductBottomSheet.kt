package com.zynexbd.crmsolution.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemSelect2ProductBinding
import com.zynexbd.crmsolution.databinding.LayoutSelect2ProductSheetBinding
import com.zynexbd.crmsolution.models.CrmProductService
import com.zynexbd.crmsolution.network.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Select2ProductBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutSelect2ProductSheetBinding? = null
    private val binding get() = _binding!!

    private var selectedProductId: Int? = null
    private var onProductSelectedListener: ((CrmProductService?) -> Unit)? = null

    private lateinit var adapter: Select2ProductAdapter
    private var searchJob: Job? = null
    private var currentSearchQuery: String? = null

    companion object {
        const val TAG = "Select2ProductBottomSheet"

        fun newInstance(
            selectedProductId: Int? = null,
            onSelected: (CrmProductService?) -> Unit
        ): Select2ProductBottomSheet {
            return Select2ProductBottomSheet().apply {
                this.selectedProductId = selectedProductId
                this.onProductSelectedListener = onSelected
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutSelect2ProductSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()

        binding.buttonClearSelection.setOnClickListener {
            onProductSelectedListener?.invoke(null)
            dismiss()
        }

        binding.buttonRetry.setOnClickListener {
            performSearch(currentSearchQuery)
        }

        // Initial fetch
        performSearch(null)
    }

    private fun setupRecyclerView() {
        adapter = Select2ProductAdapter(
            selectedId = selectedProductId,
            onItemClicked = { product ->
                onProductSelectedListener?.invoke(product)
                dismiss()
            }
        )
        binding.recyclerProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerProducts.adapter = adapter
    }

    private fun setupSearch() {
        binding.editSearchProduct.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()
                binding.buttonClearSearch.visibility = if (query.isNullOrEmpty()) View.GONE else View.VISIBLE

                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // 300ms debounce
                    performSearch(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.buttonClearSearch.setOnClickListener {
            binding.editSearchProduct.text?.clear()
        }
    }

    private fun performSearch(query: String?) {
        currentSearchQuery = query
        binding.progressLoading.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.buttonRetry.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val resp = api.getProductServices(
                    search = query,
                    activeOnly = true,
                    pageNumber = 1,
                    pageSize = 50
                )

                binding.progressLoading.visibility = View.GONE
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val items = resp.body()?.data?.items ?: emptyList()
                    adapter.submitList(items)

                    if (items.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.textEmptyMessage.text = if (query.isNullOrEmpty()) 
                            "No active products or services available." 
                        else 
                            "No matching products found for \"$query\""
                    }
                } else {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.textEmptyMessage.text = "Unable to load products. Please try again."
                    binding.buttonRetry.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.textEmptyMessage.text = "Connection error. Please try again."
                binding.buttonRetry.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Inner Adapter for Select2 List
    class Select2ProductAdapter(
        private val selectedId: Int?,
        private val onItemClicked: (CrmProductService) -> Unit
    ) : RecyclerView.Adapter<Select2ProductAdapter.ViewHolder>() {

        private val items = mutableListOf<CrmProductService>()

        fun submitList(newItems: List<CrmProductService>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemSelect2ProductBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(val b: ItemSelect2ProductBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: CrmProductService) {
                b.textProductName.text = item.name

                if (!item.code.isNullOrBlank()) {
                    b.textProductCode.visibility = View.VISIBLE
                    b.textProductCode.text = item.code
                } else {
                    b.textProductCode.visibility = View.GONE
                }

                if (item.price != null && item.price > 0) {
                    b.textProductPrice.visibility = View.VISIBLE
                    b.textProductPrice.text = String.format("৳ %,.2f", item.price)
                } else {
                    b.textProductPrice.visibility = View.GONE
                }

                val isSelected = (selectedId != null && selectedId == item.productServiceId)
                b.imageSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

                b.root.setOnClickListener {
                    onItemClicked(item)
                }
            }
        }
    }
}
