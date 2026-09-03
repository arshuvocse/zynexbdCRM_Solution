package com.zynexbd.crmsolution.repository

import android.content.Context
import com.zynexbd.crmsolution.models.AttendanceResponse
import com.zynexbd.crmsolution.network.ApiClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AttendanceRepository(context: Context) {

    private val api = ApiClient.getApiService(context)

    suspend fun punchIn(selfieFile: File, latitude: Double, longitude: Double): Result<AttendanceResponse> =
        submit(selfieFile, latitude, longitude, isPunchIn = true)

    suspend fun punchOut(selfieFile: File, latitude: Double, longitude: Double): Result<AttendanceResponse> =
        submit(selfieFile, latitude, longitude, isPunchIn = false)

    private suspend fun submit(selfieFile: File, latitude: Double, longitude: Double, isPunchIn: Boolean): Result<AttendanceResponse> = runCatching {
        val finalFile = if (selfieFile.length() > com.zynexbd.crmsolution.utils.ImageCompressor.MAX_IMAGE_SIZE_BYTES) {
            com.zynexbd.crmsolution.utils.ImageCompressor.compressFile(selfieFile, targetMaxBytes = com.zynexbd.crmsolution.utils.ImageCompressor.MAX_IMAGE_SIZE_BYTES)
        } else {
            selfieFile
        }

        val selfiePart = MultipartBody.Part.createFormData(
            "Selfie", finalFile.name, finalFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        )
        val latPart = latitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val lngPart = longitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        val resp = if (isPunchIn) api.punchIn(selfiePart, latPart, lngPart) else api.punchOut(selfiePart, latPart, lngPart)
        if (resp.isSuccessful) {
            resp.body() ?: error("Empty response")
        } else {
            error(resp.errorBody()?.string() ?: "Duty attendance failed (${resp.code()})")
        }
    }

    suspend fun getMyHistory(month: Int? = null, year: Int? = null): Result<List<AttendanceResponse>> = runCatching {
        val resp = api.getMyAttendanceHistory(month = month, year = year)
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load history (${resp.code()})")
    }

    suspend fun getAllForAdmin(userId: Int? = null, month: Int? = null, year: Int? = null): Result<List<AttendanceResponse>> = runCatching {
        val resp = api.getAllAttendance(userId = userId, month = month, year = year)
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load attendance (${resp.code()})")
    }
}
