package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemTimelineEventBinding
import com.zynexbd.crmsolution.utils.TimelineEvent
import com.zynexbd.crmsolution.utils.TimelineEventType

class TimelineAdapter(
    private var events: List<TimelineEvent> = emptyList(),
    private val onItemClick: (TimelineEvent) -> Unit
) : RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder>() {

    fun setEvents(newEvents: List<TimelineEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemTimelineEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val event = events[position]
        holder.bind(event, position, events.size)
    }

    override fun getItemCount(): Int = events.size

    inner class TimelineViewHolder(private val binding: ItemTimelineEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: TimelineEvent, position: Int, totalSize: Int) {
            // Line visibility
            binding.viewTopLine.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
            binding.viewBottomLine.visibility = if (position == totalSize - 1) View.INVISIBLE else View.VISIBLE

            // Icon by event type
            val icon = when (event.type) {
                TimelineEventType.START -> "🟢"
                TimelineEventType.MOVEMENT -> "🚗"
                TimelineEventType.CUSTOMER_VISIT -> "📍"
                TimelineEventType.OFFICE_VISIT -> "🟠"
                TimelineEventType.LONG_STOP -> "🟡"
                TimelineEventType.SHORT_STOP -> "⚪"
                TimelineEventType.GPS_GAP -> "⚠"
                TimelineEventType.LAST_POINT -> "🔴"
            }
            binding.textTimelineIcon.text = icon

            // Content
            binding.textEventTime.text = event.timeStr
            binding.textEventTitle.text = event.title
            binding.textEventDescription.text = event.description

            if (!event.badgeText.isNullOrBlank()) {
                binding.textEventBadge.visibility = View.VISIBLE
                binding.textEventBadge.text = event.badgeText
            } else {
                binding.textEventBadge.visibility = View.GONE
            }

            binding.layoutTimelineItem.setOnClickListener {
                onItemClick(event)
            }
        }
    }
}
