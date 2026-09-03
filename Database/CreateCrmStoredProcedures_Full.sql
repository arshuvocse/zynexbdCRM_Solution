-- ==============================================================================
-- 12 ENTERPRISE CRM STORED PROCEDURES
-- Database: crm_solution_DB
-- Strict Multi-Tenant Isolation & Audit Trail Preservation
-- ==============================================================================

USE [crm_solution_DB];
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

-- ==============================================================================
-- 1. sp_Crm_Lead_Save (Insert / Update Lead)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Lead_Save
    @LeadId INT = NULL OUTPUT,
    @CompanyId INT,
    @LeadName NVARCHAR(150),
    @ContactPerson NVARCHAR(100) = NULL,
    @Phone NVARCHAR(50) = NULL,
    @Email NVARCHAR(100) = NULL,
    @Address NVARCHAR(250) = NULL,
    @ProductServiceId INT = NULL,
    @LeadSourceId INT = NULL,
    @LeadSourceType NVARCHAR(50) = 'Manager',
    @LeadStatus NVARCHAR(50) = 'New Lead',
    @CreatedByUserId INT,
    @AssignedUserId INT = NULL,
    @OfficeLocationId INT = NULL,
    @EstimatedValue DECIMAL(18,2) = NULL,
    @NextFollowUpDate DATETIME2 = NULL,
    @Remarks NVARCHAR(500) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    -- Validate Company
    IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_Companies WHERE CompanyId = @CompanyId AND IsActive = 1)
    BEGIN
        RAISERROR('Invalid or inactive CompanyId: %d', 16, 1, @CompanyId);
        RETURN;
    END

    -- Validate Assigned User belongs to the same Company
    IF @AssignedUserId IS NOT NULL AND @AssignedUserId > 0
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_Users WHERE Id = @AssignedUserId AND CompanyId = @CompanyId AND IsActive = 1)
        BEGIN
            RAISERROR('Assigned employee ID %d does not belong to Company %d or is inactive', 16, 1, @AssignedUserId, @CompanyId);
            RETURN;
        END
    END

    -- Validate ProductService belongs to the same Company
    IF @ProductServiceId IS NOT NULL AND @ProductServiceId > 0
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_ProductServices WHERE ProductServiceId = @ProductServiceId AND CompanyId = @CompanyId)
        BEGIN
            RAISERROR('ProductService ID %d does not belong to Company %d', 16, 1, @ProductServiceId, @CompanyId);
            RETURN;
        END
    END

    -- Validate LeadSource belongs to the same Company
    IF @LeadSourceId IS NOT NULL AND @LeadSourceId > 0
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_LeadSources WHERE LeadSourceId = @LeadSourceId AND CompanyId = @CompanyId)
        BEGIN
            RAISERROR('LeadSource ID %d does not belong to Company %d', 16, 1, @LeadSourceId, @CompanyId);
            RETURN;
        END
    END

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();

    -- Check Insert vs Update
    IF @LeadId IS NULL OR @LeadId <= 0
    BEGIN
        -- INSERT NEW LEAD
        INSERT INTO dbo.myonline_tbl_CRM_Leads (
            CompanyId, LeadName, ContactPerson, Phone, Email, Address,
            ProductServiceId, LeadSourceId, LeadSourceType, LeadStatus,
            CreatedByUserId, AssignedUserId, OfficeLocationId, EstimatedValue,
            NextFollowUpDate, Remarks, IsActive, CreatedAtUtc, UpdatedAtUtc
        )
        VALUES (
            @CompanyId, @LeadName, @ContactPerson, @Phone, @Email, @Address,
            @ProductServiceId, @LeadSourceId, @LeadSourceType, @LeadStatus,
            @CreatedByUserId, @AssignedUserId, @OfficeLocationId, @EstimatedValue,
            @NextFollowUpDate, @Remarks, 1, @Now, @Now
        );

        SET @LeadId = SCOPE_IDENTITY();

        -- Record Initial Status History
        INSERT INTO dbo.myonline_tbl_CRM_LeadStatusHistory (
            CompanyId, LeadId, PreviousStatus, NewStatus, ChangedByUserId, ChangedDateUtc, Remarks
        )
        VALUES (
            @CompanyId, @LeadId, 'None', @LeadStatus, @CreatedByUserId, @Now, COALESCE(@Remarks, 'Lead created')
        );

        -- Record Assignment if assigned on create
        IF @AssignedUserId IS NOT NULL AND @AssignedUserId > 0
        BEGIN
            INSERT INTO dbo.myonline_tbl_CRM_LeadAssignments (
                CompanyId, LeadId, PreviousUserId, NewUserId, AssignedByUserId, AssignedDateUtc, Remarks, OfficeLocationId
            )
            VALUES (
                @CompanyId, @LeadId, NULL, @AssignedUserId, @CreatedByUserId, @Now, 'Initial Assignment upon creation', @OfficeLocationId
            );
        END
    END
    ELSE
    BEGIN
        -- UPDATE EXISTING LEAD (Multi-Tenant Enforced)
        DECLARE @ExistingStatus NVARCHAR(50);
        DECLARE @ExistingAssignee INT;

        SELECT @ExistingStatus = LeadStatus, @ExistingAssignee = AssignedUserId
        FROM dbo.myonline_tbl_CRM_Leads
        WHERE LeadId = @LeadId AND CompanyId = @CompanyId AND IsActive = 1;

        IF @ExistingStatus IS NULL
        BEGIN
            RAISERROR('Lead ID %d not found under Company %d', 16, 1, @LeadId, @CompanyId);
            RETURN;
        END

        UPDATE dbo.myonline_tbl_CRM_Leads
        SET LeadName = @LeadName,
            ContactPerson = @ContactPerson,
            Phone = @Phone,
            Email = @Email,
            Address = @Address,
            ProductServiceId = @ProductServiceId,
            LeadSourceId = @LeadSourceId,
            LeadStatus = @LeadStatus,
            EstimatedValue = @EstimatedValue,
            NextFollowUpDate = @NextFollowUpDate,
            OfficeLocationId = COALESCE(@OfficeLocationId, OfficeLocationId),
            AssignedUserId = COALESCE(@AssignedUserId, AssignedUserId),
            Remarks = COALESCE(@Remarks, Remarks),
            UpdatedAtUtc = @Now
        WHERE LeadId = @LeadId AND CompanyId = @CompanyId;

        -- Record status change if changed
        IF @ExistingStatus <> @LeadStatus
        BEGIN
            INSERT INTO dbo.myonline_tbl_CRM_LeadStatusHistory (
                CompanyId, LeadId, PreviousStatus, NewStatus, ChangedByUserId, ChangedDateUtc, Remarks
            )
            VALUES (
                @CompanyId, @LeadId, @ExistingStatus, @LeadStatus, @CreatedByUserId, @Now, COALESCE(@Remarks, 'Status update')
            );
        END

        -- Record assignment change if assignee changed
        IF @AssignedUserId IS NOT NULL AND @AssignedUserId > 0 AND (@ExistingAssignee IS NULL OR @ExistingAssignee <> @AssignedUserId)
        BEGIN
            INSERT INTO dbo.myonline_tbl_CRM_LeadAssignments (
                CompanyId, LeadId, PreviousUserId, NewUserId, AssignedByUserId, AssignedDateUtc, Remarks, OfficeLocationId
            )
            VALUES (
                @CompanyId, @LeadId, @ExistingAssignee, @AssignedUserId, @CreatedByUserId, @Now, 'Reassigned via Lead Update', @OfficeLocationId
            );
        END
    END

    -- Return the saved Lead
    SELECT 
        l.LeadId, l.CompanyId, l.LeadName, l.ContactPerson, l.Phone, l.Email, l.Address,
        l.ProductServiceId, p.Name AS ProductServiceName,
        l.LeadSourceId, s.Name AS LeadSourceName,
        l.LeadSourceType, l.LeadStatus,
        l.CreatedByUserId, uc.Name AS CreatedByUserName,
        l.AssignedUserId, ua.Name AS AssignedUserName,
        l.OfficeLocationId, o.Name AS OfficeLocationName,
        l.NextFollowUpDate, l.LastFollowUpDate, l.EstimatedValue, l.Remarks,
        l.IsActive, l.CreatedAtUtc, l.UpdatedAtUtc
    FROM dbo.myonline_tbl_CRM_Leads l
    LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
    LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
    LEFT JOIN dbo.myonline_tbl_Users uc ON l.CreatedByUserId = uc.Id
    LEFT JOIN dbo.myonline_tbl_Users ua ON l.AssignedUserId = ua.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
    WHERE l.LeadId = @LeadId AND l.CompanyId = @CompanyId;
END;
GO

-- ==============================================================================
-- 2. sp_Crm_Lead_GetList (Paginated, Multi-Tenant Scoped Lead Query)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Lead_GetList
    @CompanyId INT,
    @UserId INT = NULL,
    @AssignedUserId INT = NULL,
    @OfficeLocationId INT = NULL,
    @Status NVARCHAR(50) = NULL,
    @ProductServiceId INT = NULL,
    @LeadSourceId INT = NULL,
    @LeadSourceType NVARCHAR(50) = NULL,
    @Search NVARCHAR(100) = NULL,
    @FromDate DATETIME2 = NULL,
    @ToDate DATETIME2 = NULL,
    @PageNumber INT = 1,
    @PageSize INT = 20,
    @SortBy NVARCHAR(50) = 'CreatedAt',
    @SortOrder NVARCHAR(10) = 'DESC'
AS
BEGIN
    SET NOCOUNT ON;

    IF @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize < 1 SET @PageSize = 20;

    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;

    -- CTE with tenant and filter predicates
    WITH FilteredLeads AS (
        SELECT 
            l.LeadId, l.CompanyId, l.LeadName, l.ContactPerson, l.Phone, l.Email, l.Address,
            l.ProductServiceId, p.Name AS ProductServiceName,
            l.LeadSourceId, s.Name AS LeadSourceName,
            l.LeadSourceType, l.LeadStatus,
            l.CreatedByUserId, uc.Name AS CreatedByUserName,
            l.AssignedUserId, ua.Name AS AssignedUserName,
            l.OfficeLocationId, o.Name AS OfficeLocationName,
            l.NextFollowUpDate, l.LastFollowUpDate, l.EstimatedValue, l.Remarks,
            l.IsActive, l.CreatedAtUtc, l.UpdatedAtUtc
        FROM dbo.myonline_tbl_CRM_Leads l
        LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
        LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
        LEFT JOIN dbo.myonline_tbl_Users uc ON l.CreatedByUserId = uc.Id
        LEFT JOIN dbo.myonline_tbl_Users ua ON l.AssignedUserId = ua.Id
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
        WHERE l.CompanyId = @CompanyId
          AND l.IsActive = 1
          AND (@UserId IS NULL OR l.AssignedUserId = @UserId OR l.CreatedByUserId = @UserId)
          AND (@AssignedUserId IS NULL OR l.AssignedUserId = @AssignedUserId)
          AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
          AND (@Status IS NULL OR @Status = '' OR l.LeadStatus = @Status)
          AND (@ProductServiceId IS NULL OR @ProductServiceId <= 0 OR l.ProductServiceId = @ProductServiceId)
          AND (@LeadSourceId IS NULL OR @LeadSourceId <= 0 OR l.LeadSourceId = @LeadSourceId)
          AND (@LeadSourceType IS NULL OR @LeadSourceType = '' OR l.LeadSourceType = @LeadSourceType)
          AND (@FromDate IS NULL OR l.CreatedAtUtc >= @FromDate)
          AND (@ToDate IS NULL OR l.CreatedAtUtc <= @ToDate)
          AND (@Search IS NULL OR @Search = '' OR (
                l.LeadName LIKE '%' + @Search + '%' OR
                l.ContactPerson LIKE '%' + @Search + '%' OR
                l.Phone LIKE '%' + @Search + '%' OR
                l.Email LIKE '%' + @Search + '%'
          ))
    )
    SELECT 
        fl.*,
        TotalCount = COUNT(*) OVER()
    FROM FilteredLeads fl
    ORDER BY
        CASE WHEN @SortBy = 'LeadName' AND @SortOrder = 'ASC' THEN fl.LeadName END ASC,
        CASE WHEN @SortBy = 'LeadName' AND @SortOrder = 'DESC' THEN fl.LeadName END DESC,
        CASE WHEN @SortBy = 'LeadStatus' AND @SortOrder = 'ASC' THEN fl.LeadStatus END ASC,
        CASE WHEN @SortBy = 'LeadStatus' AND @SortOrder = 'DESC' THEN fl.LeadStatus END DESC,
        CASE WHEN @SortBy = 'NextFollowUpDate' AND @SortOrder = 'ASC' THEN fl.NextFollowUpDate END ASC,
        CASE WHEN @SortBy = 'NextFollowUpDate' AND @SortOrder = 'DESC' THEN fl.NextFollowUpDate END DESC,
        CASE WHEN (@SortBy IS NULL OR @SortBy = 'CreatedAt') AND @SortOrder = 'ASC' THEN fl.CreatedAtUtc END ASC,
        CASE WHEN (@SortBy IS NULL OR @SortBy = 'CreatedAt') AND (@SortOrder IS NULL OR @SortOrder = 'DESC') THEN fl.CreatedAtUtc END DESC
    OFFSET @Offset ROWS
    FETCH NEXT @PageSize ROWS ONLY;
END;
GO

-- ==============================================================================
-- 3. sp_Crm_Lead_GetById (Lead Details + Full Traceability History)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Lead_GetById
    @CompanyId INT,
    @LeadId INT,
    @RestrictedToUserId INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    -- Result 1: Lead Main Info
    SELECT 
        l.LeadId, l.CompanyId, l.LeadName, l.ContactPerson, l.Phone, l.Email, l.Address,
        l.ProductServiceId, p.Name AS ProductServiceName,
        l.LeadSourceId, s.Name AS LeadSourceName,
        l.LeadSourceType, l.LeadStatus,
        l.CreatedByUserId, uc.Name AS CreatedByUserName,
        l.AssignedUserId, ua.Name AS AssignedUserName,
        l.OfficeLocationId, o.Name AS OfficeLocationName,
        l.NextFollowUpDate, l.LastFollowUpDate, l.EstimatedValue, l.Remarks,
        l.IsActive, l.CreatedAtUtc, l.UpdatedAtUtc
    FROM dbo.myonline_tbl_CRM_Leads l
    LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
    LEFT JOIN dbo.myonline_tbl_CRM_LeadSources s ON l.LeadSourceId = s.LeadSourceId
    LEFT JOIN dbo.myonline_tbl_Users uc ON l.CreatedByUserId = uc.Id
    LEFT JOIN dbo.myonline_tbl_Users ua ON l.AssignedUserId = ua.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
    WHERE l.LeadId = @LeadId 
      AND l.CompanyId = @CompanyId 
      AND l.IsActive = 1
      AND (@RestrictedToUserId IS NULL OR l.AssignedUserId = @RestrictedToUserId);

    -- Result 2: Follow-Up History
    SELECT 
        fu.FollowUpId, fu.LeadId, fu.FollowUpDateUtc, fu.NextFollowUpDate,
        fu.Status, fu.Remarks, fu.CreatedByUserId, u.Name AS CreatedByUserName,
        fu.CreatedAtUtc, fu.OfficeLocationId, o.Name AS OfficeLocationName
    FROM dbo.myonline_tbl_CRM_LeadFollowUps fu
    LEFT JOIN dbo.myonline_tbl_Users u ON fu.CreatedByUserId = u.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON fu.OfficeLocationId = o.Id
    WHERE fu.LeadId = @LeadId AND fu.CompanyId = @CompanyId
    ORDER BY fu.FollowUpDateUtc DESC;

    -- Result 3: Remarks History
    SELECT 
        r.RemarkId, r.LeadId, r.UserId, u.Name AS UserName, r.Remark, r.CreatedAtUtc
    FROM dbo.myonline_tbl_CRM_LeadRemarks r
    LEFT JOIN dbo.myonline_tbl_Users u ON r.UserId = u.Id
    WHERE r.LeadId = @LeadId AND r.CompanyId = @CompanyId
    ORDER BY r.CreatedAtUtc DESC;

    -- Result 4: Assignment History (PreviousUserId -> NewUserId)
    SELECT 
        a.AssignmentId, a.LeadId,
        a.PreviousUserId, up.Name AS PreviousUserName,
        a.NewUserId, un.Name AS NewUserName,
        a.AssignedByUserId, ub.Name AS AssignedByUserName,
        a.AssignedDateUtc, a.Remarks,
        a.OfficeLocationId, o.Name AS OfficeLocationName
    FROM dbo.myonline_tbl_CRM_LeadAssignments a
    LEFT JOIN dbo.myonline_tbl_Users up ON a.PreviousUserId = up.Id
    LEFT JOIN dbo.myonline_tbl_Users un ON a.NewUserId = un.Id
    LEFT JOIN dbo.myonline_tbl_Users ub ON a.AssignedByUserId = ub.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON a.OfficeLocationId = o.Id
    WHERE a.LeadId = @LeadId AND a.CompanyId = @CompanyId
    ORDER BY a.AssignedDateUtc DESC;

    -- Result 5: Status History
    SELECT 
        sh.StatusHistoryId, sh.LeadId, sh.PreviousStatus, sh.NewStatus,
        sh.ChangedByUserId, u.Name AS ChangedByUserName, sh.ChangedDateUtc, sh.Remarks
    FROM dbo.myonline_tbl_CRM_LeadStatusHistory sh
    LEFT JOIN dbo.myonline_tbl_Users u ON sh.ChangedByUserId = u.Id
    WHERE sh.LeadId = @LeadId AND sh.CompanyId = @CompanyId
    ORDER BY sh.ChangedDateUtc DESC;
END;
GO

-- ==============================================================================
-- 4. sp_Crm_Lead_Assign (Assign Lead to Employee with Tenant Enforcement)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Lead_Assign
    @CompanyId INT,
    @LeadId INT,
    @AssignedByUserId INT,
    @NewUserId INT,
    @OfficeLocationId INT = NULL,
    @Remarks NVARCHAR(500) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    -- 1. Validate Lead exists under caller Company
    DECLARE @CurrentAssignee INT;
    SELECT @CurrentAssignee = AssignedUserId
    FROM dbo.myonline_tbl_CRM_Leads 
    WHERE LeadId = @LeadId AND CompanyId = @CompanyId AND IsActive = 1;

    IF @CurrentAssignee IS NULL AND NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_CRM_Leads WHERE LeadId = @LeadId AND CompanyId = @CompanyId AND IsActive = 1)
    BEGIN
        RAISERROR('Lead ID %d does not exist under Company %d', 16, 1, @LeadId, @CompanyId);
        RETURN;
    END

    -- 2. Validate Assignee User belongs to THE SAME Company
    DECLARE @AssigneeOffice INT;
    SELECT @AssigneeOffice = OfficeLocationId
    FROM dbo.myonline_tbl_Users
    WHERE Id = @NewUserId AND CompanyId = @CompanyId AND IsActive = 1;

    IF @AssigneeOffice IS NULL AND NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_Users WHERE Id = @NewUserId AND CompanyId = @CompanyId AND IsActive = 1)
    BEGIN
        RAISERROR('Employee ID %d does not exist or does not belong to Company %d. Cross-tenant assignment is strictly forbidden.', 16, 1, @NewUserId, @CompanyId);
        RETURN;
    END

    DECLARE @ResolvedOffice INT = COALESCE(@OfficeLocationId, @AssigneeOffice);
    DECLARE @Now DATETIME2 = SYSUTCDATETIME();

    -- 3. Update Lead
    UPDATE dbo.myonline_tbl_CRM_Leads
    SET AssignedUserId = @NewUserId,
        LeadSourceType = 'Assigned',
        OfficeLocationId = COALESCE(@ResolvedOffice, OfficeLocationId),
        UpdatedAtUtc = @Now
    WHERE LeadId = @LeadId AND CompanyId = @CompanyId;

    -- 4. Record in Assignment History (PreviousUserId -> NewUserId)
    INSERT INTO dbo.myonline_tbl_CRM_LeadAssignments (
        CompanyId, LeadId, PreviousUserId, NewUserId, AssignedByUserId, AssignedDateUtc, Remarks, OfficeLocationId
    )
    VALUES (
        @CompanyId, @LeadId, @CurrentAssignee, @NewUserId, @AssignedByUserId, @Now, COALESCE(@Remarks, 'Lead assigned to employee'), @ResolvedOffice
    );

    -- 5. Return updated lead
    EXEC dbo.sp_Crm_Lead_GetById @CompanyId = @CompanyId, @LeadId = @LeadId;
END;
GO

-- ==============================================================================
-- 5. sp_Crm_Lead_FollowUp_Save (Add Follow-Up, Remarks, and Status Transition)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Lead_FollowUp_Save
    @FollowUpId INT = NULL OUTPUT,
    @CompanyId INT,
    @LeadId INT,
    @UserId INT,
    @FollowUpDateUtc DATETIME2 = NULL,
    @NextFollowUpDate DATETIME2 = NULL,
    @Status NVARCHAR(50),
    @Remarks NVARCHAR(500),
    @OfficeLocationId INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    -- Validate Lead
    DECLARE @CurrentStatus NVARCHAR(50);
    SELECT @CurrentStatus = LeadStatus
    FROM dbo.myonline_tbl_CRM_Leads
    WHERE LeadId = @LeadId AND CompanyId = @CompanyId AND IsActive = 1;

    IF @CurrentStatus IS NULL
    BEGIN
        RAISERROR('Lead ID %d not found under Company %d', 16, 1, @LeadId, @CompanyId);
        RETURN;
    END

    IF @Remarks IS NULL OR LTRIM(RTRIM(@Remarks)) = ''
    BEGIN
        RAISERROR('Follow-up remarks cannot be empty', 16, 1);
        RETURN;
    END

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @ActualDate DATETIME2 = COALESCE(@FollowUpDateUtc, @Now);

    -- 1. Insert Follow-Up
    INSERT INTO dbo.myonline_tbl_CRM_LeadFollowUps (
        CompanyId, LeadId, FollowUpDateUtc, NextFollowUpDate, Status, Remarks, CreatedByUserId, CreatedAtUtc, OfficeLocationId
    )
    VALUES (
        @CompanyId, @LeadId, @ActualDate, @NextFollowUpDate, @Status, @Remarks, @UserId, @Now, @OfficeLocationId
    );

    SET @FollowUpId = SCOPE_IDENTITY();

    -- 2. Insert into Remarks
    INSERT INTO dbo.myonline_tbl_CRM_LeadRemarks (
        CompanyId, LeadId, UserId, Remark, CreatedAtUtc
    )
    VALUES (
        @CompanyId, @LeadId, @UserId, @Remarks, @Now
    );

    -- 3. Update Lead
    UPDATE dbo.myonline_tbl_CRM_Leads
    SET LastFollowUpDate = @ActualDate,
        NextFollowUpDate = @NextFollowUpDate,
        LeadStatus = @Status,
        UpdatedAtUtc = @Now
    WHERE LeadId = @LeadId AND CompanyId = @CompanyId;

    -- 4. Status History if changed
    IF @CurrentStatus <> @Status
    BEGIN
        INSERT INTO dbo.myonline_tbl_CRM_LeadStatusHistory (
            CompanyId, LeadId, PreviousStatus, NewStatus, ChangedByUserId, ChangedDateUtc, Remarks
        )
        VALUES (
            @CompanyId, @LeadId, @CurrentStatus, @Status, @UserId, @Now, @Remarks
        );
    END

    -- Return the created follow-up
    SELECT 
        fu.FollowUpId, fu.CompanyId, fu.LeadId, fu.FollowUpDateUtc, fu.NextFollowUpDate,
        fu.Status, fu.Remarks, fu.CreatedByUserId, u.Name AS CreatedByUserName,
        fu.CreatedAtUtc, fu.OfficeLocationId
    FROM dbo.myonline_tbl_CRM_LeadFollowUps fu
    LEFT JOIN dbo.myonline_tbl_Users u ON fu.CreatedByUserId = u.Id
    WHERE fu.FollowUpId = @FollowUpId AND fu.CompanyId = @CompanyId;
END;
GO

-- ==============================================================================
-- 6. sp_Crm_FollowUp_GetToday (Today's Pending Follow-ups)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_FollowUp_GetToday
    @CompanyId INT,
    @UserId INT = NULL,
    @OfficeLocationId INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Today DATE = CAST(SYSUTCDATETIME() AS DATE);

    SELECT 
        l.LeadId, l.CompanyId, l.LeadName, l.ContactPerson, l.Phone, l.Email,
        l.ProductServiceId, p.Name AS ProductServiceName,
        l.LeadStatus, l.OfficeLocationId, o.Name AS OfficeLocationName,
        l.AssignedUserId, u.Name AS AssignedUserName,
        l.NextFollowUpDate,
        DaysRemaining = DATEDIFF(day, @Today, CAST(l.NextFollowUpDate AS DATE)),
        IsOverdue = CAST(0 AS BIT)
    FROM dbo.myonline_tbl_CRM_Leads l
    LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
    LEFT JOIN dbo.myonline_tbl_Users u ON l.AssignedUserId = u.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
    WHERE l.CompanyId = @CompanyId
      AND l.IsActive = 1
      AND l.LeadStatus NOT IN ('Closed', 'Not Interested')
      AND l.NextFollowUpDate IS NOT NULL
      AND CAST(l.NextFollowUpDate AS DATE) = @Today
      AND (@UserId IS NULL OR l.AssignedUserId = @UserId)
      AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
    ORDER BY l.NextFollowUpDate ASC;
END;
GO

-- ==============================================================================
-- 7. sp_Crm_FollowUp_GetOverdue (Overdue Follow-ups)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_FollowUp_GetOverdue
    @CompanyId INT,
    @UserId INT = NULL,
    @OfficeLocationId INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Today DATE = CAST(SYSUTCDATETIME() AS DATE);

    SELECT 
        l.LeadId, l.CompanyId, l.LeadName, l.ContactPerson, l.Phone, l.Email,
        l.ProductServiceId, p.Name AS ProductServiceName,
        l.LeadStatus, l.OfficeLocationId, o.Name AS OfficeLocationName,
        l.AssignedUserId, u.Name AS AssignedUserName,
        l.NextFollowUpDate,
        DaysRemaining = DATEDIFF(day, @Today, CAST(l.NextFollowUpDate AS DATE)),
        IsOverdue = CAST(1 AS BIT)
    FROM dbo.myonline_tbl_CRM_Leads l
    LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
    LEFT JOIN dbo.myonline_tbl_Users u ON l.AssignedUserId = u.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
    WHERE l.CompanyId = @CompanyId
      AND l.IsActive = 1
      AND l.LeadStatus NOT IN ('Closed', 'Not Interested')
      AND l.NextFollowUpDate IS NOT NULL
      AND CAST(l.NextFollowUpDate AS DATE) < @Today
      AND (@UserId IS NULL OR l.AssignedUserId = @UserId)
      AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
    ORDER BY l.NextFollowUpDate ASC;
END;
GO

-- ==============================================================================
-- 8. sp_Crm_FollowUp_GetUpcoming (Future Follow-ups)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_FollowUp_GetUpcoming
    @CompanyId INT,
    @UserId INT = NULL,
    @OfficeLocationId INT = NULL,
    @DaysAhead INT = 30
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Today DATE = CAST(SYSUTCDATETIME() AS DATE);
    DECLARE @MaxDate DATE = DATEADD(day, @DaysAhead, @Today);

    SELECT 
        l.LeadId, l.CompanyId, l.LeadName, l.ContactPerson, l.Phone, l.Email,
        l.ProductServiceId, p.Name AS ProductServiceName,
        l.LeadStatus, l.OfficeLocationId, o.Name AS OfficeLocationName,
        l.AssignedUserId, u.Name AS AssignedUserName,
        l.NextFollowUpDate,
        DaysRemaining = DATEDIFF(day, @Today, CAST(l.NextFollowUpDate AS DATE)),
        IsOverdue = CAST(0 AS BIT)
    FROM dbo.myonline_tbl_CRM_Leads l
    LEFT JOIN dbo.myonline_tbl_CRM_ProductServices p ON l.ProductServiceId = p.ProductServiceId
    LEFT JOIN dbo.myonline_tbl_Users u ON l.AssignedUserId = u.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON l.OfficeLocationId = o.Id
    WHERE l.CompanyId = @CompanyId
      AND l.IsActive = 1
      AND l.LeadStatus NOT IN ('Closed', 'Not Interested')
      AND l.NextFollowUpDate IS NOT NULL
      AND CAST(l.NextFollowUpDate AS DATE) > @Today
      AND CAST(l.NextFollowUpDate AS DATE) <= @MaxDate
      AND (@UserId IS NULL OR l.AssignedUserId = @UserId)
      AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
    ORDER BY l.NextFollowUpDate ASC;
END;
GO

-- ==============================================================================
-- 9. sp_Crm_Kpi_Save (Set Employee / Organization KPI Targets)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Kpi_Save
    @KpiId INT = NULL OUTPUT,
    @CompanyId INT,
    @CreatedByUserId INT,
    @UserId INT = NULL,
    @PeriodType NVARCHAR(50), -- 'Daily', 'Weekly', 'Monthly'
    @FollowUpTarget INT,
    @InterestedTarget INT,
    @ClosedTarget INT,
    @EffectiveStartDate DATETIME2 = NULL,
    @OfficeLocationId INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    IF @UserId IS NOT NULL AND @UserId > 0
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM dbo.myonline_tbl_Users WHERE Id = @UserId AND CompanyId = @CompanyId AND IsActive = 1)
        BEGIN
            RAISERROR('Target Employee ID %d does not belong to Company %d or is inactive', 16, 1, @UserId, @CompanyId);
            RETURN;
        END
    END

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @StartDate DATETIME2 = COALESCE(@EffectiveStartDate, @Now);

    -- Check if active KPI already exists for this scope & period
    DECLARE @ExistingId INT;
    SELECT @ExistingId = KpiId
    FROM dbo.myonline_tbl_CRM_KPI
    WHERE CompanyId = @CompanyId
      AND PeriodType = @PeriodType
      AND (
          (@UserId IS NULL AND UserId IS NULL) OR
          (UserId = @UserId)
      )
      AND IsActive = 1;

    IF @ExistingId IS NOT NULL
    BEGIN
        -- Update existing target
        UPDATE dbo.myonline_tbl_CRM_KPI
        SET FollowUpTarget = @FollowUpTarget,
            InterestedTarget = @InterestedTarget,
            ClosedTarget = @ClosedTarget,
            EffectiveStartDate = @StartDate,
            OfficeLocationId = COALESCE(@OfficeLocationId, OfficeLocationId),
            UpdatedAtUtc = @Now
        WHERE KpiId = @ExistingId;

        SET @KpiId = @ExistingId;
    END
    ELSE
    BEGIN
        -- Insert new target
        INSERT INTO dbo.myonline_tbl_CRM_KPI (
            CompanyId, UserId, PeriodType, FollowUpTarget, InterestedTarget, ClosedTarget,
            EffectiveStartDate, IsActive, CreatedByUserId, CreatedAtUtc, UpdatedAtUtc, OfficeLocationId
        )
        VALUES (
            @CompanyId, @UserId, @PeriodType, @FollowUpTarget, @InterestedTarget, @ClosedTarget,
            @StartDate, 1, @CreatedByUserId, @Now, @Now, @OfficeLocationId
        );

        SET @KpiId = SCOPE_IDENTITY();
    END

    -- Return the saved KPI
    SELECT 
        k.KpiId, k.CompanyId, k.UserId, u.Name AS UserName,
        k.PeriodType, k.FollowUpTarget, k.InterestedTarget, k.ClosedTarget,
        k.EffectiveStartDate, k.IsActive, k.OfficeLocationId, o.Name AS OfficeLocationName
    FROM dbo.myonline_tbl_CRM_KPI k
    LEFT JOIN dbo.myonline_tbl_Users u ON k.UserId = u.Id
    LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON k.OfficeLocationId = o.Id
    WHERE k.KpiId = @KpiId AND k.CompanyId = @CompanyId;
END;
GO

-- ==============================================================================
-- 10. sp_Crm_Kpi_Productivity (Productivity Measurement & Double-Count Prevention)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_Kpi_Productivity
    @CompanyId INT,
    @PeriodType NVARCHAR(50) = 'Daily', -- 'Daily', 'Weekly', 'Monthly'
    @FromDate DATETIME2 = NULL,
    @ToDate DATETIME2 = NULL,
    @OfficeLocationId INT = NULL,
    @UserId INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Now DATETIME2 = SYSUTCDATETIME();
    DECLARE @Start DATETIME2 = @FromDate;
    DECLARE @End DATETIME2 = @ToDate;

    IF @Start IS NULL
    BEGIN
        IF @PeriodType = 'Daily'
            SET @Start = DATEADD(day, DATEDIFF(day, 0, @Now), 0);
        ELSE IF @PeriodType = 'Weekly'
            SET @Start = DATEADD(week, DATEDIFF(week, 0, @Now), 0);
        ELSE
            SET @Start = DATEADD(month, DATEDIFF(month, 0, @Now), 0);
    END

    IF @End IS NULL
        SET @End = @Now;

    -- CTE of Active Employees under this Company
    WITH CompanyEmployees AS (
        SELECT 
            u.Id AS EmployeeId,
            u.Name AS EmployeeName,
            u.OfficeLocationId,
            o.Name AS OfficeLocationName
        FROM dbo.myonline_tbl_Users u
        LEFT JOIN dbo.myonline_tbl_OfficeLocations o ON u.OfficeLocationId = o.Id
        WHERE u.CompanyId = @CompanyId 
          AND u.IsActive = 1
          AND (@UserId IS NULL OR u.Id = @UserId)
          AND (@OfficeLocationId IS NULL OR u.OfficeLocationId = @OfficeLocationId)
    ),
    -- Active KPI Targets
    EmployeeTargets AS (
        SELECT 
            k.UserId,
            k.FollowUpTarget,
            k.InterestedTarget,
            k.ClosedTarget
        FROM dbo.myonline_tbl_CRM_KPI k
        WHERE k.CompanyId = @CompanyId
          AND k.PeriodType = @PeriodType
          AND k.IsActive = 1
    ),
    DefaultCompanyTarget AS (
        SELECT TOP 1
            FollowUpTarget,
            InterestedTarget,
            ClosedTarget
        FROM dbo.myonline_tbl_CRM_KPI
        WHERE CompanyId = @CompanyId
          AND PeriodType = @PeriodType
          AND UserId IS NULL
          AND IsActive = 1
    ),
    -- Actual Follow-up counts performed by employee in period
    FollowUpActuals AS (
        SELECT 
            fu.CreatedByUserId AS EmployeeId,
            COUNT(fu.FollowUpId) AS FollowUpCount
        FROM dbo.myonline_tbl_CRM_LeadFollowUps fu
        WHERE fu.CompanyId = @CompanyId
          AND fu.FollowUpDateUtc >= @Start AND fu.FollowUpDateUtc <= @End
        GROUP BY fu.CreatedByUserId
    ),
    -- Actual Distinct Interested leads transitioned in period (PREVENT DOUBLE COUNTING)
    InterestedActuals AS (
        SELECT 
            sh.ChangedByUserId AS EmployeeId,
            COUNT(DISTINCT sh.LeadId) AS InterestedCount
        FROM dbo.myonline_tbl_CRM_LeadStatusHistory sh
        WHERE sh.CompanyId = @CompanyId
          AND sh.NewStatus = 'Interested'
          AND sh.ChangedDateUtc >= @Start AND sh.ChangedDateUtc <= @End
        GROUP BY sh.ChangedByUserId
    ),
    -- Actual Distinct Closed leads transitioned in period (PREVENT DOUBLE COUNTING)
    ClosedActuals AS (
        SELECT 
            sh.ChangedByUserId AS EmployeeId,
            COUNT(DISTINCT sh.LeadId) AS ClosedCount
        FROM dbo.myonline_tbl_CRM_LeadStatusHistory sh
        WHERE sh.CompanyId = @CompanyId
          AND sh.NewStatus = 'Closed'
          AND sh.ChangedDateUtc >= @Start AND sh.ChangedDateUtc <= @End
        GROUP BY sh.ChangedByUserId
    )
    SELECT 
        ce.EmployeeId AS UserId,
        ce.EmployeeName,
        ce.OfficeLocationId,
        ce.OfficeLocationName,
        FollowUpTarget = COALESCE(et.FollowUpTarget, dt.FollowUpTarget, 30),
        FollowUpDone = COALESCE(fa.FollowUpCount, 0),
        InterestedTarget = COALESCE(et.InterestedTarget, dt.InterestedTarget, 20),
        InterestedDone = COALESCE(ia.InterestedCount, 0),
        ClosedTarget = COALESCE(et.ClosedTarget, dt.ClosedTarget, 10),
        ClosedDone = COALESCE(ca.ClosedCount, 0),
        AchievementPercent = CAST(
            (
                (COALESCE(fa.FollowUpCount, 0) * 100.0 / NULLIF(COALESCE(et.FollowUpTarget, dt.FollowUpTarget, 30), 0)) * 0.4 +
                (COALESCE(ia.InterestedCount, 0) * 100.0 / NULLIF(COALESCE(et.InterestedTarget, dt.InterestedTarget, 20), 0)) * 0.3 +
                (COALESCE(ca.ClosedCount, 0) * 100.0 / NULLIF(COALESCE(et.ClosedTarget, dt.ClosedTarget, 10), 0)) * 0.3
            ) AS DECIMAL(5,1)
        )
    FROM CompanyEmployees ce
    LEFT JOIN EmployeeTargets et ON ce.EmployeeId = et.UserId
    CROSS JOIN DefaultCompanyTarget dt
    LEFT JOIN FollowUpActuals fa ON ce.EmployeeId = fa.EmployeeId
    LEFT JOIN InterestedActuals ia ON ce.EmployeeId = ia.EmployeeId
    LEFT JOIN ClosedActuals ca ON ce.EmployeeId = ca.EmployeeId
    ORDER BY AchievementPercent DESC, ce.EmployeeName ASC;
END;
GO

-- ==============================================================================
-- 11. sp_Crm_ManagerDashboard (Executive Team Metrics for Manager)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_ManagerDashboard
    @CompanyId INT,
    @ManagerUserId INT,
    @OfficeLocationId INT = NULL,
    @FromDate DATETIME2 = NULL,
    @ToDate DATETIME2 = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Today DATE = CAST(SYSUTCDATETIME() AS DATE);
    DECLARE @Start DATETIME2 = COALESCE(@FromDate, DATEADD(day, -30, SYSUTCDATETIME()));
    DECLARE @End DATETIME2 = COALESCE(@ToDate, SYSUTCDATETIME());

    -- Summary Card Metrics
    SELECT 
        TotalLeads = COUNT(*),
        NewLeads = COUNT(CASE WHEN l.LeadStatus = 'New Lead' THEN 1 END),
        FollowUpLeads = COUNT(CASE WHEN l.LeadStatus = 'Follow Up' THEN 1 END),
        InterestedLeads = COUNT(CASE WHEN l.LeadStatus = 'Interested' THEN 1 END),
        ClosedWonLeads = COUNT(CASE WHEN l.LeadStatus = 'Closed' THEN 1 END),
        NotInterestedLeads = COUNT(CASE WHEN l.LeadStatus = 'Not Interested' THEN 1 END),
        TodayFollowUps = COUNT(CASE WHEN CAST(l.NextFollowUpDate AS DATE) = @Today AND l.LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 END),
        OverdueFollowUps = COUNT(CASE WHEN CAST(l.NextFollowUpDate AS DATE) < @Today AND l.LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 END),
        UpcomingFollowUps = COUNT(CASE WHEN CAST(l.NextFollowUpDate AS DATE) > @Today AND l.LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 END),
        TotalEstimatedValue = COALESCE(SUM(l.EstimatedValue), 0),
        WonValue = COALESCE(SUM(CASE WHEN l.LeadStatus = 'Closed' THEN l.EstimatedValue ELSE 0 END), 0)
    FROM dbo.myonline_tbl_CRM_Leads l
    WHERE l.CompanyId = @CompanyId
      AND l.IsActive = 1
      AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
      AND (l.CreatedAtUtc >= @Start AND l.CreatedAtUtc <= @End);

    -- Status Breakdown for Funnel / Donut Charts
    SELECT 
        l.LeadStatus AS Status,
        COUNT(*) AS LeadCount,
        COALESCE(SUM(l.EstimatedValue), 0) AS TotalValue
    FROM dbo.myonline_tbl_CRM_Leads l
    WHERE l.CompanyId = @CompanyId
      AND l.IsActive = 1
      AND (@OfficeLocationId IS NULL OR l.OfficeLocationId = @OfficeLocationId)
      AND (l.CreatedAtUtc >= @Start AND l.CreatedAtUtc <= @End)
    GROUP BY l.LeadStatus;
END;
GO

-- ==============================================================================
-- 12. sp_Crm_EmployeeDashboard (Personal Performance & Follow-up Worklist)
-- ==============================================================================
CREATE OR ALTER PROCEDURE dbo.sp_Crm_EmployeeDashboard
    @CompanyId INT,
    @UserId INT,
    @FromDate DATETIME2 = NULL,
    @ToDate DATETIME2 = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Today DATE = CAST(SYSUTCDATETIME() AS DATE);
    DECLARE @Start DATETIME2 = COALESCE(@FromDate, DATEADD(day, -30, SYSUTCDATETIME()));
    DECLARE @End DATETIME2 = COALESCE(@ToDate, SYSUTCDATETIME());

    -- Personal Summary Cards
    SELECT 
        MyTotalLeads = COUNT(*),
        MyNewLeads = COUNT(CASE WHEN l.LeadStatus = 'New Lead' THEN 1 END),
        MyFollowUpLeads = COUNT(CASE WHEN l.LeadStatus = 'Follow Up' THEN 1 END),
        MyInterestedLeads = COUNT(CASE WHEN l.LeadStatus = 'Interested' THEN 1 END),
        MyClosedWonLeads = COUNT(CASE WHEN l.LeadStatus = 'Closed' THEN 1 END),
        MyTodayFollowUps = COUNT(CASE WHEN CAST(l.NextFollowUpDate AS DATE) = @Today AND l.LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 END),
        MyOverdueFollowUps = COUNT(CASE WHEN CAST(l.NextFollowUpDate AS DATE) < @Today AND l.LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 END),
        MyUpcomingFollowUps = COUNT(CASE WHEN CAST(l.NextFollowUpDate AS DATE) > @Today AND l.LeadStatus NOT IN ('Closed', 'Not Interested') THEN 1 END),
        MyTotalPipelineValue = COALESCE(SUM(l.EstimatedValue), 0),
        MyWonValue = COALESCE(SUM(CASE WHEN l.LeadStatus = 'Closed' THEN l.EstimatedValue ELSE 0 END), 0)
    FROM dbo.myonline_tbl_CRM_Leads l
    WHERE l.CompanyId = @CompanyId
      AND l.AssignedUserId = @UserId
      AND l.IsActive = 1;

    -- Today's Action List
    EXEC dbo.sp_Crm_FollowUp_GetToday @CompanyId = @CompanyId, @UserId = @UserId;
END;
GO

PRINT 'SUCCESS: All 12 Enterprise CRM Stored Procedures successfully created/updated.';
GO
