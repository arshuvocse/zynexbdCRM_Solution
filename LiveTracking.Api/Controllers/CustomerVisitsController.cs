using System.Security.Claims;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.SignalR;
using LiveTracking.Api.Hubs;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/visits")]
[Authorize]
public class CustomerVisitsController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IWebHostEnvironment _env;
    private readonly IHubContext<LocationHub> _hub;

    public CustomerVisitsController(LiveTrackingDbContext db, IWebHostEnvironment env, IHubContext<LocationHub> hub)
    {
        _db = db;
        _env = env;
        _hub = hub;
    }

    private int GetCurrentUserId()
    {
        var idClaim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return int.TryParse(idClaim, out int id) ? id : 0;
    }

    private async Task<List<int>> GetAccessibleEmployeeUserIdsAsync(int adminId)
    {
        if (adminId <= 0) return new List<int>();

        var assignedIds = await _db.AdminOfficeLocations
            .Where(a => a.AdminUserId == adminId)
            .Select(a => a.OfficeLocationId)
            .ToListAsync();

        if (assignedIds.Count == 0)
        {
            var singleOfficeId = await _db.Users
                .Where(u => u.UserId == adminId)
                .Select(u => u.OfficeLocationId)
                .FirstOrDefaultAsync();

            if (singleOfficeId.HasValue) assignedIds.Add(singleOfficeId.Value);
        }

        var admin = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == adminId);
        int? companyId = admin?.CompanyId;

        var query = _db.Users.Where(u => u.Role != "Admin");

        if (companyId.HasValue && companyId.Value > 0)
        {
            query = query.Where(u => u.CompanyId == companyId.Value);
        }

        if (assignedIds.Count > 0)
        {
            query = query.Where(u => u.OfficeLocationId.HasValue && assignedIds.Contains(u.OfficeLocationId.Value));
        }

        var list = await query.Select(u => u.UserId).ToListAsync();
        list.Add(adminId);
        return list;
    }

    [HttpPost]
    public async Task<ActionResult<CustomerVisitResponse>> RecordVisit([FromBody] RecordVisitRequest request)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var customer = await _db.Customers.FindAsync(request.CustomerId);
        if (customer == null) return NotFound(new { message = "Customer not found." });

        string? photoPath = null;
        if (!string.IsNullOrWhiteSpace(request.ShopPhotoBase64))
        {
            try
            {
                byte[] bytes = Convert.FromBase64String(request.ShopPhotoBase64);
                string uploadsFolder = Path.Combine(_env.ContentRootPath, "uploads");
                if (!Directory.Exists(uploadsFolder)) Directory.CreateDirectory(uploadsFolder);

                string fileName = $"visit_{Guid.NewGuid()}_shop.jpg";
                string fullPath = Path.Combine(uploadsFolder, fileName);
                await System.IO.File.WriteAllBytesAsync(fullPath, bytes);
                photoPath = $"/uploads/{fileName}";
            }
            catch
            {
                photoPath = null;
            }
        }

        var visit = new CustomerVisit
        {
            CustomerId = request.CustomerId,
            UserId = userId,
            VisitDate = DateTime.UtcNow,
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            Remarks = request.Remarks?.Trim(),
            VisitStatus = string.IsNullOrWhiteSpace(request.VisitStatus) ? "Completed" : request.VisitStatus.Trim(),
            NextFollowUpDate = request.NextFollowUpDate,
            ShopPhotoPath = photoPath,
            IsFollowUpCompleted = false
        };

        _db.CustomerVisits.Add(visit);
        await _db.SaveChangesAsync();

        var user = await _db.Users.FindAsync(userId);

        if (user != null && user.CompanyId.HasValue)
        {
            string userName = user.FullName.Length > 0 ? user.FullName : user.Username;
            string visitSubtitle = string.IsNullOrWhiteSpace(visit.Remarks)
                ? $"{customer.Name} • {visit.VisitStatus}"
                : $"{customer.Name} • {visit.VisitStatus} • {visit.Remarks}";

            var activityDto = new LiveTeamActivityDto(
                (int)(visit.VisitId % int.MaxValue) + 500000,
                user.CompanyId.Value,
                user.UserId,
                userName,
                user.Role,
                "CustomerVisit",
                "CustomerVisit",
                $"{userName} recorded customer visit",
                visitSubtitle,
                "#EA580C",
                null,
                DateTime.UtcNow
            );

            _ = Task.Run(async () =>
            {
                try
                {
                    await _hub.Clients.Group(LocationHub.CompanyAdminsGroup(user.CompanyId.Value)).SendAsync("ReceiveTeamActivity", activityDto);
                    await _hub.Clients.Group(LocationHub.CompanyAllGroup(user.CompanyId.Value)).SendAsync("ReceiveTeamActivity", activityDto);
                }
                catch { }
            });
        }

        return Ok(new CustomerVisitResponse
        {
            VisitId = visit.VisitId,
            CustomerId = customer.CustomerId,
            CustomerName = customer.Name,
            Mobile = customer.Mobile,
            Address = customer.Address,
            UserId = userId,
            UserName = user?.FullName ?? user?.Username ?? "User",
            VisitDate = visit.VisitDate,
            Latitude = visit.Latitude,
            Longitude = visit.Longitude,
            Remarks = visit.Remarks,
            VisitStatus = visit.VisitStatus,
            NextFollowUpDate = visit.NextFollowUpDate,
            ShopPhotoPath = visit.ShopPhotoPath,
            IsFollowUpCompleted = visit.IsFollowUpCompleted
        });
    }

    [HttpGet("my-visits")]
    public async Task<ActionResult<List<CustomerVisitResponse>>> GetMyVisits([FromQuery] int? customerId = null, [FromQuery] int? targetUserId = null)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var query = _db.CustomerVisits
            .Include(v => v.Customer)
            .Include(v => v.User)
            .AsQueryable();

        if (User.IsInRole("Admin"))
        {
            var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(userId);
            if (targetUserId.HasValue && targetUserId.Value > 0)
                query = query.Where(v => v.UserId == targetUserId.Value && accessibleUserIds.Contains(v.UserId));
            else
                query = query.Where(v => accessibleUserIds.Contains(v.UserId));
        }
        else
        {
            query = query.Where(v => v.UserId == userId);
        }

        if (customerId.HasValue)
        {
            query = query.Where(v => v.CustomerId == customerId.Value);
        }

        var visits = await query
            .OrderByDescending(v => v.VisitDate)
            .Take(100)
            .ToListAsync();

        var response = visits.Select(v => new CustomerVisitResponse
        {
            VisitId = v.VisitId,
            CustomerId = v.CustomerId,
            CustomerName = v.Customer?.Name ?? "Unknown",
            Mobile = v.Customer?.Mobile ?? "",
            Address = v.Customer?.Address ?? "",
            UserId = v.UserId,
            UserName = v.User?.FullName ?? v.User?.Username ?? "User",
            VisitDate = v.VisitDate,
            Latitude = v.Latitude,
            Longitude = v.Longitude,
            Remarks = v.Remarks,
            VisitStatus = v.VisitStatus,
            NextFollowUpDate = v.NextFollowUpDate,
            ShopPhotoPath = v.ShopPhotoPath,
            IsFollowUpCompleted = v.IsFollowUpCompleted
        }).ToList();

        return Ok(response);
    }

    [HttpGet("followups")]
    public async Task<ActionResult<List<FollowUpResponse>>> GetFollowUps([FromQuery] string? category = null, [FromQuery] int? targetUserId = null)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var now = DateTime.UtcNow;
        var todayStart = now.Date;
        var todayEnd = todayStart.AddDays(1);
        var tomorrowEnd = todayStart.AddDays(2);

        var query = _db.CustomerVisits
            .Include(v => v.Customer)
            .Include(v => v.User)
            .Where(v => v.NextFollowUpDate.HasValue && !v.IsFollowUpCompleted);

        if (User.IsInRole("Admin"))
        {
            var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(userId);
            if (targetUserId.HasValue && targetUserId.Value > 0)
                query = query.Where(v => v.UserId == targetUserId.Value && accessibleUserIds.Contains(v.UserId));
            else
                query = query.Where(v => accessibleUserIds.Contains(v.UserId));
        }
        else
        {
            query = query.Where(v => v.UserId == userId);
        }

        var rawFollowUps = await query
            .OrderBy(v => v.NextFollowUpDate)
            .ToListAsync();

        var list = new List<FollowUpResponse>();
        foreach (var f in rawFollowUps)
        {
            var fDate = f.NextFollowUpDate!.Value;
            string itemCategory;
            if (fDate < todayStart)
                itemCategory = "Overdue";
            else if (fDate >= todayStart && fDate < todayEnd)
                itemCategory = "Today";
            else if (fDate >= todayEnd && fDate < tomorrowEnd)
                itemCategory = "Tomorrow";
            else
                itemCategory = "Upcoming";

            if (string.IsNullOrWhiteSpace(category) || category.Equals("All", StringComparison.OrdinalIgnoreCase) || category.Equals(itemCategory, StringComparison.OrdinalIgnoreCase))
            {
                list.Add(new FollowUpResponse
                {
                    VisitId = f.VisitId,
                    CustomerId = f.CustomerId,
                    CustomerName = f.Customer?.Name ?? "Customer",
                    Mobile = f.Customer?.Mobile ?? "",
                    Address = f.Customer?.Address ?? "",
                    FollowUpDate = fDate,
                    Category = itemCategory,
                    IsCompleted = f.IsFollowUpCompleted,
                    Remarks = f.Remarks
                });
            }
        }

        return Ok(list);
    }

    [HttpPut("{id:long}/complete-followup")]
    public async Task<IActionResult> CompleteFollowUp(long id)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var visit = await _db.CustomerVisits.FirstOrDefaultAsync(v => v.VisitId == id && v.UserId == userId);
        if (visit == null) return NotFound(new { message = "Follow-up record not found." });

        visit.IsFollowUpCompleted = true;
        await _db.SaveChangesAsync();

        return Ok(new { message = "Follow-up marked as completed." });
    }

    [HttpGet("dashboard-stats")]
    public async Task<ActionResult<FieldUserDashboardStats>> GetDashboardStats()
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        // Calculate today's range in Bangladesh Standard Time (UTC+6)
        var localNow = DateTime.UtcNow.AddHours(6);
        var todayStartUtc = localNow.Date.AddHours(-6);
        var tomorrowStartUtc = todayStartUtc.AddDays(1);

        // Attendance records today
        var todayAttendance = await _db.AttendanceRecords
            .Where(a => a.UserId == userId && a.RecordedAtUtc >= todayStartUtc && a.RecordedAtUtc < tomorrowStartUtc)
            .OrderBy(a => a.RecordedAtUtc)
            .ToListAsync();

        var firstIn = todayAttendance.FirstOrDefault(a => a.Type.Equals("In", StringComparison.OrdinalIgnoreCase));
        var latestRecord = todayAttendance.LastOrDefault();
        var lastOut = todayAttendance.LastOrDefault(a => a.Type.Equals("Out", StringComparison.OrdinalIgnoreCase));

        string attendanceStatus = "Duty Not Started";
        string workingTimeStr = "0h 0m";
        string? punchInTimeStr = null;
        string? punchOutTimeStr = null;

        if (firstIn != null)
        {
            var punchInLocal = firstIn.RecordedAtUtc.AddHours(6);
            punchInTimeStr = punchInLocal.ToString("hh:mm tt");
        }

        if (lastOut != null)
        {
            var punchOutLocal = lastOut.RecordedAtUtc.AddHours(6);
            punchOutTimeStr = punchOutLocal.ToString("hh:mm tt");
        }

        if (latestRecord != null)
        {
            if (latestRecord.Type.Equals("In", StringComparison.OrdinalIgnoreCase))
            {
                attendanceStatus = "Duty In";
                var duration = DateTime.UtcNow - latestRecord.RecordedAtUtc;
                var totalMinutes = (int)Math.Max(0, duration.TotalMinutes);
                workingTimeStr = $"{totalMinutes / 60}h {totalMinutes % 60}m";
            }
            else
            {
                attendanceStatus = "Duty Out";
                if (firstIn != null)
                {
                    var duration = latestRecord.RecordedAtUtc - firstIn.RecordedAtUtc;
                    var totalMinutes = (int)Math.Max(0, duration.TotalMinutes);
                    workingTimeStr = $"{totalMinutes / 60}h {totalMinutes % 60}m";
                }
            }
        }

        // Today's Visits Count
        int todayVisitsCount = await _db.CustomerVisits
            .CountAsync(v => v.UserId == userId && v.VisitDate >= todayStartUtc && v.VisitDate < tomorrowStartUtc);

        // Pending Follow-ups Count (Overdue + Today + Future)
        int pendingFollowups = await _db.CustomerVisits
            .CountAsync(v => v.UserId == userId && v.NextFollowUpDate.HasValue && !v.IsFollowUpCompleted);

        return Ok(new FieldUserDashboardStats
        {
            AttendanceStatus = attendanceStatus,
            PunchInTime = punchInTimeStr,
            PunchOutTime = punchOutTimeStr,
            TodayWorkingTime = workingTimeStr,
            TodayVisitsCount = todayVisitsCount,
            PendingFollowUpsCount = pendingFollowups,
            GpsTrackingActive = true
        });
    }
}
