package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemAdminPaymentBinding
import com.zynexbd.crmsolution.models.AdminPaymentInfo

class AdminPaymentAdapter : RecyclerView.Adapter<AdminPaymentAdapter.ViewHolder>() {

    private var items: List<AdminPaymentInfo> = emptyList()

    fun setItems(list: List<AdminPaymentInfo>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemAdminPaymentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = items[position]
        with(holder.binding) {
            textAdminName.text = info.adminName
            textAdminUsername.text = "@${info.adminUsername}"
            textDueDate.text = info.formattedDueDate ?: "Not Set"
            textPhone.text = info.adminPhone ?: "N/A"

            // Days remaining color coding
            when {
                info.isExpired -> {
                    textDaysRemaining.text = "Expired"
                    textDaysRemaining.setTextColor(Color.parseColor("#EF4444"))
                    chipStatus.text = "Expired"
                    chipStatus.setBackgroundColor(Color.parseColor("#EF4444"))
                }
                info.isWarningPeriod -> {
                    textDaysRemaining.text = "${info.daysRemaining} days"
                    textDaysRemaining.setTextColor(Color.parseColor("#D97706"))
                    chipStatus.text = "⚠️ Warning"
                    chipStatus.setBackgroundColor(Color.parseColor("#F59E0B"))
                }
                else -> {
                    textDaysRemaining.text = "${info.daysRemaining} days"
                    textDaysRemaining.setTextColor(Color.parseColor("#059669"))
                    chipStatus.text = "Active"
                    chipStatus.setBackgroundColor(Color.parseColor("#059669"))
                }
            }
        }
    }
}
