package com.zynexbd.crmsolution.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemLeaveApplicationBinding
import com.zynexbd.crmsolution.models.LeaveApplicationResponse

/**
 * Shared list adapter for both the user's own leave history (with Cancel)
 * and the admin review queue (with Approve/Reject). Action buttons only
 * show while the application status is "Pending".
 */
class LeaveApplicationAdapter(
    private val mode: Mode,
    private val onCancel: (LeaveApplicationResponse) -> Unit = {},
    private val onApprove: (LeaveApplicationResponse) -> Unit = {},
    private val onReject: (LeaveApplicationResponse) -> Unit = {}
) : ListAdapter<LeaveApplicationResponse, LeaveApplicationAdapter.ViewHolder>(DIFF_CALLBACK) {

    enum class Mode { USER, ADMIN }

    inner class ViewHolder(val binding: ItemLeaveApplicationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLeaveApplicationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isPending = item.status == "Pending"

        with(holder.binding) {
            textLeaveType.text = item.leaveTypeName
            textStatus.text = item.status
            textDates.text = "${item.startDate} to ${item.endDate} (${item.totalDays} days)"
            textReason.text = item.reason

            if (mode == Mode.ADMIN) {
                textUserName.visibility = View.VISIBLE
                textUserName.text = item.userName
                buttonCancel.visibility = View.GONE
                buttonApprove.visibility = if (isPending) View.VISIBLE else View.GONE
                buttonReject.visibility = if (isPending) View.VISIBLE else View.GONE
                buttonApprove.setOnClickListener { onApprove(item) }
                buttonReject.setOnClickListener { onReject(item) }
            } else {
                textUserName.visibility = View.GONE
                buttonApprove.visibility = View.GONE
                buttonReject.visibility = View.GONE
                buttonCancel.visibility = if (isPending) View.VISIBLE else View.GONE
                buttonCancel.setOnClickListener { onCancel(item) }
            }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LeaveApplicationResponse>() {
            override fun areItemsTheSame(oldItem: LeaveApplicationResponse, newItem: LeaveApplicationResponse) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: LeaveApplicationResponse, newItem: LeaveApplicationResponse) = oldItem == newItem
        }
    }
}
