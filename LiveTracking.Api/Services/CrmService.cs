using System.Data;
using System.Data.Common;
using Microsoft.Data.SqlClient;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Models.CRM;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Services;

public class CrmService : ICrmService
{
    private readonly LiveTrackingDbContext _db;

    public CrmService(LiveTrackingDbContext db)
    {
        _db = db;
    }

    #region Authorization

    /// <summary>
    /// Resolves the office-location authorization scope for a CRM caller, mirroring the
    /// AdminOfficeLocations idiom used elsewhere in the codebase (UsersController,
    /// LocationsController, etc.): explicit AdminOfficeLocations rows first, falling back to
    /// the single User.OfficeLocationId. Admin keeps the existing fail-open behavior (no
    /// assignments = company-wide, for backward compatibility with existing Admin accounts).
    /// Manager fails closed (no assignments = sees nothing) since Managers must always be
    /// office-scoped per the office-isolation requirement. User role is not resolved here -
    /// Users are scoped by lead ownership, not office membership.
    /// </summary>
    public async Task<CrmOfficeScope> GetOfficeScopeAsync(int userId, string role)
    {
        if (userId <= 0) return CrmOfficeScope.None;

        var assignedIds = await _db.AdminOfficeLocations
            .Where(a => a.AdminUserId == userId)
            .Select(a => a.OfficeLocationId)
            .ToListAsync();

        if (assignedIds.Count == 0)
        {
            var singleOfficeId = await _db.Users
                .Where(u => u.UserId == userId)
                .Select(u => u.OfficeLocationId)
                .FirstOrDefaultAsync();

            if (singleOfficeId.HasValue)
            {
                assignedIds.Add(singleOfficeId.Value);
            }
        }

        if (assignedIds.Count > 0)
        {
            return new CrmOfficeScope(assignedIds, false);
        }

        return string.Equals(role, "Admin", StringComparison.OrdinalIgnoreCase)
            ? CrmOfficeScope.Unrestricted
            : CrmOfficeScope.None;
    }

    #endregion

    #region Products & Services

    public async Task<List<CrmProductServiceDto>> GetProductServicesAsync(int companyId, bool activeOnly = true)
    {
        var query = _db.CrmProductServices.AsNoTracking().Where(p => p.CompanyId == companyId);
        if (activeOnly)
        {
            query = query.Where(p => p.IsActive);
        }

        return await query
            .OrderBy(p => p.Name)
            .Select(p => new CrmProductServiceDto(
                p.ProductServiceId,
                p.CompanyId,
                p.Name,
                p.Code,
                p.Description,
                p.Price,
                p.IsActive
            ))
            .ToListAsync();
    }

    public async Task<CrmProductServiceDto> CreateProductServiceAsync(int companyId, CreateCrmProductServiceRequest request)
    {
        var item = new CrmProductService
        {
            CompanyId = companyId,
            Name = request.Name.Trim(),
            Code = request.Code?.Trim(),
            Description = request.Description?.Trim(),
            Price = request.Price,
            IsActive = true,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.CrmProductServices.Add(item);
        await _db.SaveChangesAsync();

        return new CrmProductServiceDto(
            item.ProductServiceId,
            item.CompanyId,
            item.Name,
            item.Code,
            item.Description,
            item.Price,
            item.IsActive
        );
    }

    public async Task<CrmProductServiceDto?> UpdateProductServiceAsync(int companyId, int productServiceId, UpdateCrmProductServiceRequest request)
    {
        var item = await _db.CrmProductServices.FirstOrDefaultAsync(p => p.CompanyId == companyId && p.ProductServiceId == productServiceId);
        if (item == null) return null;

        if (!string.IsNullOrWhiteSpace(request.Name)) item.Name = request.Name.Trim();
        if (request.Code != null) item.Code = request.Code.Trim();
        if (request.Description != null) item.Description = request.Description.Trim();
        if (request.Price.HasValue) item.Price = request.Price.Value;
        if (request.IsActive.HasValue) item.IsActive = request.IsActive.Value;
        item.UpdatedAtUtc = DateTime.UtcNow;

        await _db.SaveChangesAsync();

        return new CrmProductServiceDto(
            item.ProductServiceId,
            item.CompanyId,
            item.Name,
            item.Code,
            item.Description,
            item.Price,
            item.IsActive
        );
    }

    public async Task<bool> DeleteProductServiceAsync(int companyId, int productServiceId)
    {
        var item = await _db.CrmProductServices.FirstOrDefaultAsync(p => p.CompanyId == companyId && p.ProductServiceId == productServiceId);
        if (item == null) return false;

        item.IsActive = false;
        item.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();
        return true;
    }

    #endregion

    #region Lead Sources

    public async Task<List<CrmLeadSourceDto>> GetLeadSourcesAsync(int companyId, bool activeOnly = true)
    {
        var query = _db.CrmLeadSources.AsNoTracking().Where(s => s.CompanyId == companyId);
        if (activeOnly)
        {
            query = query.Where(s => s.IsActive);
        }

        return await query
            .OrderBy(s => s.Name)
            .Select(s => new CrmLeadSourceDto(
                s.LeadSourceId,
                s.CompanyId,
                s.Name,
                s.IsSystem,
                s.IsActive
            ))
            .ToListAsync();
    }

    public async Task<CrmLeadSourceDto> CreateLeadSourceAsync(int companyId, CreateCrmLeadSourceRequest request)
    {
        var item = new CrmLeadSource
        {
            CompanyId = companyId,
            Name = request.Name.Trim(),
            IsSystem = false,
            IsActive = true,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.CrmLeadSources.Add(item);
        await _db.SaveChangesAsync();

        return new CrmLeadSourceDto(
            item.LeadSourceId,
            item.CompanyId,
            item.Name,
            item.IsSystem,
            item.IsActive
        );
    }

    #endregion

    #region Manager Operations

    public async Task<ManagerDashboardResponse> GetManagerDashboardAsync(int companyId, CrmOfficeScope officeScope)
    {
        var now = DateTime.UtcNow;
        var todayStart = now.Date;
        var todayEnd = todayStart.AddDays(1);

        var leadsQuery = ApplyOfficeFilter(_db.CrmLeads.AsNoTracking().Where(l => l.CompanyId == companyId && l.IsActive), officeScope);

        int totalLeads = await leadsQuery.CountAsync();
        int newLeads = await leadsQuery.CountAsync(l => l.LeadStatus == "New Lead");
        int followUpLeads = await leadsQuery.CountAsync(l => l.LeadStatus == "Follow Up");
        int interestedLeads = await leadsQuery.CountAsync(l => l.LeadStatus == "Interested");
        int notInterestedLeads = await leadsQuery.CountAsync(l => l.LeadStatus == "Not Interested");
        int closedLeads = await leadsQuery.CountAsync(l => l.LeadStatus == "Closed");

        int todayFollowUps = await leadsQuery.CountAsync(l =>
            l.NextFollowUpDate.HasValue &&
            l.NextFollowUpDate.Value >= todayStart &&
            l.NextFollowUpDate.Value < todayEnd &&
            l.LeadStatus != "Closed" && l.LeadStatus != "Not Interested");

        int overdueFollowUps = await leadsQuery.CountAsync(l =>
            l.NextFollowUpDate.HasValue &&
            l.NextFollowUpDate.Value < todayStart &&
            l.LeadStatus != "Closed" && l.LeadStatus != "Not Interested");

        // Employees Performance in this company (office-scoped)
        var employeesQuery = _db.Users.AsNoTracking()
            .Where(u => u.CompanyId == companyId && u.Role != "Admin" && u.IsActive);
        if (!officeScope.IsUnrestricted)
        {
            employeesQuery = employeesQuery.Where(u => u.OfficeLocationId.HasValue && officeScope.OfficeIds.Contains(u.OfficeLocationId.Value));
        }
        var employees = await employeesQuery.OrderBy(u => u.FullName.Length > 0 ? u.FullName : u.Username).ToListAsync();

        var employeeSummaryList = new List<EmployeePerformanceSummaryDto>();

        // Default KPI
        var defaultDailyKpi = await _db.CrmKpis.AsNoTracking()
            .FirstOrDefaultAsync(k => k.CompanyId == companyId && k.UserId == null && k.PeriodType == "Daily" && k.IsActive);

        var defaultTarget = defaultDailyKpi?.FollowUpTarget ?? 30;

        foreach (var emp in employees)
        {
            int empTotalLeads = await leadsQuery.CountAsync(l => l.AssignedUserId == emp.UserId || (l.CreatedByUserId == emp.UserId && l.AssignedUserId == null));
            int empFollowUpsToday = await _db.CrmLeadFollowUps.AsNoTracking()
                .CountAsync(f => f.CompanyId == companyId && f.CreatedByUserId == emp.UserId && f.FollowUpDateUtc >= todayStart && f.FollowUpDateUtc < todayEnd);

            int empInterestedToday = await _db.CrmLeadFollowUps.AsNoTracking()
                .CountAsync(f => f.CompanyId == companyId && f.CreatedByUserId == emp.UserId && f.Status == "Interested" && f.FollowUpDateUtc >= todayStart && f.FollowUpDateUtc < todayEnd);

            int empClosedToday = await _db.CrmLeadFollowUps.AsNoTracking()
                .CountAsync(f => f.CompanyId == companyId && f.CreatedByUserId == emp.UserId && f.Status == "Closed" && f.FollowUpDateUtc >= todayStart && f.FollowUpDateUtc < todayEnd);

            var empKpi = await _db.CrmKpis.AsNoTracking()
                .FirstOrDefaultAsync(k => k.CompanyId == companyId && k.UserId == emp.UserId && k.PeriodType == "Daily" && k.IsActive);

            int target = empKpi?.FollowUpTarget ?? defaultTarget;
            double achievementPercent = target > 0 ? Math.Round((double)empFollowUpsToday / target * 100.0, 2) : 100.0;

            employeeSummaryList.Add(new EmployeePerformanceSummaryDto(
                emp.UserId,
                !string.IsNullOrEmpty(emp.FullName) ? emp.FullName : emp.Username,
                empTotalLeads,
                empFollowUpsToday,
                empInterestedToday,
                empClosedToday,
                achievementPercent
            ));
        }

        return new ManagerDashboardResponse(
            totalLeads,
            newLeads,
            followUpLeads,
            interestedLeads,
            notInterestedLeads,
            closedLeads,
            todayFollowUps,
            overdueFollowUps,
            employeeSummaryList
        );
    }

    public async Task<PagedResult<CrmLeadResponse>> GetManagerLeadsAsync(
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
        int pageSize = 20)
    {
        if (pageNumber < 1) pageNumber = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;

        var query = ApplyOfficeFilter(_db.CrmLeads.AsNoTracking()
            .Include(l => l.ProductService)
            .Include(l => l.LeadSource)
            .Include(l => l.CreatedByUser)
            .Include(l => l.AssignedUser)
            .Include(l => l.OfficeLocation)
            .Where(l => l.CompanyId == companyId && l.IsActive), officeScope);

        query = ApplyLeadListFilters(query, assignedUserId, status, productServiceId, leadSourceId, fromDate, toDate, search);

        bool isAsc = string.Equals(sortOrder, "asc", StringComparison.OrdinalIgnoreCase);
        query = (sortBy?.ToLower()) switch
        {
            "leadname" => isAsc ? query.OrderBy(l => l.LeadName) : query.OrderByDescending(l => l.LeadName),
            "status" => isAsc ? query.OrderBy(l => l.LeadStatus) : query.OrderByDescending(l => l.LeadStatus),
            "nextfollowupdate" => isAsc ? query.OrderBy(l => l.NextFollowUpDate) : query.OrderByDescending(l => l.NextFollowUpDate),
            "assigneduser" => isAsc ? query.OrderBy(l => l.AssignedUser != null ? l.AssignedUser.FullName : "") : query.OrderByDescending(l => l.AssignedUser != null ? l.AssignedUser.FullName : ""),
            _ => isAsc ? query.OrderBy(l => l.CreatedAtUtc) : query.OrderByDescending(l => l.CreatedAtUtc)
        };

        int totalRecords = await query.CountAsync();
        int totalPages = (int)Math.Ceiling(totalRecords / (double)pageSize);

        var entities = await query
            .Skip((pageNumber - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();
        var items = entities.Select(ToLeadResponse).ToList();

        return new PagedResult<CrmLeadResponse>(items, totalRecords, pageNumber, pageSize, totalPages);
    }

    public async Task<List<CrmLeadResponse>> GetManagerLeadsForExportAsync(
        int companyId,
        CrmOfficeScope officeScope,
        int? assignedUserId = null,
        string? status = null,
        int? productServiceId = null,
        int? leadSourceId = null,
        DateTime? fromDate = null,
        DateTime? toDate = null,
        string? search = null)
    {
        const int exportRowCap = 5000;

        var query = ApplyOfficeFilter(_db.CrmLeads.AsNoTracking()
            .Include(l => l.ProductService)
            .Include(l => l.LeadSource)
            .Include(l => l.CreatedByUser)
            .Include(l => l.AssignedUser)
            .Include(l => l.OfficeLocation)
            .Where(l => l.CompanyId == companyId && l.IsActive), officeScope);

        query = ApplyLeadListFilters(query, assignedUserId, status, productServiceId, leadSourceId, fromDate, toDate, search);

        var entities = await query
            .OrderByDescending(l => l.CreatedAtUtc)
            .Take(exportRowCap)
            .ToListAsync();
        return entities.Select(ToLeadResponse).ToList();
    }

    public async Task<CrmLeadDetailResponse?> GetLeadDetailsAsync(int companyId, int leadId, CrmOfficeScope? officeScope = null, int? restrictToUserId = null)
    {
        var query = _db.CrmLeads.AsNoTracking()
            .Include(l => l.ProductService)
            .Include(l => l.LeadSource)
            .Include(l => l.CreatedByUser)
            .Include(l => l.AssignedUser)
            .Include(l => l.OfficeLocation)
            .Where(l => l.CompanyId == companyId && l.LeadId == leadId && l.IsActive);

        if (officeScope != null)
        {
            query = ApplyOfficeFilter(query, officeScope);
        }

        if (restrictToUserId.HasValue && restrictToUserId.Value > 0)
        {
            query = query.Where(l => l.AssignedUserId == restrictToUserId.Value || l.CreatedByUserId == restrictToUserId.Value);
        }

        var lead = await query.FirstOrDefaultAsync();
        if (lead == null) return null;

        var followUps = await _db.CrmLeadFollowUps.AsNoTracking()
            .Include(f => f.CreatedByUser)
            .Where(f => f.CompanyId == companyId && f.LeadId == leadId)
            .OrderByDescending(f => f.FollowUpDateUtc)
            .Select(f => new CrmFollowUpDto(
                f.FollowUpId,
                f.LeadId,
                f.FollowUpDateUtc,
                f.NextFollowUpDate,
                f.Status,
                f.Remarks,
                f.CreatedByUserId,
                f.CreatedByUser != null ? (f.CreatedByUser.FullName.Length > 0 ? f.CreatedByUser.FullName : f.CreatedByUser.Username) : null,
                f.CreatedAtUtc
            ))
            .ToListAsync();

        var remarks = await _db.CrmLeadRemarks.AsNoTracking()
            .Include(r => r.User)
            .Where(r => r.CompanyId == companyId && r.LeadId == leadId)
            .OrderByDescending(r => r.CreatedAtUtc)
            .Select(r => new CrmRemarkDto(
                r.RemarkId,
                r.LeadId,
                r.UserId,
                r.User != null ? (r.User.FullName.Length > 0 ? r.User.FullName : r.User.Username) : null,
                r.Remark,
                r.CreatedAtUtc
            ))
            .ToListAsync();

        var assignments = await _db.CrmLeadAssignments.AsNoTracking()
            .Include(a => a.PreviousUser)
            .Include(a => a.NewUser)
            .Include(a => a.AssignedByUser)
            .Where(a => a.CompanyId == companyId && a.LeadId == leadId)
            .OrderByDescending(a => a.AssignedDateUtc)
            .Select(a => new CrmLeadAssignmentDto(
                a.AssignmentId,
                a.LeadId,
                a.PreviousUserId,
                a.PreviousUser != null ? (a.PreviousUser.FullName.Length > 0 ? a.PreviousUser.FullName : a.PreviousUser.Username) : null,
                a.NewUserId,
                a.NewUser != null ? (a.NewUser.FullName.Length > 0 ? a.NewUser.FullName : a.NewUser.Username) : null,
                a.AssignedByUserId,
                a.AssignedByUser != null ? (a.AssignedByUser.FullName.Length > 0 ? a.AssignedByUser.FullName : a.AssignedByUser.Username) : null,
                a.AssignedDateUtc,
                a.Remarks
            ))
            .ToListAsync();

        var statusHistory = await _db.CrmLeadStatusHistories.AsNoTracking()
            .Include(h => h.ChangedByUser)
            .Where(h => h.CompanyId == companyId && h.LeadId == leadId)
            .OrderByDescending(h => h.ChangedDateUtc)
            .Select(h => new CrmStatusHistoryDto(
                h.StatusHistoryId,
                h.LeadId,
                h.PreviousStatus,
                h.NewStatus,
                h.ChangedByUserId,
                h.ChangedByUser != null ? (h.ChangedByUser.FullName.Length > 0 ? h.ChangedByUser.FullName : h.ChangedByUser.Username) : null,
                h.ChangedDateUtc,
                h.Remarks
            ))
            .ToListAsync();

        var auditLog = await _db.CrmAuditLogs.AsNoTracking()
            .Include(a => a.User)
            .Where(a => a.CompanyId == companyId && a.EntityType == "Lead" && a.EntityId == leadId)
            .OrderByDescending(a => a.CreatedAtUtc)
            .Select(a => new CrmAuditLogDto(
                a.AuditLogId,
                a.UserId,
                a.User != null ? (a.User.FullName.Length > 0 ? a.User.FullName : a.User.Username) : null,
                a.Action,
                a.EntityType,
                a.EntityId,
                a.OldValue,
                a.NewValue,
                a.CreatedAtUtc
            ))
            .ToListAsync();

        return new CrmLeadDetailResponse(
            lead.LeadId,
            lead.CompanyId,
            lead.LeadName,
            lead.ContactPerson,
            lead.Phone,
            lead.Email,
            lead.Address,
            lead.ProductServiceId,
            lead.ProductService?.Name,
            lead.LeadSourceId,
            lead.LeadSource?.Name,
            lead.LeadSourceType,
            lead.LeadStatus,
            lead.CreatedByUserId,
            lead.CreatedByUser != null ? (lead.CreatedByUser.FullName.Length > 0 ? lead.CreatedByUser.FullName : lead.CreatedByUser.Username) : null,
            lead.AssignedUserId,
            lead.AssignedUser != null ? (lead.AssignedUser.FullName.Length > 0 ? lead.AssignedUser.FullName : lead.AssignedUser.Username) : null,
            lead.NextFollowUpDate,
            lead.LastFollowUpDate,
            lead.EstimatedValue,
            lead.Remarks,
            lead.IsActive,
            lead.CreatedAtUtc,
            lead.UpdatedAtUtc,
            lead.OfficeLocationId,
            lead.OfficeLocation?.Name,
            followUps,
            remarks,
            assignments,
            statusHistory,
            auditLog
        );
    }

    public async Task<CrmLeadResponse?> CreateLeadByManagerAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, CreateCrmLeadRequest request)
    {
        // Verify assigned employee belongs to company AND to an office the caller is authorized for
        int? assignedUserId = null;
        int? assignedUserOfficeId = null;
        if (request.AssignedUserId.HasValue && request.AssignedUserId.Value > 0)
        {
            var emp = await _db.Users.FirstOrDefaultAsync(u => u.CompanyId == companyId && u.UserId == request.AssignedUserId.Value && u.IsActive);
            if (emp == null) return null;
            if (!officeScope.Allows(emp.OfficeLocationId)) return null; // cross-office assignment blocked

            assignedUserId = emp.UserId;
            assignedUserOfficeId = emp.OfficeLocationId;
        }

        string sourceType = assignedUserId.HasValue ? "Assigned" : "Manager";

        // A lead not yet assigned takes the creating manager's own office (if any) so it stays
        // visible to them; assignment (here or later) always re-derives the office from the assignee.
        int? creatorOfficeId = await _db.Users.Where(u => u.UserId == adminUserId).Select(u => u.OfficeLocationId).FirstOrDefaultAsync();
        int? leadOfficeId = assignedUserOfficeId ?? creatorOfficeId;

        var lead = new CrmLead
        {
            CompanyId = companyId,
            LeadName = request.LeadName.Trim(),
            ContactPerson = request.ContactPerson?.Trim(),
            Phone = request.Phone?.Trim(),
            Email = request.Email?.Trim(),
            Address = request.Address?.Trim(),
            ProductServiceId = request.ProductServiceId is > 0 ? request.ProductServiceId : null,
            LeadSourceId = request.LeadSourceId is > 0 ? request.LeadSourceId : null,
            LeadSourceType = sourceType,
            LeadStatus = !string.IsNullOrWhiteSpace(request.LeadStatus) ? request.LeadStatus.Trim() : "New Lead",
            CreatedByUserId = adminUserId,
            AssignedUserId = assignedUserId,
            OfficeLocationId = leadOfficeId,
            NextFollowUpDate = request.NextFollowUpDate,
            EstimatedValue = request.EstimatedValue,
            Remarks = request.Remarks?.Trim(),
            IsActive = true,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.CrmLeads.Add(lead);
        await _db.SaveChangesAsync();
        LogAudit(companyId, adminUserId, "LeadCreated", "Lead", lead.LeadId, null, lead.LeadName);

        if (assignedUserId.HasValue)
        {
            _db.CrmLeadAssignments.Add(new CrmLeadAssignment
            {
                CompanyId = companyId,
                LeadId = lead.LeadId,
                PreviousUserId = null,
                NewUserId = assignedUserId.Value,
                AssignedByUserId = adminUserId,
                AssignedDateUtc = DateTime.UtcNow,
                OfficeLocationId = leadOfficeId,
                Remarks = "Initial lead assignment by Manager"
            });
            LogAudit(companyId, adminUserId, "LeadAssigned", "Lead", lead.LeadId, null, assignedUserId.Value.ToString());
            await _db.SaveChangesAsync();
        }

        if (!string.IsNullOrWhiteSpace(request.Remarks))
        {
            _db.CrmLeadRemarks.Add(new CrmLeadRemark
            {
                CompanyId = companyId,
                LeadId = lead.LeadId,
                UserId = adminUserId,
                Remark = request.Remarks.Trim(),
                CreatedAtUtc = DateTime.UtcNow
            });
            await _db.SaveChangesAsync();
        }

        await _db.SaveChangesAsync();
        return await GetLeadResponseByIdAsync(companyId, lead.LeadId);
    }

    public async Task<CrmLeadResponse?> UpdateLeadByManagerAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, int leadId, UpdateCrmLeadRequest request)
    {
        var lead = await _db.CrmLeads.FirstOrDefaultAsync(l => l.CompanyId == companyId && l.LeadId == leadId);
        if (lead == null) return null;
        if (!officeScope.Allows(lead.OfficeLocationId)) return null; // out of caller's authorized offices - treat as not found

        if (!string.IsNullOrWhiteSpace(request.LeadName)) lead.LeadName = request.LeadName.Trim();
        if (request.ContactPerson != null) lead.ContactPerson = request.ContactPerson.Trim();
        if (request.Phone != null) lead.Phone = request.Phone.Trim();
        if (request.Email != null) lead.Email = request.Email.Trim();
        if (request.Address != null) lead.Address = request.Address.Trim();
        if (request.ProductServiceId.HasValue) lead.ProductServiceId = request.ProductServiceId > 0 ? request.ProductServiceId : null;
        if (request.LeadSourceId.HasValue) lead.LeadSourceId = request.LeadSourceId > 0 ? request.LeadSourceId : null;
        if (!string.IsNullOrWhiteSpace(request.LeadStatus))
        {
            var previousStatus = lead.LeadStatus;
            lead.LeadStatus = request.LeadStatus.Trim();
            LogStatusChangeIfNeeded(companyId, lead.LeadId, previousStatus, lead.LeadStatus, adminUserId);
        }
        if (request.NextFollowUpDate.HasValue) lead.NextFollowUpDate = request.NextFollowUpDate;
        if (request.EstimatedValue.HasValue) lead.EstimatedValue = request.EstimatedValue;
        if (request.Remarks != null) lead.Remarks = request.Remarks.Trim();
        if (request.IsActive.HasValue) lead.IsActive = request.IsActive.Value;

        // Handle reassignment if requested - target must be same-company and in an authorized office
        if (request.AssignedUserId.HasValue && request.AssignedUserId.Value != lead.AssignedUserId)
        {
            int newUserId = request.AssignedUserId.Value;
            if (newUserId == 0)
            {
                lead.AssignedUserId = null;
            }
            else
            {
                var targetEmployee = await _db.Users.FirstOrDefaultAsync(u => u.CompanyId == companyId && u.UserId == newUserId && u.IsActive);
                if (targetEmployee != null && officeScope.Allows(targetEmployee.OfficeLocationId))
                {
                    int? prev = lead.AssignedUserId;
                    lead.AssignedUserId = newUserId;
                    lead.OfficeLocationId = targetEmployee.OfficeLocationId;

                    _db.CrmLeadAssignments.Add(new CrmLeadAssignment
                    {
                        CompanyId = companyId,
                        LeadId = lead.LeadId,
                        PreviousUserId = prev,
                        NewUserId = newUserId,
                        AssignedByUserId = adminUserId,
                        AssignedDateUtc = DateTime.UtcNow,
                        OfficeLocationId = targetEmployee.OfficeLocationId,
                        Remarks = "Lead updated by Manager"
                    });
                    LogAudit(companyId, adminUserId, prev.HasValue ? "LeadReassigned" : "LeadAssigned", "Lead", lead.LeadId, prev?.ToString(), newUserId.ToString());
                }
                // else: target user not found or not in an authorized office - reassignment silently skipped,
                // rest of the update still applies (matches prior "ignore invalid target" behavior).
            }
        }

        lead.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();

        return await GetLeadResponseByIdAsync(companyId, lead.LeadId);
    }

    public async Task<CrmLeadDetailResponse?> AssignLeadAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, bool isAdmin, int leadId, AssignLeadRequest request)
    {
        var lead = await _db.CrmLeads.FirstOrDefaultAsync(l => l.CompanyId == companyId && l.LeadId == leadId && l.IsActive);
        if (lead == null) return null;
        if (!officeScope.Allows(lead.OfficeLocationId)) return null;

        var targetEmployee = await _db.Users.FirstOrDefaultAsync(u => u.CompanyId == companyId && u.UserId == request.NewUserId && u.IsActive);
        if (targetEmployee == null) return null; // Cross-tenant security check: employee must belong to same company

        // Cross-office assignment requires Admin + explicit opt-in; Managers can never cross offices
        // regardless of what the request body claims (role is authoritative, not the client flag).
        bool allowCrossOffice = isAdmin && request.AllowCrossOffice;
        if (!allowCrossOffice && !officeScope.Allows(targetEmployee.OfficeLocationId)) return null;

        int? prevUserId = lead.AssignedUserId;
        lead.AssignedUserId = targetEmployee.UserId;
        lead.OfficeLocationId = targetEmployee.OfficeLocationId;
        lead.LeadSourceType = "Assigned";
        lead.UpdatedAtUtc = DateTime.UtcNow;

        _db.CrmLeadAssignments.Add(new CrmLeadAssignment
        {
            CompanyId = companyId,
            LeadId = lead.LeadId,
            PreviousUserId = prevUserId,
            NewUserId = targetEmployee.UserId,
            AssignedByUserId = adminUserId,
            AssignedDateUtc = DateTime.UtcNow,
            OfficeLocationId = targetEmployee.OfficeLocationId,
            Remarks = !string.IsNullOrWhiteSpace(request.Remarks) ? request.Remarks.Trim() : "Assigned by Manager"
        });
        LogAudit(companyId, adminUserId, prevUserId.HasValue ? "LeadReassigned" : "LeadAssigned", "Lead", lead.LeadId, prevUserId?.ToString(), targetEmployee.UserId.ToString());

        await _db.SaveChangesAsync();

        // Read back without re-applying the pre-assignment office scope: the assignment itself
        // was already authorized above (including the deliberate cross-office Admin override),
        // and the lead's office may have just changed as a result - showing the caller the
        // outcome of their own authorized action is correct, not a scope leak.
        return await GetLeadDetailsAsync(companyId, leadId);
    }

    public async Task<List<CrmFollowUpItemDto>> GetManagerFollowUpsAsync(
        int companyId,
        CrmOfficeScope officeScope,
        int? assignedUserId = null,
        string? filterType = null,
        DateTime? fromDate = null,
        DateTime? toDate = null)
    {
        var now = DateTime.UtcNow;
        var todayStart = now.Date;
        var todayEnd = todayStart.AddDays(1);

        var query = ApplyOfficeFilter(_db.CrmLeads.AsNoTracking()
            .Include(l => l.ProductService)
            .Include(l => l.AssignedUser)
            .Include(l => l.OfficeLocation)
            .Where(l => l.CompanyId == companyId && l.IsActive && l.NextFollowUpDate.HasValue && l.LeadStatus != "Closed" && l.LeadStatus != "Not Interested"), officeScope);

        if (assignedUserId.HasValue && assignedUserId.Value > 0)
        {
            query = query.Where(l => l.AssignedUserId == assignedUserId.Value);
        }

        switch (filterType?.ToLower())
        {
            case "today":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayEnd);
                break;
            case "tomorrow":
                query = query.Where(l => l.NextFollowUpDate >= todayEnd && l.NextFollowUpDate < todayEnd.AddDays(1));
                break;
            case "next7days":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayStart.AddDays(7));
                break;
            case "next15days":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayStart.AddDays(15));
                break;
            case "next30days":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayStart.AddDays(30));
                break;
            case "overdue":
                query = query.Where(l => l.NextFollowUpDate < todayStart);
                break;
            case "custom":
                if (fromDate.HasValue) query = query.Where(l => l.NextFollowUpDate >= fromDate.Value.ToUniversalTime());
                if (toDate.HasValue) query = query.Where(l => l.NextFollowUpDate <= toDate.Value.ToUniversalTime());
                break;
        }

        var leads = await query.OrderBy(l => l.NextFollowUpDate).ToListAsync();

        return leads.Select(l => ToFollowUpItem(l, todayStart)).ToList();
    }

    #endregion

    #region KPI Management

    public async Task<List<CrmKpiDto>> GetCompanyKpisAsync(int companyId, CrmOfficeScope officeScope)
    {
        var query = _db.CrmKpis.AsNoTracking()
            .Include(k => k.User)
            .Include(k => k.OfficeLocation)
            .Where(k => k.CompanyId == companyId && k.IsActive);

        if (!officeScope.IsUnrestricted)
        {
            // A KPI row is visible if it targets a specific office in scope, a specific user in
            // scope, or is the true company-wide default (no user, no office) - the latter is
            // informational only for a Manager, they cannot edit it (enforced in CreateOrUpdateKpiAsync).
            query = query.Where(k =>
                (k.OfficeLocationId.HasValue && officeScope.OfficeIds.Contains(k.OfficeLocationId.Value)) ||
                (k.UserId.HasValue && k.User != null && k.User.OfficeLocationId.HasValue && officeScope.OfficeIds.Contains(k.User.OfficeLocationId.Value)) ||
                (k.UserId == null && k.OfficeLocationId == null));
        }

        return await query
            .OrderBy(k => k.UserId.HasValue ? 1 : 0)
            .ThenBy(k => k.PeriodType)
            .Select(k => new CrmKpiDto(
                k.KpiId,
                k.CompanyId,
                k.UserId,
                k.User != null
                    ? (k.User.FullName.Length > 0 ? k.User.FullName : k.User.Username)
                    : (k.OfficeLocation != null ? k.OfficeLocation.Name + " Default" : "Company Default"),
                k.PeriodType,
                k.FollowUpTarget,
                k.InterestedTarget,
                k.ClosedTarget,
                k.EffectiveStartDate,
                k.IsActive,
                k.CreatedByUserId,
                k.CreatedAtUtc,
                k.OfficeLocationId,
                k.OfficeLocation != null ? k.OfficeLocation.Name : null
            ))
            .ToListAsync();
    }

    public async Task<CrmKpiDto?> CreateOrUpdateKpiAsync(int companyId, int adminUserId, CrmOfficeScope officeScope, CreateOrUpdateKpiRequest request)
    {
        var period = !string.IsNullOrWhiteSpace(request.PeriodType) ? request.PeriodType.Trim() : "Daily";

        int? userId = null;
        int? officeLocationId = null;

        if (request.UserId.HasValue && request.UserId.Value > 0)
        {
            var emp = await _db.Users.FirstOrDefaultAsync(u => u.CompanyId == companyId && u.UserId == request.UserId.Value);
            if (emp == null || !officeScope.Allows(emp.OfficeLocationId)) return null;
            userId = emp.UserId;
            officeLocationId = emp.OfficeLocationId; // office always derived from the target employee
        }
        else if (request.OfficeLocationId.HasValue && request.OfficeLocationId.Value > 0)
        {
            if (!officeScope.Allows(request.OfficeLocationId.Value)) return null;
            officeLocationId = request.OfficeLocationId.Value;
        }
        else
        {
            // True company-wide default (no user, no office) - only an unrestricted caller
            // (Admin with no office assignments) may set this; a scoped Manager must target
            // either a specific employee or one of their authorized offices.
            if (!officeScope.IsUnrestricted) return null;
        }

        var existing = await _db.CrmKpis.FirstOrDefaultAsync(k =>
            k.CompanyId == companyId &&
            k.UserId == userId &&
            k.OfficeLocationId == officeLocationId &&
            k.PeriodType == period &&
            k.IsActive);

        CrmKpi kpi;
        if (existing != null)
        {
            string oldValue = $"FollowUp={existing.FollowUpTarget},Interested={existing.InterestedTarget},Closed={existing.ClosedTarget}";
            existing.FollowUpTarget = request.FollowUpTarget;
            existing.InterestedTarget = request.InterestedTarget;
            existing.ClosedTarget = request.ClosedTarget;
            existing.UpdatedAtUtc = DateTime.UtcNow;
            string newValue = $"FollowUp={existing.FollowUpTarget},Interested={existing.InterestedTarget},Closed={existing.ClosedTarget}";
            LogAudit(companyId, adminUserId, "KpiUpdated", "Kpi", existing.KpiId, oldValue, newValue);
            await _db.SaveChangesAsync();
            kpi = existing;
        }
        else
        {
            kpi = new CrmKpi
            {
                CompanyId = companyId,
                UserId = userId,
                OfficeLocationId = officeLocationId,
                PeriodType = period,
                FollowUpTarget = request.FollowUpTarget,
                InterestedTarget = request.InterestedTarget,
                ClosedTarget = request.ClosedTarget,
                EffectiveStartDate = DateTime.UtcNow,
                IsActive = true,
                CreatedByUserId = adminUserId,
                CreatedAtUtc = DateTime.UtcNow
            };

            _db.CrmKpis.Add(kpi);
            await _db.SaveChangesAsync();
            LogAudit(companyId, adminUserId, "KpiCreated", "Kpi", kpi.KpiId, null, $"FollowUp={kpi.FollowUpTarget},Interested={kpi.InterestedTarget},Closed={kpi.ClosedTarget}");
            await _db.SaveChangesAsync();
        }

        string? officeName = officeLocationId.HasValue
            ? await _db.OfficeLocations.Where(o => o.OfficeLocationId == officeLocationId.Value).Select(o => o.Name).FirstOrDefaultAsync()
            : null;
        string? userName = userId.HasValue
            ? await _db.Users.Where(u => u.UserId == userId.Value).Select(u => u.FullName.Length > 0 ? u.FullName : u.Username).FirstOrDefaultAsync()
            : (officeLocationId.HasValue ? $"{officeName} Default" : "Company Default");

        return new CrmKpiDto(
            kpi.KpiId,
            kpi.CompanyId,
            kpi.UserId,
            userName,
            kpi.PeriodType,
            kpi.FollowUpTarget,
            kpi.InterestedTarget,
            kpi.ClosedTarget,
            kpi.EffectiveStartDate,
            kpi.IsActive,
            kpi.CreatedByUserId,
            kpi.CreatedAtUtc,
            kpi.OfficeLocationId,
            officeName
        );
    }

    public async Task<ManagerProductivityResponse> GetManagerProductivityAsync(
        int companyId,
        CrmOfficeScope officeScope,
        string periodType = "Daily",
        DateTime? customFromDate = null,
        DateTime? customToDate = null,
        string? sortBy = null,
        string? sortOrder = null)
    {
        var now = DateTime.UtcNow;
        DateTime fromDate;
        DateTime toDate;

        switch (periodType?.ToLower())
        {
            case "weekly":
            case "thisweek":
                int diff = (7 + (now.DayOfWeek - DayOfWeek.Monday)) % 7;
                fromDate = now.Date.AddDays(-1 * diff);
                toDate = fromDate.AddDays(7);
                periodType = "Weekly";
                break;
            case "monthly":
            case "thismonth":
                fromDate = new DateTime(now.Year, now.Month, 1, 0, 0, 0, DateTimeKind.Utc);
                toDate = fromDate.AddMonths(1);
                periodType = "Monthly";
                break;
            case "custom":
                fromDate = customFromDate?.ToUniversalTime() ?? now.Date;
                toDate = customToDate?.ToUniversalTime() ?? now.Date.AddDays(1);
                periodType = "Custom";
                break;
            case "daily":
            case "today":
            default:
                fromDate = now.Date;
                toDate = fromDate.AddDays(1);
                periodType = "Daily";
                break;
        }

        var employeesQuery = _db.Users.AsNoTracking()
            .Include(u => u.OfficeLocation)
            .Where(u => u.CompanyId == companyId && u.Role != "Admin" && u.IsActive);
        if (!officeScope.IsUnrestricted)
        {
            employeesQuery = employeesQuery.Where(u => u.OfficeLocationId.HasValue && officeScope.OfficeIds.Contains(u.OfficeLocationId.Value));
        }
        var employees = await employeesQuery.ToListAsync();

        var companyDefaultKpi = await _db.CrmKpis.AsNoTracking()
            .FirstOrDefaultAsync(k => k.CompanyId == companyId && k.UserId == null && k.OfficeLocationId == null && k.PeriodType == periodType && k.IsActive);

        var officeKpis = await _db.CrmKpis.AsNoTracking()
            .Where(k => k.CompanyId == companyId && k.UserId == null && k.OfficeLocationId != null && k.PeriodType == periodType && k.IsActive)
            .ToDictionaryAsync(k => k.OfficeLocationId!.Value, k => k);

        var employeeSpecificKpis = await _db.CrmKpis.AsNoTracking()
            .Where(k => k.CompanyId == companyId && k.UserId != null && k.PeriodType == periodType && k.IsActive)
            .ToDictionaryAsync(k => k.UserId!.Value, k => k);

        // Fetch followups in the date range for all employees
        var followUpsInRange = await _db.CrmLeadFollowUps.AsNoTracking()
            .Where(f => f.CompanyId == companyId && f.FollowUpDateUtc >= fromDate && f.FollowUpDateUtc < toDate)
            .ToListAsync();

        var resultList = new List<EmployeeProductivityItemDto>();

        foreach (var emp in employees)
        {
            var empFollowUps = followUpsInRange.Where(f => f.CreatedByUserId == emp.UserId).ToList();
            int followUpsDone = empFollowUps.Count;
            int interestedDone = empFollowUps.Count(f => f.Status == "Interested");
            int closedDone = empFollowUps.Count(f => f.Status == "Closed");

            CrmKpi? kpi = employeeSpecificKpis.TryGetValue(emp.UserId, out var ek) ? ek
                : (emp.OfficeLocationId.HasValue && officeKpis.TryGetValue(emp.OfficeLocationId.Value, out var ok) ? ok
                : companyDefaultKpi);

            int followUpTarget = kpi?.FollowUpTarget ?? (periodType == "Weekly" ? 150 : (periodType == "Monthly" ? 600 : 30));
            int interestedTarget = kpi?.InterestedTarget ?? (periodType == "Weekly" ? 100 : (periodType == "Monthly" ? 300 : 20));
            int closedTarget = kpi?.ClosedTarget ?? (periodType == "Weekly" ? 50 : (periodType == "Monthly" ? 100 : 10));

            double achievementPercent = followUpTarget > 0
                ? Math.Round((double)followUpsDone / followUpTarget * 100.0, 2)
                : 100.0;

            resultList.Add(new EmployeeProductivityItemDto(
                emp.UserId,
                !string.IsNullOrEmpty(emp.FullName) ? emp.FullName : emp.Username,
                followUpTarget,
                followUpsDone,
                interestedTarget,
                interestedDone,
                closedTarget,
                closedDone,
                achievementPercent,
                emp.OfficeLocationId,
                emp.OfficeLocation?.Name
            ));
        }

        bool isAsc = string.Equals(sortOrder, "asc", StringComparison.OrdinalIgnoreCase);
        resultList = (sortBy?.ToLower()) switch
        {
            "name" => isAsc ? resultList.OrderBy(r => r.EmployeeName).ToList() : resultList.OrderByDescending(r => r.EmployeeName).ToList(),
            "followup" => isAsc ? resultList.OrderBy(r => r.FollowUpDone).ToList() : resultList.OrderByDescending(r => r.FollowUpDone).ToList(),
            "interested" => isAsc ? resultList.OrderBy(r => r.InterestedDone).ToList() : resultList.OrderByDescending(r => r.InterestedDone).ToList(),
            "closed" => isAsc ? resultList.OrderBy(r => r.ClosedDone).ToList() : resultList.OrderByDescending(r => r.ClosedDone).ToList(),
            "achievement" or "productivity" => isAsc ? resultList.OrderBy(r => r.AchievementPercent).ToList() : resultList.OrderByDescending(r => r.AchievementPercent).ToList(),
            _ => isAsc ? resultList.OrderBy(r => r.AchievementPercent).ToList() : resultList.OrderByDescending(r => r.AchievementPercent).ToList()
        };

        return new ManagerProductivityResponse(periodType, fromDate, toDate, resultList);
    }

    #endregion

    #region User / Employee Operations

    public async Task<UserDashboardResponse> GetUserDashboardAsync(int companyId, int userId)
    {
        var now = DateTime.UtcNow;
        var todayStart = now.Date;
        var todayEnd = todayStart.AddDays(1);

        int diff = (7 + (now.DayOfWeek - DayOfWeek.Monday)) % 7;
        var weekStart = todayStart.AddDays(-1 * diff);
        var monthStart = new DateTime(now.Year, now.Month, 1, 0, 0, 0, DateTimeKind.Utc);

        var myLeadsQuery = _db.CrmLeads.AsNoTracking()
            .Where(l => l.CompanyId == companyId && l.IsActive && (l.AssignedUserId == userId || l.CreatedByUserId == userId));

        int myTotalLeads = await myLeadsQuery.CountAsync();
        int newLeads = await myLeadsQuery.CountAsync(l => l.LeadStatus == "New Lead");
        int followUpLeads = await myLeadsQuery.CountAsync(l => l.LeadStatus == "Follow Up");
        int interestedLeads = await myLeadsQuery.CountAsync(l => l.LeadStatus == "Interested");
        int notInterestedLeads = await myLeadsQuery.CountAsync(l => l.LeadStatus == "Not Interested");
        int closedLeads = await myLeadsQuery.CountAsync(l => l.LeadStatus == "Closed");

        int todayFollowUps = await myLeadsQuery.CountAsync(l =>
            l.NextFollowUpDate.HasValue &&
            l.NextFollowUpDate.Value >= todayStart &&
            l.NextFollowUpDate.Value < todayEnd &&
            l.LeadStatus != "Closed" && l.LeadStatus != "Not Interested");

        int overdueFollowUps = await myLeadsQuery.CountAsync(l =>
            l.NextFollowUpDate.HasValue &&
            l.NextFollowUpDate.Value < todayStart &&
            l.LeadStatus != "Closed" && l.LeadStatus != "Not Interested");

        // KPI Targets - resolved for this user's own office (falls back to company-wide default)
        int? myOfficeId = await _db.Users.Where(u => u.UserId == userId).Select(u => u.OfficeLocationId).FirstOrDefaultAsync();

        var kpis = await _db.CrmKpis.AsNoTracking()
            .Where(k => k.CompanyId == companyId && k.IsActive &&
                        (k.UserId == userId || (k.UserId == null && (k.OfficeLocationId == null || k.OfficeLocationId == myOfficeId))))
            .ToListAsync();

        CrmKpi? ResolveKpi(string period) =>
            kpis.FirstOrDefault(k => k.UserId == userId && k.PeriodType == period)
            ?? kpis.FirstOrDefault(k => k.UserId == null && k.OfficeLocationId == myOfficeId && k.PeriodType == period)
            ?? kpis.FirstOrDefault(k => k.UserId == null && k.OfficeLocationId == null && k.PeriodType == period);

        var dailyKpi = ResolveKpi("Daily");
        var weeklyKpi = ResolveKpi("Weekly");
        var monthlyKpi = ResolveKpi("Monthly");

        int dailyTarget = dailyKpi?.FollowUpTarget ?? 30;
        int weeklyTarget = weeklyKpi?.FollowUpTarget ?? 150;
        int monthlyTarget = monthlyKpi?.FollowUpTarget ?? 600;

        int dailyAchieved = await _db.CrmLeadFollowUps.AsNoTracking()
            .CountAsync(f => f.CompanyId == companyId && f.CreatedByUserId == userId && f.FollowUpDateUtc >= todayStart && f.FollowUpDateUtc < todayEnd);

        int weeklyAchieved = await _db.CrmLeadFollowUps.AsNoTracking()
            .CountAsync(f => f.CompanyId == companyId && f.CreatedByUserId == userId && f.FollowUpDateUtc >= weekStart && f.FollowUpDateUtc < todayEnd);

        int monthlyAchieved = await _db.CrmLeadFollowUps.AsNoTracking()
            .CountAsync(f => f.CompanyId == companyId && f.CreatedByUserId == userId && f.FollowUpDateUtc >= monthStart && f.FollowUpDateUtc < todayEnd);

        double dailyAchievementPercent = dailyTarget > 0 ? Math.Round((double)dailyAchieved / dailyTarget * 100.0, 2) : 100.0;
        double weeklyAchievementPercent = weeklyTarget > 0 ? Math.Round((double)weeklyAchieved / weeklyTarget * 100.0, 2) : 100.0;
        double monthlyAchievementPercent = monthlyTarget > 0 ? Math.Round((double)monthlyAchieved / monthlyTarget * 100.0, 2) : 100.0;

        return new UserDashboardResponse(
            myTotalLeads,
            newLeads,
            followUpLeads,
            interestedLeads,
            notInterestedLeads,
            closedLeads,
            todayFollowUps,
            overdueFollowUps,
            dailyTarget,
            dailyAchieved,
            dailyAchievementPercent,
            weeklyTarget,
            weeklyAchieved,
            weeklyAchievementPercent,
            monthlyTarget,
            monthlyAchieved,
            monthlyAchievementPercent
        );
    }

    public async Task<PagedResult<CrmLeadResponse>> GetUserLeadsAsync(
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
        int pageSize = 20)
    {
        if (pageNumber < 1) pageNumber = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;

        var query = _db.CrmLeads.AsNoTracking()
            .Include(l => l.ProductService)
            .Include(l => l.LeadSource)
            .Include(l => l.CreatedByUser)
            .Include(l => l.AssignedUser)
            .Include(l => l.OfficeLocation)
            .Where(l => l.CompanyId == companyId && l.IsActive && (l.AssignedUserId == userId || l.CreatedByUserId == userId));

        if (!string.IsNullOrWhiteSpace(status))
        {
            query = query.Where(l => l.LeadStatus == status.Trim());
        }

        if (productServiceId.HasValue && productServiceId.Value > 0)
        {
            query = query.Where(l => l.ProductServiceId == productServiceId.Value);
        }

        if (leadSourceId.HasValue && leadSourceId.Value > 0)
        {
            query = query.Where(l => l.LeadSourceId == leadSourceId.Value);
        }

        if (fromDate.HasValue)
        {
            query = query.Where(l => l.CreatedAtUtc >= fromDate.Value.ToUniversalTime());
        }

        if (toDate.HasValue)
        {
            query = query.Where(l => l.CreatedAtUtc <= toDate.Value.ToUniversalTime());
        }

        if (!string.IsNullOrWhiteSpace(search))
        {
            var s = search.Trim().ToLower();
            query = query.Where(l =>
                l.LeadName.ToLower().Contains(s) ||
                (l.ContactPerson != null && l.ContactPerson.ToLower().Contains(s)) ||
                (l.Phone != null && l.Phone.Contains(s)) ||
                (l.Email != null && l.Email.ToLower().Contains(s)) ||
                (l.Address != null && l.Address.ToLower().Contains(s)));
        }

        bool isAsc = string.Equals(sortOrder, "asc", StringComparison.OrdinalIgnoreCase);
        query = (sortBy?.ToLower()) switch
        {
            "leadname" => isAsc ? query.OrderBy(l => l.LeadName) : query.OrderByDescending(l => l.LeadName),
            "status" => isAsc ? query.OrderBy(l => l.LeadStatus) : query.OrderByDescending(l => l.LeadStatus),
            "nextfollowupdate" => isAsc ? query.OrderBy(l => l.NextFollowUpDate) : query.OrderByDescending(l => l.NextFollowUpDate),
            _ => isAsc ? query.OrderBy(l => l.CreatedAtUtc) : query.OrderByDescending(l => l.CreatedAtUtc)
        };

        int totalRecords = await query.CountAsync();
        int totalPages = (int)Math.Ceiling(totalRecords / (double)pageSize);

        var entities = await query
            .Skip((pageNumber - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();
        var items = entities.Select(ToLeadResponse).ToList();

        return new PagedResult<CrmLeadResponse>(items, totalRecords, pageNumber, pageSize, totalPages);
    }

    public async Task<CrmLeadResponse> CreateLeadByUserAsync(int companyId, int userId, CreateCrmLeadRequest request)
    {
        int? officeLocationId = await _db.Users.Where(u => u.UserId == userId).Select(u => u.OfficeLocationId).FirstOrDefaultAsync();

        var lead = new CrmLead
        {
            CompanyId = companyId,
            LeadName = request.LeadName.Trim(),
            ContactPerson = request.ContactPerson?.Trim(),
            Phone = request.Phone?.Trim(),
            Email = request.Email?.Trim(),
            Address = request.Address?.Trim(),
            ProductServiceId = request.ProductServiceId is > 0 ? request.ProductServiceId : null,
            LeadSourceId = request.LeadSourceId is > 0 ? request.LeadSourceId : null,
            LeadSourceType = "Self", // Self created lead
            LeadStatus = !string.IsNullOrWhiteSpace(request.LeadStatus) ? request.LeadStatus.Trim() : "New Lead",
            CreatedByUserId = userId,
            AssignedUserId = userId, // Automatically assigned to self
            OfficeLocationId = officeLocationId,
            NextFollowUpDate = request.NextFollowUpDate,
            EstimatedValue = request.EstimatedValue,
            Remarks = request.Remarks?.Trim(),
            IsActive = true,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.CrmLeads.Add(lead);
        await _db.SaveChangesAsync();
        LogAudit(companyId, userId, "LeadCreated", "Lead", lead.LeadId, null, lead.LeadName);

        if (!string.IsNullOrWhiteSpace(request.Remarks))
        {
            _db.CrmLeadRemarks.Add(new CrmLeadRemark
            {
                CompanyId = companyId,
                LeadId = lead.LeadId,
                UserId = userId,
                Remark = request.Remarks.Trim(),
                CreatedAtUtc = DateTime.UtcNow
            });
        }

        await _db.SaveChangesAsync();
        return (await GetLeadResponseByIdAsync(companyId, lead.LeadId))!;
    }

    public async Task<CrmLeadDetailResponse?> UpdateLeadStatusByUserAsync(int companyId, int userId, int leadId, UpdateLeadStatusRequest request)
    {
        var lead = await _db.CrmLeads.FirstOrDefaultAsync(l =>
            l.CompanyId == companyId &&
            l.LeadId == leadId &&
            l.IsActive &&
            (l.AssignedUserId == userId || l.CreatedByUserId == userId));

        if (lead == null) return null;

        string previousStatus = lead.LeadStatus;
        string newStatus = request.Status.Trim();
        lead.LeadStatus = newStatus;
        if (request.NextFollowUpDate.HasValue) lead.NextFollowUpDate = request.NextFollowUpDate;
        lead.UpdatedAtUtc = DateTime.UtcNow;
        LogStatusChangeIfNeeded(companyId, lead.LeadId, previousStatus, newStatus, userId, request.Remarks);

        if (!string.IsNullOrWhiteSpace(request.Remarks))
        {
            _db.CrmLeadRemarks.Add(new CrmLeadRemark
            {
                CompanyId = companyId,
                LeadId = lead.LeadId,
                UserId = userId,
                Remark = request.Remarks.Trim(),
                CreatedAtUtc = DateTime.UtcNow
            });
        }

        _db.CrmLeadFollowUps.Add(new CrmLeadFollowUp
        {
            CompanyId = companyId,
            LeadId = lead.LeadId,
            FollowUpDateUtc = DateTime.UtcNow,
            NextFollowUpDate = request.NextFollowUpDate,
            Status = newStatus,
            Remarks = !string.IsNullOrWhiteSpace(request.Remarks) ? request.Remarks.Trim() : $"Status updated to {newStatus}",
            CreatedByUserId = userId,
            OfficeLocationId = lead.OfficeLocationId,
            CreatedAtUtc = DateTime.UtcNow
        });

        await _db.SaveChangesAsync();
        return await GetLeadDetailsAsync(companyId, leadId, restrictToUserId: userId);
    }

    public async Task<CrmFollowUpDto?> AddFollowUpAsync(int companyId, int userId, int leadId, CreateFollowUpRequest request, bool isManagerOrAdmin = false)
    {
        var lead = await _db.CrmLeads.FirstOrDefaultAsync(l =>
            l.CompanyId == companyId &&
            l.LeadId == leadId &&
            l.IsActive &&
            (isManagerOrAdmin || l.AssignedUserId == userId || l.CreatedByUserId == userId));

        if (lead == null) return null;

        var followUpDate = request.FollowUpDate ?? DateTime.UtcNow;

        var followUp = new CrmLeadFollowUp
        {
            CompanyId = companyId,
            LeadId = leadId,
            FollowUpDateUtc = followUpDate,
            NextFollowUpDate = request.NextFollowUpDate,
            Status = request.Status.Trim(),
            Remarks = request.Remarks.Trim(),
            CreatedByUserId = userId,
            OfficeLocationId = lead.OfficeLocationId,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.CrmLeadFollowUps.Add(followUp);

        // Update lead status and follow-up timestamps
        string previousStatus = lead.LeadStatus;
        lead.LeadStatus = request.Status.Trim();
        lead.LastFollowUpDate = followUpDate;
        lead.NextFollowUpDate = request.NextFollowUpDate;
        lead.UpdatedAtUtc = DateTime.UtcNow;
        LogStatusChangeIfNeeded(companyId, lead.LeadId, previousStatus, lead.LeadStatus, userId, request.Remarks);
        LogAudit(companyId, userId, "FollowUpAdded", "Lead", lead.LeadId, null, request.Remarks);

        await _db.SaveChangesAsync();

        var user = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId);
        string? userName = user != null ? (user.FullName.Length > 0 ? user.FullName : user.Username) : null;

        return new CrmFollowUpDto(
            followUp.FollowUpId,
            followUp.LeadId,
            followUp.FollowUpDateUtc,
            followUp.NextFollowUpDate,
            followUp.Status,
            followUp.Remarks,
            followUp.CreatedByUserId,
            userName,
            followUp.CreatedAtUtc
        );
    }

    public async Task<CrmRemarkDto?> AddRemarkAsync(int companyId, int userId, int leadId, CreateRemarkRequest request, bool isManagerOrAdmin = false)
    {
        var lead = await _db.CrmLeads.FirstOrDefaultAsync(l =>
            l.CompanyId == companyId &&
            l.LeadId == leadId &&
            l.IsActive &&
            (isManagerOrAdmin || l.AssignedUserId == userId || l.CreatedByUserId == userId));

        if (lead == null) return null;

        var remark = new CrmLeadRemark
        {
            CompanyId = companyId,
            LeadId = leadId,
            UserId = userId,
            Remark = request.Remark.Trim(),
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.CrmLeadRemarks.Add(remark);

        lead.Remarks = request.Remark.Trim();
        lead.UpdatedAtUtc = DateTime.UtcNow;
        LogAudit(companyId, userId, "RemarkAdded", "Lead", lead.LeadId, null, request.Remark.Trim());

        await _db.SaveChangesAsync();

        var user = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId);
        string? userName = user != null ? (user.FullName.Length > 0 ? user.FullName : user.Username) : null;

        return new CrmRemarkDto(
            remark.RemarkId,
            remark.LeadId,
            remark.UserId,
            userName,
            remark.Remark,
            remark.CreatedAtUtc
        );
    }

    public async Task<List<CrmFollowUpItemDto>> GetUserFollowUpsAsync(int companyId, int userId, string? filterType = null, DateTime? fromDate = null, DateTime? toDate = null)
    {
        var now = DateTime.UtcNow;
        var todayStart = now.Date;
        var todayEnd = todayStart.AddDays(1);

        var query = _db.CrmLeads.AsNoTracking()
            .Include(l => l.ProductService)
            .Include(l => l.AssignedUser)
            .Include(l => l.OfficeLocation)
            .Where(l => l.CompanyId == companyId && l.IsActive && l.NextFollowUpDate.HasValue &&
                        (l.AssignedUserId == userId || l.CreatedByUserId == userId) &&
                        l.LeadStatus != "Closed" && l.LeadStatus != "Not Interested");

        switch (filterType?.ToLower())
        {
            case "today":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayEnd);
                break;
            case "tomorrow":
                query = query.Where(l => l.NextFollowUpDate >= todayEnd && l.NextFollowUpDate < todayEnd.AddDays(1));
                break;
            case "overdue":
                query = query.Where(l => l.NextFollowUpDate < todayStart);
                break;
            case "upcoming":
                query = query.Where(l => l.NextFollowUpDate >= todayEnd);
                break;
            case "next7days":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayStart.AddDays(7));
                break;
            case "next15days":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayStart.AddDays(15));
                break;
            case "next30days":
                query = query.Where(l => l.NextFollowUpDate >= todayStart && l.NextFollowUpDate < todayStart.AddDays(30));
                break;
            case "custom":
                if (fromDate.HasValue) query = query.Where(l => l.NextFollowUpDate >= fromDate.Value);
                if (toDate.HasValue) query = query.Where(l => l.NextFollowUpDate <= toDate.Value);
                break;
        }

        var leads = await query.OrderBy(l => l.NextFollowUpDate).ToListAsync();

        return leads.Select(l => ToFollowUpItem(l, todayStart)).ToList();
    }

    public async Task<List<UserKpiPerformanceResponse>> GetUserKpiPerformanceAsync(int companyId, int userId)
    {
        var now = DateTime.UtcNow;
        var todayStart = now.Date;
        var todayEnd = todayStart.AddDays(1);

        int diff = (7 + (now.DayOfWeek - DayOfWeek.Monday)) % 7;
        var weekStart = todayStart.AddDays(-1 * diff);
        var monthStart = new DateTime(now.Year, now.Month, 1, 0, 0, 0, DateTimeKind.Utc);

        var user = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId && u.CompanyId == companyId);
        string employeeName = user != null ? (!string.IsNullOrEmpty(user.FullName) ? user.FullName : user.Username) : "Employee";
        int? officeLocationId = user?.OfficeLocationId;

        var kpis = await _db.CrmKpis.AsNoTracking()
            .Where(k => k.CompanyId == companyId && k.IsActive &&
                        (k.UserId == userId || (k.UserId == null && (k.OfficeLocationId == null || k.OfficeLocationId == officeLocationId))))
            .ToListAsync();

        var periods = new[] { "Daily", "Weekly", "Monthly" };
        var responses = new List<UserKpiPerformanceResponse>();

        foreach (var period in periods)
        {
            DateTime start = period == "Daily" ? todayStart : (period == "Weekly" ? weekStart : monthStart);

            var kpi = kpis.FirstOrDefault(k => k.UserId == userId && k.PeriodType == period)
                ?? kpis.FirstOrDefault(k => k.UserId == null && k.OfficeLocationId == officeLocationId && k.PeriodType == period)
                ?? kpis.FirstOrDefault(k => k.UserId == null && k.OfficeLocationId == null && k.PeriodType == period);

            int followUpTarget = kpi?.FollowUpTarget ?? (period == "Weekly" ? 150 : (period == "Monthly" ? 600 : 30));
            int interestedTarget = kpi?.InterestedTarget ?? (period == "Weekly" ? 100 : (period == "Monthly" ? 300 : 20));
            int closedTarget = kpi?.ClosedTarget ?? (period == "Weekly" ? 50 : (period == "Monthly" ? 100 : 10));

            var followUps = await _db.CrmLeadFollowUps.AsNoTracking()
                .Where(f => f.CompanyId == companyId && f.CreatedByUserId == userId && f.FollowUpDateUtc >= start && f.FollowUpDateUtc < todayEnd)
                .ToListAsync();

            int followUpDone = followUps.Count;
            int interestedDone = followUps.Count(f => f.Status == "Interested");
            int closedDone = followUps.Count(f => f.Status == "Closed");

            double fPercent = followUpTarget > 0 ? Math.Round((double)followUpDone / followUpTarget * 100.0, 2) : 100.0;
            double iPercent = interestedTarget > 0 ? Math.Round((double)interestedDone / interestedTarget * 100.0, 2) : 100.0;
            double cPercent = closedTarget > 0 ? Math.Round((double)closedDone / closedTarget * 100.0, 2) : 100.0;

            double overall = Math.Round((fPercent + iPercent + cPercent) / 3.0, 2);

            responses.Add(new UserKpiPerformanceResponse(
                userId,
                employeeName,
                period,
                followUpTarget,
                followUpDone,
                fPercent,
                interestedTarget,
                interestedDone,
                iPercent,
                closedTarget,
                closedDone,
                cPercent,
                overall
            ));
        }

        return responses;
    }

    #endregion

    #region Helper

    private static IQueryable<CrmLead> ApplyOfficeFilter(IQueryable<CrmLead> query, CrmOfficeScope officeScope)
    {
        if (officeScope.IsUnrestricted) return query;
        return query.Where(l => l.OfficeLocationId.HasValue && officeScope.OfficeIds.Contains(l.OfficeLocationId.Value));
    }

    private static IQueryable<CrmLead> ApplyLeadListFilters(
        IQueryable<CrmLead> query,
        int? assignedUserId,
        string? status,
        int? productServiceId,
        int? leadSourceId,
        DateTime? fromDate,
        DateTime? toDate,
        string? search)
    {
        if (assignedUserId.HasValue && assignedUserId.Value > 0)
        {
            query = query.Where(l => l.AssignedUserId == assignedUserId.Value);
        }

        if (!string.IsNullOrWhiteSpace(status))
        {
            query = query.Where(l => l.LeadStatus == status.Trim());
        }

        if (productServiceId.HasValue && productServiceId.Value > 0)
        {
            query = query.Where(l => l.ProductServiceId == productServiceId.Value);
        }

        if (leadSourceId.HasValue && leadSourceId.Value > 0)
        {
            query = query.Where(l => l.LeadSourceId == leadSourceId.Value);
        }

        if (fromDate.HasValue)
        {
            query = query.Where(l => l.CreatedAtUtc >= fromDate.Value.ToUniversalTime());
        }

        if (toDate.HasValue)
        {
            query = query.Where(l => l.CreatedAtUtc <= toDate.Value.ToUniversalTime());
        }

        if (!string.IsNullOrWhiteSpace(search))
        {
            var s = search.Trim().ToLower();
            query = query.Where(l =>
                l.LeadName.ToLower().Contains(s) ||
                (l.ContactPerson != null && l.ContactPerson.ToLower().Contains(s)) ||
                (l.Phone != null && l.Phone.Contains(s)) ||
                (l.Email != null && l.Email.ToLower().Contains(s)) ||
                (l.Address != null && l.Address.ToLower().Contains(s)));
        }

        return query;
    }

    private static CrmLeadResponse ToLeadResponse(CrmLead l) => new(
        l.LeadId,
        l.CompanyId,
        l.LeadName,
        l.ContactPerson,
        l.Phone,
        l.Email,
        l.Address,
        l.ProductServiceId,
        l.ProductService != null ? l.ProductService.Name : null,
        l.LeadSourceId,
        l.LeadSource != null ? l.LeadSource.Name : null,
        l.LeadSourceType,
        l.LeadStatus,
        l.CreatedByUserId,
        l.CreatedByUser != null ? (l.CreatedByUser.FullName.Length > 0 ? l.CreatedByUser.FullName : l.CreatedByUser.Username) : null,
        l.AssignedUserId,
        l.AssignedUser != null ? (l.AssignedUser.FullName.Length > 0 ? l.AssignedUser.FullName : l.AssignedUser.Username) : null,
        l.NextFollowUpDate,
        l.LastFollowUpDate,
        l.EstimatedValue,
        l.Remarks,
        l.IsActive,
        l.CreatedAtUtc,
        l.OfficeLocationId,
        l.OfficeLocation != null ? l.OfficeLocation.Name : null
    );

    private static CrmFollowUpItemDto ToFollowUpItem(CrmLead l, DateTime todayStart)
    {
        int? daysRemaining = l.NextFollowUpDate.HasValue ? (int)Math.Ceiling((l.NextFollowUpDate.Value.Date - todayStart).TotalDays) : null;
        bool isOverdue = l.NextFollowUpDate.HasValue && l.NextFollowUpDate.Value < todayStart;

        return new CrmFollowUpItemDto(
            l.LeadId,
            l.LeadName,
            l.ContactPerson,
            l.Phone,
            l.ProductService?.Name,
            l.LeadStatus,
            l.NextFollowUpDate,
            daysRemaining,
            isOverdue,
            l.AssignedUserId,
            l.AssignedUser != null ? (l.AssignedUser.FullName.Length > 0 ? l.AssignedUser.FullName : l.AssignedUser.Username) : null,
            l.OfficeLocationId,
            l.OfficeLocation != null ? l.OfficeLocation.Name : null
        );
    }

    private async Task<CrmLeadResponse?> GetLeadResponseByIdAsync(int companyId, int leadId)
    {
        var lead = await _db.CrmLeads.AsNoTracking()
            .Include(l => l.ProductService)
            .Include(l => l.LeadSource)
            .Include(l => l.CreatedByUser)
            .Include(l => l.AssignedUser)
            .Include(l => l.OfficeLocation)
            .Where(l => l.CompanyId == companyId && l.LeadId == leadId)
            .FirstOrDefaultAsync();
        return lead != null ? ToLeadResponse(lead) : null;
    }

    private void LogAudit(int companyId, int userId, string action, string entityType, int entityId, string? oldValue = null, string? newValue = null)
    {
        _db.CrmAuditLogs.Add(new CrmAuditLog
        {
            CompanyId = companyId,
            UserId = userId,
            Action = action,
            EntityType = entityType,
            EntityId = entityId,
            OldValue = oldValue,
            NewValue = newValue,
            CreatedAtUtc = DateTime.UtcNow
        });
    }

    private void LogStatusChangeIfNeeded(int companyId, int leadId, string previousStatus, string newStatus, int changedByUserId, string? remarks = null)
    {
        if (string.Equals(previousStatus, newStatus, StringComparison.Ordinal)) return;

        _db.CrmLeadStatusHistories.Add(new CrmLeadStatusHistory
        {
            CompanyId = companyId,
            LeadId = leadId,
            PreviousStatus = previousStatus,
            NewStatus = newStatus,
            ChangedByUserId = changedByUserId,
            ChangedDateUtc = DateTime.UtcNow,
            Remarks = remarks
        });
    }

    #endregion

    #region Enterprise Dashboard & Reporting Stored Procedures

    private static object DbVal(object? val) => val ?? DBNull.Value;

    private static double ReadDouble(DbDataReader r, int col)
    {
        if (r.IsDBNull(col)) return 0.0;
        var val = r.GetValue(col);
        return Convert.ToDouble(val);
    }

    private static int ReadInt(DbDataReader r, int col)
    {
        if (r.IsDBNull(col)) return 0;
        var val = r.GetValue(col);
        return Convert.ToInt32(val);
    }

    private static string GetAdminReportTitle(int type) => type switch
    {
        1 => "Company Lead Summary",
        2 => "Office Location-wise Lead Report",
        3 => "Manager-wise Lead Report",
        4 => "User-wise Lead Report",
        5 => "Product/Service-wise Lead Report",
        6 => "Lead Source-wise Report",
        7 => "Lead Status Report",
        8 => "Follow-up Summary",
        9 => "Overdue Follow-up Report",
        10 => "KPI Summary",
        11 => "Employee Productivity",
        12 => "Conversion Report",
        13 => "Daily Lead Trend",
        14 => "Weekly Lead Trend",
        15 => "Monthly Lead Trend",
        _ => "CRM Report"
    };

    private static string GetManagerReportTitle(int type) => type switch
    {
        1 => "Team Lead Summary",
        2 => "Employee-wise Lead Report",
        3 => "Employee Productivity Report",
        4 => "Employee KPI Report",
        5 => "Follow-up Performance",
        6 => "Overdue Follow-up Report",
        7 => "Lead Status Report",
        8 => "Product/Service Performance",
        9 => "Lead Source Performance",
        10 => "Conversion Report",
        11 => "Daily Performance",
        12 => "Weekly Performance",
        13 => "Monthly Performance",
        _ => "Team CRM Report"
    };

    private static string GetUserReportTitle(int type) => type switch
    {
        1 => "My Lead Summary",
        2 => "My Lead Status",
        3 => "My Follow-up Report",
        4 => "My Overdue Follow-ups",
        5 => "My KPI Report",
        6 => "My KPI Achievement",
        7 => "My Productivity",
        8 => "My Product/Service-wise Leads",
        9 => "My Lead Source-wise Leads",
        10 => "My Conversion Performance",
        11 => "Daily Performance",
        12 => "Weekly Performance",
        13 => "Monthly Performance",
        _ => "My CRM Report"
    };

    public async Task<AdminCrmDashboardResponse> GetAdminDashboardAsync(int companyId, CrmDashboardFilterRequest filters)
    {
        var conn = _db.Database.GetDbConnection();
        if (conn.State != ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_GetAdminDashboard";
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(filters.FromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(filters.ToDate)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(filters.OfficeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@ManagerId", DbVal(filters.ManagerId)));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(filters.UserId)));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(filters.ProductServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadStatus", DbVal(filters.LeadStatus)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(filters.LeadSourceId)));

        await using var reader = await cmd.ExecuteReaderAsync();

        int totalLeads = 0, newLeads = 0, followUpsToday = 0, pendingFollowUps = 0, overdueFollowUps = 0;
        int interestedLeads = 0, notInterestedLeads = 0, closedLeads = 0, totalManagers = 0, totalUsers = 0;
        double conversionRate = 0;

        if (await reader.ReadAsync())
        {
            totalLeads = ReadInt(reader, 0);
            newLeads = ReadInt(reader, 1);
            followUpsToday = ReadInt(reader, 2);
            pendingFollowUps = ReadInt(reader, 3);
            overdueFollowUps = ReadInt(reader, 4);
            interestedLeads = ReadInt(reader, 5);
            notInterestedLeads = ReadInt(reader, 6);
            closedLeads = ReadInt(reader, 7);
            conversionRate = ReadDouble(reader, 8);
            totalManagers = ReadInt(reader, 9);
            totalUsers = ReadInt(reader, 10);
        }

        var statusDist = new List<ChartDonutSliceDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                statusDist.Add(new ChartDonutSliceDto(reader.GetString(0), ReadDouble(reader, 1), reader.GetString(2)));
            }
        }

        var monthlyTrend = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                monthlyTrend.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var followUpTrend = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                followUpTrend.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var managerPerf = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                managerPerf.Add(new ChartBarEntryDto(reader.GetString(1), ReadDouble(reader, 2), ReadDouble(reader, 3)));
            }
        }

        var userProd = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                userProd.Add(new ChartBarEntryDto(reader.GetString(1), ReadDouble(reader, 2), ReadDouble(reader, 3)));
            }
        }

        var prodPerf = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                prodPerf.Add(new ChartBarEntryDto(reader.GetString(1), ReadDouble(reader, 2), ReadDouble(reader, 3)));
            }
        }

        var sourceDist = new List<ChartDonutSliceDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                sourceDist.Add(new ChartDonutSliceDto(reader.GetString(0), ReadDouble(reader, 1), reader.GetString(2)));
            }
        }

        var funnel = new List<ChartFunnelStageDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                funnel.Add(new ChartFunnelStageDto(reader.GetString(0), ReadInt(reader, 1), ReadDouble(reader, 2)));
            }
        }

        return new AdminCrmDashboardResponse(
            totalLeads, newLeads, followUpsToday, pendingFollowUps, overdueFollowUps,
            interestedLeads, notInterestedLeads, closedLeads, conversionRate,
            totalManagers, totalUsers,
            statusDist, monthlyTrend, followUpTrend, managerPerf,
            userProd, prodPerf, sourceDist, funnel
        );
    }

    public async Task<ManagerCrmDashboardResponse> GetManagerCrmDashboardAsync(int companyId, int managerUserId, CrmOfficeScope officeScope, CrmDashboardFilterRequest filters)
    {
        var conn = _db.Database.GetDbConnection();
        if (conn.State != ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_GetManagerDashboard";
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@ManagerUserId", managerUserId));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(filters.OfficeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(filters.FromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(filters.ToDate)));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(filters.UserId)));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(filters.ProductServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadStatus", DbVal(filters.LeadStatus)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(filters.LeadSourceId)));

        await using var reader = await cmd.ExecuteReaderAsync();

        int teamLeads = 0, newLeads = 0, todayFollowUps = 0, pendingFollowUps = 0, overdueFollowUps = 0;
        int interestedLeads = 0, closedLeads = 0;
        double conversionRate = 0, kpiAchievement = 0;

        if (await reader.ReadAsync())
        {
            teamLeads = ReadInt(reader, 0);
            newLeads = ReadInt(reader, 1);
            todayFollowUps = ReadInt(reader, 2);
            pendingFollowUps = ReadInt(reader, 3);
            overdueFollowUps = ReadInt(reader, 4);
            interestedLeads = ReadInt(reader, 5);
            closedLeads = ReadInt(reader, 6);
            conversionRate = ReadDouble(reader, 7);
            kpiAchievement = ReadDouble(reader, 8);
        }

        var teamLeadTrend = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                teamLeadTrend.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var empProd = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                empProd.Add(new ChartBarEntryDto(reader.GetString(1), ReadDouble(reader, 2), ReadDouble(reader, 3)));
            }
        }

        var kpiBreakdown = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                kpiBreakdown.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var statusDist = new List<ChartDonutSliceDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                statusDist.Add(new ChartDonutSliceDto(reader.GetString(0), ReadDouble(reader, 1), reader.GetString(2)));
            }
        }

        var followUpPerf = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                followUpPerf.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var prodPerf = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                prodPerf.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var sourceDist = new List<ChartDonutSliceDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                sourceDist.Add(new ChartDonutSliceDto(reader.GetString(0), ReadDouble(reader, 1), reader.GetString(2)));
            }
        }

        var funnel = new List<ChartFunnelStageDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                funnel.Add(new ChartFunnelStageDto(reader.GetString(0), ReadInt(reader, 1), ReadDouble(reader, 2)));
            }
        }

        return new ManagerCrmDashboardResponse(
            teamLeads, newLeads, todayFollowUps, pendingFollowUps, overdueFollowUps,
            interestedLeads, closedLeads, conversionRate, kpiAchievement,
            teamLeadTrend, empProd, kpiBreakdown, statusDist,
            followUpPerf, prodPerf, sourceDist, funnel
        );
    }

    public async Task<UserCrmDashboardResponse> GetUserCrmDashboardAsync(int companyId, int userId, CrmDashboardFilterRequest filters)
    {
        var conn = _db.Database.GetDbConnection();
        if (conn.State != ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_GetUserDashboard";
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@UserId", userId));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(filters.FromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(filters.ToDate)));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(filters.ProductServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadStatus", DbVal(filters.LeadStatus)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(filters.LeadSourceId)));

        await using var reader = await cmd.ExecuteReaderAsync();

        int myTotalLeads = 0, myNewLeads = 0, todayFollowUps = 0, pendingFollowUps = 0, overdueFollowUps = 0;
        int interestedLeads = 0, closedLeads = 0;
        int dailyTarget = 30, dailyAchieved = 0; double dailyPercent = 0;
        int weeklyTarget = 150, weeklyAchieved = 0; double weeklyPercent = 0;
        int monthlyTarget = 600, monthlyAchieved = 0; double monthlyPercent = 0;

        if (await reader.ReadAsync())
        {
            myTotalLeads = ReadInt(reader, 0);
            myNewLeads = ReadInt(reader, 1);
            todayFollowUps = ReadInt(reader, 2);
            pendingFollowUps = ReadInt(reader, 3);
            overdueFollowUps = ReadInt(reader, 4);
            interestedLeads = ReadInt(reader, 5);
            closedLeads = ReadInt(reader, 6);

            dailyTarget = ReadInt(reader, 7);
            dailyAchieved = ReadInt(reader, 8);
            dailyPercent = ReadDouble(reader, 9);

            weeklyTarget = ReadInt(reader, 10);
            weeklyAchieved = ReadInt(reader, 11);
            weeklyPercent = ReadDouble(reader, 12);

            monthlyTarget = ReadInt(reader, 13);
            monthlyAchieved = ReadInt(reader, 14);
            monthlyPercent = ReadDouble(reader, 15);
        }

        var statusDist = new List<ChartDonutSliceDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                statusDist.Add(new ChartDonutSliceDto(reader.GetString(0), ReadDouble(reader, 1), reader.GetString(2)));
            }
        }

        var leadTrend = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                leadTrend.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var followUpTrend = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                followUpTrend.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var kpiPerf = new List<ChartBarEntryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                kpiPerf.Add(new ChartBarEntryDto(reader.GetString(0), ReadDouble(reader, 1), ReadDouble(reader, 2)));
            }
        }

        var funnel = new List<ChartFunnelStageDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                funnel.Add(new ChartFunnelStageDto(reader.GetString(0), ReadInt(reader, 1), ReadDouble(reader, 2)));
            }
        }

        return new UserCrmDashboardResponse(
            myTotalLeads, myNewLeads, todayFollowUps, pendingFollowUps, overdueFollowUps,
            interestedLeads, closedLeads,
            dailyTarget, dailyAchieved, dailyPercent,
            weeklyTarget, weeklyAchieved, weeklyPercent,
            monthlyTarget, monthlyAchieved, monthlyPercent,
            statusDist, leadTrend, followUpTrend, kpiPerf, funnel
        );
    }

    public async Task<CrmReportResponse> GetAdminReportAsync(int companyId, CrmReportFilterRequest request)
    {
        var conn = _db.Database.GetDbConnection();
        if (conn.State != ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_GetAdminReports";
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.Add(new SqlParameter("@ReportType", request.ReportType));
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(request.FromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(request.ToDate)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(request.OfficeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@ManagerId", DbVal(request.ManagerId)));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(request.UserId)));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(request.ProductServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadStatus", DbVal(request.LeadStatus)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(request.LeadSourceId)));
        cmd.Parameters.Add(new SqlParameter("@Search", DbVal(request.Search)));
        cmd.Parameters.Add(new SqlParameter("@PageNumber", request.PageNumber));
        cmd.Parameters.Add(new SqlParameter("@PageSize", request.PageSize));

        await using var reader = await cmd.ExecuteReaderAsync();

        var summary = new CrmReportSummary(0, "Total", "0", "Closed", "0", "Conversion", "0%");
        if (await reader.ReadAsync())
        {
            summary = new CrmReportSummary(
                ReadInt(reader, 0),
                reader.IsDBNull(1) ? "Metric 1" : reader.GetString(1),
                reader.IsDBNull(2) ? "0" : reader.GetString(2),
                reader.IsDBNull(3) ? "Metric 2" : reader.GetString(3),
                reader.IsDBNull(4) ? "0" : reader.GetString(4),
                reader.IsDBNull(5) ? "Metric 3" : reader.GetString(5),
                reader.IsDBNull(6) ? "0" : reader.GetString(6)
            );
        }

        var rows = new List<CrmReportRow>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                rows.Add(new CrmReportRow(
                    ReadInt(reader, 0),
                    ReadInt(reader, 1),
                    reader.IsDBNull(2) ? "" : reader.GetString(2),
                    reader.IsDBNull(3) ? "" : reader.GetString(3),
                    reader.IsDBNull(4) ? "" : reader.GetString(4),
                    reader.IsDBNull(5) ? "" : reader.GetString(5),
                    reader.IsDBNull(6) ? "" : reader.GetString(6),
                    reader.IsDBNull(7) ? "" : reader.GetString(7),
                    reader.IsDBNull(8) ? "" : reader.GetString(8),
                    reader.IsDBNull(9) ? "" : reader.GetString(9),
                    reader.IsDBNull(10) ? null : reader.GetDateTime(10)
                ));
            }
        }

        string reportTitle = GetAdminReportTitle(request.ReportType);
        int totalPages = (int)Math.Ceiling((double)summary.TotalRows / request.PageSize);
        if (totalPages < 1) totalPages = 1;

        return new CrmReportResponse(request.ReportType, reportTitle, summary, rows, request.PageNumber, request.PageSize, totalPages);
    }

    public async Task<CrmReportResponse> GetManagerReportAsync(int companyId, int managerUserId, CrmOfficeScope officeScope, CrmReportFilterRequest request)
    {
        var conn = _db.Database.GetDbConnection();
        if (conn.State != ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_GetManagerReports";
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.Add(new SqlParameter("@ReportType", request.ReportType));
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@ManagerUserId", managerUserId));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(request.FromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(request.ToDate)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(request.OfficeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(request.UserId)));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(request.ProductServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadStatus", DbVal(request.LeadStatus)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(request.LeadSourceId)));
        cmd.Parameters.Add(new SqlParameter("@Search", DbVal(request.Search)));
        cmd.Parameters.Add(new SqlParameter("@PageNumber", request.PageNumber));
        cmd.Parameters.Add(new SqlParameter("@PageSize", request.PageSize));

        await using var reader = await cmd.ExecuteReaderAsync();

        var summary = new CrmReportSummary(0, "Total", "0", "Closed", "0", "Conversion", "0%");
        if (await reader.ReadAsync())
        {
            summary = new CrmReportSummary(
                ReadInt(reader, 0),
                reader.IsDBNull(1) ? "Metric 1" : reader.GetString(1),
                reader.IsDBNull(2) ? "0" : reader.GetString(2),
                reader.IsDBNull(3) ? "Metric 2" : reader.GetString(3),
                reader.IsDBNull(4) ? "0" : reader.GetString(4),
                reader.IsDBNull(5) ? "Metric 3" : reader.GetString(5),
                reader.IsDBNull(6) ? "0" : reader.GetString(6)
            );
        }

        var rows = new List<CrmReportRow>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                rows.Add(new CrmReportRow(
                    ReadInt(reader, 0),
                    ReadInt(reader, 1),
                    reader.IsDBNull(2) ? "" : reader.GetString(2),
                    reader.IsDBNull(3) ? "" : reader.GetString(3),
                    reader.IsDBNull(4) ? "" : reader.GetString(4),
                    reader.IsDBNull(5) ? "" : reader.GetString(5),
                    reader.IsDBNull(6) ? "" : reader.GetString(6),
                    reader.IsDBNull(7) ? "" : reader.GetString(7),
                    reader.IsDBNull(8) ? "" : reader.GetString(8),
                    reader.IsDBNull(9) ? "" : reader.GetString(9),
                    reader.IsDBNull(10) ? null : reader.GetDateTime(10)
                ));
            }
        }

        string reportTitle = GetManagerReportTitle(request.ReportType);
        int totalPages = (int)Math.Ceiling((double)summary.TotalRows / request.PageSize);
        if (totalPages < 1) totalPages = 1;

        return new CrmReportResponse(request.ReportType, reportTitle, summary, rows, request.PageNumber, request.PageSize, totalPages);
    }

    public async Task<CrmReportResponse> GetUserReportAsync(int companyId, int userId, CrmReportFilterRequest request)
    {
        var conn = _db.Database.GetDbConnection();
        if (conn.State != ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_GetUserReports";
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.Add(new SqlParameter("@ReportType", request.ReportType));
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@UserId", userId));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(request.FromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(request.ToDate)));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(request.ProductServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadStatus", DbVal(request.LeadStatus)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(request.LeadSourceId)));
        cmd.Parameters.Add(new SqlParameter("@Search", DbVal(request.Search)));
        cmd.Parameters.Add(new SqlParameter("@PageNumber", request.PageNumber));
        cmd.Parameters.Add(new SqlParameter("@PageSize", request.PageSize));

        await using var reader = await cmd.ExecuteReaderAsync();

        var summary = new CrmReportSummary(0, "Total", "0", "Closed", "0", "Conversion", "0%");
        if (await reader.ReadAsync())
        {
            summary = new CrmReportSummary(
                ReadInt(reader, 0),
                reader.IsDBNull(1) ? "Metric 1" : reader.GetString(1),
                reader.IsDBNull(2) ? "0" : reader.GetString(2),
                reader.IsDBNull(3) ? "Metric 2" : reader.GetString(3),
                reader.IsDBNull(4) ? "0" : reader.GetString(4),
                reader.IsDBNull(5) ? "Metric 3" : reader.GetString(5),
                reader.IsDBNull(6) ? "0" : reader.GetString(6)
            );
        }

        var rows = new List<CrmReportRow>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                rows.Add(new CrmReportRow(
                    ReadInt(reader, 0),
                    ReadInt(reader, 1),
                    reader.IsDBNull(2) ? "" : reader.GetString(2),
                    reader.IsDBNull(3) ? "" : reader.GetString(3),
                    reader.IsDBNull(4) ? "" : reader.GetString(4),
                    reader.IsDBNull(5) ? "" : reader.GetString(5),
                    reader.IsDBNull(6) ? "" : reader.GetString(6),
                    reader.IsDBNull(7) ? "" : reader.GetString(7),
                    reader.IsDBNull(8) ? "" : reader.GetString(8),
                    reader.IsDBNull(9) ? "" : reader.GetString(9),
                    reader.IsDBNull(10) ? null : reader.GetDateTime(10)
                ));
            }
        }

        string reportTitle = GetUserReportTitle(request.ReportType);
        int totalPages = (int)Math.Ceiling((double)summary.TotalRows / request.PageSize);
        if (totalPages < 1) totalPages = 1;

        return new CrmReportResponse(request.ReportType, reportTitle, summary, rows, request.PageNumber, request.PageSize, totalPages);
    }

    #endregion

}
