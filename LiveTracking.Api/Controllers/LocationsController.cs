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
public class LocationsController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IHubContext<LocationHub> _hub;

    public LocationsController(LiveTrackingDbContext db, IHubContext<LocationHub> hub)
    {
        _db = db;
        _hub = hub;
    }

    private int CurrentUserId
    {
        get
        {
            var idClaim = User.FindFirstValue(ClaimTypes.NameIdentifier)
                ?? User.FindFirstValue("sub")
                ?? User.Claims.FirstOrDefault(c => c.Type == System.IdentityModel.Tokens.Jwt.JwtRegisteredClaimNames.Sub)?.Value;

            return int.TryParse(idClaim, out var id) ? id : 0;
        }
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

    // Called by the Android foreground service every ~60s.
    [HttpPost("ping")]
    [Authorize]
    public async Task<IActionResult> Ping([FromBody] LocationPingRequest request)
    {
        var userId = CurrentUserId;
        if (userId <= 0)
        {
            return Unauthorized(new { message = "Invalid user identity from token." });
        }

        DateTime recordedAtTime = DateTime.UtcNow;
        if (request.RecordedAtUtc.HasValue && request.RecordedAtUtc.Value.Year >= 2000)
        {
            recordedAtTime = request.RecordedAtUtc.Value.ToUniversalTime();
        }
        else if (!string.IsNullOrWhiteSpace(request.RecordedAt) && DateTime.TryParse(request.RecordedAt, out var parsed) && parsed.Year >= 2000)
        {
            recordedAtTime = parsed.ToUniversalTime();
        }

        var location = new DriverLocation
        {
            UserId = userId,
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            Accuracy = request.Accuracy,
            Speed = request.Speed,
            Bearing = request.Bearing,
            RecordedAtUtc = recordedAtTime,
            LocationAddress = request.LocationAddress
        };

        try
        {
            _db.DriverLocations.Add(location);
            await _db.SaveChangesAsync();
        }
        catch (Exception ex)
        {
            var errMsg = ex.InnerException?.Message ?? ex.Message;
            return StatusCode(500, new { message = "Failed to save location", error = errMsg });
        }

        try
        {
            var user = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId);
            var payload = new LocationResponse(
                userId,
                user?.Username ?? "",
                !string.IsNullOrEmpty(user?.FullName) ? user.FullName : (user?.Username ?? ""),
                user?.IsActive ?? true,
                location.Latitude,
                location.Longitude,
                location.Accuracy,
                location.Speed,
                location.Bearing,
                location.RecordedAtUtc,
                location.LocationAddress
            );

            if (user?.CompanyId.HasValue == true)
            {
                await _hub.Clients.Group(LocationHub.CompanyAdminsGroup(user.CompanyId.Value)).SendAsync("LocationUpdated", payload);
            }
            else
            {
                await _hub.Clients.Group(LocationHub.AdminsGroup).SendAsync("LocationUpdated", payload);
            }
        }
        catch { }

        return Ok(new { success = true, recordedAt = location.RecordedAtUtc });
    }

    // Admin: latest known location for every user.
    // Company & Office-based visibility:
    // 1. If admin belongs to a company, only employees of that company are returned.
    // 2. If admin is assigned to specific offices, only employees in those offices are returned.
    [HttpGet("latest")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<List<LocationResponse>>> GetLatest([FromQuery] int? companyId = null)
    {
        var currentAdminId = CurrentUserId;
        var currentAdmin = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == currentAdminId);
        int? targetCompanyId = currentAdmin?.CompanyId ?? companyId;
        var assignedOfficeIds = await GetAdminAssignedOfficeIdsAsync(currentAdminId);

        var usersQuery = _db.Users.Where(u => u.Role != "Admin");

        // Filter by Company if admin belongs to a company
        if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
        {
            usersQuery = usersQuery.Where(u => u.CompanyId == targetCompanyId.Value);
        }

        if (assignedOfficeIds.Count > 0)
        {
            usersQuery = usersQuery.Where(u => u.OfficeLocationId.HasValue && assignedOfficeIds.Contains(u.OfficeLocationId.Value));
        }

        var users = await usersQuery.ToListAsync();
        var userIds = users.Select(u => u.UserId).ToList();

        // Get latest location for each user
        var latestLocations = await _db.DriverLocations
            .Where(l => userIds.Contains(l.UserId))
            .GroupBy(l => l.UserId)
            .Select(g => g.OrderByDescending(l => l.RecordedAtUtc).FirstOrDefault())
            .ToListAsync();

        var locationMap = latestLocations
            .Where(l => l != null)
            .ToDictionary(l => l!.UserId, l => l!);

        var results = users.Select(u =>
        {
            locationMap.TryGetValue(u.UserId, out var loc);
            return new LocationResponse(
                u.UserId,
                u.Username,
                !string.IsNullOrEmpty(u.FullName) ? u.FullName : u.Username,
                u.IsActive,
                loc?.Latitude,
                loc?.Longitude,
                loc?.Accuracy,
                loc?.Speed,
                loc?.Bearing,
                loc?.RecordedAtUtc,
                loc?.LocationAddress
            );
        }).ToList();

        return Ok(results);
    }

    // Admin: latest location for a single user.
    [HttpGet("latest/{userId:int}")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<LocationResponse>> GetLatestByUser(int userId)
    {
        var currentAdminId = CurrentUserId;
        var currentAdmin = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == currentAdminId);
        int? targetCompanyId = currentAdmin?.CompanyId;

        var user = await _db.Users.FindAsync(userId);
        if (user is null) return NotFound();

        if (targetCompanyId.HasValue && user.CompanyId != targetCompanyId.Value)
        {
            return Forbid();
        }

        var latest = await _db.DriverLocations
            .Where(l => l.UserId == userId)
            .OrderByDescending(l => l.RecordedAtUtc)
            .FirstOrDefaultAsync();

        return Ok(new LocationResponse(user.UserId, user.Username, user.FullName, user.IsActive,
            latest?.Latitude, latest?.Longitude, latest?.Accuracy, latest?.Speed, latest?.Bearing, latest?.RecordedAtUtc, latest?.LocationAddress));
    }

    // Admin: full route (every GPS ping) for one driver on a given date, used by the
    // "Driver Route History" map screen to draw the polyline.
    [HttpGet("history/{userId:int}")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<List<LocationResponse>>> GetRouteHistory(
        int userId,
        [FromQuery] string? date = null,
        [FromQuery] string? from = null,
        [FromQuery] string? to = null)
    {
        var currentAdminId = CurrentUserId;
        var currentAdmin = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == currentAdminId);
        int? targetCompanyId = currentAdmin?.CompanyId;

        var user = await _db.Users.FindAsync(userId);
        if (user is null) return NotFound(new { message = "User not found." });

        if (targetCompanyId.HasValue && user.CompanyId != targetCompanyId.Value)
        {
            return Forbid();
        }

        DateTime fromUtc;
        DateTime toUtc;

        if (!string.IsNullOrWhiteSpace(from) && !string.IsNullOrWhiteSpace(to) &&
            DateTime.TryParse(from, out var fDate) && DateTime.TryParse(to, out var tDate))
        {
            fromUtc = fDate.ToUniversalTime();
            toUtc = tDate.ToUniversalTime();
        }
        else if (!string.IsNullOrWhiteSpace(date) && DateTime.TryParse(date, out var dDate))
        {
            // The selected date (assumed to be Bangladesh Standard Time UTC+6 or local)
            // Bangladesh day: from 00:00:00 BST (-6h UTC) to 23:59:59 BST (+18h UTC)
            var targetDay = dDate.Date;
            fromUtc = targetDay.AddHours(-6);
            toUtc = fromUtc.AddHours(24);
        }
        else
        {
            var nowBd = DateTime.UtcNow.AddHours(6).Date;
            fromUtc = nowBd.AddHours(-6);
            toUtc = fromUtc.AddHours(24);
        }

        var locations = await _db.DriverLocations
            .Where(l => l.UserId == userId && l.RecordedAtUtc >= fromUtc && l.RecordedAtUtc <= toUtc)
            .OrderBy(l => l.RecordedAtUtc)
            .Select(l => new LocationResponse(
                user.UserId,
                user.Username,
                !string.IsNullOrEmpty(user.FullName) ? user.FullName : user.Username,
                user.IsActive,
                l.Latitude,
                l.Longitude,
                l.Accuracy,
                l.Speed,
                l.Bearing,
                l.RecordedAtUtc,
                l.LocationAddress
            ))
            .ToListAsync();

        // Fallback: If no locations found in exact BST range, check standard UTC calendar day
        if (locations.Count == 0 && !string.IsNullOrWhiteSpace(date) && DateTime.TryParse(date, out var fallbackDate))
        {
            var utcStart = fallbackDate.Date;
            var utcEnd = utcStart.AddDays(1);
            locations = await _db.DriverLocations
                .Where(l => l.UserId == userId && l.RecordedAtUtc >= utcStart && l.RecordedAtUtc < utcEnd)
                .OrderBy(l => l.RecordedAtUtc)
                .Select(l => new LocationResponse(
                    user.UserId,
                    user.Username,
                    !string.IsNullOrEmpty(user.FullName) ? user.FullName : user.Username,
                    user.IsActive,
                    l.Latitude,
                    l.Longitude,
                    l.Accuracy,
                    l.Speed,
                    l.Bearing,
                    l.RecordedAtUtc,
                    l.LocationAddress
                ))
                .ToListAsync();
        }

        // Second Fallback: If still empty and date wasn't strictly specified or was today, get latest 200 pings
        if (locations.Count == 0 && (string.IsNullOrWhiteSpace(date) || (DateTime.TryParse(date, out var checkToday) && checkToday.Date == DateTime.UtcNow.AddHours(6).Date)))
        {
            var recentUtc = DateTime.UtcNow.AddHours(-24);
            locations = await _db.DriverLocations
                .Where(l => l.UserId == userId && l.RecordedAtUtc >= recentUtc)
                .OrderBy(l => l.RecordedAtUtc)
                .Select(l => new LocationResponse(
                    user.UserId,
                    user.Username,
                    !string.IsNullOrEmpty(user.FullName) ? user.FullName : user.Username,
                    user.IsActive,
                    l.Latitude,
                    l.Longitude,
                    l.Accuracy,
                    l.Speed,
                    l.Bearing,
                    l.RecordedAtUtc,
                    l.LocationAddress
                ))
                .ToListAsync();
        }

        return Ok(locations);
    }
}
