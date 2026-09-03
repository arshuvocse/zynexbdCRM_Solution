using System.Data;
using Microsoft.Data.SqlClient;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers.CRM;

[ApiController]
[Route("api/crm/products-services")]
[Authorize]
public class CrmProductServiceController : ControllerBase
{
    private readonly LiveTrackingDbContext _db;
    private readonly string _connectionString;

    public CrmProductServiceController(LiveTrackingDbContext db)
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

    private string GetCurrentUserRole()
    {
        return User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
               ?? User.FindFirst("role")?.Value
               ?? "User";
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

    [HttpGet]
    public async Task<ActionResult<ApiResponse<object>>> GetProductServices(
        [FromQuery] string? search = null,
        [FromQuery] bool? activeOnly = null,
        [FromQuery] int pageNumber = 1,
        [FromQuery] int pageSize = 50)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0)
        {
            return Forbid();
        }

        string role = GetCurrentUserRole();
        // Regular Users can ONLY see Active products
        bool effectiveActiveOnly = true;
        if (role.Equals("Admin", StringComparison.OrdinalIgnoreCase) ||
            role.Equals("Manager", StringComparison.OrdinalIgnoreCase))
        {
            effectiveActiveOnly = activeOnly ?? false;
        }

        using var conn = new SqlConnection(_connectionString);
        await conn.OpenAsync();

        using var cmd = new SqlCommand("dbo.sp_Crm_ProductService_GetList", conn);
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.AddWithValue("@CompanyId", companyId);
        cmd.Parameters.AddWithValue("@Search", (object?)search?.Trim() ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@ActiveOnly", effectiveActiveOnly);
        cmd.Parameters.AddWithValue("@PageNumber", pageNumber);
        cmd.Parameters.AddWithValue("@PageSize", pageSize);

        var list = new List<CrmProductServiceDto>();
        int totalCount = 0;

        using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            if (totalCount == 0 && !reader.IsDBNull(reader.GetOrdinal("TotalCount")))
            {
                totalCount = reader.GetInt32(reader.GetOrdinal("TotalCount"));
            }

            list.Add(new CrmProductServiceDto(
                ProductServiceId: reader.GetInt32(reader.GetOrdinal("ProductServiceId")),
                CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
                Name: reader.GetString(reader.GetOrdinal("Name")),
                Code: reader.IsDBNull(reader.GetOrdinal("Code")) ? null : reader.GetString(reader.GetOrdinal("Code")),
                Description: reader.IsDBNull(reader.GetOrdinal("Description")) ? null : reader.GetString(reader.GetOrdinal("Description")),
                Price: reader.IsDBNull(reader.GetOrdinal("Price")) ? null : reader.GetDecimal(reader.GetOrdinal("Price")),
                IsActive: reader.GetBoolean(reader.GetOrdinal("IsActive"))
            ));
        }

        var result = new
        {
            Items = list,
            TotalCount = totalCount > 0 ? totalCount : list.Count,
            PageNumber = pageNumber,
            PageSize = pageSize
        };

        return Ok(new ApiResponse<object>(true, "Products and services retrieved successfully.", result));
    }

    [HttpPost]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<ActionResult<ApiResponse<CrmProductServiceDto>>> CreateProductService([FromBody] CreateCrmProductServiceRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
        {
            return BadRequest(new ApiResponse<CrmProductServiceDto>(false, "Product/Service name is required."));
        }

        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0)
        {
            return Forbid();
        }

        using var conn = new SqlConnection(_connectionString);
        await conn.OpenAsync();

        using var cmd = new SqlCommand("dbo.sp_Crm_ProductService_Save", conn);
        cmd.CommandType = CommandType.StoredProcedure;
        
        var idParam = new SqlParameter("@ProductServiceId", SqlDbType.Int)
        {
            Direction = ParameterDirection.InputOutput,
            Value = DBNull.Value
        };
        cmd.Parameters.Add(idParam);
        cmd.Parameters.AddWithValue("@CompanyId", companyId);
        cmd.Parameters.AddWithValue("@Name", request.Name.Trim());
        cmd.Parameters.AddWithValue("@Code", (object?)request.Code?.Trim() ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@Description", (object?)request.Description?.Trim() ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@Price", (object?)request.Price ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@IsActive", true);

        try
        {
            using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
            {
                var dto = new CrmProductServiceDto(
                    ProductServiceId: reader.GetInt32(reader.GetOrdinal("ProductServiceId")),
                    CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
                    Name: reader.GetString(reader.GetOrdinal("Name")),
                    Code: reader.IsDBNull(reader.GetOrdinal("Code")) ? null : reader.GetString(reader.GetOrdinal("Code")),
                    Description: reader.IsDBNull(reader.GetOrdinal("Description")) ? null : reader.GetString(reader.GetOrdinal("Description")),
                    Price: reader.IsDBNull(reader.GetOrdinal("Price")) ? null : reader.GetDecimal(reader.GetOrdinal("Price")),
                    IsActive: reader.GetBoolean(reader.GetOrdinal("IsActive"))
                );

                return StatusCode(201, new ApiResponse<CrmProductServiceDto>(true, "Product/Service created successfully.", dto));
            }

            return BadRequest(new ApiResponse<CrmProductServiceDto>(false, "Failed to create product or service."));
        }
        catch (SqlException ex)
        {
            return BadRequest(new ApiResponse<CrmProductServiceDto>(false, ex.Message));
        }
    }

    [HttpPut("{id:int}")]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<ActionResult<ApiResponse<CrmProductServiceDto>>> UpdateProductService(int id, [FromBody] UpdateCrmProductServiceRequest request)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0)
        {
            return Forbid();
        }

        using var conn = new SqlConnection(_connectionString);
        await conn.OpenAsync();

        using var cmd = new SqlCommand("dbo.sp_Crm_ProductService_Save", conn);
        cmd.CommandType = CommandType.StoredProcedure;
        
        var idParam = new SqlParameter("@ProductServiceId", SqlDbType.Int)
        {
            Direction = ParameterDirection.InputOutput,
            Value = id
        };
        cmd.Parameters.Add(idParam);
        cmd.Parameters.AddWithValue("@CompanyId", companyId);
        cmd.Parameters.AddWithValue("@Name", (object?)request.Name?.Trim() ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@Code", (object?)request.Code?.Trim() ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@Description", (object?)request.Description?.Trim() ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@Price", (object?)request.Price ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@IsActive", request.IsActive ?? true);

        try
        {
            using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
            {
                var dto = new CrmProductServiceDto(
                    ProductServiceId: reader.GetInt32(reader.GetOrdinal("ProductServiceId")),
                    CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
                    Name: reader.GetString(reader.GetOrdinal("Name")),
                    Code: reader.IsDBNull(reader.GetOrdinal("Code")) ? null : reader.GetString(reader.GetOrdinal("Code")),
                    Description: reader.IsDBNull(reader.GetOrdinal("Description")) ? null : reader.GetString(reader.GetOrdinal("Description")),
                    Price: reader.IsDBNull(reader.GetOrdinal("Price")) ? null : reader.GetDecimal(reader.GetOrdinal("Price")),
                    IsActive: reader.GetBoolean(reader.GetOrdinal("IsActive"))
                );

                return Ok(new ApiResponse<CrmProductServiceDto>(true, "Product/Service updated successfully.", dto));
            }

            return NotFound(new ApiResponse<CrmProductServiceDto>(false, "Product/Service not found in organization."));
        }
        catch (SqlException ex)
        {
            return BadRequest(new ApiResponse<CrmProductServiceDto>(false, ex.Message));
        }
    }

    [HttpPatch("{id:int}/status")]
    [HttpPost("{id:int}/toggle-status")]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<ActionResult<ApiResponse<CrmProductServiceDto>>> ToggleStatus(int id, [FromBody] CrmProductServiceStatusRequest request)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0)
        {
            return Forbid();
        }

        using var conn = new SqlConnection(_connectionString);
        await conn.OpenAsync();

        using var cmd = new SqlCommand("dbo.sp_Crm_ProductService_ToggleStatus", conn);
        cmd.CommandType = CommandType.StoredProcedure;
        cmd.Parameters.AddWithValue("@CompanyId", companyId);
        cmd.Parameters.AddWithValue("@ProductServiceId", id);
        cmd.Parameters.AddWithValue("@IsActive", request.IsActive);

        try
        {
            using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
            {
                var dto = new CrmProductServiceDto(
                    ProductServiceId: reader.GetInt32(reader.GetOrdinal("ProductServiceId")),
                    CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
                    Name: reader.GetString(reader.GetOrdinal("Name")),
                    Code: reader.IsDBNull(reader.GetOrdinal("Code")) ? null : reader.GetString(reader.GetOrdinal("Code")),
                    Description: reader.IsDBNull(reader.GetOrdinal("Description")) ? null : reader.GetString(reader.GetOrdinal("Description")),
                    Price: reader.IsDBNull(reader.GetOrdinal("Price")) ? null : reader.GetDecimal(reader.GetOrdinal("Price")),
                    IsActive: reader.GetBoolean(reader.GetOrdinal("IsActive"))
                );

                string statusText = request.IsActive ? "activated" : "deactivated";
                return Ok(new ApiResponse<CrmProductServiceDto>(true, $"Product/Service {statusText} successfully.", dto));
            }

            return NotFound(new ApiResponse<CrmProductServiceDto>(false, "Product/Service not found in organization."));
        }
        catch (SqlException ex)
        {
            return BadRequest(new ApiResponse<CrmProductServiceDto>(false, ex.Message));
        }
    }

    [HttpDelete("{id:int}")]
    [Authorize(Roles = "Admin,Manager")]
    public async Task<IActionResult> DeleteProductService(int id)
    {
        // Soft deactivation as required by business rules
        var result = await ToggleStatus(id, new CrmProductServiceStatusRequest(false));
        if (result.Result is NotFoundObjectResult)
        {
            return NotFound(new ApiResponse<object>(false, "Product/Service not found."));
        }
        return NoContent();
    }
}
