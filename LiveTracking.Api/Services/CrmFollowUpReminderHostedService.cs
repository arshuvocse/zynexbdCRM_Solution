using LiveTracking.Api.Data;
using LiveTracking.Api.Hubs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.SignalR;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Services;

public class CrmFollowUpReminderHostedService : BackgroundService
{
    private readonly IServiceProvider _services;
    private readonly ILogger<CrmFollowUpReminderHostedService> _logger;

    public CrmFollowUpReminderHostedService(IServiceProvider services, ILogger<CrmFollowUpReminderHostedService> logger)
    {
        _services = services;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // Initial brief delay before starting background check
        await Task.Delay(TimeSpan.FromSeconds(20), stoppingToken);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await CheckAndNotifyFollowUpsAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error occurred in CrmFollowUpReminderHostedService");
            }

            // Check every hour
            await Task.Delay(TimeSpan.FromHours(1), stoppingToken);
        }
    }

    private async Task CheckAndNotifyFollowUpsAsync()
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<LiveTrackingDbContext>();
        var hub = scope.ServiceProvider.GetRequiredService<IHubContext<LocationHub>>();

        var todayStart = DateTime.UtcNow.Date;
        var todayEnd = todayStart.AddDays(1);

        var dueLeads = await db.CrmLeads.AsNoTracking()
            .Where(l =>
                l.IsActive &&
                l.AssignedUserId.HasValue &&
                l.NextFollowUpDate.HasValue &&
                l.LeadStatus != "Closed" &&
                l.LeadStatus != "Not Interested" &&
                l.NextFollowUpDate.Value < todayEnd)
            .Select(l => new { l.LeadId, l.CompanyId, l.LeadName, l.AssignedUserId, l.NextFollowUpDate })
            .ToListAsync();

        foreach (var lead in dueLeads)
        {
            try
            {
                bool isOverdue = lead.NextFollowUpDate!.Value < todayStart;
                string type = isOverdue ? "CrmFollowUpOverdue" : "CrmFollowUpDue";

                var alreadyNotifiedToday = await db.Notifications.AnyAsync(n =>
                    n.UserId == lead.AssignedUserId &&
                    n.Type == type &&
                    n.ReferenceId == lead.LeadId.ToString() &&
                    n.CreatedAtUtc >= todayStart);

                if (alreadyNotifiedToday) continue;

                var notif = new NotificationItem
                {
                    UserId = lead.AssignedUserId,
                    CompanyId = lead.CompanyId,
                    TargetRole = "User",
                    Title = isOverdue ? "⚠️ ফলো-আপ মেয়াদোত্তীর্ণ" : "🔔 আজকের ফলো-আপ",
                    Message = isOverdue
                        ? $"'{lead.LeadName}' লিডের ফলো-আপের মেয়াদ পার হয়ে গেছে ({lead.NextFollowUpDate:dd MMM yyyy})। দ্রুত যোগাযোগ করুন।"
                        : $"'{lead.LeadName}' লিডের জন্য আজ ফলো-আপ নির্ধারিত আছে।",
                    Type = type,
                    ReferenceId = lead.LeadId.ToString(),
                    IsRead = false,
                    CreatedAtUtc = DateTime.UtcNow
                };
                db.Notifications.Add(notif);
                await db.SaveChangesAsync();

                await hub.Clients.Group(LocationHub.UserGroup(lead.AssignedUserId!.Value)).SendAsync("ReceiveNotification", new LiveTracking.Api.DTOs.NotificationDto
                {
                    NotificationId = notif.NotificationId,
                    UserId = notif.UserId,
                    CompanyId = notif.CompanyId,
                    TargetRole = notif.TargetRole,
                    Title = notif.Title,
                    Message = notif.Message,
                    Type = notif.Type,
                    ReferenceId = notif.ReferenceId,
                    IsRead = false,
                    CreatedAtUtc = notif.CreatedAtUtc
                });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to send CRM follow-up reminder for Lead {LeadId}", lead.LeadId);
            }
        }
    }
}
