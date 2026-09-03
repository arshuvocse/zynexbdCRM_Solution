using System.Security.Claims;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Repositories.CRM;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LiveTracking.Api.Controllers.CRM;

[ApiController]
[Route("api/crm/kpi")]
[Authorize]
public class CrmKpiController : ControllerBase
{
    private readonly ICrmLeadRepository _repo;

    public CrmKpiController(ICrmLeadRepository repo)
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

    [HttpPost]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<ActionResult<CrmKpiDto>> SaveKpi([FromBody] CreateOrUpdateKpiRequest request)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int adminId = GetCurrentUserId();

        try
        {
            var kpi = await _repo.SaveKpiAsync(companyId, adminId, request);
            if (kpi == null) return BadRequest(new { message = "Failed to save KPI target." });

            return Ok(kpi);
        }
        catch (Exception ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    [HttpGet("productivity")]
    public async Task<ActionResult<ManagerProductivityResponse>> GetProductivity(
        [FromQuery] string periodType = "Daily",
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? userId = null)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int currentUserId = GetCurrentUserId();

        // Regular employee sees only own productivity
        int? filterUser = IsManagerOrAdmin ? userId : currentUserId;

        var result = await _repo.GetProductivityAsync(
            companyId,
            periodType: periodType,
            fromDate: fromDate,
            toDate: toDate,
            officeLocationId: officeLocationId,
            userId: filterUser
        );

        return Ok(result);
    }
}
