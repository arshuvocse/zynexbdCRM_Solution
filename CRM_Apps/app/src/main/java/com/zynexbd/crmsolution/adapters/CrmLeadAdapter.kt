package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemCrmLeadCardBinding
import com.zynexbd.crmsolution.models.CrmLead

class CrmLeadAdapter(
    private val onLeadClick: (CrmLead) -> Unit
) : ListAdapter<CrmLead, CrmLeadAdapter.LeadViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeadViewHolder {
        val binding = ItemCrmLeadCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LeadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LeadViewHolder(private val binding: ItemCrmLeadCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(lead: CrmLead) {
            val context = binding.root.context
            binding.textLeadName.text = lead.leadName
            binding.textLeadStatus.text = lead.leadStatus

            // High-contrast readable status badge
            binding.textLeadStatus.setTextColor(android.graphics.Color.WHITE)
            when (lead.leadStatus) {
                "Interested" -> {
                    binding.textLeadStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_success)
                }
                "Closed" -> {
                    binding.textLeadStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_purple)
                }
                "Not Interested", "Lost" -> {
                    binding.textLeadStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_danger)
                }
                "Follow Up", "Follow-up" -> {
                    binding.textLeadStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_warning)
                }
                else -> { // New Lead, Contacted
                    binding.textLeadStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_primary)
                }
            }

            binding.textContactPerson.text = if (!lead.contactPerson.isNullOrBlank()) "👤 ${lead.contactPerson}" else "👤 No contact person"
            binding.textPhone.text = if (!lead.phone.isNullOrBlank()) "📞 ${lead.phone}" else ""

            binding.textProductService.text = if (!lead.productServiceName.isNullOrBlank()) "📦 ${lead.productServiceName}" else "📦 General"
            binding.textLeadSource.text = "🏷️ ${lead.leadSourceName ?: lead.leadSourceType}"

            val assignee = lead.assignedUserName ?: "Unassigned"
            binding.textAssignedEmployee.text = "Assigned: $assignee"

            if (!lead.nextFollowUpDate.isNullOrBlank()) {
                val dateStr = lead.nextFollowUpDate.take(10)
                binding.textNextFollowUp.visibility = View.VISIBLE
                binding.textNextFollowUp.text = "Next: $dateStr"
            } else {
                binding.textNextFollowUp.visibility = View.GONE
            }

            binding.root.setOnClickListener { onLeadClick(lead) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CrmLead>() {
        override fun areItemsTheSame(oldItem: CrmLead, newItem: CrmLead): Boolean =
            oldItem.leadId == newItem.leadId

        override fun areContentsTheSame(oldItem: CrmLead, newItem: CrmLead): Boolean =
            oldItem == newItem
    }
}
