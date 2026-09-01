package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemHolidayCardBinding
import com.zynexbd.crmsolution.models.Holiday
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminHolidayAdapter(
    private val onEditClick: (Holiday) -> Unit,
    private val onToggleStatusClick: (Holiday) -> Unit,
    private val onDeleteClick: (Holiday) -> Unit
) : ListAdapter<Holiday, AdminHolidayAdapter.HolidayViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolidayViewHolder {
        val binding = ItemHolidayCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HolidayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HolidayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HolidayViewHolder(private val binding: ItemHolidayCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(holiday: Holiday) {
            val context = binding.root.context

            binding.textHolidayName.text = holiday.name

            // Parse Date
            val parsedDate = parseIsoDate(holiday.date)
            if (parsedDate != null) {
                val dayFormat = SimpleDateFormat("dd", Locale.US)
                val monthFormat = SimpleDateFormat("MMM", Locale.US)
                val dayOfWeekFormat = SimpleDateFormat("EEEE, yyyy", Locale.US)

                binding.textHolidayDay.text = dayFormat.format(parsedDate)
                binding.textHolidayMonth.text = monthFormat.format(parsedDate).uppercase()
                binding.textDayOfWeek.text = dayOfWeekFormat.format(parsedDate)
            } else {
                binding.textHolidayDay.text = "--"
                binding.textHolidayMonth.text = "DATE"
                binding.textDayOfWeek.text = holiday.date
            }

            // Status chip
            if (holiday.isActive) {
                binding.textStatusChip.text = "🟢 Active"
                binding.textStatusChip.setTextColor(ContextCompat.getColor(context, R.color.success))
                binding.btnToggleActive.setImageResource(R.drawable.ic_check_circle)
                binding.btnToggleActive.setColorFilter(ContextCompat.getColor(context, R.color.success))
                binding.root.alpha = 1.0f
            } else {
                binding.textStatusChip.text = "⚪ Inactive"
                binding.textStatusChip.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.btnToggleActive.setImageResource(R.drawable.ic_cancel)
                binding.btnToggleActive.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                binding.root.alpha = 0.6f
            }

            // Recurring chip
            if (holiday.isRecurring) {
                binding.textRecurringChip.visibility = View.VISIBLE
            } else {
                binding.textRecurringChip.visibility = View.GONE
            }

            // Description
            if (!holiday.description.isNullOrBlank()) {
                binding.textDescription.visibility = View.VISIBLE
                binding.textDescription.text = holiday.description
            } else {
                binding.textDescription.visibility = View.GONE
            }

            // Click listeners
            binding.btnEditHoliday.setOnClickListener { onEditClick(holiday) }
            binding.btnToggleActive.setOnClickListener { onToggleStatusClick(holiday) }
            binding.root.setOnLongClickListener {
                onDeleteClick(holiday)
                true
            }
        }

        private fun parseIsoDate(dateStr: String): Date? {
            val formats = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd"
            )
            for (pattern in formats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    return sdf.parse(dateStr)
                } catch (_: Exception) {}
            }
            return null
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Holiday>() {
        override fun areItemsTheSame(oldItem: Holiday, newItem: Holiday): Boolean {
            return oldItem.holidayId == newItem.holidayId
        }

        override fun areContentsTheSame(oldItem: Holiday, newItem: Holiday): Boolean {
            return oldItem == newItem
        }
    }
}
