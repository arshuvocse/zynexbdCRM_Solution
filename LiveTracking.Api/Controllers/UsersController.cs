using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Hubs;
using LiveTracking.Api.Models;
using LiveTracking.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.SignalR;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize(Roles = "Admin")]
public class UsersController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IPasswordHasherService _hasher;
    private readonly IHubContext<LocationHub> _hub;

    public UsersController(LiveTrackingDbContext db, IPasswordHasherService hasher, IHubContext<LocationHub> hub)
    {
        _db = db;
        _hasher = hasher;
        _hub = hub;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value
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

    private async Task<UserResponse> ToResponseAsync(User u)
    {
        string? officeLocationName = u.OfficeLocationId.HasValue
            ? await _db.OfficeLocations
                .Where(o => o.OfficeLocationId == u.OfficeLocationId.Value)
                .Select(o => o.Name)
                .FirstOrDefaultAsync()
            : null;

        List<int>? assignedOfficeIds = null;
        List<string>? assignedOfficeNames = null;

        if (u.Role == "Admin" || u.Role == "Manager")
        {
            var adminOffices = await _db.AdminOfficeLocations
                .Include(a => a.OfficeLocation)
                .Where(a => a.AdminUserId == u.UserId)
                .ToListAsync();

            if (adminOffices.Count > 0)
            {
                assignedOfficeIds = adminOffices.Select(a => a.OfficeLocationId).ToList();
                assignedOfficeNames = adminOffices.Where(a => a.OfficeLocation != null).Select(a => a.OfficeLocation!.Name).ToList();
            }
            else if (u.OfficeLocationId.HasValue && !string.IsNullOrEmpty(officeLocationName))
            {
                assignedOfficeIds = new List<int> { u.OfficeLocationId.Value };
                assignedOfficeNames = new List<string> { officeLocationName };
            }
        }

        return new UserResponse(
            u.UserId,
            u.Username,
            u.FullName,
            u.Role,
            u.IsActive,
            u.PhoneNumber,
            u.OfficeLocationId,
            officeLocationName,
            u.CreatedByAdminId,
            u.MaxUserLimit,
            u.BoundDeviceId,
            u.DeviceModel,
            assignedOfficeIds,
            assignedOfficeNames
        );
    }

    [HttpGet("quota")]
    public async Task<ActionResult<AdminUserQuotaDto>> GetQuota([FromQuery] int? companyId = null)
    {
        var currentAdminId = GetCurrentUserId();
        var admin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? targetCompanyId = admin?.CompanyId ?? companyId;
        Company? company = targetCompanyId.HasValue ? await _db.Companies.FindAsync(targetCompanyId.Value) : null;
        int maxLimit = company?.MaxUserLimit ?? admin?.MaxUserLimit ?? 10;

        var query = _db.Users.AsQueryable();
        if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
        {
            query = query.Where(u => u.CompanyId == targetCompanyId.Value && u.Role != "Admin" && u.IsActive);
        }
        else if (currentAdminId > 0 && admin?.Role == "Admin")
        {
            query = query.Where(u => (u.CreatedByAdminId == currentAdminId || u.CreatedByAdminId == null) && u.Role != "Admin" && u.IsActive);
        }
        else
        {
            query = query.Where(u => u.Role != "Admin" && u.IsActive);
        }

        int usedCount = await query.CountAsync();
        int remaining = Math.Max(0, maxLimit - usedCount);
        return Ok(new AdminUserQuotaDto(
            MaxUserLimit: maxLimit,
            UsedUserCount: usedCount,
            RemainingUserCount: remaining,
            IsLimitReached: usedCount >= maxLimit
        ));
    }

    [HttpGet]
    public async Task<ActionResult<List<UserResponse>>> GetAll([FromQuery] int? companyId = null)
    {
        var currentAdminId = GetCurrentUserId();

        // Office-based visibility: an admin assigned to offices only sees users in
        // those same offices. An admin with no offices assigned (super-admin) is
        // unrestricted, so existing/unassigned admin accounts aren't locked out.
        var assignedOfficeIds = await GetAdminAssignedOfficeIdsAsync(currentAdminId);
        var currentAdmin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? targetCompanyId = currentAdmin?.CompanyId ?? companyId;

        var query = _db.Users
            .Include(u => u.AdminOfficeLocations)
            .ThenInclude(a => a.OfficeLocation)
            .AsQueryable();

        if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
        {
            query = query.Where(u => u.CompanyId == targetCompanyId.Value);
        }

        if (assignedOfficeIds.Count > 0)
        {
            query = query.Where(u => u.UserId == currentAdminId
                                  || (u.OfficeLocationId.HasValue && assignedOfficeIds.Contains(u.OfficeLocationId.Value))
                                  || ((u.Role == "Admin" || u.Role == "Manager") && u.AdminOfficeLocations.Any(a => assignedOfficeIds.Contains(a.OfficeLocationId))));
        }

        var users = await query.OrderBy(u => u.Username).ToListAsync();
        var officeNames = await _db.OfficeLocations.ToDictionaryAsync(o => o.OfficeLocationId, o => o.Name);

        var result = users.Select(u =>
        {
            string? officeLocName = u.OfficeLocationId.HasValue && officeNames.TryGetValue(u.OfficeLocationId.Value, out var name) ? name : null;
            List<int>? aIds = null;
            List<string>? aNames = null;

            if (u.Role == "Admin" || u.Role == "Manager")
            {
                if (u.AdminOfficeLocations != null && u.AdminOfficeLocations.Count > 0)
                {
                    aIds = u.AdminOfficeLocations.Select(a => a.OfficeLocationId).ToList();
                    aNames = u.AdminOfficeLocations.Where(a => a.OfficeLocation != null).Select(a => a.OfficeLocation!.Name).ToList();
                }
                else if (u.OfficeLocationId.HasValue && officeLocName != null)
                {
                    aIds = new List<int> { u.OfficeLocationId.Value };
                    aNames = new List<string> { officeLocName };
                }
            }

            return new UserResponse(
                u.UserId,
                u.Username,
                u.FullName,
                u.Role,
                u.IsActive,
                u.PhoneNumber,
                u.OfficeLocationId,
                officeLocName,
                u.CreatedByAdminId,
                u.MaxUserLimit,
                u.BoundDeviceId,
                u.DeviceModel,
                aIds,
                aNames
            );
        }).ToList();

        return Ok(result);
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<UserResponse>> GetById(int id)
    {
        var user = await _db.Users.FindAsync(id);
        return user is null ? NotFound(new { message = "User not found" }) : Ok(await ToResponseAsync(user));
    }

    [HttpPost]
    public async Task<ActionResult<UserResponse>> Create(CreateUserRequest request)
    {
        var currentAdminId = GetCurrentUserId();
        var currentAdmin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? targetCompanyId = currentAdmin?.CompanyId;

        Company? company = null;
        if (targetCompanyId.HasValue)
        {
            company = await _db.Companies.FindAsync(targetCompanyId.Value);
        }

        int maxAllowed = company?.MaxUserLimit ?? currentAdmin?.MaxUserLimit ?? 10;

        // Dynamic Company User Quota check
        int existingCount = targetCompanyId.HasValue
            ? await _db.Users.CountAsync(u => u.CompanyId == targetCompanyId.Value && u.Role != "Admin" && u.IsActive)
            : await _db.Users.CountAsync(u => (u.CreatedByAdminId == currentAdminId || u.CreatedByAdminId == null) && u.Role != "Admin" && u.IsActive);

        if (request.Role == "Admin")
        {
            return BadRequest(new { message = "অ্যাপ থেকে অ্যাডমিন তৈরি করার অনুমতি নেই। অ্যাডমিন অ্যাকাউন্ট ডাটাবেজ থেকে ম্যানুয়ালি তৈরি করতে হবে।" });
        }

        if (existingCount >= maxAllowed)
        {
            return BadRequest(new { 
                message = $"ইউজার লিমিট পূর্ণ হয়েছে! আপনার কোম্পানির সর্বোচ্চ ইউজার সংখ্যা: {maxAllowed} জন (বর্তমান: {existingCount} জন)। আর নতুন ইউজার তৈরি করা যাবে না।" 
            });
        }

        if (await _db.Users.AnyAsync(u => u.Username == request.Username))
            return Conflict(new { message = "Username already exists." });

        if (!string.IsNullOrWhiteSpace(request.PhoneNumber))
        {
            var cleanPhone = request.PhoneNumber.Trim();
            if (await _db.Users.AnyAsync(u => u.PhoneNumber == cleanPhone))
            {
                return BadRequest(new { message = $"এই মোবাইল নম্বরটি ({cleanPhone}) দিয়ে ইতিমধ্যে একজন কর্মী/ইউজার তৈরি করা আছে।" });
            }
        }

        if (!request.OfficeLocationId.HasValue)
            return BadRequest(new { message = "ফিল্ড ইউজারের জন্য অফিস লোকেশন সিলেক্ট করা আবশ্যক।" });

        // If current admin has restricted offices, verify the employee is placed in one of those offices
        var adminOfficeIds = await GetAdminAssignedOfficeIdsAsync(currentAdminId);
        if (adminOfficeIds.Count > 0 && request.OfficeLocationId.HasValue && !adminOfficeIds.Contains(request.OfficeLocationId.Value))
        {
            return BadRequest(new { message = "আপনি শুধুমাত্র আপনার অধীনস্থ অফিস লোকেশনে ইউজার তৈরি করতে পারবেন।" });
        }

        int? primaryOfficeId = request.OfficeLocationId;

        // Auto-assign company default shift
        int? defaultShiftId = null;
        if (targetCompanyId.HasValue && targetCompanyId.Value > 0)
        {
            var defaultShift = await _db.Shifts
                .FirstOrDefaultAsync(s => s.CompanyId == targetCompanyId.Value && s.IsDefault && s.IsActive)
                ?? await _db.Shifts.FirstOrDefaultAsync(s => s.CompanyId == targetCompanyId.Value && s.IsActive);
            defaultShiftId = defaultShift?.ShiftId;
        }

        var user = new User
        {
            Username = request.Username,
            FullName = request.FullName,
            Role = request.Role == "Manager" ? "Manager" : "User",
            PhoneNumber = request.PhoneNumber,
            OfficeLocationId = primaryOfficeId,
            CompanyId = targetCompanyId,
            ShiftId = defaultShiftId,
            CreatedByAdminId = currentAdminId > 0 ? currentAdminId : null,
            MaxUserLimit = null,
            IsActive = true
        };
        user.PasswordHash = _hasher.Hash(user, request.Password);

        _db.Users.Add(user);
        await _db.SaveChangesAsync();

        // Assign multiple office locations for admin/manager
        if ((user.Role == "Admin" || user.Role == "Manager") && request.AssignedOfficeLocationIds != null && request.AssignedOfficeLocationIds.Count > 0)
        {
            foreach (var officeId in request.AssignedOfficeLocationIds.Distinct())
            {
                if (await _db.OfficeLocations.AnyAsync(o => o.OfficeLocationId == officeId))
                {
                    _db.AdminOfficeLocations.Add(new AdminOfficeLocation
                    {
                        AdminUserId = user.UserId,
                        OfficeLocationId = officeId,
                        AssignedAtUtc = DateTime.UtcNow
                    });
                }
            }
            await _db.SaveChangesAsync();
        }

        return CreatedAtAction(nameof(GetById), new { id = user.UserId }, await ToResponseAsync(user));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<UserResponse>> Update(int id, UpdateUserRequest request)
    {
        var user = await _db.Users.FindAsync(id);
        if (user is null) return NotFound(new { message = "User not found" });

        if (!string.IsNullOrWhiteSpace(request.FullName)) user.FullName = request.FullName;
        if (request.PhoneNumber != null) user.PhoneNumber = request.PhoneNumber;
        if (!string.IsNullOrWhiteSpace(request.Role)) user.Role = request.Role;
        if (request.MaxUserLimit.HasValue) user.MaxUserLimit = request.MaxUserLimit.Value;
        if (request.IsActive.HasValue)
        {
            user.IsActive = request.IsActive.Value;
            if (!user.IsActive)
            {
                try
                {
                    await _hub.Clients.Group(LocationHub.UserGroup(id)).SendAsync("ForceLogout", "আপনার একাউন্ট নিষ্ক্রিয় করা হয়েছে।");
                }
                catch { }
            }
        }
        if (request.OfficeLocationId.HasValue) user.OfficeLocationId = request.OfficeLocationId;

        // Update assigned office locations for Admin
        if (request.AssignedOfficeLocationIds != null)
        {
            var existingAssignments = await _db.AdminOfficeLocations
                .Where(a => a.AdminUserId == id)
                .ToListAsync();
            _db.AdminOfficeLocations.RemoveRange(existingAssignments);

            foreach (var officeId in request.AssignedOfficeLocationIds.Distinct())
            {
                if (await _db.OfficeLocations.AnyAsync(o => o.OfficeLocationId == officeId))
                {
                    _db.AdminOfficeLocations.Add(new AdminOfficeLocation
                    {
                        AdminUserId = id,
                        OfficeLocationId = officeId,
                        AssignedAtUtc = DateTime.UtcNow
                    });
                }
            }

            if (request.AssignedOfficeLocationIds.Count > 0 && !request.OfficeLocationId.HasValue)
            {
                user.OfficeLocationId = request.AssignedOfficeLocationIds.First();
            }
        }

        user.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();
        return Ok(await ToResponseAsync(user));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Disable(int id)
    {
        var user = await _db.Users.FindAsync(id);
        if (user is null) return NotFound(new { message = "User not found" });

        user.IsActive = false;
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();

        try
        {
            await _hub.Clients.Group(LocationHub.UserGroup(id)).SendAsync("ForceLogout", "আপনার একাউন্ট নিষ্ক্রিয় করা হয়েছে।");
        }
        catch { }

        return Ok(await ToResponseAsync(user));
    }

    [HttpPut("{id:int}/active")]
    public async Task<IActionResult> SetActive(int id, SetActiveRequest request)
    {
        var user = await _db.Users.FindAsync(id);
        if (user is null) return NotFound(new { message = "User not found" });

        user.IsActive = request.IsActive; // false = disable
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();

        if (!request.IsActive)
        {
            try
            {
                await _hub.Clients.Group(LocationHub.UserGroup(id)).SendAsync("ForceLogout", "আপনার একাউন্ট নিষ্ক্রিয় করা হয়েছে।");
            }
            catch { }
        }

        return Ok(await ToResponseAsync(user));
    }

    [HttpPost("{id:int}/reset-password")]
    public async Task<IActionResult> ResetPassword(int id, ResetPasswordRequest request)
    {
        var user = await _db.Users.FindAsync(id);
        if (user is null) return NotFound(new { message = "User not found" });

        user.PasswordHash = _hasher.Hash(user, request.NewPassword);
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();

        try
        {
            await _hub.Clients.Group(LocationHub.UserGroup(id)).SendAsync("ForceLogout", "আপনার পাসওয়ার্ড রিসেট করা হয়েছে। অনুগ্রহ করে নতুন পাসওয়ার্ড দিয়ে আবার লগইন করুন।");
        }
        catch { }

        return Ok(new { message = "Password reset successfully." });
    }

    [HttpPost("{id:int}/force-logout")]
    public async Task<IActionResult> ForceLogout(int id)
    {
        var user = await _db.Users.FindAsync(id);
        if (user is null) return NotFound(new { message = "User not found" });

        // Real-time kick via SignalR
        try
        {
            await _hub.Clients.Group(LocationHub.UserGroup(id)).SendAsync("ForceLogout", "অ্যাডমিন কর্তৃক আপনার সেশন বন্ধ করা হয়েছে। পুনরায় লগইন করুন।");
        }
        catch { }

        // Also record notification
        try
        {
            var notif = new NotificationItem
            {
                UserId = id,
                TargetRole = "User",
                Title = "⚠️ Force Logout",
                Message = "Your session was terminated by an administrator.",
                Type = "Security",
                ReferenceId = id.ToString(),
                IsRead = false,
                CreatedAtUtc = DateTime.UtcNow
            };
            _db.Notifications.Add(notif);
            await _db.SaveChangesAsync();
        }
        catch { }

        return Ok(new { message = $"User {user.Username} has been forcefully logged out." });
    }

    [HttpPost("{id:int}/reset-device")]
    public async Task<IActionResult> ResetDevice(int id)
    {
        var user = await _db.Users.FindAsync(id);
        if (user is null) return NotFound(new { message = "User not found" });

        user.BoundDeviceId = null;
        user.DeviceModel = null;
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();

        return Ok(new { message = $"ইউজার '{user.Username}' এর ডিভাইস সফলভাবে রিসেট করা হয়েছে। এখন যেকোনো নতুন ডিভাইসে লগইন করা যাবে।" });
    }
}
