package com.zynexbd.crmsolution.dialogs

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemSelect2EmployeeBinding
import com.zynexbd.crmsolution.databinding.LayoutSelect2EmployeeSheetBinding
import com.zynexbd.crmsolution.models.User
import com.zynexbd.crmsolution.network.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Select2EmployeeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutSelect2EmployeeSheetBinding? = null
    private val binding get() = _binding!!

    private var selectedUserId: Int? = null
    private var onEmployeeSelectedListener: ((User?) -> Unit)? = null
    private var initialEmployees: List<User>? = null

    private lateinit var adapter: Select2EmployeeAdapter
    private var allEmployees: List<User> = emptyList()
    private var searchJob: Job? = null

    companion object {
        const val TAG = "Select2EmployeeBottomSheet"

        fun newInstance(
            selectedUserId: Int? = null,
            preloadedEmployees: List<User>? = null,
            onSelected: (User?) -> Unit
        ): Select2EmployeeBottomSheet {
            return Select2EmployeeBottomSheet().apply {
                this.selectedUserId = selectedUserId
                this.initialEmployees = preloadedEmployees
                this.onEmployeeSelectedListener = onSelected
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutSelect2EmployeeSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()

        binding.buttonClearSelection.setOnClickListener {
            onEmployeeSelectedListener?.invoke(null)
            dismiss()
        }

        if (!initialEmployees.isNullOrEmpty()) {
            allEmployees = initialEmployees!!
            binding.textEmployeeCountBadge.text = allEmployees.size.toString()
            adapter.submitList(allEmployees)
        } else {
            fetchEmployees()
        }
    }

    private fun setupRecyclerView() {
        adapter = Select2EmployeeAdapter(
            selectedId = selectedUserId,
            onItemClicked = { user ->
                onEmployeeSelectedListener?.invoke(user)
                dismiss()
            }
        )
        binding.recyclerEmployees.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEmployees.adapter = adapter
    }

    private fun setupSearch() {
        binding.editSearchEmployee.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()
                binding.buttonClearSearch.visibility = if (query.isNullOrEmpty()) View.GONE else View.VISIBLE

                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(150) // quick 150ms debounce
                    filterList(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.buttonClearSearch.setOnClickListener {
            binding.editSearchEmployee.text?.clear()
        }
    }

    private fun fetchEmployees() {
        val ctx = context ?: return
        binding.progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(ctx)
                val response = api.getUsers()
                if (response.isSuccessful && response.body() != null) {
                    allEmployees = response.body()!!.filter { it.isActive }
                    binding.textEmployeeCountBadge.text = allEmployees.size.toString()
                    filterList(binding.editSearchEmployee.text?.toString())
                } else {
                    showEmpty("Failed to load employees from server.")
                }
            } catch (e: Exception) {
                showEmpty("Network error: ${e.localizedMessage}")
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }

    private fun filterList(query: String?) {
        val filtered = if (query.isNullOrBlank()) {
            allEmployees
        } else {
            val q = query.lowercase().trim()
            allEmployees.filter { emp ->
                emp.name.lowercase().contains(q) ||
                emp.username.lowercase().contains(q) ||
                (emp.phoneNumber?.lowercase()?.contains(q) == true) ||
                emp.role.lowercase().contains(q) ||
                (emp.officeLocationName?.lowercase()?.contains(q) == true)
            }
        }

        adapter.submitList(filtered)

        if (filtered.isEmpty()) {
            showEmpty("No employees matching \"$query\"")
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerEmployees.visibility = View.VISIBLE
        }
    }

    private fun showEmpty(message: String) {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.textEmptyMessage.text = message
        binding.recyclerEmployees.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Inner Adapter for Select2 Employee List
    class Select2EmployeeAdapter(
        private val selectedId: Int?,
        private val onItemClicked: (User) -> Unit
    ) : RecyclerView.Adapter<Select2EmployeeAdapter.ViewHolder>() {

        private val items = mutableListOf<User>()

        fun submitList(newItems: List<User>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemSelect2EmployeeBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(val b: ItemSelect2EmployeeBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(user: User) {
                val displayName = user.name.ifBlank { user.username }
                b.textEmployeeName.text = displayName

                // Avatar initials
                val initial = displayName.trim().take(1).uppercase()
                b.textAvatarInitials.text = if (initial.isNotBlank()) initial else "U"

                // Role badge: Solid vibrant badge with crisp white text
                b.textRoleBadge.text = user.role.uppercase()
                b.textRoleBadge.setTextColor(Color.WHITE)
                when (user.role.lowercase()) {
                    "admin" -> {
                        b.textRoleBadge.setBackgroundResource(R.drawable.bg_role_admin_pill)
                    }
                    "manager" -> {
                        b.textRoleBadge.setBackgroundResource(R.drawable.bg_badge_primary)
                    }
                    else -> {
                        b.textRoleBadge.setBackgroundResource(R.drawable.bg_role_user_pill)
                    }
                }

                // Subtitle: @username • 📞 phone or office
                val parts = mutableListOf<String>()
                if (user.username.isNotBlank()) parts.add("@${user.username}")
                if (!user.phoneNumber.isNullOrBlank()) parts.add("📞 ${user.phoneNumber}")
                if (!user.officeLocationName.isNullOrBlank()) parts.add("🏢 ${user.officeLocationName}")
                b.textEmployeeSubtitle.text = parts.joinToString(" • ")

                // Checkmark
                val isSelected = selectedId != null && user.id == selectedId
                b.imageSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

                b.root.setOnClickListener {
                    onItemClicked(user)
                }
            }
        }
    }
}
