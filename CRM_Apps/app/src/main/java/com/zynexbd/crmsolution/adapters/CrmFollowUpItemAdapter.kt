package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemCrmFollowUpCardBinding
import com.zynexbd.crmsolution.models.CrmFollowUpItem

class CrmFollowUpItemAdapter(
    private val onItemClick: (CrmFollowUpItem) -> Unit
) : ListAdapter<CrmFollowUpItem, CrmFollowUpItemAdapter.FollowUpViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowUpViewHolder {
        val binding = ItemCrmFollowUpCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FollowUpViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FollowUpViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FollowUpViewHolder(private val binding: ItemCrmFollowUpCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrmFollowUpItem) {
            val context = binding.root.context
            binding.textLeadName.text = item.leadName
            binding.textLeadStatus.text = item.leadStatus

            val contact = item.contactPerson ?: ""
            val phone = item.phone ?: ""
            binding.textContactAndPhone.text = if (contact.isNotBlank() && phone.isNotBlank()) "👤 $contact • 📞 $phone" else "👤 $contact $phone".trim()
            binding.textProductService.text = if (!item.productServiceName.isNullOrBlank()) "📦 ${item.productServiceName}" else "📦 General"
            binding.textNextDate.text = "📅 ${item.nextFollowUpDate?.take(10) ?: "Not set"}"

            binding.textDaysRemaining.setTextColor(android.graphics.Color.WHITE)
            if (item.isOverdue) {
                binding.textDaysRemaining.text = "OVERDUE"
                binding.textDaysRemaining.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_danger)
            } else if (item.daysRemaining != null) {
                val days = item.daysRemaining
                binding.textDaysRemaining.text = if (days == 0) "TODAY" else if (days == 1) "TOMORROW" else "In $days Days"
                binding.textDaysRemaining.background = ContextCompat.getDrawable(context, if (days <= 1) R.drawable.bg_badge_warning else R.drawable.bg_badge_primary)
            } else {
                binding.textDaysRemaining.text = "UPCOMING"
                binding.textDaysRemaining.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_success)
            }

            if (!item.assignedUserName.isNullOrBlank()) {
                binding.textAssignedEmployee.visibility = View.VISIBLE
                binding.textAssignedEmployee.text = "Assigned: ${item.assignedUserName}"
            } else {
                binding.textAssignedEmployee.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CrmFollowUpItem>() {
        override fun areItemsTheSame(oldItem: CrmFollowUpItem, newItem: CrmFollowUpItem): Boolean =
            oldItem.leadId == newItem.leadId

        override fun areContentsTheSame(oldItem: CrmFollowUpItem, newItem: CrmFollowUpItem): Boolean =
            oldItem == newItem
    }
}
