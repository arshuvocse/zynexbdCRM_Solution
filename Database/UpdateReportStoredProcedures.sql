
USE [crm_solution_DB];
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

/* ==================================================================================
   4. STORED PROCEDURE: sp_Crm_GetAdminReports
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

    CREATE TABLE #FilteredLeads (
        LeadId INT,
        CompanyId INT,
        OfficeLocationId INT,
        LeadName NVARCHAR(200),
        ContactPerson NVARCHAR(100),
        Phone NVARCHAR(50),
        Email NVARCHAR(100),
        ProductServiceId INT,
        LeadSourceId INT,
        LeadStatus NVARCHAR(50),
        CreatedByUserId INT,
        AssignedUserId INT,
        NextFollowUpDate DATETIME2,
        LastFollowUpDate DATETIME2,
        EstimatedValue DECIMAL(18,2),
        CreatedAtUtc DATETIME2,
        ProductName NVARCHAR(150),
        SourceName NVARCHAR(100),
        OfficeName NVARCHAR(100),
        CreatedByName NVARCHAR(100),
        AssignedByName NVARCHAR(100),
        ManagerUserId INT
    );

    INSERT INTO #FilteredLeads
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
      AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%');

    CREATE TABLE #ReportRows (
        RowId INT IDENTITY(1,1),
        EntityId INT,
        Title NVARCHAR(200),
        Subtitle NVARCHAR(200),
        Tag NVARCHAR(50),
        Value1 NVARCHAR(100),
        Value2 NVARCHAR(100),
        Value3 NVARCHAR(100),
        Value4 NVARCHAR(100),
        Status NVARCHAR(50),
        CreatedAtUtc DATETIME2
    );

    IF @ReportType = 1
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            LeadId, LeadName,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone'),
            LeadStatus,
            ISNULL(ProductName, 'N/A'),
            ISNULL(AssignedByName, 'Unassigned'),
            ISNULL(OfficeName, 'Headquarters'),
            FORMAT(CreatedAtUtc, 'dd MMM yyyy'),
            LeadStatus, CreatedAtUtc
        FROM #FilteredLeads
        ORDER BY CreatedAtUtc DESC;
    END
    ELSE IF @ReportType = 2
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            OfficeLocationId,
            ISNULL(OfficeName, 'Unassigned Office'),
            'Total Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Rate: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Active', MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY OfficeLocationId, OfficeName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 3
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            ManagerUserId,
            ISNULL((SELECT TOP 1 ISNULL(Name, Username) FROM dbo.myonline_tbl_Users WHERE Id = fl.ManagerUserId), 'Direct/Admin'),
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conversion: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Manager', MAX(CreatedAtUtc)
        FROM #FilteredLeads fl
        GROUP BY ManagerUserId
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 4
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            AssignedUserId,
            ISNULL(AssignedByName, 'Unassigned'),
            'Assigned Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Office: ' + ISNULL(OfficeName, 'N/A'),
            'Conv Rate: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'User', MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY AssignedUserId, AssignedByName, OfficeName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 5
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            ProductServiceId,
            ISNULL(ProductName, 'General Product'),
            'Total Inquiries: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'In Progress: ' + CAST(SUM(CASE WHEN LeadStatus IN ('Follow Up', 'New Lead') THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Active', MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY ProductServiceId, ProductName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 6
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            LeadSourceId,
            ISNULL(SourceName, 'Direct / Self'),
            'Generated Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Converted',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Source', MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY LeadSourceId, SourceName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 7
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, LeadStatus,
            'Total: ' + CAST(COUNT(*) AS NVARCHAR) + ' Leads',
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / NULLIF((SELECT COUNT(*) FROM #FilteredLeads), 0), 1) AS NVARCHAR) + '%',
            'Office: ' + ISNULL(OfficeName, 'All'),
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'Assigned: ' + CAST(SUM(CASE WHEN AssignedUserId IS NOT NULL THEN 1 ELSE 0 END) AS NVARCHAR),
            'Pending: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR),
            LeadStatus, MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY LeadStatus, OfficeName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 8
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            f.FollowUpId, fl.LeadName,
            'Follow-up By: ' + ISNULL(u.Name, u.Username),
            f.Status,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy HH:mm'),
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None'),
            'Lead Status: ' + fl.LeadStatus,
            'Remarks: ' + LEFT(f.Remarks, 40),
            f.Status, f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN #FilteredLeads fl ON f.LeadId = fl.LeadId
        LEFT JOIN dbo.myonline_tbl_Users u ON f.CreatedByUserId = u.Id
        ORDER BY f.FollowUpDateUtc DESC;
    END
    ELSE IF @ReportType = 9
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            fl.LeadId, fl.LeadName,
            'Assigned: ' + ISNULL(fl.AssignedByName, 'Unassigned'),
            'OVERDUE',
            'Due: ' + FORMAT(fl.NextFollowUpDate, 'dd MMM yyyy'),
            'Contact: ' + ISNULL(fl.Phone, 'N/A'),
            'Office: ' + ISNULL(fl.OfficeName, 'N/A'),
            'Status: ' + fl.LeadStatus,
            'Overdue', fl.CreatedAtUtc
        FROM #FilteredLeads fl
        WHERE fl.NextFollowUpDate IS NOT NULL 
          AND fl.NextFollowUpDate < @TodayStart 
          AND fl.LeadStatus NOT IN ('Closed', 'Not Interested')
        ORDER BY fl.NextFollowUpDate ASC;
    END
    ELSE IF @ReportType = 10
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            k.KpiId,
            ISNULL(u.Name, 'Company Default'),
            k.PeriodType + ' Target',
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups',
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR),
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR),
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy'),
            'Office: ' + ISNULL(o.Name, 'All Offices'),
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        LEFT JOIN dbo.myonline_tbl_Users u ON k.UserId = u.Id
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON k.OfficeLocationId = o.Id
        WHERE k.CompanyId = @CompanyId
        ORDER BY k.EffectiveStartDate DESC;
    END
    ELSE IF @ReportType = 11
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            u.Id,
            ISNULL(u.Name, u.Username),
            ISNULL(o.Name, 'Main Office'),
            CAST(COUNT(f.FollowUpId) AS NVARCHAR) + ' Follow-ups',
            'Target: ' + CAST(ISNULL(k.FollowUpTarget, 30) AS NVARCHAR),
            'Achieved: ' + CAST(CASE WHEN ISNULL(k.FollowUpTarget, 0) > 0 THEN ROUND(CAST(COUNT(f.FollowUpId) AS FLOAT) * 100.0 / k.FollowUpTarget, 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Closed: ' + CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Productive', MAX(u.CreatedAt)
        FROM dbo.myonline_tbl_Users u
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON u.OfficeLocationId = o.Id
        LEFT JOIN dbo.myonline_tbl_CRM_KPI k ON k.UserId = u.Id AND k.CompanyId = @CompanyId AND k.PeriodType = 'Daily' AND k.IsActive = 1
        LEFT JOIN dbo.myonline_tbl_CRM_LeadFollowUps f ON f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId
        WHERE u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
        GROUP BY u.Id, u.Name, u.Username, o.Name, k.FollowUpTarget
        ORDER BY COUNT(f.FollowUpId) DESC;
    END
    ELSE IF @ReportType = 12
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            fl.LeadId, fl.LeadName,
            'Assigned: ' + ISNULL(fl.AssignedByName, 'Unassigned'),
            'WON',
            'Product: ' + ISNULL(fl.ProductName, 'N/A'),
            'Value: ৳' + CAST(ISNULL(fl.EstimatedValue, 0) AS NVARCHAR),
            'Source: ' + ISNULL(fl.SourceName, 'Direct'),
            'Converted: ' + FORMAT(fl.LastFollowUpDate, 'dd MMM yyyy'),
            'Closed', fl.CreatedAtUtc
        FROM #FilteredLeads fl
        WHERE fl.LeadStatus = 'Closed'
        ORDER BY fl.CreatedAtUtc DESC;
    END
    ELSE IF @ReportType = 13
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy'),
            'New Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Not Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Not Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Trend', MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY CAST(CreatedAtUtc AS DATE)
        ORDER BY CAST(CreatedAtUtc AS DATE) DESC;
    END
    ELSE IF @ReportType = 14
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, 'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR),
            'Leads Count: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Trend', MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)
        ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC;
    END
    ELSE IF @ReportType = 15
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, FORMAT(CreatedAtUtc, 'MMMM yyyy'),
            'Monthly Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Est Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Trend', MAX(CreatedAtUtc)
        FROM #FilteredLeads
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
        ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC;
    END

    -- 1. SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM #ReportRows) AS TotalRows,
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
        CAST((SELECT COUNT(*) FROM #ReportRows) AS NVARCHAR) AS Summary1Value,
        'Closed Leads' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM #FilteredLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'Conversion Rate' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM #FilteredLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM #FilteredLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / NULLIF((SELECT COUNT(*) FROM #FilteredLeads), 0), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- 2. PAGINATED ROWS
    SELECT * FROM #ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

    DROP TABLE #FilteredLeads;
    DROP TABLE #ReportRows;
END
GO
PRINT '✅ Replaced Stored Procedure dbo.sp_Crm_GetAdminReports';
GO

/* ==================================================================================
   5. STORED PROCEDURE: sp_Crm_GetManagerReports
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

    CREATE TABLE #TeamLeads (
        LeadId INT,
        CompanyId INT,
        OfficeLocationId INT,
        LeadName NVARCHAR(200),
        ContactPerson NVARCHAR(100),
        Phone NVARCHAR(50),
        Email NVARCHAR(100),
        ProductServiceId INT,
        LeadSourceId INT,
        LeadStatus NVARCHAR(50),
        CreatedByUserId INT,
        AssignedUserId INT,
        NextFollowUpDate DATETIME2,
        LastFollowUpDate DATETIME2,
        EstimatedValue DECIMAL(18,2),
        CreatedAtUtc DATETIME2,
        ProductName NVARCHAR(150),
        SourceName NVARCHAR(100),
        OfficeName NVARCHAR(100),
        CreatedByName NVARCHAR(100),
        AssignedByName NVARCHAR(100)
    );

    INSERT INTO #TeamLeads
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
      AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%');

    CREATE TABLE #ReportRows (
        RowId INT IDENTITY(1,1),
        EntityId INT,
        Title NVARCHAR(200),
        Subtitle NVARCHAR(200),
        Tag NVARCHAR(50),
        Value1 NVARCHAR(100),
        Value2 NVARCHAR(100),
        Value3 NVARCHAR(100),
        Value4 NVARCHAR(100),
        Status NVARCHAR(50),
        CreatedAtUtc DATETIME2
    );

    IF @ReportType = 1
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            LeadId, LeadName,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone'),
            LeadStatus,
            ISNULL(ProductName, 'N/A'),
            ISNULL(AssignedByName, 'Unassigned'),
            ISNULL(OfficeName, 'Branch Office'),
            FORMAT(CreatedAtUtc, 'dd MMM yyyy'),
            LeadStatus, CreatedAtUtc
        FROM #TeamLeads
        ORDER BY CreatedAtUtc DESC;
    END
    ELSE IF @ReportType = 2
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            AssignedUserId,
            ISNULL(AssignedByName, 'Unassigned'),
            'Assigned Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Employee', MAX(CreatedAtUtc)
        FROM #TeamLeads
        GROUP BY AssignedUserId, AssignedByName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 3
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            u.Id,
            ISNULL(u.Name, u.Username),
            'Team Member',
            CAST(COUNT(f.FollowUpId) AS NVARCHAR) + ' Follow-ups',
            'Target: ' + CAST(ISNULL(k.FollowUpTarget, 30) AS NVARCHAR),
            'Achieved: ' + CAST(CASE WHEN ISNULL(k.FollowUpTarget, 0) > 0 THEN ROUND(CAST(COUNT(f.FollowUpId) AS FLOAT) * 100.0 / k.FollowUpTarget, 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Closed: ' + CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Productive', MAX(u.CreatedAt)
        FROM dbo.myonline_tbl_Users u
        LEFT JOIN dbo.myonline_tbl_CRM_KPI k ON k.UserId = u.Id AND k.CompanyId = @CompanyId AND k.PeriodType = 'Daily' AND k.IsActive = 1
        LEFT JOIN dbo.myonline_tbl_CRM_LeadFollowUps f ON f.CreatedByUserId = u.Id AND f.CompanyId = @CompanyId
        WHERE u.CompanyId = @CompanyId AND u.Role IN ('User', 'Employee') AND u.IsActive = 1
          AND (u.CreatedByAdminId = @ManagerUserId OR u.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices))
        GROUP BY u.Id, u.Name, u.Username, k.FollowUpTarget
        ORDER BY COUNT(f.FollowUpId) DESC;
    END
    ELSE IF @ReportType = 4
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            k.KpiId,
            ISNULL(u.Name, 'Office Default'),
            k.PeriodType + ' Target',
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups',
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR),
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR),
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy'),
            'Office: ' + ISNULL((SELECT Name FROM dbo.myonline_tbl_OfficeLocations WHERE Id = k.OfficeLocationId), 'Team Office'),
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        LEFT JOIN dbo.myonline_tbl_Users u ON k.UserId = u.Id
        WHERE k.CompanyId = @CompanyId
          AND (k.OfficeLocationId IN (SELECT OfficeId FROM @AllowedOffices) OR u.CreatedByAdminId = @ManagerUserId)
        ORDER BY k.EffectiveStartDate DESC;
    END
    ELSE IF @ReportType = 5
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            f.FollowUpId, tl.LeadName,
            'Done By: ' + ISNULL(u.Name, u.Username),
            f.Status,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy'),
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None'),
            'Remarks: ' + LEFT(f.Remarks, 30),
            'Status: ' + tl.LeadStatus,
            f.Status, f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN #TeamLeads tl ON f.LeadId = tl.LeadId
        LEFT JOIN dbo.myonline_tbl_Users u ON f.CreatedByUserId = u.Id
        ORDER BY f.FollowUpDateUtc DESC;
    END
    ELSE IF @ReportType = 6
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            tl.LeadId, tl.LeadName,
            'Assigned: ' + ISNULL(tl.AssignedByName, 'Unassigned'),
            'OVERDUE',
            'Due: ' + FORMAT(tl.NextFollowUpDate, 'dd MMM yyyy'),
            'Phone: ' + ISNULL(tl.Phone, 'N/A'),
            'Product: ' + ISNULL(tl.ProductName, 'N/A'),
            'Status: ' + tl.LeadStatus,
            'Overdue', tl.CreatedAtUtc
        FROM #TeamLeads tl
        WHERE tl.NextFollowUpDate IS NOT NULL 
          AND tl.NextFollowUpDate < @TodayStart 
          AND tl.LeadStatus NOT IN ('Closed', 'Not Interested')
        ORDER BY tl.NextFollowUpDate ASC;
    END
    ELSE IF @ReportType = 7
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, LeadStatus,
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / NULLIF((SELECT COUNT(*) FROM #TeamLeads), 0), 1) AS NVARCHAR) + '%',
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'Assigned: ' + CAST(SUM(CASE WHEN AssignedUserId IS NOT NULL THEN 1 ELSE 0 END) AS NVARCHAR),
            'Pending: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR),
            'Branch: ' + ISNULL(OfficeName, 'Local'),
            LeadStatus, MAX(CreatedAtUtc)
        FROM #TeamLeads
        GROUP BY LeadStatus, OfficeName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 8
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            ProductServiceId,
            ISNULL(ProductName, 'General'),
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Est Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'In Progress: ' + CAST(SUM(CASE WHEN LeadStatus IN ('Follow Up', 'New Lead') THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Product', MAX(CreatedAtUtc)
        FROM #TeamLeads
        GROUP BY ProductServiceId, ProductName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 9
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            LeadSourceId,
            ISNULL(SourceName, 'Direct / Self'),
            'Team Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Source', MAX(CreatedAtUtc)
        FROM #TeamLeads
        GROUP BY LeadSourceId, SourceName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 10
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            tl.LeadId, tl.LeadName,
            'Owner: ' + ISNULL(tl.AssignedByName, 'Unassigned'),
            'WON',
            'Value: ৳' + CAST(ISNULL(tl.EstimatedValue, 0) AS NVARCHAR),
            'Product: ' + ISNULL(tl.ProductName, 'N/A'),
            'Source: ' + ISNULL(tl.SourceName, 'Direct'),
            'Won Date: ' + FORMAT(tl.LastFollowUpDate, 'dd MMM yyyy'),
            'Closed', tl.CreatedAtUtc
        FROM #TeamLeads tl
        WHERE tl.LeadStatus = 'Closed'
        ORDER BY tl.CreatedAtUtc DESC;
    END
    ELSE IF @ReportType = 11
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy'),
            'Daily Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Rate: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Daily', MAX(CreatedAtUtc)
        FROM #TeamLeads
        GROUP BY CAST(CreatedAtUtc AS DATE)
        ORDER BY CAST(CreatedAtUtc AS DATE) DESC;
    END
    ELSE IF @ReportType = 12
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, 'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR),
            'Weekly Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Rate: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Weekly', MAX(CreatedAtUtc)
        FROM #TeamLeads
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)
        ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC;
    END
    ELSE IF @ReportType = 13
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, FORMAT(CreatedAtUtc, 'MMMM yyyy'),
            'Monthly Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Rate: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Monthly', MAX(CreatedAtUtc)
        FROM #TeamLeads
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
        ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC;
    END

    -- 1. SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM #ReportRows) AS TotalRows,
        'Team Total' AS Summary1Label,
        CAST((SELECT COUNT(*) FROM #ReportRows) AS NVARCHAR) AS Summary1Value,
        'Team Won' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM #TeamLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'Team Conv' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM #TeamLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM #TeamLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / NULLIF((SELECT COUNT(*) FROM #TeamLeads), 0), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- 2. PAGINATED ROWS
    SELECT * FROM #ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

    DROP TABLE #TeamLeads;
    DROP TABLE #ReportRows;
END
GO
PRINT '✅ Replaced Stored Procedure dbo.sp_Crm_GetManagerReports';
GO

/* ==================================================================================
   6. STORED PROCEDURE: sp_Crm_GetUserReports
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

    CREATE TABLE #MyLeads (
        LeadId INT,
        CompanyId INT,
        OfficeLocationId INT,
        LeadName NVARCHAR(200),
        ContactPerson NVARCHAR(100),
        Phone NVARCHAR(50),
        Email NVARCHAR(100),
        ProductServiceId INT,
        LeadSourceId INT,
        LeadStatus NVARCHAR(50),
        CreatedByUserId INT,
        AssignedUserId INT,
        NextFollowUpDate DATETIME2,
        LastFollowUpDate DATETIME2,
        EstimatedValue DECIMAL(18,2),
        CreatedAtUtc DATETIME2,
        ProductName NVARCHAR(150),
        SourceName NVARCHAR(100),
        OfficeName NVARCHAR(100)
    );

    INSERT INTO #MyLeads
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
      AND (@Search IS NULL OR l.LeadName LIKE '%' + @Search + '%' OR l.ContactPerson LIKE '%' + @Search + '%' OR l.Phone LIKE '%' + @Search + '%');

    CREATE TABLE #ReportRows (
        RowId INT IDENTITY(1,1),
        EntityId INT,
        Title NVARCHAR(200),
        Subtitle NVARCHAR(200),
        Tag NVARCHAR(50),
        Value1 NVARCHAR(100),
        Value2 NVARCHAR(100),
        Value3 NVARCHAR(100),
        Value4 NVARCHAR(100),
        Status NVARCHAR(50),
        CreatedAtUtc DATETIME2
    );

    IF @ReportType = 1
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            LeadId, LeadName,
            ISNULL(ContactPerson, 'No Contact') + ' | ' + ISNULL(Phone, 'No Phone'),
            LeadStatus,
            ISNULL(ProductName, 'N/A'),
            'Est: ৳' + CAST(ISNULL(EstimatedValue, 0) AS NVARCHAR),
            ISNULL(SourceName, 'Self'),
            FORMAT(CreatedAtUtc, 'dd MMM yyyy'),
            LeadStatus, CreatedAtUtc
        FROM #MyLeads
        ORDER BY CreatedAtUtc DESC;
    END
    ELSE IF @ReportType = 2
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, LeadStatus,
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / NULLIF((SELECT COUNT(*) FROM #MyLeads), 0), 1) AS NVARCHAR) + '%',
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'Pending: ' + CAST(SUM(CASE WHEN NextFollowUpDate >= @TodayStart THEN 1 ELSE 0 END) AS NVARCHAR),
            'Overdue: ' + CAST(SUM(CASE WHEN NextFollowUpDate < @TodayStart AND LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 ELSE 0 END) AS NVARCHAR),
            'Office: ' + ISNULL(OfficeName, 'Local'),
            LeadStatus, MAX(CreatedAtUtc)
        FROM #MyLeads
        GROUP BY LeadStatus, OfficeName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 3
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            f.FollowUpId, ml.LeadName,
            'Follow-up Status: ' + f.Status,
            f.Status,
            'Date: ' + FORMAT(f.FollowUpDateUtc, 'dd MMM yyyy'),
            'Next: ' + ISNULL(FORMAT(f.NextFollowUpDate, 'dd MMM yyyy'), 'None'),
            'Remarks: ' + LEFT(f.Remarks, 30),
            'Lead: ' + ml.LeadStatus,
            f.Status, f.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        INNER JOIN #MyLeads ml ON f.LeadId = ml.LeadId
        WHERE f.CreatedByUserId = @UserId
        ORDER BY f.FollowUpDateUtc DESC;
    END
    ELSE IF @ReportType = 4
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            ml.LeadId, ml.LeadName,
            'Contact: ' + ISNULL(ml.ContactPerson, 'N/A') + ' (' + ISNULL(ml.Phone, 'N/A') + ')',
            'OVERDUE',
            'Due Date: ' + FORMAT(ml.NextFollowUpDate, 'dd MMM yyyy'),
            'Product: ' + ISNULL(ml.ProductName, 'N/A'),
            'Status: ' + ml.LeadStatus,
            'Est: ৳' + CAST(ISNULL(ml.EstimatedValue, 0) AS NVARCHAR),
            'Overdue', ml.CreatedAtUtc
        FROM #MyLeads ml
        WHERE ml.NextFollowUpDate IS NOT NULL 
          AND ml.NextFollowUpDate < @TodayStart 
          AND ml.LeadStatus NOT IN ('Closed', 'Not Interested')
        ORDER BY ml.NextFollowUpDate ASC;
    END
    ELSE IF @ReportType = 5
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            k.KpiId,
            k.PeriodType + ' KPI Goal',
            'Target Setting',
            CAST(k.FollowUpTarget AS NVARCHAR) + ' Follow-ups',
            'Interested: ' + CAST(k.InterestedTarget AS NVARCHAR),
            'Closed: ' + CAST(k.ClosedTarget AS NVARCHAR),
            'Effective: ' + FORMAT(k.EffectiveStartDate, 'dd MMM yyyy'),
            'Status: ' + CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END,
            CASE WHEN k.IsActive = 1 THEN 'Active' ELSE 'Inactive' END,
            k.CreatedAtUtc
        FROM dbo.myonline_tbl_CRM_KPI k
        WHERE k.CompanyId = @CompanyId AND (k.UserId = @UserId OR k.UserId IS NULL)
        ORDER BY k.EffectiveStartDate DESC;
    END
    ELSE IF @ReportType = 6
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, p.PeriodType + ' Performance',
            'Achievement Overview',
            CAST(CASE WHEN ISNULL(p.TargetVal, 0) > 0 THEN ROUND(CAST(p.Achieved AS FLOAT) * 100.0 / p.TargetVal, 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Target: ' + CAST(p.TargetVal AS NVARCHAR),
            'Achieved: ' + CAST(p.Achieved AS NVARCHAR),
            'Remaining: ' + CAST(CASE WHEN p.TargetVal > p.Achieved THEN p.TargetVal - p.Achieved ELSE 0 END AS NVARCHAR),
            'Over: ' + CAST(CASE WHEN p.Achieved > p.TargetVal THEN p.Achieved - p.TargetVal ELSE 0 END AS NVARCHAR),
            'KPI', @Now
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
        ) p;
    END
    ELSE IF @ReportType = 7
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, FORMAT(CAST(f.FollowUpDateUtc AS DATE), 'dd MMM yyyy'),
            'Follow-ups Done: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN f.Status = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN f.Status = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow Up: ' + CAST(SUM(CASE WHEN f.Status = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Target: 30',
            'Score: ' + CAST(ROUND(CAST(COUNT(*) AS FLOAT) * 100.0 / 30, 1) AS NVARCHAR) + '%',
            'Productivity', MAX(f.CreatedAtUtc)
        FROM dbo.myonline_tbl_CRM_LeadFollowUps f
        WHERE f.CompanyId = @CompanyId AND f.CreatedByUserId = @UserId
        GROUP BY CAST(f.FollowUpDateUtc AS DATE)
        ORDER BY CAST(f.FollowUpDateUtc AS DATE) DESC;
    END
    ELSE IF @ReportType = 8
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            ProductServiceId,
            ISNULL(ProductName, 'General Product'),
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Won',
            'Est Value: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Product', MAX(CreatedAtUtc)
        FROM #MyLeads
        GROUP BY ProductServiceId, ProductName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 9
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            LeadSourceId,
            ISNULL(SourceName, 'Self / Direct'),
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Source', MAX(CreatedAtUtc)
        FROM #MyLeads
        GROUP BY LeadSourceId, SourceName
        ORDER BY COUNT(*) DESC;
    END
    ELSE IF @ReportType = 10
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            ml.LeadId, ml.LeadName,
            'Contact: ' + ISNULL(ml.ContactPerson, 'N/A'),
            'CLOSED',
            'Value: ৳' + CAST(ISNULL(ml.EstimatedValue, 0) AS NVARCHAR),
            'Product: ' + ISNULL(ml.ProductName, 'N/A'),
            'Won Date: ' + FORMAT(ml.LastFollowUpDate, 'dd MMM yyyy'),
            'Source: ' + ISNULL(ml.SourceName, 'Self'),
            'Closed', ml.CreatedAtUtc
        FROM #MyLeads ml
        WHERE ml.LeadStatus = 'Closed'
        ORDER BY ml.CreatedAtUtc DESC;
    END
    ELSE IF @ReportType = 11
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, FORMAT(CAST(CreatedAtUtc AS DATE), 'dd MMM yyyy'),
            'Created: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow Up: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Daily', MAX(CreatedAtUtc)
        FROM #MyLeads
        GROUP BY CAST(CreatedAtUtc AS DATE)
        ORDER BY CAST(CreatedAtUtc AS DATE) DESC;
    END
    ELSE IF @ReportType = 12
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, 'Week ' + CAST(DATEPART(WEEK, CreatedAtUtc) AS NVARCHAR) + ', ' + CAST(YEAR(CreatedAtUtc) AS NVARCHAR),
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Follow-ups: ' + CAST(SUM(CASE WHEN LeadStatus = 'Follow Up' THEN 1 ELSE 0 END) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Weekly', MAX(CreatedAtUtc)
        FROM #MyLeads
        GROUP BY YEAR(CreatedAtUtc), DATEPART(WEEK, CreatedAtUtc)
        ORDER BY YEAR(CreatedAtUtc) DESC, DATEPART(WEEK, CreatedAtUtc) DESC;
    END
    ELSE IF @ReportType = 13
    BEGIN
        INSERT INTO #ReportRows (EntityId, Title, Subtitle, Tag, Value1, Value2, Value3, Value4, Status, CreatedAtUtc)
        SELECT 
            0, FORMAT(CreatedAtUtc, 'MMMM yyyy'),
            'My Leads: ' + CAST(COUNT(*) AS NVARCHAR),
            CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS NVARCHAR) + ' Closed',
            'Interested: ' + CAST(SUM(CASE WHEN LeadStatus = 'Interested' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Revenue: ৳' + CAST(ISNULL(SUM(EstimatedValue), 0) AS NVARCHAR),
            'New: ' + CAST(SUM(CASE WHEN LeadStatus = 'New Lead' THEN 1 ELSE 0 END) AS NVARCHAR),
            'Conv: ' + CAST(CASE WHEN COUNT(*) > 0 THEN ROUND(CAST(SUM(CASE WHEN LeadStatus = 'Closed' THEN 1 ELSE 0 END) AS FLOAT) * 100.0 / COUNT(*), 1) ELSE 0.0 END AS NVARCHAR) + '%',
            'Monthly', MAX(CreatedAtUtc)
        FROM #MyLeads
        GROUP BY YEAR(CreatedAtUtc), MONTH(CreatedAtUtc), FORMAT(CreatedAtUtc, 'MMMM yyyy')
        ORDER BY YEAR(CreatedAtUtc) DESC, MONTH(CreatedAtUtc) DESC;
    END

    -- 1. SUMMARY
    SELECT 
        (SELECT COUNT(*) FROM #ReportRows) AS TotalRows,
        'My Total' AS Summary1Label,
        CAST((SELECT COUNT(*) FROM #ReportRows) AS NVARCHAR) AS Summary1Value,
        'My Won' AS Summary2Label,
        CAST((SELECT COUNT(*) FROM #MyLeads WHERE LeadStatus = 'Closed') AS NVARCHAR) AS Summary2Value,
        'My Conv Rate' AS Summary3Label,
        CAST(CASE WHEN (SELECT COUNT(*) FROM #MyLeads) > 0 
                  THEN ROUND(CAST((SELECT COUNT(*) FROM #MyLeads WHERE LeadStatus = 'Closed') AS FLOAT) * 100.0 / NULLIF((SELECT COUNT(*) FROM #MyLeads), 0), 1) 
                  ELSE 0.0 END AS NVARCHAR) + '%' AS Summary3Value;

    -- 2. PAGINATED ROWS
    SELECT * FROM #ReportRows
    ORDER BY RowId ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

    DROP TABLE #MyLeads;
    DROP TABLE #ReportRows;
END
GO
PRINT '✅ Replaced Stored Procedure dbo.sp_Crm_GetUserReports';
GO
