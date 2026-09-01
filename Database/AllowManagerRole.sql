-- ==================================================================================
-- MIGRATION: Allow "Manager" as a valid Users.Role value
-- Database: LiveTrackingDB (SQL Server 2019)
--
-- The live database carries a CHECK constraint on dbo.myonline_tbl_Users.Role that
-- was never defined in this repo (not in any Database .sql script, not in the EF Core
-- model) restricting Role to only "User" or "Admin". The CRM Manager role addition
-- requires "Manager" to be a valid value. This script is idempotent - safe to re-run.
-- ==================================================================================

USE [LiveTrackingDB];
GO

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_myonline_tbl_Users_Role')
BEGIN
    ALTER TABLE dbo.myonline_tbl_Users DROP CONSTRAINT CK_myonline_tbl_Users_Role;
    PRINT '✅ Dropped old constraint CK_myonline_tbl_Users_Role (User/Admin only)';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_myonline_tbl_Users_Role')
BEGIN
    ALTER TABLE dbo.myonline_tbl_Users
        ADD CONSTRAINT CK_myonline_tbl_Users_Role CHECK ([Role] = N'User' OR [Role] = N'Admin' OR [Role] = N'Manager');
    PRINT '✅ Recreated CK_myonline_tbl_Users_Role to allow User/Admin/Manager';
END
GO
