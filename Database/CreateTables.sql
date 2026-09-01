/* ===========================================================
   CreateTables.sql
   Creates core tables: Users, DriverLocations
   =========================================================== */
USE LiveTrackingDb;
GO

IF OBJECT_ID(N'dbo.DriverLocations', N'U') IS NOT NULL DROP TABLE dbo.DriverLocations;
IF OBJECT_ID(N'dbo.Users', N'U') IS NOT NULL DROP TABLE dbo.Users;
GO

CREATE TABLE dbo.Users
(
    UserId          INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
    Username        NVARCHAR(50)        NOT NULL,
    PasswordHash    NVARCHAR(256)       NOT NULL,
    FullName        NVARCHAR(150)       NOT NULL,
    Role            NVARCHAR(20)        NOT NULL CONSTRAINT DF_Users_Role DEFAULT ('User'), -- 'Admin' or 'User'
    IsActive        BIT                 NOT NULL CONSTRAINT DF_Users_IsActive DEFAULT (1),  -- disabled flag inverse
    PhoneNumber     NVARCHAR(30)        NULL,
    CreatedAtUtc    DATETIME2           NOT NULL CONSTRAINT DF_Users_CreatedAtUtc DEFAULT (SYSUTCDATETIME()),
    UpdatedAtUtc    DATETIME2           NULL,
    CONSTRAINT UQ_Users_Username UNIQUE (Username),
    CONSTRAINT CK_Users_Role CHECK (Role IN ('Admin','User'))
);
GO

CREATE TABLE dbo.DriverLocations
(
    LocationId      BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    UserId          INT                  NOT NULL,
    Latitude        FLOAT                NOT NULL,
    Longitude       FLOAT                NOT NULL,
    Accuracy        FLOAT                NULL,
    Speed           FLOAT                NULL,
    Bearing         FLOAT                NULL,
    RecordedAtUtc   DATETIME2            NOT NULL, -- time the ping was captured on device
    ReceivedAtUtc   DATETIME2            NOT NULL CONSTRAINT DF_DriverLocations_ReceivedAtUtc DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT FK_DriverLocations_Users FOREIGN KEY (UserId) REFERENCES dbo.Users(UserId)
);
GO
