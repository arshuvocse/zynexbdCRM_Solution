using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Hubs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.SignalR;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Services;

public class AttendanceShiftHostedService : BackgroundService
{
    private readonly IServiceProvider _services;
    private readonly ILogger<AttendanceShiftHostedService> _logger;

    public AttendanceShiftHostedService(IServiceProvider services, ILogger<AttendanceShiftHostedService> logger)
    {
        _services = services;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // Initial delay to let the app start up completely
        await Task.Delay(TimeSpan.FromSeconds(10), stoppingToken);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await RunShiftAndAttendanceMaintenanceAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error occurred during Attendance & Shift Background Maintenance");
            }

            // Run maintenance every 30 minutes
            await Task.Delay(TimeSpan.FromMinutes(30), stoppingToken);
        }
    }

    private async Task RunShiftAndAttendanceMaintenanceAsync()
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<LiveTrackingDbContext>();
        var hub = scope.ServiceProvider.GetRequiredService<IHubContext<LocationHub>>();

        // 1. Auto-healing: Assign Company Default Shift to Users with NULL ShiftId
        await AutoHealMissingUserShiftsAsync(db);

        // 2. Ensure each active company has at least one active default shift
        await EnsureDefaultCompanyShiftsAsync(db);

        // 3. Shift Absent / Late Check & Admin Push Notifications
        await CheckShiftAttendanceAlertsAsync(db, hub);
    }

    private async Task AutoHealMissingUserShiftsAsync(LiveTrackingDbContext db)
    {
        var usersWithoutShift = await db.Users
            .Where(u => u.ShiftId == null && u.CompanyId.HasValue && u.IsActive && u.Role != "Admin")
            .ToListAsync();

        if (usersWithoutShift.Count == 0) return;

        var defaultShifts = await db.Shifts
            .Where(s => s.IsActive && s.IsDefault && s.CompanyId.HasValue)
            .ToListAsync();

        var fallbackShifts = await db.Shifts
            .Where(s => s.IsActive && s.CompanyId.HasValue)
            .ToListAsync();

        int updatedCount = 0;
        foreach (var user in usersWithoutShift)
        {
            if (!user.CompanyId.HasValue) continue;

            var shift = defaultShifts.FirstOrDefault(s => s.CompanyId == user.CompanyId.Value)
                     ?? fallbackShifts.FirstOrDefault(s => s.CompanyId == user.CompanyId.Value);

            if (shift != null)
            {
                user.ShiftId = shift.ShiftId;
                updatedCount++;
            }
        }

        if (updatedCount > 0)
        {
            await db.SaveChangesAsync();
            _logger.LogInformation("✅ AttendanceShiftHostedService: Auto-assigned shifts to {Count} users with missing ShiftId.", updatedCount);
        }
    }

    private async Task EnsureDefaultCompanyShiftsAsync(LiveTrackingDbContext db)
    {
        var companies = await db.Companies.Where(c => c.IsActive).ToListAsync();

        foreach (var company in companies)
        {
            var companyShifts = await db.Shifts.Where(s => s.CompanyId == company.CompanyId && s.IsActive).ToListAsync();

            if (companyShifts.Count == 0)
            {
                // Create standard default shift if none exists for this company
                var newShift = new Shift
                {
                    ShiftName = $"{company.CompanyName} Regular Shift",
                    StartTime = "09:00:00",
                    EndTime = "18:00:00",
                    GracePeriodMinutes = 15,
                    IsDefault = true,
                    IsActive = true,
                    CompanyId = company.CompanyId,
                    CreatedAtUtc = DateTime.UtcNow
                };
                db.Shifts.Add(newShift);
                await db.SaveChangesAsync();
                _logger.LogInformation("Created initial default shift for Company {CompanyName} (ID: {CompanyId})", company.CompanyName, company.CompanyId);
            }
            else if (!companyShifts.Any(s => s.IsDefault))
            {
                // Set first active shift as default
                companyShifts[0].IsDefault = true;
                await db.SaveChangesAsync();
                _logger.LogInformation("Set default shift {ShiftName} for Company {CompanyName} (ID: {CompanyId})", companyShifts[0].ShiftName, company.CompanyName, company.CompanyId);
            }
        }
    }

    private async Task CheckShiftAttendanceAlertsAsync(LiveTrackingDbContext db, IHubContext<LocationHub> hub)
    {
        var localNow = DateTime.UtcNow.AddHours(6); // BST (UTC+6)
        var todayLocalDate = localNow.Date;
        var todayStartUtc = todayLocalDate.AddHours(-6);
        var todayEndUtc = todayStartUtc.AddDays(1);

        // Skip weekend (Friday) or major holiday
        if (localNow.DayOfWeek == DayOfWeek.Friday) return;

        var isHoliday = await db.Holidays.AnyAsync(h => h.IsActive && 
            (h.Date.Date == todayLocalDate || (h.IsRecurring && h.Date.Month == todayLocalDate.Month && h.Date.Day == todayLocalDate.Day)));
        if (isHoliday) return;

        var companies = await db.Companies.Where(c => c.IsActive).ToListAsync();

        foreach (var company in companies)
        {
            var defaultShift = await db.Shifts.FirstOrDefaultAsync(s => s.CompanyId == company.CompanyId && s.IsDefault && s.IsActive)
                            ?? await db.Shifts.FirstOrDefaultAsync(s => s.CompanyId == company.CompanyId && s.IsActive);

            if (defaultShift == null || !TimeSpan.TryParse(defaultShift.StartTime, out var shiftStartTime))
                continue;

            var graceTime = shiftStartTime.Add(TimeSpan.FromMinutes(defaultShift.GracePeriodMinutes));
            var alertTriggerTime = graceTime.Add(TimeSpan.FromMinutes(15)); // 15 mins after grace period

            // Only trigger alert during the window right after shift start (within 45 minutes)
            if (localNow.TimeOfDay >= alertTriggerTime && localNow.TimeOfDay <= alertTriggerTime.Add(TimeSpan.FromMinutes(45)))
            {
                var notificationTitle = "⚠️ Shift Attendance Alert";
                var todayNotifExists = await db.Notifications.AnyAsync(n =>
                    n.CompanyId == company.CompanyId &&
                    n.Title == notificationTitle &&
                    n.CreatedAtUtc >= todayStartUtc &&
                    n.CreatedAtUtc < todayEndUtc);

                if (todayNotifExists) continue; // Already alerted today

                // Find active employees of this company
                var activeEmployees = await db.Users
                    .Where(u => u.CompanyId == company.CompanyId && u.Role != "Admin" && u.IsActive)
                    .ToListAsync();

                if (activeEmployees.Count == 0) continue;

                var employeeIds = activeEmployees.Select(e => e.UserId).ToList();

                // Check who punched In today
                var punchedInUserIds = await db.AttendanceRecords
                    .Where(a => employeeIds.Contains(a.UserId) && a.Type == "In" && a.RecordedAtUtc >= todayStartUtc && a.RecordedAtUtc < todayEndUtc)
                    .Select(a => a.UserId)
                    .Distinct()
                    .ToListAsync();

                // Check who is on approved leave today
                var onLeaveUserIds = await db.LeaveApplications
                    .Where(l => employeeIds.Contains(l.UserId) && l.Status == "Approved" && l.StartDate <= todayLocalDate && l.EndDate >= todayLocalDate)
                    .Select(l => l.UserId)
                    .Distinct()
                    .ToListAsync();

                var missingOfficers = activeEmployees
                    .Where(u => !punchedInUserIds.Contains(u.UserId) && !onLeaveUserIds.Contains(u.UserId))
                    .ToList();

                if (missingOfficers.Count > 0)
                {
                    string message = missingOfficers.Count == 1
                        ? $"{missingOfficers[0].FullName ?? missingOfficers[0].Username} has not recorded Duty In yet for shift {defaultShift.ShiftName}."
                        : $"{missingOfficers.Count} officers have not recorded Duty In yet for shift {defaultShift.ShiftName}.";

                    var notification = new NotificationItem
                    {
                        UserId = null,
                        CompanyId = company.CompanyId,
                        TargetRole = "Admin",
                        Title = notificationTitle,
                        Message = message,
                        Type = "Attendance",
                        ReferenceId = $"shift_{defaultShift.ShiftId}_{todayLocalDate:yyyyMMdd}",
                        IsRead = false,
                        CreatedAtUtc = DateTime.UtcNow
                    };

                    db.Notifications.Add(notification);
                    await db.SaveChangesAsync();

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

                    await hub.Clients.Group(LocationHub.CompanyAdminsGroup(company.CompanyId)).SendAsync("ReceiveNotification", notifDto);
                    _logger.LogInformation("Sent attendance alert for company {CompanyName}: {Message}", company.CompanyName, message);
                }
            }
        }
    }
}
