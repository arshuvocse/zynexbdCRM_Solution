/* ==================================================================================
   STORED PROCEDURES & PERFORMANCE INDEXES FOR ENTERPRISE MULTI-TENANT CRM
   Database: LiveTrackingDB (or crm_solution_DB)
   Tables prefix: myonline_tbl_*
   ================================================================================== */

USE [LiveTrackingDB];
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

-- Supporting Performance Indexes
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_Leads_Company_Office_Status_Dates' AND object_id = OBJECT_ID(N'dbo.myonline_tbl_CRM_Leads'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Company_Office_Status_Dates
    ON [dbo].[myonline_tbl_CRM_Leads] ([CompanyId], [IsActive], [LeadStatus])
    INCLUDE ([LeadId], [OfficeLocationId], [AssignedUserId], [CreatedByUserId], [ProductServiceId], [LeadSourceId], [NextFollowUpDate], [CreatedAtUtc]);
    PRINT '✅ Created IX_CRM_Leads_Company_Office_Status_Dates';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_LeadFollowUps_Company_Dates_Status' AND object_id = OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadFollowUps'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_CRM_LeadFollowUps_Company_Dates_Status
    ON [dbo].[myonline_tbl_CRM_LeadFollowUps] ([CompanyId], [FollowUpDateUtc])
    INCLUDE ([FollowUpId], [LeadId], [CreatedByUserId], [Status], [NextFollowUpDate], [OfficeLocationId]);
    PRINT '✅ Created IX_CRM_LeadFollowUps_Company_Dates_Status';
END
GO

/* ==================================================================================
   1. STORED PROCEDURE: sp_Crm_GetAdminDashboard
   Returns 9 Result Sets:
   1. Summary Cards (11 metrics)
   2. Lead Status Distribution (Donut Chart)
   3. Monthly Lead Trend (Bar/Line Chart)
   4. Follow-up Trend (Completed vs Scheduled vs Overdue)
   5. Manager Performance (Comparative bars)
   6. User Productivity (Top users)
   7. Product/Service Performance (Leads, Closed, Value)
   8. Lead Source Distribution (Donut Chart)
   9. Conversion Funnel (Funnel Stages)
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetAdminDashboard', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetAdminDashboard;
GO

CREATE PROCEDURE dbo.sp_Crm_GetAdminDashboard
    @CompanyId          INT,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @OfficeLocationId   INT = NULL,
    @ManagerId          INT = NULL,
    @UserId             INT = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);
    DECLARE @TodayEnd DATETIME2 = DATEADD(DAY, 1, @TodayStart);

    -- Base Filtered Leads CTE
    ;WITH FilteredLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_Users au ON l.AssignedUserId = au.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@UserId IS NULL OR l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@ManagerId IS NULL OR au.CreatedByAdminId = @ManagerId OR l.CreatedByUserId = @ManagerId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    -- 1. SUMMARY CARDS
    SELECT 
        (SELECT COUNT(*) FROM FilteredLeads) AS TotalLeads,
        (SELECT COUNT(*) FROM FilteredLeads WHERE LeadStatus = 'New Lead') AS NewLeads,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps f 
         INNER JOIN FilteredLeads fl ON f.LeadId = fl.LeadId 
         WHERE f.FollowUpDateUtc >= @TodayStart AND f.FollowUpDateUtc < @TodayEnd) AS FollowUpsToday,
        (SELECT COUNT(*) FROM FilteredLeads WHERE NextFollowUpDate IS NOT NULL AND NextFollowUpDate >= @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested')) AS PendingFollowUps,
        (SELECT COUNT(*) FROM FilteredLeads WHERE NextFollowUpDate IS NOT NULL AND NextFollowUpDate < @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested')) AS OverdueFollowUps,
        (SELECT COUNT(*) FROM FilteredLeads WHERE LeadStatus = 'Interested') AS InterestedLeads,
        (SELECT COUNT(*) FROM FilteredLeads WHERE LeadStatus = 'Not Interested') AS NotInterestedLeads,
        (SELECT COUNT(*) FROM FilteredLeads WHERE LeadStatus = 'Closed') AS ClosedLeads,
        CASE 
            WHEN (SELECT COUNT(*) FROM FilteredLeads) > 0 
            THEN ROUND(CAST((SELECT COUNT(*) FROM FilteredLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM FilteredLeads), 2)
            ELSE 0.0 
        END AS ConversionRate,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_Users WHERE CompanyId = @CompanyId AND Role = 'Manager' AND IsActive = 1) AS TotalManagers,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_Users WHERE CompanyId = @CompanyId AND Role IN ('User', 'Employee') AND IsActive = 1) AS TotalUsers;

    -- 2. LEAD STATUS DISTRIBUTION (Donut Chart)
    ;WITH FilteredLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_Users au ON l.AssignedUserId = au.Id
        WHERE l.CompanyId = @CompanyId AND l.IsActive = 1
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@UserId IS NULL OR l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@ManagerId IS NULL OR au.CreatedByAdminId = @ManagerId OR l.CreatedByUserId = @ManagerId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    SELECT 
        LeadStatus AS Label,
        COUNT(*) AS Value,
        CASE LeadStatus
            WHEN 'New Lead' THEN '#3B82F6'       -- Blue
            WHEN 'Follow Up' THEN '#F59E0B'      -- Amber
            WHEN 'Interested' THEN '#10B981'     -- Emerald
            WHEN 'Not Interested' THEN '#EF4444' -- Red
            WHEN 'Closed' THEN '#8B5CF6'         -- Purple
            ELSE '#64748B'
        END AS ColorHex
    FROM FilteredLeads
    GROUP BY LeadStatus
    ORDER BY Value DESC;

    -- 3. MONTHLY LEAD TREND (Last 6 Months)
    ;WITH Months AS (
        SELECT 0 AS Offset UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    ),
    MonthRanges AS (
        SELECT 
            Offset,
            DATEFROMPARTS(YEAR(DATEADD(MONTH, -Offset, @Now)), MONTH(DATEADD(MONTH, -Offset, @Now)), 1) AS MStart,
            EOMONTH(DATEADD(MONTH, -Offset, @Now)) AS MEnd,
            FORMAT(DATEADD(MONTH, -Offset, @Now), 'MMM yyyy') AS MonthLabel
        FROM Months
    )
    SELECT 
        mr.MonthLabel AS Label,
        COUNT(l.LeadId) AS PrimaryValue,    -- Total Leads
        SUM(CASE WHEN l.LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS SecondaryValue -- Closed
    FROM MonthRanges mr
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l 
      ON l.CompanyId = @CompanyId 
     AND l.IsActive = 1
     AND l.CreatedAtUtc >= CAST(mr.MStart AS DATETIME2) 
     AND l.CreatedAtUtc <= DATEADD(SECOND, -1, DATEADD(DAY, 1, CAST(mr.MEnd AS DATETIME2)))
     AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
    GROUP BY mr.Offset, mr.MonthLabel
    ORDER BY mr.Offset DESC;

    -- 4. FOLLOW-UP TREND (Last 7 Days)
    ;WITH Days AS (
        SELECT 0 AS D UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
    ),
    DayRanges AS (
        SELECT 
            D,
            DATEADD(DAY, -D, @TodayStart) AS DayStart,
            DATEADD(DAY, -(D-1), @TodayStart) AS DayEnd,
            FORMAT(DATEADD(DAY, -D, @TodayStart), 'dd MMM') AS DayLabel
        FROM Days
    )
    SELECT 
        dr.DayLabel AS Label,
        COUNT(f.FollowUpId) AS PrimaryValue,    -- Followups Completed
        COUNT(CASE WHEN l.NextFollowUpDate >= dr.DayStart AND l.NextFollowUpDate < dr.DayEnd THEN 1 END) AS SecondaryValue -- Followups Scheduled
    FROM DayRanges dr
    LEFT JOIN dbo.myonline_tbl_CRM_LeadFollowUps f 
      ON f.CompanyId = @CompanyId 
     AND f.FollowUpDateUtc >= dr.DayStart 
     AND f.FollowUpDateUtc < dr.DayEnd
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l 
      ON l.CompanyId = @CompanyId 
     AND l.IsActive = 1
     AND l.NextFollowUpDate >= dr.DayStart 
     AND l.NextFollowUpDate < dr.DayEnd
    GROUP BY dr.D, dr.DayLabel
    ORDER BY dr.D DESC;

    -- 5. MANAGER PERFORMANCE
    SELECT TOP 10
        m.Id AS EntityId,
        ISNULL(m.Name, m.Username) AS Label,
        COUNT(l.LeadId) AS PrimaryValue, -- Total Leads under Manager
        SUM(CASE WHEN l.LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS SecondaryValue -- Closed Leads
    FROM dbo.myonline_tbl_Users m
    LEFT JOIN dbo.myonline_tbl_Users u ON u.CreatedByAdminId = m.Id OR u.OfficeLocationId = m.OfficeLocationId
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l ON l.AssignedUserId = u.Id AND l.CompanyId = @CompanyId AND l.IsActive = 1
    WHERE m.CompanyId = @CompanyId AND m.Role IN ('Manager', 'Admin') AND m.IsActive = 1
    GROUP BY m.Id, m.Name, m.Username
    ORDER BY PrimaryValue DESC;

    -- 6. USER PRODUCTIVITY (Top 10 Employees)
    SELECT TOP 10
        u.Id AS EntityId,
        ISNULL(u.Name, u.Username) AS Label,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps f WHERE f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId) AS PrimaryValue, -- Followups Done
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_Leads l WHERE l.AssignedUserId = u.Id AND l.CompanyId = @CompanyId AND l.LeadStatus = 'Closed' AND l.IsActive = 1) AS SecondaryValue -- Closed Leads
    FROM dbo.myonline_tbl_Users u
    WHERE u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
      AND (@OfficeLocationId IS NULL OR u.OfficeLocationId = @OfficeLocationId)
    ORDER BY PrimaryValue DESC, SecondaryValue DESC;

    -- 7. PRODUCT / SERVICE PERFORMANCE
    SELECT TOP 10
        p.ProductServiceId AS EntityId,
        p.Name AS Label,
        COUNT(l.LeadId) AS PrimaryValue, -- Leads Count
        SUM(CASE WHEN l.LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS SecondaryValue -- Closed Count
    FROM dbo.myonline_tbl_CRM_ProductServices p
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l ON l.ProductServiceId = p.ProductServiceId AND l.IsActive = 1
    WHERE p.CompanyId = @CompanyId AND p.IsActive = 1
    GROUP BY p.ProductServiceId, p.Name
    ORDER BY PrimaryValue DESC;

    -- 8. LEAD SOURCE DISTRIBUTION
    SELECT TOP 10
        s.Name AS Label,
        COUNT(l.LeadId) AS Value,
        '#0EA5E9' AS ColorHex
    FROM dbo.myonline_tbl_CRM_LeadSources s
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l ON l.LeadSourceId = s.LeadSourceId AND l.IsActive = 1
    WHERE s.CompanyId = @CompanyId AND s.IsActive = 1
    GROUP BY s.LeadSourceId, s.Name
    ORDER BY Value DESC;

    -- 9. CONVERSION FUNNEL
    ;WITH FilteredLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        WHERE l.CompanyId = @CompanyId AND l.IsActive = 1
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    SELECT 'Total Leads' AS StageName, COUNT(*) AS StageCount, 100.0 AS ConversionPercent FROM FilteredLeads
    UNION ALL
    SELECT 'Follow-up' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM FilteredLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM FilteredLeads), 2)
                ELSE 0.0 END
    FROM FilteredLeads WHERE LeadStatus IN ('Follow Up', 'Interested', 'Closed')
    UNION ALL
    SELECT 'Interested' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM FilteredLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM FilteredLeads), 2)
                ELSE 0.0 END
    FROM FilteredLeads WHERE LeadStatus IN ('Interested', 'Closed')
    UNION ALL
    SELECT 'Closed' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM FilteredLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM FilteredLeads), 2)
                ELSE 0.0 END
    FROM FilteredLeads WHERE LeadStatus = 'Closed';
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetAdminDashboard';
GO

/* ==================================================================================
   2. STORED PROCEDURE: sp_Crm_GetManagerDashboard
   Returns 9 Result Sets scoped to authorized office/team:
   1. Summary Cards (9 metrics)
   2. Team Lead Trend
   3. Employee Productivity (Comparative)
   4. KPI Achievement
   5. Lead Status Distribution
   6. Follow-up Performance
   7. Product/Service Performance
   8. Lead Source Distribution
   9. Conversion Funnel
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetManagerDashboard', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetManagerDashboard;
GO

CREATE PROCEDURE dbo.sp_Crm_GetManagerDashboard
    @CompanyId          INT,
    @ManagerUserId      INT,
    @OfficeLocationId   INT = NULL,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @UserId             INT = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);
    DECLARE @TodayEnd DATETIME2 = DATEADD(DAY, 1, @TodayStart);

    -- Authorized Offices for this Manager
    DECLARE @AllowedOffices TABLE (OfficeId INT);
    INSERT INTO @AllowedOffices (OfficeId)
    SELECT OfficeLocationId FROM dbo.myonline_tbl_AdminOfficeLocations WHERE AdminUserId = @ManagerUserId
    UNION
    SELECT OfficeLocationId FROM dbo.myonline_tbl_Users WHERE Id = @ManagerUserId AND OfficeLocationId IS NOT NULL;

    -- Base Team Leads Filter
    ;WITH TeamLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_Users au ON l.AssignedUserId = au.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (
              -- Office Scoped
              (EXISTS (SELECT 1 FROM @AllowedOffices) AND l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
              OR l.CreatedByUserId = @ManagerUserId
              OR au.CreatedByAdminId = @ManagerUserId
          )
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@UserId IS NULL OR l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    -- 1. SUMMARY CARDS
    SELECT 
        (SELECT COUNT(*) FROM TeamLeads) AS TeamLeads,
        (SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'New Lead') AS NewLeads,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps f 
         INNER JOIN TeamLeads tl ON f.LeadId = tl.LeadId 
         WHERE f.FollowUpDateUtc >= @TodayStart AND f.FollowUpDateUtc < @TodayEnd) AS TodayFollowUps,
        (SELECT COUNT(*) FROM TeamLeads WHERE NextFollowUpDate IS NOT NULL AND NextFollowUpDate >= @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested')) AS PendingFollowUps,
        (SELECT COUNT(*) FROM TeamLeads WHERE NextFollowUpDate IS NOT NULL AND NextFollowUpDate < @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested')) AS OverdueFollowUps,
        (SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'Interested') AS InterestedLeads,
        (SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'Closed') AS ClosedLeads,
        CASE 
            WHEN (SELECT COUNT(*) FROM TeamLeads) > 0 
            THEN ROUND(CAST((SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 2)
            ELSE 0.0 
        END AS ConversionRate,
        ISNULL((
            SELECT ROUND(AVG(CASE WHEN k.FollowUpTarget > 0 THEN (CAST(ISNULL(act.DoneCount, 0) AS FLOAT) / k.FollowUpTarget) * 100.0 ELSE 100.0 END), 2)
            FROM dbo.myonline_tbl_CRM_KPI k
            LEFT JOIN (
                SELECT CreatedByUserId, COUNT(*) AS DoneCount 
                FROM dbo.myonline_tbl_CRM_LeadFollowUps 
                WHERE CompanyId = @CompanyId AND FollowUpDateUtc >= @TodayStart AND FollowUpDateUtc < @TodayEnd
                GROUP BY CreatedByUserId
            ) act ON act.CreatedByUserId = k.UserId
            WHERE k.CompanyId = @CompanyId AND k.PeriodType = 'Daily' AND k.IsActive = 1
              AND (k.OfficeLocationId IS NULL OR k.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
        ), 85.0) AS KpiAchievement;

    -- 2. TEAM LEAD TREND (Last 6 Months)
    ;WITH Months AS (
        SELECT 0 AS Offset UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    ),
    MonthRanges AS (
        SELECT 
            Offset,
            DATEFROMPARTS(YEAR(DATEADD(MONTH, -Offset, @Now)), MONTH(DATEADD(MONTH, -Offset, @Now)), 1) AS MStart,
            EOMONTH(DATEADD(MONTH, -Offset, @Now)) AS MEnd,
            FORMAT(DATEADD(MONTH, -Offset, @Now), 'MMM yyyy') AS MonthLabel
        FROM Months
    )
    SELECT 
        mr.MonthLabel AS Label,
        COUNT(l.LeadId) AS PrimaryValue,
        SUM(CASE WHEN l.LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS SecondaryValue
    FROM MonthRanges mr
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l 
      ON l.CompanyId = @CompanyId 
     AND l.IsActive = 1
     AND (EXISTS (SELECT 1 FROM @AllowedOffices) AND l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices) OR l.CreatedByUserId = @ManagerUserId)
     AND l.CreatedAtUtc >= CAST(mr.MStart AS DATETIME2) 
     AND l.CreatedAtUtc <= DATEADD(SECOND, -1, DATEADD(DAY, 1, CAST(mr.MEnd AS DATETIME2)))
    GROUP BY mr.Offset, mr.MonthLabel
    ORDER BY mr.Offset DESC;

    -- 3. EMPLOYEE PRODUCTIVITY (Comparative Bar Chart)
    SELECT TOP 10
        u.Id AS EntityId,
        ISNULL(u.Name, u.Username) AS Label,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps f WHERE f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId AND f.FollowUpDateUtc >= @TodayStart AND f.FollowUpDateUtc < @TodayEnd) AS PrimaryValue, -- Followups Done
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_Leads l WHERE l.AssignedUserId = u.Id AND l.CompanyId = @CompanyId AND l.LeadStatus = 'Closed' AND l.IsActive = 1) AS SecondaryValue -- Closed
    FROM dbo.myonline_tbl_Users u
    WHERE u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
      AND (u.CreatedByAdminId = @ManagerUserId OR u.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
    ORDER BY PrimaryValue DESC, SecondaryValue DESC;

    -- 4. KPI ACHIEVEMENT
    SELECT TOP 10
        ISNULL(u.Name, u.Username) AS Label,
        ISNULL(k.FollowUpTarget, 30) AS PrimaryValue, -- Target
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps f WHERE f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId AND f.FollowUpDateUtc >= @TodayStart AND f.FollowUpDateUtc < @TodayEnd) AS SecondaryValue -- Achieved
    FROM dbo.myonline_tbl_Users u
    LEFT JOIN dbo.myonline_tbl_CRM_KPI k ON k.UserId = u.Id AND k.CompanyId = @CompanyId AND k.PeriodType = 'Daily' AND k.IsActive = 1
    WHERE u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
      AND (u.CreatedByAdminId = @ManagerUserId OR u.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
    ORDER BY SecondaryValue DESC;

    -- 5. LEAD STATUS DISTRIBUTION (Donut Chart)
    ;WITH TeamLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        WHERE l.CompanyId = @CompanyId AND l.IsActive = 1
          AND (l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices) OR l.CreatedByUserId = @ManagerUserId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    SELECT 
        LeadStatus AS Label,
        COUNT(*) AS Value,
        CASE LeadStatus
            WHEN 'New Lead' THEN '#3B82F6'
            WHEN 'Follow Up' THEN '#F59E0B'
            WHEN 'Interested' THEN '#10B981'
            WHEN 'Not Interested' THEN '#EF4444'
            WHEN 'Closed' THEN '#8B5CF6'
            ELSE '#64748B'
        END AS ColorHex
    FROM TeamLeads
    GROUP BY LeadStatus;

    -- 6. FOLLOW-UP PERFORMANCE (Today, Overdue, Scheduled, Completed)
    SELECT 'Today' AS Label, COUNT(*) AS PrimaryValue, 0 AS SecondaryValue
    FROM dbo.myonline_tbl_CRM_Leads 
    WHERE CompanyId = @CompanyId AND IsActive = 1 
      AND OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices)
      AND NextFollowUpDate >= @TodayStart AND NextFollowUpDate < @TodayEnd
    UNION ALL
    SELECT 'Overdue' AS Label, COUNT(*) AS PrimaryValue, 0 AS SecondaryValue
    FROM dbo.myonline_tbl_CRM_Leads 
    WHERE CompanyId = @CompanyId AND IsActive = 1 
      AND OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices)
      AND NextFollowUpDate < @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested')
    UNION ALL
    SELECT 'Done Today' AS Label, COUNT(*) AS PrimaryValue, 0 AS SecondaryValue
    FROM dbo.myonline_tbl_CRM_LeadFollowUps 
    WHERE CompanyId = @CompanyId 
      AND OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices)
      AND FollowUpDateUtc >= @TodayStart AND FollowUpDateUtc < @TodayEnd;

    -- 7. PRODUCT / SERVICE PERFORMANCE
    SELECT TOP 8
        p.Name AS Label,
        COUNT(l.LeadId) AS PrimaryValue,
        SUM(CASE WHEN l.LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS SecondaryValue
    FROM dbo.myonline_tbl_CRM_ProductServices p
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l ON l.ProductServiceId = p.ProductServiceId AND l.IsActive = 1 AND l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices)
    WHERE p.CompanyId = @CompanyId AND p.IsActive = 1
    GROUP BY p.ProductServiceId, p.Name
    ORDER BY PrimaryValue DESC;

    -- 8. LEAD SOURCE DISTRIBUTION
    SELECT TOP 8
        s.Name AS Label,
        COUNT(l.LeadId) AS Value,
        '#10B981' AS ColorHex
    FROM dbo.myonline_tbl_CRM_LeadSources s
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l ON l.LeadSourceId = s.LeadSourceId AND l.IsActive = 1 AND l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices)
    WHERE s.CompanyId = @CompanyId AND s.IsActive = 1
    GROUP BY s.LeadSourceId, s.Name
    ORDER BY Value DESC;

    -- 9. CONVERSION FUNNEL
    ;WITH TeamLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        WHERE l.CompanyId = @CompanyId AND l.IsActive = 1
          AND (l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices) OR l.CreatedByUserId = @ManagerUserId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    SELECT 'Team Leads' AS StageName, COUNT(*) AS StageCount, 100.0 AS ConversionPercent FROM TeamLeads
    UNION ALL
    SELECT 'Follow-up' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM TeamLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 2)
                ELSE 0.0 END
    FROM TeamLeads WHERE LeadStatus IN ('Follow Up', 'Interested', 'Closed')
    UNION ALL
    SELECT 'Interested' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM TeamLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 2)
                ELSE 0.0 END
    FROM TeamLeads WHERE LeadStatus IN ('Interested', 'Closed')
    UNION ALL
    SELECT 'Closed' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM TeamLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 2)
                ELSE 0.0 END
    FROM TeamLeads WHERE LeadStatus = 'Closed';
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetManagerDashboard';
GO

/* ==================================================================================
   3. STORED PROCEDURE: sp_Crm_GetUserDashboard
   Returns 6 Result Sets strictly scoped to authenticated user:
   1. Summary Cards (10 metrics)
   2. My Lead Status (Donut Chart)
   3. My Lead Trend (Last 6 Months)
   4. My Follow-up Trend (Last 7 Days)
   5. My KPI Achievement (Daily, Weekly, Monthly)
   6. My Conversion Funnel
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetUserDashboard', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetUserDashboard;
GO

CREATE PROCEDURE dbo.sp_Crm_GetUserDashboard
    @CompanyId          INT,
    @UserId             INT,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);
    DECLARE @TodayEnd DATETIME2 = DATEADD(DAY, 1, @TodayStart);

    DECLARE @WeekStart DATETIME2 = DATEADD(DAY, -((DATEPART(DW, @Now) + 5) % 7), @TodayStart);
    DECLARE @MonthStart DATETIME2 = DATEFROMPARTS(YEAR(@Now), MONTH(@Now), 1);

    -- Base User Leads CTE
    ;WITH MyLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    -- 1. SUMMARY CARDS
    SELECT 
        (SELECT COUNT(*) FROM MyLeads) AS MyTotalLeads,
        (SELECT COUNT(*) FROM MyLeads WHERE LeadStatus = 'New Lead') AS MyNewLeads,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps f 
         WHERE f.CompanyId = @CompanyId AND f.CreatedByUserId = @UserId 
           AND f.FollowUpDateUtc >= @TodayStart AND f.FollowUpDateUtc < @TodayEnd) AS TodayFollowUps,
        (SELECT COUNT(*) FROM MyLeads WHERE NextFollowUpDate IS NOT NULL AND NextFollowUpDate >= @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested')) AS PendingFollowUps,
        (SELECT COUNT(*) FROM MyLeads WHERE NextFollowUpDate IS NOT NULL AND NextFollowUpDate < @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested')) AS OverdueFollowUps,
        (SELECT COUNT(*) FROM MyLeads WHERE LeadStatus = 'Interested') AS InterestedLeads,
        (SELECT COUNT(*) FROM MyLeads WHERE LeadStatus = 'Closed') AS ClosedLeads,
        
        -- Daily KPI
        ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Daily' AND IsActive = 1 ORDER BY UserId DESC), 30) AS DailyFollowUpTarget,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @TodayStart AND FollowUpDateUtc < @TodayEnd) AS DailyFollowUpAchieved,
        ROUND(CAST((SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @TodayStart AND FollowUpDateUtc < @TodayEnd) AS FLOAT) * 100.0 / 
              ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Daily' AND IsActive = 1 ORDER BY UserId DESC), 30), 2) AS DailyAchievementPercent,

        -- Weekly KPI
        ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Weekly' AND IsActive = 1 ORDER BY UserId DESC), 150) AS WeeklyFollowUpTarget,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @WeekStart) AS WeeklyFollowUpAchieved,
        ROUND(CAST((SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @WeekStart) AS FLOAT) * 100.0 / 
              ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Weekly' AND IsActive = 1 ORDER BY UserId DESC), 150), 2) AS WeeklyAchievementPercent,

        -- Monthly KPI
        ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Monthly' AND IsActive = 1 ORDER BY UserId DESC), 600) AS MonthlyFollowUpTarget,
        (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @MonthStart) AS MonthlyFollowUpAchieved,
        ROUND(CAST((SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @MonthStart) AS FLOAT) * 100.0 / 
              ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Monthly' AND IsActive = 1 ORDER BY UserId DESC), 600), 2) AS MonthlyAchievementPercent;

    -- 2. MY LEAD STATUS (Donut Chart)
    ;WITH MyLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        WHERE l.CompanyId = @CompanyId AND l.IsActive = 1
          AND (l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    SELECT 
        LeadStatus AS Label,
        COUNT(*) AS Value,
        CASE LeadStatus
            WHEN 'New Lead' THEN '#3B82F6'
            WHEN 'Follow Up' THEN '#F59E0B'
            WHEN 'Interested' THEN '#10B981'
            WHEN 'Not Interested' THEN '#EF4444'
            WHEN 'Closed' THEN '#8B5CF6'
            ELSE '#64748B'
        END AS ColorHex
    FROM MyLeads
    GROUP BY LeadStatus;

    -- 3. MY LEAD TREND (Last 6 Months)
    ;WITH Months AS (
        SELECT 0 AS Offset UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    ),
    MonthRanges AS (
        SELECT 
            Offset,
            DATEFROMPARTS(YEAR(DATEADD(MONTH, -Offset, @Now)), MONTH(DATEADD(MONTH, -Offset, @Now)), 1) AS MStart,
            EOMONTH(DATEADD(MONTH, -Offset, @Now)) AS MEnd,
            FORMAT(DATEADD(MONTH, -Offset, @Now), 'MMM yyyy') AS MonthLabel
        FROM Months
    )
    SELECT 
        mr.MonthLabel AS Label,
        COUNT(l.LeadId) AS PrimaryValue,
        SUM(CASE WHEN l.LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS SecondaryValue
    FROM MonthRanges mr
    LEFT JOIN dbo.myonline_tbl_CRM_Leads l 
      ON l.CompanyId = @CompanyId 
     AND l.IsActive = 1
     AND (l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
     AND l.CreatedAtUtc >= CAST(mr.MStart AS DATETIME2) 
     AND l.CreatedAtUtc <= DATEADD(SECOND, -1, DATEADD(DAY, 1, CAST(mr.MEnd AS DATETIME2)))
    GROUP BY mr.Offset, mr.MonthLabel
    ORDER BY mr.Offset DESC;

    -- 4. MY FOLLOW-UP TREND (Last 7 Days)
    ;WITH Days AS (
        SELECT 0 AS D UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
    ),
    DayRanges AS (
        SELECT 
            D,
            DATEADD(DAY, -D, @TodayStart) AS DayStart,
            DATEADD(DAY, -(D-1), @TodayStart) AS DayEnd,
            FORMAT(DATEADD(DAY, -D, @TodayStart), 'dd MMM') AS DayLabel
        FROM Days
    )
    SELECT 
        dr.DayLabel AS Label,
        COUNT(f.FollowUpId) AS PrimaryValue,
        0 AS SecondaryValue
    FROM DayRanges dr
    LEFT JOIN dbo.myonline_tbl_CRM_LeadFollowUps f 
      ON f.CompanyId = @CompanyId 
     AND f.CreatedByUserId = @UserId
     AND f.FollowUpDateUtc >= dr.DayStart 
     AND f.FollowUpDateUtc < dr.DayEnd
    GROUP BY dr.D, dr.DayLabel
    ORDER BY dr.D DESC;

    -- 5. MY KPI ACHIEVEMENT
    SELECT 'Daily' AS Label, 
           ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Daily' AND IsActive = 1 ORDER BY UserId DESC), 30) AS PrimaryValue,
           (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @TodayStart AND FollowUpDateUtc < @TodayEnd) AS SecondaryValue
    UNION ALL
    SELECT 'Weekly' AS Label, 
           ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Weekly' AND IsActive = 1 ORDER BY UserId DESC), 150) AS PrimaryValue,
           (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @WeekStart) AS SecondaryValue
    UNION ALL
    SELECT 'Monthly' AS Label, 
           ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Monthly' AND IsActive = 1 ORDER BY UserId DESC), 600) AS PrimaryValue,
           (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @MonthStart) AS SecondaryValue;

    -- 6. MY CONVERSION FUNNEL
    ;WITH MyLeads AS (
        SELECT l.*
        FROM dbo.myonline_tbl_CRM_Leads l
        WHERE l.CompanyId = @CompanyId AND l.IsActive = 1
          AND (l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
    )
    SELECT 'My Leads' AS StageName, COUNT(*) AS StageCount, 100.0 AS ConversionPercent FROM MyLeads
    UNION ALL
    SELECT 'Follow-up' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM MyLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM MyLeads), 2)
                ELSE 0.0 END
    FROM MyLeads WHERE LeadStatus IN ('Follow Up', 'Interested', 'Closed')
    UNION ALL
    SELECT 'Interested' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM MyLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM MyLeads), 2)
                ELSE 0.0 END
    FROM MyLeads WHERE LeadStatus IN ('Interested', 'Closed')
    UNION ALL
    SELECT 'Closed' AS StageName, COUNT(*) AS StageCount,
           CASE WHEN (SELECT COUNT(*) FROM MyLeads) > 0 
                THEN ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM MyLeads), 2)
                ELSE 0.0 END
    FROM MyLeads WHERE LeadStatus = 'Closed';
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetUserDashboard';
GO


/* ==================================================================================
   4. STORED PROCEDURE: sp_Crm_GetAdminReports
   Handles 15 Admin Reports with Server-side Aggregation and Pagination
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetAdminReports', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetAdminReports;
GO

CREATE PROCEDURE dbo.sp_Crm_GetAdminReports
    @ReportType         INT,                -- 1 to 15
    @CompanyId          INT,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @OfficeLocationId   INT = NULL,
    @ManagerId          INT = NULL,
    @UserId             INT = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL,
    @Search             NVARCHAR(100) = NULL,
    @PageNumber         INT = 1,
    @PageSize           INT = 20
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);

    IF @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize < 1 OR @PageSize > 100 SET @PageSize = 20;
    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;

    -- CTE for Leads Matching Filters
    ;WITH FilteredLeads AS (
        SELECT 
            l.LeadId, l.CompanyId, l.OfficeLocationId, l.LeadName, l.ContactPerson,
            l.Phone, l.Email, l.ProductServiceId, l.LeadSourceId, l.LeadStatus,
            l.CreatedByUserId, l.AssignedUserId, l.NextFollowUpDate, l.LastFollowUpDate,
            l.EstimatedValue, l.CreatedAtUtc,
            p.Name AS ProductName,
            s.Name AS SourceName,
            o.Name AS OfficeName,
            cu.Name AS CreatedByName,
            au.Name AS AssignedByName,
            au.CreatedByAdminId AS ManagerUserId
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
        LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
        LEFT JOIN dbo.myonline_tbl_Users cu ON l.CreatedByUserId = cu.Id
        LEFT JOIN dbo.myonline_tbl_Users au ON l.AssignedUserId = au.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@UserId IS NULL OR l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@ManagerId IS NULL OR au.CreatedByAdminId = @ManagerId OR l.CreatedByUserId = @ManagerId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
          AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%')
    ),
    ReportRows AS (
        -- 1. Company Lead Summary
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CreatedAtUtc DESC) AS RowId,
            LeadId AS EntityId,
            LeadName AS Title,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone') AS Subtitle,
            LeadStatus AS Tag,
            ISNULL(ProductName, 'N/A') AS Value1,
            ISNULL(AssignedByName, 'Unassigned') AS Value2,
            ISNULL(OfficeName, 'Headquarters') AS Value3,
            FORMAT(CreatedAtUtc, 'dd MMM yyyy') AS Value4,
            LeadStatus AS Status,
            CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 1

        UNION ALL
        -- 2. Office Location-wise Lead Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            OfficeLocationId AS EntityId,
            ISNULL(OfficeName, 'Unassigned Office') AS Title,
            'Total Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Rate: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Active' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 2
        GROUP BY OfficeLocationId, OfficeName

        UNION ALL
        -- 3. Manager-wise Lead Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            ManagerUserId AS EntityId,
            ISNULL((SELECT TOP 1 ISNULL(Name, Username) FROM dbo.myonline_tbl_Users WHERE Id = fl.ManagerUserId), 'Direct/Admin') AS Title,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conversion: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Manager' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads fl
        WHERE @ReportType = 3
        GROUP BY ManagerUserId

        UNION ALL
        -- 4. User-wise Lead Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            AssignedUserId AS EntityId,
            ISNULL(AssignedByName, 'Unassigned') AS Title,
            'Assigned Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Office: ' + ISNULL(OfficeName, 'N/A') AS Value3,
            'Conv Rate: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'User' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 4
        GROUP BY AssignedUserId, AssignedByName, OfficeName

        UNION ALL
        -- 5. Product/Service-wise Lead Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            ProductServiceId AS EntityId,
            ISNULL(ProductName, 'General Product') AS Title,
            'Total Inquiries: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'In Progress: ' + CAST(SUM(CASE WHEN LeadStatus IN ('Follow Up', 'New Lead') THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Active' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 5
        GROUP BY ProductServiceId, ProductName

        UNION ALL
        -- 6. Lead Source-wise Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            LeadSourceId AS EntityId,
            ISNULL(SourceName, 'Direct / Self') AS Title,
            'Generated Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Converted' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Source' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 6
        GROUP BY LeadSourceId, SourceName

        UNION ALL
        -- 7. Lead Status Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            0 AS EntityId,
            LeadStatus AS Title,
            'Total: ' + CAST(COUNT(*) AS NVARCHAR) + ' Leads' AS Subtitle,
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM FilteredLeads), 1) AS NVARCHAR) + '%' AS Tag,
            'Office: ' + ISNULL(OfficeName, 'All') AS Value1,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value2,
            'Assigned: ' + CAST(SUM(CASE WHEN AssignedUserId IS NOT NULL THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Pending Follow-up: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR) AS Value4,
            LeadStatus AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 7
        GROUP BY LeadStatus, OfficeName

        UNION ALL
        -- 8. Follow-up Summary
        SELECT 
            ROW_NUMBER() OVER(ORDER BY f.FollowUpDateUtc DESC) AS RowId,
            f.FollowUpId AS EntityId,
            fl.LeadName AS Title,
            'Follow-up By: ' + ISNULL(u.Name, u.Username) AS Subtitle,
            f.Status AS Tag,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy HH:mm') AS Value1,
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None') AS Value2,
            'Lead Status: ' + fl.LeadStatus AS Value3,
            'Remarks: ' + LEFT(f.Remarks, 40) AS Value4,
            f.Status AS Status,
            f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN FilteredLeads fl ON f.LeadId = fl.LeadId
        LEFT JOIN dbo.myonline_tbl_Users u ON f.CreatedByUserId = u.Id
        WHERE @ReportType = 8

        UNION ALL
        -- 9. Overdue Follow-up Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY fl.NextFollowUpDate ASC) AS RowId,
            fl.LeadId AS EntityId,
            fl.LeadName AS Title,
            'Assigned: ' + ISNULL(fl.AssignedByName, 'Unassigned') AS Subtitle,
            'OVERDUE' AS Tag,
            'Due: ' + FORMAT(fl.NextFollowUpDate, 'dd MMM yyyy') AS Value1,
            'Contact: ' + ISNULL(fl.Phone, 'N/A') AS Value2,
            'Office: ' + ISNULL(fl.OfficeName, 'N/A') AS Value3,
            'Status: ' + fl.LeadStatus AS Value4,
            'Overdue' AS Status,
            fl.CreatedAtUtc
        FROM FilteredLeads fl
        WHERE @ReportType = 9
          AND fl.NextFollowUpDate IS NOT NULL 
          AND fl.NextFollowUpDate < @TodayStart 
          AND fl.LeadStatus NOT IN ('Closed', 'Not Interested')

        UNION ALL
        -- 10. KPI Summary
        SELECT 
            ROW_NUMBER() OVER(ORDER BY k.EffectiveStartDate DESC) AS RowId,
            k.KpiId AS EntityId,
            ISNULL(u.Name, 'Company Default') AS Title,
            k.PeriodType + ' Target' AS Subtitle,
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR) AS Value1,
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR) AS Value2,
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy') AS Value3,
            'Office: ' + ISNULL(o.Name, 'All Offices') AS Value4,
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END AS Status,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        LEFT JOIN dbo.myonline_tbl_Users u ON k.UserId = u.Id
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON k.OfficeLocationId = o.Id
        WHERE @ReportType = 10 AND k.CompanyId = @CompanyId

        UNION ALL
        -- 11. Employee Productivity
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(f.FollowUpId) DESC) AS RowId,
            u.Id AS EntityId,
            ISNULL(u.Name, u.Username) AS Title,
            ISNULL(o.Name, 'Main Office') AS Subtitle,
            CAST(COUNT(f.FollowUpId) AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Target: ' + CAST(ISNULL(k.FollowUpTarget, 30) AS NVARCHAR) AS Value1,
            'Achieved: ' + CAST(ROUND(CAST(COUNT(f.FollowUpId) AS FLOAT) * 100.0 / ISNULL(k.FollowUpTarget, 30), 1) AS NVARCHAR) + '%' AS Value2,
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Closed: ' + CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value4,
            'Productive' AS Status,
            MAX(u.CreatedAt) AS CreatedAtUtc
        FROM dbo.myonline_tbl_Users u
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON u.OfficeLocationId = o.Id
        LEFT JOIN dbo.myonline_tbl_CRM_KPI k ON k.UserId = u.Id AND k.CompanyId = @CompanyId AND k.PeriodType = 'Daily' AND k.IsActive = 1
        LEFT JOIN dbo.myonline_tbl_CRM_LeadFollowUps f ON f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId
        WHERE @ReportType = 11 AND u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
        GROUP BY u.Id, u.Name, u.Username, o.Name, k.FollowUpTarget

        UNION ALL
        -- 12. Conversion Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY fl.CreatedAtUtc DESC) AS RowId,
            fl.LeadId AS EntityId,
            fl.LeadName AS Title,
            'Assigned: ' + ISNULL(fl.AssignedByName, 'Unassigned') AS Subtitle,
            'WON' AS Tag,
            'Product: ' + ISNULL(fl.ProductName, 'N/A') AS Value1,
            'Value: ৳' + CAST(ISNULL(fl.EstimatedValue, 0) AS NVARCHAR) AS Value2,
            'Source: ' + ISNULL(fl.SourceName, 'Direct') AS Value3,
            'Converted: ' + FORMAT(fl.LastFollowUpDate, 'dd MMM yyyy') AS Value4,
            'Closed' AS Status,
            fl.CreatedAtUtc
        FROM FilteredLeads fl
        WHERE @ReportType = 12 AND fl.LeadStatus = 'Closed'

        UNION ALL
        -- 13. Daily Lead Trend (Last 30 Days)
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CAST(CreatedAtUtc AS DATE) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy') AS Title,
            'New Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Not Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Not Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Trend' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 13
        GROUP BY CAST(CreatedAtUtc AS DATE)

        UNION ALL
        -- 14. Weekly Lead Trend
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR) AS Title,
            'Leads Count: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Trend' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 14
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)

        UNION ALL
        -- 15. Monthly Lead Trend
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CreatedAtUtc, 'MMMM yyyy') AS Title,
            'Monthly Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Est Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Trend' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM FilteredLeads
        WHERE @ReportType = 15
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
    )
    -- RESULT SET 1: REPORT SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM ReportRows) AS TotalRows,
        CASE @ReportType
            WHEN 1 THEN 'Total Leads'
            WHEN 2 THEN 'Total Offices'
            WHEN 3 THEN 'Total Managers'
            WHEN 4 THEN 'Total Users'
            WHEN 5 THEN 'Products'
            WHEN 6 THEN 'Sources'
            WHEN 7 THEN 'Statuses'
            WHEN 8 THEN 'Follow-ups'
            WHEN 9 THEN 'Overdue'
            WHEN 10 THEN 'KPI Target'
            WHEN 11 THEN 'Productivity'
            WHEN 12 THEN 'Conversions'
            ELSE 'Periods'
        END AS Summary1Label,
        CAST((SELECT COUNT(*) FROM ReportRows) AS NVARCHAR) AS Summary1Value,
        'Closed Leads' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM FilteredLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'Conversion Rate' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM FilteredLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM FilteredLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM FilteredLeads), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- RESULT SET 2: PAGINATED ROWS
    SELECT *
    FROM ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetAdminReports';
GO


/* ==================================================================================
   5. STORED PROCEDURE: sp_Crm_GetManagerReports
   Handles Manager Reports scoped to authorized office/team
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetManagerReports', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetManagerReports;
GO

CREATE PROCEDURE dbo.sp_Crm_GetManagerReports
    @ReportType         INT,                -- 1 to 13
    @CompanyId          INT,
    @ManagerUserId      INT,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @OfficeLocationId   INT = NULL,
    @UserId             INT = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL,
    @Search             NVARCHAR(100) = NULL,
    @PageNumber         INT = 1,
    @PageSize           INT = 20
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);

    IF @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize < 1 OR @PageSize > 100 SET @PageSize = 20;
    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;

    DECLARE @AllowedOffices TABLE (OfficeId INT);
    INSERT INTO @AllowedOffices (OfficeId)
    SELECT OfficeLocationId FROM dbo.myonline_tbl_AdminOfficeLocations WHERE AdminUserId = @ManagerUserId
    UNION
    SELECT OfficeLocationId FROM dbo.myonline_tbl_Users WHERE Id = @ManagerUserId AND OfficeLocationId IS NOT NULL;

    ;WITH TeamLeads AS (
        SELECT 
            l.LeadId, l.CompanyId, l.OfficeLocationId, l.LeadName, l.ContactPerson,
            l.Phone, l.Email, l.ProductServiceId, l.LeadSourceId, l.LeadStatus,
            l.CreatedByUserId, l.AssignedUserId, l.NextFollowUpDate, l.LastFollowUpDate,
            l.EstimatedValue, l.CreatedAtUtc,
            p.Name AS ProductName,
            s.Name AS SourceName,
            o.Name AS OfficeName,
            cu.Name AS CreatedByName,
            au.Name AS AssignedByName
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
        LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
        LEFT JOIN dbo.myonline_tbl_Users cu ON l.CreatedByUserId = cu.Id
        LEFT JOIN dbo.myonline_tbl_Users au ON l.AssignedUserId = au.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (
              (EXISTS (SELECT 1 FROM @AllowedOffices) AND l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
              OR l.CreatedByUserId = @ManagerUserId
              OR au.CreatedByAdminId = @ManagerUserId
          )
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@UserId IS NULL OR l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
          AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%')
    ),
    ReportRows AS (
        -- 1. Team Lead Summary
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CreatedAtUtc DESC) AS RowId,
            LeadId AS EntityId,
            LeadName AS Title,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone') AS Subtitle,
            LeadStatus AS Tag,
            ISNULL(ProductName, 'N/A') AS Value1,
            ISNULL(AssignedByName, 'Unassigned') AS Value2,
            ISNULL(OfficeName, 'Branch Office') AS Value3,
            FORMAT(CreatedAtUtc, 'dd MMM yyyy') AS Value4,
            LeadStatus AS Status,
            CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 1

        UNION ALL
        -- 2. Employee-wise Lead Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            AssignedUserId AS EntityId,
            ISNULL(AssignedByName, 'Unassigned') AS Title,
            'Assigned Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Employee' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 2
        GROUP BY AssignedUserId, AssignedByName

        UNION ALL
        -- 3. Employee Productivity Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(f.FollowUpId) DESC) AS RowId,
            u.Id AS EntityId,
            ISNULL(u.Name, u.Username) AS Title,
            'Team Member' AS Subtitle,
            CAST(COUNT(f.FollowUpId) AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Target: ' + CAST(ISNULL(k.FollowUpTarget, 30) AS NVARCHAR) AS Value1,
            'Achieved: ' + CAST(ROUND(CAST(COUNT(f.FollowUpId) AS FLOAT) * 100.0 / ISNULL(k.FollowUpTarget, 30), 1) AS NVARCHAR) + '%' AS Value2,
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Closed: ' + CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value4,
            'Productive' AS Status,
            MAX(u.CreatedAt) AS CreatedAtUtc
        FROM dbo.myonline_tbl_Users u
        LEFT JOIN dbo.myonline_tbl_CRM_KPI k ON k.UserId = u.Id AND k.CompanyId = @CompanyId AND k.PeriodType = 'Daily' AND k.IsActive = 1
        LEFT JOIN dbo.myonline_tbl_CRM_LeadFollowUps f ON f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId
        WHERE @ReportType = 3 
          AND u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
          AND (u.CreatedByAdminId = @ManagerUserId OR u.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
        GROUP BY u.Id, u.Name, u.Username, k.FollowUpTarget

        UNION ALL
        -- 4. Employee KPI Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY k.EffectiveStartDate DESC) AS RowId,
            k.KpiId AS EntityId,
            ISNULL(u.Name, 'Office Default') AS Title,
            k.PeriodType + ' Target' AS Subtitle,
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR) AS Value1,
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR) AS Value2,
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy') AS Value3,
            'Office: ' + ISNULL((SELECT Name FROM dbo.myonline_tbl_OfficeLocations WHERE Id = k.OfficeLocationId), 'Team Office') AS Value4,
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END AS Status,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        LEFT JOIN dbo.myonline_tbl_Users u ON k.UserId = u.Id
        WHERE @ReportType = 4 
          AND k.CompanyId = @CompanyId
          AND (k.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices) OR u.CreatedByAdminId = @ManagerUserId)

        UNION ALL
        -- 5. Follow-up Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY f.FollowUpDateUtc DESC) AS RowId,
            f.FollowUpId AS EntityId,
            tl.LeadName AS Title,
            'Done By: ' + ISNULL(u.Name, u.Username) AS Subtitle,
            f.Status AS Tag,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy') AS Value1,
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None') AS Value2,
            'Remarks: ' + LEFT(f.Remarks, 30) AS Value3,
            'Status: ' + tl.LeadStatus AS Value4,
            f.Status AS Status,
            f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN TeamLeads tl ON f.LeadId = tl.LeadId
        LEFT JOIN dbo.myonline_tbl_Users u ON f.CreatedByUserId = u.Id
        WHERE @ReportType = 5

        UNION ALL
        -- 6. Overdue Follow-up Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY tl.NextFollowUpDate ASC) AS RowId,
            tl.LeadId AS EntityId,
            tl.LeadName AS Title,
            'Assigned: ' + ISNULL(tl.AssignedByName, 'Unassigned') AS Subtitle,
            'OVERDUE' AS Tag,
            'Due: ' + FORMAT(tl.NextFollowUpDate, 'dd MMM yyyy') AS Value1,
            'Phone: ' + ISNULL(tl.Phone, 'N/A') AS Value2,
            'Product: ' + ISNULL(tl.ProductName, 'N/A') AS Value3,
            'Status: ' + tl.LeadStatus AS Value4,
            'Overdue' AS Status,
            tl.CreatedAtUtc
        FROM TeamLeads tl
        WHERE @ReportType = 6
          AND tl.NextFollowUpDate IS NOT NULL 
          AND tl.NextFollowUpDate < @TodayStart 
          AND tl.LeadStatus NOT IN ('Closed', 'Not Interested')

        UNION ALL
        -- 7. Lead Status Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            0 AS EntityId,
            LeadStatus AS Title,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 1) AS NVARCHAR) + '%' AS Tag,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Assigned: ' + CAST(SUM(CASE WHEN AssignedUserId IS NOT NULL THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Pending: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Branch: ' + ISNULL(OfficeName, 'Local') AS Value4,
            LeadStatus AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 7
        GROUP BY LeadStatus, OfficeName

        UNION ALL
        -- 8. Product/Service Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            ProductServiceId AS EntityId,
            ISNULL(ProductName, 'General') AS Title,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Est Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'In Progress: ' + CAST(SUM(CASE WHEN LeadStatus IN ('Follow Up', 'New Lead') THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Product' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 8
        GROUP BY ProductServiceId, ProductName

        UNION ALL
        -- 9. Lead Source Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            LeadSourceId AS EntityId,
            ISNULL(SourceName, 'Direct / Self') AS Title,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Source' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 9
        GROUP BY LeadSourceId, SourceName

        UNION ALL
        -- 10. Conversion Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY tl.CreatedAtUtc DESC) AS RowId,
            tl.LeadId AS EntityId,
            tl.LeadName AS Title,
            'Owner: ' + ISNULL(tl.AssignedByName, 'Unassigned') AS Subtitle,
            'WON' AS Tag,
            'Value: ৳' + CAST(ISNULL(tl.EstimatedValue, 0) AS NVARCHAR) AS Value1,
            'Product: ' + ISNULL(tl.ProductName, 'N/A') AS Value2,
            'Source: ' + ISNULL(tl.SourceName, 'Direct') AS Value3,
            'Won Date: ' + FORMAT(tl.LastFollowUpDate, 'dd MMM yyyy') AS Value4,
            'Closed' AS Status,
            tl.CreatedAtUtc
        FROM TeamLeads tl
        WHERE @ReportType = 10 AND tl.LeadStatus = 'Closed'

        UNION ALL
        -- 11. Daily Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CAST(CreatedAtUtc AS DATE) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy') AS Title,
            'Daily Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Rate: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Daily' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 11
        GROUP BY CAST(CreatedAtUtc AS DATE)

        UNION ALL
        -- 12. Weekly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR) AS Title,
            'Weekly Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Rate: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Weekly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 12
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)

        UNION ALL
        -- 13. Monthly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CreatedAtUtc, 'MMMM yyyy') AS Title,
            'Monthly Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Rate: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Monthly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 13
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
    )
    -- RESULT SET 1: REPORT SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM ReportRows) AS TotalRows,
        'Team Total' AS Summary1Label,
        CAST((SELECT COUNT(*) FROM ReportRows) AS NVARCHAR) AS Summary1Value,
        'Team Won' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'Team Conv' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM TeamLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- RESULT SET 2: PAGINATED ROWS
    SELECT *
    FROM ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetManagerReports';
GO

/* ==================================================================================
   6. STORED PROCEDURE: sp_Crm_GetUserReports
   Handles 13 User Reports strictly scoped to the authenticated user
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetUserReports', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetUserReports;
GO

CREATE PROCEDURE dbo.sp_Crm_GetUserReports
    @ReportType         INT,                -- 1 to 13
    @CompanyId          INT,
    @UserId             INT,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL,
    @Search             NVARCHAR(100) = NULL,
    @PageNumber         INT = 1,
    @PageSize           INT = 20
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);

    IF @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize < 1 OR @PageSize > 100 SET @PageSize = 20;
    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;

    ;WITH MyLeads AS (
        SELECT 
            l.LeadId, l.CompanyId, l.OfficeLocationId, l.LeadName, l.ContactPerson,
            l.Phone, l.Email, l.ProductServiceId, l.LeadSourceId, l.LeadStatus,
            l.CreatedByUserId, l.AssignedUserId, l.NextFollowUpDate, l.LastFollowUpDate,
            l.EstimatedValue, l.CreatedAtUtc,
            p.Name AS ProductName,
            s.Name AS SourceName,
            o.Name AS OfficeName
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
        LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
          AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%')
    ),
    ReportRows AS (
        -- 1. My Lead Summary
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CreatedAtUtc DESC) AS RowId,
            LeadId AS EntityId,
            LeadName AS Title,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone') AS Subtitle,
            LeadStatus AS Tag,
            ISNULL(ProductName, 'N/A') AS Value1,
            'Est: ৳' + CAST(ISNULL(EstimatedValue, 0) AS NVARCHAR) AS Value2,
            ISNULL(SourceName, 'Self') AS Value3,
            FORMAT(CreatedAtUtc, 'dd MMM yyyy') AS Value4,
            LeadStatus AS Status,
            CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 1

        UNION ALL
        -- 2. My Lead Status
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            0 AS EntityId,
            LeadStatus AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM MyLeads), 1) AS NVARCHAR) + '%' AS Tag,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Pending Follow-up: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Overdue: ' + CAST(SUM(CASE WHEN NextFollowUpDate < @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Office: ' + ISNULL(OfficeName, 'Local') AS Value4,
            LeadStatus AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 2
        GROUP BY LeadStatus, OfficeName

        UNION ALL
        -- 3. My Follow-up Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY f.FollowUpDateUtc DESC) AS RowId,
            f.FollowUpId AS EntityId,
            ml.LeadName AS Title,
            'Follow-up Status: ' + f.Status AS Subtitle,
            f.Status AS Tag,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy') AS Value1,
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None') AS Value2,
            'Remarks: ' + LEFT(f.Remarks, 30) AS Value3,
            'Lead: ' + ml.LeadStatus AS Value4,
            f.Status AS Status,
            f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN MyLeads ml ON f.LeadId = ml.LeadId
        WHERE @ReportType = 3 AND f.CreatedByUserId = @UserId

        UNION ALL
        -- 4. My Overdue Follow-ups
        SELECT 
            ROW_NUMBER() OVER(ORDER BY ml.NextFollowUpDate ASC) AS RowId,
            ml.LeadId AS EntityId,
            ml.LeadName AS Title,
            'Contact: ' + ISNULL(ml.ContactPerson, 'N/A') + ' (' + ISNULL(ml.Phone, 'N/A') + ')' AS Subtitle,
            'OVERDUE' AS Tag,
            'Due Date: ' + FORMAT(ml.NextFollowUpDate, 'dd MMM yyyy') AS Value1,
            'Product: ' + ISNULL(ml.ProductName, 'N/A') AS Value2,
            'Status: ' + ml.LeadStatus AS Value3,
            'Est: ৳' + CAST(ISNULL(ml.EstimatedValue, 0) AS NVARCHAR) AS Value4,
            'Overdue' AS Status,
            ml.CreatedAtUtc
        FROM MyLeads ml
        WHERE @ReportType = 4
          AND ml.NextFollowUpDate IS NOT NULL 
          AND ml.NextFollowUpDate < @TodayStart 
          AND ml.LeadStatus NOT IN ('Closed', 'Not Interested')

        UNION ALL
        -- 5. My KPI Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY k.EffectiveStartDate DESC) AS RowId,
            k.KpiId AS EntityId,
            k.PeriodType + ' KPI Goal' AS Title,
            'Target Setting' AS Subtitle,
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR) AS Value1,
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR) AS Value2,
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy') AS Value3,
            'Status: ' + CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END AS Value4,
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END AS Status,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        WHERE @ReportType = 5 AND k.CompanyId = @CompanyId AND (k.UserId = @UserId OR k.UserId IS NULL)

        UNION ALL
        -- 6. My KPI Achievement
        SELECT 
            ROW_NUMBER() OVER(ORDER BY p.PeriodType ASC) AS RowId,
            0 AS EntityId,
            p.PeriodType + ' Performance' AS Title,
            'Achievement Overview' AS Subtitle,
            CAST(ROUND(CAST(p.Achieved AS FLOAT) * 100.0 / p.TargetVal, 1) AS NVARCHAR) + '%' AS Tag,
            'Target: ' + CAST(p.TargetVal AS NVARCHAR) AS Value1,
            'Achieved: ' + CAST(p.Achieved AS NVARCHAR) AS Value2,
            'Remaining: ' + CAST(CASE WHEN p.TargetVal > p.Achieved THEN p.TargetVal - p.Achieved ELSE 0 END AS NVARCHAR) AS Value3,
            'Over: ' + CAST(CASE WHEN p.Achieved > p.TargetVal THEN p.Achieved - p.TargetVal ELSE 0 END AS NVARCHAR) AS Value4,
            'KPI' AS Status,
            @Now AS CreatedAtUtc
        FROM (
            SELECT 'Daily' AS PeriodType,
                   ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Daily' AND IsActive = 1 ORDER BY UserId DESC), 30) AS TargetVal,
                   (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @TodayStart) AS Achieved
            UNION ALL
            SELECT 'Weekly' AS PeriodType,
                   ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Weekly' AND IsActive = 1 ORDER BY UserId DESC), 150) AS TargetVal,
                   (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= DATEADD(DAY, -7, @TodayStart)) AS Achieved
            UNION ALL
            SELECT 'Monthly' AS PeriodType,
                   ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Monthly' AND IsActive = 1 ORDER BY UserId DESC), 600) AS TargetVal,
                   (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= DATEADD(MONTH, -1, @TodayStart)) AS Achieved
        ) p
        WHERE @ReportType = 6

        UNION ALL
        -- 7. My Productivity
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CAST(f.FollowUpDateUtc AS DATE) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CAST(f.FollowUpDateUtc AS DATE), 'dd MMM yyyy') AS Title,
            'Follow-ups Done: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN f.Status = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Target: 30' AS Value3,
            'Score: ' + CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / 30, 1) AS NVARCHAR) + '%' AS Value4,
            'Productivity' AS Status,
            MAX(f.CreatedAtUtc) AS CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        WHERE @ReportType = 7 AND f.CompanyId = @CompanyId AND f.CreatedByUserId = @UserId
        GROUP BY CAST(f.FollowUpDateUtc AS DATE)

        UNION ALL
        -- 8. My Product/Service-wise Leads
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            ProductServiceId AS EntityId,
            ISNULL(ProductName, 'General Product') AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won' AS Tag,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Product' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 8
        GROUP BY ProductServiceId, ProductName

        UNION ALL
        -- 9. My Lead Source-wise Leads
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            LeadSourceId AS EntityId,
            ISNULL(SourceName, 'Self / Direct') AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Source' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 9
        GROUP BY LeadSourceId, SourceName

        UNION ALL
        -- 10. My Conversion Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY ml.CreatedAtUtc DESC) AS RowId,
            ml.LeadId AS EntityId,
            ml.LeadName AS Title,
            'Contact: ' + ISNULL(ml.ContactPerson, 'N/A') AS Subtitle,
            'CLOSED' AS Tag,
            'Value: ৳' + CAST(ISNULL(ml.EstimatedValue, 0) AS NVARCHAR) AS Value1,
            'Product: ' + ISNULL(ml.ProductName, 'N/A') AS Value2,
            'Won Date: ' + FORMAT(ml.LastFollowUpDate, 'dd MMM yyyy') AS Value3,
            'Source: ' + ISNULL(ml.SourceName, 'Self') AS Value4,
            'Closed' AS Status,
            ml.CreatedAtUtc
        FROM MyLeads ml
        WHERE @ReportType = 10 AND ml.LeadStatus = 'Closed'

        UNION ALL
        -- 11. Daily Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CAST(CreatedAtUtc AS DATE) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy') AS Title,
            'Created: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Daily' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 11
        GROUP BY CAST(CreatedAtUtc AS DATE)

        UNION ALL
        -- 12. Weekly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR) AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Weekly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 12
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)

        UNION ALL
        -- 13. Monthly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CreatedAtUtc, 'MMMM yyyy') AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Monthly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 13
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
    )
    -- RESULT SET 1: REPORT SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM ReportRows) AS TotalRows,
        'My Total' AS Summary1Label,
        CAST((SELECT COUNT(*) FROM ReportRows) AS NVARCHAR) AS Summary1Value,
        'My Won' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM MyLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'My Conv Rate' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM MyLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM MyLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM MyLeads), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- RESULT SET 2: PAGINATED ROWS
    SELECT *
    FROM ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetUserReports';
GO


/* ==================================================================================
   5. STORED PROCEDURE: sp_Crm_GetManagerReports
   Handles Manager Reports scoped to authorized office/team
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetManagerReports', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetManagerReports;
GO

CREATE PROCEDURE dbo.sp_Crm_GetManagerReports
    @ReportType         INT,                -- 1 to 13
    @CompanyId          INT,
    @ManagerUserId      INT,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @OfficeLocationId   INT = NULL,
    @UserId             INT = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL,
    @Search             NVARCHAR(100) = NULL,
    @PageNumber         INT = 1,
    @PageSize           INT = 20
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);

    IF @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize < 1 OR @PageSize > 100 SET @PageSize = 20;
    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;

    DECLARE @AllowedOffices TABLE (OfficeId INT);
    INSERT INTO @AllowedOffices (OfficeId)
    SELECT OfficeLocationId FROM dbo.myonline_tbl_AdminOfficeLocations WHERE AdminUserId = @ManagerUserId
    UNION
    SELECT OfficeLocationId FROM dbo.myonline_tbl_Users WHERE Id = @ManagerUserId AND OfficeLocationId IS NOT NULL;

    ;WITH TeamLeads AS (
        SELECT 
            l.LeadId, l.CompanyId, l.OfficeLocationId, l.LeadName, l.ContactPerson,
            l.Phone, l.Email, l.ProductServiceId, l.LeadSourceId, l.LeadStatus,
            l.CreatedByUserId, l.AssignedUserId, l.NextFollowUpDate, l.LastFollowUpDate,
            l.EstimatedValue, l.CreatedAtUtc,
            p.Name AS ProductName,
            s.Name AS SourceName,
            o.Name AS OfficeName,
            cu.Name AS CreatedByName,
            au.Name AS AssignedByName
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
        LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
        LEFT JOIN dbo.myonline_tbl_Users cu ON l.CreatedByUserId = cu.Id
        LEFT JOIN dbo.myonline_tbl_Users au ON l.AssignedUserId = au.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (
              (EXISTS (SELECT 1 FROM @AllowedOffices) AND l.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
              OR l.CreatedByUserId = @ManagerUserId
              OR au.CreatedByAdminId = @ManagerUserId
          )
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@UserId IS NULL OR l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
          AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%')
    ),
    ReportRows AS (
        -- 1. Team Lead Summary
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CreatedAtUtc DESC) AS RowId,
            LeadId AS EntityId,
            LeadName AS Title,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone') AS Subtitle,
            LeadStatus AS Tag,
            ISNULL(ProductName, 'N/A') AS Value1,
            ISNULL(AssignedByName, 'Unassigned') AS Value2,
            ISNULL(OfficeName, 'Branch Office') AS Value3,
            FORMAT(CreatedAtUtc, 'dd MMM yyyy') AS Value4,
            LeadStatus AS Status,
            CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 1

        UNION ALL
        -- 2. Employee-wise Lead Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            AssignedUserId AS EntityId,
            ISNULL(AssignedByName, 'Unassigned') AS Title,
            'Assigned Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Employee' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 2
        GROUP BY AssignedUserId, AssignedByName

        UNION ALL
        -- 3. Employee Productivity Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(f.FollowUpId) DESC) AS RowId,
            u.Id AS EntityId,
            ISNULL(u.Name, u.Username) AS Title,
            'Team Member' AS Subtitle,
            CAST(COUNT(f.FollowUpId) AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Target: ' + CAST(ISNULL(k.FollowUpTarget, 30) AS NVARCHAR) AS Value1,
            'Achieved: ' + CAST(ROUND(CAST(COUNT(f.FollowUpId) AS FLOAT) * 100.0 / ISNULL(k.FollowUpTarget, 30), 1) AS NVARCHAR) + '%' AS Value2,
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Closed: ' + CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value4,
            'Productive' AS Status,
            MAX(u.CreatedAt) AS CreatedAtUtc
        FROM dbo.myonline_tbl_Users u
        LEFT JOIN dbo.myonline_tbl_CRM_KPI k ON k.UserId = u.Id AND k.CompanyId = @CompanyId AND k.PeriodType = 'Daily' AND k.IsActive = 1
        LEFT JOIN dbo.myonline_tbl_CRM_LeadFollowUps f ON f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId
        WHERE @ReportType = 3 
          AND u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
          AND (u.CreatedByAdminId = @ManagerUserId OR u.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
        GROUP BY u.Id, u.Name, u.Username, k.FollowUpTarget

        UNION ALL
        -- 4. Employee KPI Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY k.EffectiveStartDate DESC) AS RowId,
            k.KpiId AS EntityId,
            ISNULL(u.Name, 'Office Default') AS Title,
            k.PeriodType + ' Target' AS Subtitle,
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR) AS Value1,
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR) AS Value2,
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy') AS Value3,
            'Office: ' + ISNULL((SELECT Name FROM dbo.myonline_tbl_OfficeLocations WHERE Id = k.OfficeLocationId), 'Team Office') AS Value4,
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END AS Status,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        LEFT JOIN dbo.myonline_tbl_Users u ON k.UserId = u.Id
        WHERE @ReportType = 4 
          AND k.CompanyId = @CompanyId
          AND (k.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices) OR u.CreatedByAdminId = @ManagerUserId)

        UNION ALL
        -- 5. Follow-up Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY f.FollowUpDateUtc DESC) AS RowId,
            f.FollowUpId AS EntityId,
            tl.LeadName AS Title,
            'Done By: ' + ISNULL(u.Name, u.Username) AS Subtitle,
            f.Status AS Tag,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy') AS Value1,
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None') AS Value2,
            'Remarks: ' + LEFT(f.Remarks, 30) AS Value3,
            'Status: ' + tl.LeadStatus AS Value4,
            f.Status AS Status,
            f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN TeamLeads tl ON f.LeadId = tl.LeadId
        LEFT JOIN dbo.myonline_tbl_Users u ON f.CreatedByUserId = u.Id
        WHERE @ReportType = 5

        UNION ALL
        -- 6. Overdue Follow-up Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY tl.NextFollowUpDate ASC) AS RowId,
            tl.LeadId AS EntityId,
            tl.LeadName AS Title,
            'Assigned: ' + ISNULL(tl.AssignedByName, 'Unassigned') AS Subtitle,
            'OVERDUE' AS Tag,
            'Due: ' + FORMAT(tl.NextFollowUpDate, 'dd MMM yyyy') AS Value1,
            'Phone: ' + ISNULL(tl.Phone, 'N/A') AS Value2,
            'Product: ' + ISNULL(tl.ProductName, 'N/A') AS Value3,
            'Status: ' + tl.LeadStatus AS Value4,
            'Overdue' AS Status,
            tl.CreatedAtUtc
        FROM TeamLeads tl
        WHERE @ReportType = 6
          AND tl.NextFollowUpDate IS NOT NULL 
          AND tl.NextFollowUpDate < @TodayStart 
          AND tl.LeadStatus NOT IN ('Closed', 'Not Interested')

        UNION ALL
        -- 7. Lead Status Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            0 AS EntityId,
            LeadStatus AS Title,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 1) AS NVARCHAR) + '%' AS Tag,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Assigned: ' + CAST(SUM(CASE WHEN AssignedUserId IS NOT NULL THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Pending: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Branch: ' + ISNULL(OfficeName, 'Local') AS Value4,
            LeadStatus AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 7
        GROUP BY LeadStatus, OfficeName

        UNION ALL
        -- 8. Product/Service Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            ProductServiceId AS EntityId,
            ISNULL(ProductName, 'General') AS Title,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Est Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'In Progress: ' + CAST(SUM(CASE WHEN LeadStatus IN ('Follow Up', 'New Lead') THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Product' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 8
        GROUP BY ProductServiceId, ProductName

        UNION ALL
        -- 9. Lead Source Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            LeadSourceId AS EntityId,
            ISNULL(SourceName, 'Direct / Self') AS Title,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Source' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 9
        GROUP BY LeadSourceId, SourceName

        UNION ALL
        -- 10. Conversion Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY tl.CreatedAtUtc DESC) AS RowId,
            tl.LeadId AS EntityId,
            tl.LeadName AS Title,
            'Owner: ' + ISNULL(tl.AssignedByName, 'Unassigned') AS Subtitle,
            'WON' AS Tag,
            'Value: ৳' + CAST(ISNULL(tl.EstimatedValue, 0) AS NVARCHAR) AS Value1,
            'Product: ' + ISNULL(tl.ProductName, 'N/A') AS Value2,
            'Source: ' + ISNULL(tl.SourceName, 'Direct') AS Value3,
            'Won Date: ' + FORMAT(tl.LastFollowUpDate, 'dd MMM yyyy') AS Value4,
            'Closed' AS Status,
            tl.CreatedAtUtc
        FROM TeamLeads tl
        WHERE @ReportType = 10 AND tl.LeadStatus = 'Closed'

        UNION ALL
        -- 11. Daily Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CAST(CreatedAtUtc AS DATE) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy') AS Title,
            'Daily Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Rate: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Daily' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 11
        GROUP BY CAST(CreatedAtUtc AS DATE)

        UNION ALL
        -- 12. Weekly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR) AS Title,
            'Weekly Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Rate: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Weekly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 12
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)

        UNION ALL
        -- 13. Monthly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CreatedAtUtc, 'MMMM yyyy') AS Title,
            'Monthly Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Rate: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Monthly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM TeamLeads
        WHERE @ReportType = 13
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
    )
    -- RESULT SET 1: REPORT SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM ReportRows) AS TotalRows,
        'Team Total' AS Summary1Label,
        CAST((SELECT COUNT(*) FROM ReportRows) AS NVARCHAR) AS Summary1Value,
        'Team Won' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'Team Conv' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM TeamLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM TeamLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM TeamLeads), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- RESULT SET 2: PAGINATED ROWS
    SELECT *
    FROM ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetManagerReports';
GO

/* ==================================================================================
   6. STORED PROCEDURE: sp_Crm_GetUserReports
   Handles 13 User Reports strictly scoped to the authenticated user
   ================================================================================== */
IF OBJECT_ID(N'dbo.sp_Crm_GetUserReports', N'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_Crm_GetUserReports;
GO

CREATE PROCEDURE dbo.sp_Crm_GetUserReports
    @ReportType         INT,                -- 1 to 13
    @CompanyId          INT,
    @UserId             INT,
    @FromDate           DATETIME2 = NULL,
    @ToDate             DATETIME2 = NULL,
    @ProductServiceId   INT = NULL,
    @LeadStatus         NVARCHAR(50) = NULL,
    @LeadSourceId       INT = NULL,
    @Search             NVARCHAR(100) = NULL,
    @PageNumber         INT = 1,
    @PageSize           INT = 20
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @TodayStart DATETIME2 = CAST(CAST(@Now AS DATE) AS DATETIME2);

    IF @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize < 1 OR @PageSize > 100 SET @PageSize = 20;
    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;

    ;WITH MyLeads AS (
        SELECT 
            l.LeadId, l.CompanyId, l.OfficeLocationId, l.LeadName, l.ContactPerson,
            l.Phone, l.Email, l.ProductServiceId, l.LeadSourceId, l.LeadStatus,
            l.CreatedByUserId, l.AssignedUserId, l.NextFollowUpDate, l.LastFollowUpDate,
            l.EstimatedValue, l.CreatedAtUtc,
            p.Name AS ProductName,
            s.Name AS SourceName,
            o.Name AS OfficeName
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
        LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (l.AssignedUserId = @UserId OR (l.CreatedByUserId = @UserId AND l.AssignedUserId IS NULL))
          AND (@ProductServiceId IS NULL OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadStatus IS NULL OR l.LeadStatus = @LeadStatus)
          AND (@LeadSourceId IS NULL OR l.LeadSourceId = @LeadSourceId)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
          AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%')
    ),
    ReportRows AS (
        -- 1. My Lead Summary
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CreatedAtUtc DESC) AS RowId,
            LeadId AS EntityId,
            LeadName AS Title,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone') AS Subtitle,
            LeadStatus AS Tag,
            ISNULL(ProductName, 'N/A') AS Value1,
            'Est: ৳' + CAST(ISNULL(EstimatedValue, 0) AS NVARCHAR) AS Value2,
            ISNULL(SourceName, 'Self') AS Value3,
            FORMAT(CreatedAtUtc, 'dd MMM yyyy') AS Value4,
            LeadStatus AS Status,
            CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 1

        UNION ALL
        -- 2. My Lead Status
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            0 AS EntityId,
            LeadStatus AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM MyLeads), 1) AS NVARCHAR) + '%' AS Tag,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Pending Follow-up: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Overdue: ' + CAST(SUM(CASE WHEN NextFollowUpDate < @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Office: ' + ISNULL(OfficeName, 'Local') AS Value4,
            LeadStatus AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 2
        GROUP BY LeadStatus, OfficeName

        UNION ALL
        -- 3. My Follow-up Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY f.FollowUpDateUtc DESC) AS RowId,
            f.FollowUpId AS EntityId,
            ml.LeadName AS Title,
            'Follow-up Status: ' + f.Status AS Subtitle,
            f.Status AS Tag,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy') AS Value1,
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None') AS Value2,
            'Remarks: ' + LEFT(f.Remarks, 30) AS Value3,
            'Lead: ' + ml.LeadStatus AS Value4,
            f.Status AS Status,
            f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN MyLeads ml ON f.LeadId = ml.LeadId
        WHERE @ReportType = 3 AND f.CreatedByUserId = @UserId

        UNION ALL
        -- 4. My Overdue Follow-ups
        SELECT 
            ROW_NUMBER() OVER(ORDER BY ml.NextFollowUpDate ASC) AS RowId,
            ml.LeadId AS EntityId,
            ml.LeadName AS Title,
            'Contact: ' + ISNULL(ml.ContactPerson, 'N/A') + ' (' + ISNULL(ml.Phone, 'N/A') + ')' AS Subtitle,
            'OVERDUE' AS Tag,
            'Due Date: ' + FORMAT(ml.NextFollowUpDate, 'dd MMM yyyy') AS Value1,
            'Product: ' + ISNULL(ml.ProductName, 'N/A') AS Value2,
            'Status: ' + ml.LeadStatus AS Value3,
            'Est: ৳' + CAST(ISNULL(ml.EstimatedValue, 0) AS NVARCHAR) AS Value4,
            'Overdue' AS Status,
            ml.CreatedAtUtc
        FROM MyLeads ml
        WHERE @ReportType = 4
          AND ml.NextFollowUpDate IS NOT NULL 
          AND ml.NextFollowUpDate < @TodayStart 
          AND ml.LeadStatus NOT IN ('Closed', 'Not Interested')

        UNION ALL
        -- 5. My KPI Report
        SELECT 
            ROW_NUMBER() OVER(ORDER BY k.EffectiveStartDate DESC) AS RowId,
            k.KpiId AS EntityId,
            k.PeriodType + ' KPI Goal' AS Title,
            'Target Setting' AS Subtitle,
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups' AS Tag,
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR) AS Value1,
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR) AS Value2,
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy') AS Value3,
            'Status: ' + CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END AS Value4,
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END AS Status,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        WHERE @ReportType = 5 AND k.CompanyId = @CompanyId AND (k.UserId = @UserId OR k.UserId IS NULL)

        UNION ALL
        -- 6. My KPI Achievement
        SELECT 
            ROW_NUMBER() OVER(ORDER BY p.PeriodType ASC) AS RowId,
            0 AS EntityId,
            p.PeriodType + ' Performance' AS Title,
            'Achievement Overview' AS Subtitle,
            CAST(ROUND(CAST(p.Achieved AS FLOAT) * 100.0 / p.TargetVal, 1) AS NVARCHAR) + '%' AS Tag,
            'Target: ' + CAST(p.TargetVal AS NVARCHAR) AS Value1,
            'Achieved: ' + CAST(p.Achieved AS NVARCHAR) AS Value2,
            'Remaining: ' + CAST(CASE WHEN p.TargetVal > p.Achieved THEN p.TargetVal - p.Achieved ELSE 0 END AS NVARCHAR) AS Value3,
            'Over: ' + CAST(CASE WHEN p.Achieved > p.TargetVal THEN p.Achieved - p.TargetVal ELSE 0 END AS NVARCHAR) AS Value4,
            'KPI' AS Status,
            @Now AS CreatedAtUtc
        FROM (
            SELECT 'Daily' AS PeriodType,
                   ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Daily' AND IsActive = 1 ORDER BY UserId DESC), 30) AS TargetVal,
                   (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= @TodayStart) AS Achieved
            UNION ALL
            SELECT 'Weekly' AS PeriodType,
                   ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Weekly' AND IsActive = 1 ORDER BY UserId DESC), 150) AS TargetVal,
                   (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= DATEADD(DAY, -7, @TodayStart)) AS Achieved
            UNION ALL
            SELECT 'Monthly' AS PeriodType,
                   ISNULL((SELECT TOP 1 FollowUpTarget FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND (UserId = @UserId OR UserId IS NULL) AND PeriodType = 'Monthly' AND IsActive = 1 ORDER BY UserId DESC), 600) AS TargetVal,
                   (SELECT COUNT(*) FROM dbo.myonline_tbl_CRM_LeadFollowUps WHERE CompanyId = @CompanyId AND CreatedByUserId = @UserId AND FollowUpDateUtc >= DATEADD(MONTH, -1, @TodayStart)) AS Achieved
        ) p
        WHERE @ReportType = 6

        UNION ALL
        -- 7. My Productivity
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CAST(f.FollowUpDateUtc AS DATE) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CAST(f.FollowUpDateUtc AS DATE), 'dd MMM yyyy') AS Title,
            'Follow-ups Done: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN f.Status = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Target: 30' AS Value3,
            'Score: ' + CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / 30, 1) AS NVARCHAR) + '%' AS Value4,
            'Productivity' AS Status,
            MAX(f.CreatedAtUtc) AS CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        WHERE @ReportType = 7 AND f.CompanyId = @CompanyId AND f.CreatedByUserId = @UserId
        GROUP BY CAST(f.FollowUpDateUtc AS DATE)

        UNION ALL
        -- 8. My Product/Service-wise Leads
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            ProductServiceId AS EntityId,
            ISNULL(ProductName, 'General Product') AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won' AS Tag,
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value1,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Product' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 8
        GROUP BY ProductServiceId, ProductName

        UNION ALL
        -- 9. My Lead Source-wise Leads
        SELECT 
            ROW_NUMBER() OVER(ORDER BY COUNT(*) DESC) AS RowId,
            LeadSourceId AS EntityId,
            ISNULL(SourceName, 'Self / Direct') AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%' AS Value4,
            'Source' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 9
        GROUP BY LeadSourceId, SourceName

        UNION ALL
        -- 10. My Conversion Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY ml.CreatedAtUtc DESC) AS RowId,
            ml.LeadId AS EntityId,
            ml.LeadName AS Title,
            'Contact: ' + ISNULL(ml.ContactPerson, 'N/A') AS Subtitle,
            'CLOSED' AS Tag,
            'Value: ৳' + CAST(ISNULL(ml.EstimatedValue, 0) AS NVARCHAR) AS Value1,
            'Product: ' + ISNULL(ml.ProductName, 'N/A') AS Value2,
            'Won Date: ' + FORMAT(ml.LastFollowUpDate, 'dd MMM yyyy') AS Value3,
            'Source: ' + ISNULL(ml.SourceName, 'Self') AS Value4,
            'Closed' AS Status,
            ml.CreatedAtUtc
        FROM MyLeads ml
        WHERE @ReportType = 10 AND ml.LeadStatus = 'Closed'

        UNION ALL
        -- 11. Daily Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY CAST(CreatedAtUtc AS DATE) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy') AS Title,
            'Created: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Daily' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 11
        GROUP BY CAST(CreatedAtUtc AS DATE)

        UNION ALL
        -- 12. Weekly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR) AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Weekly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 12
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)

        UNION ALL
        -- 13. Monthly Performance
        SELECT 
            ROW_NUMBER() OVER(ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC) AS RowId,
            0 AS EntityId,
            FORMAT(CreatedAtUtc, 'MMMM yyyy') AS Title,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR) AS Subtitle,
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed' AS Tag,
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value1,
            'Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR) AS Value2,
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR) AS Value3,
            'Conv: ' + CAST(ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) AS NVARCHAR) + '%' AS Value4,
            'Monthly' AS Status,
            MAX(CreatedAtUtc) AS CreatedAtUtc
        FROM MyLeads
        WHERE @ReportType = 13
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
    )
    -- RESULT SET 1: REPORT SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM ReportRows) AS TotalRows,
        'My Total' AS Summary1Label,
        CAST((SELECT COUNT(*) FROM ReportRows) AS NVARCHAR) AS Summary1Value,
        'My Won' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM MyLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'My Conv Rate' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM MyLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM MyLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / (SELECT COUNT(*) FROM MyLeads), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- RESULT SET 2: PAGINATED ROWS
    SELECT *
    FROM ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO
PRINT '✅ Created Stored Procedure dbo.sp_Crm_GetUserReports';
GO
