using LiveTracking.Api.Data;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class HolidaysController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;

    public HolidaysController(LiveTrackingDbContext db)
    {
        _db = db;
    }

    [HttpGet]
    public async Task<ActionResult<List<Holiday>>> GetHolidays(
        [FromQuery] int? year,
        [FromQuery] int? month,
        [FromQuery] bool includeInactive = true)
    {
        var query = _db.Holidays.AsQueryable();

        if (!includeInactive)
        {
            query = query.Where(h => h.IsActive);
        }

        if (year.HasValue)
        {
            query = query.Where(h => h.Year == year.Value || h.IsRecurring);
        }

        if (month.HasValue && month.Value >= 1 && month.Value <= 12)
        {
            query = query.Where(h => h.Date.Month == month.Value);
        }

        var holidays = await query
            .OrderBy(h => h.Date)
            .ToListAsync();

        return Ok(holidays);
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<Holiday>> GetHolidayById(int id)
    {
        var holiday = await _db.Holidays.FindAsync(id);
        if (holiday == null) return NotFound(new { message = "Holiday not found." });
        return Ok(holiday);
    }

    [HttpPost]
    [Authorize(Roles = "Admin")]
    public ActionResult<Holiday> CreateHoliday([FromBody] CreateOrUpdateHolidayRequest request)
    {
        return BadRequest(new { message = "ছুটির তালিকা শুধুমাত্র ডাটাবেজ থেকে নিয়ন্ত্রণযোগ্য। অ্যাপ থেকে নতুন ছুটি যুক্ত করার অনুমতি নেই।" });
    }

    [HttpPut("{id}")]
    [Authorize(Roles = "Admin")]
    public ActionResult<Holiday> UpdateHoliday(int id, [FromBody] CreateOrUpdateHolidayRequest request)
    {
        return BadRequest(new { message = "ছুটির তালিকা শুধুমাত্র ডাটাবেজ থেকে নিয়ন্ত্রণযোগ্য। অ্যাপ থেকে ছুটি এডিট করার অনুমতি নেই।" });
    }

    [HttpPatch("{id}/toggle-status")]
    [Authorize(Roles = "Admin")]
    public ActionResult<Holiday> ToggleHolidayStatus(int id)
    {
        return BadRequest(new { message = "ছুটির তালিকা শুধুমাত্র ডাটাবেজ থেকে নিয়ন্ত্রণযোগ্য।" });
    }

    [HttpDelete("{id}")]
    [Authorize(Roles = "Admin")]
    public ActionResult DeleteHoliday(int id)
    {
        return BadRequest(new { message = "ছুটির তালিকা শুধুমাত্র ডাটাবেজ থেকে নিয়ন্ত্রণযোগ্য। অ্যাপ থেকে ছুটি মুছে ফেলার অনুমতি নেই।" });
    }
}

public record CreateOrUpdateHolidayRequest(
    string Name,
    string Date,
    bool IsRecurring = false,
    bool IsActive = true,
    string? Description = null
);
