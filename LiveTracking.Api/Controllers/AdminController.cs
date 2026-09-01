using System.Security.Claims;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class AdminController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;

    public AdminController(LiveTrackingDbContext db)
    {
        _db = db;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                    ?? User.FindFirst("sub")?.Value;
        return int.TryParse(claim, out var id) ? id : 0;
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

    [HttpGet("office-locations")]
    public async Task<ActionResult<List<OfficeLocation>>> GetOfficeLocations([FromQuery] bool all = false)
    {
        var currentAdminId = GetCurrentUserId();
        var currentAdmin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? targetCompanyId = currentAdmin?.CompanyId;
        var assignedOfficeIds = await GetAdminAssignedOfficeIdsAsync(currentAdminId);

        var query = _db.OfficeLocations.AsQueryable();

        if (targetCompanyId.HasValue)
        {
            query = query.Where(o => o.CompanyId == targetCompanyId.Value || o.CompanyId == null);
        }

        if (!all)
        {
            query = query.Where(o => o.IsActive);
            // If user is regular admin with assigned offices, filter to their assigned offices
            if (assignedOfficeIds.Count > 0)
            {
                query = query.Where(o => assignedOfficeIds.Contains(o.OfficeLocationId));
            }
        }

        var locations = await query
            .OrderBy(o => o.Name)
            .ToListAsync();

        return Ok(locations);
    }

    [HttpGet("office-locations/{id:int}")]
    public async Task<ActionResult<OfficeLocation>> GetOfficeLocationById(int id)
    {
        var location = await _db.OfficeLocations.FindAsync(id);
        if (location == null) return NotFound(new { message = "Office location not found." });
        return Ok(location);
    }

    [HttpPost("office-locations")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<OfficeLocation>> CreateOfficeLocation([FromBody] CreateOfficeLocationRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
            return BadRequest(new { message = "Office name is required." });

        var currentAdminId = GetCurrentUserId();
        var currentAdmin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? targetCompanyId = currentAdmin?.CompanyId;

        var location = new OfficeLocation
        {
            Name = request.Name.Trim(),
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            RadiusMeters = request.RadiusMeters > 0 ? request.RadiusMeters : 200.0,
            Address = request.Address?.Trim(),
            CompanyId = targetCompanyId,
            IsActive = true,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.OfficeLocations.Add(location);
        await _db.SaveChangesAsync();

        // Automatically associate the newly created office with the creating admin so they immediately see and can assign users to it
        if (currentAdminId > 0)
        {
            var alreadyAssigned = await _db.AdminOfficeLocations
                .AnyAsync(a => a.AdminUserId == currentAdminId && a.OfficeLocationId == location.OfficeLocationId);
            if (!alreadyAssigned)
            {
                _db.AdminOfficeLocations.Add(new AdminOfficeLocation
                {
                    AdminUserId = currentAdminId,
                    OfficeLocationId = location.OfficeLocationId,
                    AssignedAtUtc = DateTime.UtcNow
                });
                await _db.SaveChangesAsync();
            }
        }

        return CreatedAtAction(nameof(GetOfficeLocationById), new { id = location.OfficeLocationId }, location);
    }

    [HttpPut("office-locations/{id:int}")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<OfficeLocation>> UpdateOfficeLocation(int id, [FromBody] UpdateOfficeLocationRequest request)
    {
        var location = await _db.OfficeLocations.FindAsync(id);
        if (location == null) return NotFound(new { message = "Office location not found." });

        if (!string.IsNullOrWhiteSpace(request.Name)) location.Name = request.Name.Trim();
        location.Latitude = request.Latitude;
        location.Longitude = request.Longitude;
        if (request.RadiusMeters > 0) location.RadiusMeters = request.RadiusMeters;
        if (request.Address != null) location.Address = request.Address.Trim();
        location.IsActive = request.IsActive;

        await _db.SaveChangesAsync();
        return Ok(location);
    }

    [HttpDelete("office-locations/{id:int}")]
    [Authorize(Roles = "Admin")]
    public async Task<IActionResult> DeleteOfficeLocation(int id)
    {
        var location = await _db.OfficeLocations.FindAsync(id);
        if (location == null) return NotFound(new { message = "Office location not found." });

        location.IsActive = false;
        await _db.SaveChangesAsync();
        return Ok(new { message = "Office location deactivated successfully." });
    }

    [HttpGet("summary")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<ExecutiveSummaryDto>> GetExecutiveSummary()
    {
        var currentAdminId = GetCurrentUserId();
        var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(currentAdminId);

        var nowUtc = DateTime.UtcNow;
        var todayStartUtc = nowUtc.Date;
        var cutoff15m = nowUtc.AddMinutes(-15);

        var totalUsers = accessibleUserIds.Count;
        var activeUsers = await _db.Users.CountAsync(u => accessibleUserIds.Contains(u.UserId) && u.IsActive);

        // Drivers with location pings in last 15 minutes
        var recentPings = await _db.DriverLocations
            .Where(l => accessibleUserIds.Contains(l.UserId) && l.RecordedAtUtc >= cutoff15m)
            .Select(l => l.UserId)
            .Distinct()
            .ToListAsync();
        var onlineTrackingUsers = recentPings.Count;

        // Today's attendance records
        var todayAttendance = await _db.AttendanceRecords
            .Where(a => accessibleUserIds.Contains(a.UserId) && a.RecordedAtUtc >= todayStartUtc)
            .ToListAsync();

        var punchedInUserIds = todayAttendance
            .Where(a => a.Type == "In")
            .Select(a => a.UserId)
            .Distinct()
            .ToList();
        var todayPunchInCount = punchedInUserIds.Count;

        var todayAbsentCount = Math.Max(0, activeUsers - todayPunchInCount);

        // Late attendance: Punched in after 9:30 AM local (or after todayStartUtc + 3.5 hrs UTC assuming UTC+6)
        var todayLateCount = todayAttendance
            .Where(a => a.Type == "In" && a.RecordedAtUtc.TimeOfDay > new TimeSpan(3, 30, 0))
            .Select(a => a.UserId)
            .Distinct()
            .Count();

        // Pending Leave Applications
        var pendingLeaveRequestsCount = await _db.LeaveApplications
            .CountAsync(l => accessibleUserIds.Contains(l.UserId) && l.Status != null && l.Status.ToLower() == "pending");

        // Pending Follow ups
        var pendingCustomerFollowUpsCount = await _db.CustomerVisits
            .CountAsync(v => accessibleUserIds.Contains(v.UserId) && v.NextFollowUpDate.HasValue && !v.IsFollowUpCompleted);

        // GPS Disabled or no update today among active users
        var gpsDisabledUsersCount = Math.Max(0, activeUsers - onlineTrackingUsers);

        // Build Attention Items list
        var attentionItems = new List<AttentionItemDto>();

        // 1. Pending Leaves
        var pendingLeaves = await _db.LeaveApplications
            .Include(l => l.User)
            .Include(l => l.LeaveType)
            .Where(l => accessibleUserIds.Contains(l.UserId) && l.Status != null && l.Status.ToLower() == "pending")
            .Take(5)
            .ToListAsync();

        foreach (var leave in pendingLeaves)
        {
            var uName = leave.User?.FullName ?? leave.User?.Username ?? "Employee";
            attentionItems.Add(new AttentionItemDto(
                $"leave_{leave.LeaveApplicationId}",
                "PendingLeave",
                $"Leave Request: {uName}",
                $"{leave.LeaveType?.Name ?? "Leave"} ({leave.TotalDays} day(s)) from {leave.StartDate:yyyy-MM-dd}",
                leave.UserId,
                uName,
                "Medium",
                "Approve",
                leave.AppliedAtUtc.ToString("o")
            ));
        }

        // 2. Pending / Overdue Follow ups
        var overdueVisits = await _db.CustomerVisits
            .Include(v => v.Customer)
            .Include(v => v.User)
            .Where(v => accessibleUserIds.Contains(v.UserId) && v.NextFollowUpDate.HasValue && !v.IsFollowUpCompleted)
            .Take(5)
            .ToListAsync();

        foreach (var visit in overdueVisits)
        {
            var uName = visit.User?.FullName ?? visit.User?.Username ?? "Executive";
            attentionItems.Add(new AttentionItemDto(
                $"visit_{visit.VisitId}",
                "MissedFollowUp",
                $"Follow-up: {visit.Customer?.Name ?? "Customer"}",
                $"Assigned to {uName}. Remarks: {visit.Remarks ?? "Action required"}",
                visit.UserId,
                uName,
                "High",
                "Call",
                visit.VisitDate.ToString("o")
            ));
        }

        // System Health
        var health = new SystemHealthDto(
            "Healthy",
            "Connected",
            onlineTrackingUsers,
            nowUtc.ToString("o")
        );

        return Ok(new ExecutiveSummaryDto(
            totalUsers,
            activeUsers,
            onlineTrackingUsers,
            todayPunchInCount,
            todayAbsentCount,
            todayLateCount,
            pendingLeaveRequestsCount,
            pendingCustomerFollowUpsCount,
            gpsDisabledUsersCount,
            attentionItems,
            health
        ));
    }

    [HttpGet("reports/employee-performance")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<MonthlyPerformanceReportResponse>> GetEmployeePerformanceReport(
        [FromQuery] int? year = null,
        [FromQuery] int? month = null,
        [FromQuery] int? userId = null)
    {
        var currentAdminId = GetCurrentUserId();
        var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(currentAdminId);

        var now = DateTime.UtcNow;
        int targetYear = (year.HasValue && year.Value >= 2000 && year.Value <= 2100) ? year.Value : now.Year;
        int targetMonth = (month.HasValue && month.Value >= 1 && month.Value <= 12) ? month.Value : now.Month;

        var startDateUtc = new DateTime(targetYear, targetMonth, 1, 0, 0, 0, DateTimeKind.Utc);
        var endDateUtc = startDateUtc.AddMonths(1);

        var usersQuery = _db.Users.AsQueryable();
        if (userId.HasValue && userId.Value > 0)
        {
            usersQuery = usersQuery.Where(u => u.UserId == userId.Value && accessibleUserIds.Contains(u.UserId));
        }
        else
        {
            usersQuery = usersQuery.Where(u => accessibleUserIds.Contains(u.UserId) && u.Role != "Admin");
        }

        var targetUsers = await usersQuery
            .OrderBy(u => u.FullName ?? u.Username)
            .ToListAsync();

        var targetUserIds = targetUsers.Select(u => u.UserId).ToList();

        // 1. Visits in this month
        var visits = await _db.CustomerVisits
            .Include(v => v.Customer)
            .Where(v => targetUserIds.Contains(v.UserId) && v.VisitDate >= startDateUtc && v.VisitDate < endDateUtc)
            .OrderByDescending(v => v.VisitDate)
            .ToListAsync();

        // 2. Follow-ups scheduled or created in this month
        var followUps = await _db.CustomerVisits
            .Include(v => v.Customer)
            .Where(v => targetUserIds.Contains(v.UserId) 
                     && v.NextFollowUpDate.HasValue 
                     && v.NextFollowUpDate.Value >= startDateUtc 
                     && v.NextFollowUpDate.Value < endDateUtc)
            .OrderBy(v => v.NextFollowUpDate)
            .ToListAsync();

        // 3. Customers created by users in this month
        var customers = await _db.Customers
            .Where(c => c.CreatedByUserId.HasValue 
                     && targetUserIds.Contains(c.CreatedByUserId.Value)
                     && c.CreatedDate >= startDateUtc 
                     && c.CreatedDate < endDateUtc)
            .ToListAsync();

        var employeeReports = new List<EmployeePerformanceItemDto>();

        foreach (var user in targetUsers)
        {
            var uVisits = visits.Where(v => v.UserId == user.UserId).ToList();
            var uFollowUps = followUps.Where(v => v.UserId == user.UserId).ToList();
            var uCustomers = customers.Where(c => c.CreatedByUserId == user.UserId).ToList();

            var completedFollowUps = uFollowUps.Count(f => f.IsFollowUpCompleted);
            var pendingFollowUps = uFollowUps.Count(f => !f.IsFollowUpCompleted);

            employeeReports.Add(new EmployeePerformanceItemDto
            {
                UserId = user.UserId,
                FullName = user.FullName ?? user.Username,
                Username = user.Username,
                Role = user.Role,
                IsActive = user.IsActive,
                TotalCustomersAdded = uCustomers.Count,
                TotalVisits = uVisits.Count,
                TotalFollowUps = uFollowUps.Count,
                CompletedFollowUps = completedFollowUps,
                PendingFollowUps = pendingFollowUps,
                Customers = uCustomers.Select(c => new ReportCustomerItemDto
                {
                    CustomerId = c.CustomerId,
                    Name = c.Name,
                    Mobile = c.Mobile,
                    Address = c.Address,
                    CreatedDate = c.CreatedDate,
                    Remarks = c.Remarks
                }).ToList(),
                Visits = uVisits.Select(v => new ReportVisitItemDto
                {
                    VisitId = v.VisitId,
                    CustomerId = v.CustomerId,
                    CustomerName = v.Customer?.Name ?? "Unknown",
                    CustomerMobile = v.Customer?.Mobile ?? "",
                    CustomerAddress = v.Customer?.Address ?? "",
                    VisitDate = v.VisitDate,
                    Remarks = v.Remarks,
                    VisitStatus = v.VisitStatus,
                    ShopPhotoPath = v.ShopPhotoPath
                }).ToList(),
                FollowUps = uFollowUps.Select(f => new ReportFollowUpItemDto
                {
                    VisitId = f.VisitId,
                    CustomerId = f.CustomerId,
                    CustomerName = f.Customer?.Name ?? "Customer",
                    CustomerMobile = f.Customer?.Mobile ?? "",
                    FollowUpDate = f.NextFollowUpDate,
                    IsCompleted = f.IsFollowUpCompleted,
                    Remarks = f.Remarks
                }).ToList()
            });
        }

        var monthName = new DateTime(targetYear, targetMonth, 1).ToString("MMMM yyyy");

        var response = new MonthlyPerformanceReportResponse
        {
            Year = targetYear,
            Month = targetMonth,
            MonthName = monthName,
            TotalVisits = visits.Count,
            TotalFollowUps = followUps.Count,
            CompletedFollowUps = followUps.Count(f => f.IsFollowUpCompleted),
            PendingFollowUps = followUps.Count(f => !f.IsFollowUpCompleted),
            TotalCustomersAdded = customers.Count,
            Employees = employeeReports
        };

        return Ok(response);
    }
}
