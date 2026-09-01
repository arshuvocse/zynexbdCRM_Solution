/* ===========================================================
   CreateAppVersionsTable.sql
   Table: myonline_tbl_AppVersions (App Version Control)
   =========================================================== */
USE LiveTrackingDb;
GO

IF OBJECT_ID(N'dbo.myonline_tbl_AppVersions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_AppVersions
    (
        Id              INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        Platform        NVARCHAR(30)        NOT NULL CONSTRAINT DF_AppVersions_Platform DEFAULT ('Android'),
        VersionCode     INT                 NOT NULL,
        VersionName     NVARCHAR(50)        NOT NULL,
        MinVersionCode  INT                 NOT NULL CONSTRAINT DF_AppVersions_MinVersionCode DEFAULT (1),
        IsForceUpdate   BIT                 NOT NULL CONSTRAINT DF_AppVersions_IsForceUpdate DEFAULT (0),
        DownloadUrl     NVARCHAR(1000)      NOT NULL,
        Title           NVARCHAR(200)       NOT NULL CONSTRAINT DF_AppVersions_Title DEFAULT ('New Update Available'),
        ReleaseNotes    NVARCHAR(MAX)       NULL,
        IsActive        BIT                 NOT NULL CONSTRAINT DF_AppVersions_IsActive DEFAULT (1),
        CreatedAt       DATETIME2           NOT NULL CONSTRAINT DF_AppVersions_CreatedAt DEFAULT (SYSUTCDATETIME())
    );

    CREATE INDEX IX_AppVersions_Platform_Active_Code
        ON dbo.myonline_tbl_AppVersions(Platform, IsActive, VersionCode);

    -- Seed Initial Version
    INSERT INTO dbo.myonline_tbl_AppVersions 
        (Platform, VersionCode, VersionName, MinVersionCode, IsForceUpdate, DownloadUrl, Title, ReleaseNotes, IsActive)
    VALUES 
        ('Android', 1, '1.0', 1, 0, 'http://104.215.157.203:120/downloads/app-release.apk', 'Welcome to Live Tracking v1.0', 'Initial Release with live tracking, attendance with selfie, and customer visit management.', 1);
END
GO
