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
[Route("api/notifications")]
[Authorize]
public class NotificationsController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IHubContext<LocationHub> _hub;

    public NotificationsController(LiveTrackingDbContext db, IHubContext<LocationHub> hub)
    {
        _db = db;
        _hub = hub;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return int.TryParse(claim, out var id) ? id : 0;
    }

    private bool IsAdmin() => User.IsInRole("Admin");

    /// <summary>
    /// Get notification history for current logged-in user or admin.
    /// </summary>
    [HttpGet]
    public async Task<ActionResult<List<NotificationDto>>> GetNotifications(
        [FromQuery] bool unreadOnly = false,
        [FromQuery] int take = 50,
        [FromQuery] int? companyId = null)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var currentUser = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId);
        int? targetCompanyId = currentUser?.CompanyId ?? companyId;
        bool isAdmin = IsAdmin();

        var query = _db.Notifications.AsQueryable();

        if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
        {
            if (isAdmin)
            {
                query = query.Where(n => (n.UserId == userId || ((n.CompanyId == targetCompanyId.Value || n.CompanyId == null) && (n.TargetRole == "Admin" || n.TargetRole == "All"))));
            }
            else
            {
                query = query.Where(n => (n.UserId == userId || ((n.CompanyId == targetCompanyId.Value || n.CompanyId == null) && (n.TargetRole == "User" || n.TargetRole == "All"))));
            }
        }
        else
        {
            if (isAdmin)
            {
                query = query.Where(n => n.UserId == userId || n.TargetRole == "Admin" || n.TargetRole == "All");
            }
            else
            {
                query = query.Where(n => n.UserId == userId || n.TargetRole == "User" || n.TargetRole == "All");
            }
        }

        if (unreadOnly)
        {
            query = query.Where(n => !n.IsRead);
        }

        var list = await query
            .OrderByDescending(n => n.CreatedAtUtc)
            .Take(take > 0 ? Math.Min(take, 100) : 50)
            .Select(n => new NotificationDto
            {
                NotificationId = n.NotificationId,
                UserId = n.UserId,
                CompanyId = n.CompanyId,
                TargetRole = n.TargetRole,
                Title = n.Title,
                Message = n.Message,
                Type = n.Type,
                ReferenceId = n.ReferenceId,
                IsRead = n.IsRead,
                CreatedAtUtc = n.CreatedAtUtc
            })
            .ToListAsync();

        return Ok(list);
    }

    /// <summary>
    /// Get unread notification count.
    /// </summary>
    [HttpGet("unread-count")]
    public async Task<ActionResult<UnreadCountResponseDto>> GetUnreadCount([FromQuery] int? companyId = null)
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var currentUser = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId);
        int? targetCompanyId = currentUser?.CompanyId ?? companyId;
        bool isAdmin = IsAdmin();

        var query = _db.Notifications.Where(n => !n.IsRead);

        if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
        {
            if (isAdmin)
            {
                query = query.Where(n => (n.UserId == userId || ((n.CompanyId == targetCompanyId.Value || n.CompanyId == null) && (n.TargetRole == "Admin" || n.TargetRole == "All"))));
            }
            else
            {
                query = query.Where(n => (n.UserId == userId || ((n.CompanyId == targetCompanyId.Value || n.CompanyId == null) && (n.TargetRole == "User" || n.TargetRole == "All"))));
            }
        }
        else
        {
            if (isAdmin)
            {
                query = query.Where(n => n.UserId == userId || n.TargetRole == "Admin" || n.TargetRole == "All");
            }
            else
            {
                query = query.Where(n => n.UserId == userId || n.TargetRole == "User" || n.TargetRole == "All");
            }
        }

        int count = await query.CountAsync();
        return Ok(new UnreadCountResponseDto { UnreadCount = count });
    }

    /// <summary>
    /// Mark a notification as read.
    /// </summary>
    [HttpPut("{id}/read")]
    public async Task<IActionResult> MarkAsRead([FromRoute] int id)
    {
        var item = await _db.Notifications.FindAsync(id);
        if (item == null) return NotFound();

        item.IsRead = true;
        await _db.SaveChangesAsync();

        return NoContent();
    }

    /// <summary>
    /// Mark all notifications as read for current user.
    /// </summary>
    [HttpPut("mark-all-read")]
    public async Task<IActionResult> MarkAllAsRead()
    {
        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var currentUser = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId);
        int? targetCompanyId = currentUser?.CompanyId;
        bool isAdmin = IsAdmin();

        var query = _db.Notifications.Where(n => !n.IsRead);

        if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
        {
            if (isAdmin)
            {
                query = query.Where(n => (n.UserId == userId || ((n.CompanyId == targetCompanyId.Value || n.CompanyId == null) && (n.TargetRole == "Admin" || n.TargetRole == "All"))));
            }
            else
            {
                query = query.Where(n => (n.UserId == userId || ((n.CompanyId == targetCompanyId.Value || n.CompanyId == null) && (n.TargetRole == "User" || n.TargetRole == "All"))));
            }
        }
        else
        {
            if (isAdmin)
            {
                query = query.Where(n => n.UserId == userId || n.TargetRole == "Admin" || n.TargetRole == "All");
            }
            else
            {
                query = query.Where(n => n.UserId == userId || n.TargetRole == "User" || n.TargetRole == "All");
            }
        }

        var unreadList = await query.ToListAsync();
        foreach (var item in unreadList)
        {
            item.IsRead = true;
        }

        await _db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// Send custom notification / announcement (Admin only).
    /// Broadcasts to SignalR clients immediately and saves to DB with CompanyId isolation.
    /// </summary>
    [HttpPost("broadcast")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<NotificationDto>> BroadcastNotification([FromBody] SendNotificationRequestDto req)
    {
        if (string.IsNullOrWhiteSpace(req.Title) || string.IsNullOrWhiteSpace(req.Message))
        {
            return BadRequest(new { message = "Title and Message are required." });
        }

        int currentAdminId = GetCurrentUserId();
        var currentAdmin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? targetCompanyId = req.CompanyId ?? currentAdmin?.CompanyId;

        var entity = new NotificationItem
        {
            UserId = req.UserId,
            CompanyId = targetCompanyId,
            TargetRole = string.IsNullOrWhiteSpace(req.TargetRole) ? "All" : req.TargetRole,
            Title = req.Title.Trim(),
            Message = req.Message.Trim(),
            Type = string.IsNullOrWhiteSpace(req.Type) ? "Announcement" : req.Type,
            ReferenceId = req.ReferenceId,
            IsRead = false,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.Notifications.Add(entity);
        await _db.SaveChangesAsync();

        var dto = new NotificationDto
        {
            NotificationId = entity.NotificationId,
            UserId = entity.UserId,
            CompanyId = entity.CompanyId,
            TargetRole = entity.TargetRole,
            Title = entity.Title,
            Message = entity.Message,
            Type = entity.Type,
            ReferenceId = entity.ReferenceId,
            IsRead = false,
            CreatedAtUtc = entity.CreatedAtUtc
        };

        // Dispatch SignalR event with company isolation
        if (req.UserId.HasValue && req.UserId.Value > 0)
        {
            await _hub.Clients.Group(LocationHub.UserGroup(req.UserId.Value)).SendAsync("ReceiveNotification", dto);
        }
        else if (entity.TargetRole.Equals("Admin", StringComparison.OrdinalIgnoreCase))
        {
            if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
            {
                await _hub.Clients.Group(LocationHub.CompanyAdminsGroup(targetCompanyId.Value)).SendAsync("ReceiveNotification", dto);
            }
            else
            {
                await _hub.Clients.Group(LocationHub.AdminsGroup).SendAsync("ReceiveNotification", dto);
            }
        }
        else if (entity.TargetRole.Equals("User", StringComparison.OrdinalIgnoreCase))
        {
            if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
            {
                await _hub.Clients.Group(LocationHub.CompanyUsersGroup(targetCompanyId.Value)).SendAsync("ReceiveNotification", dto);
            }
            else
            {
                await _hub.Clients.Group(LocationHub.UsersGroup).SendAsync("ReceiveNotification", dto);
            }
        }
        else
        {
            if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
            {
                await _hub.Clients.Group(LocationHub.CompanyAllGroup(targetCompanyId.Value)).SendAsync("ReceiveNotification", dto);
            }
            else
            {
                await _hub.Clients.Group(LocationHub.AllGroup).SendAsync("ReceiveNotification", dto);
            }
        }

        return Ok(dto);
    }
}
