using System.Security.Claims;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class CustomersController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;

    public CustomersController(LiveTrackingDbContext db)
    {
        _db = db;
    }

    private int GetCurrentUserId()
    {
        var idClaim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                      ?? User.FindFirst("sub")?.Value;
        return int.TryParse(idClaim, out int id) ? id : 0;
    }

    private async Task<List<int>> GetAccessibleEmployeeUserIdsAsync(int adminId)
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

            if (singleOfficeId.HasValue) assignedIds.Add(singleOfficeId.Value);
        }

        var admin = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == adminId);
        int? companyId = admin?.CompanyId;

        var query = _db.Users.Where(u => u.Role != "Admin");

        if (companyId.HasValue && companyId.Value > 0)
        {
            query = query.Where(u => u.CompanyId == companyId.Value);
        }

        if (assignedIds.Count > 0)
        {
            query = query.Where(u => u.OfficeLocationId.HasValue && assignedIds.Contains(u.OfficeLocationId.Value));
        }

        var list = await query.Select(u => u.UserId).ToListAsync();
        list.Add(adminId);
        return list;
    }

    [HttpGet]
    public async Task<ActionResult<List<CustomerResponse>>> GetCustomers(
        [FromQuery] string? search = null,
        [FromQuery] bool activeOnly = true,
        [FromQuery] int? targetUserId = null)
    {
        var currentUserId = GetCurrentUserId();
        if (currentUserId <= 0) return Unauthorized();

        var query = _db.Customers.AsNoTracking().AsQueryable();

        if (activeOnly)
        {
            query = query.Where(c => c.IsActive);
        }

        // Ownership filtering:
        // Admin sees customers of their fleet/users (or specified target user).
        // Regular user only sees customers created by themselves.
        if (User.IsInRole("Admin"))
        {
            var accessibleUserIds = await GetAccessibleEmployeeUserIdsAsync(currentUserId);
            if (targetUserId.HasValue && targetUserId.Value > 0)
            {
                query = query.Where(c => c.CreatedByUserId == targetUserId.Value && accessibleUserIds.Contains(c.CreatedByUserId.Value));
            }
            else
            {
                query = query.Where(c => c.CreatedByUserId.HasValue && accessibleUserIds.Contains(c.CreatedByUserId.Value));
            }
        }
        else
        {
            query = query.Where(c => c.CreatedByUserId == currentUserId);
        }

        if (!string.IsNullOrWhiteSpace(search))
        {
            search = search.Trim();
            query = query.Where(c => c.Name.Contains(search) || c.Mobile.Contains(search) || c.Address.Contains(search));
        }

        var customers = await query
            .OrderByDescending(c => c.CreatedDate)
            .ToListAsync();

        var customerIds = customers.Select(c => c.CustomerId).ToList();

        var lastVisits = await _db.CustomerVisits.AsNoTracking()
            .Where(v => customerIds.Contains(v.CustomerId))
            .GroupBy(v => v.CustomerId)
            .Select(g => new { CustomerId = g.Key, LastVisit = g.Max(x => x.VisitDate) })
            .ToDictionaryAsync(x => x.CustomerId, x => x.LastVisit);

        var nextFollowUps = await _db.CustomerVisits.AsNoTracking()
            .Where(v => customerIds.Contains(v.CustomerId) && v.NextFollowUpDate.HasValue && !v.IsFollowUpCompleted)
            .GroupBy(v => v.CustomerId)
            .Select(g => new { CustomerId = g.Key, NextFollowUp = g.Min(x => x.NextFollowUpDate) })
            .ToDictionaryAsync(x => x.CustomerId, x => x.NextFollowUp);

        // Fetch user names for createdBy
        var creatorIds = customers.Where(c => c.CreatedByUserId.HasValue).Select(c => c.CreatedByUserId!.Value).Distinct().ToList();
        var creatorNames = await _db.Users
            .Where(u => creatorIds.Contains(u.UserId))
            .ToDictionaryAsync(u => u.UserId, u => u.FullName.Length > 0 ? u.FullName : u.Username);

        var result = customers.Select(c => new CustomerResponse
        {
            CustomerId = c.CustomerId,
            Name = c.Name,
            Mobile = c.Mobile,
            Address = c.Address,
            Latitude = c.Latitude,
            Longitude = c.Longitude,
            Remarks = c.Remarks,
            CreatedDate = c.CreatedDate,
            IsActive = c.IsActive,
            CreatedByUserId = c.CreatedByUserId,
            CreatedByUserName = c.CreatedByUserId.HasValue && creatorNames.TryGetValue(c.CreatedByUserId.Value, out var creator) ? creator : null,
            LastVisitDate = lastVisits.TryGetValue(c.CustomerId, out var lv) ? lv : null,
            NextFollowUpDate = nextFollowUps.TryGetValue(c.CustomerId, out var nf) ? nf : null
        }).ToList();

        return Ok(result);
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<CustomerResponse>> GetCustomer(int id)
    {
        var currentUserId = GetCurrentUserId();
        if (currentUserId <= 0) return Unauthorized();

        var query = _db.Customers.AsNoTracking().Where(c => c.CustomerId == id);

        if (User.IsInRole("Admin"))
        {
            var subordinateUserIds = await _db.Users
                .Where(u => u.CreatedByAdminId == currentUserId || u.UserId == currentUserId)
                .Select(u => u.UserId)
                .ToListAsync();

            query = query.Where(c => c.CreatedByUserId.HasValue && subordinateUserIds.Contains(c.CreatedByUserId.Value));
        }
        else
        {
            query = query.Where(c => c.CreatedByUserId == currentUserId);
        }

        var customer = await query.FirstOrDefaultAsync();
        if (customer == null) return NotFound(new { message = "Customer not found." });

        var visits = await _db.CustomerVisits.AsNoTracking()
            .Where(v => v.CustomerId == id)
            .OrderByDescending(v => v.VisitDate)
            .ToListAsync();

        var lastVisit = visits.FirstOrDefault()?.VisitDate;
        var nextFollowUp = visits.Where(v => v.NextFollowUpDate.HasValue && !v.IsFollowUpCompleted)
            .OrderBy(v => v.NextFollowUpDate)
            .Select(v => v.NextFollowUpDate)
            .FirstOrDefault();

        string? creatorName = null;
        if (customer.CreatedByUserId.HasValue)
        {
            creatorName = await _db.Users
                .Where(u => u.UserId == customer.CreatedByUserId.Value)
                .Select(u => u.FullName.Length > 0 ? u.FullName : u.Username)
                .FirstOrDefaultAsync();
        }

        return Ok(new CustomerResponse
        {
            CustomerId = customer.CustomerId,
            Name = customer.Name,
            Mobile = customer.Mobile,
            Address = customer.Address,
            Latitude = customer.Latitude,
            Longitude = customer.Longitude,
            Remarks = customer.Remarks,
            CreatedDate = customer.CreatedDate,
            IsActive = customer.IsActive,
            CreatedByUserId = customer.CreatedByUserId,
            CreatedByUserName = creatorName,
            LastVisitDate = lastVisit,
            NextFollowUpDate = nextFollowUp
        });
    }

    [HttpPost]
    public async Task<ActionResult<CustomerResponse>> CreateCustomer([FromBody] CreateCustomerRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
            return BadRequest(new { message = "Customer name is required." });
        if (string.IsNullOrWhiteSpace(request.Mobile))
            return BadRequest(new { message = "Mobile number is required." });
        if (string.IsNullOrWhiteSpace(request.Address))
            return BadRequest(new { message = "Address is required." });

        var cleanMobile = request.Mobile.Trim();
        if (await _db.Customers.AnyAsync(c => c.Mobile == cleanMobile))
        {
            return BadRequest(new { message = $"এই মোবাইল নম্বরটি ({cleanMobile}) দিয়ে ইতিমধ্যে একজন কাস্টমার এন্ট্রি করা আছে।" });
        }

        int userId = GetCurrentUserId();
        if (userId <= 0) return Unauthorized();

        var customer = new Customer
        {
            Name = request.Name.Trim(),
            Mobile = request.Mobile.Trim(),
            Address = request.Address.Trim(),
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            Remarks = request.Remarks?.Trim(),
            CreatedDate = DateTime.UtcNow,
            IsActive = true,
            CreatedByUserId = userId
        };

        _db.Customers.Add(customer);
        await _db.SaveChangesAsync();

        var creatorName = await _db.Users
            .Where(u => u.UserId == userId)
            .Select(u => u.FullName.Length > 0 ? u.FullName : u.Username)
            .FirstOrDefaultAsync();

        return CreatedAtAction(nameof(GetCustomer), new { id = customer.CustomerId }, new CustomerResponse
        {
            CustomerId = customer.CustomerId,
            Name = customer.Name,
            Mobile = customer.Mobile,
            Address = customer.Address,
            Latitude = customer.Latitude,
            Longitude = customer.Longitude,
            Remarks = customer.Remarks,
            CreatedDate = customer.CreatedDate,
            IsActive = customer.IsActive,
            CreatedByUserId = customer.CreatedByUserId,
            CreatedByUserName = creatorName
        });
    }
}
