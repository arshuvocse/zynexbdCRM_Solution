/* ==================================================================================
   DATABASE MIGRATION SCRIPT: CRM AUDIT LOG TABLE
   Database: LiveTrackingDB (SQL Server 2019)
   Tenant Discriminator: CompanyId
   Run after CreateCrmTablesAndSeed.sql
   ================================================================================== */

USE [LiveTrackingDB];
GO

IF OBJECT_ID(N'dbo.myonline_tbl_CRM_AuditLog', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_AuditLog
    (
        AuditLogId   INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId    INT                 NOT NULL,
        UserId       INT                 NOT NULL,
        Action       NVARCHAR(50)        NOT NULL, -- LeadCreated, LeadAssigned, LeadReassigned, StatusChanged, FollowUpAdded, RemarkAdded, KpiCreated, KpiUpdated
        EntityType   NVARCHAR(30)        NOT NULL, -- Lead, Kpi
        EntityId     INT                 NOT NULL,
        OldValue     NVARCHAR(MAX)       NULL,
        NewValue     NVARCHAR(MAX)       NULL,
        CreatedAtUtc DATETIME2           NOT NULL CONSTRAINT DF_CRM_AuditLog_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_CRM_AuditLog_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_AuditLog_Users FOREIGN KEY (UserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_AuditLog_Company_Entity
        ON dbo.myonline_tbl_CRM_AuditLog(CompanyId, EntityType, EntityId);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_AuditLog';
END
ELSE
BEGIN
    PRINT 'ℹ️ Table dbo.myonline_tbl_CRM_AuditLog already exists - skipped.';
END
GO
