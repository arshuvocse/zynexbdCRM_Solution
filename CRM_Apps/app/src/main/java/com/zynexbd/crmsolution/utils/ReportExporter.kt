package com.zynexbd.crmsolution.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.zynexbd.crmsolution.databinding.DialogExportLoadingBinding
import com.zynexbd.crmsolution.models.AttendanceResponse
import com.zynexbd.crmsolution.models.EmployeeMonthlyAttendanceSummary
import com.zynexbd.crmsolution.models.MonthlyPerformanceReportResponse
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReportExporter {

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        topY: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int = 2
    ) {
        if (maxWidth <= 0f || text.isEmpty()) return
        val textPaint = TextPaint(paint)
        try {
            val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxWidth.toInt())
                    .setMaxLines(maxLines)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, textPaint, maxWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
            }
            canvas.save()
            canvas.translate(x, topY)
            staticLayout.draw(canvas)
            canvas.restore()
        } catch (e: Exception) {
            val fallback = if (text.length > 12) text.take(10) + ".." else text
            canvas.drawText(fallback, x, topY + 12f, paint)
        }
    }

    private fun showLoadingDialog(context: Context, title: String, message: String): AlertDialog? {
        val activity = context as? Activity ?: return null
        if (activity.isFinishing || activity.isDestroyed) return null

        try {
            val dialogBinding = DialogExportLoadingBinding.inflate(LayoutInflater.from(activity))
            dialogBinding.textExportLoadingTitle.text = title
            dialogBinding.textExportLoadingMessage.text = message

            val dialog = AlertDialog.Builder(activity)
                .setView(dialogBinding.root)
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
            return dialog
        } catch (_: Exception) {
            return null
        }
    }

    private fun dismissLoadingDialog(context: Context, dialog: AlertDialog?) {
        val activity = context as? Activity ?: return
        activity.runOnUiThread {
            try {
                if (dialog != null && dialog.isShowing) {
                    dialog.dismiss()
                }
            } catch (_: Exception) {}
        }
    }

    fun exportToExcel(
        context: Context,
        records: List<AttendanceResponse>,
        title: String = "Attendance_Report",
        summaries: List<EmployeeMonthlyAttendanceSummary> = emptyList()
    ) {
        if (records.isEmpty() && summaries.isEmpty()) {
            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = showLoadingDialog(
            context,
            "Exporting to Excel...",
            "Compiling attendance records and monthly summaries..."
        )

        Thread {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "${title}_$timeStamp.csv"
                val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
                val file = File(reportsDir, fileName)

                FileOutputStream(file).use { fos ->
                    // Write UTF-8 BOM so Excel opens with proper character encoding
                    fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    val sb = StringBuilder()

                    // Section 1: Monthly Summary if present
                    if (summaries.isNotEmpty()) {
                        sb.append("EMPLOYEE MONTHLY ATTENDANCE SUMMARY\n")
                        sb.append("SL,Employee Name,Role,Shift,Working Days,Present,On Time,Late,Early Out,Leave,Absent,Attendance Rate,Total Presence (HH:MM)\n")
                        summaries.forEachIndexed { index, s ->
                            val sl = index + 1
                            val name = escapeCsv(s.fullName.ifBlank { s.username })
                            val role = escapeCsv(s.role)
                            val shift = escapeCsv(s.shiftName)
                            val rate = "%.1f%%".format(s.attendancePercentage)
                            val presence = if (s.totalPresenceTime.isNotBlank()) s.totalPresenceTime else "00:00"
                            sb.append("$sl,$name,$role,$shift,${s.totalWorkingDays},${s.presentDays},${s.onTimeDays},${s.lateDays},${s.earlyOutDays},${s.approvedLeaveDays},${s.absentDays},\"$rate\",$presence\n")
                        }
                        sb.append("\n\nDETAILED ATTENDANCE DUTY LOGS\n")
                    }

                    // Section 2: Detailed Logs (Date placed first right after SL)
                    sb.append("SL,Date,Employee Name,Shift,Duty Type,Time,Duty Duration (HH:MM),Status,Office Geofence Status,Latitude,Longitude\n")
                    records.forEachIndexed { index, item ->
                        val sl = index + 1
                        val name = escapeCsv(item.userName ?: "Officer")
                        val shift = escapeCsv(item.shiftName ?: "General Shift")
                        val type = if (item.type == "In") "Duty In" else "Duty Out"
                        val status = escapeCsv(formatLateStatus(item.status ?: "On Time"))
                        val (dateStr, timeStr) = formatDateTime(item.timestamp)
                        val dutyDuration = calculateDayDutyDuration(item, records)
                        val geofence = if (item.isWithinGeofence) "Within Office" else "Outside Office"
                        val lat = item.latitude?.toString() ?: ""
                        val lng = item.longitude?.toString() ?: ""
                        sb.append("$sl,\"$dateStr\",$name,$shift,$type,\"$timeStr\",\"$dutyDuration\",$status,$geofence,$lat,$lng\n")
                    }
                    sb.append("\nGenerated On: $timeStamp | Powered by ZyNex Soft Tech\n")
                    fos.write(sb.toString().toByteArray(Charsets.UTF_8))
                }

                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    shareFile(context, file, "application/vnd.ms-excel", "Export Attendance Excel")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    fun exportToPdf(
        context: Context,
        records: List<AttendanceResponse>,
        title: String = "Attendance Report",
        filterSubtitle: String = "All Records",
        summaries: List<EmployeeMonthlyAttendanceSummary> = emptyList()
    ) {
        if (records.isEmpty() && summaries.isEmpty()) {
            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = showLoadingDialog(
            context,
            "Generating PDF Report...",
            "Downloading employee photos and creating document..."
        )

        // Run PDF generation in background thread to allow downloading selfie images for PDF
        Thread {
            try {
                val pdfDoc = PdfDocument()
                val pageWidth = 595 // A4 standard width
                val pageHeight = 842 // A4 standard height
                val rowHeight = 36f
                val recordsPerPage = 16

                val logPages = if (records.isNotEmpty()) ((records.size - 1) / recordsPerPage) + 1 else 0
                val summaryPages = if (summaries.isNotEmpty()) ((summaries.size - 1) / 22) + 1 else 0
                val totalPages = maxOf(1, summaryPages + logPages)
                val currentDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

                // Pre-fetch selfie bitmaps
                val bitmapCache = mutableMapOf<Long, android.graphics.Bitmap?>()
                for (rec in records) {
                    if (!rec.selfieUrl.isNullOrBlank()) {
                        try {
                            val rawUrl = rec.selfieUrl.trim()
                            val fullUrl = if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
                                rawUrl
                            } else {
                                val baseUrl = com.zynexbd.crmsolution.BuildConfig.API_BASE_URL.trimEnd('/')
                                val path = rawUrl.replace("\\", "/").trimStart('/')
                                "$baseUrl/$path"
                            }

                            var bmp: android.graphics.Bitmap? = null
                            try {
                                val conn = (java.net.URL(fullUrl).openConnection() as java.net.HttpURLConnection).apply {
                                    connectTimeout = 6000
                                    readTimeout = 6000
                                    doInput = true
                                    connect()
                                }
                                if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                    val stream = conn.inputStream
                                    bmp = android.graphics.BitmapFactory.decodeStream(stream)
                                    stream.close()
                                }
                                conn.disconnect()
                            } catch (_: Exception) {}

                            if (bmp == null) {
                                try {
                                    bmp = com.bumptech.glide.Glide.with(context)
                                        .asBitmap()
                                        .load(fullUrl)
                                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                        .submit(80, 80)
                                        .get()
                                } catch (_: Exception) {}
                            }

                            bitmapCache[rec.id] = bmp
                        } catch (e: Exception) {
                            bitmapCache[rec.id] = null
                        }
                    }
                }

                val titlePaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    textSize = 16f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val subtitlePaint = Paint().apply {
                    color = Color.parseColor("#64748B")
                    textSize = 9f
                    isAntiAlias = true
                }

                val headerBgPaint = Paint().apply {
                    color = Color.parseColor("#4F46E5")
                    style = Paint.Style.FILL
                }

                val headerTextPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 8f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val rowBgEvenPaint = Paint().apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                }

                val rowBgOddPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }

                val rowTextPaint = Paint().apply {
                    color = Color.parseColor("#1E293B")
                    textSize = 8f
                    isAntiAlias = true
                }

                val borderPaint = Paint().apply {
                    color = Color.parseColor("#E2E8F0")
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f
                }

                val photoPlaceholderPaint = Paint().apply {
                    color = Color.parseColor("#F1F5F9")
                    style = Paint.Style.FILL
                }

                val photoTextPaint = Paint().apply {
                    color = Color.parseColor("#94A3B8")
                    textSize = 7.5f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                var globalPageIndex = 0

                // 1. Render Summary Pages if summaries exist
                if (summaries.isNotEmpty()) {
                    val summariesPerPage = 22
                    val sumTotalPages = ((summaries.size - 1) / summariesPerPage) + 1

                    for (p in 0 until sumTotalPages) {
                        globalPageIndex++
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, globalPageIndex).create()
                        val page = pdfDoc.startPage(pageInfo)
                        val canvas: Canvas = page.canvas

                        // Header
                        canvas.drawText("Smart Workforce - Monthly Attendance Summary", 36f, 44f, titlePaint)
                        canvas.drawText("Period: $filterSubtitle | Generated: $currentDate | Total Employees: ${summaries.size}", 36f, 60f, subtitlePaint)

                        val startX = 36f
                        val endX = (pageWidth - 36).toFloat()
                        var currentY = 78f
                        val sumRowHeight = 24f

                        // Summary Table Header
                        canvas.drawRect(startX, currentY, endX, currentY + sumRowHeight, headerBgPaint)
                        canvas.drawText("SL", startX + 4f, currentY + 16f, headerTextPaint)
                        canvas.drawText("EMPLOYEE", startX + 22f, currentY + 16f, headerTextPaint)
                        canvas.drawText("ROLE", startX + 118f, currentY + 16f, headerTextPaint)
                        canvas.drawText("WORK", startX + 180f, currentY + 16f, headerTextPaint)
                        canvas.drawText("PRESENT", startX + 215f, currentY + 16f, headerTextPaint)
                        canvas.drawText("ON TIME", startX + 260f, currentY + 16f, headerTextPaint)
                        canvas.drawText("LATE", startX + 305f, currentY + 16f, headerTextPaint)
                        canvas.drawText("EARLY", startX + 340f, currentY + 16f, headerTextPaint)
                        canvas.drawText("LEAVE", startX + 375f, currentY + 16f, headerTextPaint)
                        canvas.drawText("ABSENT", startX + 410f, currentY + 16f, headerTextPaint)
                        canvas.drawText("RATE", startX + 450f, currentY + 16f, headerTextPaint)
                        canvas.drawText("PRESENCE", startX + 482f, currentY + 16f, headerTextPaint)

                        currentY += sumRowHeight

                        val startIdx = p * summariesPerPage
                        val endIdx = minOf(startIdx + summariesPerPage, summaries.size)

                        for (i in startIdx until endIdx) {
                            val item = summaries[i]
                            val isEven = (i % 2 == 0)
                            canvas.drawRect(startX, currentY, endX, currentY + sumRowHeight, if (isEven) rowBgEvenPaint else rowBgOddPaint)
                            canvas.drawRect(startX, currentY, endX, currentY + sumRowHeight, borderPaint)

                            val centerY = currentY + 16f
                            canvas.drawText("${i + 1}", startX + 4f, centerY, rowTextPaint)
                            val name = item.fullName.ifBlank { item.username }
                            drawWrappedText(canvas, name, startX + 22f, currentY + 3f, 92f, rowTextPaint, maxLines = 2)
                            drawWrappedText(canvas, item.role, startX + 118f, currentY + 3f, 58f, rowTextPaint, maxLines = 2)
                            canvas.drawText("${item.totalWorkingDays}", startX + 180f, centerY, rowTextPaint)
                            canvas.drawText("${item.presentDays}", startX + 215f, centerY, rowTextPaint)
                            canvas.drawText("${item.onTimeDays}", startX + 260f, centerY, rowTextPaint)
                            canvas.drawText("${item.lateDays}", startX + 305f, centerY, rowTextPaint)
                            canvas.drawText("${item.earlyOutDays}", startX + 340f, centerY, rowTextPaint)
                            canvas.drawText("${item.approvedLeaveDays}", startX + 375f, centerY, rowTextPaint)
                            canvas.drawText("${item.absentDays}", startX + 410f, centerY, rowTextPaint)
                            canvas.drawText("%.1f%%".format(item.attendancePercentage), startX + 450f, centerY, rowTextPaint)
                            val presence = if (item.totalPresenceTime.isNotBlank()) item.totalPresenceTime else "00:00"
                            canvas.drawText(presence, startX + 482f, centerY, rowTextPaint)

                            currentY += sumRowHeight
                        }

                        // Footer
                        val footerPaint = Paint().apply {
                            color = Color.parseColor("#94A3B8")
                            textSize = 9f
                            isAntiAlias = true
                        }
                        canvas.drawText("Powered by ZyNex Soft Tech • Page $globalPageIndex of $totalPages", startX, (pageHeight - 24).toFloat(), footerPaint)
                        pdfDoc.finishPage(page)
                    }
                }

                // 2. Render Detailed Log Pages
                for (pageIndex in 0 until logPages) {
                    globalPageIndex++
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, globalPageIndex).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas: Canvas = page.canvas

                    // Draw Header Brand
                    canvas.drawText("Smart Workforce - Detailed Attendance Logs", 36f, 44f, titlePaint)
                    canvas.drawText("Generated: $currentDate | Filter: $filterSubtitle | Total: ${records.size}", 36f, 62f, subtitlePaint)

                    // Table Layout
                    val startX = 36f
                    val endX = (pageWidth - 36).toFloat()
                    var currentY = 82f
                    val tableHeaderHeight = 26f

                    // Header Row with DATE, EMPLOYEE, PHOTO, TYPE, TIME, DUTY HRS, STATUS, SHIFT, GEOFENCE
                    canvas.drawRect(startX, currentY, endX, currentY + tableHeaderHeight, headerBgPaint)
                    canvas.drawText("SL", startX + 4f, currentY + 17f, headerTextPaint)
                    canvas.drawText("DATE", startX + 18f, currentY + 17f, headerTextPaint)
                    canvas.drawText("EMPLOYEE", startX + 82f, currentY + 17f, headerTextPaint)
                    canvas.drawText("PHOTO", startX + 162f, currentY + 17f, headerTextPaint)
                    canvas.drawText("TYPE", startX + 195f, currentY + 17f, headerTextPaint)
                    canvas.drawText("TIME", startX + 245f, currentY + 17f, headerTextPaint)
                    canvas.drawText("DUTY HRS", startX + 300f, currentY + 17f, headerTextPaint)
                    canvas.drawText("STATUS", startX + 360f, currentY + 17f, headerTextPaint)
                    canvas.drawText("SHIFT", startX + 420f, currentY + 17f, headerTextPaint)
                    canvas.drawText("GEOFENCE", startX + 478f, currentY + 17f, headerTextPaint)

                    currentY += tableHeaderHeight

                        val startIndex = pageIndex * recordsPerPage
                        val endIndex = minOf(startIndex + recordsPerPage, records.size)

                        for (i in startIndex until endIndex) {
                            val item = records[i]
                            val isEven = (i % 2 == 0)
                            canvas.drawRect(startX, currentY, endX, currentY + rowHeight, if (isEven) rowBgEvenPaint else rowBgOddPaint)
                            canvas.drawRect(startX, currentY, endX, currentY + rowHeight, borderPaint)

                            val centerY = currentY + (rowHeight / 2f)

                            // SL
                            canvas.drawText("${i + 1}", startX + 4f, centerY + 3.5f, rowTextPaint)

                            // Date
                            val (dateStr, timeStr) = formatDateTime(item.timestamp)
                            val datePaint = Paint().apply {
                                color = Color.parseColor("#0F172A")
                                textSize = 7.5f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                isAntiAlias = true
                            }
                            canvas.drawText(dateStr, startX + 18f, centerY + 3.5f, datePaint)

                            // Name (Wrapped to 2 lines)
                            val rawName = item.userName ?: "Officer"
                            drawWrappedText(canvas, rawName, startX + 82f, currentY + 4f, 76f, rowTextPaint, maxLines = 2)

                            // Photo Thumbnail
                            val photoBmp = bitmapCache[item.id]
                            val photoSize = 24f
                            val photoX = startX + 162f
                            val photoY = currentY + (rowHeight - photoSize) / 2f
                            if (photoBmp != null) {
                                val photoRect = RectF(photoX, photoY, photoX + photoSize, photoY + photoSize)
                                canvas.drawBitmap(photoBmp, null, photoRect, null)
                                canvas.drawRect(photoRect, borderPaint)
                            } else {
                                val photoRect = RectF(photoX, photoY, photoX + photoSize, photoY + photoSize)
                                canvas.drawRoundRect(photoRect, 3f, 3f, photoPlaceholderPaint)
                                canvas.drawText("N/A", photoX + photoSize / 2f, photoY + 15f, photoTextPaint)
                            }

                            // Type
                            val isPunchIn = (item.type == "In")
                            val typeText = if (isPunchIn) "Duty In" else "Duty Out"
                            val typePaint = Paint().apply {
                                color = if (isPunchIn) Color.parseColor("#059669") else Color.parseColor("#E11D48")
                                textSize = 7.5f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                isAntiAlias = true
                            }
                            canvas.drawText(typeText, startX + 195f, centerY + 3.5f, typePaint)

                            // Time
                            canvas.drawText(timeStr, startX + 245f, centerY + 3.5f, rowTextPaint)

                            // Duty Hours
                            val dutyHrs = calculateDayDutyDuration(item, records)
                            val dutyPaint = Paint().apply {
                                color = if (dutyHrs != "-") Color.parseColor("#059669") else Color.parseColor("#94A3B8")
                                textSize = 8f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                isAntiAlias = true
                            }
                            canvas.drawText(dutyHrs, startX + 300f, centerY + 3.5f, dutyPaint)

                            // Status Badge (Wrapped to 2 lines)
                            val statusText = formatLateStatus(item.status ?: "On Time")
                            val statusPaint = Paint().apply {
                                color = when {
                                    statusText.startsWith("Late", ignoreCase = true) -> Color.parseColor("#D97706")
                                    statusText.startsWith("Early", ignoreCase = true) -> Color.parseColor("#EA580C")
                                    else -> Color.parseColor("#059669")
                                }
                                textSize = 7f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                isAntiAlias = true
                            }
                            drawWrappedText(canvas, statusText, startX + 360f, currentY + 4f, 56f, statusPaint, maxLines = 2)

                            // Shift (Wrapped to 2 lines)
                            val rawShift = item.shiftName ?: "General"
                            drawWrappedText(canvas, rawShift, startX + 420f, currentY + 4f, 55f, rowTextPaint, maxLines = 2)

                            // Geofence (Wrapped to 2 lines)
                            val geoText = if (item.isWithinGeofence) "Within Office" else "Outside Office"
                            val geoPaint = Paint().apply {
                                color = if (item.isWithinGeofence) Color.parseColor("#059669") else Color.parseColor("#D97706")
                                textSize = 7.5f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                isAntiAlias = true
                            }
                            drawWrappedText(canvas, geoText, startX + 478f, currentY + 4f, 50f, geoPaint, maxLines = 2)

                            currentY += rowHeight
                        }

                    // Page Footer
                    val footerPaint = Paint().apply {
                        color = Color.parseColor("#94A3B8")
                        textSize = 9f
                        isAntiAlias = true
                    }
                    canvas.drawText("Powered by ZyNex Soft Tech • Page $globalPageIndex of $totalPages", startX, (pageHeight - 24).toFloat(), footerPaint)

                    pdfDoc.finishPage(page)
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Attendance_Report_$timeStamp.pdf"
                val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
                val file = File(reportsDir, fileName)

                FileOutputStream(file).use { fos ->
                    pdfDoc.writeTo(fos)
                }
                pdfDoc.close()

                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    shareFile(context, file, "application/pdf", "Export Attendance PDF")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    Toast.makeText(context, "PDF generation failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    fun exportPerformanceReportToExcel(
        context: Context,
        report: MonthlyPerformanceReportResponse
    ) {
        if (report.employees.isEmpty()) {
            Toast.makeText(context, "No report data to export", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = showLoadingDialog(
            context,
            "Exporting to Excel...",
            "Preparing performance spreadsheet..."
        )

        Thread {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Performance_Report_${report.monthName.replace(" ", "_")}_$timeStamp.csv"
                val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
                val file = File(reportsDir, fileName)

                FileOutputStream(file).use { fos ->
                    fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    val sb = StringBuilder()
                    sb.append("SMART WORKFORCE - OFFICER PERFORMANCE REPORT\n")
                    sb.append("Period:,${escapeCsv(report.monthName)}\n")
                    sb.append("Total Visits:,${report.totalVisits},Total Follow-ups:,${report.totalFollowUps},Completed Follow-ups:,${report.completedFollowUps},New Customers:,${report.totalCustomersAdded}\n\n")

                    sb.append("SL,Officer Name,Username,Role,Status,Total Visits,Follow-ups Done,Pending Follow-ups,Customers Added\n")
                    report.employees.forEachIndexed { index, emp ->
                        val sl = index + 1
                        val name = escapeCsv(emp.fullName.ifBlank { emp.username })
                        val uname = escapeCsv("@${emp.username}")
                        val role = escapeCsv(emp.role)
                        val status = if (emp.isActive) "Active" else "Disabled"
                        sb.append("$sl,$name,$uname,$role,$status,${emp.totalVisits},${emp.completedFollowUps},${emp.pendingFollowUps},${emp.totalCustomersAdded}\n")
                    }

                    sb.append("\nDETAILED ACTIVITY BREAKDOWN\n")
                    sb.append("Officer,Type,Customer Name,Date,Status,Remarks\n")
                    for (emp in report.employees) {
                        val officerName = escapeCsv(emp.fullName.ifBlank { emp.username })
                        for (c in emp.customers) {
                            val cDate = escapeCsv(c.createdDate?.take(10) ?: "")
                            val cName = escapeCsv(c.name)
                            val cRem = escapeCsv("Phone: ${c.mobile} | Address: ${c.address}")
                            sb.append("$officerName,CUSTOMER,$cName,$cDate,New Customer,$cRem\n")
                        }
                        for (v in emp.visits) {
                            val vDate = escapeCsv(v.visitDate.take(10))
                            val vName = escapeCsv(v.customerName)
                            val vRem = escapeCsv(v.remarks ?: "")
                            sb.append("$officerName,VISIT,$vName,$vDate,${v.visitStatus},$vRem\n")
                        }
                        for (f in emp.followUps) {
                            val fDate = escapeCsv(f.followUpDate?.take(10) ?: "")
                            val fName = escapeCsv(f.customerName)
                            val fStatus = if (f.isCompleted) "Completed" else "Pending"
                            val fRem = escapeCsv(f.remarks ?: "")
                            sb.append("$officerName,FOLLOW-UP,$fName,$fDate,$fStatus,$fRem\n")
                        }
                    }
                    sb.append("\nGenerated On: $timeStamp | Powered by ZyNex Soft Tech\n")
                    fos.write(sb.toString().toByteArray(Charsets.UTF_8))
                }

                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    shareFile(context, file, "application/vnd.ms-excel", "Export Performance Excel Report")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    Toast.makeText(context, "Excel export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    fun exportPerformanceReportToPdf(
        context: Context,
        report: MonthlyPerformanceReportResponse
    ) {
        if (report.employees.isEmpty()) {
            Toast.makeText(context, "No report data to export", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = showLoadingDialog(
            context,
            "Generating PDF Report...",
            "Compiling officer performance data..."
        )

        Thread {
            try {
                val pdfDoc = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val employeesPerPage = 14

                val totalPages = ((report.employees.size - 1) / employeesPerPage) + 1
                val currentDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

                val titlePaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    textSize = 17f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val subtitlePaint = Paint().apply {
                    color = Color.parseColor("#64748B")
                    textSize = 9.5f
                    isAntiAlias = true
                }

                val headerBgPaint = Paint().apply {
                    color = Color.parseColor("#2563EB")
                    style = Paint.Style.FILL
                }

                val headerTextPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val rowBgEvenPaint = Paint().apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                }

                val rowBgOddPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }

                val rowTextPaint = Paint().apply {
                    color = Color.parseColor("#1E293B")
                    textSize = 9f
                    isAntiAlias = true
                }

                val numTextPaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val borderPaint = Paint().apply {
                    color = Color.parseColor("#E2E8F0")
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f
                }

                for (pageIndex in 0 until totalPages) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas: Canvas = page.canvas

                    // Header
                    canvas.drawText("Smart Workforce - Officer Performance Report", 36f, 44f, titlePaint)
                    canvas.drawText("Period: ${report.monthName} | Generated: $currentDate | Total Officers: ${report.employees.size}", 36f, 60f, subtitlePaint)
                    canvas.drawText("Totals -> Visits: ${report.totalVisits} | Follow-ups: ${report.totalFollowUps} (Done: ${report.completedFollowUps}) | Customers: ${report.totalCustomersAdded}", 36f, 74f, subtitlePaint)

                    val startX = 36f
                    val endX = (pageWidth - 36).toFloat()
                    var currentY = 92f
                    val rowHeight = 26f

                    // Table Header
                    canvas.drawRect(startX, currentY, endX, currentY + rowHeight, headerBgPaint)
                    canvas.drawText("SL", startX + 6f, currentY + 17f, headerTextPaint)
                    canvas.drawText("OFFICER NAME", startX + 28f, currentY + 17f, headerTextPaint)
                    canvas.drawText("ROLE", startX + 170f, currentY + 17f, headerTextPaint)
                    canvas.drawText("VISITS", startX + 245f, currentY + 17f, headerTextPaint)
                    canvas.drawText("F-UP DONE", startX + 310f, currentY + 17f, headerTextPaint)
                    canvas.drawText("PENDING", startX + 385f, currentY + 17f, headerTextPaint)
                    canvas.drawText("CUSTOMERS", startX + 455f, currentY + 17f, headerTextPaint)

                    currentY += rowHeight

                    val startIndex = pageIndex * employeesPerPage
                    val endIndex = minOf(startIndex + employeesPerPage, report.employees.size)

                    for (i in startIndex until endIndex) {
                        val emp = report.employees[i]
                        val isEven = (i % 2 == 0)
                        canvas.drawRect(startX, currentY, endX, currentY + rowHeight, if (isEven) rowBgEvenPaint else rowBgOddPaint)
                        canvas.drawRect(startX, currentY, endX, currentY + rowHeight, borderPaint)

                        canvas.drawText("${i + 1}", startX + 6f, currentY + 17f, rowTextPaint)

                        val rawName = emp.fullName.ifBlank { emp.username }
                        val displayName = if (rawName.length > 20) rawName.substring(0, 18) + ".." else rawName
                        canvas.drawText(displayName, startX + 28f, currentY + 17f, numTextPaint)

                        canvas.drawText(emp.role, startX + 170f, currentY + 17f, rowTextPaint)
                        canvas.drawText("${emp.totalVisits}", startX + 255f, currentY + 17f, numTextPaint)
                        canvas.drawText("${emp.completedFollowUps}", startX + 325f, currentY + 17f, numTextPaint)
                        canvas.drawText("${emp.pendingFollowUps}", startX + 398f, currentY + 17f, numTextPaint)
                        canvas.drawText("${emp.totalCustomersAdded}", startX + 475f, currentY + 17f, numTextPaint)

                        currentY += rowHeight
                    }

                    // Footer
                    val footerPaint = Paint().apply {
                        color = Color.parseColor("#94A3B8")
                        textSize = 9f
                        isAntiAlias = true
                    }
                    canvas.drawText("Powered by ZyNex Soft Tech • Page ${pageIndex + 1} of $totalPages", startX, (pageHeight - 24).toFloat(), footerPaint)

                    pdfDoc.finishPage(page)
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Performance_Report_${report.monthName.replace(" ", "_")}_$timeStamp.pdf"
                val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
                val file = File(reportsDir, fileName)

                FileOutputStream(file).use { fos ->
                    pdfDoc.writeTo(fos)
                }
                pdfDoc.close()

                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    shareFile(context, file, "application/pdf", "Export Performance PDF Report")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? Activity)?.runOnUiThread {
                    dismissLoadingDialog(context, loadingDialog)
                    Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun formatDateTime(raw: String?): Pair<String, String> {
        if (raw.isNullOrBlank()) return Pair("-", "-")

        val localTz = TimeZone.getDefault()
        val utcTz = TimeZone.getTimeZone("UTC")

        // 1. Check ISO 8601 UTC patterns
        val utcPatterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )

        for (pattern in utcPatterns) {
            try {
                val sdfUtc = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = utcTz
                }
                val date = sdfUtc.parse(raw)
                if (date != null) {
                    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US).apply { timeZone = localTz }
                    val timeFmt = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = localTz }
                    return Pair(dateFmt.format(date), timeFmt.format(date))
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        // 2. Secondary fallback for local pre-formatted strings
        val otherPatterns = arrayOf(
            "yyyy-MM-dd",
            "dd MMM yyyy, hh:mm a",
            "MMM d, yyyy, hh:mm a",
            "yyyy/MM/dd HH:mm:ss"
        )
        for (pattern in otherPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date = sdf.parse(raw)
                if (date != null) {
                    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
                    val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
                    return Pair(dateFmt.format(date), timeFmt.format(date))
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        val clean = raw.replace("T", " ").replace("Z", "")
        val parts = clean.split(" ")
        if (parts.size >= 2) {
            return Pair(parts[0], parts.drop(1).joinToString(" "))
        }
        return Pair(raw, "-")
    }

    fun formatLateStatus(status: String?): String {
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

    fun calculateDayDutyDuration(item: AttendanceResponse, allRecords: List<AttendanceResponse>): String {
        val (itemDateStr, _) = formatDateTime(item.timestamp)
        if (itemDateStr.isBlank()) return "-"

        val dayRecords = allRecords.filter { r ->
            val (rDateStr, _) = formatDateTime(r.timestamp)
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
            return "%02d:%02d".format(hours, mins)
        } else if (inMillis > 0) {
            val todayDateStr = formatDateTime(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
            ).first

            if (itemDateStr == todayDateStr) {
                val diff = Math.max(0, System.currentTimeMillis() - inMillis)
                val hours = diff / (1000 * 60 * 60)
                val mins = (diff / (1000 * 60)) % 60
                return "%02d:%02d (Ongoing)".format(hours, mins)
            }
        }
        return "-"
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
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(timestampStr)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return 0L
    }
}
