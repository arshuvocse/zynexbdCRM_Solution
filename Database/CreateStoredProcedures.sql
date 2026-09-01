/* ===========================================================
   CreateStoredProcedures.sql
   Database: LiveTrackingDb
   =========================================================== */
USE LiveTrackingDb;
GO

/* -----------------------------------------------------------
   1. sp_InsertLocationPing
   ----------------------------------------------------------- */
IF OBJECT_ID(N'dbo.sp_InsertLocationPing', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_InsertLocationPing;
GO
CREATE PROCEDURE dbo.sp_InsertLocationPing
    @UserId         INT,
    @Latitude       FLOAT,
    @Longitude      FLOAT,
    @Accuracy       FLOAT = NULL,
    @Speed          FLOAT = NULL,
    @Bearing        FLOAT = NULL,
    @RecordedAtUtc  DATETIME2
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO dbo.DriverLocations (UserId, Latitude, Longitude, Accuracy, Speed, Bearing, RecordedAtUtc)
    VALUES (@UserId, @Latitude, @Longitude, @Accuracy, @Speed, @Bearing, @RecordedAtUtc);

    SELECT SCOPE_IDENTITY() AS LocationId;
END
GO

/* -----------------------------------------------------------
   2. sp_GetLatestLocationPerUser
   ----------------------------------------------------------- */
IF OBJECT_ID(N'dbo.sp_GetLatestLocationPerUser', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_GetLatestLocationPerUser;
GO
CREATE PROCEDURE dbo.sp_GetLatestLocationPerUser
AS
BEGIN
    SET NOCOUNT ON;
    ;WITH LatestPing AS
    (
        SELECT dl.*, ROW_NUMBER() OVER (PARTITION BY dl.UserId ORDER BY dl.RecordedAtUtc DESC) AS rn
        FROM dbo.DriverLocations dl
    )
    SELECT u.UserId, u.Username, u.FullName, u.Role, u.IsActive,
           lp.Latitude, lp.Longitude, lp.Accuracy, lp.Speed, lp.Bearing, lp.RecordedAtUtc
    FROM dbo.Users u
    LEFT JOIN LatestPing lp ON lp.UserId = u.UserId AND lp.rn = 1
    WHERE u.Role = 'User';
END
GO

/* -----------------------------------------------------------
   3. sp_GetLatestLocationByUserId
   ----------------------------------------------------------- */
IF OBJECT_ID(N'dbo.sp_GetLatestLocationByUserId', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_GetLatestLocationByUserId;
GO
CREATE PROCEDURE dbo.sp_GetLatestLocationByUserId
    @UserId INT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT TOP (1) *
    FROM dbo.DriverLocations
    WHERE UserId = @UserId
    ORDER BY RecordedAtUtc DESC;
END
GO

/* -----------------------------------------------------------
   4. sp_GetAttendanceReportByMonthYear (Attendance Report SP)
   Supports filtering by UserId, AdminId, Month, Year, Date Range
   ----------------------------------------------------------- */
IF OBJECT_ID(N'dbo.sp_GetAttendanceReportByMonthYear', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_GetAttendanceReportByMonthYear;
GO
CREATE PROCEDURE dbo.sp_GetAttendanceReportByMonthYear
    @UserId     INT = NULL,          -- NULL for all employees
    @AdminId    INT = NULL,          -- Subordinates under this admin (optional)
    @Month      INT = NULL,          -- 1 to 12 (NULL for all months)
    @Year       INT = NULL,          -- e.g. 2026 (NULL for all years)
    @FromDate   DATETIME2 = NULL,    -- Custom From Date
    @ToDate     DATETIME2 = NULL     -- Custom To Date
AS
BEGIN
    SET NOCOUNT ON;

    SELECT 
        a.AttendanceId,
        a.UserId,
        ISNULL(u.FullName, u.Username) AS FullName,
        u.Username,
        u.Role,
        u.PhoneNumber,
        a.Type,                      -- 'In' or 'Out'
        a.RecordedAtUtc,
        a.Latitude,
        a.Longitude,
        a.IsWithinGeofence,
        a.SelfieUrl
    FROM dbo.AttendanceRecords a
    INNER JOIN dbo.Users u ON u.UserId = a.UserId
    WHERE 
        (@UserId IS NULL OR a.UserId = @UserId)
        AND (@AdminId IS NULL OR u.CreatedByAdminId = @AdminId OR u.UserId = @AdminId)
        AND (@Year IS NULL OR YEAR(a.RecordedAtUtc) = @Year)
        AND (@Month IS NULL OR MONTH(a.RecordedAtUtc) = @Month)
        AND (@FromDate IS NULL OR a.RecordedAtUtc >= @FromDate)
        AND (@ToDate IS NULL OR a.RecordedAtUtc <= @ToDate)
    ORDER BY a.RecordedAtUtc DESC;
END
GO

/* -----------------------------------------------------------
   5. sp_GetMonthlyAttendanceSummary (Aggregated Monthly Stats SP)
   ----------------------------------------------------------- */
IF OBJECT_ID(N'dbo.sp_GetMonthlyAttendanceSummary', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_GetMonthlyAttendanceSummary;
GO
CREATE PROCEDURE dbo.sp_GetMonthlyAttendanceSummary
    @UserId     INT = NULL,
    @Month      INT = NULL,
    @Year       INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    SELECT 
        u.UserId,
        ISNULL(u.FullName, u.Username) AS FullName,
        u.Username,
        ISNULL(@Year, YEAR(SYSUTCDATETIME())) AS ReportYear,
        ISNULL(@Month, MONTH(SYSUTCDATETIME())) AS ReportMonth,
        COUNT(CASE WHEN a.Type = 'In' THEN 1 END) AS TotalDutyIn,
        COUNT(CASE WHEN a.Type = 'Out' THEN 1 END) AS TotalDutyOut,
        COUNT(DISTINCT CAST(a.RecordedAtUtc AS DATE)) AS TotalWorkingDays,
        COUNT(CASE WHEN a.IsWithinGeofence = 1 THEN 1 END) AS TotalWithinOffice,
        COUNT(CASE WHEN a.IsWithinGeofence = 0 THEN 1 END) AS TotalOutsideOffice
    FROM dbo.Users u
    LEFT JOIN dbo.AttendanceRecords a ON a.UserId = u.UserId
        AND (@Year IS NULL OR YEAR(a.RecordedAtUtc) = @Year)
        AND (@Month IS NULL OR MONTH(a.RecordedAtUtc) = @Month)
    WHERE (@UserId IS NULL OR u.UserId = @UserId)
    GROUP BY u.UserId, u.FullName, u.Username;
END
GO
