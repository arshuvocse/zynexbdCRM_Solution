package com.zynexbd.crmsolution.models

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?,
    val errors: List<String>?
)
