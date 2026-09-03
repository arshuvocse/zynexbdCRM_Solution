-- ==============================================================================
-- CRM PRODUCT/SERVICE & DYNAMIC COMPANY BRANDING MIGRATION & PROCEDURES
-- Database: crm_solution_DB
-- Strict Multi-Tenant Enforcement & Duplicate Validation
-- ==============================================================================

USE [crm_solution_DB];
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

-- 1. Add LogoUrl to myonline_tbl_Companies if missing
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'myonline_tbl_Companies' AND COLUMN_NAME = 'LogoUrl'
)
BEGIN
    ALTER TABLE dbo.myonline_tbl_Companies ADD LogoUrl NVARCHAR(500) NULL;
    PRINT 'Added LogoUrl column to myonline_tbl_Companies.';
END
GO

-- 2. Seed / Update Logos for Company 1 and Company 2
UPDATE dbo.myonline_tbl_Companies
SET LogoUrl = '/uploads/companies/company1_logo.png'
WHERE CompanyId = 1 AND (LogoUrl IS NULL OR LogoUrl = '');

UPDATE dbo.myonline_tbl_Companies
SET LogoUrl = '/uploads/companies/company2_logo.png'
WHERE CompanyId = 2 AND (LogoUrl IS NULL OR LogoUrl = '');
GO

-- 3. Composite Performance Index on myonline_tbl_CRM_ProductServices
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes 
    WHERE name = 'IX_CRM_ProductServices_Company_Active_Name' 
      AND object_id = OBJECT_ID('dbo.myonline_tbl_CRM_ProductServices')
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_CRM_ProductServices_Company_Active_Name
    ON dbo.myonline_tbl_CRM_ProductServices (CompanyId, IsActive, Name)
    INCLUDE (Code, Price, Description, CreatedAtUtc, UpdatedAtUtc);
    PRINT 'Created index IX_CRM_ProductServices_Company_Active_Name.';
END
GO

-- ==============================================================================
-- 4. sp_Crm_ProductService_GetList (Searchable, Multi-Tenant, Select2 Backend)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_ProductService_GetList
    @CompanyId INT,
    @Search NVARCHAR(100) = NULL,
    @ActiveOnly BIT = 1,
    @PageNumber INT = 1,
    @PageSize INT = 50
AS
BEGIN
    SET NOCOUNT ON;

    IF @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize < 1 SET @PageSize = 50;

    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;
    SET @Search = LTRIM(RTRIM(@Search));

    WITH FilteredItems AS (
        SELECT 
            p.ProductServiceId,
            p.CompanyId,
            p.Name,
            p.Code,
            p.Description,
            p.Price,
            p.IsActive,
            p.CreatedAtUtc,
            p.UpdatedAtUtc
        FROM dbo.myonline_tbl_CRM_ProductServices p
        WHERE p.CompanyId = @CompanyId
          AND (@ActiveOnly = 0 OR p.IsActive = 1)
          AND (
              @Search IS NULL OR @Search = '' OR
              p.Name LIKE '%' + @Search + '%' OR
              p.Code LIKE '%' + @Search + '%' OR
              p.Description LIKE '%' + @Search + '%'
          )
    )
    SELECT 
        fi.*,
        TotalCount = COUNT(*) OVER()
    FROM FilteredItems fi
    ORDER BY fi.IsActive DESC, fi.Name ASC
    OFFSET @Offset ROWS
    FETCH NEXT @PageSize ROWS ONLY
    OPTION (RECOMPILE);
END;
GO

-- ==============================================================================
-- 5. sp_Crm_ProductService_Save (Insert / Update with Duplicate Check)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_ProductService_Save
    @ProductServiceId INT = NULL OUTPUT,
    @CompanyId INT,
    @Name NVARCHAR(150),
    @Code NVARCHAR(50) = NULL,
    @Description NVARCHAR(500) = NULL,
    @Price DECIMAL(18,2) = NULL,
    @IsActive BIT = 1
AS
BEGIN
    SET NOCOUNT ON;

    -- 1. Validate Company
    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_Companies WHERE CompanyId = @CompanyId AND IsActive = 1)
    BEGIN
        RAISERROR('Invalid or inactive CompanyId: %d', 16, 1, @CompanyId);
        RETURN;
    END

    SET @Name = LTRIM(RTRIM(@Name));
    IF @Name IS NULL OR @Name = ''
    BEGIN
        RAISERROR('Product/Service Name is required.', 16, 1);
        RETURN;
    END

    SET @Code = LTRIM(RTRIM(@Code));
    IF @Code = '' SET @Code = NULL;

    SET @Description = LTRIM(RTRIM(@Description));
    IF @Description = '' SET @Description = NULL;

    -- 2. Duplicate Validation (Company + Name)
    IF EXISTS (
        SELECT 1 
        FROM dbo.myonline_tbl_CRM_ProductServices 
        WHERE CompanyId = @CompanyId 
          AND LOWER(LTRIM(RTRIM(Name))) = LOWER(@Name)
          AND (@ProductServiceId IS NULL OR @ProductServiceId <= 0 OR ProductServiceId <> @ProductServiceId)
    )
    BEGIN
        RAISERROR('A product or service with the name "%s" already exists in your organization.', 16, 1, @Name);
        RETURN;
    END

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();

    -- 3. Insert vs Update
    IF @ProductServiceId IS NULL OR @ProductServiceId <= 0
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_ProductServices (
            CompanyId, Name, Code, Description, Price, IsActive, CreatedAtUtc, UpdatedAtUtc
        )
        VALUES (
            @CompanyId, @Name, @Code, @Description, @Price, @IsActive, @Now, @Now
        );

        SET @ProductServiceId = SCOPE_IDENTITY();
    END
    ELSE
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_ProductServices WHERE ProductServiceId = @ProductServiceId AND CompanyId = @CompanyId)
        BEGIN
            RAISERROR('Product/Service ID %d not found under Company %d', 16, 1, @ProductServiceId, @CompanyId);
            RETURN;
        END

        UPDATE dbo.myonline_tbl_CRM_ProductServices
        SET Name = @Name,
            Code = @Code,
            Description = @Description,
            Price = @Price,
            IsActive = @IsActive,
            UpdatedAtUtc = @Now
        WHERE ProductServiceId = @ProductServiceId AND CompanyId = @CompanyId;
    END

    -- Return the saved record
    SELECT 
        ProductServiceId, CompanyId, Name, Code, Description, Price, IsActive, CreatedAtUtc, UpdatedAtUtc
    FROM dbo.myonline_tbl_CRM_ProductServices
    WHERE ProductServiceId = @ProductServiceId AND CompanyId = @CompanyId;
END;
GO

-- ==============================================================================
-- 6. sp_Crm_ProductService_ToggleStatus (Soft Activate / Inactivate)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_ProductService_ToggleStatus
    @CompanyId INT,
    @ProductServiceId INT,
    @IsActive BIT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_ProductServices WHERE ProductServiceId = @ProductServiceId AND CompanyId = @CompanyId)
    BEGIN
        RAISERROR('Product/Service ID %d not found under Company %d', 16, 1, @ProductServiceId, @CompanyId);
        RETURN;
    END

    UPDATE dbo.myonline_tbl_CRM_ProductServices
    SET IsActive = @IsActive,
        UpdatedAtUtc = SYSUTCDATETIME()
    WHERE ProductServiceId = @ProductServiceId AND CompanyId = @CompanyId;

    SELECT 
        ProductServiceId, CompanyId, Name, Code, Description, Price, IsActive, CreatedAtUtc, UpdatedAtUtc
    FROM dbo.myonline_tbl_CRM_ProductServices
    WHERE ProductServiceId = @ProductServiceId AND CompanyId = @CompanyId;
END;
GO

-- ==============================================================================
-- 7. sp_Crm_Company_GetBranding (Tenant Isolated Company Branding)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Company_GetBranding
    @CompanyId INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT 
        CompanyId,
        CompanyName,
        CompanyCode,
        LogoUrl = COALESCE(LogoUrl, ''),
        ContactPhone = COALESCE(ContactPhone, ''),
        ContactEmail = COALESCE(ContactEmail, ''),
        ContactPerson = COALESCE(ContactPerson, '')
    FROM dbo.myonline_tbl_Companies
    WHERE CompanyId = @CompanyId AND IsActive = 1;
END;
GO

-- ==============================================================================
-- 8. sp_Crm_Company_UpdateBranding (Update Name or Logo)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Company_UpdateBranding
    @CompanyId INT,
    @CompanyName NVARCHAR(200) = NULL,
    @LogoUrl NVARCHAR(500) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_Companies WHERE CompanyId = @CompanyId AND IsActive = 1)
    BEGIN
        RAISERROR('Company ID %d not found or inactive', 16, 1, @CompanyId);
        RETURN;
    END

    UPDATE dbo.myonline_tbl_Companies
    SET CompanyName = COALESCE(@CompanyName, CompanyName),
        LogoUrl = COALESCE(@LogoUrl, LogoUrl),
        UpdatedAtUtc = SYSUTCDATETIME()
    WHERE CompanyId = @CompanyId;

    EXEC dbo.sp_Crm_Company_GetBranding @CompanyId = @CompanyId;
END;
GO

PRINT 'SUCCESS: CRM Product/Service and Company Branding migration completed successfully.';
GO
