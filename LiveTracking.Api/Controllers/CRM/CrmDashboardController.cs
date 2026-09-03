using System.Security.Claims;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Repositories.CRM;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LiveTracking.Api.Controllers.CRM;

[ApiController]
[Route("api/crm/dashboard")]
[Authorize]
public class CrmDashboardController : ControllerBase
{
    private readonly ICrmLeadRepository _repo;
    private readonly LiveTracking.Api.Services.ICrmService _crm;

    public CrmDashboardController(ICrmLeadRepository repo, LiveTracking.Api.Services.ICrmService crm)
    {
        _repo = repo;
        _crm = crm;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                    ?? User.FindFirst("sub")?.Value;
        return int.TryParse(claim, out var id) ? id : 0;
    }

    private int GetCurrentCompanyId()
    {
        var claim = User.FindFirst("companyId")?.Value;
        return int.TryParse(claim, out var cid) ? cid : 0;
    }

    [HttpGet("manager")]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<ActionResult<ManagerDashboardResponse>> GetManagerDashboard(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int managerId = GetCurrentUserId();
        var result = await _repo.GetManagerDashboardAsync(companyId, managerId, officeLocationId, fromDate, toDate);
        return Ok(result);
    }

    [HttpGet("employee")]
    public async Task<ActionResult<UserDashboardResponse>> GetEmployeeDashboard(
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int userId = GetCurrentUserId();
        var result = await _repo.GetEmployeeDashboardAsync(companyId, userId, fromDate, toDate);
        return Ok(result);
    }

    [HttpGet("live-activities")]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<ActionResult<List<LiveTeamActivityDto>>> GetLiveTeamActivities(
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] string? actionType = null,
        [FromQuery] int? userId = null,
        [FromQuery] int limit = 100)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int currentUserId = GetCurrentUserId();
        string role = User.IsInRole("Admin") ? "Admin" : "Manager";
        var officeScope = await _crm.GetOfficeScopeAsync(currentUserId, role);

        var activities = await _crm.GetLiveTeamActivitiesAsync(
            companyId,
            officeScope,
            fromDate,
            toDate,
            actionType,
            userId,
            Math.Clamp(limit, 1, 500)
        );
        return Ok(activities);
    }
}
