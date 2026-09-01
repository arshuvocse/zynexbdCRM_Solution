package com.zynexbd.crmsolution.network

import com.zynexbd.crmsolution.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/subscription/status")
    suspend fun getSubscriptionStatus(): Response<SubscriptionStatus>

    @GET("api/subscription/plans")
    suspend fun getSubscriptionPlans(): Response<List<SubscriptionPlan>>

    @GET("api/subscription/user/{userId}/status")
    suspend fun getUserSubscriptionStatus(@Path("userId") userId: Int): Response<SubscriptionStatus>

    @POST("api/subscription/update-due-date")
    suspend fun updateSubscriptionDueDate(@Body request: UpdatePaymentDueDateRequest): Response<SubscriptionStatus>

    @GET("api/users")
    suspend fun getUsers(@Query("companyId") companyId: Int? = null): Response<List<User>>

    @GET("api/users/quota")
    suspend fun getUserQuota(@Query("companyId") companyId: Int? = null): Response<AdminUserQuota>

    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<User>

    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body request: UpdateUserRequest): Response<User>

    @DELETE("api/users/{id}")
    suspend fun disableUser(@Path("id") id: Int): Response<Unit>

    @POST("api/users/{id}/reset-password")
    suspend fun resetPassword(@Path("id") id: Int, @Body request: ResetPasswordRequest): Response<Unit>

    @POST("api/users/{id}/reset-device")
    suspend fun resetUserDevice(@Path("id") id: Int): Response<Unit>

    @POST("api/locations/ping")
    suspend fun sendLocationPing(@Body request: LocationPingRequest): Response<Unit>

    @GET("api/locations/latest")
    suspend fun getLatestLocations(@Query("companyId") companyId: Int? = null): Response<List<LocationResponse>>

    @GET("api/locations/history/{userId}")
    suspend fun getRouteHistory(
        @Path("userId") userId: Int,
        @Query("date") date: String
    ): Response<List<LocationResponse>>

    @GET("api/admin/office-locations")
    suspend fun getOfficeLocations(@Query("all") all: Boolean? = null): Response<List<OfficeLocation>>

    @POST("api/admin/office-locations")
    suspend fun createOfficeLocation(@Body request: CreateOfficeLocationRequest): Response<OfficeLocation>

    @PUT("api/admin/office-locations/{id}")
    suspend fun updateOfficeLocation(@Path("id") id: Int, @Body request: UpdateOfficeLocationRequest): Response<OfficeLocation>

    @DELETE("api/admin/office-locations/{id}")
    suspend fun deleteOfficeLocation(@Path("id") id: Int): Response<Unit>

    @GET("api/admin/summary")
    suspend fun getExecutiveSummary(): Response<ExecutiveSummaryResponse>

    // Attendance
    @Multipart
    @POST("api/attendance/punch-in")
    suspend fun punchIn(
        @Part selfie: MultipartBody.Part,
        @Part("Latitude") latitude: RequestBody,
        @Part("Longitude") longitude: RequestBody
    ): Response<AttendanceResponse>

    @Multipart
    @POST("api/attendance/punch-out")
    suspend fun punchOut(
        @Part selfie: MultipartBody.Part,
        @Part("Latitude") latitude: RequestBody,
        @Part("Longitude") longitude: RequestBody
    ): Response<AttendanceResponse>

    @GET("api/attendance/today")
    suspend fun getTodayAttendanceStatus(): Response<TodayAttendanceStatusResponse>

    @GET("api/attendance/history")
    suspend fun getMyAttendanceHistory(
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<List<AttendanceResponse>>

    @GET("api/attendance/admin")
    suspend fun getAllAttendance(
        @Query("userId") userId: Int? = null,
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<List<AttendanceResponse>>

    @GET("api/attendance/admin/monthly-summary")
    suspend fun getMonthlyAttendanceSummary(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("userId") userId: Int? = null
    ): Response<List<EmployeeMonthlyAttendanceSummary>>

    // Leave
    @GET("api/leave/types")
    suspend fun getActiveLeaveTypes(): Response<List<LeaveType>>

    @GET("api/leave/my-balances")
    suspend fun getMyLeaveBalances(): Response<List<LeaveBalance>>

    @POST("api/leave/apply")
    suspend fun applyLeave(@Body request: ApplyLeaveRequest): Response<LeaveApplicationResponse>

    @GET("api/leave/my-history")
    suspend fun getMyLeaveHistory(): Response<List<LeaveApplicationResponse>>

    @PUT("api/leave/{id}/cancel")
    suspend fun cancelLeave(@Path("id") id: Int): Response<LeaveApplicationResponse>

    @GET("api/leave/admin")
    suspend fun getLeaveApplications(@Query("status") status: String? = null): Response<List<LeaveApplicationResponse>>

    @PUT("api/leave/admin/{id}/approve")
    suspend fun approveLeave(@Path("id") id: Int, @Body request: LeaveReviewRequest): Response<LeaveApplicationResponse>

    @PUT("api/leave/admin/{id}/reject")
    suspend fun rejectLeave(@Path("id") id: Int, @Body request: LeaveReviewRequest): Response<LeaveApplicationResponse>

    @PUT("api/leave/admin/bulk-approve")
    suspend fun bulkApproveLeave(@Body request: BulkLeaveRequest): Response<BulkLeaveResponse>

    @GET("api/customers")
    suspend fun getCustomers(
        @Query("search") search: String? = null,
        @Query("targetUserId") targetUserId: Int? = null
    ): Response<List<Customer>>

    @GET("api/customers/{id}")
    suspend fun getCustomerById(@Path("id") id: Int): Response<Customer>

    @POST("api/customers")
    suspend fun createCustomer(@Body request: CreateCustomerRequest): Response<Customer>

    @POST("api/visits")
    suspend fun recordVisit(@Body request: RecordVisitRequest): Response<CustomerVisit>

    @GET("api/visits/my-visits")
    suspend fun getMyVisits(
        @Query("customerId") customerId: Int? = null,
        @Query("targetUserId") targetUserId: Int? = null
    ): Response<List<CustomerVisit>>

    @GET("api/visits/followups")
    suspend fun getFollowUps(
        @Query("category") category: String? = null,
        @Query("targetUserId") targetUserId: Int? = null
    ): Response<List<FollowUpItem>>

    @PUT("api/visits/{id}/complete-followup")
    suspend fun completeFollowUp(@Path("id") id: Long): Response<Unit>

    @GET("api/visits/dashboard-stats")
    suspend fun getFieldUserDashboardStats(): Response<FieldUserDashboardStats>

    // Shifts Management
    @GET("api/shifts")
    suspend fun getShifts(): Response<List<com.zynexbd.crmsolution.models.Shift>>

    @POST("api/shifts")
    suspend fun createShift(@Body request: com.zynexbd.crmsolution.models.CreateShiftRequest): Response<com.zynexbd.crmsolution.models.Shift>

    @PUT("api/shifts/{id}")
    suspend fun updateShift(@Path("id") id: Int, @Body request: com.zynexbd.crmsolution.models.UpdateShiftRequest): Response<com.zynexbd.crmsolution.models.Shift>

    @PUT("api/shifts/{id}/set-default")
    suspend fun setDefaultShift(@Path("id") id: Int): Response<com.zynexbd.crmsolution.models.Shift>

    @DELETE("api/shifts/{id}")
    suspend fun deleteShift(@Path("id") id: Int): Response<Unit>

    @GET("api/app-version/check")
    suspend fun checkAppVersion(
        @Query("versionCode") versionCode: Int,
        @Query("platform") platform: String = "Android",
        @Query("companyId") companyId: Int? = null
    ): Response<AppVersionCheckResponse>

    @GET("api/app-version/latest")
    suspend fun getLatestAppVersion(
        @Query("platform") platform: String = "Android",
        @Query("companyId") companyId: Int? = null
    ): Response<AppVersionDetails>

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("unreadOnly") unreadOnly: Boolean = false,
        @Query("take") take: Int = 50,
        @Query("companyId") companyId: Int? = null
    ): Response<List<NotificationItem>>

    @GET("api/notifications/unread-count")
    suspend fun getUnreadNotificationCount(
        @Query("companyId") companyId: Int? = null
    ): Response<UnreadNotificationCount>

    @PUT("api/notifications/{id}/read")
    suspend fun markNotificationAsRead(@Path("id") id: Int): Response<Unit>

    @PUT("api/notifications/mark-all-read")
    suspend fun markAllNotificationsAsRead(): Response<Unit>

    @POST("api/notifications/broadcast")
    suspend fun sendBroadcastNotification(@Body request: SendNotificationRequest): Response<NotificationItem>

    @POST("api/users/{id}/force-logout")
    suspend fun forceLogoutUser(@Path("id") id: Int): Response<Unit>

    @GET("api/admin/reports/employee-performance")
    suspend fun getEmployeePerformanceReport(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("userId") userId: Int? = null
    ): Response<MonthlyPerformanceReportResponse>

    // Holidays
    @GET("api/holidays")
    suspend fun getHolidays(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("includeInactive") includeInactive: Boolean = true
    ): Response<List<com.zynexbd.crmsolution.models.Holiday>>

    @POST("api/holidays")
    suspend fun createHoliday(
        @Body request: com.zynexbd.crmsolution.models.CreateOrUpdateHolidayRequest
    ): Response<com.zynexbd.crmsolution.models.Holiday>

    @PUT("api/holidays/{id}")
    suspend fun updateHoliday(
        @Path("id") id: Int,
        @Body request: com.zynexbd.crmsolution.models.CreateOrUpdateHolidayRequest
    ): Response<com.zynexbd.crmsolution.models.Holiday>

    @PATCH("api/holidays/{id}/toggle-status")
    suspend fun toggleHolidayStatus(
        @Path("id") id: Int
    ): Response<com.zynexbd.crmsolution.models.Holiday>

    @DELETE("api/holidays/{id}")
    suspend fun deleteHoliday(
        @Path("id") id: Int
    ): Response<Unit>

    // ==========================================
    // CRM MANAGER ENDPOINTS
    // ==========================================

    @GET("api/crm/manager/dashboard")
    suspend fun getCrmManagerDashboard(@Query("officeLocationId") officeLocationId: Int? = null): Response<ManagerCrmDashboard>

    @GET("api/crm/manager/leads")
    suspend fun getCrmManagerLeads(
        @Query("officeLocationId") officeLocationId: Int? = null,
        @Query("assignedUserId") assignedUserId: Int? = null,
        @Query("status") status: String? = null,
        @Query("productServiceId") productServiceId: Int? = null,
        @Query("leadSourceId") leadSourceId: Int? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null,
        @Query("search") search: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<PagedResult<CrmLead>>

    @Streaming
    @GET("api/crm/manager/leads/export")
    suspend fun exportCrmLeads(
        @Query("officeLocationId") officeLocationId: Int? = null,
        @Query("assignedUserId") assignedUserId: Int? = null,
        @Query("status") status: String? = null,
        @Query("productServiceId") productServiceId: Int? = null,
        @Query("leadSourceId") leadSourceId: Int? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null,
        @Query("search") search: String? = null
    ): Response<ResponseBody>

    @GET("api/crm/manager/leads/{id}")
    suspend fun getCrmManagerLeadDetails(@Path("id") id: Int): Response<CrmLeadDetail>

    @POST("api/crm/manager/leads")
    suspend fun createCrmLeadByManager(@Body request: CreateCrmLeadRequest): Response<CrmLead>

    @PUT("api/crm/manager/leads/{id}")
    suspend fun updateCrmLeadByManager(@Path("id") id: Int, @Body request: UpdateCrmLeadRequest): Response<CrmLead>

    @POST("api/crm/manager/leads/{id}/assign")
    suspend fun assignCrmLead(@Path("id") id: Int, @Body request: AssignLeadRequest): Response<CrmLeadDetail>

    @GET("api/crm/manager/followups")
    suspend fun getCrmManagerFollowUps(
        @Query("officeLocationId") officeLocationId: Int? = null,
        @Query("assignedUserId") assignedUserId: Int? = null,
        @Query("filterType") filterType: String? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null
    ): Response<List<CrmFollowUpItem>>

    @Streaming
    @GET("api/crm/manager/followups/export")
    suspend fun exportCrmFollowUps(
        @Query("officeLocationId") officeLocationId: Int? = null,
        @Query("assignedUserId") assignedUserId: Int? = null,
        @Query("filterType") filterType: String? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null
    ): Response<ResponseBody>

    @GET("api/crm/manager/kpi")
    suspend fun getCrmCompanyKpis(@Query("officeLocationId") officeLocationId: Int? = null): Response<List<CrmKpi>>

    @POST("api/crm/manager/kpi")
    suspend fun createOrUpdateCrmKpi(@Body request: CreateOrUpdateKpiRequest): Response<CrmKpi>

    @Streaming
    @GET("api/crm/manager/kpi/export")
    suspend fun exportCrmKpi(@Query("officeLocationId") officeLocationId: Int? = null): Response<ResponseBody>

    @GET("api/crm/manager/productivity")
    suspend fun getCrmManagerProductivity(
        @Query("officeLocationId") officeLocationId: Int? = null,
        @Query("periodType") periodType: String = "Daily",
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null
    ): Response<ManagerProductivity>

    @Streaming
    @GET("api/crm/manager/productivity/export")
    suspend fun exportCrmProductivity(
        @Query("officeLocationId") officeLocationId: Int? = null,
        @Query("periodType") periodType: String = "Daily",
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null
    ): Response<ResponseBody>

    @GET("api/crm/manager/products-services")
    suspend fun getCrmProductServices(@Query("activeOnly") activeOnly: Boolean = true): Response<List<CrmProductService>>

    @POST("api/crm/manager/products-services")
    suspend fun createCrmProductService(@Body request: CreateCrmProductServiceRequest): Response<CrmProductService>

    @PUT("api/crm/manager/products-services/{id}")
    suspend fun updateCrmProductService(@Path("id") id: Int, @Body request: UpdateCrmProductServiceRequest): Response<CrmProductService>

    @DELETE("api/crm/manager/products-services/{id}")
    suspend fun deleteCrmProductService(@Path("id") id: Int): Response<Unit>

    @GET("api/crm/manager/lead-sources")
    suspend fun getCrmLeadSources(@Query("activeOnly") activeOnly: Boolean = true): Response<List<CrmLeadSource>>

    @POST("api/crm/manager/lead-sources")
    suspend fun createCrmLeadSource(@Body request: CreateCrmLeadSourceRequest): Response<CrmLeadSource>

    // ==========================================
    // CRM USER / EMPLOYEE ENDPOINTS
    // ==========================================

    @GET("api/crm/user/dashboard")
    suspend fun getCrmUserDashboard(): Response<UserCrmDashboard>

    @GET("api/crm/user/leads")
    suspend fun getCrmUserLeads(
        @Query("status") status: String? = null,
        @Query("productServiceId") productServiceId: Int? = null,
        @Query("leadSourceId") leadSourceId: Int? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null,
        @Query("search") search: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<PagedResult<CrmLead>>

    @GET("api/crm/user/leads/{id}")
    suspend fun getCrmUserLeadDetails(@Path("id") id: Int): Response<CrmLeadDetail>

    @POST("api/crm/user/leads")
    suspend fun createCrmSelfLead(@Body request: CreateCrmLeadRequest): Response<CrmLead>

    @PUT("api/crm/user/leads/{id}/status")
    suspend fun updateCrmLeadStatusByUser(@Path("id") id: Int, @Body request: UpdateLeadStatusRequest): Response<CrmLeadDetail>

    @POST("api/crm/user/leads/{id}/followup")
    suspend fun addCrmFollowUp(@Path("id") id: Int, @Body request: CreateFollowUpRequest): Response<CrmFollowUp>

    @POST("api/crm/user/leads/{id}/remarks")
    suspend fun addCrmRemark(@Path("id") id: Int, @Body request: CreateRemarkRequest): Response<CrmRemark>

    @GET("api/crm/user/followups")
    suspend fun getCrmUserFollowUps(@Query("filterType") filterType: String? = null): Response<List<CrmFollowUpItem>>

    @GET("api/crm/user/kpi")
    suspend fun getCrmUserKpiPerformance(): Response<List<UserKpiPerformance>>

    @GET("api/crm/user/products-services")
    suspend fun getCrmUserActiveProductServices(): Response<List<CrmProductService>>

    @GET("api/crm/user/lead-sources")
    suspend fun getCrmUserActiveLeadSources(): Response<List<CrmLeadSource>>
}
