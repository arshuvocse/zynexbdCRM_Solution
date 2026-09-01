-- Migration: Add CompanyId column and index to myonline_tbl_Notifications
IF NOT EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE Name = N'CompanyId' 
    AND Object_ID = Object_ID(N'dbo.myonline_tbl_Notifications')
)
BEGIN
    ALTER TABLE dbo.myonline_tbl_Notifications
    ADD CompanyId INT NULL;

    PRINT 'Added CompanyId column to myonline_tbl_Notifications.';
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes 
    WHERE Name = N'IX_myonline_tbl_Notifications_CompanyId_TargetRole_Created' 
    AND Object_ID = Object_ID(N'dbo.myonline_tbl_Notifications')
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_myonline_tbl_Notifications_CompanyId_TargetRole_Created
    ON dbo.myonline_tbl_Notifications(CompanyId, TargetRole, CreatedAt DESC);

    PRINT 'Created index IX_myonline_tbl_Notifications_CompanyId_TargetRole_Created.';
END
GO
