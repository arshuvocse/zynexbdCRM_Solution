using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/app-version")]
public class AppVersionController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;

    public AppVersionController(LiveTrackingDbContext db)
    {
        _db = db;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value
                    ?? User.FindFirst("sub")?.Value;
        return int.TryParse(claim, out int id) ? id : 0;
    }

    /// <summary>
    /// Check if the client app needs an update.
    /// Supports company-specific versioning with fallback to global releases.
    /// </summary>
    [HttpGet("check")]
    [AllowAnonymous]
    public async Task<ActionResult<AppVersionCheckResponseDto>> CheckVersion(
        [FromQuery] int versionCode,
        [FromQuery] string platform = "Android",
        [FromQuery] int? companyId = null)
    {
        // 1. Try to find the latest version specifically for this company
        AppVersion? latest = null;
        if (companyId.HasValue)
        {
            latest = await _db.AppVersions
                .Include(v => v.Company)
                .Where(v => v.Platform.ToLower() == platform.ToLower() && v.IsActive && v.CompanyId == companyId.Value)
                .OrderByDescending(v => v.VersionCode)
                .FirstOrDefaultAsync();
        }

        // 2. Fallback to global release (CompanyId == null) if no company-specific version found
        if (latest == null)
        {
            latest = await _db.AppVersions
                .Include(v => v.Company)
                .Where(v => v.Platform.ToLower() == platform.ToLower() && v.IsActive && v.CompanyId == null)
                .OrderByDescending(v => v.VersionCode)
                .FirstOrDefaultAsync();
        }

        if (latest == null)
        {
            return Ok(new AppVersionCheckResponseDto
            {
                HasUpdate = false,
                IsForceUpdate = false,
                CurrentVersionCode = versionCode,
                LatestVersionCode = versionCode,
                LatestVersionName = "1.0",
                Title = string.Empty,
                ReleaseNotes = string.Empty,
                DownloadUrl = string.Empty,
                CompanyId = companyId,
                CompanyName = null
            });
        }

        bool hasUpdate = latest.VersionCode > versionCode;
        bool isForce = hasUpdate && (latest.IsForceUpdate || versionCode < latest.MinVersionCode);

        return Ok(new AppVersionCheckResponseDto
        {
            HasUpdate = hasUpdate,
            IsForceUpdate = isForce,
            CurrentVersionCode = versionCode,
            LatestVersionCode = latest.VersionCode,
            LatestVersionName = latest.VersionName,
            Title = latest.Title,
            ReleaseNotes = latest.ReleaseNotes,
            DownloadUrl = latest.DownloadUrl,
            CompanyId = latest.CompanyId,
            CompanyName = latest.Company?.CompanyName
        });
    }

    /// <summary>
    /// Get the latest active app version details.
    /// </summary>
    [HttpGet("latest")]
    [AllowAnonymous]
    public async Task<ActionResult<AppVersionDetailsDto>> GetLatestVersion(
        [FromQuery] string platform = "Android",
        [FromQuery] int? companyId = null)
    {
        AppVersion? latest = null;
        if (companyId.HasValue)
        {
            latest = await _db.AppVersions
                .Include(v => v.Company)
                .Where(v => v.Platform.ToLower() == platform.ToLower() && v.IsActive && v.CompanyId == companyId.Value)
                .OrderByDescending(v => v.VersionCode)
                .FirstOrDefaultAsync();
        }

        if (latest == null)
        {
            latest = await _db.AppVersions
                .Include(v => v.Company)
                .Where(v => v.Platform.ToLower() == platform.ToLower() && v.IsActive && v.CompanyId == null)
                .OrderByDescending(v => v.VersionCode)
                .FirstOrDefaultAsync();
        }

        if (latest == null) return NotFound(new { message = "No active version found." });

        return Ok(new AppVersionDetailsDto
        {
            AppVersionId = latest.AppVersionId,
            Platform = latest.Platform,
            VersionCode = latest.VersionCode,
            VersionName = latest.VersionName,
            MinVersionCode = latest.MinVersionCode,
            IsForceUpdate = latest.IsForceUpdate,
            DownloadUrl = latest.DownloadUrl,
            Title = latest.Title,
            ReleaseNotes = latest.ReleaseNotes,
            IsActive = latest.IsActive,
            CompanyId = latest.CompanyId,
            CompanyName = latest.Company?.CompanyName,
            CreatedAtUtc = latest.CreatedAtUtc
        });
    }

    /// <summary>
    /// List all app version releases (Admin only).
    /// </summary>
    [HttpGet("history")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<List<AppVersionDetailsDto>>> GetHistory(
        [FromQuery] string? platform = null,
        [FromQuery] int? companyId = null)
    {
        var currentAdminId = GetCurrentUserId();
        var currentAdmin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? callerCompanyId = companyId ?? currentAdmin?.CompanyId;

        var query = _db.AppVersions.Include(v => v.Company).AsQueryable();

        if (callerCompanyId.HasValue)
        {
            query = query.Where(v => v.CompanyId == callerCompanyId.Value || v.CompanyId == null);
        }

        if (!string.IsNullOrWhiteSpace(platform))
        {
            query = query.Where(v => v.Platform.ToLower() == platform.ToLower());
        }

        var list = await query
            .OrderByDescending(v => v.VersionCode)
            .Select(v => new AppVersionDetailsDto
            {
                AppVersionId = v.AppVersionId,
                Platform = v.Platform,
                VersionCode = v.VersionCode,
                VersionName = v.VersionName,
                MinVersionCode = v.MinVersionCode,
                IsForceUpdate = v.IsForceUpdate,
                DownloadUrl = v.DownloadUrl,
                Title = v.Title,
                ReleaseNotes = v.ReleaseNotes,
                IsActive = v.IsActive,
                CompanyId = v.CompanyId,
                CompanyName = v.Company != null ? v.Company.CompanyName : "Global",
                CreatedAtUtc = v.CreatedAtUtc
            })
            .ToListAsync();

        return Ok(list);
    }

    /// <summary>
    /// Publish a new app version for a specific company or globally (Admin only).
    /// </summary>
    [HttpPost]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<AppVersionDetailsDto>> CreateVersion([FromBody] CreateAppVersionDto dto)
    {
        if (dto.VersionCode <= 0) return BadRequest(new { message = "VersionCode must be positive." });
        if (string.IsNullOrWhiteSpace(dto.VersionName)) return BadRequest(new { message = "VersionName is required." });
        if (string.IsNullOrWhiteSpace(dto.DownloadUrl)) return BadRequest(new { message = "DownloadUrl is required." });

        var currentAdminId = GetCurrentUserId();
        var currentAdmin = currentAdminId > 0 ? await _db.Users.FindAsync(currentAdminId) : null;
        int? targetCompanyId = dto.CompanyId ?? currentAdmin?.CompanyId;

        var entity = new AppVersion
        {
            Platform = string.IsNullOrWhiteSpace(dto.Platform) ? "Android" : dto.Platform.Trim(),
            VersionCode = dto.VersionCode,
            VersionName = dto.VersionName.Trim(),
            MinVersionCode = dto.MinVersionCode > 0 ? dto.MinVersionCode : 1,
            IsForceUpdate = dto.IsForceUpdate,
            DownloadUrl = dto.DownloadUrl.Trim(),
            Title = string.IsNullOrWhiteSpace(dto.Title) ? "New Update Available" : dto.Title.Trim(),
            ReleaseNotes = dto.ReleaseNotes?.Trim() ?? string.Empty,
            CompanyId = targetCompanyId,
            IsActive = dto.IsActive,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.AppVersions.Add(entity);
        await _db.SaveChangesAsync();

        return CreatedAtAction(nameof(GetLatestVersion), new { platform = entity.Platform, companyId = entity.CompanyId }, new AppVersionDetailsDto
        {
            AppVersionId = entity.AppVersionId,
            Platform = entity.Platform,
            VersionCode = entity.VersionCode,
            VersionName = entity.VersionName,
            MinVersionCode = entity.MinVersionCode,
            IsForceUpdate = entity.IsForceUpdate,
            DownloadUrl = entity.DownloadUrl,
            Title = entity.Title,
            ReleaseNotes = entity.ReleaseNotes,
            IsActive = entity.IsActive,
            CompanyId = entity.CompanyId,
            CreatedAtUtc = entity.CreatedAtUtc
        });
    }
}
