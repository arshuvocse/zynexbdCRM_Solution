/* ===========================================================
   CreateIndexes.sql
   =========================================================== */
USE LiveTrackingDb;
GO

CREATE NONCLUSTERED INDEX IX_DriverLocations_UserId_RecordedAtUtc
    ON dbo.DriverLocations (UserId, RecordedAtUtc DESC)
    INCLUDE (Latitude, Longitude, Accuracy, Speed, Bearing);
GO

CREATE NONCLUSTERED INDEX IX_DriverLocations_RecordedAtUtc
    ON dbo.DriverLocations (RecordedAtUtc DESC);
GO

CREATE NONCLUSTERED INDEX IX_Users_Role_IsActive
    ON dbo.Users (Role, IsActive);
GO
