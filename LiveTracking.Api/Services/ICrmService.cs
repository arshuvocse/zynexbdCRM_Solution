using LiveTracking.Api.DTOs;

namespace LiveTracking.Api.Services;

public interface ICrmService
{
    // Authorization
    Task<CrmOfficeScope> GetOfficeScopeAsync(int userId, string role);

    // Products & Services
    Task<List<CrmProductServiceDto>> GetProductServicesAsync(int companyId, bool activeOnly = true);
    Task<CrmProductServiceDto> CreateProductServiceAsync(int companyId, CreateCrmProductServiceRequest request);
    Task<CrmProductServiceDto?> UpdateProductServiceAsync(int companyId, int productServiceId, UpdateCrmProductServiceRequest request);
    Task<bool> DeleteProductServiceAsync(int companyId, int productServiceId);

    // Lead Sources
    Task<List<CrmLeadSourceDto>> GetLeadSourcesAsync(int companyId, bool activeOnly = true);
    Task<CrmLeadSourceDto> CreateLeadSourceAsync(int companyId, CreateCrmLeadSourceRequest request);

    // Manager Endpoints
    Task<ManagerDashboardResponse> GetManagerDashboardAsync(int companyId, CrmOfficeScope officeScope);
    Task<PagedResult<CrmLeadResponse>> GetManagerLeadsAsync(
        int companyId,
        CrmOfficeScope officeScope,
        int? assignedUserId = null,
        string? status = null,
        int? productServiceId = null,
        int? leadSourceId = null,
        DateTime? fromDate = null,
        DateTime? toDate = null,
        string? search = null,
        string? sortBy = null,
        string? sortOrder = null,
        int pageNumber = 1,
        int pageSize = 20);

    Task<CrmLeadDetailResponse?> GetLeadDetailsAsync(int companyId, int leadId, CrmOfficeScope? officeScope = null, int? restrictToUserId = null);
    Task<CrmLeadResponse?> CreateLeadByManagerAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, CreateCrmLeadRequest request);
    Task<CrmLeadResponse?> UpdateLeadByManagerAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, int leadId, UpdateCrmLeadRequest request);
    Task<CrmLeadDetailResponse?> AssignLeadAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, bool isAdmin, int leadId, AssignLeadRequest request);
    Task<List<CrmFollowUpItemDto>> GetManagerFollowUpsAsync(int companyId, CrmOfficeScope officeScope, int? assignedUserId = null, string? filterType = null, DateTime? fromDate = null, DateTime? toDate = null);
    Task<List<CrmLeadResponse>> GetManagerLeadsForExportAsync(
        int companyId,
        CrmOfficeScope officeScope,
        int? assignedUserId = null,
        string? status = null,
        int? productServiceId = null,
        int? leadSourceId = null,
        DateTime? fromDate = null,
        DateTime? toDate = null,
        string? search = null);

    // KPI Management
    Task<List<CrmKpiDto>> GetCompanyKpisAsync(int companyId, CrmOfficeScope officeScope);
    Task<CrmKpiDto?> CreateOrUpdateKpiAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, CreateOrUpdateKpiRequest request);
    Task<ManagerProductivityResponse> GetManagerProductivityAsync(int companyId, CrmOfficeScope officeScope, string periodType = "Daily", DateTime? customFromDate = null, DateTime? customToDate = null, string? sortBy = null, string? sortOrder = null);

    // User / Employee Endpoints
    Task<UserDashboardResponse> GetUserDashboardAsync(int companyId, int userId);
    Task<PagedResult<CrmLeadResponse>> GetUserLeadsAsync(
        int companyId,
        int userId,
        string? status = null,
        int? productServiceId = null,
        int? leadSourceId = null,
        DateTime? fromDate = null,
        DateTime? toDate = null,
        string? search = null,
        string? sortBy = null,
        string? sortOrder = null,
        int pageNumber = 1,
        int pageSize = 20);

    Task<CrmLeadResponse> CreateLeadByUserAsync(int companyId, int userId, CreateCrmLeadRequest request);
    Task<CrmLeadDetailResponse?> UpdateLeadStatusByUserAsync(int companyId, int userId, int leadId, UpdateLeadStatusRequest request);
    Task<CrmFollowUpDto?> AddFollowUpAsync(int companyId, int userId, int leadId, CreateFollowUpRequest request, bool isManagerOrAdmin = false);
    Task<CrmRemarkDto?> AddRemarkAsync(int companyId, int userId, int leadId, CreateRemarkRequest request, bool isManagerOrAdmin = false);
    // Enterprise Dashboard & Analytics
    Task<AdminCrmDashboardResponse> GetAdminDashboardAsync(int companyId, CrmDashboardFilterRequest filters);
    Task<ManagerCrmDashboardResponse> GetManagerCrmDashboardAsync(int companyId, int managerUserId, CrmOfficeScope officeScope, CrmDashboardFilterRequest filters);
    Task<UserCrmDashboardResponse> GetUserCrmDashboardAsync(int companyId, int userId, CrmDashboardFilterRequest filters);

    // Enterprise CRM Reports
    Task<CrmReportResponse> GetAdminReportAsync(int companyId, CrmReportFilterRequest request);
    Task<CrmReportResponse> GetManagerReportAsync(int companyId, int managerUserId, CrmOfficeScope officeScope, CrmReportFilterRequest request);
    Task<CrmReportResponse> GetUserReportAsync(int companyId, int userId, CrmReportFilterRequest request);

    Task<List<CrmFollowUpItemDto>> GetUserFollowUpsAsync(int companyId, int userId, string? filterType = null, DateTime? fromDate = null, DateTime? toDate = null);
    Task<List<UserKpiPerformanceResponse>> GetUserKpiPerformanceAsync(int companyId, int userId);
}
