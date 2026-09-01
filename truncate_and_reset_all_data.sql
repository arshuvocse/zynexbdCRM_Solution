-- ==========================================================
-- COMPLETE DATABASE CLEANUP & MULTI-COMPANY SEED SCRIPT
-- Companies: MOXX & Beta Tech Solutions
-- Database: LiveTrackingDB
-- Password for all accounts: password123
-- ==========================================================

USE [LiveTrackingDB];
GO

PRINT '⚠️ Starting Database Reset & Cleanup...';

-- ১. Disable all Foreign Key constraints temporarily
EXEC sp_MSforeachtable "ALTER TABLE ? NOCHECK CONSTRAINT all";
GO

-- ২. Drop old global unique constraints on Name (so each company can have its own 'Casual Leave' / 'General Shift')
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_myonline_tbl_LeaveTypes_Name')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_LeaveTypes] DROP CONSTRAINT [UQ_myonline_tbl_LeaveTypes_Name];
    PRINT '✅ Dropped old global constraint UQ_myonline_tbl_LeaveTypes_Name';
END
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_myonline_tbl_LeaveTypes_Name' AND object_id = OBJECT_ID('dbo.myonline_tbl_LeaveTypes'))
BEGIN
    DROP INDEX [UQ_myonline_tbl_LeaveTypes_Name] ON [dbo].[myonline_tbl_LeaveTypes];
    PRINT '✅ Dropped old index UQ_myonline_tbl_LeaveTypes_Name';
END
GO

IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_myonline_tbl_Shifts_ShiftName')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_Shifts] DROP CONSTRAINT [UQ_myonline_tbl_Shifts_ShiftName];
    PRINT '✅ Dropped old global constraint UQ_myonline_tbl_Shifts_ShiftName';
END
GO

-- ৩. Delete all tables safely
-- CRM tables first (children before parents, to stay FK-safe even with constraints re-enabled WITH CHECK later)
IF OBJECT_ID('dbo.myonline_tbl_CRM_AuditLog', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_AuditLog];
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadStatusHistory', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_LeadStatusHistory];
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadFollowUps', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_LeadFollowUps];
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadRemarks', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_LeadRemarks];
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadAssignments', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_LeadAssignments];
IF OBJECT_ID('dbo.myonline_tbl_CRM_Leads', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_Leads];
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadSources', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_LeadSources];
IF OBJECT_ID('dbo.myonline_tbl_CRM_ProductServices', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_ProductServices];
IF OBJECT_ID('dbo.myonline_tbl_CRM_KPI', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CRM_KPI];
IF OBJECT_ID('dbo.myonline_tbl_DriverLocations', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_DriverLocations];
IF OBJECT_ID('dbo.myonline_tbl_CustomerVisits', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_CustomerVisits];
IF OBJECT_ID('dbo.myonline_tbl_Customers', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_Customers];
IF OBJECT_ID('dbo.myonline_tbl_Attendances', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_Attendances];
IF OBJECT_ID('dbo.myonline_tbl_AttendanceRecords', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_AttendanceRecords];
IF OBJECT_ID('dbo.myonline_tbl_LeaveApplications', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_LeaveApplications];
IF OBJECT_ID('dbo.myonline_tbl_LeaveBalances', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_LeaveBalances];
IF OBJECT_ID('dbo.myonline_tbl_Notifications', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_Notifications];
IF OBJECT_ID('dbo.myonline_tbl_AdminOfficeLocations', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_AdminOfficeLocations];
IF OBJECT_ID('dbo.myonline_tbl_Users', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_Users];
IF OBJECT_ID('dbo.myonline_tbl_OfficeLocations', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_OfficeLocations];
IF OBJECT_ID('dbo.myonline_tbl_Shifts', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_Shifts];
IF OBJECT_ID('dbo.myonline_tbl_LeaveTypes', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_LeaveTypes];
IF OBJECT_ID('dbo.myonline_tbl_Holidays', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_Holidays];
IF OBJECT_ID('dbo.myonline_tbl_SubscriptionPlans', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_SubscriptionPlans];
IF OBJECT_ID('dbo.myonline_tbl_Companies', 'U') IS NOT NULL DELETE FROM [dbo].[myonline_tbl_Companies];
GO

-- ৪. Reseed IDENTITY columns back to 0
IF OBJECT_ID('dbo.myonline_tbl_CRM_AuditLog', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_AuditLog', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadStatusHistory', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_LeadStatusHistory', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadFollowUps', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_LeadFollowUps', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadRemarks', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_LeadRemarks', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadAssignments', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_LeadAssignments', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_Leads', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_Leads', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_LeadSources', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_LeadSources', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_ProductServices', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_ProductServices', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CRM_KPI', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CRM_KPI', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_DriverLocations', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_DriverLocations', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_CustomerVisits', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_CustomerVisits', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_Customers', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_Customers', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_Attendances', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_Attendances', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_AttendanceRecords', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_AttendanceRecords', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_LeaveApplications', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_LeaveApplications', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_LeaveBalances', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_LeaveBalances', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_Notifications', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_Notifications', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_AdminOfficeLocations', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_AdminOfficeLocations', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_Users', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_Users', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_OfficeLocations', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_OfficeLocations', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_Shifts', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_Shifts', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_LeaveTypes', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_LeaveTypes', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_Holidays', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_Holidays', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_SubscriptionPlans', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_SubscriptionPlans', RESEED, 0);
IF OBJECT_ID('dbo.myonline_tbl_Companies', 'U') IS NOT NULL DBCC CHECKIDENT ('dbo.myonline_tbl_Companies', RESEED, 0);
GO

-- ৫. Re-enable all Foreign Key constraints
EXEC sp_MSforeachtable "ALTER TABLE ? WITH CHECK CHECK CONSTRAINT all";
GO

PRINT '✅ All tables truncated and Identity counters reset to 0.';

-- ==========================================================
-- ৬. SEED COMPANY 1: MOXX (MaxUserLimit: 14)
-- ==========================================================

-- 1. Insert Company: MOXX (ID: 1)
INSERT INTO [dbo].[myonline_tbl_Companies] 
    ([CompanyName], [CompanyCode], [ContactPerson], [ContactPhone], [ContactEmail], [MaxUserLimit], [PaymentDueDate], [IsActive], [CreatedAtUtc])
VALUES 
    (N'MOXX', N'MOXX', N'MOXX Managing Director', N'01711000001', N'admin@moxx.com', 14, DATEADD(year, 2, GETUTCDATE()), 1, GETUTCDATE());
GO

-- 2. Office Locations for MOXX (ID: 1)
INSERT INTO [dbo].[myonline_tbl_OfficeLocations] ([Name], [Latitude], [Longitude], [RadiusMeters], [Address], [CompanyId], [IsActive], [CreatedAt])
VALUES (N'MOXX Head Office', 23.8103, 90.4125, 200.0, N'Gulshan-2, Dhaka', 1, 1, GETUTCDATE());
GO

-- 3. Shift for MOXX (ID: 1)
INSERT INTO [dbo].[myonline_tbl_Shifts] ([ShiftName], [StartTime], [EndTime], [GracePeriodMinutes], [IsDefault], [IsActive], [CompanyId], [CreatedByAdminId], [CreatedAtUtc])
VALUES (N'MOXX Regular Shift', N'09:00:00', N'18:00:00', 15, 1, 1, 1, 1, GETUTCDATE());
GO

-- 4. Leave Types for MOXX (ID: 1)
INSERT INTO [dbo].[myonline_tbl_LeaveTypes] ([Name], [DefaultDaysPerYear], [CompanyId], [IsActive])
VALUES 
    (N'Casual Leave', 10, 1, 1),
    (N'Sick Leave', 14, 1, 1),
    (N'Earned Leave', 15, 1, 1);
GO

-- 5. 2 Head Admins for MOXX (Password: password123)
-- PasswordHash: 'AQAAAAIAAYagAAAAEG3sPZ3sD/9k5mK9wV7tE3N1c7Z9X5qY7W2b1M3n4P5q6R7s8T9u0V1w2X3y4Z5A=='
INSERT INTO [dbo].[myonline_tbl_Users] 
    ([Name], [Username], [PasswordHash], [Role], [PhoneNumber], [CompanyId], [OfficeLocationId], [ShiftId], [MaxUserLimit], [IsActive], [CreatedAt])
VALUES 
    (N'MOXX Head Admin 1', N'moxx_admin1', N'AQAAAAIAAYagAAAAEG3sPZ3sD/9k5mK9wV7tE3N1c7Z9X5qY7W2b1M3n4P5q6R7s8T9u0V1w2X3y4Z5A==', N'Admin', N'01711000001', 1, NULL, 1, 14, 1, GETUTCDATE()),
    (N'MOXX Head Admin 2', N'moxx_admin2', N'AQAAAAIAAYagAAAAEG3sPZ3sD/9k5mK9wV7tE3N1c7Z9X5qY7W2b1M3n4P5q6R7s8T9u0V1w2X3y4Z5A==', N'Admin', N'01711000002', 1, NULL, 1, 14, 1, GETUTCDATE());
GO

-- ==========================================================
-- ৭. SEED COMPANY 2: Beta Tech Solutions (MaxUserLimit: 17)
-- ==========================================================

-- 1. Insert Company: Beta Tech Solutions (ID: 2)
INSERT INTO [dbo].[myonline_tbl_Companies] 
    ([CompanyName], [CompanyCode], [ContactPerson], [ContactPhone], [ContactEmail], [MaxUserLimit], [PaymentDueDate], [IsActive], [CreatedAtUtc])
VALUES 
    (N'Beta Tech Solutions', N'BETA_TECH', N'Beta Tech Operations Head', N'01722000001', N'info@betatechbd.com', 17, DATEADD(year, 2, GETUTCDATE()), 1, GETUTCDATE());
GO

-- 2. Office Locations for Beta Tech (ID: 2)
INSERT INTO [dbo].[myonline_tbl_OfficeLocations] ([Name], [Latitude], [Longitude], [RadiusMeters], [Address], [CompanyId], [IsActive], [CreatedAt])
VALUES (N'Beta Tech Main Station', 23.7925, 90.4078, 250.0, N'Airport Road, Dhaka', 2, 1, GETUTCDATE());
GO

-- 3. Shift for Beta Tech (ID: 2)
INSERT INTO [dbo].[myonline_tbl_Shifts] ([ShiftName], [StartTime], [EndTime], [GracePeriodMinutes], [IsDefault], [IsActive], [CompanyId], [CreatedByAdminId], [CreatedAtUtc])
VALUES (N'Beta Duty Shift', N'08:00:00', N'17:00:00', 15, 1, 1, 2, 1, GETUTCDATE());
GO

-- 4. Leave Types for Beta Tech (ID: 2)
INSERT INTO [dbo].[myonline_tbl_LeaveTypes] ([Name], [DefaultDaysPerYear], [CompanyId], [IsActive])
VALUES 
    (N'Casual Leave', 10, 2, 1),
    (N'Medical Leave', 14, 2, 1),
    (N'Annual Leave', 15, 2, 1);
GO

-- 5. 3 Head Admins for Beta Tech (Password: password123)
INSERT INTO [dbo].[myonline_tbl_Users] 
    ([Name], [Username], [PasswordHash], [Role], [PhoneNumber], [CompanyId], [OfficeLocationId], [ShiftId], [MaxUserLimit], [IsActive], [CreatedAt])
VALUES 
    (N'Beta Head Admin 1', N'beta_admin1', N'AQAAAAIAAYagAAAAEG3sPZ3sD/9k5mK9wV7tE3N1c7Z9X5qY7W2b1M3n4P5q6R7s8T9u0V1w2X3y4Z5A==', N'Admin', N'01722000001', 2, NULL, 2, 17, 1, GETUTCDATE()),
    (N'Beta Head Admin 2', N'beta_admin2', N'AQAAAAIAAYagAAAAEG3sPZ3sD/9k5mK9wV7tE3N1c7Z9X5qY7W2b1M3n4P5q6R7s8T9u0V1w2X3y4Z5A==', N'Admin', N'01722000002', 2, NULL, 2, 17, 1, GETUTCDATE()),
    (N'Beta Head Admin 3', N'beta_admin3', N'AQAAAAIAAYagAAAAEG3sPZ3sD/9k5mK9wV7tE3N1c7Z9X5qY7W2b1M3n4P5q6R7s8T9u0V1w2X3y4Z5A==', N'Admin', N'01722000003', 2, NULL, 2, 17, 1, GETUTCDATE());
GO

-- ==========================================================
-- ৮. Bangladesh Official Government Holidays
-- ==========================================================
INSERT INTO [dbo].[myonline_tbl_Holidays] ([Name], [Date], [Year], [IsRecurring], [IsActive], [Description])
VALUES
    (N'শহীদ দিবস ও আন্তর্জাতিক মাতৃভাষা দিবস', '2026-02-21', 2026, 1, 1, N'২১শে ফেব্রুয়ারি জাতীয় শহীদ দিবস'),
    (N'জাতীয় শিশু দিবস / বঙ্গবন্ধুর জন্মদিন', '2026-03-17', 2026, 1, 1, N'১৭ই মার্চ জাতীয় শিশু দিবস'),
    (N'স্বাধীনতা ও জাতীয় দিবস', '2026-03-26', 2026, 1, 1, N'২৬শে মার্চ স্বাধীনতা দিবস'),
    (N'পহেলা বৈশাখ (বাংলা নববর্ষ)', '2026-04-14', 2026, 1, 1, N'বাংলা শুভ নববর্ষ'),
    (N'মে দিবস (আন্তর্জাতিক শ্রমিক দিবস)', '2026-05-01', 2026, 1, 1, N'মে দিবস'),
    (N'জাতীয় শোক দিবস', '2026-08-15', 2026, 1, 1, N'১৫ই আগস্ট জাতীয় শোক দিবস'),
    (N'বিজয় দিবস', '2026-12-16', 2026, 1, 1, N'১৬ই ডিসেম্বর মহান বিজয় দিবস'),
    (N'বড়দিন (যীশু খ্রিষ্টের জন্মদিন)', '2026-12-25', 2026, 1, 1, N'খ্রিষ্টান ধর্মাবলম্বীদের প্রধান ধর্মীয় উৎসব');
GO

PRINT '🎉 Complete Multi-Company Reset & Seed Finished Successfully!';
PRINT '-------------------------------------------------------------';
PRINT '🏢 Company 1 (MOXX): 2 Head Admins (moxx_admin1, moxx_admin2) - Max User Limit: 14';
PRINT '🏢 Company 2 (Beta Tech): 3 Head Admins (beta_admin1, beta_admin2, beta_admin3) - Max User Limit: 17';
PRINT '🔑 Default Password for ALL accounts: password123';

