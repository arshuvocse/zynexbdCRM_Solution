package com.zynexbd.crmsolution.repository

import android.content.Context
import com.zynexbd.crmsolution.models.*
import com.zynexbd.crmsolution.network.ApiClient

class LeaveRepository(context: Context) {

    private val api = ApiClient.getApiService(context)

    suspend fun getActiveTypes(): Result<List<LeaveType>> = runCatching {
        val resp = api.getActiveLeaveTypes()
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load leave types (${resp.code()})")
    }

    suspend fun getMyBalances(): Result<List<LeaveBalance>> = runCatching {
        val resp = api.getMyLeaveBalances()
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load balances (${resp.code()})")
    }

    suspend fun apply(request: ApplyLeaveRequest): Result<LeaveApplicationResponse> = runCatching {
        val resp = api.applyLeave(request)
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed to apply for leave (${resp.code()})")
    }

    suspend fun getMyHistory(): Result<List<LeaveApplicationResponse>> = runCatching {
        val resp = api.getMyLeaveHistory()
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load leave history (${resp.code()})")
    }

    suspend fun cancel(id: Int): Result<LeaveApplicationResponse> = runCatching {
        val resp = api.cancelLeave(id)
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed to cancel (${resp.code()})")
    }

    suspend fun getApplications(status: String? = null): Result<List<LeaveApplicationResponse>> = runCatching {
        val resp = api.getLeaveApplications(status)
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load applications (${resp.code()})")
    }

    suspend fun approve(id: Int, comment: String?): Result<LeaveApplicationResponse> = runCatching {
        val resp = api.approveLeave(id, LeaveReviewRequest(comment))
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed to approve (${resp.code()})")
    }

    suspend fun reject(id: Int, comment: String?): Result<LeaveApplicationResponse> = runCatching {
        val resp = api.rejectLeave(id, LeaveReviewRequest(comment))
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed to reject (${resp.code()})")
    }

    suspend fun bulkApprove(ids: List<Int>, comment: String?): Result<BulkLeaveResponse> = runCatching {
        val resp = api.bulkApproveLeave(BulkLeaveRequest(ids, comment))
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed bulk approval (${resp.code()})")
    }
}
