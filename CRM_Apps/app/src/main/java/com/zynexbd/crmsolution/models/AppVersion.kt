package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class AppVersionCheckResponse(
    @SerializedName("hasUpdate") val hasUpdate: Boolean = false,
    @SerializedName("isForceUpdate") val isForceUpdate: Boolean = false,
    @SerializedName("latestVersionCode") val latestVersionCode: Int = 1,
    @SerializedName("latestVersionName") val latestVersionName: String = "1.0",
    @SerializedName("downloadUrl") val downloadUrl: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("releaseNotes") val releaseNotes: String = "",
    @SerializedName("currentVersionCode") val currentVersionCode: Int = 1
)

data class AppVersionDetails(
    @SerializedName("appVersionId") val appVersionId: Int = 0,
    @SerializedName("platform") val platform: String = "Android",
    @SerializedName("versionCode") val versionCode: Int = 1,
    @SerializedName("versionName") val versionName: String = "1.0",
    @SerializedName("minVersionCode") val minVersionCode: Int = 1,
    @SerializedName("isForceUpdate") val isForceUpdate: Boolean = false,
    @SerializedName("downloadUrl") val downloadUrl: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("releaseNotes") val releaseNotes: String = "",
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("createdAtUtc") val createdAtUtc: String = ""
)
