using LiveTracking.Api.DTOs;

namespace LiveTracking.Api.Repositories.CRM;

public interface ICrmLeadRepository
{
    Task<CrmLeadResponse?> SaveLeadAsync(
        int companyId,
        int createdByUserId,
        CreateCrmLeadRequest request,
        int? leadId = null,
        string leadSourceType = "Manager",
        int? officeLocationId = null);

    Task<CrmLeadDetailResponse?> GetLeadByIdAsync(
        int companyId,
        int leadId,
        int? restrictedToUserId = null);

    Task<PagedResult<CrmLeadResponse>> GetLeadListAsync(
        int companyId,
        int? userId = null,
        int? assignedUserId = null,
        int? officeLocationId = null,
        string? status = null,
        int? productServiceId = null,
        int? leadSourceId = null,
        string? leadSourceType = null,
        string? search = null,
        DateTime? fromDate = null,
        DateTime? toDate = null,
        int pageNumber = 1,
        int pageSize = 20,
        string? sortBy = "CreatedAt",
        string? sortOrder = "DESC");

    Task<CrmLeadDetailResponse?> AssignLeadAsync(
        int companyId,
        int assignedByUserId,
        int leadId,
        int newUserId,
        string? remarks = null,
        int? officeLocationId = null);

    Task<CrmFollowUpDto?> SaveFollowUpAsync(
        int companyId,
        int leadId,
        int userId,
        CreateFollowUpRequest request,
        int? officeLocationId = null);

    Task<List<CrmFollowUpItemDto>> GetTodayFollowUpsAsync(
        int companyId,
        int? userId = null,
        int? officeLocationId = null);

    Task<List<CrmFollowUpItemDto>> GetOverdueFollowUpsAsync(
        int companyId,
        int? userId = null,
        int? officeLocationId = null);

    Task<List<CrmFollowUpItemDto>> GetUpcomingFollowUpsAsync(
        int companyId,
        int? userId = null,
        int? officeLocationId = null,
        int daysAhead = 30);

    Task<CrmKpiDto?> SaveKpiAsync(
        int companyId,
        int createdByUserId,
        CreateOrUpdateKpiRequest request,
        int? officeLocationId = null);

    Task<ManagerProductivityResponse> GetProductivityAsync(
        int companyId,
        string periodType = "Daily",
        DateTime? fromDate = null,
        DateTime? toDate = null,
        int? officeLocationId = null,
        int? userId = null);

    Task<ManagerDashboardResponse> GetManagerDashboardAsync(
        int companyId,
        int managerUserId,
        int? officeLocationId = null,
        DateTime? fromDate = null,
        DateTime? toDate = null);

    Task<UserDashboardResponse> GetEmployeeDashboardAsync(
        int companyId,
        int userId,
        DateTime? fromDate = null,
        DateTime? toDate = null);
}
