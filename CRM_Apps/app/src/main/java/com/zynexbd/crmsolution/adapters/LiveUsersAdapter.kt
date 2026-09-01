package com.zynexbd.crmsolution.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zynexbd.crmsolution.databinding.ItemLiveUserRowBinding
import com.zynexbd.crmsolution.models.LocationResponse
import com.zynexbd.crmsolution.utils.GpsRouteProcessor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveUsersAdapter(
    private val onUserClicked: (LocationResponse) -> Unit
) : RecyclerView.Adapter<LiveUsersAdapter.ViewHolder>() {

    private var allUsers: List<LocationResponse> = emptyList()
    private var displayedUsers: List<LocationResponse> = emptyList()
    private val addressCache = mutableMapOf<Pair<Double, Double>, String>()

    fun setUsers(list: List<LocationResponse>) {
        allUsers = list
        displayedUsers = list
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        displayedUsers = if (query.isBlank()) {
            allUsers
        } else {
            val q = query.trim().lowercase()
            allUsers.filter {
                (it.name?.lowercase()?.contains(q) == true) ||
                (it.username?.lowercase()?.contains(q) == true) ||
                "employee #${it.userId}".contains(q)
            }
        }
        notifyDataSetChanged()
    }

    fun isEmpty(): Boolean = displayedUsers.isEmpty()

    class ViewHolder(val binding: ItemLiveUserRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLiveUserRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = displayedUsers.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = displayedUsers[position]
        with(holder.binding) {
            val displayName = user.name?.ifBlank { user.username } ?: user.username ?: "Employee #${user.userId}"
            textFullName.text = displayName
            textUsername.text = if (!user.username.isNullOrBlank()) "@${user.username}" else "ID: #${user.userId}"

            // Avatar Initials
            val initial = displayName.trim().take(1).uppercase()
            textAvatarInitials.text = if (initial.isNotBlank()) initial else "U"

            // Online / Offline Status Badge
            if (user.isOnline) {
                textOnlineStatus.text = "ONLINE"
                textOnlineStatus.setTextColor(Color.parseColor("#FFFFFF"))
                textOnlineStatus.setBackgroundResource(com.zynexbd.crmsolution.R.drawable.bg_status_active_pill)
            } else {
                textOnlineStatus.text = "OFFLINE"
                textOnlineStatus.setTextColor(Color.parseColor("#FFFFFF"))
                textOnlineStatus.setBackgroundResource(com.zynexbd.crmsolution.R.drawable.bg_status_inactive_pill)
            }

            // Speed
            val speed = user.speed ?: 0.0
            if (speed > 2.0) {
                textSpeed.text = "• 🚗 %.1f km/h".format(speed)
                textSpeed.setTextColor(Color.parseColor("#059669"))
            } else {
                textSpeed.text = "• ⏸ Idle (0 km/h)"
                textSpeed.setTextColor(Color.parseColor("#64748B"))
            }

            // Battery
            if (user.deviceBattery != null && user.deviceBattery > 0) {
                textBattery.visibility = View.VISIBLE
                textBattery.text = "• 🔋 ${user.deviceBattery}%"
            } else {
                textBattery.visibility = View.GONE
            }

            // Address / Location
            val cachedAddr = if (user.latitude != null && user.longitude != null) {
                val key = Pair(Math.round(user.latitude * 1000.0) / 1000.0, Math.round(user.longitude * 1000.0) / 1000.0)
                addressCache[key]
            } else null

            if (!user.locationAddress.isNullOrBlank()) {
                textAddress.text = "📍 ${user.locationAddress}"
            } else if (!cachedAddr.isNullOrBlank()) {
                textAddress.text = "📍 $cachedAddr"
            } else if (user.latitude != null && user.longitude != null) {
                textAddress.text = "📍 Lat: ${"%.4f".format(user.latitude)}, Lng: ${"%.4f".format(user.longitude)}"
                // Asynchronously fetch specific address
                val key = Pair(Math.round(user.latitude * 1000.0) / 1000.0, Math.round(user.longitude * 1000.0) / 1000.0)
                val appContext = root.context.applicationContext
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val resolved = com.zynexbd.crmsolution.utils.AddressHelper.resolveSpecificAddress(
                        appContext,
                        user.latitude,
                        user.longitude
                    )
                    if (!resolved.isNullOrBlank()) {
                        addressCache[key] = resolved
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            textAddress.text = "📍 $resolved"
                        }
                    }
                }
            } else {
                textAddress.text = "📍 Location not available"
            }

            // Formatted Time
            textLastUpdated.text = "🕒 " + formatTime(user.recordedAt)

            root.setOnClickListener {
                onUserClicked(user)
            }
        }
    }

    private fun formatTime(recordedAt: String?): String {
        if (recordedAt.isNullOrBlank()) return "Just now"
        val millis = GpsRouteProcessor.parseTimestampToMillis(recordedAt)
        return if (millis > 0L) {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(millis))
        } else {
            recordedAt
        }
    }
}
