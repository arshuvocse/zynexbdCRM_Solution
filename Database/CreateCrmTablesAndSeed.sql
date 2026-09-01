/* ==================================================================================
   DATABASE MIGRATION SCRIPT: CRM MODULE TABLES, INDEXES & DEFAULT SEED DATA
   Database: LiveTrackingDB (SQL Server 2019)
   Tenant Discriminator: CompanyId
   ================================================================================== */

USE [LiveTrackingDB];
GO

-- 1. Table: myonline_tbl_CRM_ProductServices
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

-- 2. Table: myonline_tbl_CRM_LeadSources
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

-- 3. Table: myonline_tbl_CRM_Leads
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_Leads', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_Leads
    (
        LeadId           INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        LeadName         NVARCHAR(200)       NOT NULL,
        ContactPerson    NVARCHAR(150)       NULL,
        Phone            NVARCHAR(30)        NULL,
        Email            NVARCHAR(150)       NULL,
        Address          NVARCHAR(500)       NULL,
        ProductServiceId INT                 NULL,
        LeadSourceId     INT                 NULL,
        LeadSourceType   NVARCHAR(50)        NOT NULL CONSTRAINT DF_CRM_Leads_LeadSourceType DEFAULT ('Self'), -- 'Self' or 'Assigned'
        LeadStatus       NVARCHAR(50)        NOT NULL CONSTRAINT DF_CRM_Leads_LeadStatus DEFAULT ('New Lead'), -- 'New Lead', 'Follow Up', 'Interested', 'Not Interested', 'Closed'
        CreatedByUserId  INT                 NOT NULL,
        AssignedUserId   INT                 NULL,
        NextFollowUpDate DATETIME2           NULL,
        LastFollowUpDate DATETIME2           NULL,
        EstimatedValue   DECIMAL(18,2)       NULL,
        Remarks          NVARCHAR(MAX)       NULL,
        IsActive         BIT                 NOT NULL CONSTRAINT DF_CRM_Leads_IsActive DEFAULT (1),
        CreatedAtUtc     DATETIME2           NOT NULL CONSTRAINT DF_CRM_Leads_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        UpdatedAtUtc     DATETIME2           NULL,
        CONSTRAINT FK_CRM_Leads_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
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

    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Company_CreatedAt
        ON dbo.myonline_tbl_CRM_Leads(CompanyId, CreatedAtUtc DESC);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_Leads';
END
GO

-- 4. Table: myonline_tbl_CRM_LeadAssignments
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadAssignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadAssignments
    (
        AssignmentId     INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        LeadId           INT                 NOT NULL,
        PreviousUserId   INT                 NULL,
        NewUserId        INT                 NOT NULL,
        AssignedByUserId INT                 NOT NULL,
        AssignedDateUtc  DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadAssignments_AssignedDateUtc DEFAULT (SYSUTCDATETIME()),
        Remarks          NVARCHAR(500)       NULL,
        CONSTRAINT FK_CRM_LeadAssignments_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadAssignments_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId),
        CONSTRAINT FK_CRM_LeadAssignments_PrevUser FOREIGN KEY (PreviousUserId) REFERENCES dbo.myonline_tbl_Users(Id),
        CONSTRAINT FK_CRM_LeadAssignments_NewUser FOREIGN KEY (NewUserId) REFERENCES dbo.myonline_tbl_Users(Id),
        CONSTRAINT FK_CRM_LeadAssignments_AssignedBy FOREIGN KEY (AssignedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadAssignments_Company_Lead_Date
        ON dbo.myonline_tbl_CRM_LeadAssignments(CompanyId, LeadId, AssignedDateUtc DESC);

    CREATE NONCLUSTERED INDEX IX_CRM_LeadAssignments_Company_NewUser
        ON dbo.myonline_tbl_CRM_LeadAssignments(CompanyId, NewUserId);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadAssignments';
END
GO

-- 5. Table: myonline_tbl_CRM_LeadFollowUps
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadFollowUps', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadFollowUps
    (
        FollowUpId       INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        LeadId           INT                 NOT NULL,
        FollowUpDateUtc  DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadFollowUps_FollowUpDateUtc DEFAULT (SYSUTCDATETIME()),
        NextFollowUpDate DATETIME2           NULL,
        Status           NVARCHAR(50)        NOT NULL,
        Remarks          NVARCHAR(MAX)       NOT NULL,
        CreatedByUserId  INT                 NOT NULL,
        CreatedAtUtc     DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadFollowUps_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_LeadFollowUps_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadFollowUps_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId),
        CONSTRAINT FK_CRM_LeadFollowUps_CreatedBy FOREIGN KEY (CreatedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadFollowUps_Company_Lead_Created
        ON dbo.myonline_tbl_CRM_LeadFollowUps(CompanyId, LeadId, CreatedAtUtc DESC);

    CREATE NONCLUSTERED INDEX IX_CRM_LeadFollowUps_Company_User_Date
        ON dbo.myonline_tbl_CRM_LeadFollowUps(CompanyId, CreatedByUserId, FollowUpDateUtc);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadFollowUps';
END
GO

-- 6. Table: myonline_tbl_CRM_LeadRemarks
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadRemarks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadRemarks
    (
        RemarkId     INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId    INT                 NOT NULL,
        LeadId       INT                 NOT NULL,
        UserId       INT                 NOT NULL,
        Remark       NVARCHAR(MAX)       NOT NULL,
        CreatedAtUtc DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadRemarks_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_LeadRemarks_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadRemarks_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId),
        CONSTRAINT FK_CRM_LeadRemarks_Users FOREIGN KEY (UserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadRemarks_Company_Lead_Created
        ON dbo.myonline_tbl_CRM_LeadRemarks(CompanyId, LeadId, CreatedAtUtc DESC);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadRemarks';
END
GO

-- 7. Table: myonline_tbl_CRM_KPI
IF OBJECT_ID(N'dbo.myonline_tbl_CRM_KPI', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_KPI
    (
        KpiId              INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId          INT                 NOT NULL,
        UserId             INT                 NULL, -- NULL = Company Default Target, Non-NULL = Specific Employee Target
        PeriodType         NVARCHAR(20)        NOT NULL, -- 'Daily', 'Weekly', 'Monthly'
        FollowUpTarget     INT                 NOT NULL CONSTRAINT DF_CRM_KPI_FollowUpTarget DEFAULT (0),
        InterestedTarget   INT                 NOT NULL CONSTRAINT DF_CRM_KPI_InterestedTarget DEFAULT (0),
        ClosedTarget       INT                 NOT NULL CONSTRAINT DF_CRM_KPI_ClosedTarget DEFAULT (0),
        EffectiveStartDate DATETIME2           NOT NULL CONSTRAINT DF_CRM_KPI_EffectiveStartDate DEFAULT (SYSUTCDATETIME()),
        IsActive           BIT                 NOT NULL CONSTRAINT DF_CRM_KPI_IsActive DEFAULT (1),
        CreatedByUserId    INT                 NOT NULL,
        CreatedAtUtc       DATETIME2           NOT NULL CONSTRAINT DF_CRM_KPI_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        UpdatedAtUtc       DATETIME2           NULL,
        CONSTRAINT FK_CRM_KPI_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_KPI_Users FOREIGN KEY (UserId) REFERENCES dbo.myonline_tbl_Users(Id),
        CONSTRAINT FK_CRM_KPI_CreatedBy FOREIGN KEY (CreatedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_KPI_Company_User_Period_Active
        ON dbo.myonline_tbl_CRM_KPI(CompanyId, UserId, PeriodType, IsActive);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_KPI';
END
GO

-- 8. Seed Default Master Data for all existing companies
DECLARE @CompanyId INT;
DECLARE company_cursor CURSOR FOR 
    SELECT CompanyId FROM dbo.myonline_tbl_Companies WHERE IsActive = 1;

OPEN company_cursor;
FETCH NEXT FROM company_cursor INTO @CompanyId;

WHILE @@FETCH_STATUS = 0
BEGIN
    -- Seed Default Lead Sources
    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_LeadSources WHERE CompanyId = @CompanyId AND Name = N'Self')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_LeadSources (CompanyId, Name, IsSystem, IsActive)
        VALUES (@CompanyId, N'Self', 1, 1);
    END

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_LeadSources WHERE CompanyId = @CompanyId AND Name = N'Assigned By Manager')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_LeadSources (CompanyId, Name, IsSystem, IsActive)
        VALUES (@CompanyId, N'Assigned By Manager', 1, 1);
    END

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_LeadSources WHERE CompanyId = @CompanyId AND Name = N'Website')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_LeadSources (CompanyId, Name, IsSystem, IsActive)
        VALUES (@CompanyId, N'Website', 0, 1);
    END

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_LeadSources WHERE CompanyId = @CompanyId AND Name = N'Referral')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_LeadSources (CompanyId, Name, IsSystem, IsActive)
        VALUES (@CompanyId, N'Referral', 0, 1);
    END

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_LeadSources WHERE CompanyId = @CompanyId AND Name = N'Direct Visit')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_LeadSources (CompanyId, Name, IsSystem, IsActive)
        VALUES (@CompanyId, N'Direct Visit', 0, 1);
    END

    -- Seed Default Products/Services
    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_ProductServices WHERE CompanyId = @CompanyId)
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_ProductServices (CompanyId, Name, Code, Description, Price, IsActive)
        VALUES 
            (@CompanyId, N'Enterprise SaaS Subscription', N'SAAS-ENT', N'Monthly/Annual Software Subscription', 15000.00, 1),
            (@CompanyId, N'GPS Tracking Device & Installation', N'GPS-DEV', N'Hardware device with installation service', 4500.00, 1),
            (@CompanyId, N'Technical Support & Maintenance', N'SUP-MAINT', N'24/7 dedicated support package', 2500.00, 1);
    END

    -- Seed Default Company KPIs
    DECLARE @AdminUserId INT = (SELECT TOP 1 Id FROM dbo.myonline_tbl_Users WHERE (CompanyId = @CompanyId OR CompanyId IS NULL) AND Role = 'Admin' ORDER BY Id ASC);
    IF @AdminUserId IS NULL SET @AdminUserId = 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND UserId IS NULL AND PeriodType = 'Daily')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_KPI (CompanyId, UserId, PeriodType, FollowUpTarget, InterestedTarget, ClosedTarget, CreatedByUserId, IsActive)
        VALUES (@CompanyId, NULL, 'Daily', 30, 20, 10, @AdminUserId, 1);
    END

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND UserId IS NULL AND PeriodType = 'Weekly')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_KPI (CompanyId, UserId, PeriodType, FollowUpTarget, InterestedTarget, ClosedTarget, CreatedByUserId, IsActive)
        VALUES (@CompanyId, NULL, 'Weekly', 150, 100, 50, @AdminUserId, 1);
    END

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_KPI WHERE CompanyId = @CompanyId AND UserId IS NULL AND PeriodType = 'Monthly')
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_KPI (CompanyId, UserId, PeriodType, FollowUpTarget, InterestedTarget, ClosedTarget, CreatedByUserId, IsActive)
        VALUES (@CompanyId, NULL, 'Monthly', 600, 300, 100, @AdminUserId, 1);
    END

    FETCH NEXT FROM company_cursor INTO @CompanyId;
END

CLOSE company_cursor;
DEALLOCATE company_cursor;
GO

PRINT '🎉 CRM Tables, Indexes, and Default Seeds Created Successfully!';
GO
