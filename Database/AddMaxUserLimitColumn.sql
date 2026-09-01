USE LiveTrackingDB;
GO

IF NOT EXISTS (
    SELECT * FROM sys.columns 
    WHERE object_id = OBJECT_ID('myonline_tbl_Users') 
    AND name = 'MaxUserLimit'
)
BEGIN
    ALTER TABLE myonline_tbl_Users 
    ADD MaxUserLimit INT NULL CONSTRAINT DF_Users_MaxUserLimit DEFAULT 10;
    
    PRINT 'Added MaxUserLimit column to myonline_tbl_Users with default 10';
END
GO

-- Set default 10 for existing Admin accounts if null
UPDATE myonline_tbl_Users 
SET MaxUserLimit = 10 
WHERE Role = 'Admin' AND MaxUserLimit IS NULL;
GO

SELECT Id, Username, Name, Role, MaxUserLimit, CreatedByAdminId 
FROM myonline_tbl_Users 
WHERE Role = 'Admin';
GO
