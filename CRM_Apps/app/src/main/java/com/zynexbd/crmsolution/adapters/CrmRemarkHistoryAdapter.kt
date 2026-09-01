package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemCrmRemarkHistoryBinding
import com.zynexbd.crmsolution.models.CrmRemark

class CrmRemarkHistoryAdapter :
    ListAdapter<CrmRemark, CrmRemarkHistoryAdapter.RemarkViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemarkViewHolder {
        val binding = ItemCrmRemarkHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RemarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RemarkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RemarkViewHolder(private val binding: ItemCrmRemarkHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrmRemark) {
            binding.textRemarkUser.text = item.userName ?: "User"
            binding.textRemarkDate.text = item.createdAtUtc.replace("T", " ").take(16)
            binding.textRemarkContent.text = item.remark
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CrmRemark>() {
        override fun areItemsTheSame(oldItem: CrmRemark, newItem: CrmRemark): Boolean =
            oldItem.remarkId == newItem.remarkId

        override fun areContentsTheSame(oldItem: CrmRemark, newItem: CrmRemark): Boolean =
            oldItem == newItem
    }
}
