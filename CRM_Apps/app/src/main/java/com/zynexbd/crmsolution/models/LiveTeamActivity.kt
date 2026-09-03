package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class LiveTeamActivity(
    @SerializedName("activityId")
    val activityId: Long = 0L,

    @SerializedName("companyId")
    val companyId: Int = 0,

    @SerializedName("userId")
    val userId: Int = 0,

    @SerializedName("userName")
    val userName: String = "",

    @SerializedName("userRole")
    val userRole: String = "User",

    @SerializedName("actionType")
    val actionType: String = "", // LeadCreated, FollowUpAdded, StatusChanged, LeadAssigned, CustomerVisit, RemarkAdded, KpiCreated, KpiUpdated

    @SerializedName("entityType")
    val entityType: String = "Lead", // Lead, Kpi, CustomerVisit

    @SerializedName("title")
    val title: String = "",

    @SerializedName("subtitle")
    val subtitle: String = "",

    @SerializedName("badgeColorHex")
    val badgeColorHex: String? = null,

    @SerializedName("targetEntityId")
    val targetEntityId: Int? = null,

    @SerializedName("createdAtUtc")
    val createdAtUtc: String = ""
)
