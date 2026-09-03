using System.Security.Claims;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Repositories.CRM;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LiveTracking.Api.Controllers.CRM;

[ApiController]
[Route("api/crm/followups")]
[Authorize]
public class CrmFollowUpController : ControllerBase
{
    private readonly ICrmLeadRepository _repo;

    public CrmFollowUpController(ICrmLeadRepository repo)
    {
        _repo = repo;
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

    private bool IsManagerOrAdmin => User.IsInRole("Admin") || User.IsInRole("Manager");

    [HttpGet("today")]
    public async Task<ActionResult<List<CrmFollowUpItemDto>>> GetTodayFollowUps(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? assignedUserId = null)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int currentUserId = GetCurrentUserId();
        int? filterUser = IsManagerOrAdmin ? assignedUserId : currentUserId;

        var items = await _repo.GetTodayFollowUpsAsync(companyId, filterUser, officeLocationId);
        return Ok(items);
    }

    [HttpGet("overdue")]
    public async Task<ActionResult<List<CrmFollowUpItemDto>>> GetOverdueFollowUps(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? assignedUserId = null)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int currentUserId = GetCurrentUserId();
        int? filterUser = IsManagerOrAdmin ? assignedUserId : currentUserId;

        var items = await _repo.GetOverdueFollowUpsAsync(companyId, filterUser, officeLocationId);
        return Ok(items);
    }

    [HttpGet("upcoming")]
    public async Task<ActionResult<List<CrmFollowUpItemDto>>> GetUpcomingFollowUps(
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? assignedUserId = null,
        [FromQuery] int daysAhead = 30)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int currentUserId = GetCurrentUserId();
        int? filterUser = IsManagerOrAdmin ? assignedUserId : currentUserId;

        var items = await _repo.GetUpcomingFollowUpsAsync(companyId, filterUser, officeLocationId, daysAhead);
        return Ok(items);
    }
}
