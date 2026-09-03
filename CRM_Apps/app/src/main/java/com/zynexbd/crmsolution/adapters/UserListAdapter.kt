package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemUserBinding
import com.zynexbd.crmsolution.models.User

class UserListAdapter(
    private val onToggleActive: (User) -> Unit,
    private val onResetPassword: (User) -> Unit,
    private val onViewLocation: (User) -> Unit,
    private val onViewCustomers: (User) -> Unit,
    private val onViewFollowUps: (User) -> Unit,
    private val onCallUser: (User) -> Unit,
    private val onResetDevice: (User) -> Unit,
    private val onEditOffice: (User) -> Unit
) : ListAdapter<User, UserListAdapter.UserViewHolder>(DIFF_CALLBACK) {

    inner class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        with(holder.binding) {
            val displayName = user.name.ifBlank { user.username }
            textFullName.text = displayName
            textUsername.text = "@${user.username}"

            // Avatar Initials
            val initial = displayName.trim().take(1).uppercase()
            textAvatarInitials.text = if (initial.isNotBlank()) initial else "U"

            // Role Badge Styling with solid vibrant pill and crisp white text
            textRole.text = user.role.uppercase()
            textRole.setTextColor(Color.WHITE)
            when (user.role.lowercase()) {
                "admin" -> textRole.setBackgroundResource(R.drawable.bg_role_admin_pill)
                "manager" -> textRole.setBackgroundResource(R.drawable.bg_badge_primary)
                else -> textRole.setBackgroundResource(R.drawable.bg_role_user_pill)
            }

            val officeLabel = if (user.role.equals("Admin", ignoreCase = true)) {
                if (!user.assignedOfficeLocationNames.isNullOrEmpty()) {
                    user.assignedOfficeLocationNames.joinToString(", ")
                } else {
                    user.officeLocationName ?: "All Offices (Unrestricted)"
                }
            } else {
                user.officeLocationName ?: "Unassigned"
            }

            if (user.isActive) {
                textStatus.text = "🟢 Active  •  🏢 $officeLabel"
                textStatus.setTextColor(Color.parseColor("#059669"))
            } else {
                textStatus.text = "🔴 Disabled  •  🏢 $officeLabel"
                textStatus.setTextColor(Color.parseColor("#DC2626"))
            }

            if (!user.phoneNumber.isNullOrBlank()) {
                textPhoneNumber.text = user.phoneNumber
            } else {
                textPhoneNumber.text = "Contact"
            }

            // Device Binding Status
            if (user.role.equals("Admin", ignoreCase = true)) {
                layoutDeviceInfo.visibility = View.GONE
            } else {
                layoutDeviceInfo.visibility = View.VISIBLE
                if (!user.boundDeviceId.isNullOrBlank()) {
                    val modelName = user.deviceModel?.takeIf { it.isNotBlank() } ?: "Locked Device"
                    textDeviceBoundInfo.text = "🔒 Bound Device: $modelName"
                    textDeviceBoundInfo.setTextColor(Color.parseColor("#0284C7"))
                } else {
                    textDeviceBoundInfo.text = "🔓 Device: Unbound (First login locks)"
                    textDeviceBoundInfo.setTextColor(Color.parseColor("#64748B"))
                }
                buttonResetDevice.visibility = View.GONE
            }

            // Always hide buttonToggle and buttonResetDevice
            buttonToggle.visibility = View.GONE
            buttonResetDevice.visibility = View.GONE

            buttonViewLocation.setOnClickListener { onViewLocation(user) }
            buttonToggle.setOnClickListener { onToggleActive(user) }
            buttonResetPassword.setOnClickListener { onResetPassword(user) }
            buttonViewCustomers.setOnClickListener { onViewCustomers(user) }
            buttonViewFollowUps.setOnClickListener { onViewFollowUps(user) }
            buttonResetDevice.setOnClickListener { onResetDevice(user) }
            textPhoneNumber.setOnClickListener { onCallUser(user) }
            textStatus.setOnClickListener { onEditOffice(user) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
        }
    }
}
