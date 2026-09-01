using System.Security.Claims;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/crm/manager")]
[Authorize(Roles = "Admin,Manager")]
public class CrmManagerController : ControllerBase
{
    private readonly ICrmService _crm;
    private readonly LiveTrackingDbContext _db;

    public CrmManagerController(ICrmService crm, LiveTrackingDbContext db)
    {
        _crm = crm;
        _db = db;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                    ?? User.FindFirst("sub")?.Value;
        return int.TryParse(claim, out var id) ? id : 0;
    }

    private bool IsAdmin => User.IsInRole("Admin");

    private async Task<int> GetCurrentCompanyIdAsync()
    {
        var claim = User.FindFirst("companyId")?.Value;
        if (int.TryParse(claim, out var cid) && cid > 0)
        {
            return cid;
        }

        int userId = GetCurrentUserId();
        if (userId > 0)
        {
            var userCid = await _db.Users
                .Where(u => u.UserId == userId)
                .Select(u => u.CompanyId)
                .FirstOrDefaultAsync();

            if (userCid.HasValue && userCid.Value > 0)
            {
                return userCid.Value;
            }
        }

        return 0;
    }

    /// <summary>
    /// Resolves the caller's authorized office scope, optionally narrowed to a single
    /// explicitly-requested office. Never trusts the client value without checking it against
    /// the DB-resolved authorized set first - returns ok=false if the caller is not actually
    /// authorized for the requested office (controller should respond 403, not silently widen).
    /// </summary>
    private async Task<(CrmOfficeScope scope, bool ok)> ResolveOfficeScopeAsync(int? requestedOfficeId)
    {
        int userId = GetCurrentUserId();
        string role = IsAdmin ? "Admin" : "Manager";
        var scope = await _crm.GetOfficeScopeAsync(userId, role);

        if (!requestedOfficeId.HasValue || requestedOfficeId.Value <= 0)
        {
            return (scope, true);
        }

        if (!scope.Allows(requestedOfficeId.Value))
        {
            return (scope, false);
        }

        return (new CrmOfficeScope(new List<int> { requestedOfficeId.Value }, false), true);
    }

    private static ObjectResult OfficeForbidden() =>
        new(new { message = "You are not authorized for the specified office location." }) { StatusCode = 403 };

    [HttpGet("dashboard")]
    public async Task<ActionResult<ManagerDashboardResponse>> GetDashboard([FromQuery] int? officeLocationId = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var result = await _crm.GetManagerDashboardAsync(companyId, officeScope);
        return Ok(result);
    }

    [HttpGet("leads")]
    public async Task<ActionResult<PagedResult<CrmLeadResponse>>> GetLeads(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? assignedUserId = null,
        [FromQuery] string? status = null,
        [FromQuery] int? productServiceId = null,
        [FromQuery] int? leadSourceId = null,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] string? search = null,
        [FromQuery] string? sortBy = null,
        [FromQuery] string? sortOrder = null,
        [FromQuery] int pageNumber = 1,
        [FromQuery] int pageSize = 20)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var result = await _crm.GetManagerLeadsAsync(
            companyId, officeScope, assignedUserId, status, productServiceId, leadSourceId,
            fromDate, toDate, search, sortBy, sortOrder, pageNumber, pageSize);

        return Ok(result);
    }

    [HttpGet("leads/{id:int}")]
    public async Task<ActionResult<CrmLeadDetailResponse>> GetLeadDetails(int id)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, _) = await ResolveOfficeScopeAsync(null);
        var lead = await _crm.GetLeadDetailsAsync(companyId, id, officeScope);
        if (lead == null) return NotFound(new { message = "Lead not found." });

        return Ok(lead);
    }

    [HttpPost("leads")]
    public async Task<ActionResult<CrmLeadResponse>> CreateLead([FromBody] CreateCrmLeadRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.LeadName))
            return BadRequest(new { message = "Lead name is required." });

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, _) = await ResolveOfficeScopeAsync(null);
        int adminId = GetCurrentUserId();
        var lead = await _crm.CreateLeadByManagerAsync(companyId, adminId, officeScope, request);
        if (lead == null) return BadRequest(new { message = "Failed to create lead. The assigned employee must belong to your authorized office." });

        return CreatedAtAction(nameof(GetLeadDetails), new { id = lead.LeadId }, lead);
    }

    [HttpPut("leads/{id:int}")]
    public async Task<ActionResult<CrmLeadResponse>> UpdateLead(int id, [FromBody] UpdateCrmLeadRequest request)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, _) = await ResolveOfficeScopeAsync(null);
        int adminId = GetCurrentUserId();
        var lead = await _crm.UpdateLeadByManagerAsync(companyId, adminId, officeScope, id, request);
        if (lead == null) return NotFound(new { message = "Lead not found." });

        return Ok(lead);
    }

    [HttpPost("leads/{id:int}/assign")]
    public async Task<ActionResult<CrmLeadDetailResponse>> AssignLead(int id, [FromBody] AssignLeadRequest request)
    {
        if (request.NewUserId <= 0)
            return BadRequest(new { message = "Valid employee ID is required." });

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, _) = await ResolveOfficeScopeAsync(null);
        int adminId = GetCurrentUserId();
        var lead = await _crm.AssignLeadAsync(companyId, adminId, officeScope, IsAdmin, id, request);
        if (lead == null) return BadRequest(new { message = "Failed to assign lead. Verify the lead and employee belong to your organization and authorized office." });

        return Ok(lead);
    }

    [HttpGet("leads/export")]
    public async Task<IActionResult> ExportLeads(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? assignedUserId = null,
        [FromQuery] string? status = null,
        [FromQuery] int? productServiceId = null,
        [FromQuery] int? leadSourceId = null,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] string? search = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var leads = await _crm.GetManagerLeadsForExportAsync(
            companyId, officeScope, assignedUserId, status, productServiceId, leadSourceId, fromDate, toDate, search);

        var rows = new List<string[]>
        {
            new[] { "LeadId", "LeadName", "ContactPerson", "Phone", "Email", "Address", "ProductService", "LeadSource", "LeadSourceType", "LeadStatus", "OfficeLocation", "CreatedBy", "AssignedTo", "NextFollowUpDate", "LastFollowUpDate", "EstimatedValue", "CreatedAtUtc" }
        };
        rows.AddRange(leads.Select(l => new[]
        {
            l.LeadId.ToString(), l.LeadName, l.ContactPerson ?? "", l.Phone ?? "", l.Email ?? "", l.Address ?? "",
            l.ProductServiceName ?? "", l.LeadSourceName ?? "", l.LeadSourceType, l.LeadStatus, l.OfficeLocationName ?? "",
            l.CreatedByUserName ?? "", l.AssignedUserName ?? "",
            l.NextFollowUpDate?.ToString("o") ?? "", l.LastFollowUpDate?.ToString("o") ?? "",
            l.EstimatedValue?.ToString() ?? "", l.CreatedAtUtc.ToString("o")
        }));

        return File(CsvHelper.ToCsvBytes(rows), "text/csv", $"CRM_Leads_{DateTime.UtcNow:yyyy-MM-dd_HHmmss}.csv");
    }

    [HttpGet("followups/export")]
    public async Task<IActionResult> ExportFollowUps(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? assignedUserId = null,
        [FromQuery] string? filterType = null,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var items = await _crm.GetManagerFollowUpsAsync(companyId, officeScope, assignedUserId, filterType, fromDate, toDate);

        var rows = new List<string[]>
        {
            new[] { "LeadId", "LeadName", "ContactPerson", "Phone", "ProductService", "LeadStatus", "OfficeLocation", "NextFollowUpDate", "DaysRemaining", "IsOverdue", "AssignedTo" }
        };
        rows.AddRange(items.Select(f => new[]
        {
            f.LeadId.ToString(), f.LeadName, f.ContactPerson ?? "", f.Phone ?? "", f.ProductServiceName ?? "",
            f.LeadStatus, f.OfficeLocationName ?? "", f.NextFollowUpDate?.ToString("o") ?? "", f.DaysRemaining?.ToString() ?? "",
            f.IsOverdue.ToString(), f.AssignedUserName ?? ""
        }));

        return File(CsvHelper.ToCsvBytes(rows), "text/csv", $"CRM_Followups_{DateTime.UtcNow:yyyy-MM-dd_HHmmss}.csv");
    }

    [HttpGet("productivity/export")]
    public async Task<IActionResult> ExportProductivity(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] string periodType = "Daily",
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var result = await _crm.GetManagerProductivityAsync(companyId, officeScope, periodType, fromDate, toDate);

        var rows = new List<string[]>
        {
            new[] { "EmployeeName", "OfficeLocation", "FollowUpTarget", "FollowUpDone", "InterestedTarget", "InterestedDone", "ClosedTarget", "ClosedDone", "AchievementPercent" }
        };
        rows.AddRange(result.Items.Select(i => new[]
        {
            i.EmployeeName, i.OfficeLocationName ?? "", i.FollowUpTarget.ToString(), i.FollowUpDone.ToString(),
            i.InterestedTarget.ToString(), i.InterestedDone.ToString(),
            i.ClosedTarget.ToString(), i.ClosedDone.ToString(), i.AchievementPercent.ToString("F1")
        }));

        return File(CsvHelper.ToCsvBytes(rows), "text/csv", $"CRM_Productivity_{DateTime.UtcNow:yyyy-MM-dd_HHmmss}.csv");
    }

    [HttpGet("kpi/export")]
    public async Task<IActionResult> ExportKpi([FromQuery] int? officeLocationId = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var kpis = await _crm.GetCompanyKpisAsync(companyId, officeScope);

        var rows = new List<string[]>
        {
            new[] { "KpiId", "Employee", "OfficeLocation", "PeriodType", "FollowUpTarget", "InterestedTarget", "ClosedTarget", "EffectiveStartDate" }
        };
        rows.AddRange(kpis.Select(k => new[]
        {
            k.KpiId.ToString(), k.UserName ?? "", k.OfficeLocationName ?? "", k.PeriodType,
            k.FollowUpTarget.ToString(), k.InterestedTarget.ToString(), k.ClosedTarget.ToString(),
            k.EffectiveStartDate.ToString("o")
        }));

        return File(CsvHelper.ToCsvBytes(rows), "text/csv", $"CRM_KPI_{DateTime.UtcNow:yyyy-MM-dd_HHmmss}.csv");
    }

    [HttpGet("followups")]
    public async Task<ActionResult<List<CrmFollowUpItemDto>>> GetFollowUps(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? assignedUserId = null,
        [FromQuery] string? filterType = null,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var result = await _crm.GetManagerFollowUpsAsync(companyId, officeScope, assignedUserId, filterType, fromDate, toDate);
        return Ok(result);
    }

    [HttpGet("kpi")]
    public async Task<ActionResult<List<CrmKpiDto>>> GetKpis([FromQuery] int? officeLocationId = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var result = await _crm.GetCompanyKpisAsync(companyId, officeScope);
        return Ok(result);
    }

    [HttpPost("kpi")]
    public async Task<ActionResult<CrmKpiDto>> CreateOrUpdateKpi([FromBody] CreateOrUpdateKpiRequest request)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, _) = await ResolveOfficeScopeAsync(null);
        int adminId = GetCurrentUserId();
        var result = await _crm.CreateOrUpdateKpiAsync(companyId, adminId, officeScope, request);
        if (result == null) return BadRequest(new { message = "Failed to set KPI. Target employee/office must be within your authorized offices." });

        return Ok(result);
    }

    [HttpGet("productivity")]
    public async Task<ActionResult<ManagerProductivityResponse>> GetProductivity(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] string periodType = "Daily",
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] string? sortBy = null,
        [FromQuery] string? sortOrder = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var (officeScope, ok) = await ResolveOfficeScopeAsync(officeLocationId);
        if (!ok) return OfficeForbidden();

        var result = await _crm.GetManagerProductivityAsync(companyId, officeScope, periodType, fromDate, toDate, sortBy, sortOrder);
        return Ok(result);
    }

    #region Master Data

    [HttpGet("products-services")]
    public async Task<ActionResult<List<CrmProductServiceDto>>> GetProductServices([FromQuery] bool activeOnly = true)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetProductServicesAsync(companyId, activeOnly);
        return Ok(result);
    }

    [HttpPost("products-services")]
    public async Task<ActionResult<CrmProductServiceDto>> CreateProductService([FromBody] CreateCrmProductServiceRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
            return BadRequest(new { message = "Product/Service name is required." });

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.CreateProductServiceAsync(companyId, request);
        return Ok(result);
    }

    [HttpPut("products-services/{id:int}")]
    public async Task<ActionResult<CrmProductServiceDto>> UpdateProductService(int id, [FromBody] UpdateCrmProductServiceRequest request)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.UpdateProductServiceAsync(companyId, id, request);
        if (result == null) return NotFound(new { message = "Product/Service not found." });

        return Ok(result);
    }

    [HttpDelete("products-services/{id:int}")]
    public async Task<IActionResult> DeleteProductService(int id)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        bool deleted = await _crm.DeleteProductServiceAsync(companyId, id);
        if (!deleted) return NotFound(new { message = "Product/Service not found." });

        return NoContent();
    }

    [HttpGet("lead-sources")]
    public async Task<ActionResult<List<CrmLeadSourceDto>>> GetLeadSources([FromQuery] bool activeOnly = true)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetLeadSourcesAsync(companyId, activeOnly);
        return Ok(result);
    }

    [HttpPost("lead-sources")]
    public async Task<ActionResult<CrmLeadSourceDto>> CreateLeadSource([FromBody] CreateCrmLeadSourceRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
            return BadRequest(new { message = "Lead source name is required." });

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.CreateLeadSourceAsync(companyId, request);
        return Ok(result);
    }

    #endregion
}
