using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Hubs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.SignalR;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using System.Data;
using System.Security.Claims;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class SubscriptionController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IHubContext<LocationHub> _hub;

    public SubscriptionController(LiveTrackingDbContext db, IHubContext<LocationHub> hub)
    {
        _db = db;
        _hub = hub;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirstValue(ClaimTypes.NameIdentifier);
        return int.TryParse(claim, out var id) ? id : 0;
    }

    [HttpGet("status")]
    public async Task<ActionResult<SubscriptionStatusDto>> GetStatus()
    {
        var currentUserId = GetCurrentUserId();
        if (currentUserId <= 0)
        {
            return Unauthorized();
        }

        return await FetchSubscriptionStatusFromSpAsync(currentUserId);
    }

    [HttpGet("user/{userId:int}/status")]
    public async Task<ActionResult<SubscriptionStatusDto>> GetUserStatus(int userId)
    {
        return await FetchSubscriptionStatusFromSpAsync(userId);
    }

    [HttpGet("plans")]
    public async Task<ActionResult<List<SubscriptionPlan>>> GetPlans()
    {
        try
        {
            var plans = await _db.SubscriptionPlans
                .AsNoTracking()
                .Where(p => p.IsActive)
                .OrderBy(p => p.DisplayOrder)
                .ToListAsync();

            return Ok(plans);
        }
        catch (Exception ex)
        {
            return StatusCode(500, new { message = "Error loading subscription plans", error = ex.Message });
        }
    }

    private static List<SubscriptionPlan> GetDefaultFallbackPlans()
    {
        return new List<SubscriptionPlan>
        {
            new()
            {
                PlanId = 1,
                PlanCode = "REGULAR_1M",
                TierName = "Regular",
                Title = "1 Month Regular Plan",
                TitleBn = "১ মাস রেগুলার প্ল্যান",
                DurationMonths = 1,
                Price = 1000,
                OriginalPrice = 1000,
                DiscountPercent = 0,
                DiscountText = "Regular Price",
                BadgeText = "Starter",
                BadgeTextBn = "শুরু করার জন্য",
                FeaturesJson = "[\"Live GPS Tracking & Route History\",\"Real-time Attendance & Geofencing\",\"Customer & Visit Logging\",\"Standard Support\"]",
                IsActive = true,
                DisplayOrder = 1
            },
            new()
            {
                PlanId = 2,
                PlanCode = "SILVER_3M",
                TierName = "Silver",
                Title = "3 Months Silver Pack",
                TitleBn = "৩ মাস সিলভার প্যাক",
                DurationMonths = 3,
                Price = 2500,
                OriginalPrice = 3000,
                DiscountPercent = 17,
                DiscountText = "Save ৳500 (17% OFF)",
                BadgeText = "Popular Offer",
                BadgeTextBn = "🔥 আকর্ষণীয় অফার (জনপ্রিয়)",
                FeaturesJson = "[\"All Regular Features Included\",\"3 Months Uninterrupted Access\",\"Save ৳500 Total Discount\",\"Priority Support Response\",\"Daily Activity & Performance Reports\"]",
                IsActive = true,
                DisplayOrder = 2
            },
            new()
            {
                PlanId = 3,
                PlanCode = "GOLD_6M",
                TierName = "Gold",
                Title = "6 Months Gold Pack",
                TitleBn = "৬ মাস গোল্ড প্যাক",
                DurationMonths = 6,
                Price = 4500,
                OriginalPrice = 6000,
                DiscountPercent = 25,
                DiscountText = "Save ৳1,500 (25% OFF)",
                BadgeText = "Best Value",
                BadgeTextBn = "⭐ সেরা সাশ্রয়ী (বেস্ট ভ্যালু)",
                FeaturesJson = "[\"All Silver Features Included\",\"6 Months Guaranteed Service\",\"Save ৳1,500 Mega Discount\",\"Real-time SignalR Alerts\",\"VIP Account Manager\"]",
                IsActive = true,
                DisplayOrder = 3
            },
            new()
            {
                PlanId = 4,
                PlanCode = "PLATINUM_12M",
                TierName = "Platinum",
                Title = "1 Year Platinum Super Saver",
                TitleBn = "১ বছর প্ল্যাটিনাম সুপার সেভার",
                DurationMonths = 12,
                Price = 8000,
                OriginalPrice = 12000,
                DiscountPercent = 33,
                DiscountText = "Save ৳4,000 (33% OFF)",
                BadgeText = "Mega Saver",
                BadgeTextBn = "💎 মেগা সেভার (সর্বোচ্চ ছাড়)",
                FeaturesJson = "[\"Full Year 365 Days Access\",\"Save ৳4,000 Huge Discount\",\"Unlimited Employees Support\",\"24/7 Dedicated Priority Hotline\",\"Zero Interruption Guarantee\"]",
                IsActive = true,
                DisplayOrder = 4
            }
        };
    }

    [HttpPost("update-due-date")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<SubscriptionStatusDto>> UpdateDueDate([FromBody] UpdatePaymentDueDateRequest request)
    {
        if (request.AdminId <= 0)
        {
            return BadRequest(new { message = "Invalid Admin ID" });
        }

        try
        {
            var adminIdParam = new SqlParameter("@AdminId", SqlDbType.Int) { Value = request.AdminId };
            var newDateParam = new SqlParameter("@NewDueDate", SqlDbType.DateTime) { Value = request.NewDueDate };

            var conn = _db.Database.GetDbConnection();
            if (conn.State != ConnectionState.Open)
                await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = "sp_UpdatePaymentDueDate";
            cmd.CommandType = CommandType.StoredProcedure;
            cmd.Parameters.Add(adminIdParam);
            cmd.Parameters.Add(newDateParam);

            using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
            {
                var adminId = reader.GetInt32(reader.GetOrdinal("AdminId"));
                var adminName = reader.GetString(reader.GetOrdinal("AdminName"));
                var adminUsername = reader.GetString(reader.GetOrdinal("AdminUsername"));
                var adminPhone = reader.IsDBNull(reader.GetOrdinal("AdminPhone")) ? null : reader.GetString(reader.GetOrdinal("AdminPhone"));
                DateTime? dueDate = reader.IsDBNull(reader.GetOrdinal("PaymentDueDate")) ? null : reader.GetDateTime(reader.GetOrdinal("PaymentDueDate"));
                var daysRemaining = reader.GetInt32(reader.GetOrdinal("DaysRemaining"));
                var isExpired = reader.GetInt32(reader.GetOrdinal("IsExpired")) == 1;
                var isWarning = reader.GetInt32(reader.GetOrdinal("IsWarningPeriod")) == 1;

                string statusText = isExpired ? "মেয়াদ উত্তীর্ণ (Expired)" : (isWarning ? $"সতর্কতা: {daysRemaining} দিন বাকি (Warning)" : "সক্রিয় (Active)");

                // Notify admin via SignalR
                try
                {
                    await _hub.Clients.Group(LocationHub.AdminsGroup).SendAsync("SubscriptionUpdated", new
                    {
                        AdminId = adminId,
                        PaymentDueDate = dueDate,
                        DaysRemaining = daysRemaining,
                        IsExpired = isExpired
                    });
                }
                catch { }

                return Ok(new SubscriptionStatusDto(
                    AdminId: adminId,
                    AdminName: adminName,
                    AdminUsername: adminUsername,
                    AdminPhone: adminPhone,
                    PaymentDueDate: dueDate,
                    DaysRemaining: daysRemaining,
                    IsExpired: isExpired,
                    IsWarningPeriod: isWarning,
                    StatusText: statusText
                ));
            }

            return BadRequest(new { message = "Failed to update payment due date." });
        }
        catch (Exception ex)
        {
            return StatusCode(500, new { message = "Error updating payment date", error = ex.Message });
        }
    }

    [HttpPost("check-and-notify")]
    [Authorize(Roles = "Admin")]
    public async Task<IActionResult> CheckAndNotifyWarning()
    {
        var admins = await _db.Users.Where(u => u.Role == "Admin" && u.IsActive).ToListAsync();
        int notifiedCount = 0;

        foreach (var admin in admins)
        {
            var statusResult = await FetchSubscriptionStatusFromSpAsync(admin.UserId);
            if (statusResult.Value != null)
            {
                var st = statusResult.Value;
                if (st.IsWarningPeriod && !st.IsExpired)
                {
                    try
                    {
                        var notif = new NotificationItem
                        {
                            UserId = admin.UserId,
                            CompanyId = admin.CompanyId,
                            TargetRole = "Admin",
                            Title = "⚠️ বিল পরিশোধের সতর্কতা",
                            Message = $"আপনার অ্যাকাউন্টের বিল পরিশোধের মেয়াদ আর {st.DaysRemaining} দিন বাকি আছে ({st.PaymentDueDate:dd MMM yyyy})। নিরবচ্ছিন্ন সেবা পেতে দ্রুত বিল পরিশোধ করুন।",
                            Type = "BillingWarning",
                            ReferenceId = admin.UserId.ToString(),
                            IsRead = false,
                            CreatedAtUtc = DateTime.UtcNow
                        };
                        _db.Notifications.Add(notif);
                        await _db.SaveChangesAsync();

                        await _hub.Clients.Group(LocationHub.UserGroup(admin.UserId)).SendAsync("ReceiveNotification", new LiveTracking.Api.DTOs.NotificationDto
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
                        notifiedCount++;
                    }
                    catch { }
                }
            }
        }

        return Ok(new { message = $"Subscription warning check completed. {notifiedCount} notifications sent." });
    }

    private async Task<ActionResult<SubscriptionStatusDto>> FetchSubscriptionStatusFromSpAsync(int userId)
    {
        try
        {
            var userParam = new SqlParameter("@UserId", SqlDbType.Int) { Value = userId };

            var conn = _db.Database.GetDbConnection();
            if (conn.State != ConnectionState.Open)
                await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = "sp_GetSubscriptionStatus";
            cmd.CommandType = CommandType.StoredProcedure;
            cmd.Parameters.Add(userParam);

            using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
            {
                var adminId = reader.GetInt32(reader.GetOrdinal("AdminId"));
                var adminName = reader.GetString(reader.GetOrdinal("AdminName"));
                var adminUsername = reader.GetString(reader.GetOrdinal("AdminUsername"));
                var adminPhone = reader.IsDBNull(reader.GetOrdinal("AdminPhone")) ? null : reader.GetString(reader.GetOrdinal("AdminPhone"));
                DateTime? dueDate = reader.IsDBNull(reader.GetOrdinal("PaymentDueDate")) ? null : reader.GetDateTime(reader.GetOrdinal("PaymentDueDate"));
                var daysRemaining = reader.GetInt32(reader.GetOrdinal("DaysRemaining"));
                var isExpired = reader.GetInt32(reader.GetOrdinal("IsExpired")) == 1;
                var isWarning = reader.GetInt32(reader.GetOrdinal("IsWarningPeriod")) == 1;

                string statusText = isExpired ? "মেয়াদ উত্তীর্ণ (Expired)" : (isWarning ? $"সতর্কতা: {daysRemaining} দিন বাকি (Warning)" : "সক্রিয় (Active)");

                return Ok(new SubscriptionStatusDto(
                    AdminId: adminId,
                    AdminName: adminName,
                    AdminUsername: adminUsername,
                    AdminPhone: adminPhone,
                    PaymentDueDate: dueDate,
                    DaysRemaining: daysRemaining,
                    IsExpired: isExpired,
                    IsWarningPeriod: isWarning,
                    StatusText: statusText
                ));
            }

            // Fallback default
            return Ok(new SubscriptionStatusDto(
                AdminId: 1,
                AdminName: "Administrator",
                AdminUsername: "admin",
                AdminPhone: null,
                PaymentDueDate: DateTime.UtcNow.AddDays(30),
                DaysRemaining: 30,
                IsExpired: false,
                IsWarningPeriod: false,
                StatusText: "সক্রিয় (Active)"
            ));
        }
        catch (Exception ex)
        {
            return StatusCode(500, new { message = "Error querying subscription status", error = ex.Message });
        }
    }
}
