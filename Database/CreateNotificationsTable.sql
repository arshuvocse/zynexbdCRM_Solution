/* ===========================================================
   CreateNotificationsTable.sql
   Table: myonline_tbl_Notifications (Push & In-App Notifications)
   =========================================================== */
USE LiveTrackingDb;
GO

IF OBJECT_ID(N'dbo.myonline_tbl_Notifications', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.myonline_tbl_Notifications
    (
        Id              INT IDENTITY(1,1)   NOT NULL PRIMARY KEY,
        UserId          INT                 NULL, -- NULL for Broadcast / Admins
        TargetRole      NVARCHAR(20)        NOT NULL CONSTRAINT DF_Notifications_TargetRole DEFAULT ('All'),
        Title           NVARCHAR(200)       NOT NULL,
        Message         NVARCHAR(1000)      NOT NULL,
        Type            NVARCHAR(50)        NOT NULL CONSTRAINT DF_Notifications_Type DEFAULT ('General'),
        ReferenceId     NVARCHAR(100)       NULL,
        IsRead          BIT                 NOT NULL CONSTRAINT DF_Notifications_IsRead DEFAULT (0),
        CreatedAt       DATETIME2           NOT NULL CONSTRAINT DF_Notifications_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_Notifications_Users FOREIGN KEY (UserId) REFERENCES dbo.myonline_tbl_Users(Id) ON DELETE SET NULL
    );

    CREATE INDEX IX_Notifications_User_Read_Created
        ON dbo.myonline_tbl_Notifications(UserId, IsRead, CreatedAt DESC);
        
    CREATE INDEX IX_Notifications_TargetRole_Created
        ON dbo.myonline_tbl_Notifications(TargetRole, CreatedAt DESC);
END
GO
