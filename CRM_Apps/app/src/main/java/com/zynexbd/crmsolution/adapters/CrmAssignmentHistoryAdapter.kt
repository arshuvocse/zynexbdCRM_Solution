package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemCrmAssignmentHistoryBinding
import com.zynexbd.crmsolution.models.CrmLeadAssignment

class CrmAssignmentHistoryAdapter :
    ListAdapter<CrmLeadAssignment, CrmAssignmentHistoryAdapter.AssignmentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssignmentViewHolder {
        val binding = ItemCrmAssignmentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AssignmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AssignmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AssignmentViewHolder(private val binding: ItemCrmAssignmentHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrmLeadAssignment) {
            val toName = item.newUserName ?: "Employee"
            val fromName = item.previousUserName
            binding.textAssignmentAction.text = if (fromName != null) "Reassigned: $fromName ➔ $toName" else "Assigned to: $toName"
            binding.textAssignmentDate.text = item.assignedDateUtc.replace("T", " ").take(16)
            binding.textAssignmentBy.text = "By: ${item.assignedByUserName ?: "Manager"}"

            if (!item.remarks.isNullOrBlank()) {
                binding.textAssignmentRemarks.visibility = View.VISIBLE
                binding.textAssignmentRemarks.text = "Note: ${item.remarks}"
            } else {
                binding.textAssignmentRemarks.visibility = View.GONE
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CrmLeadAssignment>() {
        override fun areItemsTheSame(oldItem: CrmLeadAssignment, newItem: CrmLeadAssignment): Boolean =
            oldItem.assignmentId == newItem.assignmentId

        override fun areContentsTheSame(oldItem: CrmLeadAssignment, newItem: CrmLeadAssignment): Boolean =
            oldItem == newItem
    }
}
