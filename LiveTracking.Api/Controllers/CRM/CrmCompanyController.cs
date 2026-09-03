using System.Data;
using Microsoft.Data.SqlClient;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers.CRM;

[ApiController]
[Route("api/crm/company")]
[Authorize]
public class CrmCompanyController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly string _connectionString;

    public CrmCompanyController(LiveTrackingDbContext db)
    {
        _db = db;
        _connectionString = _db.Database.GetConnectionString() 
            ?? throw new InvalidOperationException("Connection string not found.");
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value
                    ?? User.FindFirst("sub")?.Value;
        return int.TryParse(claim, out var id) ? id : 0;
    }

    private async Task<int> GetCurrentCompanyIdAsync()
    {
        var claim = User.FindFirst("companyId")?.Value;
        if (int.TryParse(claim, out var cid) && cid > 0)
        {
            return cid;
        }

        int userId = GetCurrentUserId();
        if (userId > 0)
        {
            var userCid = await _db.Users
                .Where(u => u.UserId == userId)
                .Select(u => u.CompanyId)
                .FirstOrDefaultAsync();

            if (userCid.HasValue && userCid.Value > 0)
            {
                return userCid.Value;
            }
        }

        return 0;
    }

    [HttpGet("branding")]
    public async Task<ActionResult<ApiResponse<CompanyBrandingDto>>> GetCompanyBranding()
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0)
        {
            return Forbid();
        }

        using var conn = new SqlConnection(_connectionString);
        await conn.OpenAsync();

        using var cmd = new SqlCommand("dbo.sp_Crm_Company_GetBranding", conn);
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.AddWithValue("@CompanyId", companyId);

        using var reader = await cmd.ExecuteReaderAsync();
        if (await reader.ReadAsync())
        {
            var logoUrl = reader["LogoUrl"]?.ToString();
            
            // If relative path and request context available, construct full URL if helpful
            if (!string.IsNullOrWhiteSpace(logoUrl) && logoUrl.StartsWith("/"))
            {
                logoUrl = $"{Request.Scheme}://{Request.Host}{logoUrl}";
            }

            var dto = new CompanyBrandingDto(
                CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
                CompanyName: reader.GetString(reader.GetOrdinal("CompanyName")),
                CompanyCode: reader.GetString(reader.GetOrdinal("CompanyCode")),
                LogoUrl: logoUrl,
                ContactPhone: reader["ContactPhone"]?.ToString(),
                ContactEmail: reader["ContactEmail"]?.ToString(),
                ContactPerson: reader["ContactPerson"]?.ToString()
            );

            return Ok(new ApiResponse<CompanyBrandingDto>(true, "Company branding retrieved successfully.", dto));
        }

        return NotFound(new ApiResponse<CompanyBrandingDto>(false, "Company not found or inactive."));
    }

    [HttpPut("branding")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<ApiResponse<CompanyBrandingDto>>> UpdateCompanyBranding([FromBody] UpdateCompanyBrandingRequest request)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0)
        {
            return Forbid();
        }

        using var conn = new SqlConnection(_connectionString);
        await conn.OpenAsync();

        using var cmd = new SqlCommand("dbo.sp_Crm_Company_UpdateBranding", conn);
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.AddWithValue("@CompanyId", companyId);
        cmd.Parameters.AddWithValue("@CompanyName", (object?)request.CompanyName ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@LogoUrl", (object?)request.LogoUrl ?? DBNull.Value);

        try
        {
            using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
            {
                var logoUrl = reader["LogoUrl"]?.ToString();
                if (!string.IsNullOrWhiteSpace(logoUrl) && logoUrl.StartsWith("/"))
                {
                    logoUrl = $"{Request.Scheme}://{Request.Host}{logoUrl}";
                }

                var dto = new CompanyBrandingDto(
                    CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
                    CompanyName: reader.GetString(reader.GetOrdinal("CompanyName")),
                    CompanyCode: reader.GetString(reader.GetOrdinal("CompanyCode")),
                    LogoUrl: logoUrl,
                    ContactPhone: reader["ContactPhone"]?.ToString(),
                    ContactEmail: reader["ContactEmail"]?.ToString(),
                    ContactPerson: reader["ContactPerson"]?.ToString()
                );

                return Ok(new ApiResponse<CompanyBrandingDto>(true, "Company branding updated successfully.", dto));
            }

            return NotFound(new ApiResponse<CompanyBrandingDto>(false, "Company not found or inactive."));
        }
        catch (SqlException ex)
        {
            return BadRequest(new ApiResponse<CompanyBrandingDto>(false, ex.Message));
        }
    }
}
