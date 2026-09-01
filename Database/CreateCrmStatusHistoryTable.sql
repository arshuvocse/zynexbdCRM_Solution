/* ==================================================================================
   DATABASE MIGRATION SCRIPT: CRM LEAD STATUS HISTORY TABLE
   Database: LiveTrackingDB (SQL Server 2019)
   Tenant Discriminator: CompanyId
   Run after CreateCrmTablesAndSeed.sql
   ================================================================================== */

USE [LiveTrackingDB];
GO

IF OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadStatusHistory', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_CRM_LeadStatusHistory
    (
        StatusHistoryId  INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        CompanyId        INT                 NOT NULL,
        LeadId           INT                 NOT NULL,
        PreviousStatus   NVARCHAR(50)        NOT NULL,
        NewStatus        NVARCHAR(50)        NOT NULL,
        ChangedByUserId  INT                 NOT NULL,
        ChangedDateUtc   DATETIME2           NOT NULL CONSTRAINT DF_CRM_LeadStatusHistory_ChangedDateUtc DEFAULT (SYSUTCDATETIME()),
        Remarks          NVARCHAR(500)       NULL,
        CONSTRAINT FK_CRM_LeadStatusHistory_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.myonline_tbl_Companies(CompanyId),
        CONSTRAINT FK_CRM_LeadStatusHistory_Leads FOREIGN KEY (LeadId) REFERENCES dbo.myonline_tbl_CRM_Leads(LeadId) ON DELETE CASCADE,
        CONSTRAINT FK_CRM_LeadStatusHistory_ChangedByUser FOREIGN KEY (ChangedByUserId) REFERENCES dbo.myonline_tbl_Users(Id)
    );

    CREATE NONCLUSTERED INDEX IX_CRM_LeadStatusHistory_LeadId_ChangedDateUtc
        ON dbo.myonline_tbl_CRM_LeadStatusHistory(LeadId, ChangedDateUtc);

    PRINT '✅ Created table dbo.myonline_tbl_CRM_LeadStatusHistory';
END
ELSE
BEGIN
    PRINT 'ℹ️ Table dbo.myonline_tbl_CRM_LeadStatusHistory already exists - skipped.';
END
GO
