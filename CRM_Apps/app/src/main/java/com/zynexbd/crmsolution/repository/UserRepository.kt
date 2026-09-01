package com.zynexbd.crmsolution.repository

import android.content.Context
import com.zynexbd.crmsolution.models.*
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.utils.SessionManager

class UserRepository(context: Context) {

    private val api = ApiClient.getApiService(context)
    private val session = SessionManager(context)

    suspend fun getUsers(): Result<List<User>> = runCatching {
        val resp = api.getUsers(session.getCompanyId())
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load users (${resp.code()})")
    }

    suspend fun createUser(request: CreateUserRequest): Result<User> = runCatching {
        val resp = api.createUser(request)
        if (resp.isSuccessful) {
            resp.body() ?: error("Empty response")
        } else {
            val errBody = resp.errorBody()?.string()?.takeIf { it.isNotBlank() }
            error(errBody ?: "Failed to create user (${resp.code()})")
        }
    }

    suspend fun updateUser(id: Int, request: UpdateUserRequest): Result<User> = runCatching {
        val resp = api.updateUser(id, request)
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed to update user (${resp.code()})")
    }

    suspend fun setActive(user: User, isActive: Boolean): Result<User> =
        updateUser(user.id, UpdateUserRequest(
            name = user.name,
            role = user.role,
            isActive = isActive,
            phoneNumber = user.phoneNumber,
            officeLocationId = user.officeLocationId,
            assignedOfficeLocationIds = user.assignedOfficeLocationIds
        ))

    suspend fun resetPassword(id: Int, newPassword: String): Result<Unit> = runCatching {
        val resp = api.resetPassword(id, ResetPasswordRequest(newPassword))
        if (!resp.isSuccessful) error("Failed to reset password (${resp.code()})")
    }

    suspend fun resetUserDevice(id: Int): Result<Unit> = runCatching {
        val resp = api.resetUserDevice(id)
        if (!resp.isSuccessful) error("Failed to reset device binding (${resp.code()})")
    }

    suspend fun getOfficeLocations(all: Boolean? = null): Result<List<OfficeLocation>> = runCatching {
        val resp = api.getOfficeLocations(all)
        if (resp.isSuccessful) resp.body() ?: emptyList() else error("Failed to load office locations (${resp.code()})")
    }

    suspend fun createOfficeLocation(request: CreateOfficeLocationRequest): Result<OfficeLocation> = runCatching {
        val resp = api.createOfficeLocation(request)
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed to create office location (${resp.code()})")
    }

    suspend fun updateOfficeLocation(id: Int, request: UpdateOfficeLocationRequest): Result<OfficeLocation> = runCatching {
        val resp = api.updateOfficeLocation(id, request)
        if (resp.isSuccessful) resp.body() ?: error("Empty response") else error("Failed to update office location (${resp.code()})")
    }

    suspend fun deleteOfficeLocation(id: Int): Result<Unit> = runCatching {
        val resp = api.deleteOfficeLocation(id)
        if (!resp.isSuccessful) error("Failed to delete office location (${resp.code()})")
    }

    suspend fun getUserQuota(): Result<AdminUserQuota> = runCatching {
        val resp = api.getUserQuota(session.getCompanyId())
        if (resp.isSuccessful) resp.body() ?: AdminUserQuota() else error("Failed to load user quota (${resp.code()})")
    }
}
