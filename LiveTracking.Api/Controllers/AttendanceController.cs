using System.Security.Claims;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Hubs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.SignalR;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class AttendanceController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IWebHostEnvironment _env;
    private readonly IHubContext<LocationHub> _hub;

    public AttendanceController(LiveTrackingDbContext db, IWebHostEnvironment env, IHubContext<LocationHub> hub)
    {
        _db = db;
        _env = env;
        _hub = hub;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return int.TryParse(claim, out int id) ? id : 0;
    }

    private async Task<List<int>> GetAdminAssignedOfficeIdsAsync(int adminId)
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

            if (singleOfficeId.HasValue)
            {
                assignedIds.Add(singleOfficeId.Value);
            }
        }

        return assignedIds;
    }

    private async Task<List<int>> GetAccessibleEmployeeUserIdsAsync(int adminId)
    {
        var admin = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == adminId);
        int? companyId = admin?.CompanyId;
        var assignedOfficeIds = await GetAdminAssignedOfficeIdsAsync(adminId);
        var query = _db.Users.Where(u => u.Role != "Admin");

        if (companyId.HasValue && companyId.Value > 0)
        {
            query = query.Where(u => u.CompanyId == companyId.Value);
        }

        if (assignedOfficeIds.Count > 0)
        {
            query = query.Where(u => u.OfficeLocationId.HasValue && assignedOfficeIds.Contains(u.OfficeLocationId.Value));
        }

        return await query.Select(u => u.UserId).ToListAsync();
    }

    private async Task<(string status, string shiftName)> CalculatePunchStatusAsync(int userId, string type, DateTime recordedAtUtc)
    {
        var user = await _db.Users.Include(u => u.Shift).FirstOrDefaultAsync(u => u.UserId == userId);
        
        Shift? shift = user?.Shift;
        if (shift == null && user?.CompanyId.HasValue == true && user.CompanyId.Value > 0)
        {
            shift = await _db.Shifts.FirstOrDefaultAsync(s => s.CompanyId == user.CompanyId.Value && s.IsDefault && s.IsActive)
                 ?? await _db.Shifts.FirstOrDefaultAsync(s => s.CompanyId == user.CompanyId.Value && s.IsActive);
        }

        if (shift == null)
        {
            shift = await _db.Shifts.FirstOrDefaultAsync(s => s.IsDefault && s.IsActive) 
                 ?? await _db.Shifts.FirstOrDefaultAsync(s => s.IsActive);
        }

        if (shift == null)
        {
            return (type == "In" ? "On Time" : "Completed", "General Shift");
        }

        var localTime = recordedAtUtc.AddHours(6); // Bangladesh Standard Time (UTC+6)
        var localTimeOfDay = localTime.TimeOfDay;

        if (string.Equals(type, "In", StringComparison.OrdinalIgnoreCase))
        {
            if (TimeSpan.TryParse(shift.StartTime, out var startTime))
            {
                var lateThreshold = startTime.Add(TimeSpan.FromMinutes(shift.GracePeriodMinutes));
                if (localTimeOfDay <= lateThreshold)
                {
                    return ("On Time", shift.ShiftName);
                }
                else
                {
                    var lateMinutes = (int)Math.Max(1, (localTimeOfDay - startTime).TotalMinutes);
                    int lateHours = lateMinutes / 60;
                    int lateRemMins = lateMinutes % 60;
                    return ($"Late ({lateHours:D2}:{lateRemMins:D2})", shift.ShiftName);
                }
            }
            return ("On Time", shift.ShiftName);
        }
        else // "Out"
        {
            if (TimeSpan.TryParse(shift.EndTime, out var endTime))
            {
                if (localTimeOfDay < endTime)
                {
                    var earlyMinutes = (int)Math.Max(1, (endTime - localTimeOfDay).TotalMinutes);
                    int earlyHours = earlyMinutes / 60;
                    int earlyRemMins = earlyMinutes % 60;
                    return ($"Early Out ({earlyHours:D2}:{earlyRemMins:D2})", shift.ShiftName);
                }
                else
                {
                    return ("Completed", shift.ShiftName);
                }
            }
            return ("Completed", shift.ShiftName);
        }
    }

    [HttpGet("today")]
    public async Task<ActionResult<TodayAttendanceStatusDto>> GetTodayStatus()
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var todayLocalDate = DateTime.UtcNow.AddHours(6).Date;
        var todayStartUtc = todayLocalDate.AddHours(-6);
        var todayEndUtc = todayStartUtc.AddDays(1);

        var todayRecords = await _db.AttendanceRecords
            .Where(a => a.UserId == userId && a.RecordedAtUtc >= todayStartUtc && a.RecordedAtUtc < todayEndUtc)
            .OrderBy(a => a.RecordedAtUtc)
            .ToListAsync();

        var inRecord = todayRecords.FirstOrDefault(r => r.Type == "In");
        var outRecord = todayRecords.FirstOrDefault(r => r.Type == "Out");

        var user = await _db.Users.Include(u => u.Shift).FirstOrDefaultAsync(u => u.UserId == userId);
        string? shiftName = inRecord?.ShiftName ?? outRecord?.ShiftName ?? user?.Shift?.ShiftName;
        if (string.IsNullOrWhiteSpace(shiftName) && user?.CompanyId.HasValue == true && user.CompanyId.Value > 0)
        {
            var companyShift = await _db.Shifts.FirstOrDefaultAsync(s => s.CompanyId == user.CompanyId.Value && s.IsDefault && s.IsActive)
                            ?? await _db.Shifts.FirstOrDefaultAsync(s => s.CompanyId == user.CompanyId.Value && s.IsActive);
            shiftName = companyShift?.ShiftName;
        }
        shiftName ??= "General Shift";

        return Ok(new TodayAttendanceStatusDto
        {
            HasPunchedIn = inRecord != null,
            PunchInTime = inRecord != null ? inRecord.RecordedAtUtc.AddHours(6).ToString("hh:mm tt") : null,
            PunchInStatus = inRecord?.Status,
            PunchInSelfieUrl = inRecord?.SelfieUrl,

            HasPunchedOut = outRecord != null,
            PunchOutTime = outRecord != null ? outRecord.RecordedAtUtc.AddHours(6).ToString("hh:mm tt") : null,
            PunchOutStatus = outRecord?.Status,
            PunchOutSelfieUrl = outRecord?.SelfieUrl,

            ShiftName = shiftName
        });
    }

    [HttpPost("punch-in")]
    [Consumes("multipart/form-data")]
    public async Task<ActionResult<AttendanceResponseDto>> PunchIn([FromForm] AttendancePunchRequest request)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var todayLocalDate = DateTime.UtcNow.AddHours(6).Date;
        var todayStartUtc = todayLocalDate.AddHours(-6);
        var todayEndUtc = todayStartUtc.AddDays(1);

        var alreadyIn = await _db.AttendanceRecords
            .AnyAsync(a => a.UserId == userId && a.Type == "In" && a.RecordedAtUtc >= todayStartUtc && a.RecordedAtUtc < todayEndUtc);
        if (alreadyIn)
        {
            return BadRequest("You have already recorded Duty In for today.");
        }

        var selfieUrl = await SaveSelfieAsync(request.Selfie, userId);
        var (status, shiftName) = await CalculatePunchStatusAsync(userId, "In", DateTime.UtcNow);

        var record = new AttendanceRecord
        {
            UserId = userId,
            Type = "In",
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            IsWithinGeofence = true,
            SelfieUrl = selfieUrl,
            Status = status,
            ShiftName = shiftName,
            RecordedAtUtc = DateTime.UtcNow
        };

        _db.AttendanceRecords.Add(record);
        await _db.SaveChangesAsync();

        var user = await _db.Users.FindAsync(userId);
        var userName = user?.FullName ?? user?.Username ?? "Officer";
        var localTime = record.RecordedAtUtc.AddHours(6).ToString("hh:mm tt");

        // Send Push Notification to Admins
        try
        {
            var notification = new NotificationItem
            {
                UserId = null,
                CompanyId = user?.CompanyId,
                TargetRole = "Admin",
                Title = "🟢 Duty In Alert",
                Message = $"{userName} has recorded Duty In at {localTime} ({status} - {shiftName}).",
                Type = "Attendance",
                ReferenceId = record.AttendanceId.ToString(),
                IsRead = false,
                CreatedAtUtc = DateTime.UtcNow
            };
            _db.Notifications.Add(notification);
            await _db.SaveChangesAsync();

            var notifDto = new NotificationDto
            {
                NotificationId = notification.NotificationId,
                UserId = null,
                CompanyId = notification.CompanyId,
                TargetRole = "Admin",
                Title = notification.Title,
                Message = notification.Message,
                Type = notification.Type,
                ReferenceId = notification.ReferenceId,
                IsRead = false,
                CreatedAtUtc = notification.CreatedAtUtc
            };

            if (user?.CompanyId.HasValue == true && user.CompanyId.Value > 0)
            {
                await _hub.Clients.Group(LocationHub.CompanyAdminsGroup(user.CompanyId.Value)).SendAsync("ReceiveNotification", notifDto);
            }
            else
            {
                await _hub.Clients.Group(LocationHub.AdminsGroup).SendAsync("ReceiveNotification", notifDto);
            }
        }
        catch { }

        return Ok(new AttendanceResponseDto(
            record.AttendanceId,
            record.UserId,
            userName,
            record.Type,
            record.RecordedAtUtc.ToString("o"),
            record.Latitude,
            record.Longitude,
            record.IsWithinGeofence,
            record.SelfieUrl,
            record.Status,
            record.ShiftName
        ));
    }

    [HttpPost("punch-out")]
    [Consumes("multipart/form-data")]
    public async Task<ActionResult<AttendanceResponseDto>> PunchOut([FromForm] AttendancePunchRequest request)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var todayLocalDate = DateTime.UtcNow.AddHours(6).Date;
        var todayStartUtc = todayLocalDate.AddHours(-6);
        var todayEndUtc = todayStartUtc.AddDays(1);

        var alreadyOut = await _db.AttendanceRecords
            .AnyAsync(a => a.UserId == userId && a.Type == "Out" && a.RecordedAtUtc >= todayStartUtc && a.RecordedAtUtc < todayEndUtc);
        if (alreadyOut)
        {
            return BadRequest("You have already recorded Duty Out for today.");
        }

        var selfieUrl = await SaveSelfieAsync(request.Selfie, userId);
        var (status, shiftName) = await CalculatePunchStatusAsync(userId, "Out", DateTime.UtcNow);

        var record = new AttendanceRecord
        {
            UserId = userId,
            Type = "Out",
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            IsWithinGeofence = true,
            SelfieUrl = selfieUrl,
            Status = status,
            ShiftName = shiftName,
            RecordedAtUtc = DateTime.UtcNow
        };

        _db.AttendanceRecords.Add(record);
        await _db.SaveChangesAsync();

        var user = await _db.Users.FindAsync(userId);
        var userName = user?.FullName ?? user?.Username ?? "Officer";
        var localTime = record.RecordedAtUtc.AddHours(6).ToString("hh:mm tt");

        // Send Push Notification to Admins
        try
        {
            var notification = new NotificationItem
            {
                UserId = null,
                CompanyId = user?.CompanyId,
                TargetRole = "Admin",
                Title = "🔴 Duty Out Alert",
                Message = $"{userName} has recorded Duty Out at {localTime} ({status} - {shiftName}).",
                Type = "Attendance",
                ReferenceId = record.AttendanceId.ToString(),
                IsRead = false,
                CreatedAtUtc = DateTime.UtcNow
            };
            _db.Notifications.Add(notification);
            await _db.SaveChangesAsync();

            var notifDto = new NotificationDto
            {
                NotificationId = notification.NotificationId,
                UserId = null,
                CompanyId = notification.CompanyId,
                TargetRole = "Admin",
                Title = notification.Title,
                Message = notification.Message,
                Type = notification.Type,
                ReferenceId = notification.ReferenceId,
                IsRead = false,
                CreatedAtUtc = notification.CreatedAtUtc
            };

            if (user?.CompanyId.HasValue == true && user.CompanyId.Value > 0)
            {
                await _hub.Clients.Group(LocationHub.CompanyAdminsGroup(user.CompanyId.Value)).SendAsync("ReceiveNotification", notifDto);
            }
            else
            {
                await _hub.Clients.Group(LocationHub.AdminsGroup).SendAsync("ReceiveNotification", notifDto);
            }
        }
        catch { }

        return Ok(new AttendanceResponseDto(
            record.AttendanceId,
            record.UserId,
            userName,
            record.Type,
            record.RecordedAtUtc.ToString("o"),
            record.Latitude,
            record.Longitude,
            record.IsWithinGeofence,
            record.SelfieUrl,
            record.Status,
            record.ShiftName
        ));
    }

    [HttpGet("history")]
    public async Task<ActionResult<List<AttendanceResponseDto>>> GetMyAttendanceHistory(
        [FromQuery] int? month,
        [FromQuery] int? year,
        [FromQuery] string? from,
        [FromQuery] string? to)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var query = _db.AttendanceRecords
            .Include(a => a.User)
            .Where(a => a.UserId == userId);

        if (year.HasValue && year.Value > 0)
        {
            query = query.Where(a => a.RecordedAtUtc.Year == year.Value);
        }

        if (month.HasValue && month.Value >= 1 && month.Value <= 12)
        {
            query = query.Where(a => a.RecordedAtUtc.Month == month.Value);
        }

        if (DateTime.TryParse(from, out DateTime fromDate))
        {
            query = query.Where(a => a.RecordedAtUtc >= fromDate.ToUniversalTime());
        }

        if (DateTime.TryParse(to, out DateTime toDate))
        {
            query = query.Where(a => a.RecordedAtUtc <= toDate.ToUniversalTime());
        }

        var records = await query
            .OrderByDescending(a => a.RecordedAtUtc)
            .Take(500)
            .Select(a => new AttendanceResponseDto(
                a.AttendanceId,
                a.UserId,
                a.User != null ? a.User.FullName : "Officer",
                a.Type,
                a.RecordedAtUtc.ToString("o"),
                a.Latitude,
                a.Longitude,
                a.IsWithinGeofence,
                a.SelfieUrl,
                a.Status ?? "On Time",
                a.ShiftName ?? "General Shift"
            ))
            .ToListAsync();

        return Ok(records);
    }

    [HttpGet("admin")]
    public async Task<ActionResult<List<AttendanceResponseDto>>> GetAllAttendance(
        [FromQuery] int? userId,
        [FromQuery] int? month,
        [FromQuery] int? year,
        [FromQuery] string? from,
        [FromQuery] string? to)
    {
        int currentAdminId = GetCurrentUserId();
        if (currentAdminId <= 0) return Unauthorized();

        var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(currentAdminId);

        var query = _db.AttendanceRecords
            .Include(a => a.User)
            .AsQueryable();

        if (userId.HasValue && userId.Value > 0)
        {
            query = query.Where(a => a.UserId == userId.Value && accessibleUserIds.Contains(a.UserId));
        }
        else
        {
            query = query.Where(a => accessibleUserIds.Contains(a.UserId));
        }

        if (year.HasValue && year.Value > 0)
        {
            query = query.Where(a => a.RecordedAtUtc.Year == year.Value);
        }

        if (month.HasValue && month.Value >= 1 && month.Value <= 12)
        {
            query = query.Where(a => a.RecordedAtUtc.Month == month.Value);
        }

        if (DateTime.TryParse(from, out DateTime fromDate))
        {
            query = query.Where(a => a.RecordedAtUtc >= fromDate.ToUniversalTime());
        }

        if (DateTime.TryParse(to, out DateTime toDate))
        {
            query = query.Where(a => a.RecordedAtUtc <= toDate.ToUniversalTime());
        }

        var records = await query
            .OrderByDescending(a => a.RecordedAtUtc)
            .Take(500)
            .Select(a => new AttendanceResponseDto(
                a.AttendanceId,
                a.UserId,
                a.User != null ? a.User.FullName : "Officer",
                a.Type,
                a.RecordedAtUtc.ToString("o"),
                a.Latitude,
                a.Longitude,
                a.IsWithinGeofence,
                a.SelfieUrl,
                a.Status ?? "On Time",
                a.ShiftName ?? "General Shift"
            ))
            .ToListAsync();

        return Ok(records);
    }

    [HttpGet("admin/monthly-summary")]
    public async Task<ActionResult<List<EmployeeMonthlyAttendanceSummaryDto>>> GetMonthlyAttendanceSummary(
        [FromQuery] int? year,
        [FromQuery] int? month,
        [FromQuery] int? userId)
    {
        int currentAdminId = GetCurrentUserId();
        if (currentAdminId <= 0) return Unauthorized();

        var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(currentAdminId);

        var localNow = DateTime.UtcNow.AddHours(6);
        int targetYear = (year.HasValue && year.Value >= 2000) ? year.Value : localNow.Year;
        int targetMonth = (month.HasValue && month.Value >= 1 && month.Value <= 12) ? month.Value : localNow.Month;

        var monthStartDate = new DateTime(targetYear, targetMonth, 1);
        var monthEndDate = monthStartDate.AddMonths(1).AddDays(-1);

        // Effective calculation end date (up to today if current month)
        var effectiveEndDate = (targetYear == localNow.Year && targetMonth == localNow.Month)
            ? (localNow.Date < monthEndDate ? localNow.Date : monthEndDate)
            : monthEndDate;

        var holidays = await _db.Holidays
            .Where(h => h.IsActive)
            .ToListAsync();

        // Calculate total working days in this month (up to effective end date) - Friday only as weekly holiday
        int totalWorkingDays = 0;
        for (var date = monthStartDate; date <= effectiveEndDate; date = date.AddDays(1))
        {
            bool isWeekend = (date.DayOfWeek == DayOfWeek.Friday);
            if (isWeekend) continue;

            bool isGovtHoliday = holidays.Any(h =>
                (h.Date.Date == date) ||
                (h.IsRecurring && h.Date.Month == date.Month && h.Date.Day == date.Day)
            );
            if (isGovtHoliday) continue;

            totalWorkingDays++;
        }

        // Query target users
        var userQuery = _db.Users
            .Include(u => u.Shift)
            .Where(u => u.IsActive && u.Role != "Admin" && accessibleUserIds.Contains(u.UserId));

        if (userId.HasValue && userId.Value > 0)
        {
            userQuery = userQuery.Where(u => u.UserId == userId.Value);
        }

        var users = await userQuery.OrderBy(u => u.FullName).ToListAsync();

        var companyDefaultShifts = await _db.Shifts
            .Where(s => s.IsActive && s.IsDefault)
            .ToListAsync();

        // UTC range for month queries
        var monthStartUtc = monthStartDate.AddHours(-6);
        var monthEndUtc = monthEndDate.AddDays(1).AddHours(-6);

        var summaries = new List<EmployeeMonthlyAttendanceSummaryDto>();

        foreach (var u in users)
        {
            var records = await _db.AttendanceRecords
                .Where(a => a.UserId == u.UserId && a.RecordedAtUtc >= monthStartUtc && a.RecordedAtUtc < monthEndUtc)
                .ToListAsync();

            // Group by local day
            var dailyPunches = records
                .GroupBy(a => a.RecordedAtUtc.AddHours(6).Date)
                .ToList();

            int presentDays = dailyPunches.Count(g => g.Any(a => a.Type == "In"));
            int onTimeDays = 0;
            int lateDays = 0;
            int earlyOutDays = 0;
            double totalPresenceMinutes = 0;

            foreach (var group in dailyPunches)
            {
                var firstIn = group.Where(a => a.Type == "In").OrderBy(a => a.RecordedAtUtc).FirstOrDefault();
                var lastOut = group.Where(a => a.Type == "Out").OrderByDescending(a => a.RecordedAtUtc).FirstOrDefault();

                if (firstIn != null)
                {
                    if (firstIn.Status != null && firstIn.Status.StartsWith("Late", StringComparison.OrdinalIgnoreCase))
                    {
                        lateDays++;
                    }
                    else
                    {
                        onTimeDays++;
                    }

                    if (lastOut != null && lastOut.RecordedAtUtc > firstIn.RecordedAtUtc)
                    {
                        totalPresenceMinutes += (lastOut.RecordedAtUtc - firstIn.RecordedAtUtc).TotalMinutes;
                    }
                    else if (lastOut == null && group.Key == DateTime.UtcNow.AddHours(6).Date)
                    {
                        var ongoingMins = (DateTime.UtcNow - firstIn.RecordedAtUtc).TotalMinutes;
                        if (ongoingMins > 0) totalPresenceMinutes += ongoingMins;
                    }
                }

                if (lastOut != null && lastOut.Status != null && lastOut.Status.StartsWith("Early", StringComparison.OrdinalIgnoreCase))
                {
                    earlyOutDays++;
                }
            }

            int presenceHours = (int)(totalPresenceMinutes / 60);
            int presenceMins = (int)(totalPresenceMinutes % 60);
            string totalPresenceTimeFormatted = $"{presenceHours:D2}:{presenceMins:D2}";

            // Query Approved Leaves for this user in this month
            var leaves = await _db.LeaveApplications
                .Where(l => l.UserId == u.UserId && l.Status == "Approved" && l.EndDate >= monthStartDate && l.StartDate <= monthEndDate)
                .ToListAsync();

            int approvedLeaveDays = 0;
            foreach (var leave in leaves)
            {
                var lStart = leave.StartDate < monthStartDate ? monthStartDate : leave.StartDate;
                var lEnd = leave.EndDate > monthEndDate ? monthEndDate : leave.EndDate;

                for (var d = lStart.Date; d <= lEnd.Date; d = d.AddDays(1))
                {
                    bool isWeekend = (d.DayOfWeek == DayOfWeek.Friday);
                    if (isWeekend) continue;

                    bool isHoliday = holidays.Any(h =>
                        (h.Date.Date == d) ||
                        (h.IsRecurring && h.Date.Month == d.Month && h.Date.Day == d.Day)
                    );
                    if (isHoliday) continue;

                    approvedLeaveDays++;
                }
            }

            int absentDays = Math.Max(0, totalWorkingDays - (presentDays + approvedLeaveDays));
            double attendancePercentage = totalWorkingDays > 0 
                ? Math.Round(((double)presentDays / totalWorkingDays) * 100.0, 1) 
                : 0.0;

            var shiftName = u.Shift?.ShiftName 
                ?? companyDefaultShifts.FirstOrDefault(s => s.CompanyId == u.CompanyId)?.ShiftName 
                ?? "General Shift";

            summaries.Add(new EmployeeMonthlyAttendanceSummaryDto(
                UserId: u.UserId,
                FullName: string.IsNullOrWhiteSpace(u.FullName) ? u.Username : u.FullName,
                Username: u.Username,
                Role: u.Role,
                ShiftName: shiftName,
                Year: targetYear,
                Month: targetMonth,
                TotalWorkingDays: totalWorkingDays,
                PresentDays: presentDays,
                OnTimeDays: onTimeDays,
                LateDays: lateDays,
                EarlyOutDays: earlyOutDays,
                ApprovedLeaveDays: approvedLeaveDays,
                AbsentDays: absentDays,
                AttendancePercentage: attendancePercentage,
                TotalPresenceTime: totalPresenceTimeFormatted
            ));
        }

        return Ok(summaries);
    }

    [HttpGet("selfie/{id:long}")]
    [AllowAnonymous]
    public async Task<IActionResult> GetSelfie(long id)
    {
        var record = await _db.AttendanceRecords.FindAsync(id);
        if (record == null || string.IsNullOrWhiteSpace(record.SelfieUrl))
        {
            return NotFound("Attendance record or selfie not found.");
        }

        var fileName = Path.GetFileName(record.SelfieUrl.TrimStart('/'));
        return GetSelfieByFileName(fileName);
    }

    [HttpGet("selfie-file/{fileName}")]
    [AllowAnonymous]
    public IActionResult GetSelfieByFileName(string fileName)
    {
        if (string.IsNullOrWhiteSpace(fileName)) return NotFound("Selfie filename required.");

        var safeFileName = Path.GetFileName(fileName);
        var candidates = new[]
        {
            Path.Combine(_env.ContentRootPath, "wwwroot", "uploads", "selfies", safeFileName),
            Path.Combine(_env.ContentRootPath, "uploads", "selfies", safeFileName),
            Path.Combine(AppContext.BaseDirectory, "wwwroot", "uploads", "selfies", safeFileName),
            Path.Combine(AppContext.BaseDirectory, "uploads", "selfies", safeFileName),
            Path.Combine(Directory.GetCurrentDirectory(), "wwwroot", "uploads", "selfies", safeFileName),
            Path.Combine(Directory.GetCurrentDirectory(), "uploads", "selfies", safeFileName)
        };

        foreach (var path in candidates)
        {
            if (System.IO.File.Exists(path))
            {
                var ext = Path.GetExtension(path).ToLowerInvariant();
                var contentType = ext switch
                {
                    ".jpg" or ".jpeg" => "image/jpeg",
                    ".png" => "image/png",
                    ".webp" => "image/webp",
                    _ => "image/jpeg"
                };
                return PhysicalFile(path, contentType);
            }
        }

        return NotFound("Selfie file not found on server.");
    }

    private async Task<string?> SaveSelfieAsync(IFormFile? file, int userId)
    {
        if (file == null || file.Length == 0) return null;

        var webRoot = Path.Combine(_env.ContentRootPath, "wwwroot");
        var uploadsDir = Path.Combine(webRoot, "uploads", "selfies");
        if (!Directory.Exists(uploadsDir))
        {
            Directory.CreateDirectory(uploadsDir);
        }

        var ext = Path.GetExtension(file.FileName);
        if (string.IsNullOrEmpty(ext)) ext = ".jpg";

        var fileName = $"{userId}_{DateTime.UtcNow:yyyyMMdd_HHmmss}_{Guid.NewGuid():N}{ext}";
        var filePath = Path.Combine(uploadsDir, fileName);

        using (var stream = new FileStream(filePath, FileMode.Create))
        {
            await file.CopyToAsync(stream);
        }

        // Duplicate to secondary fallback locations to ensure accessibility across hosting models
        try
        {
            var altDir1 = Path.Combine(_env.ContentRootPath, "uploads", "selfies");
            if (!Directory.Exists(altDir1)) Directory.CreateDirectory(altDir1);
            System.IO.File.Copy(filePath, Path.Combine(altDir1, fileName), true);

            var altDir2 = Path.Combine(AppContext.BaseDirectory, "wwwroot", "uploads", "selfies");
            if (!Directory.Exists(altDir2)) Directory.CreateDirectory(altDir2);
            System.IO.File.Copy(filePath, Path.Combine(altDir2, fileName), true);
        }
        catch { }

        return $"/uploads/selfies/{fileName}";
    }
}
