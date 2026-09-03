package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemCrmProductServiceBinding
import com.zynexbd.crmsolution.models.CrmProductService

class ProductServiceAdapter(
    private val onEditClicked: (CrmProductService) -> Unit,
    private val onToggleStatusClicked: (CrmProductService) -> Unit
) : RecyclerView.Adapter<ProductServiceAdapter.ViewHolder>() {

    private val items = mutableListOf<CrmProductService>()

    fun submitList(newItems: List<CrmProductService>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemCrmProductServiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val b: ItemCrmProductServiceBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CrmProductService) {
            b.textName.text = item.name

            if (!item.code.isNullOrBlank()) {
                b.textCode.visibility = View.VISIBLE
                b.textCode.text = item.code
            } else {
                b.textCode.visibility = View.GONE
            }

            if (item.price != null && item.price > 0) {
                b.textPrice.visibility = View.VISIBLE
                b.textPrice.text = String.format("৳ %,.2f", item.price)
            } else {
                b.textPrice.visibility = View.GONE
            }

            if (!item.description.isNullOrBlank()) {
                b.textDescription.visibility = View.VISIBLE
                b.textDescription.text = item.description
            } else {
                b.textDescription.visibility = View.GONE
            }

            // Status Pill
            if (item.isActive) {
                b.textStatus.text = "Active"
                b.textStatus.setTextColor(Color.WHITE)
                b.textStatus.setBackgroundResource(R.drawable.bg_status_active_pill)
                b.buttonToggleStatus.text = "Inactivate"
                b.buttonToggleStatus.setTextColor(Color.parseColor("#EF4444"))
            } else {
                b.textStatus.text = "Inactive"
                b.textStatus.setTextColor(Color.WHITE)
                b.textStatus.setBackgroundResource(R.drawable.bg_status_inactive_pill)
                b.buttonToggleStatus.text = "Activate"
                b.buttonToggleStatus.setTextColor(Color.parseColor("#10B981"))
            }

            b.buttonEdit.setOnClickListener { onEditClicked(item) }
            b.buttonToggleStatus.setOnClickListener { onToggleStatusClicked(item) }
        }
    }
}
