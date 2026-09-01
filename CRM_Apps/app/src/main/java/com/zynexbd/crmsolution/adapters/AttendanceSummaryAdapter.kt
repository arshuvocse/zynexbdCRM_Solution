package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemAttendanceSummaryCardBinding
import com.zynexbd.crmsolution.models.EmployeeMonthlyAttendanceSummary

class AttendanceSummaryAdapter : ListAdapter<EmployeeMonthlyAttendanceSummary, AttendanceSummaryAdapter.SummaryViewHolder>(DiffCallback) {

    class SummaryViewHolder(private val binding: ItemAttendanceSummaryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EmployeeMonthlyAttendanceSummary) {
            val name = item.fullName.ifBlank { item.username }
            binding.textEmpName.text = name
            binding.textEmpRoleShift.text = "${item.role} • ${item.shiftName}"

            // Avatar Initials
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
            binding.textEmpAvatar.text = if (initials.isNotBlank()) initials else "EO"

            // Attendance Rate
            val rate = item.attendancePercentage
            binding.textAttendanceRate.text = "%.1f%%".format(rate)
            binding.progressAttendanceRate.progress = rate.toInt().coerceIn(0, 100)

            // Metrics
            binding.textPresentDays.text = "${item.presentDays}"
            binding.textOnTimeDays.text = "${item.onTimeDays}"
            binding.textLateDays.text = "${item.lateDays}"
            binding.textEarlyOutDays.text = "${item.earlyOutDays}"
            binding.textLeaveDays.text = "${item.approvedLeaveDays}"
            binding.textAbsentDays.text = "${item.absentDays}"

            binding.textWorkingDaysFooter.text = "📅 Total Working Days: ${item.totalWorkingDays} (excluding holidays & weekends)"
            val presence = if (item.totalPresenceTime.isNotBlank()) item.totalPresenceTime else "00:00"
            val parts = presence.split(":")
            val formattedPresence = if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                "${h}h ${m}m ($presence)"
            } else {
                presence
            }
            binding.textTotalPresenceTime.text = "⏱️ Total Duty: $formattedPresence"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val binding = ItemAttendanceSummaryCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SummaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<EmployeeMonthlyAttendanceSummary>() {
        override fun areItemsTheSame(oldItem: EmployeeMonthlyAttendanceSummary, newItem: EmployeeMonthlyAttendanceSummary): Boolean {
            return oldItem.userId == newItem.userId && oldItem.month == newItem.month && oldItem.year == newItem.year
        }

        override fun areContentsTheSame(oldItem: EmployeeMonthlyAttendanceSummary, newItem: EmployeeMonthlyAttendanceSummary): Boolean {
            return oldItem == newItem
        }
    }
}
