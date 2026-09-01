package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemDashboardActivityTimelineBinding

data class DashboardActivityItem(
    val title: String,
    val subtitle: String,
    val time: String,
    val tag: String,
    val tagColor: String,
    val iconRes: Int = R.drawable.ic_pulse
)

class DashboardActivityAdapter(private var items: List<DashboardActivityItem>) :
    RecyclerView.Adapter<DashboardActivityAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemDashboardActivityTimelineBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemDashboardActivityTimelineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.textActivityTitle.text = item.title
        b.textActivitySubtitle.text = item.subtitle
        b.textActivityTime.text = item.time
        b.textActivityTag.text = item.tag
        b.imageActivityIcon.setImageResource(item.iconRes)

        try {
            val color = Color.parseColor(item.tagColor)
            b.textActivityTag.setTextColor(color)
            b.imageActivityIcon.setColorFilter(color)
        } catch (e: Exception) {
            // Default
        }
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<DashboardActivityItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
