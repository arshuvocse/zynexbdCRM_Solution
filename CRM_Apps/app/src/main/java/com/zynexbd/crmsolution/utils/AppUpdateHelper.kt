package com.zynexbd.crmsolution.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zynexbd.crmsolution.BuildConfig
import com.zynexbd.crmsolution.models.AppVersionCheckResponse
import com.zynexbd.crmsolution.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppUpdateHelper {

    private const val TAG = "AppUpdateHelper"

    /**
     * Checks if a new app version is available and shows an update dialog if needed.
     * @param activity The host activity context.
     * @param onCheckCompleted Optional callback when check is completed (hasUpdate, isForceUpdate)
     */
    fun checkForUpdate(
        activity: Activity,
        scope: CoroutineScope,
        onCheckCompleted: ((hasUpdate: Boolean) -> Unit)? = null
    ) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME

        scope.launch {
            try {
                val companyId = SessionManager(activity).getCompanyId()
                val api = RetrofitClient.getApiService(activity)
                val response = withContext(Dispatchers.IO) {
                    api.checkAppVersion(
                        versionCode = currentVersionCode,
                        platform = "Android",
                        companyId = companyId
                    )
                }

                if (response.isSuccessful && response.body() != null) {
                    val updateInfo = response.body()!!
                    if (updateInfo.hasUpdate) {
                        showUpdateDialog(activity, updateInfo)
                        onCheckCompleted?.invoke(true)
                    } else {
                        Log.d(TAG, "App is up to date (v$currentVersionName / code $currentVersionCode)")
                        onCheckCompleted?.invoke(false)
                    }
                } else {
                    Log.w(TAG, "Version check failed: ${response.code()}")
                    onCheckCompleted?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking app version", e)
                onCheckCompleted?.invoke(false)
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, updateInfo: AppVersionCheckResponse) {
        if (activity.isFinishing || activity.isDestroyed) return

        val isEn = LanguageManager.getLanguage(activity) == LanguageManager.LANG_EN

        val title = if (updateInfo.title.isNotBlank()) {
            updateInfo.title
        } else {
            if (isEn) "New Update Available (v${updateInfo.latestVersionName})" else "নতুন ভার্সন আপডেট এসেছে (v${updateInfo.latestVersionName})"
        }

        val messageBuilder = StringBuilder()
        if (isEn) {
            messageBuilder.append("A new version of the app (v${updateInfo.latestVersionName}) is available.\n\n")
        } else {
            messageBuilder.append("অ্যাপটির নতুন ভার্সন (v${updateInfo.latestVersionName}) পাওয়া গেছে।\n\n")
        }

        if (updateInfo.releaseNotes.isNotBlank()) {
            messageBuilder.append(if (isEn) "Release Notes:\n" else "নতুন ফিচারসমূহ:\n")
            messageBuilder.append(updateInfo.releaseNotes)
            messageBuilder.append("\n\n")
        }

        if (updateInfo.isForceUpdate) {
            messageBuilder.append(
                if (isEn) "⚠️ This is a mandatory update to continue using the app."
                else "⚠️ অ্যাপটি ব্যবহার চালিয়ে যেতে এই আপডেটটি বাধ্যতামূলক।"
            )
        }

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(messageBuilder.toString())
            .setPositiveButton(if (isEn) "UPDATE NOW" else "ডাউনলোড / আপডেট") { dialog, _ ->
                openDownloadUrl(activity, updateInfo.downloadUrl)
                if (updateInfo.isForceUpdate) {
                    // Keep dialog or recreate on resume
                } else {
                    dialog.dismiss()
                }
            }

        if (updateInfo.isForceUpdate) {
            builder.setCancelable(false)
            builder.setOnKeyListener { _, _, _ ->
                // Consume back press for mandatory update
                true
            }
        } else {
            builder.setNegativeButton(if (isEn) "LATER" else "পরে করব") { dialog, _ ->
                dialog.dismiss()
            }
            builder.setCancelable(true)
        }

        val dialog = builder.create()
        dialog.show()

        if (updateInfo.isForceUpdate) {
            dialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun openDownloadUrl(activity: Activity, url: String) {
        if (url.isBlank()) {
            Toast.makeText(activity, "Download URL is not configured.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download URL", e)
            Toast.makeText(activity, "Cannot open download link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
