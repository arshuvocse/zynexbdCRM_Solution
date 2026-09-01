package com.zynexbd.crmsolution.repository

import android.content.Context
import com.zynexbd.crmsolution.models.*
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.network.ApiService
import okhttp3.ResponseBody
import retrofit2.Response

class CrmRepository(context: Context) {

    private val api: ApiService = ApiClient.getApiService(context)

    // Manager
    suspend fun getManagerDashboard(officeLocationId: Int? = null): Response<ManagerCrmDashboard> =
        api.getCrmManagerDashboard(officeLocationId)

    suspend fun getManagerLeads(
        officeLocationId: Int? = null,
        assignedUserId: Int? = null,
        status: String? = null,
        productServiceId: Int? = null,
        leadSourceId: Int? = null,
        fromDate: String? = null,
        toDate: String? = null,
        search: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        pageNumber: Int = 1,
        pageSize: Int = 20
    ): Response<PagedResult<CrmLead>> = api.getCrmManagerLeads(
        officeLocationId, assignedUserId, status, productServiceId, leadSourceId,
        fromDate, toDate, search, sortBy, sortOrder, pageNumber, pageSize
    )

    suspend fun exportLeads(
        officeLocationId: Int? = null,
        assignedUserId: Int? = null,
        status: String? = null,
        productServiceId: Int? = null,
        leadSourceId: Int? = null,
        fromDate: String? = null,
        toDate: String? = null,
        search: String? = null
    ): Response<ResponseBody> = api.exportCrmLeads(
        officeLocationId, assignedUserId, status, productServiceId, leadSourceId, fromDate, toDate, search
    )

    suspend fun getManagerLeadDetails(id: Int): Response<CrmLeadDetail> = api.getCrmManagerLeadDetails(id)

    suspend fun createLeadByManager(request: CreateCrmLeadRequest): Response<CrmLead> = api.createCrmLeadByManager(request)

    suspend fun updateLeadByManager(id: Int, request: UpdateCrmLeadRequest): Response<CrmLead> = api.updateCrmLeadByManager(id, request)

    suspend fun assignLead(id: Int, request: AssignLeadRequest): Response<CrmLeadDetail> = api.assignCrmLead(id, request)

    suspend fun getManagerFollowUps(
        officeLocationId: Int? = null,
        assignedUserId: Int? = null,
        filterType: String? = null,
        fromDate: String? = null,
        toDate: String? = null
    ): Response<List<CrmFollowUpItem>> = api.getCrmManagerFollowUps(officeLocationId, assignedUserId, filterType, fromDate, toDate)

    suspend fun exportFollowUps(
        officeLocationId: Int? = null,
        assignedUserId: Int? = null,
        filterType: String? = null,
        fromDate: String? = null,
        toDate: String? = null
    ): Response<ResponseBody> = api.exportCrmFollowUps(officeLocationId, assignedUserId, filterType, fromDate, toDate)

    suspend fun getCompanyKpis(officeLocationId: Int? = null): Response<List<CrmKpi>> = api.getCrmCompanyKpis(officeLocationId)

    suspend fun createOrUpdateKpi(request: CreateOrUpdateKpiRequest): Response<CrmKpi> = api.createOrUpdateCrmKpi(request)

    suspend fun exportKpi(officeLocationId: Int? = null): Response<ResponseBody> = api.exportCrmKpi(officeLocationId)

    suspend fun getManagerProductivity(
        officeLocationId: Int? = null,
        periodType: String = "Daily",
        fromDate: String? = null,
        toDate: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null
    ): Response<ManagerProductivity> = api.getCrmManagerProductivity(officeLocationId, periodType, fromDate, toDate, sortBy, sortOrder)

    suspend fun exportProductivity(
        officeLocationId: Int? = null,
        periodType: String = "Daily",
        fromDate: String? = null,
        toDate: String? = null
    ): Response<ResponseBody> = api.exportCrmProductivity(officeLocationId, periodType, fromDate, toDate)

    suspend fun getProductServices(activeOnly: Boolean = true): Response<List<CrmProductService>> = api.getCrmProductServices(activeOnly)

    suspend fun createProductService(request: CreateCrmProductServiceRequest): Response<CrmProductService> = api.createCrmProductService(request)

    suspend fun updateProductService(id: Int, request: UpdateCrmProductServiceRequest): Response<CrmProductService> = api.updateCrmProductService(id, request)

    suspend fun deleteProductService(id: Int): Response<Unit> = api.deleteCrmProductService(id)

    suspend fun getLeadSources(activeOnly: Boolean = true): Response<List<CrmLeadSource>> = api.getCrmLeadSources(activeOnly)

    suspend fun createLeadSource(request: CreateCrmLeadSourceRequest): Response<CrmLeadSource> = api.createCrmLeadSource(request)

    // User / Employee
    suspend fun getUserDashboard(): Response<UserCrmDashboard> = api.getCrmUserDashboard()

    suspend fun getUserLeads(
        status: String? = null,
        productServiceId: Int? = null,
        leadSourceId: Int? = null,
        fromDate: String? = null,
        toDate: String? = null,
        search: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        pageNumber: Int = 1,
        pageSize: Int = 20
    ): Response<PagedResult<CrmLead>> = api.getCrmUserLeads(
        status, productServiceId, leadSourceId,
        fromDate, toDate, search, sortBy, sortOrder, pageNumber, pageSize
    )

    suspend fun getUserLeadDetails(id: Int): Response<CrmLeadDetail> = api.getCrmUserLeadDetails(id)

    suspend fun createSelfLead(request: CreateCrmLeadRequest): Response<CrmLead> = api.createCrmSelfLead(request)

    suspend fun updateLeadStatusByUser(id: Int, request: UpdateLeadStatusRequest): Response<CrmLeadDetail> = api.updateCrmLeadStatusByUser(id, request)

    suspend fun addFollowUp(id: Int, request: CreateFollowUpRequest): Response<CrmFollowUp> = api.addCrmFollowUp(id, request)

    suspend fun addRemark(id: Int, request: CreateRemarkRequest): Response<CrmRemark> = api.addCrmRemark(id, request)

    suspend fun getUserFollowUps(filterType: String? = null): Response<List<CrmFollowUpItem>> = api.getCrmUserFollowUps(filterType)

    suspend fun getUserKpiPerformance(): Response<List<UserKpiPerformance>> = api.getCrmUserKpiPerformance()

    suspend fun getUserActiveProductServices(): Response<List<CrmProductService>> = api.getCrmUserActiveProductServices()

    suspend fun getUserActiveLeadSources(): Response<List<CrmLeadSource>> = api.getCrmUserActiveLeadSources()
}
