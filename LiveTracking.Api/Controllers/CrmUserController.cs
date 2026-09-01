using System.Security.Claims;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/crm/user")]
[Authorize]
public class CrmUserController : ControllerBase
{
    private readonly ICrmService _crm;
    private readonly LiveTrackingDbContext _db;

    public CrmUserController(ICrmService crm, LiveTrackingDbContext db)
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

    [HttpGet("dashboard")]
    public async Task<ActionResult<UserDashboardResponse>> GetDashboard()
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetUserDashboardAsync(companyId, userId);
        return Ok(result);
    }

    [HttpGet("leads")]
    public async Task<ActionResult<PagedResult<CrmLeadResponse>>> GetMyLeads(
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
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetUserLeadsAsync(
            companyId, userId, status, productServiceId, leadSourceId,
            fromDate, toDate, search, sortBy, sortOrder, pageNumber, pageSize);

        return Ok(result);
    }

    [HttpGet("leads/{id:int}")]
    public async Task<ActionResult<CrmLeadDetailResponse>> GetLeadDetails(int id)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var lead = await _crm.GetLeadDetailsAsync(companyId, id, restrictToUserId: userId);
        if (lead == null) return NotFound(new { message = "Lead not found." });

        return Ok(lead);
    }

    [HttpPost("leads")]
    public async Task<ActionResult<CrmLeadResponse>> CreateSelfLead([FromBody] CreateCrmLeadRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.LeadName))
            return BadRequest(new { message = "Lead name is required." });

        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var lead = await _crm.CreateLeadByUserAsync(companyId, userId, request);
        return CreatedAtAction(nameof(GetLeadDetails), new { id = lead.LeadId }, lead);
    }

    [HttpPut("leads/{id:int}/status")]
    public async Task<ActionResult<CrmLeadDetailResponse>> UpdateLeadStatus(int id, [FromBody] UpdateLeadStatusRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Status))
            return BadRequest(new { message = "Status is required." });

        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var lead = await _crm.UpdateLeadStatusByUserAsync(companyId, userId, id, request);
        if (lead == null) return NotFound(new { message = "Lead not found or inaccessible." });

        return Ok(lead);
    }

    [HttpPost("leads/{id:int}/followup")]
    public async Task<ActionResult<CrmFollowUpDto>> AddFollowUp(int id, [FromBody] CreateFollowUpRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Remarks))
            return BadRequest(new { message = "Follow-up remark is required." });

        if (string.IsNullOrWhiteSpace(request.Status))
            return BadRequest(new { message = "Status is required." });

        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        bool isManagerOrAdmin = User.IsInRole("Admin") || User.IsInRole("Manager");
        var result = await _crm.AddFollowUpAsync(companyId, userId, id, request, isManagerOrAdmin);
        if (result == null) return NotFound(new { message = "Lead not found or inaccessible." });

        return Ok(result);
    }

    [HttpPost("leads/{id:int}/remarks")]
    public async Task<ActionResult<CrmRemarkDto>> AddRemark(int id, [FromBody] CreateRemarkRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Remark))
            return BadRequest(new { message = "Remark text is required." });

        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        bool isManagerOrAdmin = User.IsInRole("Admin") || User.IsInRole("Manager");
        var result = await _crm.AddRemarkAsync(companyId, userId, id, request, isManagerOrAdmin);
        if (result == null) return NotFound(new { message = "Lead not found or inaccessible." });

        return Ok(result);
    }

    [HttpGet("followups")]
    public async Task<ActionResult<List<CrmFollowUpItemDto>>> GetMyFollowUps([FromQuery] string? filterType = null)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetUserFollowUpsAsync(companyId, userId, filterType);
        return Ok(result);
    }

    [HttpGet("kpi")]
    public async Task<ActionResult<List<UserKpiPerformanceResponse>>> GetMyKpiPerformance()
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetUserKpiPerformanceAsync(companyId, userId);
        return Ok(result);
    }

    [HttpGet("products-services")]
    public async Task<ActionResult<List<CrmProductServiceDto>>> GetActiveProductServices()
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetProductServicesAsync(companyId, activeOnly: true);
        return Ok(result);
    }

    [HttpGet("lead-sources")]
    public async Task<ActionResult<List<CrmLeadSourceDto>>> GetActiveLeadSources()
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var result = await _crm.GetLeadSourcesAsync(companyId, activeOnly: true);
        return Ok(result);
    }
}
