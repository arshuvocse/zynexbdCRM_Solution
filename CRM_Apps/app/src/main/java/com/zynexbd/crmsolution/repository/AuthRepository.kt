package com.zynexbd.crmsolution.repository

import android.content.Context
import com.zynexbd.crmsolution.models.LoginRequest
import com.zynexbd.crmsolution.models.LoginResponse
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.utils.SessionManager

sealed class AuthResult {
    data class Success(val response: LoginResponse) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository(private val context: Context) {

    private val api = ApiClient.getApiService(context)
    private val session = SessionManager(context)

    suspend fun login(username: String, password: String, deviceId: String? = null, deviceModel: String? = null): AuthResult {
        com.zynexbd.crmsolution.utils.AppLogger.i("AuthRepo", "Attempting login for user='$username', deviceId='$deviceId', model='$deviceModel'")
        return try {
            val resp = api.login(LoginRequest(username, password, deviceId, deviceModel))
            com.zynexbd.crmsolution.utils.AppLogger.d("AuthRepo", "Login HTTP status: ${resp.code()}")
            if (resp.isSuccessful) {
                val body = resp.body()
                val target = if (!body?.token.isNullOrEmpty()) body else body?.data
                val token = target?.token

                if (target != null && !token.isNullOrEmpty()) {
                    val role = target.role ?: body?.role ?: "User"
                    val userId = if (target.userId != 0) target.userId else body?.userId ?: 0
                    val uname = target.username ?: body?.username ?: username
                    val fullName = target.name ?: body?.name ?: uname
                    val companyId = target.companyId ?: body?.companyId
                    val companyName = target.companyName ?: body?.companyName
                    val officeLocationId = target.officeLocationId ?: body?.officeLocationId
                    val officeLocationName = target.officeLocationName ?: body?.officeLocationName
                    val authorizedOffices = target.authorizedOfficeLocations ?: body?.authorizedOfficeLocations

                    com.zynexbd.crmsolution.utils.AppLogger.i("AuthRepo", "Login SUCCESS for user='$uname' (ID: $userId, Role: $role, Company: $companyName)")

                    session.saveSession(
                        token = token,
                        role = role,
                        userId = userId,
                        username = uname,
                        fullName = fullName,
                        companyId = companyId,
                        companyName = companyName,
                        officeLocationId = officeLocationId,
                        officeLocationName = officeLocationName,
                        authorizedOfficeLocations = authorizedOffices
                    )
                    AuthResult.Success(
                        LoginResponse(
                            token = token,
                            expiresAt = target.expiresAt ?: body?.expiresAt,
                            userId = userId,
                            name = fullName,
                            username = uname,
                            role = role,
                            companyId = companyId,
                            companyName = companyName,
                            officeLocationId = officeLocationId,
                            officeLocationName = officeLocationName,
                            authorizedOfficeLocations = authorizedOffices
                        )
                    )
                } else {
                    com.zynexbd.crmsolution.utils.AppLogger.e("AuthRepo", "Login response payload did not contain a valid JWT token")
                    AuthResult.Error("Invalid response format: token missing.")
                }
            } else if (resp.code() == 401) {
                com.zynexbd.crmsolution.utils.AppLogger.w("AuthRepo", "Login failed: 401 Unauthorized for user '$username'")
                AuthResult.Error("ইউজারনেম বা পাসওয়ার্ড সঠিক নয়।")
            } else {
                val rawBody = resp.errorBody()?.string()?.takeIf { it.isNotBlank() }
                com.zynexbd.crmsolution.utils.AppLogger.e("AuthRepo", "Login error: HTTP ${resp.code()} - $rawBody")
                val parsedMsg = try {
                    if (rawBody != null && rawBody.startsWith("{")) {
                        org.json.JSONObject(rawBody).optString("message", rawBody)
                    } else rawBody
                } catch (e: Exception) {
                    rawBody
                }
                AuthResult.Error(parsedMsg ?: "Server error (${resp.code()}).")
            }
        } catch (e: Exception) {
            com.zynexbd.crmsolution.utils.AppLogger.e("AuthRepo", "Login exception: ${e.javaClass.simpleName} - ${e.message}", e)
            AuthResult.Error(e.message ?: "Network error.")
        }
    }

    fun logout() {
        session.clear()
    }
}
