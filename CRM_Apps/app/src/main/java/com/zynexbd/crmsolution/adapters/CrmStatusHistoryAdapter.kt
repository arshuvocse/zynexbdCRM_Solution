package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemCrmStatusHistoryBinding
import com.zynexbd.crmsolution.models.CrmStatusHistory

class CrmStatusHistoryAdapter :
    ListAdapter<CrmStatusHistory, CrmStatusHistoryAdapter.StatusHistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusHistoryViewHolder {
        val binding = ItemCrmStatusHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StatusHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StatusHistoryViewHolder(private val binding: ItemCrmStatusHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrmStatusHistory) {
            binding.textStatusChangeUser.text = item.changedByUserName ?: "User"
            binding.textStatusChangeDate.text = item.changedDateUtc.replace("T", " ").take(16)
            binding.textStatusChangeTransition.text = "${item.previousStatus} → ${item.newStatus}"
            binding.textStatusChangeRemarks.text = item.remarks ?: ""
            binding.textStatusChangeRemarks.visibility =
                if (item.remarks.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CrmStatusHistory>() {
        override fun areItemsTheSame(oldItem: CrmStatusHistory, newItem: CrmStatusHistory): Boolean =
            oldItem.statusHistoryId == newItem.statusHistoryId

        override fun areContentsTheSame(oldItem: CrmStatusHistory, newItem: CrmStatusHistory): Boolean =
            oldItem == newItem
    }
}
