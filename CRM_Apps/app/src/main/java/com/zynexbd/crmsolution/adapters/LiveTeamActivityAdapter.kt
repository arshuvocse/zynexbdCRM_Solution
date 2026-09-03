package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemCrmLiveActivityBinding
import com.zynexbd.crmsolution.models.LiveTeamActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LiveTeamActivityAdapter(
    private val onItemClick: (LiveTeamActivity) -> Unit
) : RecyclerView.Adapter<LiveTeamActivityAdapter.ViewHolder>() {

    private val items = mutableListOf<LiveTeamActivity>()
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun submitList(newItems: List<LiveTeamActivity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun prependItem(item: LiveTeamActivity) {
        items.add(0, item)
        // Keep max 30 items
        if (items.size > 30) {
            items.removeAt(items.size - 1)
        }
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemCrmLiveActivityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val b: ItemCrmLiveActivityBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: LiveTeamActivity) {
            b.textUserName.text = item.userName.ifBlank { "Executive" }
            b.textActivityTitle.text = item.title
            b.textActivitySubtitle.text = item.subtitle

            // Avatar initial with colorful dynamic circle & crisp white font
            val initial = item.userName.trim().take(1).uppercase()
            b.textAvatarInitials.text = if (initial.isNotBlank()) initial else "U"
            val avatarPalette = intArrayOf(
                Color.parseColor("#2563EB"), // Royal Blue
                Color.parseColor("#7C3AED"), // Deep Purple
                Color.parseColor("#059669"), // Emerald Green
                Color.parseColor("#D97706"), // Amber Orange
                Color.parseColor("#DB2777"), // Vibrant Rose
                Color.parseColor("#0891B2"), // Ocean Teal
                Color.parseColor("#4F46E5")  // Indigo
            )
            val colorIndex = Math.abs(item.userName.hashCode()) % avatarPalette.size
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(avatarPalette[colorIndex])
            }
            b.textAvatarInitials.background = bgDrawable
            b.textAvatarInitials.setTextColor(Color.WHITE)

            // Action Badge styling
            when (item.actionType) {
                "LeadCreated" -> {
                    b.textActionBadge.text = "📝 Lead"
                    b.textActionBadge.setBackgroundResource(R.drawable.bg_status_active_pill)
                }
                "FollowUpAdded" -> {
                    b.textActionBadge.text = "📞 Follow-up"
                    b.textActionBadge.setBackgroundResource(R.drawable.bg_badge_primary)
                }
                "StatusChanged" -> {
                    b.textActionBadge.text = "🔄 Status"
                    b.textActionBadge.setBackgroundResource(R.drawable.bg_badge_purple)
                }
                "LeadAssigned", "LeadReassigned" -> {
                    b.textActionBadge.text = "👤 Assigned"
                    b.textActionBadge.setBackgroundResource(R.drawable.bg_badge_warning)
                }
                "CustomerVisit" -> {
                    b.textActionBadge.text = "📍 Visit"
                    b.textActionBadge.setBackgroundColor(Color.parseColor("#EA580C"))
                }
                "KpiCreated", "KpiUpdated" -> {
                    b.textActionBadge.text = "🎯 KPI"
                    b.textActionBadge.setBackgroundColor(Color.parseColor("#EC4899"))
                }
                "RemarkAdded" -> {
                    b.textActionBadge.text = "💬 Note"
                    b.textActionBadge.setBackgroundColor(Color.parseColor("#64748B"))
                }
                else -> {
                    b.textActionBadge.text = "⚡ Activity"
                    b.textActionBadge.setBackgroundResource(R.drawable.bg_badge_primary)
                }
            }
            b.textActionBadge.setTextColor(Color.WHITE)

            // Relative Time calculation
            b.textTimeAgo.text = formatTimeAgo(item.createdAtUtc)

            b.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun formatTimeAgo(dateStr: String): String {
            if (dateStr.isBlank()) return "Just now"
            return try {
                val cleanDateStr = if (dateStr.length >= 19) dateStr.substring(0, 19) else dateStr
                val date = isoFormat.parse(cleanDateStr) ?: return "Just now"
                val diffMs = System.currentTimeMillis() - date.time
                val diffSec = diffMs / 1000
                when {
                    diffSec < 30 -> "Just now"
                    diffSec < 60 -> "${diffSec}s ago"
                    diffSec < 3600 -> "${diffSec / 60}m ago"
                    diffSec < 86400 -> "${diffSec / 3600}h ago"
                    else -> "${diffSec / 86400}d ago"
                }
            } catch (e: Exception) {
                "Just now"
            }
        }
    }
}
