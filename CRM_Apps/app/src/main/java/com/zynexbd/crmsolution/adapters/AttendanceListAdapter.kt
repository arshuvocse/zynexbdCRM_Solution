package com.zynexbd.crmsolution.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ItemAttendanceBinding
import com.zynexbd.crmsolution.models.AttendanceResponse

class AttendanceListAdapter(private val showUserName: Boolean = false) :
    ListAdapter<AttendanceResponse, AttendanceListAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val addressCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    inner class ViewHolder(val binding: ItemAttendanceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            // Duty type label
            val isPunchIn = item.type == "In"
            textType.text = if (isPunchIn) "🟢 Duty In" else "🔴 Duty Out"
            textType.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isPunchIn) R.color.statusActive else android.R.color.holo_red_light
                )
            )

            // Separate Date and Time
            val (dateStr, timeStr) = com.zynexbd.crmsolution.utils.ReportExporter.formatDateTime(item.timestamp)
            textDate.text = "📅 $dateStr"
            textTime.text = "🕒 $timeStr"
            textTimestamp.text = item.timestamp ?: "-"

            // Day-wise Duty Working Duration (Hours & Minutes)
            val duration = calculateDutyDurationForRecord(item, currentList)
            if (!duration.isNullOrBlank()) {
                layoutDutyDuration.visibility = View.VISIBLE
                textDutyDuration.text = duration
                when {
                    duration.contains("Ongoing", ignoreCase = true) -> {
                        textDutyDuration.setTextColor(android.graphics.Color.parseColor("#2563EB")) // Blue
                    }
                    duration.contains("hrs", ignoreCase = true) || duration.contains("mins", ignoreCase = true) -> {
                        textDutyDuration.setTextColor(android.graphics.Color.parseColor("#059669")) // Green
                    }
                    else -> {
                        textDutyDuration.setTextColor(android.graphics.Color.parseColor("#D97706")) // Amber
                    }
                }
            } else {
                layoutDutyDuration.visibility = View.GONE
            }

            // Geofence badge
            val withinGeofence = item.isWithinGeofence
            textGeofence.text = if (withinGeofence) "✅ Within Office" else "⚠️ Outside Office"

            // Shift & Status Badges
            val shift = item.shiftName ?: "General Shift"
            textShift.text = "🔄 $shift"

            val rawStatus = item.status ?: "On Time"
            val status = formatAttendanceStatus(rawStatus)
            textStatus.setTextColor(android.graphics.Color.WHITE)
            when {
                status.contains("On Time", ignoreCase = true) || status.contains("Completed", ignoreCase = true) -> {
                    textStatus.text = "🟢 $status"
                    textStatus.setBackgroundResource(R.drawable.bg_status_active)
                }
                status.contains("Late", ignoreCase = true) -> {
                    textStatus.text = "🟡 $status"
                    textStatus.setBackgroundColor(android.graphics.Color.parseColor("#F59E0B"))
                }
                status.contains("Early", ignoreCase = true) -> {
                    textStatus.text = "🟠 $status"
                    textStatus.setBackgroundColor(android.graphics.Color.parseColor("#F97316"))
                }
                else -> {
                    textStatus.text = status
                    textStatus.setBackgroundColor(android.graphics.Color.parseColor("#2563EB"))
                }
            }

            // Username (admin view)
            if (showUserName && !item.userName.isNullOrBlank()) {
                textUserName.visibility = View.VISIBLE
                textUserName.text = "👤 ${item.userName}"
            } else {
                textUserName.visibility = View.GONE
            }

            // Location Address & Google Maps Pin Button
            val hasLocation = (item.latitude != 0.0 || item.longitude != 0.0)
            if (hasLocation) {
                layoutLocation.visibility = View.VISIBLE
                textCoordinates.text = "📍 %.5f, %.5f".format(item.latitude, item.longitude)
                
                val addressKey = "%.5f,%.5f".format(item.latitude, item.longitude)
                val cached = addressCache[addressKey]
                if (cached != null) {
                    textLocationAddress.text = "📍 $cached"
                } else {
                    textLocationAddress.text = "📍 লোকেশন খোঁজা হচ্ছে..."
                    Thread {
                        val resolved = com.zynexbd.crmsolution.utils.AddressHelper.resolveSpecificAddress(
                            context, item.latitude, item.longitude
                        )
                        val finalAddr = resolved ?: "%.5f, %.5f".format(item.latitude, item.longitude)
                        addressCache[addressKey] = finalAddr
                        (context as? android.app.Activity)?.runOnUiThread {
                            textLocationAddress.text = "📍 $finalAddr"
                        }
                    }.start()
                }

                buttonOpenMap.setOnClickListener {
                    val label = "${item.userName ?: "Employee"} - ${if (item.type == "In") "Duty In" else "Duty Out"}"
                    val uri = android.net.Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${android.net.Uri.encode(label)})")
                    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    try {
                        context.startActivity(mapIntent)
                    } catch (e: Exception) {
                        val fallbackUri = android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${item.latitude},${item.longitude}")
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, fallbackUri))
                    }
                }
            } else {
                layoutLocation.visibility = View.GONE
            }

            // Selfie image
            if (!item.selfieUrl.isNullOrBlank()) {
                containerSelfie.visibility = View.VISIBLE
                progressSelfie.visibility = View.VISIBLE

                val fullImageUrl = resolveImageUrl(item.selfieUrl)

                Glide.with(context)
                    .load(fullImageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .placeholder(R.drawable.bg_glass_card)
                    .error(android.R.drawable.ic_menu_camera)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            progressSelfie.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            progressSelfie.visibility = View.GONE
                            return false
                        }
                    })
                    .into(imageSelfie)

                // Click listener to show full image modal
                containerSelfie.setOnClickListener {
                    showFullImageModal(context, fullImageUrl, item)
                }
            } else {
                containerSelfie.visibility = View.GONE
                containerSelfie.setOnClickListener(null)
            }
        }
    }

    private fun resolveImageUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        val baseUrl = com.zynexbd.crmsolution.BuildConfig.API_BASE_URL.trimEnd('/')
        val path = trimmed.replace("\\", "/").trimStart('/')
        return "$baseUrl/$path"
    }

    private fun showFullImageModal(context: Context, fullImageUrl: String, item: AttendanceResponse) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_full_image_preview, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val textTitle = dialogView.findViewById<TextView>(R.id.dialogImageTitle)
        val textSubtitle = dialogView.findViewById<TextView>(R.id.dialogImageSubtitle)
        val buttonClose = dialogView.findViewById<ImageButton>(R.id.buttonCloseImageDialog)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.imageProgressBar)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogFullImageView)

        val typeText = if (item.type == "In") "Duty In Selfie" else "Duty Out Selfie"
        val userText = if (!item.userName.isNullOrBlank()) " • 👤 ${item.userName}" else ""
        textTitle.text = "$typeText$userText"
        textSubtitle.text = "🕒 ${item.timestamp ?: "Date N/A"}"

        buttonClose.setOnClickListener { dialog.dismiss() }

        progressBar.visibility = View.VISIBLE
        Glide.with(context)
            .load(fullImageUrl)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .fitCenter()
            .error(android.R.drawable.ic_menu_camera)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            })
            .into(imageView)

        dialog.show()
    }

    private fun formatAttendanceStatus(status: String?): String {
        if (status.isNullOrBlank()) return "On Time"
        val trimmed = status.trim()

        // Match HH:mm format e.g. "Late (02:06)", "Early Out (01:30)", "Overtime (01:45)"
        val timeRegex = Regex("""(Late|Early(?:\s*Out)?|Overtime)\s*\(?(\d{1,2}):(\d{2})\)?""", RegexOption.IGNORE_CASE)
        val timeMatch = timeRegex.find(trimmed)
        if (timeMatch != null) {
            val matchedType = timeMatch.groupValues[1]
            val prefix = when {
                matchedType.contains("Early", ignoreCase = true) -> "Early Out"
                matchedType.contains("Overtime", ignoreCase = true) -> "Overtime"
                else -> "Late"
            }
            val hours = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val mins = timeMatch.groupValues[3].toIntOrNull() ?: 0
            return when {
                hours > 0 && mins > 0 -> "$prefix by ${hours}h ${mins}m"
                hours > 0 -> "$prefix by ${hours} hr"
                mins > 0 -> "$prefix by ${mins} min"
                else -> "$prefix (0m)"
            }
        }

        // Match minutes only: e.g. "Late (126m)", "Late (126)"
        val minRegex = Regex("""(Late|Early(?:\s*Out)?|Overtime)\s*\(?(\d+)\s*(?:m|min|mins|minutes)?\)?""", RegexOption.IGNORE_CASE)
        val minMatch = minRegex.find(trimmed)
        if (minMatch != null && !trimmed.contains(":")) {
            val matchedType = minMatch.groupValues[1]
            val prefix = when {
                matchedType.contains("Early", ignoreCase = true) -> "Early Out"
                matchedType.contains("Overtime", ignoreCase = true) -> "Overtime"
                else -> "Late"
            }
            val totalMins = minMatch.groupValues[2].toIntOrNull() ?: 0
            val hours = totalMins / 60
            val mins = totalMins % 60
            return when {
                hours > 0 && mins > 0 -> "$prefix by ${hours}h ${mins}m"
                hours > 0 -> "$prefix by ${hours} hr"
                mins > 0 -> "$prefix by ${mins} min"
                else -> "$prefix (0m)"
            }
        }

        if (trimmed.equals("Completed", ignoreCase = true)) return "On Time / Completed"
        if (trimmed.equals("On Time", ignoreCase = true)) return "On Time"

        return trimmed
    }

    private fun calculateDutyDurationForRecord(
        item: AttendanceResponse,
        allRecords: List<AttendanceResponse>
    ): String? {
        val (itemDateStr, _) = com.zynexbd.crmsolution.utils.ReportExporter.formatDateTime(item.timestamp)
        if (itemDateStr.isBlank()) return null

        val dayRecords = allRecords.filter { r ->
            val (rDateStr, _) = com.zynexbd.crmsolution.utils.ReportExporter.formatDateTime(r.timestamp)
            r.userId == item.userId && rDateStr == itemDateStr
        }

        val inRecord = dayRecords.filter { it.type == "In" }.minByOrNull { parseTimestampMillis(it.timestamp) }
        val outRecord = dayRecords.filter { it.type == "Out" }.maxByOrNull { parseTimestampMillis(it.timestamp) }

        val inMillis = inRecord?.timestamp?.let { parseTimestampMillis(it) } ?: 0L
        val outMillis = outRecord?.timestamp?.let { parseTimestampMillis(it) } ?: 0L

        if (inMillis > 0 && outMillis > 0 && outMillis >= inMillis) {
            val diff = outMillis - inMillis
            val hours = diff / (1000 * 60 * 60)
            val mins = (diff / (1000 * 60)) % 60
            return "%d hrs %02d mins (%02d:%02d)".format(hours, mins, hours, mins)
        } else if (inMillis > 0) {
            val todayDateStr = com.zynexbd.crmsolution.utils.ReportExporter.formatDateTime(
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())
            ).first

            if (itemDateStr == todayDateStr) {
                val diff = Math.max(0, System.currentTimeMillis() - inMillis)
                val hours = diff / (1000 * 60 * 60)
                val mins = (diff / (1000 * 60)) % 60
                return "%d hrs %02d mins (Ongoing / চলমান)".format(hours, mins)
            } else {
                return "Duty In (No Out Recorded)"
            }
        } else if (outMillis > 0) {
            return "Duty Out Recorded (No In)"
        }
        return null
    }

    private fun parseTimestampMillis(timestampStr: String?): Long {
        if (timestampStr.isNullOrBlank()) return 0L
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (fmt in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = sdf.parse(timestampStr)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return 0L
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AttendanceResponse>() {
            override fun areItemsTheSame(oldItem: AttendanceResponse, newItem: AttendanceResponse) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: AttendanceResponse, newItem: AttendanceResponse) = oldItem == newItem
        }
    }
}
