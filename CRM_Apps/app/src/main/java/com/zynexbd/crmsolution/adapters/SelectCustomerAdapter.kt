package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemSelectCustomerBinding
import com.zynexbd.crmsolution.models.Customer
import java.util.Locale

class SelectCustomerAdapter(
    private var allCustomers: List<Customer>,
    private var selectedCustomerId: Int = 0,
    private val onCustomerSelected: (Customer) -> Unit
) : RecyclerView.Adapter<SelectCustomerAdapter.ViewHolder>() {

    private var filteredCustomers: List<Customer> = allCustomers

    fun updateList(newList: List<Customer>, currentSelectedId: Int = selectedCustomerId) {
        this.allCustomers = newList
        this.selectedCustomerId = currentSelectedId
        this.filteredCustomers = newList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val cleanQuery = query.trim().lowercase(Locale.getDefault())
        filteredCustomers = if (cleanQuery.isEmpty()) {
            allCustomers
        } else {
            allCustomers.filter {
                it.name.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                it.mobile.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                it.address.lowercase(Locale.getDefault()).contains(cleanQuery)
            }
        }
        notifyDataSetChanged()
    }

    fun getFilteredCount(): Int = filteredCustomers.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectCustomerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(filteredCustomers[position])
    }

    override fun getItemCount(): Int = filteredCustomers.size

    inner class ViewHolder(private val binding: ItemSelectCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(customer: Customer) {
            val initial = if (customer.name.isNotBlank()) {
                customer.name.trim().take(1).uppercase(Locale.getDefault())
            } else "C"

            binding.textAvatar.text = initial
            binding.textCustomerName.text = customer.name
            binding.textCustomerPhone.text = customer.mobile
            binding.textCustomerAddress.text = if (customer.address.isNotBlank()) "• ${customer.address}" else ""

            val isSelected = customer.customerId == selectedCustomerId
            binding.imageSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.root.isSelected = isSelected

            binding.root.setOnClickListener {
                selectedCustomerId = customer.customerId
                notifyDataSetChanged()
                onCustomerSelected(customer)
            }
        }
    }
}
