package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemCustomerCardBinding
import com.zynexbd.crmsolution.models.Customer

class CustomerAdapter(
    private val onCustomerClick: (Customer) -> Unit,
    private val onCallClick: (Customer) -> Unit,
    private val onNavigateClick: (Customer) -> Unit,
    private val onVisitClick: (Customer) -> Unit
) : ListAdapter<Customer, CustomerAdapter.CustomerViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CustomerViewHolder(private val binding: ItemCustomerCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(customer: Customer) {
            binding.textCustomerName.text = customer.name
            binding.textMobile.text = customer.mobile
            binding.textAddress.text = customer.address
            binding.textLastVisit.text = customer.lastVisitDate?.take(10) ?: "No previous visits"
            binding.textNextFollowUp.text = customer.nextFollowUpDate?.take(10) ?: "None"

            binding.root.setOnClickListener { onCustomerClick(customer) }
            binding.buttonCall.setOnClickListener { onCallClick(customer) }
            binding.buttonNavigate.setOnClickListener { onNavigateClick(customer) }
            binding.buttonVisit.setOnClickListener { onVisitClick(customer) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer): Boolean =
            oldItem.customerId == newItem.customerId

        override fun areContentsTheSame(oldItem: Customer, newItem: Customer): Boolean =
            oldItem == newItem
    }
}
