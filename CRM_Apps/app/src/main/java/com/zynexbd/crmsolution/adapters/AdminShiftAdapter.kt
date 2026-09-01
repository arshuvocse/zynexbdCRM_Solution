package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemShiftCardBinding
import com.zynexbd.crmsolution.models.Shift
import java.text.SimpleDateFormat
import java.util.Locale

class AdminShiftAdapter(
    private val onEditClick: (Shift) -> Unit,
    private val onDeleteClick: (Shift) -> Unit,
    private val onSetDefaultClick: (Shift) -> Unit
) : ListAdapter<Shift, AdminShiftAdapter.ShiftViewHolder>(DIFF_CALLBACK) {

    inner class ShiftViewHolder(val binding: ItemShiftCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShiftViewHolder {
        val binding = ItemShiftCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShiftViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShiftViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textShiftName.text = item.shiftName

            val startFormatted = formatTimeDisplay(item.startTime)
            val endFormatted = formatTimeDisplay(item.endTime)
            textShiftTimeRange.text = "🕒 $startFormatted – $endFormatted"

            textGracePeriod.text = "⏱ ${item.gracePeriodMinutes}m Grace"

            if (item.isDefault) {
                badgeDefaultShift.visibility = View.VISIBLE
                btnSetDefault.visibility = View.GONE
            } else {
                badgeDefaultShift.visibility = View.GONE
                btnSetDefault.visibility = View.VISIBLE
            }

            btnEditShift.setOnClickListener { onEditClick(item) }
            btnDeleteShift.visibility = View.GONE
            btnDeleteShift.setOnClickListener { onDeleteClick(item) }
            btnSetDefault.setOnClickListener { onSetDefaultClick(item) }
        }
    }

    private fun formatTimeDisplay(rawTime: String): String {
        return try {
            val inputFormats = arrayOf(
                SimpleDateFormat("HH:mm:ss", Locale.US),
                SimpleDateFormat("HH:mm", Locale.US),
                SimpleDateFormat("hh:mm a", Locale.US)
            )
            for (sdf in inputFormats) {
                try {
                    val date = sdf.parse(rawTime)
                    if (date != null) {
                        return SimpleDateFormat("hh:mm a", Locale.US).format(date)
                    }
                } catch (e: Exception) {}
            }
            rawTime
        } catch (e: Exception) {
            rawTime
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Shift>() {
            override fun areItemsTheSame(oldItem: Shift, newItem: Shift) = oldItem.shiftId == newItem.shiftId
            override fun areContentsTheSame(oldItem: Shift, newItem: Shift) = oldItem == newItem
        }
    }
}
