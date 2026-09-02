namespace LiveTracking.Api.DTOs;

public record PagedResult<T>(
    List<T> Items,
    int TotalRecords,
    int PageNumber,
    int PageSize,
    int TotalPages
);

public record CrmProductServiceDto(
    int ProductServiceId,
    int CompanyId,
    string Name,
    string? Code,
    string? Description,
    decimal? Price,
    bool IsActive
);

public record CreateCrmProductServiceRequest(
    string Name,
    string? Code,
    string? Description,
    decimal? Price
);

public record UpdateCrmProductServiceRequest(
    string? Name,
    string? Code,
    string? Description,
    decimal? Price,
    bool? IsActive
);

public record CrmLeadSourceDto(
    int LeadSourceId,
    int CompanyId,
    string Name,
    bool IsSystem,
    bool IsActive
);

public record CreateCrmLeadSourceRequest(
    string Name
);

public record CreateCrmLeadRequest(
    string LeadName,
    string? ContactPerson,
    string? Phone,
    string? Email,
    string? Address,
    int? ProductServiceId,
    int? LeadSourceId,
    string? LeadStatus,
    int? AssignedUserId,
    DateTime? NextFollowUpDate,
    decimal? EstimatedValue,
    string? Remarks
);

public record UpdateCrmLeadRequest(
    string? LeadName,
    string? ContactPerson,
    string? Phone,
    string? Email,
    string? Address,
    int? ProductServiceId,
    int? LeadSourceId,
    string? LeadStatus,
    int? AssignedUserId,
    DateTime? NextFollowUpDate,
    decimal? EstimatedValue,
    string? Remarks,
    bool? IsActive
);

public record AssignLeadRequest(
    int NewUserId,
    string? Remarks,
    bool AllowCrossOffice = false
);

public record UpdateLeadStatusRequest(
    string Status,
    DateTime? NextFollowUpDate,
    string? Remarks
);

public record CreateFollowUpRequest(
    DateTime? FollowUpDate,
    DateTime? NextFollowUpDate,
    string Status,
    string Remarks
);

public record CreateRemarkRequest(
    string Remark
);

public record CrmLeadResponse(
    int LeadId,
    int CompanyId,
    string LeadName,
    string? ContactPerson,
    string? Phone,
    string? Email,
    string? Address,
    int? ProductServiceId,
    string? ProductServiceName,
    int? LeadSourceId,
    string? LeadSourceName,
    string LeadSourceType,
    string LeadStatus,
    int CreatedByUserId,
    string? CreatedByUserName,
    int? AssignedUserId,
    string? AssignedUserName,
    DateTime? NextFollowUpDate,
    DateTime? LastFollowUpDate,
    decimal? EstimatedValue,
    string? LatestRemark,
    bool IsActive,
    DateTime CreatedAtUtc,
    int? OfficeLocationId,
    string? OfficeLocationName,
    int FollowUpCount = 0,
    string? ManagerName = null
);

public record CrmLeadDetailResponse(
    int LeadId,
    int CompanyId,
    string LeadName,
    string? ContactPerson,
    string? Phone,
    string? Email,
    string? Address,
    int? ProductServiceId,
    string? ProductServiceName,
    int? LeadSourceId,
    string? LeadSourceName,
    string LeadSourceType,
    string LeadStatus,
    int CreatedByUserId,
    string? CreatedByUserName,
    int? AssignedUserId,
    string? AssignedUserName,
    DateTime? NextFollowUpDate,
    DateTime? LastFollowUpDate,
    decimal? EstimatedValue,
    string? Remarks,
    bool IsActive,
    DateTime CreatedAtUtc,
    DateTime? UpdatedAtUtc,
    int? OfficeLocationId,
    string? OfficeLocationName,
    List<CrmFollowUpDto> FollowUps,
    List<CrmRemarkDto> RemarksHistory,
    List<CrmLeadAssignmentDto> Assignments,
    List<CrmStatusHistoryDto> StatusHistory,
    List<CrmAuditLogDto> AuditLog,
    int FollowUpCount = 0,
    string? ManagerName = null
);

public record CrmStatusHistoryDto(
    int StatusHistoryId,
    int LeadId,
    string PreviousStatus,
    string NewStatus,
    int ChangedByUserId,
    string? ChangedByUserName,
    DateTime ChangedDateUtc,
    string? Remarks
);

public record CrmAuditLogDto(
    int AuditLogId,
    int UserId,
    string? UserName,
    string Action,
    string EntityType,
    int EntityId,
    string? OldValue,
    string? NewValue,
    DateTime CreatedAtUtc
);

public record CrmFollowUpDto(
    int FollowUpId,
    int LeadId,
    DateTime FollowUpDateUtc,
    DateTime? NextFollowUpDate,
    string Status,
    string Remarks,
    int CreatedByUserId,
    string? CreatedByUserName,
    DateTime CreatedAtUtc
);

public record CrmRemarkDto(
    int RemarkId,
    int LeadId,
    int UserId,
    string? UserName,
    string Remark,
    DateTime CreatedAtUtc
);

public record CrmLeadAssignmentDto(
    int AssignmentId,
    int LeadId,
    int? PreviousUserId,
    string? PreviousUserName,
    int NewUserId,
    string? NewUserName,
    int AssignedByUserId,
    string? AssignedByUserName,
    DateTime AssignedDateUtc,
    string? Remarks
);

public record CrmKpiDto(
    int KpiId,
    int CompanyId,
    int? UserId,
    string? UserName,
    string PeriodType, // 'Daily', 'Weekly', 'Monthly'
    int FollowUpTarget,
    int InterestedTarget,
    int ClosedTarget,
    DateTime EffectiveStartDate,
    bool IsActive,
    int CreatedByUserId,
    DateTime CreatedAtUtc,
    int? OfficeLocationId,
    string? OfficeLocationName
);

public record CreateOrUpdateKpiRequest(
    int? UserId,
    string PeriodType,
    int FollowUpTarget,
    int InterestedTarget,
    int ClosedTarget,
    int? OfficeLocationId = null
);

public record CrmFollowUpItemDto(
    int LeadId,
    string LeadName,
    string? ContactPerson,
    string? Phone,
    string? ProductServiceName,
    string LeadStatus,
    DateTime? NextFollowUpDate,
    int? DaysRemaining,
    bool IsOverdue,
    int? AssignedUserId,
    string? AssignedUserName,
    int? OfficeLocationId,
    string? OfficeLocationName
);

public record EmployeePerformanceSummaryDto(
    int UserId,
    string EmployeeName,
    int TotalLeads,
    int FollowUpsDone,
    int InterestedCount,
    int ClosedCount,
    double KpiAchievementPercent
);

public record ManagerDashboardResponse(
    int TotalLeads,
    int NewLeads,
    int FollowUpLeads,
    int InterestedLeads,
    int NotInterestedLeads,
    int ClosedLeads,
    int TodayFollowUps,
    int OverdueFollowUps,
    List<EmployeePerformanceSummaryDto> EmployeePerformance
);

public record UserDashboardResponse(
    int MyTotalLeads,
    int NewLeads,
    int FollowUpLeads,
    int InterestedLeads,
    int NotInterestedLeads,
    int ClosedLeads,
    int TodayFollowUps,
    int OverdueFollowUps,
    int DailyFollowUpTarget,
    int DailyFollowUpAchieved,
    double DailyAchievementPercent,
    int WeeklyFollowUpTarget,
    int WeeklyFollowUpAchieved,
    double WeeklyAchievementPercent,
    int MonthlyFollowUpTarget,
    int MonthlyFollowUpAchieved,
    double MonthlyAchievementPercent
);

public record UserKpiPerformanceResponse(
    int UserId,
    string EmployeeName,
    string PeriodType,
    int FollowUpTarget,
    int FollowUpDone,
    double FollowUpAchievementPercent,
    int InterestedTarget,
    int InterestedDone,
    double InterestedAchievementPercent,
    int ClosedTarget,
    int ClosedDone,
    double ClosedAchievementPercent,
    double OverallAchievementPercent
);

public record EmployeeProductivityItemDto(
    int UserId,
    string EmployeeName,
    int FollowUpTarget,
    int FollowUpDone,
    int InterestedTarget,
    int InterestedDone,
    int ClosedTarget,
    int ClosedDone,
    double AchievementPercent,
    int? OfficeLocationId,
    string? OfficeLocationName
);

public record ManagerProductivityResponse(
    string PeriodType,
    DateTime FromDate,
    DateTime ToDate,
    List<EmployeeProductivityItemDto> Items
);

// ==================== VISUAL CHARTS & DASHBOARD ANALYTICS ====================

public record ChartDonutSliceDto(
    string Label,
    double Value,
    string ColorHex
);

public record ChartBarEntryDto(
    string Label,
    double PrimaryValue,
    double SecondaryValue,
    string? Category = null
);

public record ChartFunnelStageDto(
    string StageName,
    int StageCount,
    double ConversionPercent
);

public record CrmDashboardFilterRequest(
    DateTime? FromDate = null,
    DateTime? ToDate = null,
    int? OfficeLocationId = null,
    int? ManagerId = null,
    int? UserId = null,
    int? ProductServiceId = null,
    string? LeadStatus = null,
    int? LeadSourceId = null
);

public record AdminCrmDashboardResponse(
    int TotalLeads,
    int NewLeads,
    int FollowUpsToday,
    int PendingFollowUps,
    int OverdueFollowUps,
    int InterestedLeads,
    int NotInterestedLeads,
    int ClosedLeads,
    double ConversionRate,
    int TotalManagers,
    int TotalUsers,
    List<ChartDonutSliceDto> StatusDistribution,
    List<ChartBarEntryDto> MonthlyLeadTrend,
    List<ChartBarEntryDto> FollowUpTrend,
    List<ChartBarEntryDto> ManagerPerformance,
    List<ChartBarEntryDto> UserProductivity,
    List<ChartBarEntryDto> ProductPerformance,
    List<ChartDonutSliceDto> SourceDistribution,
    List<ChartFunnelStageDto> ConversionFunnel
);

public record ManagerCrmDashboardResponse(
    int TeamLeads,
    int NewLeads,
    int TodayFollowUps,
    int PendingFollowUps,
    int OverdueFollowUps,
    int InterestedLeads,
    int ClosedLeads,
    double ConversionRate,
    double KpiAchievement,
    List<ChartBarEntryDto> TeamLeadTrend,
    List<ChartBarEntryDto> EmployeeProductivity,
    List<ChartBarEntryDto> KpiAchievementBreakdown,
    List<ChartDonutSliceDto> StatusDistribution,
    List<ChartBarEntryDto> FollowUpPerformance,
    List<ChartBarEntryDto> ProductPerformance,
    List<ChartDonutSliceDto> SourceDistribution,
    List<ChartFunnelStageDto> ConversionFunnel
);

public record UserCrmDashboardResponse(
    int MyTotalLeads,
    int MyNewLeads,
    int TodayFollowUps,
    int PendingFollowUps,
    int OverdueFollowUps,
    int InterestedLeads,
    int ClosedLeads,
    int DailyFollowUpTarget,
    int DailyFollowUpAchieved,
    double DailyAchievementPercent,
    int WeeklyFollowUpTarget,
    int WeeklyFollowUpAchieved,
    double WeeklyAchievementPercent,
    int MonthlyFollowUpTarget,
    int MonthlyFollowUpAchieved,
    double MonthlyAchievementPercent,
    List<ChartDonutSliceDto> MyLeadStatus,
    List<ChartBarEntryDto> MyLeadTrend,
    List<ChartBarEntryDto> MyFollowUpTrend,
    List<ChartBarEntryDto> MyKpiAchievement,
    List<ChartFunnelStageDto> MyConversionFunnel
);

// ==================== ENTERPRISE CRM REPORTS ====================

public record CrmReportFilterRequest(
    int ReportType,
    DateTime? FromDate = null,
    DateTime? ToDate = null,
    int? OfficeLocationId = null,
    int? ManagerId = null,
    int? UserId = null,
    int? ProductServiceId = null,
    string? LeadStatus = null,
    int? LeadSourceId = null,
    string? Search = null,
    int PageNumber = 1,
    int PageSize = 20
);

public record CrmReportSummary(
    int TotalRows,
    string Summary1Label,
    string Summary1Value,
    string Summary2Label,
    string Summary2Value,
    string Summary3Label,
    string Summary3Value
);

public record CrmReportRow(
    int RowId,
    int EntityId,
    string Title,
    string Subtitle,
    string Tag,
    string Value1,
    string Value2,
    string Value3,
    string Value4,
    string Status,
    DateTime? CreatedAtUtc
);

public record CrmReportResponse(
    int ReportType,
    string ReportTitle,
    CrmReportSummary Summary,
    List<CrmReportRow> Rows,
    int PageNumber,
    int PageSize,
    int TotalPages
);
