using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/companies")]
public class CompaniesController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;

    public CompaniesController(LiveTrackingDbContext db)
    {
        _db = db;
    }

    private int GetCurrentUserId()
    {
        var claim = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value
                    ?? User.FindFirst("sub")?.Value;
        return int.TryParse(claim, out var id) ? id : 0;
    }

    [HttpGet]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<List<CompanyDto>>> GetCompanies()
    {
        var companies = await _db.Companies
            .Include(c => c.Users)
            .Include(c => c.OfficeLocations)
            .OrderByDescending(c => c.CompanyId)
            .ToListAsync();

        var result = companies.Select(c =>
        {
            var status = "Active";
            if (c.PaymentDueDate.HasValue)
            {
                if (c.PaymentDueDate.Value < DateTime.UtcNow) status = "Expired";
                else if ((c.PaymentDueDate.Value - DateTime.UtcNow).TotalDays <= 5) status = "DueSoon";
            }

            return new CompanyDto(
                c.CompanyId,
                c.CompanyName,
                c.CompanyCode,
                c.ContactPerson,
                c.ContactPhone,
                c.ContactEmail,
                c.MaxUserLimit,
                c.PaymentDueDate?.ToString("o"),
                c.IsActive,
                c.OfficeLocations.Count(o => o.IsActive),
                c.Users.Count(u => u.Role == "Admin" && u.IsActive),
                c.Users.Count(u => u.Role == "User" && u.IsActive),
                status
            );
        }).ToList();

        return Ok(result);
    }

    [HttpGet("{id}")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<CompanyDto>> GetCompanyById(int id)
    {
        var c = await _db.Companies
            .Include(c => c.Users)
            .Include(c => c.OfficeLocations)
            .FirstOrDefaultAsync(c => c.CompanyId == id);

        if (c == null) return NotFound(new { message = "Company not found." });

        var status = "Active";
        if (c.PaymentDueDate.HasValue)
        {
            if (c.PaymentDueDate.Value < DateTime.UtcNow) status = "Expired";
            else if ((c.PaymentDueDate.Value - DateTime.UtcNow).TotalDays <= 5) status = "DueSoon";
        }

        return Ok(new CompanyDto(
            c.CompanyId,
            c.CompanyName,
            c.CompanyCode,
            c.ContactPerson,
            c.ContactPhone,
            c.ContactEmail,
            c.MaxUserLimit,
            c.PaymentDueDate?.ToString("o"),
            c.IsActive,
            c.OfficeLocations.Count(o => o.IsActive),
            c.Users.Count(u => u.Role == "Admin" && u.IsActive),
            c.Users.Count(u => u.Role == "User" && u.IsActive),
            status
        ));
    }

    [HttpGet("{id}/stats")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<CompanyStatsDto>> GetCompanyStats(int id)
    {
        var c = await _db.Companies
            .Include(c => c.Users)
            .Include(c => c.OfficeLocations)
            .FirstOrDefaultAsync(c => c.CompanyId == id);

        if (c == null) return NotFound(new { message = "Company not found." });

        int activeOfficers = c.Users.Count(u => u.Role == "User" && u.IsActive);
        int totalAdmins = c.Users.Count(u => u.Role == "Admin" && u.IsActive);
        int totalOffices = c.OfficeLocations.Count(o => o.IsActive);

        var status = "Active";
        if (c.PaymentDueDate.HasValue)
        {
            if (c.PaymentDueDate.Value < DateTime.UtcNow) status = "Expired";
            else if ((c.PaymentDueDate.Value - DateTime.UtcNow).TotalDays <= 5) status = "DueSoon";
        }

        return Ok(new CompanyStatsDto(
            c.CompanyId,
            c.CompanyName,
            c.CompanyCode,
            totalOffices,
            totalAdmins,
            activeOfficers,
            c.MaxUserLimit,
            activeOfficers >= c.MaxUserLimit,
            c.PaymentDueDate?.ToString("o"),
            status
        ));
    }

    [HttpPost]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<CompanyDto>> CreateCompany([FromBody] CreateCompanyRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.CompanyName))
            return BadRequest(new { message = "Company name is required." });

        var code = string.IsNullOrWhiteSpace(request.CompanyCode)
            ? request.CompanyName.Trim().ToUpper().Replace(" ", "_")
            : request.CompanyCode.Trim().ToUpper();

        if (await _db.Companies.AnyAsync(c => c.CompanyCode == code))
            return Conflict(new { message = $"Company code '{code}' already exists." });

        DateTime? dueDate = null;
        if (!string.IsNullOrWhiteSpace(request.PaymentDueDate) && DateTime.TryParse(request.PaymentDueDate, out var parsedDate))
        {
            dueDate = parsedDate.ToUniversalTime();
        }

        var company = new Company
        {
            CompanyName = request.CompanyName.Trim(),
            CompanyCode = code,
            ContactPerson = request.ContactPerson?.Trim(),
            ContactPhone = request.ContactPhone?.Trim(),
            ContactEmail = request.ContactEmail?.Trim(),
            MaxUserLimit = request.MaxUserLimit is > 0 ? request.MaxUserLimit.Value : 10,
            PaymentDueDate = dueDate,
            IsActive = true,
            CreatedAtUtc = DateTime.UtcNow
        };

        _db.Companies.Add(company);
        await _db.SaveChangesAsync();

        return CreatedAtAction(nameof(GetCompanyById), new { id = company.CompanyId }, new CompanyDto(
            company.CompanyId,
            company.CompanyName,
            company.CompanyCode,
            company.ContactPerson,
            company.ContactPhone,
            company.ContactEmail,
            company.MaxUserLimit,
            company.PaymentDueDate?.ToString("o"),
            company.IsActive,
            0, 0, 0,
            "Active"
        ));
    }

    [HttpPut("{id}")]
    [Authorize(Roles = "Admin")]
    public async Task<ActionResult<CompanyDto>> UpdateCompany(int id, [FromBody] UpdateCompanyRequest request)
    {
        var company = await _db.Companies
            .Include(c => c.Users)
            .Include(c => c.OfficeLocations)
            .FirstOrDefaultAsync(c => c.CompanyId == id);

        if (company == null) return NotFound(new { message = "Company not found." });

        if (!string.IsNullOrWhiteSpace(request.CompanyName))
            company.CompanyName = request.CompanyName.Trim();

        if (request.ContactPerson != null) company.ContactPerson = request.ContactPerson.Trim();
        if (request.ContactPhone != null) company.ContactPhone = request.ContactPhone.Trim();
        if (request.ContactEmail != null) company.ContactEmail = request.ContactEmail.Trim();

        if (request.MaxUserLimit is > 0)
            company.MaxUserLimit = request.MaxUserLimit.Value;

        if (request.PaymentDueDate != null)
        {
            if (DateTime.TryParse(request.PaymentDueDate, out var parsedDate))
                company.PaymentDueDate = parsedDate.ToUniversalTime();
            else if (string.IsNullOrWhiteSpace(request.PaymentDueDate))
                company.PaymentDueDate = null;
        }

        if (request.IsActive.HasValue)
            company.IsActive = request.IsActive.Value;

        company.UpdatedAtUtc = DateTime.UtcNow;
        await _db.SaveChangesAsync();

        var status = "Active";
        if (company.PaymentDueDate.HasValue)
        {
            if (company.PaymentDueDate.Value < DateTime.UtcNow) status = "Expired";
            else if ((company.PaymentDueDate.Value - DateTime.UtcNow).TotalDays <= 5) status = "DueSoon";
        }

        return Ok(new CompanyDto(
            company.CompanyId,
            company.CompanyName,
            company.CompanyCode,
            company.ContactPerson,
            company.ContactPhone,
            company.ContactEmail,
            company.MaxUserLimit,
            company.PaymentDueDate?.ToString("o"),
            company.IsActive,
            company.OfficeLocations.Count(o => o.IsActive),
            company.Users.Count(u => u.Role == "Admin" && u.IsActive),
            company.Users.Count(u => u.Role == "User" && u.IsActive),
            status
        ));
    }
}
