package com.zynexbd.crmsolution.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.zynexbd.crmsolution.databinding.DialogExportLoadingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Downloads server-generated CRM CSV exports (leads/follow-ups/productivity/KPI reports) and
 * hands them off via the app's existing FileProvider share/open flow. Unlike [ReportExporter],
 * the CSV content always comes from the backend export endpoints - office/role authorization is
 * enforced server-side, so this never regenerates rows from client-side data.
 */
object CrmCsvExporter {

    sealed class ExportResult {
        data class Success(val file: File) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    private fun showLoadingDialog(activity: Activity, message: String): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        return try {
            val binding = DialogExportLoadingBinding.inflate(LayoutInflater.from(activity))
            binding.textExportLoadingTitle.text = "Exporting CSV"
            binding.textExportLoadingMessage.text = message
            val dialog = AlertDialog.Builder(activity)
                .setView(binding.root)
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
            dialog
        } catch (_: Exception) {
            null
        }
    }

    private fun dismissLoadingDialog(activity: Activity, dialog: AlertDialog?) {
        activity.runOnUiThread {
            try {
                if (dialog?.isShowing == true) dialog.dismiss()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Runs [call] (a suspend Retrofit call returning a streamed CSV [ResponseBody]), saves it to
     * cacheDir/reports/CRM_<entityLabel>_<timestamp>.csv, and shows a loading dialog while the
     * download is in flight. Safe to call from Dispatchers.Main; network + disk I/O runs on
     * Dispatchers.IO internally.
     */
    suspend fun downloadCsv(
        activity: Activity,
        entityLabel: String, // e.g. "Leads", "Followups", "Productivity", "KPI"
        call: suspend () -> Response<ResponseBody>
    ): ExportResult {
        val loadingDialog = showLoadingDialog(activity, "Preparing $entityLabel CSV…")
        return try {
            val response = withContext(Dispatchers.IO) { call() }
            if (!response.isSuccessful) {
                val err = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                val message = if (response.code() == 403)
                    "You are not authorized to export this office's data."
                else
                    err ?: "Export failed (HTTP ${response.code()})."
                return ExportResult.Error(message)
            }

            val body = response.body() ?: return ExportResult.Error("Empty response from server.")

            val file = withContext(Dispatchers.IO) {
                val dir = File(activity.cacheDir, "reports").apply { if (!exists()) mkdirs() }
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
                val outFile = File(dir, "CRM_${entityLabel}_$timestamp.csv")
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                outFile
            }
            ExportResult.Success(file)
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Network error while exporting.")
        } finally {
            dismissLoadingDialog(activity, loadingDialog)
        }
    }

    fun shareCsv(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share CSV"))
    }

    fun openCsv(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "No app found to open CSV files. Try Share instead.", Toast.LENGTH_LONG).show()
        }
    }

    /** Shows the standard "downloaded successfully" dialog with Open / Share / Done actions. */
    fun showSuccessDialog(activity: Activity, file: File) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("CSV downloaded successfully")
            .setMessage(file.name)
            .setPositiveButton("Open") { _, _ -> openCsv(activity, file) }
            .setNeutralButton("Share") { _, _ -> shareCsv(activity, file) }
            .setNegativeButton("Done", null)
            .show()
    }

    fun showErrorDialog(activity: Activity, message: String, onRetry: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("Export failed")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ -> onRetry() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
