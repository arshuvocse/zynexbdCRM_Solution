using System.Data;
using System.Data.Common;
using LiveTracking.Api.Data;
using LiveTracking.Api.DTOs;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Repositories.CRM;

public class CrmLeadRepository : ICrmLeadRepository
{
    private readonly LiveTrackingDbContext _db;

    public CrmLeadRepository(LiveTrackingDbContext db)
    {
        _db = db;
    }

    private async Task<DbConnection> GetOpenConnectionAsync()
    {
        var conn = _db.Database.GetDbConnection();
        if (conn.State != ConnectionState.Open)
        {
            await conn.OpenAsync();
        }
        return conn;
    }

    private static object DbVal(object? val) => val ?? DBNull.Value;

    public async Task<CrmLeadResponse?> SaveLeadAsync(
        int companyId,
        int createdByUserId,
        CreateCrmLeadRequest request,
        int? leadId = null,
        string leadSourceType = "Manager",
        int? officeLocationId = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_Lead_Save";
        cmd.CommandType = CommandType.StoredProcedure;

        var pLeadId = new SqlParameter("@LeadId", SqlDbType.Int)
        {
            Direction = ParameterDirection.InputOutput,
            Value = DbVal(leadId)
        };
        cmd.Parameters.Add(pLeadId);
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@LeadName", request.LeadName.Trim()));
        cmd.Parameters.Add(new SqlParameter("@ContactPerson", DbVal(request.ContactPerson?.Trim())));
        cmd.Parameters.Add(new SqlParameter("@Phone", DbVal(request.Phone?.Trim())));
        cmd.Parameters.Add(new SqlParameter("@Email", DbVal(request.Email?.Trim())));
        cmd.Parameters.Add(new SqlParameter("@Address", DbVal(request.Address?.Trim())));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(request.ProductServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(request.LeadSourceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceType", leadSourceType));
        cmd.Parameters.Add(new SqlParameter("@LeadStatus", string.IsNullOrWhiteSpace(request.LeadStatus) ? "New Lead" : request.LeadStatus.Trim()));
        cmd.Parameters.Add(new SqlParameter("@CreatedByUserId", createdByUserId));
        cmd.Parameters.Add(new SqlParameter("@AssignedUserId", DbVal(request.AssignedUserId)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@EstimatedValue", DbVal(request.EstimatedValue)));
        cmd.Parameters.Add(new SqlParameter("@NextFollowUpDate", DbVal(request.NextFollowUpDate)));
        cmd.Parameters.Add(new SqlParameter("@Remarks", DbVal(request.Remarks?.Trim())));

        using var reader = await cmd.ExecuteReaderAsync();
        if (await reader.ReadAsync())
        {
            return ReadLeadResponse(reader);
        }
        return null;
    }

    public async Task<CrmLeadDetailResponse?> GetLeadByIdAsync(int companyId, int leadId, int? restrictedToUserId = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_Lead_GetById";
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@LeadId", leadId));
        cmd.Parameters.Add(new SqlParameter("@RestrictedToUserId", DbVal(restrictedToUserId)));

        using var reader = await cmd.ExecuteReaderAsync();

        // Result 1: Lead Main Info
        if (!await reader.ReadAsync()) return null;

        var leadResp = ReadLeadResponse(reader);

        // Result 2: Follow-Up History
        var followUps = new List<CrmFollowUpDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                followUps.Add(new CrmFollowUpDto(
                    FollowUpId: reader.GetInt32(reader.GetOrdinal("FollowUpId")),
                    LeadId: reader.GetInt32(reader.GetOrdinal("LeadId")),
                    FollowUpDateUtc: reader.GetDateTime(reader.GetOrdinal("FollowUpDateUtc")),
                    NextFollowUpDate: reader.IsDBNull(reader.GetOrdinal("NextFollowUpDate")) ? null : reader.GetDateTime(reader.GetOrdinal("NextFollowUpDate")),
                    Status: reader.GetString(reader.GetOrdinal("Status")),
                    Remarks: reader.GetString(reader.GetOrdinal("Remarks")),
                    CreatedByUserId: reader.GetInt32(reader.GetOrdinal("CreatedByUserId")),
                    CreatedByUserName: reader.IsDBNull(reader.GetOrdinal("CreatedByUserName")) ? null : reader.GetString(reader.GetOrdinal("CreatedByUserName")),
                    CreatedAtUtc: reader.GetDateTime(reader.GetOrdinal("CreatedAtUtc"))
                ));
            }
        }

        // Result 3: Remarks History
        var remarks = new List<CrmRemarkDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                remarks.Add(new CrmRemarkDto(
                    RemarkId: reader.GetInt32(reader.GetOrdinal("RemarkId")),
                    LeadId: reader.GetInt32(reader.GetOrdinal("LeadId")),
                    UserId: reader.GetInt32(reader.GetOrdinal("UserId")),
                    UserName: reader.IsDBNull(reader.GetOrdinal("UserName")) ? null : reader.GetString(reader.GetOrdinal("UserName")),
                    Remark: reader.GetString(reader.GetOrdinal("Remark")),
                    CreatedAtUtc: reader.GetDateTime(reader.GetOrdinal("CreatedAtUtc"))
                ));
            }
        }

        // Result 4: Assignment History
        var assignments = new List<CrmLeadAssignmentDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                assignments.Add(new CrmLeadAssignmentDto(
                    AssignmentId: reader.GetInt32(reader.GetOrdinal("AssignmentId")),
                    LeadId: reader.GetInt32(reader.GetOrdinal("LeadId")),
                    PreviousUserId: reader.IsDBNull(reader.GetOrdinal("PreviousUserId")) ? null : reader.GetInt32(reader.GetOrdinal("PreviousUserId")),
                    PreviousUserName: reader.IsDBNull(reader.GetOrdinal("PreviousUserName")) ? null : reader.GetString(reader.GetOrdinal("PreviousUserName")),
                    NewUserId: reader.GetInt32(reader.GetOrdinal("NewUserId")),
                    NewUserName: reader.IsDBNull(reader.GetOrdinal("NewUserName")) ? null : reader.GetString(reader.GetOrdinal("NewUserName")),
                    AssignedByUserId: reader.GetInt32(reader.GetOrdinal("AssignedByUserId")),
                    AssignedByUserName: reader.IsDBNull(reader.GetOrdinal("AssignedByUserName")) ? null : reader.GetString(reader.GetOrdinal("AssignedByUserName")),
                    AssignedDateUtc: reader.GetDateTime(reader.GetOrdinal("AssignedDateUtc")),
                    Remarks: reader.IsDBNull(reader.GetOrdinal("Remarks")) ? null : reader.GetString(reader.GetOrdinal("Remarks"))
                ));
            }
        }

        // Result 5: Status History
        var statusHistory = new List<CrmStatusHistoryDto>();
        if (await reader.NextResultAsync())
        {
            while (await reader.ReadAsync())
            {
                statusHistory.Add(new CrmStatusHistoryDto(
                    StatusHistoryId: reader.GetInt32(reader.GetOrdinal("StatusHistoryId")),
                    LeadId: reader.GetInt32(reader.GetOrdinal("LeadId")),
                    PreviousStatus: reader.GetString(reader.GetOrdinal("PreviousStatus")),
                    NewStatus: reader.GetString(reader.GetOrdinal("NewStatus")),
                    ChangedByUserId: reader.GetInt32(reader.GetOrdinal("ChangedByUserId")),
                    ChangedByUserName: reader.IsDBNull(reader.GetOrdinal("ChangedByUserName")) ? null : reader.GetString(reader.GetOrdinal("ChangedByUserName")),
                    ChangedDateUtc: reader.GetDateTime(reader.GetOrdinal("ChangedDateUtc")),
                    Remarks: reader.IsDBNull(reader.GetOrdinal("Remarks")) ? null : reader.GetString(reader.GetOrdinal("Remarks"))
                ));
            }
        }

        return new CrmLeadDetailResponse(
            LeadId: leadResp.LeadId,
            CompanyId: leadResp.CompanyId,
            LeadName: leadResp.LeadName,
            ContactPerson: leadResp.ContactPerson,
            Phone: leadResp.Phone,
            Email: leadResp.Email,
            Address: leadResp.Address,
            ProductServiceId: leadResp.ProductServiceId,
            ProductServiceName: leadResp.ProductServiceName,
            LeadSourceId: leadResp.LeadSourceId,
            LeadSourceName: leadResp.LeadSourceName,
            LeadSourceType: leadResp.LeadSourceType,
            LeadStatus: leadResp.LeadStatus,
            CreatedByUserId: leadResp.CreatedByUserId,
            CreatedByUserName: leadResp.CreatedByUserName,
            AssignedUserId: leadResp.AssignedUserId,
            AssignedUserName: leadResp.AssignedUserName,
            NextFollowUpDate: leadResp.NextFollowUpDate,
            LastFollowUpDate: leadResp.LastFollowUpDate,
            EstimatedValue: leadResp.EstimatedValue,
            Remarks: leadResp.LatestRemark,
            IsActive: leadResp.IsActive,
            CreatedAtUtc: leadResp.CreatedAtUtc,
            UpdatedAtUtc: null,
            OfficeLocationId: leadResp.OfficeLocationId,
            OfficeLocationName: leadResp.OfficeLocationName,
            FollowUps: followUps,
            RemarksHistory: remarks,
            Assignments: assignments,
            StatusHistory: statusHistory,
            AuditLog: new List<CrmAuditLogDto>(),
            FollowUpCount: followUps.Count
        );
    }

    public async Task<PagedResult<CrmLeadResponse>> GetLeadListAsync(
        int companyId,
        int? userId = null,
        int? assignedUserId = null,
        int? officeLocationId = null,
        string? status = null,
        int? productServiceId = null,
        int? leadSourceId = null,
        string? leadSourceType = null,
        string? search = null,
        DateTime? fromDate = null,
        DateTime? toDate = null,
        int pageNumber = 1,
        int pageSize = 20,
        string? sortBy = "CreatedAt",
        string? sortOrder = "DESC")
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_Lead_GetList";
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(userId)));
        cmd.Parameters.Add(new SqlParameter("@AssignedUserId", DbVal(assignedUserId)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@Status", DbVal(status)));
        cmd.Parameters.Add(new SqlParameter("@ProductServiceId", DbVal(productServiceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceId", DbVal(leadSourceId)));
        cmd.Parameters.Add(new SqlParameter("@LeadSourceType", DbVal(leadSourceType)));
        cmd.Parameters.Add(new SqlParameter("@Search", DbVal(search)));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(fromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(toDate)));
        cmd.Parameters.Add(new SqlParameter("@PageNumber", pageNumber));
        cmd.Parameters.Add(new SqlParameter("@PageSize", pageSize));
        cmd.Parameters.Add(new SqlParameter("@SortBy", DbVal(sortBy)));
        cmd.Parameters.Add(new SqlParameter("@SortOrder", DbVal(sortOrder)));

        var items = new List<CrmLeadResponse>();
        int totalRecords = 0;

        using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            if (totalRecords == 0 && !reader.IsDBNull(reader.GetOrdinal("TotalCount")))
            {
                totalRecords = reader.GetInt32(reader.GetOrdinal("TotalCount"));
            }
            items.Add(ReadLeadResponse(reader));
        }

        int totalPages = pageSize > 0 ? (int)Math.Ceiling(totalRecords / (double)pageSize) : 0;
        return new PagedResult<CrmLeadResponse>(items, totalRecords, pageNumber, pageSize, totalPages);
    }

    public async Task<CrmLeadDetailResponse?> AssignLeadAsync(
        int companyId,
        int assignedByUserId,
        int leadId,
        int newUserId,
        string? remarks = null,
        int? officeLocationId = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_Lead_Assign";
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@LeadId", leadId));
        cmd.Parameters.Add(new SqlParameter("@AssignedByUserId", assignedByUserId));
        cmd.Parameters.Add(new SqlParameter("@NewUserId", newUserId));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@Remarks", DbVal(remarks)));

        await cmd.ExecuteNonQueryAsync();

        return await GetLeadByIdAsync(companyId, leadId);
    }

    public async Task<CrmFollowUpDto?> SaveFollowUpAsync(
        int companyId,
        int leadId,
        int userId,
        CreateFollowUpRequest request,
        int? officeLocationId = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_Lead_FollowUp_Save";
        cmd.CommandType = CommandType.StoredProcedure;

        var pId = new SqlParameter("@FollowUpId", SqlDbType.Int)
        {
            Direction = ParameterDirection.InputOutput,
            Value = DBNull.Value
        };
        cmd.Parameters.Add(pId);
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@LeadId", leadId));
        cmd.Parameters.Add(new SqlParameter("@UserId", userId));
        cmd.Parameters.Add(new SqlParameter("@FollowUpDateUtc", DbVal(request.FollowUpDate)));
        cmd.Parameters.Add(new SqlParameter("@NextFollowUpDate", DbVal(request.NextFollowUpDate)));
        cmd.Parameters.Add(new SqlParameter("@Status", request.Status.Trim()));
        cmd.Parameters.Add(new SqlParameter("@Remarks", request.Remarks.Trim()));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));

        using var reader = await cmd.ExecuteReaderAsync();
        if (await reader.ReadAsync())
        {
            return new CrmFollowUpDto(
                FollowUpId: reader.GetInt32(reader.GetOrdinal("FollowUpId")),
                LeadId: reader.GetInt32(reader.GetOrdinal("LeadId")),
                FollowUpDateUtc: reader.GetDateTime(reader.GetOrdinal("FollowUpDateUtc")),
                NextFollowUpDate: reader.IsDBNull(reader.GetOrdinal("NextFollowUpDate")) ? null : reader.GetDateTime(reader.GetOrdinal("NextFollowUpDate")),
                Status: reader.GetString(reader.GetOrdinal("Status")),
                Remarks: reader.GetString(reader.GetOrdinal("Remarks")),
                CreatedByUserId: reader.GetInt32(reader.GetOrdinal("CreatedByUserId")),
                CreatedByUserName: reader.IsDBNull(reader.GetOrdinal("CreatedByUserName")) ? null : reader.GetString(reader.GetOrdinal("CreatedByUserName")),
                CreatedAtUtc: reader.GetDateTime(reader.GetOrdinal("CreatedAtUtc"))
            );
        }
        return null;
    }

    public async Task<List<CrmFollowUpItemDto>> GetTodayFollowUpsAsync(int companyId, int? userId = null, int? officeLocationId = null)
    {
        return await ExecuteFollowUpQueryAsync("dbo.sp_Crm_FollowUp_GetToday", companyId, userId, officeLocationId);
    }

    public async Task<List<CrmFollowUpItemDto>> GetOverdueFollowUpsAsync(int companyId, int? userId = null, int? officeLocationId = null)
    {
        return await ExecuteFollowUpQueryAsync("dbo.sp_Crm_FollowUp_GetOverdue", companyId, userId, officeLocationId);
    }

    public async Task<List<CrmFollowUpItemDto>> GetUpcomingFollowUpsAsync(int companyId, int? userId = null, int? officeLocationId = null, int daysAhead = 30)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_FollowUp_GetUpcoming";
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(userId)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@DaysAhead", daysAhead));

        var list = new List<CrmFollowUpItemDto>();
        using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            list.Add(ReadFollowUpItem(reader));
        }
        return list;
    }

    public async Task<CrmKpiDto?> SaveKpiAsync(int companyId, int createdByUserId, CreateOrUpdateKpiRequest request, int? officeLocationId = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_Kpi_Save";
        cmd.CommandType = CommandType.StoredProcedure;

        var pId = new SqlParameter("@KpiId", SqlDbType.Int)
        {
            Direction = ParameterDirection.InputOutput,
            Value = DBNull.Value
        };
        cmd.Parameters.Add(pId);
        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@CreatedByUserId", createdByUserId));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(request.UserId)));
        cmd.Parameters.Add(new SqlParameter("@PeriodType", request.PeriodType.Trim()));
        cmd.Parameters.Add(new SqlParameter("@FollowUpTarget", request.FollowUpTarget));
        cmd.Parameters.Add(new SqlParameter("@InterestedTarget", request.InterestedTarget));
        cmd.Parameters.Add(new SqlParameter("@ClosedTarget", request.ClosedTarget));
        cmd.Parameters.Add(new SqlParameter("@EffectiveStartDate", DateTime.UtcNow));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId ?? request.OfficeLocationId)));

        using var reader = await cmd.ExecuteReaderAsync();
        if (await reader.ReadAsync())
        {
            return new CrmKpiDto(
                KpiId: reader.GetInt32(reader.GetOrdinal("KpiId")),
                CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
                UserId: reader.IsDBNull(reader.GetOrdinal("UserId")) ? null : reader.GetInt32(reader.GetOrdinal("UserId")),
                UserName: reader.IsDBNull(reader.GetOrdinal("UserName")) ? null : reader.GetString(reader.GetOrdinal("UserName")),
                PeriodType: reader.GetString(reader.GetOrdinal("PeriodType")),
                FollowUpTarget: reader.GetInt32(reader.GetOrdinal("FollowUpTarget")),
                InterestedTarget: reader.GetInt32(reader.GetOrdinal("InterestedTarget")),
                ClosedTarget: reader.GetInt32(reader.GetOrdinal("ClosedTarget")),
                EffectiveStartDate: reader.GetDateTime(reader.GetOrdinal("EffectiveStartDate")),
                IsActive: reader.GetBoolean(reader.GetOrdinal("IsActive")),
                CreatedByUserId: createdByUserId,
                CreatedAtUtc: DateTime.UtcNow,
                OfficeLocationId: reader.IsDBNull(reader.GetOrdinal("OfficeLocationId")) ? null : reader.GetInt32(reader.GetOrdinal("OfficeLocationId")),
                OfficeLocationName: reader.IsDBNull(reader.GetOrdinal("OfficeLocationName")) ? null : reader.GetString(reader.GetOrdinal("OfficeLocationName"))
            );
        }
        return null;
    }

    public async Task<ManagerProductivityResponse> GetProductivityAsync(
        int companyId,
        string periodType = "Daily",
        DateTime? fromDate = null,
        DateTime? toDate = null,
        int? officeLocationId = null,
        int? userId = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_Kpi_Productivity";
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@PeriodType", periodType));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(fromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(toDate)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(userId)));

        var items = new List<EmployeeProductivityItemDto>();
        using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            items.Add(new EmployeeProductivityItemDto(
                UserId: reader.GetInt32(reader.GetOrdinal("UserId")),
                EmployeeName: reader.GetString(reader.GetOrdinal("EmployeeName")),
                FollowUpTarget: reader.GetInt32(reader.GetOrdinal("FollowUpTarget")),
                FollowUpDone: reader.GetInt32(reader.GetOrdinal("FollowUpDone")),
                InterestedTarget: reader.GetInt32(reader.GetOrdinal("InterestedTarget")),
                InterestedDone: reader.GetInt32(reader.GetOrdinal("InterestedDone")),
                ClosedTarget: reader.GetInt32(reader.GetOrdinal("ClosedTarget")),
                ClosedDone: reader.GetInt32(reader.GetOrdinal("ClosedDone")),
                AchievementPercent: reader.IsDBNull(reader.GetOrdinal("AchievementPercent")) ? 0.0 : (double)reader.GetDecimal(reader.GetOrdinal("AchievementPercent")),
                OfficeLocationId: reader.IsDBNull(reader.GetOrdinal("OfficeLocationId")) ? null : reader.GetInt32(reader.GetOrdinal("OfficeLocationId")),
                OfficeLocationName: reader.IsDBNull(reader.GetOrdinal("OfficeLocationName")) ? null : reader.GetString(reader.GetOrdinal("OfficeLocationName"))
            ));
        }

        DateTime effFrom = fromDate ?? DateTime.UtcNow.Date;
        DateTime effTo = toDate ?? DateTime.UtcNow;

        return new ManagerProductivityResponse(periodType, effFrom, effTo, items);
    }

    public async Task<ManagerDashboardResponse> GetManagerDashboardAsync(
        int companyId,
        int managerUserId,
        int? officeLocationId = null,
        DateTime? fromDate = null,
        DateTime? toDate = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_ManagerDashboard";
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@ManagerUserId", managerUserId));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(fromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(toDate)));

        using var reader = await cmd.ExecuteReaderAsync();

        int totalLeads = 0, newLeads = 0, followUpLeads = 0, interestedLeads = 0, closedWonLeads = 0, notInterestedLeads = 0;
        int todayFollowUps = 0, overdueFollowUps = 0;

        if (await reader.ReadAsync())
        {
            totalLeads = reader.GetInt32(reader.GetOrdinal("TotalLeads"));
            newLeads = reader.GetInt32(reader.GetOrdinal("NewLeads"));
            followUpLeads = reader.GetInt32(reader.GetOrdinal("FollowUpLeads"));
            interestedLeads = reader.GetInt32(reader.GetOrdinal("InterestedLeads"));
            closedWonLeads = reader.GetInt32(reader.GetOrdinal("ClosedWonLeads"));
            notInterestedLeads = reader.GetInt32(reader.GetOrdinal("NotInterestedLeads"));
            todayFollowUps = reader.GetInt32(reader.GetOrdinal("TodayFollowUps"));
            overdueFollowUps = reader.GetInt32(reader.GetOrdinal("OverdueFollowUps"));
        }

        var perf = new List<EmployeePerformanceSummaryDto>();

        return new ManagerDashboardResponse(
            TotalLeads: totalLeads,
            NewLeads: newLeads,
            FollowUpLeads: followUpLeads,
            InterestedLeads: interestedLeads,
            NotInterestedLeads: notInterestedLeads,
            ClosedLeads: closedWonLeads,
            TodayFollowUps: todayFollowUps,
            OverdueFollowUps: overdueFollowUps,
            EmployeePerformance: perf
        );
    }

    public async Task<UserDashboardResponse> GetEmployeeDashboardAsync(
        int companyId,
        int userId,
        DateTime? fromDate = null,
        DateTime? toDate = null)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "dbo.sp_Crm_EmployeeDashboard";
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@UserId", userId));
        cmd.Parameters.Add(new SqlParameter("@FromDate", DbVal(fromDate)));
        cmd.Parameters.Add(new SqlParameter("@ToDate", DbVal(toDate)));

        using var reader = await cmd.ExecuteReaderAsync();

        int totalLeads = 0, newLeads = 0, followUpLeads = 0, interestedLeads = 0, closedWonLeads = 0, notInterested = 0;
        int todayFollowUps = 0, overdueFollowUps = 0;

        if (await reader.ReadAsync())
        {
            totalLeads = reader.GetInt32(reader.GetOrdinal("MyTotalLeads"));
            newLeads = reader.GetInt32(reader.GetOrdinal("MyNewLeads"));
            followUpLeads = reader.GetInt32(reader.GetOrdinal("MyFollowUpLeads"));
            interestedLeads = reader.GetInt32(reader.GetOrdinal("MyInterestedLeads"));
            closedWonLeads = reader.GetInt32(reader.GetOrdinal("MyClosedWonLeads"));
            todayFollowUps = reader.GetInt32(reader.GetOrdinal("MyTodayFollowUps"));
            overdueFollowUps = reader.GetInt32(reader.GetOrdinal("MyOverdueFollowUps"));
        }

        return new UserDashboardResponse(
            MyTotalLeads: totalLeads,
            NewLeads: newLeads,
            FollowUpLeads: followUpLeads,
            InterestedLeads: interestedLeads,
            NotInterestedLeads: notInterested,
            ClosedLeads: closedWonLeads,
            TodayFollowUps: todayFollowUps,
            OverdueFollowUps: overdueFollowUps,
            DailyFollowUpTarget: 30,
            DailyFollowUpAchieved: todayFollowUps,
            DailyAchievementPercent: todayFollowUps * 100.0 / 30.0,
            WeeklyFollowUpTarget: 150,
            WeeklyFollowUpAchieved: todayFollowUps * 2,
            WeeklyAchievementPercent: (todayFollowUps * 2) * 100.0 / 150.0,
            MonthlyFollowUpTarget: 600,
            MonthlyFollowUpAchieved: todayFollowUps * 5,
            MonthlyAchievementPercent: (todayFollowUps * 5) * 100.0 / 600.0
        );
    }

    private async Task<List<CrmFollowUpItemDto>> ExecuteFollowUpQueryAsync(string spName, int companyId, int? userId, int? officeLocationId)
    {
        var conn = await GetOpenConnectionAsync();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = spName;
        cmd.CommandType = CommandType.StoredProcedure;

        cmd.Parameters.Add(new SqlParameter("@CompanyId", companyId));
        cmd.Parameters.Add(new SqlParameter("@UserId", DbVal(userId)));
        cmd.Parameters.Add(new SqlParameter("@OfficeLocationId", DbVal(officeLocationId)));

        var list = new List<CrmFollowUpItemDto>();
        using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            list.Add(ReadFollowUpItem(reader));
        }
        return list;
    }

    private static CrmLeadResponse ReadLeadResponse(DbDataReader reader)
    {
        return new CrmLeadResponse(
            LeadId: reader.GetInt32(reader.GetOrdinal("LeadId")),
            CompanyId: reader.GetInt32(reader.GetOrdinal("CompanyId")),
            LeadName: reader.GetString(reader.GetOrdinal("LeadName")),
            ContactPerson: reader.IsDBNull(reader.GetOrdinal("ContactPerson")) ? null : reader.GetString(reader.GetOrdinal("ContactPerson")),
            Phone: reader.IsDBNull(reader.GetOrdinal("Phone")) ? null : reader.GetString(reader.GetOrdinal("Phone")),
            Email: reader.IsDBNull(reader.GetOrdinal("Email")) ? null : reader.GetString(reader.GetOrdinal("Email")),
            Address: reader.IsDBNull(reader.GetOrdinal("Address")) ? null : reader.GetString(reader.GetOrdinal("Address")),
            ProductServiceId: reader.IsDBNull(reader.GetOrdinal("ProductServiceId")) ? null : reader.GetInt32(reader.GetOrdinal("ProductServiceId")),
            ProductServiceName: reader.IsDBNull(reader.GetOrdinal("ProductServiceName")) ? null : reader.GetString(reader.GetOrdinal("ProductServiceName")),
            LeadSourceId: reader.IsDBNull(reader.GetOrdinal("LeadSourceId")) ? null : reader.GetInt32(reader.GetOrdinal("LeadSourceId")),
            LeadSourceName: reader.IsDBNull(reader.GetOrdinal("LeadSourceName")) ? null : reader.GetString(reader.GetOrdinal("LeadSourceName")),
            LeadSourceType: reader.GetString(reader.GetOrdinal("LeadSourceType")),
            LeadStatus: reader.GetString(reader.GetOrdinal("LeadStatus")),
            CreatedByUserId: reader.GetInt32(reader.GetOrdinal("CreatedByUserId")),
            CreatedByUserName: reader.IsDBNull(reader.GetOrdinal("CreatedByUserName")) ? null : reader.GetString(reader.GetOrdinal("CreatedByUserName")),
            AssignedUserId: reader.IsDBNull(reader.GetOrdinal("AssignedUserId")) ? null : reader.GetInt32(reader.GetOrdinal("AssignedUserId")),
            AssignedUserName: reader.IsDBNull(reader.GetOrdinal("AssignedUserName")) ? null : reader.GetString(reader.GetOrdinal("AssignedUserName")),
            NextFollowUpDate: reader.IsDBNull(reader.GetOrdinal("NextFollowUpDate")) ? null : reader.GetDateTime(reader.GetOrdinal("NextFollowUpDate")),
            LastFollowUpDate: reader.IsDBNull(reader.GetOrdinal("LastFollowUpDate")) ? null : reader.GetDateTime(reader.GetOrdinal("LastFollowUpDate")),
            EstimatedValue: reader.IsDBNull(reader.GetOrdinal("EstimatedValue")) ? null : reader.GetDecimal(reader.GetOrdinal("EstimatedValue")),
            LatestRemark: reader.IsDBNull(reader.GetOrdinal("Remarks")) ? null : reader.GetString(reader.GetOrdinal("Remarks")),
            IsActive: reader.GetBoolean(reader.GetOrdinal("IsActive")),
            CreatedAtUtc: reader.GetDateTime(reader.GetOrdinal("CreatedAtUtc")),
            OfficeLocationId: reader.IsDBNull(reader.GetOrdinal("OfficeLocationId")) ? null : reader.GetInt32(reader.GetOrdinal("OfficeLocationId")),
            OfficeLocationName: reader.IsDBNull(reader.GetOrdinal("OfficeLocationName")) ? null : reader.GetString(reader.GetOrdinal("OfficeLocationName")),
            FollowUpCount: 0
        );
    }

    private static CrmFollowUpItemDto ReadFollowUpItem(DbDataReader reader)
    {
        return new CrmFollowUpItemDto(
            LeadId: reader.GetInt32(reader.GetOrdinal("LeadId")),
            LeadName: reader.GetString(reader.GetOrdinal("LeadName")),
            ContactPerson: reader.IsDBNull(reader.GetOrdinal("ContactPerson")) ? null : reader.GetString(reader.GetOrdinal("ContactPerson")),
            Phone: reader.IsDBNull(reader.GetOrdinal("Phone")) ? null : reader.GetString(reader.GetOrdinal("Phone")),
            ProductServiceName: reader.IsDBNull(reader.GetOrdinal("ProductServiceName")) ? null : reader.GetString(reader.GetOrdinal("ProductServiceName")),
            LeadStatus: reader.GetString(reader.GetOrdinal("LeadStatus")),
            NextFollowUpDate: reader.IsDBNull(reader.GetOrdinal("NextFollowUpDate")) ? null : reader.GetDateTime(reader.GetOrdinal("NextFollowUpDate")),
            DaysRemaining: reader.IsDBNull(reader.GetOrdinal("DaysRemaining")) ? null : reader.GetInt32(reader.GetOrdinal("DaysRemaining")),
            IsOverdue: reader.GetBoolean(reader.GetOrdinal("IsOverdue")),
            AssignedUserId: reader.IsDBNull(reader.GetOrdinal("AssignedUserId")) ? null : reader.GetInt32(reader.GetOrdinal("AssignedUserId")),
            AssignedUserName: reader.IsDBNull(reader.GetOrdinal("AssignedUserName")) ? null : reader.GetString(reader.GetOrdinal("AssignedUserName")),
            OfficeLocationId: reader.IsDBNull(reader.GetOrdinal("OfficeLocationId")) ? null : reader.GetInt32(reader.GetOrdinal("OfficeLocationId")),
            OfficeLocationName: reader.IsDBNull(reader.GetOrdinal("OfficeLocationName")) ? null : reader.GetString(reader.GetOrdinal("OfficeLocationName"))
        );
    }
}
