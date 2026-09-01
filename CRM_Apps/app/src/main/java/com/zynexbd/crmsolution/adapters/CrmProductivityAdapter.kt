package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemCrmProductivityRowBinding
import com.zynexbd.crmsolution.models.EmployeeProductivityItem

class CrmProductivityAdapter :
    ListAdapter<EmployeeProductivityItem, CrmProductivityAdapter.ProductivityViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductivityViewHolder {
        val binding = ItemCrmProductivityRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductivityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductivityViewHolder(private val binding: ItemCrmProductivityRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EmployeeProductivityItem) {
            val context = binding.root.context
            binding.textEmpName.text = item.employeeName
            binding.textAchievementPercent.text = "${item.achievementPercent}%"
            binding.progressKpi.progress = item.achievementPercent.toInt().coerceIn(0, 100)

            // High contrast readable badge styling
            val percent = item.achievementPercent
            when {
                percent >= 100.0 -> {
                    binding.textAchievementPercent.background = androidx.core.content.ContextCompat.getDrawable(context, com.zynexbd.crmsolution.R.drawable.bg_badge_success)
                    binding.textAchievementPercent.setTextColor(android.graphics.Color.WHITE)
                }
                percent >= 50.0 -> {
                    binding.textAchievementPercent.background = androidx.core.content.ContextCompat.getDrawable(context, com.zynexbd.crmsolution.R.drawable.bg_badge_primary)
                    binding.textAchievementPercent.setTextColor(android.graphics.Color.WHITE)
                }
                percent > 0.0 -> {
                    binding.textAchievementPercent.background = androidx.core.content.ContextCompat.getDrawable(context, com.zynexbd.crmsolution.R.drawable.bg_badge_warning)
                    binding.textAchievementPercent.setTextColor(android.graphics.Color.WHITE)
                }
                else -> {
                    binding.textAchievementPercent.background = androidx.core.content.ContextCompat.getDrawable(context, com.zynexbd.crmsolution.R.drawable.bg_badge_slate)
                    binding.textAchievementPercent.setTextColor(android.graphics.Color.parseColor("#475569"))
                }
            }

            binding.textFollowUpDoneTarget.text = "Follow-up: ${item.followUpDone}/${item.followUpTarget}"
            binding.textInterestedDoneTarget.text = "Interested: ${item.interestedDone}/${item.interestedTarget}"
            binding.textClosedDoneTarget.text = "Closed: ${item.closedDone}/${item.closedTarget}"
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<EmployeeProductivityItem>() {
        override fun areItemsTheSame(oldItem: EmployeeProductivityItem, newItem: EmployeeProductivityItem): Boolean =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: EmployeeProductivityItem, newItem: EmployeeProductivityItem): Boolean =
            oldItem == newItem
    }
}
