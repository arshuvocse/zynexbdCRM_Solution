package com.zynexbd.crmsolution.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.zynexbd.crmsolution.models.LiveTeamActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TeamActivityReportExporter {

    fun exportToExcel(
        context: Context,
        activities: List<LiveTeamActivity>,
        periodSubtitle: String
    ) {
        if (activities.isEmpty()) {
            Toast.makeText(context, "No activities to export", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, "Team_Activity_${timeStamp}.csv")

                val fos = FileOutputStream(file)
                // Write UTF-8 BOM so Excel opens Bangla and symbols correctly
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

                val sb = StringBuilder()
                // Metadata Header
                sb.append("CRM Live Team Activity Report\n")
                sb.append("Period:,\"").append(periodSubtitle.replace("\"", "\"\"")).append("\"\n")
                sb.append("Generated At:,\"").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\"\n")
                sb.append("Total Activities:,").append(activities.size).append("\n\n")

                // Table Header
                sb.append("\"Sl\",\"Date & Time\",\"Officer Name\",\"Role\",\"Action Type\",\"Activity Summary\",\"Details / Remarks\",\"Target Entity ID\"\n")

                for ((idx, item) in activities.withIndex()) {
                    sb.append(idx + 1).append(",")
                    sb.append("\"").append(escapeCsv(item.createdAtUtc)).append("\",")
                    sb.append("\"").append(escapeCsv(item.userName)).append("\",")
                    sb.append("\"").append(escapeCsv(item.userRole)).append("\",")
                    sb.append("\"").append(escapeCsv(item.actionType)).append("\",")
                    sb.append("\"").append(escapeCsv(item.title)).append("\",")
                    sb.append("\"").append(escapeCsv(item.subtitle)).append("\",")
                    sb.append(item.targetEntityId ?: "").append("\n")
                }

                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.close()

                withContext(Dispatchers.Main) {
                    shareFile(context, file, "text/csv", "Export Team Activity (Excel)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Excel export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportToPdf(
        activity: Activity,
        companyName: String,
        periodSubtitle: String,
        activities: List<LiveTeamActivity>
    ) {
        if (activities.isEmpty()) {
            Toast.makeText(activity, "No activities to export", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(activity)
            .setTitle("Generating PDF Report")
            .setMessage("Please wait while the activity report is being created...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfDoc = PdfDocument()
                val pageWidth = 595 // Standard A4 width in points
                val pageHeight = 842 // Standard A4 height in points
                var currentPageNumber = 1

                val titlePaint = Paint().apply {
                    color = Color.parseColor("#1E3A8A") // Dark Navy Blue
                    textSize = 17f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val subtitlePaint = Paint().apply {
                    color = Color.parseColor("#475569") // Slate gray
                    textSize = 10.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }

                val headerBgPaint = Paint().apply {
                    color = Color.parseColor("#F1F5F9") // Light slate
                }

                val tableHeaderPaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val bodyPaint = Paint().apply {
                    color = Color.parseColor("#1E293B")
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }

                val badgePaint = Paint().apply {
                    color = Color.parseColor("#2563EB")
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val linePaint = Paint().apply {
                    color = Color.parseColor("#E2E8F0")
                    strokeWidth = 0.8f
                }

                val altRowPaint = Paint().apply {
                    color = Color.parseColor("#F8FAFC")
                }

                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                var page = pdfDoc.startPage(pageInfo)
                var canvas = page.canvas

                var y = 45f

                fun drawPageHeader() {
                    // Organization Header
                    canvas.drawText(companyName.ifBlank { "CRM SOLUTION" }, 36f, y, titlePaint)
                    y += 18f
                    canvas.drawText("Team Activity Stream & Event Log", 36f, y, titlePaint.apply { textSize = 13f; color = Color.parseColor("#2563EB") })
                    y += 16f
                    canvas.drawText("Report Period: $periodSubtitle  •  Total Records: ${activities.size}", 36f, y, subtitlePaint)
                    y += 14f
                    val generatedTime = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
                    canvas.drawText("Generated on: $generatedTime", 36f, y, subtitlePaint.apply { textSize = 9f })
                    y += 15f

                    // Divider line
                    canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint.apply { strokeWidth = 1.2f; color = Color.parseColor("#94A3B8") })
                    y += 14f

                    // Table Header Row
                    canvas.drawRect(36f, y, pageWidth - 36f, y + 22f, headerBgPaint)
                    canvas.drawText("SL", 42f, y + 15f, tableHeaderPaint)
                    canvas.drawText("DATE / TIME", 65f, y + 15f, tableHeaderPaint)
                    canvas.drawText("OFFICER", 155f, y + 15f, tableHeaderPaint)
                    canvas.drawText("ACTION", 245f, y + 15f, tableHeaderPaint)
                    canvas.drawText("ACTIVITY SUMMARY & DETAILS", 335f, y + 15f, tableHeaderPaint)
                    y += 26f
                }

                drawPageHeader()

                for ((index, item) in activities.withIndex()) {
                    // Check if we need a new page
                    if (y > pageHeight - 65f) {
                        // Draw footer for current page
                        canvas.drawText("Page $currentPageNumber", pageWidth - 80f, pageHeight - 25f, subtitlePaint)
                        pdfDoc.finishPage(page)

                        currentPageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                        page = pdfDoc.startPage(pageInfo)
                        canvas = page.canvas
                        y = 45f
                        drawPageHeader()
                    }

                    // Alternating row background
                    if (index % 2 == 1) {
                        canvas.drawRect(36f, y - 10f, pageWidth - 36f, y + 16f, altRowPaint)
                    }

                    // Row items
                    canvas.drawText("${index + 1}", 42f, y + 4f, bodyPaint)

                    val formattedTime = formatDateTimeForPdf(item.createdAtUtc)
                    canvas.drawText(formattedTime, 65f, y + 4f, bodyPaint)

                    val officerText = truncateText(item.userName, 16)
                    canvas.drawText(officerText, 155f, y + 4f, bodyPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

                    // Action badge text
                    val actionName = when (item.actionType) {
                        "LeadCreated" -> "Lead Created"
                        "FollowUpAdded" -> "Follow-up"
                        "StatusChanged" -> "Status Change"
                        "LeadAssigned" -> "Lead Assigned"
                        "CustomerVisit" -> "Field Visit"
                        "KpiCreated" -> "KPI Setup"
                        "RemarkAdded" -> "Note Added"
                        else -> item.actionType
                    }
                    canvas.drawText(truncateText(actionName, 14), 245f, y + 4f, badgePaint)

                    // Title & subtitle
                    val summaryLine = if (item.subtitle.isNotBlank()) "${item.title} (${item.subtitle})" else item.title
                    canvas.drawText(truncateText(summaryLine, 40), 335f, y + 4f, bodyPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) })

                    // Row underline
                    canvas.drawLine(36f, y + 16f, pageWidth - 36f, y + 16f, linePaint.apply { strokeWidth = 0.5f; color = Color.parseColor("#E2E8F0") })

                    y += 24f
                }

                // Footer on last page
                canvas.drawText("Page $currentPageNumber", pageWidth - 80f, pageHeight - 25f, subtitlePaint)
                canvas.drawText("DeshiCRM Enterprise Platform  •  Confidential Team Report", 36f, pageHeight - 25f, subtitlePaint)
                pdfDoc.finishPage(page)

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val exportDir = File(activity.cacheDir, "exports").apply { mkdirs() }
                val pdfFile = File(exportDir, "Team_Activity_Report_${timeStamp}.pdf")

                val fos = FileOutputStream(pdfFile)
                pdfDoc.writeTo(fos)
                fos.flush()
                fos.close()
                pdfDoc.close()

                withContext(Dispatchers.Main) {
                    if (progressDialog.isShowing) progressDialog.dismiss()
                    shareFile(activity, pdfFile, "application/pdf", "Export Team Activity PDF")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (progressDialog.isShowing) progressDialog.dismiss()
                    Toast.makeText(activity, "PDF Generation failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) text.take(maxLength - 2) + ".." else text
    }

    private fun formatDateTimeForPdf(rawUtc: String): String {
        if (rawUtc.isBlank()) return "-"
        return try {
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val d = iso.parse(rawUtc)
            if (d != null) {
                SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).format(d)
            } else rawUtc.take(16)
        } catch (_: Exception) {
            rawUtc.take(16)
        }
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
