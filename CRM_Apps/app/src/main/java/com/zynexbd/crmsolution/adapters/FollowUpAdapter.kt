package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemFollowUpCardBinding
import com.zynexbd.crmsolution.models.FollowUpItem

class FollowUpAdapter(
    private val onCallClick: (FollowUpItem) -> Unit,
    private val onVisitClick: (FollowUpItem) -> Unit,
    private val onCompleteClick: (FollowUpItem) -> Unit
) : ListAdapter<FollowUpItem, FollowUpAdapter.FollowUpViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowUpViewHolder {
        val binding = ItemFollowUpCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FollowUpViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FollowUpViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FollowUpViewHolder(private val binding: ItemFollowUpCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FollowUpItem) {
            binding.textCustomerName.text = item.customerName
            binding.textMobile.text = item.mobile
            binding.textAddress.text = item.address
            binding.textFollowUpDate.text = item.followUpDate?.take(10) ?: "Today"
            binding.chipCategory.text = item.category

            binding.buttonCall.setOnClickListener { onCallClick(item) }
            binding.buttonVisit.setOnClickListener { onVisitClick(item) }
            binding.buttonComplete.setOnClickListener { onCompleteClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FollowUpItem>() {
        override fun areItemsTheSame(oldItem: FollowUpItem, newItem: FollowUpItem): Boolean =
            oldItem.visitId == newItem.visitId

        override fun areContentsTheSame(oldItem: FollowUpItem, newItem: FollowUpItem): Boolean =
            oldItem == newItem
    }
}
