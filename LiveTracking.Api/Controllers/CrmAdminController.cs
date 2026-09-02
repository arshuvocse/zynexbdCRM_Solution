using System.Text;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using LiveTracking.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Controllers;

[ApiController]
[Route("api/crm/admin")]
[Authorize(Roles = "Admin")]
public class CrmAdminController : ControllerBase
{
    private readonly ICrmService _crm;
    private readonly LiveTrackingDbContext _db;

    public CrmAdminController(ICrmService crm, LiveTrackingDbContext db)
    {
        _crm = crm;
        _db = db;
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

    /// <summary>
    /// Returns the complete Company-wide Admin CRM Dashboard (11 Cards + 8 Dynamic Charts)
    /// </summary>
    [HttpGet("dashboard")]
    public async Task<ActionResult<AdminCrmDashboardResponse>> GetDashboard(
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? managerId = null,
        [FromQuery] int? userId = null,
        [FromQuery] int? productServiceId = null,
        [FromQuery] string? leadStatus = null,
        [FromQuery] int? leadSourceId = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var filter = new CrmDashboardFilterRequest(
            fromDate, toDate, officeLocationId, managerId, userId,
            productServiceId, leadStatus, leadSourceId
        );

        var result = await _crm.GetAdminDashboardAsync(companyId, filter);
        return Ok(result);
    }

    /// <summary>
    /// Executes any of the 15 Admin CRM Reports with Server-side Aggregation, Filtering & Pagination
    /// </summary>
    [HttpGet("reports")]
    public async Task<ActionResult<CrmReportResponse>> GetReport(
        [FromQuery] int reportType = 1,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? managerId = null,
        [FromQuery] int? userId = null,
        [FromQuery] int? productServiceId = null,
        [FromQuery] string? leadStatus = null,
        [FromQuery] int? leadSourceId = null,
        [FromQuery] string? search = null,
        [FromQuery] int pageNumber = 1,
        [FromQuery] int pageSize = 20)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        if (reportType < 1 || reportType > 15)
        {
            reportType = 1;
        }

        var request = new CrmReportFilterRequest(
            reportType, fromDate, toDate, officeLocationId, managerId, userId,
            productServiceId, leadStatus, leadSourceId, search, pageNumber, pageSize
        );

        var report = await _crm.GetAdminReportAsync(companyId, request);
        return Ok(report);
    }

    /// <summary>
    /// Exports the specified Admin CRM Report to CSV format
    /// </summary>
    [HttpGet("reports/export")]
    public async Task<IActionResult> ExportReport(
        [FromQuery] int reportType = 1,
        [FromQuery] DateTime? fromDate = null,
        [FromQuery] DateTime? toDate = null,
        [FromQuery] int? officeLocationId = null,
        [FromQuery] int? managerId = null,
        [FromQuery] int? userId = null,
        [FromQuery] int? productServiceId = null,
        [FromQuery] string? leadStatus = null,
        [FromQuery] int? leadSourceId = null,
        [FromQuery] string? search = null)
    {
        int companyId = await GetCurrentCompanyIdAsync();
        if (companyId <= 0) return Forbid();

        var request = new CrmReportFilterRequest(
            reportType, fromDate, toDate, officeLocationId, managerId, userId,
            productServiceId, leadStatus, leadSourceId, search, 1, 10000
        );

        var report = await _crm.GetAdminReportAsync(companyId, request);

        var sb = new StringBuilder();
        sb.AppendLine($"# Report: {report.ReportTitle}");
        sb.AppendLine($"# GeneratedUtc: {DateTime.UtcNow:O}");
        sb.AppendLine($"# {report.Summary.Summary1Label}: {report.Summary.Summary1Value}, {report.Summary.Summary2Label}: {report.Summary.Summary2Value}, {report.Summary.Summary3Label}: {report.Summary.Summary3Value}");
        sb.AppendLine("RowId,Title,Subtitle,Tag,Value1,Value2,Value3,Value4,Status,CreatedAtUtc");

        foreach (var r in report.Rows)
        {
            sb.AppendLine($"{r.RowId},\"{EscapeCsv(r.Title)}\",\"{EscapeCsv(r.Subtitle)}\",\"{EscapeCsv(r.Tag)}\",\"{EscapeCsv(r.Value1)}\",\"{EscapeCsv(r.Value2)}\",\"{EscapeCsv(r.Value3)}\",\"{EscapeCsv(r.Value4)}\",\"{EscapeCsv(r.Status)}\",\"{r.CreatedAtUtc:O}\"");
        }

        var bytes = Encoding.UTF8.GetBytes(sb.ToString());
        string safeTitle = report.ReportTitle.Replace(" ", "_").ToLower();
        return File(bytes, "text/csv", $"crm_admin_{safeTitle}_{DateTime.UtcNow:yyyyMMddHHmm}.csv");
    }

    private static string EscapeCsv(string? val) => (val ?? "").Replace("\"", "\"\"");
}
