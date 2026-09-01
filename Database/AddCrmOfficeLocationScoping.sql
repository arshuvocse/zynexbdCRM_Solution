/* ==================================================================================
   DATABASE MIGRATION SCRIPT: OFFICE-LOCATION-LEVEL SCOPING FOR CRM
   Database: LiveTrackingDB (SQL Server 2019)
   Tenant Discriminator: CompanyId (unchanged) + new OfficeLocationId (this script)

   Adds nullable OfficeLocationId to CRM_Leads, CRM_LeadAssignments, CRM_LeadFollowUps,
   CRM_KPI; backfills existing rows from the assigned/creating user's office; adds
   supporting indexes. Idempotent - safe to re-run. Does NOT touch legacy Customer/Visit
   tables, CRM_LeadRemarks, CRM_LeadStatusHistory, CRM_AuditLog, CRM_ProductServices, or
   CRM_LeadSources (office scoping for those is enforced via their parent Lead at query
   time, not a duplicated column - see plan notes).

   Column stays NULLable permanently: a company with zero configured office locations
   (e.g. live "Beta" company, CompanyId=2) has no office to backfill into, and that is a
   legitimate state, not data corruption to paper over.
   ================================================================================== */

USE [LiveTrackingDB];
GO

------------------------------------------------------------------------------------
-- 1. Add columns
------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.myonline_tbl_CRM_Leads') AND name = 'OfficeLocationId')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_Leads ADD OfficeLocationId INT NULL;
    PRINT '✅ Added OfficeLocationId to myonline_tbl_CRM_Leads';
END
ELSE PRINT 'ℹ️ myonline_tbl_CRM_Leads.OfficeLocationId already exists - skipped.';
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadAssignments') AND name = 'OfficeLocationId')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_LeadAssignments ADD OfficeLocationId INT NULL;
    PRINT '✅ Added OfficeLocationId to myonline_tbl_CRM_LeadAssignments';
END
ELSE PRINT 'ℹ️ myonline_tbl_CRM_LeadAssignments.OfficeLocationId already exists - skipped.';
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.myonline_tbl_CRM_LeadFollowUps') AND name = 'OfficeLocationId')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_LeadFollowUps ADD OfficeLocationId INT NULL;
    PRINT '✅ Added OfficeLocationId to myonline_tbl_CRM_LeadFollowUps';
END
ELSE PRINT 'ℹ️ myonline_tbl_CRM_LeadFollowUps.OfficeLocationId already exists - skipped.';
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.myonline_tbl_CRM_KPI') AND name = 'OfficeLocationId')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_KPI ADD OfficeLocationId INT NULL;
    PRINT '✅ Added OfficeLocationId to myonline_tbl_CRM_KPI';
END
ELSE PRINT 'ℹ️ myonline_tbl_CRM_KPI.OfficeLocationId already exists - skipped.';
GO

------------------------------------------------------------------------------------
-- 2. Foreign keys (SET NULL on office delete - a deleted office should not orphan
--    the FK constraint, CRM rows just become "unscoped" for that record)
------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_CRM_Leads_OfficeLocations')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_Leads WITH CHECK
        ADD CONSTRAINT FK_CRM_Leads_OfficeLocations FOREIGN KEY (OfficeLocationId)
        REFERENCES dbo.myonline_tbl_OfficeLocations(Id) ON DELETE SET NULL;
    PRINT '✅ Added FK_CRM_Leads_OfficeLocations';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_CRM_LeadAssignments_OfficeLocations')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_LeadAssignments WITH CHECK
        ADD CONSTRAINT FK_CRM_LeadAssignments_OfficeLocations FOREIGN KEY (OfficeLocationId)
        REFERENCES dbo.myonline_tbl_OfficeLocations(Id) ON DELETE SET NULL;
    PRINT '✅ Added FK_CRM_LeadAssignments_OfficeLocations';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_CRM_LeadFollowUps_OfficeLocations')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_LeadFollowUps WITH CHECK
        ADD CONSTRAINT FK_CRM_LeadFollowUps_OfficeLocations FOREIGN KEY (OfficeLocationId)
        REFERENCES dbo.myonline_tbl_OfficeLocations(Id) ON DELETE SET NULL;
    PRINT '✅ Added FK_CRM_LeadFollowUps_OfficeLocations';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_CRM_KPI_OfficeLocations')
BEGIN
    ALTER TABLE dbo.myonline_tbl_CRM_KPI WITH CHECK
        ADD CONSTRAINT FK_CRM_KPI_OfficeLocations FOREIGN KEY (OfficeLocationId)
        REFERENCES dbo.myonline_tbl_OfficeLocations(Id) ON DELETE SET NULL;
    PRINT '✅ Added FK_CRM_KPI_OfficeLocations';
END
GO

------------------------------------------------------------------------------------
-- 3. Backfill CRM_Leads: prefer AssignedUser's office, fall back to CreatedByUser's
------------------------------------------------------------------------------------
UPDATE l
SET l.OfficeLocationId = COALESCE(au.OfficeLocationId, cu.OfficeLocationId)
FROM dbo.myonline_tbl_CRM_Leads l
LEFT JOIN dbo.myonline_tbl_Users au ON au.Id = l.AssignedUserId
LEFT JOIN dbo.myonline_tbl_Users cu ON cu.Id = l.CreatedByUserId
WHERE l.OfficeLocationId IS NULL;
PRINT CONCAT('✅ Backfilled CRM_Leads.OfficeLocationId for ', @@ROWCOUNT, ' rows (COALESCE of AssignedUser/CreatedByUser office)');
GO

------------------------------------------------------------------------------------
-- 4. Backfill CRM_LeadAssignments and CRM_LeadFollowUps from their parent lead
------------------------------------------------------------------------------------
UPDATE a
SET a.OfficeLocationId = l.OfficeLocationId
FROM dbo.myonline_tbl_CRM_LeadAssignments a
JOIN dbo.myonline_tbl_CRM_Leads l ON l.LeadId = a.LeadId
WHERE a.OfficeLocationId IS NULL;
PRINT CONCAT('✅ Backfilled CRM_LeadAssignments.OfficeLocationId for ', @@ROWCOUNT, ' rows (from parent lead)');
GO

UPDATE f
SET f.OfficeLocationId = l.OfficeLocationId
FROM dbo.myonline_tbl_CRM_LeadFollowUps f
JOIN dbo.myonline_tbl_CRM_Leads l ON l.LeadId = f.LeadId
WHERE f.OfficeLocationId IS NULL;
PRINT CONCAT('✅ Backfilled CRM_LeadFollowUps.OfficeLocationId for ', @@ROWCOUNT, ' rows (from parent lead)');
GO

------------------------------------------------------------------------------------
-- 5. Backfill CRM_KPI from the target UserId's office (company-default rows with
--    UserId IS NULL stay NULL/office-unscoped - they apply company-wide by design)
------------------------------------------------------------------------------------
UPDATE k
SET k.OfficeLocationId = u.OfficeLocationId
FROM dbo.myonline_tbl_CRM_KPI k
JOIN dbo.myonline_tbl_Users u ON u.Id = k.UserId
WHERE k.OfficeLocationId IS NULL AND k.UserId IS NOT NULL;
PRINT CONCAT('✅ Backfilled CRM_KPI.OfficeLocationId for ', @@ROWCOUNT, ' rows (from target user)');
GO

------------------------------------------------------------------------------------
-- 6. Report any leads that could NOT be backfilled (no office on creator or assignee)
--    per instructions: report, do not invent an office.
------------------------------------------------------------------------------------
PRINT '--- Leads that could not be backfilled with an OfficeLocationId (review manually) ---';
SELECT l.LeadId, l.CompanyId, l.LeadName, l.CreatedByUserId, l.AssignedUserId
FROM dbo.myonline_tbl_CRM_Leads l
WHERE l.OfficeLocationId IS NULL;
GO

------------------------------------------------------------------------------------
-- 7. Indexes supporting office-scoped CRM queries
------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_Leads_Company_Office_Status')
    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Company_Office_Status
        ON dbo.myonline_tbl_CRM_Leads (CompanyId, OfficeLocationId, LeadStatus);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_Leads_Office_AssignedUser')
    CREATE NONCLUSTERED INDEX IX_CRM_Leads_Office_AssignedUser
        ON dbo.myonline_tbl_CRM_Leads (OfficeLocationId, AssignedUserId);
GO

-- Filtered index requires QUOTED_IDENTIFIER ON for this session/batch.
SET QUOTED_IDENTIFIER ON;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_Leads_NextFollowUpDate_Active')
    CREATE NONCLUSTERED INDEX IX_CRM_Leads_NextFollowUpDate_Active
        ON dbo.myonline_tbl_CRM_Leads (NextFollowUpDate) WHERE IsActive = 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_LeadFollowUps_Office_CreatedByUser')
    CREATE NONCLUSTERED INDEX IX_CRM_LeadFollowUps_Office_CreatedByUser
        ON dbo.myonline_tbl_CRM_LeadFollowUps (OfficeLocationId, CreatedByUserId);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_LeadAssignments_Office')
    CREATE NONCLUSTERED INDEX IX_CRM_LeadAssignments_Office
        ON dbo.myonline_tbl_CRM_LeadAssignments (OfficeLocationId);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CRM_KPI_Company_Office_User')
    CREATE NONCLUSTERED INDEX IX_CRM_KPI_Company_Office_User
        ON dbo.myonline_tbl_CRM_KPI (CompanyId, OfficeLocationId, UserId);
GO

PRINT '✅ Office-location scoping migration complete.';
GO
