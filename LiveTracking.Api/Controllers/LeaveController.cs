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
public class LeaveController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IHubContext<LocationHub> _hub;

    public LeaveController(LiveTrackingDbContext db, IHubContext<LocationHub> hub)
    {
        _db = db;
        _hub = hub;
    }

    private int GetCurrentUserId()
    {
        var idClaim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return int.TryParse(idClaim, out int id) ? id : 0;
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

    private async Task<int> CalculateActualWorkingDaysAsync(DateTime startDate, DateTime endDate)
    {
        if (endDate < startDate) return 0;

        var start = startDate.Date;
        var end = endDate.Date;

        var holidays = await _db.Holidays
            .Where(h => h.IsActive)
            .ToListAsync();

        int workingDays = 0;
        for (var date = start; date <= end; date = date.AddDays(1))
        {
            // Exclude Weekly Weekend: Friday only (Saturday is a normal working day)
            bool isWeekend = (date.DayOfWeek == DayOfWeek.Friday);
            if (isWeekend) continue;

            // Exclude Government Holidays (exact date or recurring day-of-year)
            bool isGovtHoliday = holidays.Any(h => 
                (h.Date.Date == date) || 
                (h.IsRecurring && h.Date.Month == date.Month && h.Date.Day == date.Day)
            );

            if (isGovtHoliday) continue;

            workingDays++;
        }

        return workingDays;
    }

    private async Task DeductLeaveBalanceAsync(int userId, int leaveTypeId, int totalDays, int year = 0)
    {
        if (year <= 0) year = DateTime.UtcNow.Year;
        var balance = await _db.LeaveBalances
            .FirstOrDefaultAsync(b => b.UserId == userId && b.LeaveTypeId == leaveTypeId && b.Year == year);

        if (balance == null)
        {
            var leaveType = await _db.LeaveTypes.FindAsync(leaveTypeId);
            int defaultTotal = leaveType?.DefaultDaysPerYear ?? 14;
            balance = new LeaveBalance
            {
                UserId = userId,
                LeaveTypeId = leaveTypeId,
                Year = year,
                TotalDays = defaultTotal,
                UsedDays = totalDays
            };
            _db.LeaveBalances.Add(balance);
        }
        else
        {
            balance.UsedDays += totalDays;
        }
    }

    private async Task RestoreLeaveBalanceAsync(int userId, int leaveTypeId, int totalDays, int year = 0)
    {
        if (year <= 0) year = DateTime.UtcNow.Year;
        var balance = await _db.LeaveBalances
            .FirstOrDefaultAsync(b => b.UserId == userId && b.LeaveTypeId == leaveTypeId && b.Year == year);

        if (balance != null)
        {
            balance.UsedDays = Math.Max(0, balance.UsedDays - totalDays);
        }
    }

    [HttpGet("types")]
    public async Task<ActionResult<List<LeaveTypeDto>>> GetActiveLeaveTypes()
    {
        var currentUserId = GetCurrentUserId();
        var user = currentUserId > 0 ? await _db.Users.FindAsync(currentUserId) : null;
        int? companyId = user?.CompanyId;

        var query = _db.LeaveTypes.Where(t => t.IsActive);
        if (companyId.HasValue)
        {
            query = query.Where(t => t.CompanyId == companyId.Value || t.CompanyId == null);
        }

        var types = await query
            .Select(t => new LeaveTypeDto(t.LeaveTypeId, t.Name, t.DefaultDaysPerYear, t.IsActive))
            .ToListAsync();

        return Ok(types);
    }

    [HttpGet("my-balances")]
    public async Task<ActionResult<List<LeaveBalanceDto>>> GetMyLeaveBalances()
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        int currentYear = DateTime.UtcNow.Year;
        var types = await _db.LeaveTypes.Where(t => t.IsActive).ToListAsync();
        var userBalances = await _db.LeaveBalances
            .Where(b => b.UserId == userId && b.Year == currentYear)
            .ToListAsync();

        var result = new List<LeaveBalanceDto>();
        foreach (var type in types)
        {
            var userBalance = userBalances.FirstOrDefault(b => b.LeaveTypeId == type.LeaveTypeId);
            int total = userBalance?.TotalDays ?? type.DefaultDaysPerYear;
            int used = userBalance?.UsedDays ?? 0;
            result.Add(new LeaveBalanceDto(type.LeaveTypeId, type.Name, total, used, Math.Max(0, total - used)));
        }

        return Ok(result);
    }

    [HttpGet("calculate-working-days")]
    public async Task<ActionResult> CalculateWorkingDays([FromQuery] string startDate, [FromQuery] string endDate)
    {
        if (!DateTime.TryParse(startDate, out var start) || !DateTime.TryParse(endDate, out var end))
        {
            return BadRequest("Invalid date format.");
        }

        if (end < start)
        {
            return BadRequest("End date cannot be earlier than start date.");
        }

        int totalCalendarDays = (int)(end.Date - start.Date).TotalDays + 1;
        var holidays = await _db.Holidays.Where(h => h.IsActive).ToListAsync();

        int workingDays = 0;
        int weekendDays = 0;
        var matchedHolidays = new List<string>();

        for (var date = start.Date; date <= end.Date; date = date.AddDays(1))
        {
            bool isWeekend = (date.DayOfWeek == DayOfWeek.Friday);
            if (isWeekend)
            {
                weekendDays++;
                continue;
            }

            var holiday = holidays.FirstOrDefault(h => 
                (h.Date.Date == date) || 
                (h.IsRecurring && h.Date.Month == date.Month && h.Date.Day == date.Day)
            );

            if (holiday != null)
            {
                matchedHolidays.Add($"{holiday.Name} ({date:dd MMM})");
                continue;
            }

            workingDays++;
        }

        return Ok(new
        {
            totalCalendarDays,
            workingDays,
            weekendDays,
            holidayDays = matchedHolidays.Count,
            holidayNames = matchedHolidays
        });
    }

    [HttpPost("apply")]
    public async Task<ActionResult<LeaveApplicationResponseDto>> ApplyLeave([FromBody] ApplyLeaveRequestDto request)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        if (!DateTime.TryParse(request.StartDate, out var start))
        {
            return BadRequest("Invalid start date format (use yyyy-MM-dd).");
        }

        if (!DateTime.TryParse(request.EndDate, out var end))
        {
            return BadRequest("Invalid end date format (use yyyy-MM-dd).");
        }

        if (end < start)
        {
            return BadRequest("End date cannot be earlier than start date.");
        }

        // Calculate actual working days excluding weekends (Fri/Sat) and Government Holidays
        int actualWorkingDays = await CalculateActualWorkingDaysAsync(start, end);
        if (actualWorkingDays <= 0)
        {
            return BadRequest("The selected date range consists only of government holidays or weekends. No working days to apply for leave.");
        }

        var leaveType = await _db.LeaveTypes.FindAsync(request.LeaveTypeId);
        if (leaveType == null)
        {
            return BadRequest("Selected leave type does not exist.");
        }

        // Validate Leave Balance against Actual Working Days
        var userBalance = await _db.LeaveBalances
            .FirstOrDefaultAsync(b => b.UserId == userId && b.LeaveTypeId == request.LeaveTypeId);
        int totalAllowed = userBalance?.TotalDays ?? leaveType.DefaultDaysPerYear;
        int currentlyUsed = userBalance?.UsedDays ?? 0;
        int availableDays = totalAllowed - currentlyUsed;

        if (actualWorkingDays > availableDays)
        {
            return BadRequest($"Insufficient leave balance. You are requesting {actualWorkingDays} working days, but have {availableDays} days remaining for {leaveType.Name}.");
        }

        var application = new LeaveApplication
        {
            UserId = userId,
            LeaveTypeId = request.LeaveTypeId,
            StartDate = start,
            EndDate = end,
            TotalDays = actualWorkingDays,
            Reason = request.Reason,
            Status = "Pending",
            AppliedAtUtc = DateTime.UtcNow
        };

        _db.LeaveApplications.Add(application);
        await _db.SaveChangesAsync();

        var user = await _db.Users.FindAsync(userId);
        var userName = user?.FullName ?? user?.Username ?? "Officer";
        var leaveTypeName = leaveType.Name;

        // Dispatch Notification to Admins
        try
        {
            var notif = new NotificationItem
            {
                UserId = null,
                CompanyId = user?.CompanyId,
                TargetRole = "Admin",
                Title = "📝 New Leave Request",
                Message = $"{userName} applied for {leaveTypeName} ({actualWorkingDays} working day{(actualWorkingDays > 1 ? "s" : "")}).",
                Type = "Leave",
                ReferenceId = application.LeaveApplicationId.ToString(),
                IsRead = false,
                CreatedAtUtc = DateTime.UtcNow
            };
            _db.Notifications.Add(notif);
            await _db.SaveChangesAsync();

            var notifDto = new NotificationDto
            {
                NotificationId = notif.NotificationId,
                UserId = null,
                CompanyId = notif.CompanyId,
                TargetRole = "Admin",
                Title = notif.Title,
                Message = notif.Message,
                Type = notif.Type,
                ReferenceId = notif.ReferenceId,
                IsRead = false,
                CreatedAtUtc = notif.CreatedAtUtc
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

        return Ok(new LeaveApplicationResponseDto(
            application.LeaveApplicationId,
            application.UserId,
            userName,
            application.LeaveTypeId,
            leaveTypeName,
            application.StartDate.ToString("yyyy-MM-dd"),
            application.EndDate.ToString("yyyy-MM-dd"),
            application.TotalDays,
            application.Reason,
            application.Status,
            application.AppliedAtUtc.ToString("o"),
            null,
            null
        ));
    }

    [HttpGet("my-history")]
    public async Task<ActionResult<List<LeaveApplicationResponseDto>>> GetMyLeaveHistory()
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var list = await _db.LeaveApplications
            .Include(a => a.User)
            .Include(a => a.LeaveType)
            .Where(a => a.UserId == userId)
            .OrderByDescending(a => a.AppliedAtUtc)
            .Select(a => new LeaveApplicationResponseDto(
                a.LeaveApplicationId,
                a.UserId,
                a.User != null ? a.User.FullName : "User",
                a.LeaveTypeId,
                a.LeaveType != null ? a.LeaveType.Name : "Leave",
                a.StartDate.ToString("yyyy-MM-dd"),
                a.EndDate.ToString("yyyy-MM-dd"),
                a.TotalDays,
                a.Reason,
                a.Status,
                a.AppliedAtUtc.ToString("o"),
                a.ReviewedAtUtc.HasValue ? a.ReviewedAtUtc.Value.ToString("o") : null,
                a.ReviewComment
            ))
            .ToListAsync();

        return Ok(list);
    }

    [HttpPut("{id}/cancel")]
    public async Task<ActionResult<LeaveApplicationResponseDto>> CancelLeave(int id)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var application = await _db.LeaveApplications
            .Include(a => a.User)
            .Include(a => a.LeaveType)
            .FirstOrDefaultAsync(a => a.LeaveApplicationId == id && a.UserId == userId);

        if (application == null) return NotFound(new { message = "Leave application not found." });

        if (application.Status.Equals("Cancelled", StringComparison.OrdinalIgnoreCase))
        {
            return BadRequest(new { message = "Leave is already cancelled." });
        }

        bool wasApproved = application.Status.Equals("Approved", StringComparison.OrdinalIgnoreCase);

        application.Status = "Cancelled";
        application.ReviewedAtUtc = DateTime.UtcNow;
        application.ReviewComment = "Cancelled by Employee";

        // If it was already approved, restore the used balance
        if (wasApproved)
        {
            await RestoreLeaveBalanceAsync(application.UserId, application.LeaveTypeId, application.TotalDays);
        }

        await _db.SaveChangesAsync();

        return Ok(new LeaveApplicationResponseDto(
            application.LeaveApplicationId,
            application.UserId,
            application.User?.FullName,
            application.LeaveTypeId,
            application.LeaveType?.Name,
            application.StartDate.ToString("yyyy-MM-dd"),
            application.EndDate.ToString("yyyy-MM-dd"),
            application.TotalDays,
            application.Reason,
            application.Status,
            application.AppliedAtUtc.ToString("o"),
            application.ReviewedAtUtc?.ToString("o"),
            application.ReviewComment
        ));
    }

    [HttpGet("admin/pending-count")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<LiveTracking.Api.DTOs.PendingLeaveCountDto>> GetPendingLeaveCount()
    {
        var currentAdminId = GetCurrentUserId();
        var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(currentAdminId);

        var count = await _db.LeaveApplications
            .CountAsync(a => accessibleUserIds.Contains(a.UserId) && a.Status.ToLower() == "pending");

        return Ok(new LiveTracking.Api.DTOs.PendingLeaveCountDto(count));
    }

    [HttpGet("admin")]
    [HttpGet("admin/applications")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<List<LeaveApplicationResponseDto>>> GetAdminLeaveApplications(
        [FromQuery] string? status = null,
        [FromQuery] int? userId = null,
        [FromQuery] int? leaveTypeId = null,
        [FromQuery] int? year = null)
    {
        var currentAdminId = GetCurrentUserId();
        var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(currentAdminId);

        var query = _db.LeaveApplications
            .Include(a => a.User)
            .Include(a => a.LeaveType)
            .AsQueryable();

        query = query.Where(a => accessibleUserIds.Contains(a.UserId));

        if (!string.IsNullOrWhiteSpace(status) && !status.Equals("All", StringComparison.OrdinalIgnoreCase))
        {
            var st = status.Trim().ToLower();
            query = query.Where(a => a.Status != null && a.Status.ToLower() == st);
        }

        if (userId.HasValue && userId.Value > 0)
        {
            query = query.Where(a => a.UserId == userId.Value);
        }

        if (leaveTypeId.HasValue && leaveTypeId.Value > 0)
        {
            query = query.Where(a => a.LeaveTypeId == leaveTypeId.Value);
        }

        if (year.HasValue && year.Value > 0)
        {
            query = query.Where(a => a.StartDate.Year == year.Value || a.EndDate.Year == year.Value);
        }

        var list = await query
            .OrderByDescending(a => a.AppliedAtUtc)
            .Select(a => new LeaveApplicationResponseDto(
                a.LeaveApplicationId,
                a.UserId,
                a.User != null ? (!string.IsNullOrEmpty(a.User.FullName) ? a.User.FullName : a.User.Username) : "Employee",
                a.LeaveTypeId,
                a.LeaveType != null ? a.LeaveType.Name : "Leave",
                a.StartDate.ToString("yyyy-MM-dd"),
                a.EndDate.ToString("yyyy-MM-dd"),
                a.TotalDays,
                a.Reason,
                a.Status ?? "Pending",
                a.AppliedAtUtc.ToString("o"),
                a.ReviewedAtUtc.HasValue ? a.ReviewedAtUtc.Value.ToString("o") : null,
                a.ReviewComment
            ))
            .ToListAsync();

        return Ok(list);
    }

    [HttpPut("admin/{id}/approve")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<LeaveApplicationResponseDto>> ApproveLeave(int id, [FromBody] LeaveReviewRequestDto? request)
    {
        int currentAdminId = GetCurrentUserId();
        var application = await _db.LeaveApplications
            .Include(a => a.User)
            .Include(a => a.LeaveType)
            .FirstOrDefaultAsync(a => a.LeaveApplicationId == id);

        if (application == null) return NotFound();

        bool wasNotApproved = !application.Status.Equals("Approved", StringComparison.OrdinalIgnoreCase);

        application.Status = "Approved";
        application.ReviewedBy = currentAdminId > 0 ? currentAdminId : null;
        application.ReviewedAtUtc = DateTime.UtcNow;
        application.ReviewComment = request?.Comment ?? "Approved by Admin";

        // Automatically deduct leave days from LeaveBalance
        if (wasNotApproved)
        {
            await DeductLeaveBalanceAsync(application.UserId, application.LeaveTypeId, application.TotalDays, application.StartDate.Year);
        }

        await _db.SaveChangesAsync();

        var leaveTypeName = application.LeaveType?.Name ?? "Leave";

        // Dispatch Notification to User
        try
        {
            var notif = new NotificationItem
            {
                UserId = application.UserId,
                CompanyId = application.User?.CompanyId,
                TargetRole = "User",
                Title = "✅ Leave Approved",
                Message = $"Your {leaveTypeName} application ({application.TotalDays} working day{(application.TotalDays > 1 ? "s" : "")}) has been approved.",
                Type = "Leave",
                ReferenceId = application.LeaveApplicationId.ToString(),
                IsRead = false,
                CreatedAtUtc = DateTime.UtcNow
            };
            _db.Notifications.Add(notif);
            await _db.SaveChangesAsync();

            await _hub.Clients.Group(LocationHub.UserGroup(application.UserId)).SendAsync("ReceiveNotification", new NotificationDto
            {
                NotificationId = notif.NotificationId,
                UserId = application.UserId,
                CompanyId = notif.CompanyId,
                TargetRole = "User",
                Title = notif.Title,
                Message = notif.Message,
                Type = notif.Type,
                ReferenceId = notif.ReferenceId,
                IsRead = false,
                CreatedAtUtc = notif.CreatedAtUtc
            });
        }
        catch { }

        return Ok(new LeaveApplicationResponseDto(
            application.LeaveApplicationId,
            application.UserId,
            application.User?.FullName,
            application.LeaveTypeId,
            application.LeaveType?.Name,
            application.StartDate.ToString("yyyy-MM-dd"),
            application.EndDate.ToString("yyyy-MM-dd"),
            application.TotalDays,
            application.Reason,
            application.Status,
            application.AppliedAtUtc.ToString("o"),
            application.ReviewedAtUtc?.ToString("o"),
            application.ReviewComment
        ));
    }

    [HttpPut("admin/{id}/reject")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<LeaveApplicationResponseDto>> RejectLeave(int id, [FromBody] LeaveReviewRequestDto? request)
    {
        int currentAdminId = GetCurrentUserId();
        var application = await _db.LeaveApplications
            .Include(a => a.User)
            .Include(a => a.LeaveType)
            .FirstOrDefaultAsync(a => a.LeaveApplicationId == id);

        if (application == null) return NotFound();

        bool wasApproved = application.Status.Equals("Approved", StringComparison.OrdinalIgnoreCase);

        application.Status = "Rejected";
        application.ReviewedBy = currentAdminId > 0 ? currentAdminId : null;
        application.ReviewedAtUtc = DateTime.UtcNow;
        application.ReviewComment = request?.Comment ?? "Rejected by Admin";

        // If previously approved, restore the deducted leave balance
        if (wasApproved)
        {
            await RestoreLeaveBalanceAsync(application.UserId, application.LeaveTypeId, application.TotalDays, application.StartDate.Year);
        }

        await _db.SaveChangesAsync();

        var leaveTypeName = application.LeaveType?.Name ?? "Leave";

        // Dispatch Notification to User
        try
        {
            var notif = new NotificationItem
            {
                UserId = application.UserId,
                CompanyId = application.User?.CompanyId,
                TargetRole = "User",
                Title = "❌ Leave Rejected",
                Message = $"Your {leaveTypeName} application has been rejected. Reason: {request?.Comment ?? "N/A"}",
                Type = "Leave",
                ReferenceId = application.LeaveApplicationId.ToString(),
                IsRead = false,
                CreatedAtUtc = DateTime.UtcNow
            };
            _db.Notifications.Add(notif);
            await _db.SaveChangesAsync();

            await _hub.Clients.Group(LocationHub.UserGroup(application.UserId)).SendAsync("ReceiveNotification", new NotificationDto
            {
                NotificationId = notif.NotificationId,
                UserId = application.UserId,
                CompanyId = notif.CompanyId,
                TargetRole = "User",
                Title = notif.Title,
                Message = notif.Message,
                Type = notif.Type,
                ReferenceId = notif.ReferenceId,
                IsRead = false,
                CreatedAtUtc = notif.CreatedAtUtc
            });
        }
        catch { }

        return Ok(new LeaveApplicationResponseDto(
            application.LeaveApplicationId,
            application.UserId,
            application.User?.FullName,
            application.LeaveTypeId,
            application.LeaveType?.Name,
            application.StartDate.ToString("yyyy-MM-dd"),
            application.EndDate.ToString("yyyy-MM-dd"),
            application.TotalDays,
            application.Reason,
            application.Status,
            application.AppliedAtUtc.ToString("o"),
            application.ReviewedAtUtc?.ToString("o"),
            application.ReviewComment
        ));
    }

    [HttpPut("admin/bulk-approve")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<LiveTracking.Api.DTOs.BulkLeaveApprovalResultDto>> BulkApproveLeaves([FromBody] LiveTracking.Api.DTOs.BulkLeaveApprovalDto request)
    {
        if (request.ApplicationIds == null || !request.ApplicationIds.Any())
        {
            return BadRequest("No application IDs provided.");
        }

        int currentAdminId = GetCurrentUserId();
        var applications = await _db.LeaveApplications
            .Where(a => request.ApplicationIds.Contains(a.LeaveApplicationId) && a.Status.ToLower() == "pending")
            .ToListAsync();

        var approvedIds = new List<int>();
        var now = DateTime.UtcNow;

        foreach (var app in applications)
        {
            app.Status = "Approved";
            app.ReviewedBy = currentAdminId > 0 ? currentAdminId : null;
            app.ReviewedAtUtc = now;
            app.ReviewComment = request.Comment ?? "Bulk Approved by Admin";

            // Automatically deduct leave days from LeaveBalance
            await DeductLeaveBalanceAsync(app.UserId, app.LeaveTypeId, app.TotalDays, app.StartDate.Year);

            approvedIds.Add(app.LeaveApplicationId);
        }

        await _db.SaveChangesAsync();

        int approvedCount = approvedIds.Count;
        int failedCount = request.ApplicationIds.Count - approvedCount;

        return Ok(new LiveTracking.Api.DTOs.BulkLeaveApprovalResultDto(approvedCount, failedCount, approvedIds));
    }
}
