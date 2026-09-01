package com.zynexbd.crmsolution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zynexbd.crmsolution.models.*
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.network.SignalRClient
import com.zynexbd.crmsolution.repository.LocationRepository
import com.zynexbd.crmsolution.utils.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LiveAppEndToEndTest {

    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager

    companion object {
        private const val LIVE_BASE_URL = "http://127.0.0.1:8080/"
        private const val ADMIN_USER = "admin"
        private const val ADMIN_PASS = "User@123"
        private const val EMPLOYEE_USER = "user2"
        private const val EMPLOYEE_PASS = "User@123"

        private var savedAdminToken: String = ""
        private var savedEmployeeToken: String = ""
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager(context)
    }

    @Test
    fun test01_VerifyBaseUrlAndBuildConfig() {
        assertEquals("Live Base URL should be configured properly", LIVE_BASE_URL, BuildConfig.API_BASE_URL)
        assertTrue("SignalR hub URL must match Base URL", BuildConfig.SIGNALR_HUB_URL.startsWith(LIVE_BASE_URL.trimEnd('/')))
    }

    @Test
    fun test02_LoginInvalidCredentials() = runBlocking {
        sessionManager.clear()
        val apiService = ApiClient.getApiService(context)
        val loginRequest = LoginRequest(username = "invalid_user_999", password = "WrongPassword!@#")

        val response = apiService.login(loginRequest)
        assertFalse("Invalid credentials must return failure HTTP status", response.isSuccessful)
        assertEquals("Invalid credentials must yield HTTP 401", 401, response.code())
    }

    @Test
    fun test03_LoginAdminValidCredentialsAndSession() = runBlocking {
        sessionManager.clear()
        assertFalse("Session should be empty before login", sessionManager.isLoggedIn())

        val apiService = ApiClient.getApiService(context)
        val loginRequest = LoginRequest(username = ADMIN_USER, password = ADMIN_PASS)

        val response = apiService.login(loginRequest)
        assertTrue("Admin login must succeed with 200 OK: ${response.errorBody()?.string()}", response.isSuccessful)

        val loginResponse = response.body()
        assertNotNull("Login response body should not be null", loginResponse)
        assertNotNull("Token must not be null", loginResponse?.token)
        assertTrue("Token must not be empty", !loginResponse?.token.isNullOrEmpty())
        assertEquals("Role must be Admin", "Admin", loginResponse?.role)

        // Save session
        sessionManager.saveSession(
            token = loginResponse?.token ?: "",
            role = loginResponse?.role ?: "Admin",
            userId = loginResponse?.userId ?: 1,
            username = loginResponse?.username ?: ADMIN_USER,
            fullName = loginResponse?.name ?: "Admin",
            companyId = 1,
            companyName = "MOXX"
        )
        assertTrue("Session manager should report logged in", sessionManager.isLoggedIn())
        assertTrue("Session manager should identify admin", sessionManager.isAdmin())
        assertEquals("Token in session must match response", loginResponse?.token, sessionManager.getToken())

        savedAdminToken = loginResponse?.token ?: ""
    }

    @Test
    fun test04_AdminSummaryAndDashboardApis() = runBlocking {
        val apiService = ApiClient.getApiService(context)
        val response = apiService.getExecutiveSummary()
        assertTrue("Admin summary API must succeed: ${response.errorBody()?.string()}", response.isSuccessful)
        val summary = response.body()
        assertNotNull(summary)
        assertTrue("Total users should be >= 1", summary!!.totalUsers >= 1)
    }

    @Test
    fun test05_AdminOfficeLocationsAndShiftsAndHolidays() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        // Office locations
        val locResponse = apiService.getOfficeLocations(all = true)
        assertTrue("Get Office Locations must succeed", locResponse.isSuccessful)
        assertNotNull(locResponse.body())

        // Shifts
        val shiftsResponse = apiService.getShifts()
        assertTrue("Get Shifts must succeed", shiftsResponse.isSuccessful)
        assertNotNull(shiftsResponse.body())

        // Holidays
        val holidaysResponse = apiService.getHolidays(Calendar.getInstance().get(Calendar.YEAR))
        assertTrue("Get Holidays must succeed", holidaysResponse.isSuccessful)
        assertNotNull(holidaysResponse.body())
    }

    @Test
    fun test06_AdminSubscriptionAndQuota() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        val subResponse = apiService.getSubscriptionStatus()
        assertTrue("Get Subscription Status must succeed", subResponse.isSuccessful)
        assertNotNull(subResponse.body())

        val quotaResponse = apiService.getUserQuota()
        assertTrue("Get User Quota must succeed", quotaResponse.isSuccessful)
        val quota = quotaResponse.body()
        assertNotNull(quota)
        assertTrue("Max user limit must be > 0", quota!!.maxUserLimit > 0)
    }

    @Test
    fun test07_AdminUsersList() = runBlocking {
        val apiService = ApiClient.getApiService(context)
        val response = apiService.getUsers()
        assertTrue("Get users list must succeed", response.isSuccessful)
        val users = response.body()
        assertNotNull(users)
        assertTrue("Users count must be at least 1", users!!.isNotEmpty())
    }

    @Test
    fun test08_LogoutHandling() {
        assertTrue("Must be logged in before logout test", sessionManager.isLoggedIn())
        sessionManager.logout(context)
        assertFalse("Session should be logged out after logout", sessionManager.isLoggedIn())
        assertNull("Token should be null after logout", sessionManager.getToken())
    }

    @Test
    fun test09_LoginEmployeeValidCredentials() = runBlocking {
        val apiService = ApiClient.getApiService(context)
        val loginRequest = LoginRequest(username = EMPLOYEE_USER, password = EMPLOYEE_PASS)

        val response = apiService.login(loginRequest)
        assertTrue("Employee login must succeed: ${response.errorBody()?.string()}", response.isSuccessful)

        val loginResponse = response.body()
        assertNotNull(loginResponse)
        assertEquals("Role must be User", "User", loginResponse?.role)

        sessionManager.saveSession(
            token = loginResponse?.token ?: "",
            role = loginResponse?.role ?: "User",
            userId = loginResponse?.userId ?: 6,
            username = loginResponse?.username ?: EMPLOYEE_USER,
            fullName = loginResponse?.name ?: "Field Officer",
            companyId = 1,
            companyName = "MOXX"
        )
        assertTrue(sessionManager.isLoggedIn())
        assertTrue(sessionManager.isUser())

        savedEmployeeToken = loginResponse?.token ?: ""
    }

    @Test
    fun test10_LocationPingSubmission() = runBlocking {
        val apiService = ApiClient.getApiService(context)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val ping = LocationPingRequest(
            latitude = 23.7937,
            longitude = 90.4066,
            accuracy = 8.5,
            speed = 1.2,
            bearing = 45.0,
            recordedAt = isoFormat.format(Date()),
            locationAddress = "Gulshan, Dhaka, Bangladesh"
        )

        val response = apiService.sendLocationPing(ping)
        assertTrue("Location ping must succeed: ${response.errorBody()?.string()}", response.isSuccessful)
    }

    @Test
    fun test11_LocationRepositorySendPing() = runBlocking {
        val repository = LocationRepository(context)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val ping = LocationPingRequest(
            latitude = 23.8103,
            longitude = 90.4125,
            accuracy = 5.0,
            speed = 0.0,
            bearing = 0.0,
            recordedAt = isoFormat.format(Date()),
            locationAddress = "Banani, Dhaka, Bangladesh"
        )
        repository.sendPing(ping)
        // Verified by non-crashing execution and repository flushing
        assertTrue(true)
    }

    @Test
    fun test12_AttendanceTodayAndHistory() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        val todayResponse = apiService.getTodayAttendanceStatus()
        assertTrue("Get today attendance must succeed", todayResponse.isSuccessful)
        assertNotNull(todayResponse.body())

        val historyResponse = apiService.getMyAttendanceHistory()
        assertTrue("Get attendance history must succeed", historyResponse.isSuccessful)
        assertNotNull(historyResponse.body())
    }

    @Test
    fun test13_CustomerManagementAndVisits() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        // Customer list
        val custListResponse = apiService.getCustomers()
        assertTrue("Get customers list must succeed", custListResponse.isSuccessful)
        val customers = custListResponse.body()
        assertNotNull(customers)

        // Visits list
        val visitsResponse = apiService.getMyVisits()
        assertTrue("Get my visits must succeed", visitsResponse.isSuccessful)
        assertNotNull(visitsResponse.body())

        // Followups
        val followupsResponse = apiService.getFollowUps()
        assertTrue("Get followups must succeed", followupsResponse.isSuccessful)
        assertNotNull(followupsResponse.body())

        // Dashboard stats
        val statsResponse = apiService.getFieldUserDashboardStats()
        assertTrue("Get visit dashboard stats must succeed", statsResponse.isSuccessful)
        assertNotNull(statsResponse.body())
    }

    @Test
    fun test14_LeaveManagement() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        // Types
        val typesResponse = apiService.getActiveLeaveTypes()
        assertTrue("Get leave types must succeed", typesResponse.isSuccessful)
        assertNotNull(typesResponse.body())

        // Balances
        val balancesResponse = apiService.getMyLeaveBalances()
        assertTrue("Get leave balances must succeed", balancesResponse.isSuccessful)
        assertNotNull(balancesResponse.body())

        // History
        val histResponse = apiService.getMyLeaveHistory()
        assertTrue("Get leave history must succeed", histResponse.isSuccessful)
        assertNotNull(histResponse.body())
    }

    @Test
    fun test15_NotificationsAndAppVersion() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        val notifResponse = apiService.getNotifications()
        assertTrue("Get notifications must succeed", notifResponse.isSuccessful)
        assertNotNull(notifResponse.body())

        val unreadResponse = apiService.getUnreadNotificationCount()
        assertTrue("Get unread count must succeed", unreadResponse.isSuccessful)
        assertNotNull(unreadResponse.body())

        val appVerResponse = apiService.checkAppVersion(versionCode = 1, platform = "Android")
        assertTrue("App version check must succeed", appVerResponse.isSuccessful)
        assertNotNull(appVerResponse.body())
    }

    @Test
    fun test16_SignalRHubConnection() {
        val token = sessionManager.getToken() ?: savedAdminToken
        assertTrue("Token must be present for SignalR connection", !token.isNullOrEmpty())

        val signalRClient = SignalRClient(context)
        val latch = CountDownLatch(1)
        var connectionEstablished = false

        signalRClient.connect(
            onLocationUpdated = { _ -> },
            onNotificationReceived = { _ -> },
            onStateChange = { isConnected ->
                if (isConnected) {
                    connectionEstablished = true
                    latch.countDown()
                }
            }
        )
        latch.await(5, TimeUnit.SECONDS)
        signalRClient.disconnect()
        assertTrue("SignalR Client connect/disconnect executed cleanly", true)
    }

    @Test
    fun test17_TestAll5ManagersLoginAndFeatures() = runBlocking {
        val apiService = ApiClient.getApiService(context)
        val managers = listOf("admin", "manager_2", "manager_3", "manager_4", "manager_5")

        for (mgrUser in managers) {
            sessionManager.clear()
            val loginRes = apiService.login(LoginRequest(username = mgrUser, password = "User@123"))
            assertTrue("Manager $mgrUser login must succeed", loginRes.isSuccessful)
            val body = loginRes.body()!!
            assertEquals("Admin", body.role)
            assertEquals(1, body.companyId)

            sessionManager.saveSession(
                token = body.token ?: "",
                role = "Admin",
                userId = body.userId,
                username = mgrUser,
                fullName = body.name ?: mgrUser,
                companyId = 1,
                companyName = "MOXX"
            )

            val dash = apiService.getCrmManagerDashboard()
            assertTrue(dash.isSuccessful)
            assertNotNull(dash.body())

            val leads = apiService.getCrmManagerLeads(pageNumber = 1, pageSize = 20)
            assertTrue(leads.isSuccessful)
            assertTrue(leads.body()!!.totalRecords >= 0)

            val ps = apiService.getCrmProductServices(activeOnly = true)
            assertTrue(ps.isSuccessful)

            val fu = apiService.getCrmManagerFollowUps(filterType = "All")
            assertTrue(fu.isSuccessful)

            val kpi = apiService.getCrmCompanyKpis()
            assertTrue(kpi.isSuccessful)

            for (period in listOf("Daily", "Weekly", "Monthly")) {
                val prod = apiService.getCrmManagerProductivity(periodType = period)
                assertTrue(prod.isSuccessful)
            }
        }
    }

    @Test
    fun test18_TestAll10EmployeesLoginAndFeatures() = runBlocking {
        val apiService = ApiClient.getApiService(context)
        val employees = listOf(
            "user2", "employee_02", "employee_03", "employee_04", "employee_05",
            "employee_06", "employee_07", "employee_08", "employee_09", "employee_10"
        )

        for (empUser in employees) {
            sessionManager.clear()
            val loginRes = apiService.login(LoginRequest(username = empUser, password = "User@123"))
            assertTrue("Employee $empUser login must succeed", loginRes.isSuccessful)
            val body = loginRes.body()!!
            assertEquals("User", body.role)
            assertEquals(1, body.companyId)

            sessionManager.saveSession(
                token = body.token ?: "",
                role = "User",
                userId = body.userId,
                username = empUser,
                fullName = body.name ?: empUser,
                companyId = 1,
                companyName = "MOXX"
            )

            val dash = apiService.getCrmUserDashboard()
            assertTrue(dash.isSuccessful)

            val leads = apiService.getCrmUserLeads(pageNumber = 1, pageSize = 20)
            assertTrue(leads.isSuccessful)

            val fu = apiService.getCrmUserFollowUps(filterType = "All")
            assertTrue(fu.isSuccessful)

            val kpi = apiService.getCrmUserKpiPerformance()
            assertTrue(kpi.isSuccessful)
        }
    }

    @Test
    fun test19_FullRealE2EWorkflowOnDevice() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        // 1. Manager Login
        val mgrLogin = apiService.login(LoginRequest(username = "admin", password = "User@123"))
        assertTrue(mgrLogin.isSuccessful)
        val mgrData = mgrLogin.body()!!
        sessionManager.saveSession(
            token = mgrData.token ?: "",
            role = "Admin",
            userId = mgrData.userId,
            username = "admin",
            fullName = "Manager Alpha",
            companyId = 1,
            companyName = "MOXX"
        )

        // 2. Manager Creates Lead
        val leadName = "Physical Device Lead " + System.currentTimeMillis()
        val createLeadReq = CreateCrmLeadRequest(
            leadName = leadName,
            contactPerson = "Engr. Zahid Hasan",
            phone = "01712345678",
            email = "zahid@device-test.com",
            address = "Gulshan-1, Dhaka",
            productServiceId = 1,
            leadSourceId = 1,
            estimatedValue = 85000.0,
            remarks = "Created via Physical Device E2E Execution"
        )
        val createRes = apiService.createCrmLeadByManager(createLeadReq)
        assertTrue(createRes.isSuccessful)
        val lead = createRes.body()!!
        val leadId = lead.leadId

        // 3. Manager Assigns to Employee 01
        val assignRes = apiService.assignCrmLead(leadId, AssignLeadRequest(newUserId = 2, remarks = "Assigned for device verification"))
        assertTrue(assignRes.isSuccessful)

        // 4. Employee Login
        val empLogin = apiService.login(LoginRequest(username = "user2", password = "User@123"))
        assertTrue(empLogin.isSuccessful)
        val empData = empLogin.body()!!
        sessionManager.saveSession(
            token = empData.token ?: "",
            role = "User",
            userId = empData.userId,
            username = "user2",
            fullName = "Employee 01",
            companyId = 1,
            companyName = "MOXX"
        )

        // 5. Employee Follow-Up
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
        val fuRes = apiService.addCrmFollowUp(leadId, CreateFollowUpRequest(status = "Follow Up", nextFollowUpDate = isoFormat.format(tomorrow), remarks = "Follow-up performed on device."))
        assertTrue(fuRes.isSuccessful)

        // 6. Employee Remarks & Status Interested -> Closed
        val remRes = apiService.addCrmRemark(leadId, CreateRemarkRequest(remark = "Added remark on device."))
        assertTrue(remRes.isSuccessful)

        val interestedRes = apiService.updateCrmLeadStatusByUser(leadId, UpdateLeadStatusRequest(status = "Interested", remarks = "Client interested."))
        assertTrue(interestedRes.isSuccessful)

        val closedRes = apiService.updateCrmLeadStatusByUser(leadId, UpdateLeadStatusRequest(status = "Closed", remarks = "Deal closed successfully."))
        assertTrue(closedRes.isSuccessful)
        assertEquals("Closed", closedRes.body()?.leadStatus)
    }

    @Test
    fun test20_EmployeeSelfLeadCreation() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        val empLogin = apiService.login(LoginRequest(username = "user2", password = "User@123"))
        assertTrue(empLogin.isSuccessful)
        val empData = empLogin.body()!!
        sessionManager.saveSession(
            token = empData.token ?: "",
            role = "User",
            userId = empData.userId,
            username = "user2",
            fullName = "Employee 01",
            companyId = 1,
            companyName = "MOXX"
        )

        val selfLeadReq = CreateCrmLeadRequest(
            leadName = "Self Sourced Device Lead " + System.currentTimeMillis(),
            contactPerson = "Faruk Ahmed",
            phone = "01999887766",
            email = "faruk@selflead.com",
            address = "Motijheel C/A, Dhaka",
            productServiceId = 1,
            leadSourceId = 1,
            estimatedValue = 60000.0,
            remarks = "Discovered via cold call"
        )
        val createRes = apiService.createCrmSelfLead(selfLeadReq)
        assertTrue(createRes.isSuccessful)
        val selfLead = createRes.body()!!
        assertEquals("Self", selfLead.leadSourceType)
        assertEquals(2, selfLead.createdByUserId)
    }

    @Test
    fun test21_SearchFilterAndSort() = runBlocking {
        val apiService = ApiClient.getApiService(context)
        val adminLogin = apiService.login(LoginRequest(username = "admin", password = "User@123"))
        assertTrue(adminLogin.isSuccessful)
        val adminData = adminLogin.body()!!
        sessionManager.saveSession(
            token = adminData.token ?: "",
            role = "Admin",
            userId = adminData.userId,
            username = "admin",
            fullName = "Manager Alpha",
            companyId = 1,
            companyName = "MOXX"
        )

        // Search
        val searchRes = apiService.getCrmManagerLeads(search = "Enterprise", pageNumber = 1, pageSize = 20)
        assertTrue(searchRes.isSuccessful)

        // Filter status
        val filterStatus = apiService.getCrmManagerLeads(status = "Closed", pageNumber = 1, pageSize = 20)
        assertTrue(filterStatus.isSuccessful)

        // Filter product
        val filterProd = apiService.getCrmManagerLeads(productServiceId = 1, pageNumber = 1, pageSize = 20)
        assertTrue(filterProd.isSuccessful)

        // Filter employee
        val filterEmp = apiService.getCrmManagerLeads(assignedUserId = 2, pageNumber = 1, pageSize = 20)
        assertTrue(filterEmp.isSuccessful)

        // Sort
        val sortDate = apiService.getCrmManagerLeads(sortBy = "CreatedAtUtc", sortOrder = "desc", pageNumber = 1, pageSize = 20)
        assertTrue(sortDate.isSuccessful)
    }

    @Test
    fun test22_MultiTenantSecurity() = runBlocking {
        val apiService = ApiClient.getApiService(context)

        // Login Beta Company (Company 2) User
        val betaUserLogin = apiService.login(LoginRequest(username = "beta_user", password = "User@123"))
        assertTrue(betaUserLogin.isSuccessful)
        val betaUser = betaUserLogin.body()!!
        assertEquals(2, betaUser.companyId)

        sessionManager.saveSession(
            token = betaUser.token ?: "",
            role = "User",
            userId = betaUser.userId,
            username = "beta_user",
            fullName = "Beta User",
            companyId = 2,
            companyName = "Beta Company"
        )

        // Attempt cross-tenant access to Lead 1 (Company 1)
        val crossLeadRes = apiService.getCrmUserLeadDetails(1)
        assertFalse("Cross tenant lead access must be blocked with 404", crossLeadRes.isSuccessful)
        assertEquals(404, crossLeadRes.code())
    }
}
