using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly IJwtTokenService _jwt;
    private readonly IPasswordHasherService _hasher;
    private readonly IConfiguration _config;
    private readonly ICrmService _crm;

    public AuthController(LiveTrackingDbContext db, IJwtTokenService jwt, IPasswordHasherService hasher, IConfiguration config, ICrmService crm)
    {
        _db = db;
        _jwt = jwt;
        _hasher = hasher;
        _config = config;
        _crm = crm;
    }

    [HttpPost("login")]
    public async Task<ActionResult<LoginResponse>> Login(LoginRequest request)
    {
        try
        {
            // Accept username OR phone number as the login identifier
            var user = await _db.Users
                .Include(u => u.Company)
                .FirstOrDefaultAsync(u =>
                    u.Username == request.Username ||
                    (u.PhoneNumber != null && u.PhoneNumber == request.Username));

            if (user is null || !user.IsActive)
                return Unauthorized(new { message = "Invalid username/mobile or password." });

            if (!_hasher.Verify(user, user.PasswordHash, request.Password))
                return Unauthorized(new { message = "Invalid username/mobile or password." });

            // Transparently upgrade legacy plaintext-stored passwords to a proper hash on successful login.
            if (user.PasswordHash == request.Password)
            {
                user.PasswordHash = _hasher.Hash(user, request.Password);
                await _db.SaveChangesAsync();
            }

            // Device Locking & Single-Device Enforcement for all roles (Admin, Manager, User)
            if (!string.IsNullOrWhiteSpace(request.DeviceId))
            {
                var reqDeviceId = request.DeviceId.Trim();
                var reqDeviceModel = request.DeviceModel?.Trim();

                if (string.IsNullOrWhiteSpace(user.BoundDeviceId))
                {
                    // First time login on a device -> Bind this device permanently to the user
                    user.BoundDeviceId = reqDeviceId;
                    user.DeviceModel = reqDeviceModel;
                    user.UpdatedAtUtc = DateTime.UtcNow;
                    await _db.SaveChangesAsync();
                }
                else if (!string.Equals(user.BoundDeviceId.Trim(), reqDeviceId, StringComparison.OrdinalIgnoreCase))
                {
                    var boundDeviceName = !string.IsNullOrWhiteSpace(user.DeviceModel) ? user.DeviceModel : "রেজিস্টার্ড ডিভাইসে";
                    return StatusCode(403, new
                    {
                        message = $"ডিভাইস অনুমোদিত নয়!\nআপনার একাউন্টটি ইতোমধ্যে '{boundDeviceName}' ডিভাইসে নিবন্ধিত আছে। একটির বেশি ডিভাইসে লগইন করা যাবে না। ডিভাইস পরিবর্তন করতে অ্যাডমিনের সাথে যোগাযোগ করুন।"
                    });
                }
            }

            var token = _jwt.GenerateToken(user);
            var expiryHours = double.Parse(_config.GetSection("Jwt")["ExpiryHours"] ?? "12");
            var expiresAt = DateTime.UtcNow.AddHours(expiryHours).ToString("o");

            string? officeLocationName = user.OfficeLocationId.HasValue
                ? await _db.OfficeLocations.Where(o => o.OfficeLocationId == user.OfficeLocationId.Value).Select(o => o.Name).FirstOrDefaultAsync()
                : null;

            List<AuthorizedOfficeDto>? authorizedOffices = null;
            if (user.Role is "Admin" or "Manager")
            {
                var scope = await _crm.GetOfficeScopeAsync(user.UserId, user.Role);
                if (!scope.IsUnrestricted && scope.OfficeIds.Count > 0)
                {
                    authorizedOffices = await _db.OfficeLocations
                        .Where(o => scope.OfficeIds.Contains(o.OfficeLocationId))
                        .Select(o => new AuthorizedOfficeDto(o.OfficeLocationId, o.Name))
                        .ToListAsync();
                }
            }

            var response = new LoginResponse(
                token,
                expiresAt,
                user.UserId,
                user.FullName,
                user.Username,
                user.Role,
                user.CompanyId,
                user.Company?.CompanyName,
                user.OfficeLocationId,
                officeLocationName,
                authorizedOffices
            );
            return Ok(response);
        }
        catch (Exception ex)
        {
            return StatusCode(500, new
            {
                message = "An internal server error occurred while processing login.",
                error = ex.Message,
                innerError = ex.InnerException?.Message
            });
        }
    }

    [HttpPost("seed-admin")]
    [Authorize(Roles = "Admin")]
    public async Task<IActionResult> SeedAdmin()
    {
        try
        {
            var user = await _db.Users.FirstOrDefaultAsync(u => u.Username == "admin");
            if (user == null)
            {
                user = new LiveTracking.Api.Models.User
                {
                    Username = "admin",
                    FullName = "System Administrator",
                    Role = "Admin",
                    IsActive = true,
                    CreatedAtUtc = DateTime.UtcNow
                };
                user.PasswordHash = _hasher.Hash(user, "Admin@123");
                _db.Users.Add(user);
                await _db.SaveChangesAsync();
                return Ok(new { message = "Admin user created successfully with password Admin@123." });
            }

            user.IsActive = true;
            user.PasswordHash = _hasher.Hash(user, "Admin@123");
            user.UpdatedAtUtc = DateTime.UtcNow;
            await _db.SaveChangesAsync();
            return Ok(new { message = "Admin user password reset successfully to Admin@123 and activated." });
        }
        catch (Exception ex)
        {
            return StatusCode(500, new { message = "Failed to seed admin", error = ex.Message });
        }
    }
}
