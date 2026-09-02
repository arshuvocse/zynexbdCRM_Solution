package com.zynexbd.crmsolution.models

import java.io.Serializable

data class PagedResult<T>(
    val items: List<T> = emptyList(),
    val totalRecords: Int = 0,
    val pageNumber: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 1
) : Serializable

data class CrmProductService(
    val productServiceId: Int = 0,
    val companyId: Int = 0,
    val name: String = "",
    val code: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val isActive: Boolean = true
) : Serializable {
    override fun toString(): String = name
}

data class CreateCrmProductServiceRequest(
    val name: String,
    val code: String? = null,
    val description: String? = null,
    val price: Double? = null
)

data class UpdateCrmProductServiceRequest(
    val name: String? = null,
    val code: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val isActive: Boolean? = null
)

data class CrmLeadSource(
    val leadSourceId: Int = 0,
    val companyId: Int = 0,
    val name: String = "",
    val isSystem: Boolean = false,
    val isActive: Boolean = true
) : Serializable {
    override fun toString(): String = name
}

data class CreateCrmLeadSourceRequest(
    val name: String
)

data class CreateCrmLeadRequest(
    val leadName: String,
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val productServiceId: Int? = null,
    val leadSourceId: Int? = null,
    val leadStatus: String? = "New Lead",
    val assignedUserId: Int? = null,
    val nextFollowUpDate: String? = null,
    val estimatedValue: Double? = null,
    val remarks: String? = null
)

data class UpdateCrmLeadRequest(
    val leadName: String? = null,
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val productServiceId: Int? = null,
    val leadSourceId: Int? = null,
    val leadStatus: String? = null,
    val assignedUserId: Int? = null,
    val nextFollowUpDate: String? = null,
    val estimatedValue: Double? = null,
    val remarks: String? = null,
    val isActive: Boolean? = null
)

data class AssignLeadRequest(
    val newUserId: Int,
    val remarks: String? = null,
    val allowCrossOffice: Boolean = false
)

data class UpdateLeadStatusRequest(
    val status: String,
    val nextFollowUpDate: String? = null,
    val remarks: String? = null
)

data class CreateFollowUpRequest(
    val followUpDate: String? = null,
    val nextFollowUpDate: String? = null,
    val status: String,
    val remarks: String
)

data class CreateRemarkRequest(
    val remark: String
)

data class CrmLead(
    val leadId: Int = 0,
    val companyId: Int = 0,
    val leadName: String = "",
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val productServiceId: Int? = null,
    val productServiceName: String? = null,
    val leadSourceId: Int? = null,
    val leadSourceName: String? = null,
    val leadSourceType: String = "Self",
    val leadStatus: String = "New Lead",
    val createdByUserId: Int = 0,
    val createdByUserName: String? = null,
    val assignedUserId: Int? = null,
    val assignedUserName: String? = null,
    val nextFollowUpDate: String? = null,
    val lastFollowUpDate: String? = null,
    val estimatedValue: Double? = null,
    val latestRemark: String? = null,
    val isActive: Boolean = true,
    val createdAtUtc: String = "",
    val officeLocationId: Int? = null,
    val officeLocationName: String? = null,
    val followUpCount: Int = 0,
    val managerName: String? = null
) : Serializable

data class CrmLeadDetail(
    val leadId: Int = 0,
    val companyId: Int = 0,
    val leadName: String = "",
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val productServiceId: Int? = null,
    val productServiceName: String? = null,
    val leadSourceId: Int? = null,
    val leadSourceName: String? = null,
    val leadSourceType: String = "Self",
    val leadStatus: String = "New Lead",
    val createdByUserId: Int = 0,
    val createdByUserName: String? = null,
    val assignedUserId: Int? = null,
    val assignedUserName: String? = null,
    val nextFollowUpDate: String? = null,
    val lastFollowUpDate: String? = null,
    val estimatedValue: Double? = null,
    val remarks: String? = null,
    val isActive: Boolean = true,
    val createdAtUtc: String = "",
    val updatedAtUtc: String? = null,
    val officeLocationId: Int? = null,
    val officeLocationName: String? = null,
    val followUps: List<CrmFollowUp> = emptyList(),
    val remarksHistory: List<CrmRemark> = emptyList(),
    val assignments: List<CrmLeadAssignment> = emptyList(),
    val statusHistory: List<CrmStatusHistory> = emptyList(),
    val auditLog: List<CrmAuditLogEntry> = emptyList(),
    val followUpCount: Int = 0,
    val managerName: String? = null
) : Serializable

data class CrmFollowUp(
    val followUpId: Int = 0,
    val leadId: Int = 0,
    val followUpDateUtc: String = "",
    val nextFollowUpDate: String? = null,
    val status: String = "",
    val remarks: String = "",
    val createdByUserId: Int = 0,
    val createdByUserName: String? = null,
    val createdAtUtc: String = ""
) : Serializable

data class CrmRemark(
    val remarkId: Int = 0,
    val leadId: Int = 0,
    val userId: Int = 0,
    val userName: String? = null,
    val remark: String = "",
    val createdAtUtc: String = ""
) : Serializable

data class CrmLeadAssignment(
    val assignmentId: Int = 0,
    val leadId: Int = 0,
    val previousUserId: Int? = null,
    val previousUserName: String? = null,
    val newUserId: Int = 0,
    val newUserName: String? = null,
    val assignedByUserId: Int = 0,
    val assignedByUserName: String? = null,
    val assignedDateUtc: String = "",
    val remarks: String? = null
) : Serializable

data class CrmStatusHistory(
    val statusHistoryId: Int = 0,
    val leadId: Int = 0,
    val previousStatus: String = "",
    val newStatus: String = "",
    val changedByUserId: Int = 0,
    val changedByUserName: String? = null,
    val changedDateUtc: String = "",
    val remarks: String? = null
) : Serializable

data class CrmAuditLogEntry(
    val auditLogId: Int = 0,
    val userId: Int = 0,
    val userName: String? = null,
    val action: String = "",
    val entityType: String = "",
    val entityId: Int = 0,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAtUtc: String = ""
) : Serializable

data class CrmKpi(
    val kpiId: Int = 0,
    val companyId: Int = 0,
    val userId: Int? = null,
    val userName: String? = null,
    val periodType: String = "Daily", // 'Daily', 'Weekly', 'Monthly'
    val followUpTarget: Int = 0,
    val interestedTarget: Int = 0,
    val closedTarget: Int = 0,
    val effectiveStartDate: String = "",
    val isActive: Boolean = true,
    val createdByUserId: Int = 0,
    val createdAtUtc: String = "",
    val officeLocationId: Int? = null,
    val officeLocationName: String? = null
) : Serializable

data class CreateOrUpdateKpiRequest(
    val userId: Int? = null,
    val periodType: String,
    val followUpTarget: Int,
    val interestedTarget: Int,
    val closedTarget: Int,
    val officeLocationId: Int? = null
)

data class CrmFollowUpItem(
    val leadId: Int = 0,
    val leadName: String = "",
    val contactPerson: String? = null,
    val phone: String? = null,
    val productServiceName: String? = null,
    val leadStatus: String = "",
    val nextFollowUpDate: String? = null,
    val daysRemaining: Int? = null,
    val isOverdue: Boolean = false,
    val assignedUserId: Int? = null,
    val assignedUserName: String? = null,
    val officeLocationId: Int? = null,
    val officeLocationName: String? = null
) : Serializable

data class EmployeePerformanceSummary(
    val userId: Int = 0,
    val employeeName: String = "",
    val totalLeads: Int = 0,
    val followUpsDone: Int = 0,
    val interestedCount: Int = 0,
    val closedCount: Int = 0,
    val kpiAchievementPercent: Double = 0.0
) : Serializable

data class ManagerCrmDashboard(
    val totalLeads: Int = 0,
    val newLeads: Int = 0,
    val followUpLeads: Int = 0,
    val interestedLeads: Int = 0,
    val notInterestedLeads: Int = 0,
    val closedLeads: Int = 0,
    val todayFollowUps: Int = 0,
    val overdueFollowUps: Int = 0,
    val employeePerformance: List<EmployeePerformanceSummary> = emptyList()
) : Serializable

data class UserCrmDashboard(
    val myTotalLeads: Int = 0,
    val newLeads: Int = 0,
    val followUpLeads: Int = 0,
    val interestedLeads: Int = 0,
    val notInterestedLeads: Int = 0,
    val closedLeads: Int = 0,
    val todayFollowUps: Int = 0,
    val overdueFollowUps: Int = 0,
    val dailyFollowUpTarget: Int = 0,
    val dailyFollowUpAchieved: Int = 0,
    val dailyAchievementPercent: Double = 0.0,
    val weeklyFollowUpTarget: Int = 0,
    val weeklyFollowUpAchieved: Int = 0,
    val weeklyAchievementPercent: Double = 0.0,
    val monthlyFollowUpTarget: Int = 0,
    val monthlyFollowUpAchieved: Int = 0,
    val monthlyAchievementPercent: Double = 0.0
) : Serializable

data class UserKpiPerformance(
    val userId: Int = 0,
    val employeeName: String = "",
    val periodType: String = "Daily",
    val followUpTarget: Int = 0,
    val followUpDone: Int = 0,
    val followUpAchievementPercent: Double = 0.0,
    val interestedTarget: Int = 0,
    val interestedDone: Int = 0,
    val interestedAchievementPercent: Double = 0.0,
    val closedTarget: Int = 0,
    val closedDone: Int = 0,
    val closedAchievementPercent: Double = 0.0,
    val overallAchievementPercent: Double = 0.0
) : Serializable

data class EmployeeProductivityItem(
    val userId: Int = 0,
    val employeeName: String = "",
    val followUpTarget: Int = 0,
    val followUpDone: Int = 0,
    val interestedTarget: Int = 0,
    val interestedDone: Int = 0,
    val closedTarget: Int = 0,
    val closedDone: Int = 0,
    val achievementPercent: Double = 0.0,
    val officeLocationId: Int? = null,
    val officeLocationName: String? = null
) : Serializable

data class ManagerProductivity(
    val periodType: String = "Daily",
    val fromDate: String = "",
    val toDate: String = "",
    val items: List<EmployeeProductivityItem> = emptyList()
) : Serializable

// ==================== VISUAL CHARTS & DASHBOARD ANALYTICS ====================

data class ChartDonutSlice(
    val label: String = "",
    val value: Double = 0.0,
    val colorHex: String = "#3B82F6"
) : Serializable

data class ChartBarEntry(
    val label: String = "",
    val primaryValue: Double = 0.0,
    val secondaryValue: Double = 0.0,
    val category: String? = null
) : Serializable

data class ChartFunnelStage(
    val stageName: String = "",
    val stageCount: Int = 0,
    val conversionPercent: Double = 0.0
) : Serializable

data class AdminCrmDashboardResponse(
    val totalLeads: Int = 0,
    val newLeads: Int = 0,
    val followUpsToday: Int = 0,
    val pendingFollowUps: Int = 0,
    val overdueFollowUps: Int = 0,
    val interestedLeads: Int = 0,
    val notInterestedLeads: Int = 0,
    val closedLeads: Int = 0,
    val conversionRate: Double = 0.0,
    val totalManagers: Int = 0,
    val totalUsers: Int = 0,
    val statusDistribution: List<ChartDonutSlice> = emptyList(),
    val monthlyLeadTrend: List<ChartBarEntry> = emptyList(),
    val followUpTrend: List<ChartBarEntry> = emptyList(),
    val managerPerformance: List<ChartBarEntry> = emptyList(),
    val userProductivity: List<ChartBarEntry> = emptyList(),
    val productPerformance: List<ChartBarEntry> = emptyList(),
    val sourceDistribution: List<ChartDonutSlice> = emptyList(),
    val conversionFunnel: List<ChartFunnelStage> = emptyList()
) : Serializable

data class ManagerCrmDashboardResponse(
    val teamLeads: Int = 0,
    val newLeads: Int = 0,
    val todayFollowUps: Int = 0,
    val pendingFollowUps: Int = 0,
    val overdueFollowUps: Int = 0,
    val interestedLeads: Int = 0,
    val closedLeads: Int = 0,
    val conversionRate: Double = 0.0,
    val kpiAchievement: Double = 0.0,
    val teamLeadTrend: List<ChartBarEntry> = emptyList(),
    val employeeProductivity: List<ChartBarEntry> = emptyList(),
    val kpiAchievementBreakdown: List<ChartBarEntry> = emptyList(),
    val statusDistribution: List<ChartDonutSlice> = emptyList(),
    val followUpPerformance: List<ChartBarEntry> = emptyList(),
    val productPerformance: List<ChartBarEntry> = emptyList(),
    val sourceDistribution: List<ChartDonutSlice> = emptyList(),
    val conversionFunnel: List<ChartFunnelStage> = emptyList()
) : Serializable

data class UserCrmDashboardResponse(
    val myTotalLeads: Int = 0,
    val myNewLeads: Int = 0,
    val todayFollowUps: Int = 0,
    val pendingFollowUps: Int = 0,
    val overdueFollowUps: Int = 0,
    val interestedLeads: Int = 0,
    val closedLeads: Int = 0,
    val dailyFollowUpTarget: Int = 0,
    val dailyFollowUpAchieved: Int = 0,
    val dailyAchievementPercent: Double = 0.0,
    val weeklyFollowUpTarget: Int = 0,
    val weeklyFollowUpAchieved: Int = 0,
    val weeklyAchievementPercent: Double = 0.0,
    val monthlyFollowUpTarget: Int = 0,
    val monthlyFollowUpAchieved: Int = 0,
    val monthlyAchievementPercent: Double = 0.0,
    val myLeadStatus: List<ChartDonutSlice> = emptyList(),
    val myLeadTrend: List<ChartBarEntry> = emptyList(),
    val myFollowUpTrend: List<ChartBarEntry> = emptyList(),
    val myKpiAchievement: List<ChartBarEntry> = emptyList(),
    val myConversionFunnel: List<ChartFunnelStage> = emptyList()
) : Serializable

// ==================== ENTERPRISE CRM REPORTS ====================

data class CrmReportSummary(
    val totalRows: Int = 0,
    val summary1Label: String = "",
    val summary1Value: String = "",
    val summary2Label: String = "",
    val summary2Value: String = "",
    val summary3Label: String = "",
    val summary3Value: String = ""
) : Serializable

data class CrmReportRow(
    val rowId: Int = 0,
    val entityId: Int = 0,
    val title: String = "",
    val subtitle: String = "",
    val tag: String = "",
    val value1: String = "",
    val value2: String = "",
    val value3: String = "",
    val value4: String = "",
    val status: String = "",
    val createdAtUtc: String? = null
) : Serializable

data class CrmReportResponse(
    val reportType: Int = 1,
    val reportTitle: String = "",
    val summary: CrmReportSummary = CrmReportSummary(),
    val rows: List<CrmReportRow> = emptyList(),
    val pageNumber: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 1
) : Serializable
