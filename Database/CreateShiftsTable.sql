-- Create Shifts Table if not exists
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'myonline_tbl_Shifts')
BEGIN
    CREATE TABLE myonline_tbl_Shifts (
        ShiftId INT IDENTITY(1,1) PRIMARY KEY,
        ShiftName NVARCHAR(100) NOT NULL,
        StartTime NVARCHAR(20) NOT NULL DEFAULT '09:00:00',
        EndTime NVARCHAR(20) NOT NULL DEFAULT '18:00:00',
        GracePeriodMinutes INT NOT NULL DEFAULT 15,
        IsDefault BIT NOT NULL DEFAULT 0,
        IsActive BIT NOT NULL DEFAULT 1,
        CreatedByAdminId INT NULL,
        CreatedAtUtc DATETIME2 NOT NULL DEFAULT GETUTCDATE()
    );

    -- Insert Default General Shift
    INSERT INTO myonline_tbl_Shifts (ShiftName, StartTime, EndTime, GracePeriodMinutes, IsDefault, IsActive, CreatedAtUtc)
    VALUES ('General Shift', '09:00:00', '18:00:00', 15, 1, 1, GETUTCDATE());
END
GO

-- Add Status & ShiftName to myonline_tbl_Attendances
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('myonline_tbl_Attendances') AND name = 'Status')
BEGIN
    ALTER TABLE myonline_tbl_Attendances ADD Status NVARCHAR(50) NULL DEFAULT 'On Time';
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('myonline_tbl_Attendances') AND name = 'ShiftName')
BEGIN
    ALTER TABLE myonline_tbl_Attendances ADD ShiftName NVARCHAR(100) NULL DEFAULT 'General Shift';
END
GO

-- Add ShiftId to myonline_tbl_Users
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('myonline_tbl_Users') AND name = 'ShiftId')
BEGIN
    ALTER TABLE myonline_tbl_Users ADD ShiftId INT NULL;
END
GO
