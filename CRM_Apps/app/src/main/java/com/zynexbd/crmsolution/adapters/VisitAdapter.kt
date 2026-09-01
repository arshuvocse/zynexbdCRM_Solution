package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemVisitCardBinding
import com.zynexbd.crmsolution.models.CustomerVisit

class VisitAdapter : ListAdapter<CustomerVisit, VisitAdapter.VisitViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VisitViewHolder {
        val binding = ItemVisitCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VisitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VisitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VisitViewHolder(private val binding: ItemVisitCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(visit: CustomerVisit) {
            binding.textCustomerName.text = visit.customerName
            binding.textVisitStatus.text = visit.visitStatus
            binding.textVisitDate.text = visit.visitDate.take(16).replace("T", " ")
            binding.textRemarks.text = visit.remarks ?: "No remarks recorded"
            binding.textNextFollowUp.text = visit.nextFollowUpDate?.take(10) ?: "None"
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CustomerVisit>() {
        override fun areItemsTheSame(oldItem: CustomerVisit, newItem: CustomerVisit): Boolean =
            oldItem.visitId == newItem.visitId

        override fun areContentsTheSame(oldItem: CustomerVisit, newItem: CustomerVisit): Boolean =
            oldItem == newItem
    }
}
