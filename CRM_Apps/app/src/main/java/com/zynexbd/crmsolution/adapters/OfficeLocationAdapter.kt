package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemOfficeLocationBinding
import com.zynexbd.crmsolution.models.OfficeLocation

class OfficeLocationAdapter(
    private val onEdit: (OfficeLocation) -> Unit,
    private val onToggle: (OfficeLocation) -> Unit
) : ListAdapter<OfficeLocation, OfficeLocationAdapter.OfficeViewHolder>(DIFF_CALLBACK) {

    inner class OfficeViewHolder(val binding: ItemOfficeLocationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfficeViewHolder {
        val binding = ItemOfficeLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OfficeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OfficeViewHolder, position: Int) {
        val office = getItem(position)
        with(holder.binding) {
            textOfficeName.text = office.name
            textOfficeCoordinates.text = "📍 ${office.latitude}, ${office.longitude} (Radius: ${office.radiusMeters.toInt()}m)"

            if (!office.address.isNullOrBlank()) {
                textOfficeAddress.visibility = android.view.View.VISIBLE
                textOfficeAddress.text = "🏢 Address: ${office.address}"
            } else {
                textOfficeAddress.visibility = android.view.View.GONE
            }

            if (office.isActive) {
                textOfficeStatus.text = "Active"
                textOfficeStatus.setBackgroundResource(com.zynexbd.crmsolution.R.drawable.bg_status_active_pill)
                textOfficeStatus.setTextColor(Color.WHITE)
            } else {
                textOfficeStatus.text = "Inactive"
                textOfficeStatus.setBackgroundResource(com.zynexbd.crmsolution.R.drawable.bg_status_inactive_pill)
                textOfficeStatus.setTextColor(Color.WHITE)
            }

            buttonEditOffice.setOnClickListener { onEdit(office) }
            buttonToggleOffice.setOnClickListener { onToggle(office) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<OfficeLocation>() {
            override fun areItemsTheSame(oldItem: OfficeLocation, newItem: OfficeLocation) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: OfficeLocation, newItem: OfficeLocation) = oldItem == newItem
        }
    }
}
