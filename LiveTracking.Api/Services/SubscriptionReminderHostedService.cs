using LiveTracking.Api.Data;
using LiveTracking.Api.Hubs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.SignalR;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using System.Data;

namespace LiveTracking.Api.Services;

public class SubscriptionReminderHostedService : BackgroundService
{
    private readonly IServiceProvider _services;
    private readonly ILogger<SubscriptionReminderHostedService> _logger;

    public SubscriptionReminderHostedService(IServiceProvider services, ILogger<SubscriptionReminderHostedService> logger)
    {
        _services = services;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // Initial brief delay before starting background check
        await Task.Delay(TimeSpan.FromSeconds(15), stoppingToken);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await CheckAndNotifyAdminsAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error occurred in SubscriptionReminderHostedService");
            }

            // Check every 4 hours
            await Task.Delay(TimeSpan.FromHours(4), stoppingToken);
        }
    }

    private async Task CheckAndNotifyAdminsAsync()
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<LiveTrackingDbContext>();
        var hub = scope.ServiceProvider.GetRequiredService<IHubContext<LocationHub>>();

        var admins = await db.Users.Where(u => u.Role == "Admin" && u.IsActive).ToListAsync();

        var todayUtc = DateTime.UtcNow.Date;

        foreach (var admin in admins)
        {
            try
            {
                var conn = db.Database.GetDbConnection();
                if (conn.State != ConnectionState.Open)
                    await conn.OpenAsync();

                using var cmd = conn.CreateCommand();
                cmd.CommandText = "sp_GetSubscriptionStatus";
                cmd.CommandType = CommandType.StoredProcedure;
                cmd.Parameters.Add(new SqlParameter("@UserId", SqlDbType.Int) { Value = admin.UserId });

                using var reader = await cmd.ExecuteReaderAsync();
                if (await reader.ReadAsync())
                {
                    var isExpired = reader.GetInt32(reader.GetOrdinal("IsExpired")) == 1;
                    var isWarning = reader.GetInt32(reader.GetOrdinal("IsWarningPeriod")) == 1;
                    var daysRemaining = reader.GetInt32(reader.GetOrdinal("DaysRemaining"));
                    DateTime? dueDate = reader.IsDBNull(reader.GetOrdinal("PaymentDueDate")) ? null : reader.GetDateTime(reader.GetOrdinal("PaymentDueDate"));

                    if (isWarning && !isExpired)
                    {
                        // Check if we already notified today to prevent spam
                        var alreadyNotifiedToday = await db.Notifications.AnyAsync(n =>
                            n.UserId == admin.UserId &&
                            n.Type == "BillingWarning" &&
                            n.CreatedAtUtc >= todayUtc);

                        if (!alreadyNotifiedToday)
                        {
                            var notif = new NotificationItem
                            {
                                UserId = admin.UserId,
                                CompanyId = admin.CompanyId,
                                TargetRole = "Admin",
                                Title = "⚠️ বিল পরিশোধের সতর্কতা",
                                Message = $"আপনার অ্যাকাউন্টের বিল পরিশোধের মেয়াদ আর {daysRemaining} দিন বাকি আছে ({dueDate:dd MMM yyyy})। নিরবচ্ছিন্ন সেবা পেতে দ্রুত বিল পরিশোধ করুন।",
                                Type = "BillingWarning",
                                ReferenceId = admin.UserId.ToString(),
                                IsRead = false,
                                CreatedAtUtc = DateTime.UtcNow
                            };
                            db.Notifications.Add(notif);
                            await db.SaveChangesAsync();

                            await hub.Clients.Group(LocationHub.UserGroup(admin.UserId)).SendAsync("ReceiveNotification", new LiveTracking.Api.DTOs.NotificationDto
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
                            _logger.LogInformation("Sent subscription warning notification to Admin {AdminId} ({DaysRemaining} days remaining)", admin.UserId, daysRemaining);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to check subscription for Admin {AdminId}", admin.UserId);
            }
        }
    }
}
