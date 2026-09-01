package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemCrmFollowupHistoryBinding
import com.zynexbd.crmsolution.models.CrmFollowUp

class CrmFollowUpHistoryAdapter :
    ListAdapter<CrmFollowUp, CrmFollowUpHistoryAdapter.FollowUpViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowUpViewHolder {
        val binding = ItemCrmFollowupHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FollowUpViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FollowUpViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FollowUpViewHolder(private val binding: ItemCrmFollowupHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrmFollowUp) {
            val context = binding.root.context
            binding.textFollowUpStatus.text = item.status
            binding.textFollowUpStatus.setTextColor(Color.WHITE)
            when (item.status) {
                "Interested" -> binding.textFollowUpStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_success)
                "Closed" -> binding.textFollowUpStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_purple)
                "Not Interested", "Lost" -> binding.textFollowUpStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_danger)
                "Follow Up", "Follow-up" -> binding.textFollowUpStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_warning)
                else -> binding.textFollowUpStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_primary)
            }
            binding.textFollowUpDate.text = item.followUpDateUtc.replace("T", " ").take(16)
            binding.textFollowUpBy.text = "By: ${item.createdByUserName ?: "User"}"
            binding.textFollowUpRemarks.text = item.remarks

            if (!item.nextFollowUpDate.isNullOrBlank()) {
                binding.textFollowUpNextDate.visibility = View.VISIBLE
                binding.textFollowUpNextDate.text = "Next Scheduled: ${item.nextFollowUpDate.take(10)}"
            } else {
                binding.textFollowUpNextDate.visibility = View.GONE
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CrmFollowUp>() {
        override fun areItemsTheSame(oldItem: CrmFollowUp, newItem: CrmFollowUp): Boolean =
            oldItem.followUpId == newItem.followUpId

        override fun areContentsTheSame(oldItem: CrmFollowUp, newItem: CrmFollowUp): Boolean =
            oldItem == newItem
    }
}
