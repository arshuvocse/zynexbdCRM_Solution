-- ==========================================================
-- MULTI-TENANT SAAS ARCHITECTURE: COMPANIES, COLUMNS & STORED PROCEDURES
-- Database: LiveTrackingDB
-- ==========================================================

USE [LiveTrackingDB];
GO

-- ==========================================================
-- ১. Create Companies Table (কোম্পানি টেবিল)
-- ==========================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'myonline_tbl_Companies')
BEGIN
    CREATE TABLE [dbo].[myonline_tbl_Companies] (
        [CompanyId] INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [CompanyName] NVARCHAR(200) NOT NULL,
        [CompanyCode] NVARCHAR(50) NOT NULL,
        [ContactPerson] NVARCHAR(150) NULL,
        [ContactPhone] NVARCHAR(30) NULL,
        [ContactEmail] NVARCHAR(150) NULL,
        [MaxUserLimit] INT NOT NULL DEFAULT(10),
        [PaymentDueDate] DATETIME2(7) NULL,
        [IsActive] BIT NOT NULL DEFAULT(1),
        [CreatedAtUtc] DATETIME2(7) NOT NULL DEFAULT(GETUTCDATE()),
        [UpdatedAtUtc] DATETIME2(7) NULL
    );

    CREATE UNIQUE INDEX [IX_myonline_tbl_Companies_CompanyCode] 
        ON [dbo].[myonline_tbl_Companies]([CompanyCode]);
    
    PRINT '✅ Created table: myonline_tbl_Companies';
END
GO

-- ==========================================================
-- ২. Seed Default Company (প্রাথমিক ডিফল্ট কোম্পানি এন্ট্রি)
-- ==========================================================
IF NOT EXISTS (SELECT 1 FROM [dbo].[myonline_tbl_Companies])
BEGIN
    INSERT INTO [dbo].[myonline_tbl_Companies] 
        ([CompanyName], [CompanyCode], [ContactPerson], [ContactPhone], [MaxUserLimit], [PaymentDueDate], [IsActive], [CreatedAtUtc])
    VALUES 
        (N'Default Organization', N'DEFAULT_ORG', N'System Administrator', N'01700000000', 50, DATEADD(month, 12, GETUTCDATE()), 1, GETUTCDATE());
    
    PRINT '✅ Inserted default Company: Default Organization (ID: 1)';
END
GO

-- ==========================================================
-- ৩. Add CompanyId to OfficeLocations Table (অফিস টেবিলে কলাম যুক্ত)
-- ==========================================================
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[myonline_tbl_OfficeLocations]') AND name = 'CompanyId')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_OfficeLocations]
    ADD [CompanyId] INT NULL;

    PRINT '✅ Added column CompanyId to myonline_tbl_OfficeLocations';
END
GO

-- বিদ্যমান সমস্ত অফিস লোকেশনে ডিফল্ট CompanyId সেট করা
UPDATE [dbo].[myonline_tbl_OfficeLocations]
SET [CompanyId] = (SELECT TOP 1 [CompanyId] FROM [dbo].[myonline_tbl_Companies])
WHERE [CompanyId] IS NULL;
GO

-- Foreign Key Constraint
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_myonline_tbl_OfficeLocations_Companies')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_OfficeLocations]
    ADD CONSTRAINT [FK_myonline_tbl_OfficeLocations_Companies]
    FOREIGN KEY ([CompanyId]) REFERENCES [dbo].[myonline_tbl_Companies]([CompanyId]);

    PRINT '✅ Added FK_myonline_tbl_OfficeLocations_Companies';
END
GO

-- ==========================================================
-- ৪. Add CompanyId to Users Table (ইউজার টেবিলে কলাম যুক্ত)
-- ==========================================================
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[myonline_tbl_Users]') AND name = 'CompanyId')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_Users]
    ADD [CompanyId] INT NULL;

    PRINT '✅ Added column CompanyId to myonline_tbl_Users';
END
GO

-- বিদ্যমান সমস্ত ইউজারে ডিফল্ট CompanyId সেট করা
UPDATE [dbo].[myonline_tbl_Users]
SET [CompanyId] = (SELECT TOP 1 [CompanyId] FROM [dbo].[myonline_tbl_Companies])
WHERE [CompanyId] IS NULL;
GO

-- Foreign Key Constraint
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_myonline_tbl_Users_Companies')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_Users]
    ADD CONSTRAINT [FK_myonline_tbl_Users_Companies]
    FOREIGN KEY ([CompanyId]) REFERENCES [dbo].[myonline_tbl_Companies]([CompanyId]);

    PRINT '✅ Added FK_myonline_tbl_Users_Companies';
END
GO

-- ==========================================================
-- ৫. Add CompanyId to Shifts Table (শিফট টেবিলে কলাম যুক্ত)
-- ==========================================================
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[myonline_tbl_Shifts]') AND name = 'CompanyId')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_Shifts]
    ADD [CompanyId] INT NULL;

    PRINT '✅ Added column CompanyId to myonline_tbl_Shifts';
END
GO

UPDATE [dbo].[myonline_tbl_Shifts]
SET [CompanyId] = (SELECT TOP 1 [CompanyId] FROM [dbo].[myonline_tbl_Companies])
WHERE [CompanyId] IS NULL;
GO

-- ==========================================================
-- ৬. Add CompanyId to LeaveTypes Table (লিভ টাইপ টেবিলে কলাম যুক্ত)
-- ==========================================================
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[myonline_tbl_LeaveTypes]') AND name = 'CompanyId')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_LeaveTypes]
    ADD [CompanyId] INT NULL;

    PRINT '✅ Added column CompanyId to myonline_tbl_LeaveTypes';
END
GO

UPDATE [dbo].[myonline_tbl_LeaveTypes]
SET [CompanyId] = (SELECT TOP 1 [CompanyId] FROM [dbo].[myonline_tbl_Companies])
WHERE [CompanyId] IS NULL;
GO

-- ==========================================================
-- ৭. Add CompanyId to AppVersions Table (অ্যাপ ভার্সন টেবিলে কলাম যুক্ত)
-- ==========================================================
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[myonline_tbl_AppVersions]') AND name = 'CompanyId')
BEGIN
    ALTER TABLE [dbo].[myonline_tbl_AppVersions]
    ADD [CompanyId] INT NULL;

    PRINT '✅ Added column CompanyId to myonline_tbl_AppVersions';
END
GO

-- ==========================================================
-- ৫. STORED PROCEDURE 1: sp_GetCompanySummary
-- ==========================================================
CREATE OR ALTER PROCEDURE [dbo].[sp_GetCompanySummary]
    @CompanyId INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT 
        c.[CompanyId],
        c.[CompanyName],
        c.[CompanyCode],
        c.[ContactPerson],
        c.[ContactPhone],
        c.[ContactEmail],
        c.[MaxUserLimit],
        c.[PaymentDueDate],
        c.[IsActive],
        c.[CreatedAtUtc],
        (SELECT COUNT(1) FROM [dbo].[myonline_tbl_OfficeLocations] o WHERE o.[CompanyId] = c.[CompanyId] AND o.[IsActive] = 1) AS [TotalOffices],
        (SELECT COUNT(1) FROM [dbo].[myonline_tbl_Users] u WHERE u.[CompanyId] = c.[CompanyId] AND u.[Role] = 'Admin' AND u.[IsActive] = 1) AS [TotalAdmins],
        (SELECT COUNT(1) FROM [dbo].[myonline_tbl_Users] u WHERE u.[CompanyId] = c.[CompanyId] AND u.[Role] = 'User' AND u.[IsActive] = 1) AS [ActiveOfficersCount],
        CASE 
            WHEN c.[PaymentDueDate] IS NULL THEN 'Active'
            WHEN c.[PaymentDueDate] < GETUTCDATE() THEN 'Expired'
            WHEN DATEDIFF(day, GETUTCDATE(), c.[PaymentDueDate]) <= 5 THEN 'DueSoon'
            ELSE 'Active'
        END AS [SubscriptionStatus],
        CASE 
            WHEN (SELECT COUNT(1) FROM [dbo].[myonline_tbl_Users] u WHERE u.[CompanyId] = c.[CompanyId] AND u.[Role] = 'User' AND u.[IsActive] = 1) >= c.[MaxUserLimit] THEN 1
            ELSE 0
        END AS [IsQuotaFull]
    FROM [dbo].[myonline_tbl_Companies] c
    WHERE c.[CompanyId] = @CompanyId;
END
GO

-- ==========================================================
-- ৬. STORED PROCEDURE 2: sp_ValidateCompanyUserQuota
-- ==========================================================
CREATE OR ALTER PROCEDURE [dbo].[sp_ValidateCompanyUserQuota]
    @CompanyId INT,
    @CanAdd BIT OUTPUT,
    @Message NVARCHAR(300) OUTPUT,
    @CurrentCount INT OUTPUT,
    @MaxLimit INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @IsActive BIT;
    DECLARE @PaymentDueDate DATETIME2(7);

    SELECT 
        @MaxLimit = [MaxUserLimit],
        @IsActive = [IsActive],
        @PaymentDueDate = [PaymentDueDate]
    FROM [dbo].[myonline_tbl_Companies]
    WHERE [CompanyId] = @CompanyId;

    IF @MaxLimit IS NULL
    BEGIN
        SET @CanAdd = 0;
        SET @Message = N'Company not found.';
        SET @CurrentCount = 0;
        SET @MaxLimit = 0;
        RETURN;
    END

    IF @IsActive = 0
    BEGIN
        SET @CanAdd = 0;
        SET @Message = N'কোম্পানি অ্যাকাউন্টটি বর্তমানে নিষ্ক্রিয় রয়েছে।';
        SET @CurrentCount = 0;
        RETURN;
    END

    SELECT @CurrentCount = COUNT(1)
    FROM [dbo].[myonline_tbl_Users]
    WHERE [CompanyId] = @CompanyId AND [Role] = 'User' AND [IsActive] = 1;

    IF @CurrentCount >= @MaxLimit
    BEGIN
        SET @CanAdd = 0;
        SET @Message = CONCAT(N'ইউজার লিমিট পূর্ণ হয়েছে! আপনার কোম্পানির সর্বোচ্চ ইউজার সংখ্যা: ', @MaxLimit, N' জন (বর্তমান: ', @CurrentCount, N' জন)।');
    END
    ELSE
    BEGIN
        SET @CanAdd = 1;
        SET @Message = N'Quota available.';
    END
END
GO

-- ==========================================================
-- ৭. STORED PROCEDURE 3: sp_GetCompanyBranchesAndOfficers
-- (Using exact DB column names: Id, Name, CreatedAt)
-- ==========================================================
CREATE OR ALTER PROCEDURE [dbo].[sp_GetCompanyBranchesAndOfficers]
    @CompanyId INT
AS
BEGIN
    SET NOCOUNT ON;

    -- Result Set 1: Branches (ব্রাঞ্চসমূহ)
    SELECT 
        [Id] AS [OfficeLocationId],
        [Name],
        [Address],
        [Latitude],
        [Longitude],
        [RadiusMeters],
        [IsActive]
    FROM [dbo].[myonline_tbl_OfficeLocations]
    WHERE [CompanyId] = @CompanyId AND [IsActive] = 1
    ORDER BY [Name];

    -- Result Set 2: Officers & Admins (কর্মকর্তা ও কর্মচারীগণ)
    SELECT 
        u.[Id] AS [UserId],
        u.[Username],
        u.[Name] AS [FullName],
        u.[PhoneNumber],
        u.[Role],
        u.[OfficeLocationId],
        o.[Name] AS [OfficeLocationName],
        u.[IsActive],
        u.[CreatedAt] AS [CreatedAtUtc]
    FROM [dbo].[myonline_tbl_Users] u
    LEFT JOIN [dbo].[myonline_tbl_OfficeLocations] o ON u.[OfficeLocationId] = o.[Id]
    WHERE u.[CompanyId] = @CompanyId
    ORDER BY u.[Role], u.[Name];
END
GO

PRINT '🎉 Multi-Tenant Company Tables, Columns and Stored Procedures executed successfully!';
