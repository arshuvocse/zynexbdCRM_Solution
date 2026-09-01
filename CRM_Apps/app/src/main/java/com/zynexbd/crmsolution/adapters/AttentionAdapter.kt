package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemAttentionCardBinding
import com.zynexbd.crmsolution.models.AttentionItem

class AttentionAdapter(
    private val onActionClick: (AttentionItem) -> Unit
) : RecyclerView.Adapter<AttentionAdapter.AttentionViewHolder>() {

    private val items = mutableListOf<AttentionItem>()

    fun setItems(newItems: List<AttentionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttentionViewHolder {
        val binding = ItemAttentionCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AttentionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttentionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AttentionViewHolder(private val binding: ItemAttentionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AttentionItem) {
            binding.textAttentionTitle.text = item.title
            binding.textAttentionDescription.text = item.description
            binding.buttonAttentionAction.text = item.actionType.uppercase()

            val severity = item.severity.uppercase()
            binding.textSeverityBadge.text = severity

            val context = binding.root.context
            binding.textSeverityBadge.setTextColor(Color.WHITE)
            when (severity) {
                "HIGH" -> binding.textSeverityBadge.background = androidx.core.content.ContextCompat.getDrawable(context, com.zynexbd.crmsolution.R.drawable.bg_badge_danger)
                "MEDIUM" -> binding.textSeverityBadge.background = androidx.core.content.ContextCompat.getDrawable(context, com.zynexbd.crmsolution.R.drawable.bg_badge_warning)
                else -> binding.textSeverityBadge.background = androidx.core.content.ContextCompat.getDrawable(context, com.zynexbd.crmsolution.R.drawable.bg_badge_primary)
            }

            binding.buttonAttentionAction.setOnClickListener {
                onActionClick(item)
            }
        }
    }
}
