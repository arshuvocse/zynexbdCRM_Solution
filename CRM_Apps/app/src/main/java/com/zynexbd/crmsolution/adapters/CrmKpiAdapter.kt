package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemCrmKpiCardBinding
import com.zynexbd.crmsolution.models.CrmKpi

class CrmKpiAdapter(
    private val onEditClick: (CrmKpi) -> Unit
) : ListAdapter<CrmKpi, CrmKpiAdapter.KpiViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KpiViewHolder {
        val binding = ItemCrmKpiCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return KpiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KpiViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class KpiViewHolder(private val binding: ItemCrmKpiCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrmKpi) {
            val targetFor = item.userName ?: "Company Default Target"
            binding.textKpiTargetFor.text = targetFor
            binding.textKpiPeriod.text = item.periodType
            binding.textFollowUpTargetVal.text = item.followUpTarget.toString()
            binding.textInterestedTargetVal.text = item.interestedTarget.toString()
            binding.textClosedTargetVal.text = item.closedTarget.toString()

            binding.buttonEditKpi.setOnClickListener { onEditClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CrmKpi>() {
        override fun areItemsTheSame(oldItem: CrmKpi, newItem: CrmKpi): Boolean =
            oldItem.kpiId == newItem.kpiId

        override fun areContentsTheSame(oldItem: CrmKpi, newItem: CrmKpi): Boolean =
            oldItem == newItem
    }
}
