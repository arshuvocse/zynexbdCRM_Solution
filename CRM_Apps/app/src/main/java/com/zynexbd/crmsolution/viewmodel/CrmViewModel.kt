package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.*
import com.zynexbd.crmsolution.repository.CrmRepository
import kotlinx.coroutines.launch

class CrmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CrmRepository(application)

    // Loading & Error states
    val isLoading = MutableLiveData<Boolean>(false)
    val errorMessage = MutableLiveData<String?>()
    val successMessage = MutableLiveData<String?>()

    // Manager states
    val managerDashboard = MutableLiveData<ManagerCrmDashboard?>()
    val managerLeads = MutableLiveData<PagedResult<CrmLead>?>()
    val leadDetails = MutableLiveData<CrmLeadDetail?>()
    val managerFollowUps = MutableLiveData<List<CrmFollowUpItem>>(emptyList())
    val companyKpis = MutableLiveData<List<CrmKpi>>(emptyList())
    val managerProductivity = MutableLiveData<ManagerProductivity?>()
    val productServices = MutableLiveData<List<CrmProductService>>(emptyList())
    val leadSources = MutableLiveData<List<CrmLeadSource>>(emptyList())

    // User states
    val userDashboard = MutableLiveData<UserCrmDashboard?>()
    val userLeads = MutableLiveData<PagedResult<CrmLead>?>()
    val userFollowUps = MutableLiveData<List<CrmFollowUpItem>>(emptyList())
    val userKpiPerformance = MutableLiveData<List<UserKpiPerformance>>(emptyList())

    // Manager actions
    fun loadManagerDashboard(officeLocationId: Int? = null) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getManagerDashboard(officeLocationId)
                if (res.isSuccessful) {
                    managerDashboard.value = res.body()
                } else {
                    errorMessage.value = "Failed to load CRM Dashboard (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadManagerLeads(
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
    ) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getManagerLeads(
                    officeLocationId, assignedUserId, status, productServiceId, leadSourceId,
                    fromDate, toDate, search, sortBy, sortOrder, pageNumber, pageSize
                )
                if (res.isSuccessful) {
                    managerLeads.value = res.body()
                } else {
                    errorMessage.value = "Failed to load Leads (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadLeadDetails(leadId: Int, isManager: Boolean = true) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = if (isManager) repository.getManagerLeadDetails(leadId) else repository.getUserLeadDetails(leadId)
                if (res.isSuccessful) {
                    leadDetails.value = res.body()
                } else {
                    errorMessage.value = "Failed to load Lead details (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun createLead(request: CreateCrmLeadRequest, isManager: Boolean, onComplete: (Boolean) -> Unit) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = if (isManager) repository.createLeadByManager(request) else repository.createSelfLead(request)
                if (res.isSuccessful) {
                    successMessage.value = "Lead created successfully!"
                    onComplete(true)
                } else {
                    val rawErr = res.errorBody()?.string()
                    val msg = try {
                        val obj = org.json.JSONObject(rawErr ?: "")
                        obj.optString("message", rawErr ?: "Failed to create Lead (${res.code()})")
                    } catch (_: Exception) {
                        rawErr?.takeIf { it.isNotBlank() } ?: "Failed to create Lead (${res.code()})"
                    }
                    errorMessage.value = msg
                    onComplete(false)
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
                onComplete(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun updateLead(leadId: Int, request: UpdateCrmLeadRequest, onComplete: (Boolean) -> Unit) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.updateLeadByManager(leadId, request)
                if (res.isSuccessful) {
                    successMessage.value = "Lead updated successfully!"
                    loadLeadDetails(leadId, true)
                    onComplete(true)
                } else {
                    errorMessage.value = "Failed to update Lead (${res.code()})"
                    onComplete(false)
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
                onComplete(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun assignLead(leadId: Int, newUserId: Int, remarks: String?, onComplete: (Boolean) -> Unit) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.assignLead(leadId, AssignLeadRequest(newUserId, remarks))
                if (res.isSuccessful) {
                    leadDetails.value = res.body()
                    successMessage.value = "Lead assigned successfully!"
                    onComplete(true)
                } else {
                    errorMessage.value = "Failed to assign lead (${res.code()})"
                    onComplete(false)
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
                onComplete(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addFollowUp(leadId: Int, request: CreateFollowUpRequest, isManager: Boolean, onComplete: (Boolean) -> Unit) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.addFollowUp(leadId, request)
                if (res.isSuccessful) {
                    successMessage.value = "Follow-up recorded successfully!"
                    loadLeadDetails(leadId, isManager)
                    onComplete(true)
                } else {
                    errorMessage.value = "Failed to add follow-up (${res.code()})"
                    onComplete(false)
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
                onComplete(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addRemark(leadId: Int, remark: String, isManager: Boolean, onComplete: (Boolean) -> Unit) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.addRemark(leadId, CreateRemarkRequest(remark))
                if (res.isSuccessful) {
                    successMessage.value = "Remark added successfully!"
                    loadLeadDetails(leadId, isManager)
                    onComplete(true)
                } else {
                    errorMessage.value = "Failed to add remark (${res.code()})"
                    onComplete(false)
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
                onComplete(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun updateLeadStatus(leadId: Int, status: String, nextFollowUpDate: String?, remarks: String?, onComplete: (Boolean) -> Unit) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.updateLeadStatusByUser(leadId, UpdateLeadStatusRequest(status, nextFollowUpDate, remarks))
                if (res.isSuccessful) {
                    leadDetails.value = res.body()
                    successMessage.value = "Status updated successfully!"
                    onComplete(true)
                } else {
                    errorMessage.value = "Failed to update status (${res.code()})"
                    onComplete(false)
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
                onComplete(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadManagerFollowUps(officeLocationId: Int? = null, assignedUserId: Int? = null, filterType: String? = null, fromDate: String? = null, toDate: String? = null) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getManagerFollowUps(officeLocationId, assignedUserId, filterType, fromDate, toDate)
                if (res.isSuccessful) {
                    managerFollowUps.value = res.body() ?: emptyList()
                } else {
                    errorMessage.value = "Failed to load follow-ups (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadCompanyKpis(officeLocationId: Int? = null) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getCompanyKpis(officeLocationId)
                if (res.isSuccessful) {
                    companyKpis.value = res.body() ?: emptyList()
                } else {
                    errorMessage.value = "Failed to load KPIs (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun saveKpi(request: CreateOrUpdateKpiRequest, onComplete: (Boolean) -> Unit) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.createOrUpdateKpi(request)
                if (res.isSuccessful) {
                    successMessage.value = "KPI target saved successfully!"
                    loadCompanyKpis()
                    onComplete(true)
                } else {
                    errorMessage.value = "Failed to save KPI (${res.code()})"
                    onComplete(false)
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
                onComplete(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadManagerProductivity(
        officeLocationId: Int? = null,
        periodType: String = "Daily",
        fromDate: String? = null,
        toDate: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null
    ) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getManagerProductivity(officeLocationId, periodType, fromDate, toDate, sortBy, sortOrder)
                if (res.isSuccessful) {
                    managerProductivity.value = res.body()
                } else {
                    errorMessage.value = "Failed to load Productivity (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    // CSV export passthroughs - CrmCsvExporter drives its own loading/error UI around these,
    // so they intentionally don't touch isLoading/errorMessage like the LiveData-backed loaders.
    suspend fun exportLeadsCsv(
        officeLocationId: Int? = null,
        assignedUserId: Int? = null,
        status: String? = null,
        productServiceId: Int? = null,
        leadSourceId: Int? = null,
        fromDate: String? = null,
        toDate: String? = null,
        search: String? = null
    ) = repository.exportLeads(officeLocationId, assignedUserId, status, productServiceId, leadSourceId, fromDate, toDate, search)

    suspend fun exportFollowUpsCsv(
        officeLocationId: Int? = null,
        assignedUserId: Int? = null,
        filterType: String? = null,
        fromDate: String? = null,
        toDate: String? = null
    ) = repository.exportFollowUps(officeLocationId, assignedUserId, filterType, fromDate, toDate)

    suspend fun exportProductivityCsv(
        officeLocationId: Int? = null,
        periodType: String = "Daily",
        fromDate: String? = null,
        toDate: String? = null
    ) = repository.exportProductivity(officeLocationId, periodType, fromDate, toDate)

    suspend fun exportKpiCsv(officeLocationId: Int? = null) = repository.exportKpi(officeLocationId)

    fun loadMasterData(isManager: Boolean = true) {
        viewModelScope.launch {
            try {
                val pRes = if (isManager) repository.getProductServices(true) else repository.getUserActiveProductServices()
                if (pRes.isSuccessful) productServices.value = pRes.body() ?: emptyList()

                val sRes = if (isManager) repository.getLeadSources(true) else repository.getUserActiveLeadSources()
                if (sRes.isSuccessful) leadSources.value = sRes.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    // User actions
    fun loadUserDashboard() {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getUserDashboard()
                if (res.isSuccessful) {
                    userDashboard.value = res.body()
                } else {
                    errorMessage.value = "Failed to load Dashboard (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadUserLeads(
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
    ) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getUserLeads(
                    status, productServiceId, leadSourceId,
                    fromDate, toDate, search, sortBy, sortOrder, pageNumber, pageSize
                )
                if (res.isSuccessful) {
                    userLeads.value = res.body()
                } else {
                    errorMessage.value = "Failed to load My Leads (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadUserFollowUps(filterType: String? = null) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getUserFollowUps(filterType)
                if (res.isSuccessful) {
                    userFollowUps.value = res.body() ?: emptyList()
                } else {
                    errorMessage.value = "Failed to load Follow-ups (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadUserKpiPerformance() {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val res = repository.getUserKpiPerformance()
                if (res.isSuccessful) {
                    userKpiPerformance.value = res.body() ?: emptyList()
                } else {
                    errorMessage.value = "Failed to load KPI Performance (${res.code()})"
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "Network error"
            } finally {
                isLoading.value = false
            }
        }
    }
}
