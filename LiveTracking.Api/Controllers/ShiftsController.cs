using System.Security.Claims;
using LiveTracking.Api.Data;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class ShiftsController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;

    public ShiftsController(LiveTrackingDbContext db)
    {
        _db = db;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return int.TryParse(claim, out int id) ? id : 0;
    }

    [HttpGet]
    public async Task<ActionResult<List<Shift>>> GetShifts()
    {
        var currentUserId = GetCurrentUserId();
        var user = currentUserId > 0 ? await _db.Users.FindAsync(currentUserId) : null;
        int? companyId = user?.CompanyId;

        var query = _db.Shifts.AsQueryable();
        if (companyId.HasValue)
        {
            query = query.Where(s => s.CompanyId == companyId.Value || s.CompanyId == null);
        }

        var shifts = await query
            .OrderByDescending(s => s.IsDefault)
            .ThenBy(s => s.ShiftName)
            .ToListAsync();

        // If no shifts exist, create a default "General Shift" for this company
        if (!shifts.Any())
        {
            var defaultShift = new Shift
            {
                ShiftName = "General Shift",
                StartTime = "09:00:00",
                EndTime = "18:00:00",
                GracePeriodMinutes = 15,
                IsDefault = true,
                IsActive = true,
                CompanyId = companyId,
                CreatedByAdminId = currentUserId,
                CreatedAtUtc = DateTime.UtcNow
            };
            _db.Shifts.Add(defaultShift);
            await _db.SaveChangesAsync();
            shifts.Add(defaultShift);
        }

        return Ok(shifts);
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<Shift>> GetShiftById(int id)
    {
        var shift = await _db.Shifts.FindAsync(id);
        if (shift == null) return NotFound(new { message = "Shift not found." });
        return Ok(shift);
    }

    [HttpPost]
    [Authorize(Roles = "Admin")]
    public ActionResult<Shift> CreateShift([FromBody] CreateShiftRequest request)
    {
        return BadRequest(new { message = "শিফট তৈরি করার অনুমতি নেই (Adding new shifts is disabled)." });
    }

    [HttpPut("{id}")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<Shift>> UpdateShift(int id, [FromBody] UpdateShiftRequest request)
    {
        var shift = await _db.Shifts.FindAsync(id);
        if (shift == null) return NotFound(new { message = "Shift not found." });

        if (!string.IsNullOrWhiteSpace(request.ShiftName))
            shift.ShiftName = request.ShiftName.Trim();

        if (!string.IsNullOrWhiteSpace(request.StartTime))
            shift.StartTime = request.StartTime.Trim();

        if (!string.IsNullOrWhiteSpace(request.EndTime))
            shift.EndTime = request.EndTime.Trim();

        if (request.GracePeriodMinutes >= 0)
            shift.GracePeriodMinutes = request.GracePeriodMinutes;

        if (request.IsDefault)
        {
            var currentDefaults = await _db.Shifts
                .Where(s => s.IsDefault && s.ShiftId != id && s.CompanyId == shift.CompanyId)
                .ToListAsync();
            foreach (var s in currentDefaults) s.IsDefault = false;
            shift.IsDefault = true;
        }
        else
        {
            shift.IsDefault = request.IsDefault;
        }

        shift.IsActive = request.IsActive;

        await _db.SaveChangesAsync();
        return Ok(shift);
    }

    [HttpPut("{id}/set-default")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<Shift>> SetDefaultShift(int id)
    {
        var target = await _db.Shifts.FindAsync(id);
        if (target == null) return NotFound(new { message = "Shift not found." });

        var companyShifts = await _db.Shifts
            .Where(s => s.CompanyId == target.CompanyId)
            .ToListAsync();
        foreach (var s in companyShifts)
        {
            s.IsDefault = (s.ShiftId == id);
        }

        await _db.SaveChangesAsync();
        return Ok(target);
    }

    [HttpDelete("{id}")]
    [Authorize(Roles = "Admin")]
    public ActionResult DeleteShift(int id)
    {
        return BadRequest(new { message = "শিফট মুছে ফেলার অনুমতি নেই (Deleting shifts is disabled)." });
    }
}

public record CreateShiftRequest(
    string ShiftName,
    string StartTime,
    string EndTime,
    int GracePeriodMinutes = 15,
    bool IsDefault = false
);

public record UpdateShiftRequest(
    string ShiftName,
    string StartTime,
    string EndTime,
    int GracePeriodMinutes = 15,
    bool IsDefault = false,
    bool IsActive = true
);
