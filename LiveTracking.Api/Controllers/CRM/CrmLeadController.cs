using System.Security.Claims;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Repositories.CRM;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LiveTracking.Api.Controllers.CRM;

[ApiController]
[Route("api/crm/leads")]
[Authorize]
public class CrmLeadController : ControllerBase
{
    private readonly ICrmLeadRepository _repo;

    public CrmLeadController(ICrmLeadRepository repo)
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
    public async Task<ActionResult<CrmLeadResponse>> CreateLead([FromBody] CreateCrmLeadRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.LeadName))
            return BadRequest(new { message = "Lead name is required." });

        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int userId = GetCurrentUserId();
        string leadSourceType = IsManagerOrAdmin && request.AssignedUserId.HasValue && request.AssignedUserId.Value > 0
            ? "Assigned"
            : (IsManagerOrAdmin ? "Manager" : "Self");

        // If regular employee creates lead, assign to self
        var req = !IsManagerOrAdmin
            ? request with { AssignedUserId = userId }
            : request;

        try
        {
            var lead = await _repo.SaveLeadAsync(companyId, userId, req, leadId: null, leadSourceType: leadSourceType);
            if (lead == null) return BadRequest(new { message = "Failed to create lead." });

            return CreatedAtAction(nameof(GetLeadById), new { id = lead.LeadId }, lead);
        }
        catch (Exception ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    [HttpGet]
    public async Task<ActionResult<PagedResult<CrmLeadResponse>>> GetLeads(
        [FromQuery] int? assignedUserId = null,
        [FromQuery] int? officeLocationId = null,
        [FromQuery] string? status = null,
        [FromQuery] int? productServiceId = null,
        [FromQuery] int? leadSourceId = null,
        [FromQuery] string? leadSourceType = null,
        [FromQuery] string? search = null,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] int pageNumber = 1,
        [FromQuery] int pageSize = 20,
        [FromQuery] string? sortBy = "CreatedAt",
        [FromQuery] string? sortOrder = "DESC")
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int currentUserId = GetCurrentUserId();

        // Regular employee sees only own leads unless manager/admin
        int? filterUserId = IsManagerOrAdmin ? null : currentUserId;
        int? filterAssigned = IsManagerOrAdmin ? assignedUserId : currentUserId;

        var result = await _repo.GetLeadListAsync(
            companyId,
            userId: filterUserId,
            assignedUserId: filterAssigned,
            officeLocationId: officeLocationId,
            status: status,
            productServiceId: productServiceId,
            leadSourceId: leadSourceId,
            leadSourceType: leadSourceType,
            search: search,
            fromDate: fromDate,
            toDate: toDate,
            pageNumber: pageNumber,
            pageSize: pageSize,
            sortBy: sortBy,
            sortOrder: sortOrder
        );

        return Ok(result);
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<CrmLeadDetailResponse>> GetLeadById(int id)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int currentUserId = GetCurrentUserId();
        int? restrictToUserId = IsManagerOrAdmin ? null : currentUserId;

        var lead = await _repo.GetLeadByIdAsync(companyId, id, restrictToUserId);
        if (lead == null) return NotFound(new { message = "Lead not found or inaccessible." });

        return Ok(lead);
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<CrmLeadResponse>> UpdateLead(int id, [FromBody] CreateCrmLeadRequest request)
    {
        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int userId = GetCurrentUserId();

        try
        {
            var lead = await _repo.SaveLeadAsync(companyId, userId, request, leadId: id);
            if (lead == null) return NotFound(new { message = "Lead not found." });

            return Ok(lead);
        }
        catch (Exception ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    [HttpPost("{id:int}/assign")]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<ActionResult<CrmLeadDetailResponse>> AssignLead(int id, [FromBody] AssignLeadRequest request)
    {
        if (request.NewUserId <= 0)
            return BadRequest(new { message = "Valid employee ID is required." });

        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int adminId = GetCurrentUserId();

        try
        {
            var lead = await _repo.AssignLeadAsync(companyId, adminId, id, request.NewUserId, request.Remarks);
            if (lead == null) return BadRequest(new { message = "Failed to assign lead. Verify lead and employee belong to your organization." });

            return Ok(lead);
        }
        catch (Exception ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    [HttpPost("{id:int}/follow-up")]
    public async Task<ActionResult<CrmFollowUpDto>> AddFollowUp(int id, [FromBody] CreateFollowUpRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Remarks))
            return BadRequest(new { message = "Follow-up remark is required." });

        if (string.IsNullOrWhiteSpace(request.Status))
            return BadRequest(new { message = "Status is required." });

        int companyId = GetCurrentCompanyId();
        if (companyId <= 0) return Forbid();

        int userId = GetCurrentUserId();

        try
        {
            var fu = await _repo.SaveFollowUpAsync(companyId, id, userId, request);
            if (fu == null) return NotFound(new { message = "Lead not found or inaccessible." });

            return Ok(fu);
        }
        catch (Exception ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }
}
