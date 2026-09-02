package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.models.CrmReportRow

class CrmReportAdapter(
    private val onItemClick: ((CrmReportRow) -> Unit)? = null
) : RecyclerView.Adapter<CrmReportAdapter.ReportViewHolder>() {

    private val items = mutableListOf<CrmReportRow>()

    fun submitList(newItems: List<CrmReportRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_crm_report_row, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.textReportTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.textReportSubtitle)
        private val tag: TextView = itemView.findViewById(R.id.textReportTag)
        private val val1: TextView = itemView.findViewById(R.id.textReportVal1)
        private val val2: TextView = itemView.findViewById(R.id.textReportVal2)
        private val val3: TextView = itemView.findViewById(R.id.textReportVal3)
        private val val4: TextView = itemView.findViewById(R.id.textReportVal4)

        fun bind(item: CrmReportRow) {
            title.text = item.title.ifBlank { "Item #${item.rowId}" }
            subtitle.text = item.subtitle
            subtitle.visibility = if (item.subtitle.isNotBlank()) View.VISIBLE else View.GONE

            if (item.tag.isNotBlank()) {
                tag.text = item.tag
                tag.visibility = View.VISIBLE
            } else {
                tag.visibility = View.GONE
            }

            val1.text = item.value1
            val1.visibility = if (item.value1.isNotBlank()) View.VISIBLE else View.GONE

            val2.text = item.value2
            val2.visibility = if (item.value2.isNotBlank()) View.VISIBLE else View.GONE

            val3.text = item.value3
            val3.visibility = if (item.value3.isNotBlank()) View.VISIBLE else View.GONE

            val4.text = item.value4
            val4.visibility = if (item.value4.isNotBlank()) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }
}
