/* ==================================================================================
   UNIFIED PRODUCTION CRM DATABASE MIGRATION SCRIPT
   Database: SalesDisDB_SMC_TrSalesRepor (or LiveTrackingDB)
   Tables prefix: myonline_tbl_*
   ================================================================================== */

USE [SalesDisDB_SMC_TrSalesRepor];
GO

-- 0. Ensure Database Compatibility Level is at least 130+ (150 for SQL 2019)
IF (SELECT compatibility_level FROM sys.databases WHERE name = DB_NAME()) < 130
BEGIN
    DECLARE @dbName NVARCHAR(128) = DB_NAME();
    EXEC('ALTER DATABASE [' + @dbName + '] SET COMPATIBILITY_LEVEL = 150');
    PRINT '✅ Updated database compatibility level to 150 (SQL Server 2019)';
END
GO

-- 1. Ensure Role Check Constraint on myonline_tbl_Users (Allow 'Manager' & 'FieldOfficer')
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'myonline_tbl_Users')
BEGIN
    DECLARE @chkName NVARCHAR(256);
    SELECT @chkName = name FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.myonline_tbl_Users') AND definition LIKE '%Role%';
    IF @chkName IS NOT NULL
    BEGIN
        EXEC('ALTER TABLE dbo.myonline_tbl_Users DROP CONSTRAINT [' + @chkName + ']');
    END

    ALTER TABLE dbo.myonline_tbl_Users WITH CHECK ADD CONSTRAINT CK_myonline_tbl_Users_Role 
    CHECK (Role IN ('Admin', 'Manager', 'FieldOfficer', 'User', 'Employee'));

    PRINT '✅ Updated Role constraint on dbo.myonline_tbl_Users';
END
GO

-- 2. Table: myonline_tbl_CRM_ProductServices
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_ProductServices', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_ProductServices
    (
        ProductServiceId INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        Name             NVARCHAR(150)       NOT NULL,
        Code             NVARCHAR(50)        NULL,
        Description      NVARCHAR(500)       NULL,
        Price            DECIMAL(18,2)       NULL,
        IsActive         BIT                 NOT NULL CONSTRAINT DF_CRM_ProductServices_IsActive DEFAULT (1),
        CreatedAtUtc     DATETIME2           NOT NULL CONSTRAINT DF_CRM_ProductServices_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        UpdatedAtUtc     DATETIME2           NULL,
        CONSTRAINT FK_CRM_ProductServices_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_ProductServices_CompanyId_Active
        ON dbo.myonline_tbl_CRM_ProductServices(CompanyId, IsActive);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_ProductServices';
END
GO

-- 3. Table: myonline_tbl_CRM_LeadSources
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadSources', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadSources
    (
        LeadSourceId INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId    INT                 NOT NULL,
        Name         NVARCHAR(100)       NOT NULL,
        IsSystem     BIT                 NOT NULL CONSTRAINT DF_CRM_LeadSources_IsSystem DEFAULT (0),
        IsActive     BIT                 NOT NULL CONSTRAINT DF_CRM_LeadSources_IsActive DEFAULT (1),
        CreatedAtUtc DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadSources_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_LeadSources_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadSources_CompanyId_Active
        ON dbo.myonline_tbl_CRM_LeadSources(CompanyId, IsActive);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadSources';
END
GO

-- 4. Table: myonline_tbl_CRM_Leads
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_Leads', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_Leads
    (
        LeadId           INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        OfficeLocationId INT                 NULL,
        LeadName         NVARCHAR(200)       NOT NULL,
        ContactPerson    NVARCHAR(150)       NULL,
        Phone            NVARCHAR(30)        NULL,
        Email            NVARCHAR(150)       NULL,
        Address          NVARCHAR(500)       NULL,
        ProductServiceId INT                 NULL,
        LeadSourceId     INT                 NULL,
        LeadSourceType   NVARCHAR(50)        NOT NULL CONSTRAINT DF_CRM_Leads_LeadSourceType DEFAULT ('Self'),
        LeadStatus       NVARCHAR(50)        NOT NULL CONSTRAINT DF_CRM_Leads_LeadStatus DEFAULT ('New Lead'),
        CreatedByUserId  INT                 NOT NULL,
        AssignedUserId   INT                 NULL,
        NextFollowUpDate DATETIME2           NULL,
        LastFollowUpDate DATETIME2           NULL,
        EstimatedValue   DECIMAL(18,2)       NULL,
        Remarks          NVARCHAR(MAX)       NULL,
        Latitude         FLOAT               NULL,
        Longitude        FLOAT               NULL,
        IsActive         BIT                 NOT NULL CONSTRAINT DF_CRM_Leads_IsActive DEFAULT (1),
        CreatedAtUtc     DATETIME2           NOT NULL CONSTRAINT DF_CRM_Leads_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        UpdatedAtUtc     DATETIME2           NULL,
        CONSTRAINT FK_CRM_Leads_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_Leads_OfficeLocations FOREIGN KEY (OfficeLocationId) REFERENCES dbo.myonline_tbl_OfficeLocations(Id),
        CONSTRAINT FK_CRM_Leads_ProductServices FOREIGN KEY (ProductServiceId) REFERENCES dbo.myonline_tbl_CRM_ProductServices(ProductServiceId),
        CONSTRAINT FK_CRM_Leads_LeadSources FOREIGN KEY (LeadSourceId) REFERENCES dbo.myonline_tbl_CRM_LeadSources(LeadSourceId),
        CONSTRAINT FK_CRM_Leads_CreatedByUser FOREIGN KEY (CreatedByUserId) REFERENCES dbo.myonline_tbl_Users(Id),
        CONSTRAINT FK_CRM_Leads_AssignedUser FOREIGN KEY (AssignedUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Company_Assigned_Active
        ON dbo.myonline_tbl_CRM_Leads(CompanyId, AssignedUserId, IsActive);

    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Company_Status_Active
        ON dbo.myonline_tbl_CRM_Leads(CompanyId, LeadStatus, IsActive);

    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Company_NextFollowUp
        ON dbo.myonline_tbl_CRM_Leads(CompanyId, NextFollowUpDate)
        INCLUDE (LeadStatus, AssignedUserId, LeadName);

    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Company_OfficeLocation
        ON dbo.myonline_tbl_CRM_Leads(CompanyId, OfficeLocationId);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_Leads';
END
ELSE
BEGIN
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.myonline_tbl_CRM_Leads') AND name = 'OfficeLocationId')
    BEGIN
        ALTER TABLE dbo.myonline_tbl_CRM_Leads ADD OfficeLocationId INT NULL
            CONSTRAINT FK_CRM_Leads_OfficeLocations FOREIGN KEY REFERENCES dbo.myonline_tbl_OfficeLocations(Id);
        PRINT '✅ Added OfficeLocationId to dbo.myonline_tbl_CRM_Leads';
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.myonline_tbl_CRM_Leads') AND name = 'Latitude')
    BEGIN
        ALTER TABLE dbo.myonline_tbl_CRM_Leads ADD Latitude FLOAT NULL;
        ALTER TABLE dbo.myonline_tbl_CRM_Leads ADD Longitude FLOAT NULL;
        PRINT '✅ Added Latitude/Longitude to dbo.myonline_tbl_CRM_Leads';
    END
END
GO

-- 5. Table: myonline_tbl_CRM_LeadAssignments
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadAssignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadAssignments
    (
        AssignmentId     INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        OfficeLocationId INT                 NULL,
        LeadId           INT                 NOT NULL,
        PreviousUserId   INT                 NULL,
        NewUserId        INT                 NOT NULL,
        AssignedByUserId INT                 NOT NULL,
        AssignedDateUtc  DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadAssignments_AssignedDateUtc DEFAULT (SYSUTCDATETIME()),
        Remarks          NVARCHAR(500)       NULL,
        CONSTRAINT FK_CRM_LeadAssignments_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadAssignments_OfficeLocations FOREIGN KEY (OfficeLocationId) REFERENCES dbo.myonline_tbl_OfficeLocations(Id),
        CONSTRAINT FK_CRM_LeadAssignments_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId) ON DELETE CASCADE,
        CONSTRAINT FK_CRM_LeadAssignments_PrevUser FOREIGN KEY (PreviousUserId) REFERENCES dbo.myonline_tbl_Users(Id),
        CONSTRAINT FK_CRM_LeadAssignments_NewUser FOREIGN KEY (NewUserId) REFERENCES dbo.myonline_tbl_Users(Id),
        CONSTRAINT FK_CRM_LeadAssignments_AssignedByUser FOREIGN KEY (AssignedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadAssignments_LeadId
        ON dbo.myonline_tbl_CRM_LeadAssignments(LeadId);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadAssignments';
END
ELSE
BEGIN
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.myonline_tbl_CRM_LeadAssignments') AND name = 'OfficeLocationId')
    BEGIN
        ALTER TABLE dbo.myonline_tbl_CRM_LeadAssignments ADD OfficeLocationId INT NULL
            CONSTRAINT FK_CRM_LeadAssignments_OfficeLocations FOREIGN KEY REFERENCES dbo.myonline_tbl_OfficeLocations(Id);
        PRINT '✅ Added OfficeLocationId to dbo.myonline_tbl_CRM_LeadAssignments';
    END
END
GO

-- 6. Table: myonline_tbl_CRM_LeadFollowUps
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadFollowUps', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadFollowUps
    (
        FollowUpId       INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        OfficeLocationId INT                 NULL,
        LeadId           INT                 NOT NULL,
        CreatedByUserId  INT                 NOT NULL,
        FollowUpDateUtc  DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadFollowUps_FollowUpDateUtc DEFAULT (SYSUTCDATETIME()),
        NextFollowUpDate DATETIME2           NULL,
        ContactMethod    NVARCHAR(50)        NOT NULL CONSTRAINT DF_CRM_LeadFollowUps_ContactMethod DEFAULT ('Call'), -- 'Call', 'Meeting', 'Email', 'Visit'
        Status           NVARCHAR(50)        NOT NULL CONSTRAINT DF_CRM_LeadFollowUps_Status DEFAULT ('Follow Up'),
        Remarks          NVARCHAR(MAX)       NULL,
        Latitude         FLOAT               NULL,
        Longitude        FLOAT               NULL,
        LocationAddress  NVARCHAR(500)       NULL,
        CreatedAtUtc     DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadFollowUps_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_LeadFollowUps_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadFollowUps_OfficeLocations FOREIGN KEY (OfficeLocationId) REFERENCES dbo.myonline_tbl_OfficeLocations(Id),
        CONSTRAINT FK_CRM_LeadFollowUps_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId) ON DELETE CASCADE,
        CONSTRAINT FK_CRM_LeadFollowUps_CreatedByUser FOREIGN KEY (CreatedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadFollowUps_LeadId
        ON dbo.myonline_tbl_CRM_LeadFollowUps(LeadId);

    CREATE NONCLUSTERED INDEX IX_CRM_LeadFollowUps_Company_CreatedBy
        ON dbo.myonline_tbl_CRM_LeadFollowUps(CompanyId, CreatedByUserId, FollowUpDateUtc);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadFollowUps';
END
ELSE
BEGIN
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.myonline_tbl_CRM_LeadFollowUps') AND name = 'OfficeLocationId')
    BEGIN
        ALTER TABLE dbo.myonline_tbl_CRM_LeadFollowUps ADD OfficeLocationId INT NULL
            CONSTRAINT FK_CRM_LeadFollowUps_OfficeLocations FOREIGN KEY REFERENCES dbo.myonline_tbl_OfficeLocations(Id);
        PRINT '✅ Added OfficeLocationId to dbo.myonline_tbl_CRM_LeadFollowUps';
    END
END
GO

-- 7. Table: myonline_tbl_CRM_LeadRemarks
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadRemarks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadRemarks
    (
        RemarkId     INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId    INT                 NOT NULL,
        LeadId       INT                 NOT NULL,
        UserId       INT                 NOT NULL,
        RemarkText   NVARCHAR(MAX)       NOT NULL,
        CreatedAtUtc DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadRemarks_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_LeadRemarks_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadRemarks_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId) ON DELETE CASCADE,
        CONSTRAINT FK_CRM_LeadRemarks_Users FOREIGN KEY (UserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadRemarks_LeadId
        ON dbo.myonline_tbl_CRM_LeadRemarks(LeadId);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadRemarks';
END
GO

-- 8. Table: myonline_tbl_CRM_KPI
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_KPI', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_KPI
    (
        KpiId             INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId         INT                 NOT NULL,
        OfficeLocationId  INT                 NULL,
        UserId            INT                 NULL,
        PeriodType        NVARCHAR(20)        NOT NULL CONSTRAINT DF_CRM_KPI_PeriodType DEFAULT ('Monthly'), -- 'Daily', 'Weekly', 'Monthly', 'Quarterly', 'Yearly'
        TargetCalls       INT                 NOT NULL CONSTRAINT DF_CRM_KPI_TargetCalls DEFAULT (0),
        TargetVisits      INT                 NOT NULL CONSTRAINT DF_CRM_KPI_TargetVisits DEFAULT (0),
        TargetLeads       INT                 NOT NULL CONSTRAINT DF_CRM_KPI_TargetLeads DEFAULT (0),
        TargetConversions INT                 NOT NULL CONSTRAINT DF_CRM_KPI_TargetConversions DEFAULT (0),
        TargetRevenue     DECIMAL(18,2)       NOT NULL CONSTRAINT DF_CRM_KPI_TargetRevenue DEFAULT (0),
        StartDate         DATE                NOT NULL,
        EndDate           DATE                NOT NULL,
        CreatedByUserId   INT                 NOT NULL,
        CreatedAtUtc      DATETIME2           NOT NULL CONSTRAINT DF_CRM_KPI_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        UpdatedAtUtc      DATETIME2           NULL,
        CONSTRAINT FK_CRM_KPI_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_KPI_OfficeLocations FOREIGN KEY (OfficeLocationId) REFERENCES dbo.myonline_tbl_OfficeLocations(Id),
        CONSTRAINT FK_CRM_KPI_Users FOREIGN KEY (UserId) REFERENCES dbo.myonline_tbl_Users(Id),
        CONSTRAINT FK_CRM_KPI_CreatedByUser FOREIGN KEY (CreatedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_KPI_Company_User_Period
        ON dbo.myonline_tbl_CRM_KPI(CompanyId, UserId, PeriodType, StartDate, EndDate);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_KPI';
END
GO

-- 9. Table: myonline_tbl_CRM_LeadStatusHistory
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadStatusHistory', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadStatusHistory
    (
        StatusHistoryId INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId       INT                 NOT NULL,
        LeadId          INT                 NOT NULL,
        PreviousStatus  NVARCHAR(50)        NOT NULL,
        NewStatus       NVARCHAR(50)        NOT NULL,
        ChangedByUserId INT                 NOT NULL,
        Remarks         NVARCHAR(500)       NULL,
        ChangedDateUtc  DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadStatusHistory_ChangedDateUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_LeadStatusHistory_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadStatusHistory_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId) ON DELETE CASCADE,
        CONSTRAINT FK_CRM_LeadStatusHistory_Users FOREIGN KEY (ChangedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadStatusHistory_LeadId
        ON dbo.myonline_tbl_CRM_LeadStatusHistory(LeadId, ChangedDateUtc);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadStatusHistory';
END
GO

-- 10. Table: myonline_tbl_CRM_AuditLog
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_AuditLog', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_AuditLog
    (
        AuditLogId   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        CompanyId    INT                  NOT NULL,
        UserId       INT                  NOT NULL,
        Action       NVARCHAR(50)         NOT NULL,
        EntityType   NVARCHAR(30)         NOT NULL,
        EntityId     INT                  NOT NULL,
        OldValues    NVARCHAR(MAX)        NULL,
        NewValues    NVARCHAR(MAX)        NULL,
        IpAddress    NVARCHAR(50)         NULL,
        TimestampUtc DATETIME2            NOT NULL CONSTRAINT DF_CRM_AuditLog_TimestampUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_AuditLog_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_AuditLog_Users FOREIGN KEY (UserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_AuditLog_Company_Entity
        ON dbo.myonline_tbl_CRM_AuditLog(CompanyId, EntityType, EntityId);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_AuditLog';
END
GO

-- 11. Seed Default Lead Sources for All Companies (if empty)
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'myonline_tbl_Companies')
BEGIN
    INSERT INTO dbo.myonline_tbl_CRM_LeadSources (CompanyId, Name, IsSystem, IsActive, CreatedAtUtc)
    SELECT c.CompanyId, s.Name, 1, 1, SYSUTCDATETIME()
    FROM dbo.myonline_tbl_Companies c
    CROSS JOIN (
        VALUES 
            (N'Direct Call'),
            (N'Website Inquiry'),
            (N'Social Media (Facebook/LinkedIn)'),
            (N'Referral / Word of Mouth'),
            (N'Field Visit / Cold Call'),
            (N'Email Campaign'),
            (N'Exhibition / Event')
    ) AS s(Name)
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.myonline_tbl_CRM_LeadSources ls 
        WHERE ls.CompanyId = c.CompanyId AND ls.Name = s.Name
    );

    PRINT '✅ Seeded Default CRM Lead Sources';
END
GO

PRINT '🎉 Complete Production CRM Migration Executed Successfully!';
GO
