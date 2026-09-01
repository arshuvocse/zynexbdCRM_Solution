package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class OfficeLocation(
    @SerializedName(value = "id", alternate = ["officeLocationId"]) val id: Int = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 100.0,
    val address: String? = null,
    val isActive: Boolean = true
)

data class CreateOfficeLocationRequest(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 200.0,
    val address: String? = null
)

data class UpdateOfficeLocationRequest(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 200.0,
    val address: String? = null,
    val isActive: Boolean = true
)
